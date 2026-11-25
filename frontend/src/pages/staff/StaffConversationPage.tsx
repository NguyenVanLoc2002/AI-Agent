import { useCallback, useEffect, useRef, useState } from 'react';
import type { ChatMessage as Message } from '../../api/chat';
import { MAX_MESSAGE_CHARS } from '../../api/chat';
import { ApiError } from '../../api/client';
import { postStaffMessage, readHeldMessages, readHeldQuote, resolveHandoff, type Handoff, type StaffQuote } from '../../api/staff';
import { formatMoney } from '../../app/money';
import { ATTENTION_STATUSES, reasonLabel, shortConversation, staffQuoteStatusLabel } from '../../app/staffLabels';
import { failureText, formatDateTime, referenceText } from '../../app/texts';
import { Alert, Badge, Button, Dialog, Skeleton, Textarea } from '../../design-system';
import type { InboxNotice } from './StaffInboxPage';

// F3 · HO-02 conversation held by this staff member (UC-06; specs/50 §F3). The backend checks on every
// call that the caller still holds it (A-15): a 404 means the access ended. Messages and the quote are
// read on entry; messages again on "Tải tin mới" and after a send (A-69, 2A). The quote is read once per
// visit because every price read is audited (AUDIT-STRICT-READ). It has no confirmation token: staff
// never confirm for the customer.

type Messages = { phase: 'loading' } | { phase: 'ready'; items: Message[] } | { phase: 'failed'; error: unknown };
type Quote = { phase: 'loading' } | { phase: 'none' } | { phase: 'ready'; quote: StaffQuote } | { phase: 'failed'; error: unknown };

interface Props {
  handoff: Handoff;
  /** Back to the inbox, with what to tell the staff member there. */
  onLeave: (notice: InboxNotice | null) => void;
  onUnauthenticated: () => void;
}

const LOST: InboxNotice = { tone: 'info', title: 'Không tìm thấy cuộc trò chuyện.', body: 'Bạn không còn giữ cuộc trò chuyện này. Danh sách đã được tải lại.' };

export function StaffConversationPage({ handoff, onLeave, onUnauthenticated }: Props) {
  const [messages, setMessages] = useState<Messages>({ phase: 'loading' });
  const [quote, setQuote] = useState<Quote>({ phase: 'loading' });
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [sendNotice, setSendNotice] = useState<{ tone: 'warning' | 'error' | 'success'; title: string; body?: string } | null>(null);
  const [confirmResolve, setConfirmResolve] = useState(false);
  const [resolving, setResolving] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  /** UX review M2: what a read of the messages on request found, announced politely. */
  const [refreshNote, setRefreshNote] = useState('');
  const mounted = useRef(true);
  const field = useRef<HTMLDivElement>(null);
  /** Security review M-1: only the newest read of the messages is applied. */
  const readSeq = useRef(0);
  /**
   * Security review M-2: the text of a send whose outcome is unknown and that the next read did not show.
   * Sending that same text again needs an explicit "Xác nhận gửi lại" (the endpoint cannot deduplicate).
   */
  const [unconfirmed, setUnconfirmed] = useState<string | null>(null);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  /** 401 → login; 404 → the access ended, back to the inbox. True when handled. */
  const common = useCallback((error: unknown): boolean => {
    if (!(error instanceof ApiError)) return false;
    if (error.unauthenticated) { onUnauthenticated(); return true; }
    if (error.status === 404 || error.forbidden) { onLeave(LOST); return true; }
    return false;
  }, [onLeave, onUnauthenticated]);

  const readMessages = useCallback(async (): Promise<Message[] | null> => {
    const seq = ++readSeq.current;
    try {
      const items = await readHeldMessages(handoff.conversationId);
      if (!mounted.current || seq !== readSeq.current) return null;
      {
        setMessages((current) => {
          if (current.phase === 'ready') {
            const added = items.length - current.items.length;
            setRefreshNote(added > 0 ? `Có ${added} tin mới.` : 'Chưa có tin mới.');
          }
          return { phase: 'ready', items };
        });
      }
      return items;
    } catch (error) {
      if (mounted.current && seq === readSeq.current && !common(error)) setMessages({ phase: 'failed', error });
      return null;
    }
  }, [common, handoff.conversationId]);

  const readQuote = useCallback(async () => {
    setQuote({ phase: 'loading' });
    try {
      const found = await readHeldQuote(handoff.conversationId);
      if (mounted.current) setQuote({ phase: 'ready', quote: found });
    } catch (error) {
      if (!mounted.current) return;
      if (error instanceof ApiError && error.unauthenticated) { onUnauthenticated(); return; }
      if (error instanceof ApiError && error.forbidden) { onLeave(LOST); return; }
      // The messages were readable a moment ago, so a 404 here means "no quote" rather than lost access;
      // a lost access shows on the next message read or send.
      if (error instanceof ApiError && error.status === 404) { setQuote({ phase: 'none' }); return; }
      setQuote({ phase: 'failed', error });
    }
  }, [handoff.conversationId, onLeave, onUnauthenticated]);

  useEffect(() => {
    void (async () => {
      if (await readMessages()) await readQuote();
    })();
  }, [readMessages, readQuote]);

  async function refresh() {
    if (refreshing || sending) return;
    setRefreshing(true);
    setRefreshNote('');
    try {
      await readMessages();
    } finally {
      if (mounted.current) setRefreshing(false);
    }
  }

  async function send() {
    if (sending || refreshing) return;
    const text = draft.trim();
    if (text === '' || text.length > MAX_MESSAGE_CHARS) return;
    setSending(true);
    setSendNotice(null);
    try {
      await postStaffMessage(handoff.conversationId, text);
      if (!mounted.current) return;
      setUnconfirmed(null);
      setDraft('');
      setSendNotice({ tone: 'success', title: 'Đã gửi tin cho khách.' });
      await readMessages();
    } catch (error) {
      if (!mounted.current || common(error)) return;
      if (error instanceof ApiError && error.status === 409) {
        setSendNotice({ tone: 'warning', title: 'Hệ thống đang bận.', body: 'Tin chưa được gửi. Nội dung vẫn được giữ; vui lòng thử lại sau ít giây.' });
      } else if (error instanceof ApiError && (!error.sent || (error.kind === 'http' && error.status !== null && error.status < 500))) {
        setSendNotice({ tone: 'error', title: 'Chưa gửi được tin.', body: `Tin chưa được gửi; nội dung vẫn được giữ. ${failureText(error)} ${referenceText(error) ?? ''}`.trim() });
      } else {
        // The message may be stored, and this endpoint has no event ID to dedupe a resend: read the
        // conversation again so staff see whether it arrived before sending again.
        const items = await readMessages();
        if (!mounted.current) return;
        const last = items?.filter((m) => m.direction === 'STAFF').pop();
        if (last && last.text.trim() === text) {
          setDraft('');
          setUnconfirmed(null);
          setSendNotice({ tone: 'success', title: 'Đã gửi tin cho khách.', body: 'Tin đã có trong cuộc trò chuyện.' });
        } else {
          setUnconfirmed(text);
          setSendNotice({ tone: 'warning', title: 'Chưa xác nhận được tin đã gửi.',
            body: 'Chưa thấy tin trong cuộc trò chuyện. Bấm "Tải tin mới" để kiểm tra; nếu vẫn không có, bấm "Xác nhận gửi lại". Nội dung vẫn được giữ.' });
        }
      }
    } finally {
      if (mounted.current) {
        setSending(false);
        // UX review M1: the box is never disabled while sending (read-only instead), so focus can return.
        field.current?.querySelector('textarea')?.focus();
      }
    }
  }

  async function resolve() {
    if (resolving) return;
    setResolving(true);
    try {
      const result = await resolveHandoff(handoff.handoffId);
      if (!mounted.current) return;
      onLeave(result.followUpReasonCode
        ? { tone: 'info', title: 'Đã hoàn tất.', body: `Hệ thống đã mở yêu cầu mới (${reasonLabel(result.followUpReasonCode)}); trợ lý AI vẫn tạm dừng.` }
        : { tone: 'success', title: 'Đã hoàn tất.', body: 'Trợ lý AI trả lời khách trở lại.' });
    } catch (error) {
      if (!mounted.current) return;
      if (error instanceof ApiError && error.unauthenticated) { onUnauthenticated(); return; }
      if (error instanceof ApiError && (error.status === 404 || error.forbidden)) { onLeave(LOST); return; }
      setConfirmResolve(false);
      if (error instanceof ApiError && error.status === 409) {
        setSendNotice({ tone: 'warning', title: 'Chưa hoàn tất được.', body: 'Yêu cầu đang bận hoặc không còn ở trạng thái bạn giữ. Vui lòng thử lại sau ít giây.' });
      } else if (error instanceof ApiError && !error.sent) {
        setSendNotice({ tone: 'error', title: 'Chưa hoàn tất được.', body: `Yêu cầu chưa được gửi. ${failureText(error)}` });
      } else {
        onLeave({ tone: 'warning', title: 'Chưa xác nhận được việc hoàn tất.', body: 'Danh sách đã được tải lại; nếu yêu cầu vẫn do bạn giữ, hãy mở lại và hoàn tất lần nữa.' });
      }
    } finally {
      if (mounted.current) setResolving(false);
    }
  }

  return (
    <section aria-labelledby="held-title" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 'var(--space-4)', flexWrap: 'wrap' }}>
        <div>
          <Button variant="ghost" size="sm" iconLeft="arrow-left" onClick={() => onLeave(null)}>Về hộp chờ</Button>
          <h1 id="held-title" style={{ margin: '4px 0 0', font: 'var(--type-page-title)' }}>Cuộc trò chuyện <span className="ap-code">{shortConversation(handoff.conversationId)}</span></h1>
          <p style={{ margin: '4px 0 0', font: 'var(--type-body)', color: 'var(--text-secondary)' }}>
            Lý do chuyển: {reasonLabel(handoff.reasonCode)}{handoff.followUpReasonCode ? ` · Việc tiếp theo: ${reasonLabel(handoff.followUpReasonCode)}` : ''}
          </p>
        </div>
        <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
          <Button variant="secondary" iconLeft="refresh-cw" loading={refreshing || messages.phase === 'loading'} disabled={sending} onClick={() => void refresh()}>
            {refreshing ? 'Đang tải…' : 'Tải tin mới'}
          </Button>
          <Button iconLeft="circle-check" onClick={() => setConfirmResolve(true)}>Hoàn tất và mở lại AI</Button>
        </div>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: 'minmax(0, 2fr) minmax(280px, 1fr)', gap: 'var(--space-4)', alignItems: 'start' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
          {messages.phase === 'loading' && <Skeleton lines={5} />}
          {messages.phase === 'failed' && (
            <Alert tone="error" title="Chưa tải được tin nhắn"
              action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" loading={refreshing} onClick={() => void refresh()}>Thử lại</Button>}>
              {busyOr(messages.error)}
              {referenceText(messages.error) && <span style={{ display: 'block' }}>{referenceText(messages.error)}</span>}
            </Alert>
          )}
          {messages.phase === 'ready' && (
            <ol aria-label="Tin nhắn" style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
              {messages.items.length === 0 && <li style={{ color: 'var(--text-secondary)' }}>Chưa có tin nhắn.</li>}
              {messages.items.map((m, i) => <li key={i}><MessageRow message={m} /></li>)}
            </ol>
          )}
          <p role="status" style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--text-secondary)', minHeight: '1em' }}>{refreshNote}</p>
          {sendNotice && <Alert tone={sendNotice.tone} title={sendNotice.title} onDismiss={() => setSendNotice(null)}>{sendNotice.body}</Alert>}
          <div ref={field}>
            <Textarea label="Tin nhắn cho khách" rows={3} maxLength={MAX_MESSAGE_CHARS} value={draft} readOnly={sending} aria-busy={sending || undefined}
              helper="Tối đa 2.000 ký tự. Khách thấy tin với nhãn &quot;Nhân viên tư vấn&quot;."
              onChange={(e) => { setDraft(e.target.value); if (sendNotice?.tone === 'success') setSendNotice(null); }} />
          </div>
          <div>
            <Button loading={sending} disabled={draft.trim() === '' || sending || refreshing} iconLeft="send" onClick={() => void send()}>
              {sending ? 'Đang gửi…' : unconfirmed !== null && unconfirmed === draft.trim() ? 'Xác nhận gửi lại' : 'Gửi cho khách'}
            </Button>
          </div>
        </div>
        <QuoteBox quote={quote} onRetry={() => void readQuote()} />
      </div>

      <Dialog open={confirmResolve} tone="warning" title="Hoàn tất và mở lại trợ lý AI?"
        description="Sau khi hoàn tất, bạn không xem được cuộc trò chuyện này nữa. Trợ lý AI trả lời khách trở lại, trừ khi còn việc tiếp theo: khi đó hệ thống mở yêu cầu mới và trợ lý AI vẫn tạm dừng."
        onClose={resolving ? undefined : () => setConfirmResolve(false)}
        footer={<>
          <Button variant="secondary" disabled={resolving} onClick={() => setConfirmResolve(false)}>Hủy</Button>
          <Button loading={resolving} onClick={() => void resolve()}>{resolving ? 'Đang hoàn tất…' : 'Hoàn tất và mở lại AI'}</Button>
        </>}>
        {draft.trim() !== '' && (
          <p style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--status-warning-fg)' }}>Tin đang soạn chưa được gửi và sẽ không được gửi.</p>
        )}
      </Dialog>
    </section>
  );
}

function busyOr(error: unknown): string {
  if (error instanceof ApiError && error.status === 409) return 'Hệ thống đang bận. Vui lòng thử lại sau ít giây.';
  return failureText(error);
}

const AUTHORS: Record<Message['direction'], string> = { CUSTOMER: 'Khách hàng', BOT: 'Trợ lý AI', STAFF: 'Nhân viên tư vấn' };

function MessageRow({ message }: { message: Message }) {
  const staff = message.direction === 'STAFF';
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: staff ? 'flex-end' : 'flex-start', gap: 4 }}>
      <div style={{ display: 'flex', gap: 'var(--space-2)', font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>
        <span style={{ fontWeight: 600, color: message.direction === 'BOT' ? 'var(--source-ai-fg)' : 'var(--text-primary)' }}>{AUTHORS[message.direction]}</span>
        {message.sentAt && <span>{formatDateTime(message.sentAt)}</span>}
      </div>
      <div style={{ maxWidth: '85%', padding: '8px 12px', borderRadius: 'var(--radius-lg)', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', font: 'var(--type-body)',
        background: staff ? 'var(--action-primary-subtle)' : message.direction === 'BOT' ? 'var(--source-ai-bg)' : 'var(--bg-muted)', color: 'var(--text-primary)' }}>
        {message.text}
      </div>
    </div>
  );
}

function QuoteBox({ quote, onRetry }: { quote: Quote; onRetry: () => void }) {
  return (
    <aside aria-labelledby="held-quote-title" style={{ background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-card)', borderRadius: 'var(--radius-lg)',
      padding: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
      <h2 id="held-quote-title" style={{ margin: 0, font: 'var(--type-card-title)' }}>Báo giá của cuộc trò chuyện</h2>
      {quote.phase === 'loading' && <Skeleton lines={3} />}
      {quote.phase === 'none' && <p style={{ margin: 0, color: 'var(--text-secondary)', font: 'var(--type-body)' }}>Chưa có báo giá.</p>}
      {quote.phase === 'failed' && (
        <Alert tone="error" title="Chưa hiển thị được báo giá"
          action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={onRetry}>Thử lại</Button>}>
          {quote.error instanceof ApiError && quote.error.status === 503
            ? 'Hệ thống chưa ghi được nhật ký xem giá nên không hiển thị giá. Vui lòng thử lại sau.'
            : busyOr(quote.error)}
          {referenceText(quote.error) && <span style={{ display: 'block' }}>{referenceText(quote.error)}</span>}
        </Alert>
      )}
      {quote.phase === 'ready' && <QuoteDetails quote={quote.quote} />}
    </aside>
  );
}

function QuoteDetails({ quote }: { quote: StaffQuote }) {
  return (
    <>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
        <span className="ap-code" style={{ fontWeight: 600 }}>{quote.quoteRef} · phiên bản {quote.version}</span>
        <Badge tone={ATTENTION_STATUSES.includes(quote.status) ? 'warning' : 'neutral'} shape="pill">
          {quote.status === 'ISSUED' && quote.validUntil.getTime() <= Date.now() ? 'Hết hiệu lực' : staffQuoteStatusLabel(quote.status)}
        </Badge>
      </div>
      <p style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>Hiệu lực đến {formatDateTime(quote.validUntil)}</p>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', font: 'var(--type-table)' }}>
          <caption style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>Các dòng của báo giá {quote.quoteRef}</caption>
          <thead>
            <tr style={{ font: 'var(--type-table-header)', color: 'var(--text-secondary)', textAlign: 'left' }}>
              <th scope="col">SKU</th><th scope="col" style={{ textAlign: 'right' }}>SL × Đơn giá</th><th scope="col" style={{ textAlign: 'right' }}>Thành tiền</th>
            </tr>
          </thead>
          <tbody>
            {quote.lines.map((line, i) => (
              <tr key={i} style={{ borderTop: '1px solid var(--border-subtle)' }}>
                <td className="ap-code" style={{ padding: 'var(--space-1) 0', overflowWrap: 'anywhere' }}>{line.sku}</td>
                <td style={{ padding: 'var(--space-1) 0', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{line.quantity} × {formatMoney(line.unitPrice, quote.currency)}</td>
                <td style={{ padding: 'var(--space-1) 0', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{formatMoney(line.lineTotal, quote.currency)}</td>
              </tr>
            ))}
          </tbody>
          <tfoot>
            <tr style={{ borderTop: '1px solid var(--border-default)', fontWeight: 600 }}>
              <th scope="row" colSpan={2} style={{ textAlign: 'left', padding: 'var(--space-1) 0' }}>Tạm tính</th>
              <td style={{ textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{formatMoney(quote.total, quote.currency)}</td>
            </tr>
          </tfoot>
        </table>
      </div>
      {quote.notes.length > 0 && (
        <ul style={{ margin: 0, paddingLeft: 'var(--space-5)', font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>
          {quote.notes.map((n, i) => <li key={i}>{n}</li>)}
        </ul>
      )}
      <p style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>
        Giá do hệ thống lập. Chỉ xem: khách tự xác nhận báo giá trên kênh chat; nhân viên không xác nhận thay khách.
      </p>
    </>
  );
}
