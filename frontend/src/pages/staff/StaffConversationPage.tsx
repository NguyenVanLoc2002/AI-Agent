import { useCallback, useEffect, useRef, useState } from 'react';
import type { ChatMessage as Message } from '../../api/chat';
import { MAX_MESSAGE_CHARS } from '../../api/chat';
import { ApiError } from '../../api/client';
import { postStaffMessage, readHeldMessages, readHeldQuote, resolveHandoff, type Handoff, type StaffQuote } from '../../api/staff';
import { formatMoney } from '../../app/money';
import { PageState } from '../../app/PageState';
import { useExtraCrumb } from '../../app/shells';
import { ATTENTION_STATUSES, reasonLabel, shortConversation, staffQuoteStatusLabel } from '../../app/staffLabels';
import { failureText, formatDateTime, referenceText } from '../../app/texts';
import { useToast } from '../../app/toast';
import { Alert, Badge, Button, ChatMessage, Dialog, Skeleton, Textarea } from '../../design-system';
import type { InboxNotice } from './StaffInboxPage';

// F3 · HO-02 conversation held by this staff member (UC-06; specs/50 §F3). The backend checks on every
// call that the caller still holds it (A-15): a 404 means the access ended. Messages and the quote are
// read on entry; messages again on "Tải tin mới" and after a send (A-69, 2A). The quote is read once per
// visit because every price read is audited (AUDIT-STRICT-READ). It has no confirmation token: staff
// never confirm for the customer.
// FE-UI-P3: layout and copy of the approved artboard F3-Conversation; a lost access (404) is the F6 "not found"
// state on this page, the content is removed at once; a 409 is the F6 "busy" toast.

type Messages = { phase: 'loading' } | { phase: 'ready'; items: Message[] } | { phase: 'failed'; error: unknown };
type Quote = { phase: 'loading' } | { phase: 'none' } | { phase: 'ready'; quote: StaffQuote } | { phase: 'failed'; error: unknown };

interface Props {
  handoff: Handoff;
  /** Back to the inbox, with what to tell the staff member there. */
  onLeave: (notice: InboxNotice | null) => void;
  onUnauthenticated: () => void;
}

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
  /** The access ended (404, or 403): messages and quote leave the screen; only the "not found" state stays. */
  const [lost, setLost] = useState<{ error: unknown } | null>(null);
  const toast = useToast();
  const header = useRef<HTMLDivElement>(null);
  const ref = shortConversation(handoff.conversationId);
  // The breadcrumb keeps the ref only while the access lasts (P3 security review L-2).
  useExtraCrumb(lost ? null : ref);
  const lostBox = useRef<HTMLDivElement>(null);
  // The control that had focus (send, resolve) is gone with the content: focus the way back (WCAG 2.4.3).
  useEffect(() => { if (lost) lostBox.current?.querySelector('button')?.focus(); }, [lost]);
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
    // F3 opens without a route change (D04), so the shell does not move focus: start at the page heading (WCAG 2.4.3).
    const h1 = header.current?.querySelector('h1');
    if (h1) { h1.setAttribute('tabindex', '-1'); h1.focus(); }
    return () => { mounted.current = false; };
  }, []);

  /** 401 → login; 404 → the access ended (the page shows "not found"). True when handled. */
  const common = useCallback((error: unknown): boolean => {
    if (!(error instanceof ApiError)) return false;
    if (error.unauthenticated) { onUnauthenticated(); return true; }
    if (error.status === 404 || error.forbidden) { setLost({ error }); return true; }
    return false;
  }, [onUnauthenticated]);

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
      if (error instanceof ApiError && error.forbidden) { setLost({ error }); return; }
      // The messages were readable a moment ago, so a 404 here means "no quote" rather than lost access;
      // a lost access shows on the next message read or send.
      if (error instanceof ApiError && error.status === 404) { setQuote({ phase: 'none' }); return; }
      setQuote({ phase: 'failed', error });
    }
  }, [handoff.conversationId, onUnauthenticated]);

  /** The quote is read once per visit (audited), after the first messages read that succeeds (P3 review UX M1). */
  const quoteRead = useRef(false);
  const readQuoteOnce = useCallback(async () => {
    if (quoteRead.current) return;
    quoteRead.current = true;
    await readQuote();
  }, [readQuote]);

  useEffect(() => {
    void (async () => {
      if (await readMessages()) await readQuoteOnce();
    })();
  }, [readMessages, readQuoteOnce]);

  async function refresh() {
    if (refreshing || sending) return;
    setRefreshing(true);
    setRefreshNote('');
    try {
      if (await readMessages()) await readQuoteOnce();
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
        // F6 toast; the text stays in the box, nothing was sent.
        toast.show('conversation-busy');
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
      if (error instanceof ApiError && (error.status === 404 || error.forbidden)) { setConfirmResolve(false); setLost({ error }); return; }
      setConfirmResolve(false);
      if (error instanceof ApiError && error.status === 409) {
        // Not the "busy" toast: the backend also answers 409 when the handoff is no longer held by this member
        // (P3 security review M-1), so the wording stays hedged as before P3.
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

  if (lost) {
    return (
      <div ref={lostBox}>
        <PageState kind="not-found" error={lost.error} title="Không tìm thấy cuộc trò chuyện"
        description="Yêu cầu đã hoàn tất hoặc không còn do bạn xử lý. Quay lại hộp chờ để xem yêu cầu khác."
        action={<Button variant="secondary" size="sm" iconLeft="arrow-left" onClick={() => onLeave(null)}>Về hộp chờ</Button>} />
      </div>
    );
  }

  return (
    <>
      <div ref={header} style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 'var(--space-4)', flexWrap: 'wrap' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
          <h1 style={{ margin: 0, font: 'var(--type-page-title)', outline: 'none' }}>Cuộc trò chuyện <span className="ap-code">{ref}</span></h1>
          <div className="ap-cluster">
            <Badge tone="info" shape="pill" size="sm">Bạn đang xử lý</Badge>
            <Badge tone="neutral" size="sm" icon="circle-alert">Lý do: {reasonLabel(handoff.reasonCode)}</Badge>
            {handoff.followUpReasonCode && <Badge tone="neutral" size="sm" icon="list-checks">Việc tiếp theo: {reasonLabel(handoff.followUpReasonCode)}</Badge>}
            <Badge tone="restricted" size="sm" icon="circle-pause">Trợ lý AI đang tạm dừng</Badge>
          </div>
        </div>
        <div className="ap-cluster">
          <Button variant="ghost" iconLeft="arrow-left" onClick={() => onLeave(null)}>Về hộp chờ</Button>
          <Button iconLeft="circle-check" onClick={() => setConfirmResolve(true)}>Hoàn tất và mở lại AI</Button>
        </div>
      </div>

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-5)', alignItems: 'flex-start' }}>
        <section aria-labelledby="held-messages-title" style={{ flex: '999 1 480px', minWidth: 0, background: 'var(--bg-surface)',
          border: 'var(--border-width-default) solid var(--border-card)', borderRadius: 'var(--radius-lg)', display: 'flex', flexDirection: 'column' }}>
          <div style={{ padding: 'var(--space-5)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)', minHeight: 420, boxSizing: 'border-box' }}>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)' }}>
              <h2 id="held-messages-title" className="ap-visually-hidden">Tin nhắn</h2>
              {/* Not on the artboard: messages are read again only on request (A-69, 2A), so the page needs this button. */}
              <Button variant="ghost" size="sm" iconLeft="refresh-cw" loading={refreshing || messages.phase === 'loading'} disabled={sending}
                onClick={() => void refresh()} style={{ marginLeft: 'auto' }}>
                {refreshing ? 'Đang tải…' : 'Tải tin mới'}
              </Button>
            </div>
            {messages.phase === 'loading' && <PageState kind="loading" loadingLabel="Đang tải tin nhắn" />}
            {messages.phase === 'failed' && (
              <PageState kind={messages.error instanceof ApiError && messages.error.kind === 'timeout' ? 'timeout' : 'error'} error={messages.error}
                title={messages.error instanceof ApiError && messages.error.kind === 'timeout' ? undefined : 'Chưa tải được tin nhắn'}
                description={messages.error instanceof ApiError && messages.error.kind === 'timeout' ? undefined : busyOr(messages.error)}
                action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" loading={refreshing} onClick={() => void refresh()}>Thử lại</Button>} />
            )}
            {messages.phase === 'ready' && (
              <ol aria-label="Tin nhắn" style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
                {messages.items.length === 0 && <li style={{ color: 'var(--text-secondary)' }}>Chưa có tin nhắn.</li>}
                {messages.items.map((m, i) => <li key={i}><MessageRow message={m} /></li>)}
              </ol>
            )}
            <p role="status" style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--text-secondary)', minHeight: '1em' }}>{refreshNote}</p>
          </div>
          <div style={{ borderTop: 'var(--border-width-default) solid var(--border-default)', padding: 'var(--space-4) var(--space-5)', display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' }}>
            {sendNotice && <Alert tone={sendNotice.tone} title={sendNotice.title} onDismiss={() => setSendNotice(null)}>{sendNotice.body}</Alert>}
            <div ref={field}>
              <Textarea label="Trả lời khách hàng" rows={3} maxLength={MAX_MESSAGE_CHARS} value={draft} readOnly={sending} aria-busy={sending || undefined}
                placeholder="Nhập tin nhắn cho khách…"
                helper="Tối đa 2.000 ký tự. Khách thấy tin với nhãn “Nhân viên tư vấn”."
                onChange={(e) => { setDraft(e.target.value); if (sendNotice?.tone === 'success') setSendNotice(null); }} />
            </div>
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <Button variant="secondary" loading={sending} disabled={draft.trim() === '' || sending || refreshing} iconLeft="send" onClick={() => void send()}>
                {sending ? 'Đang gửi…' : unconfirmed !== null && unconfirmed === draft.trim() ? 'Xác nhận gửi lại' : 'Gửi'}
              </Button>
            </div>
          </div>
        </section>
        <aside aria-label="Báo giá và lưu ý" style={{ flex: '1 1 320px', minWidth: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <QuoteBox quote={quote} onRetry={() => void readQuote()} />
          <Alert tone="neutral" icon="history" title="Đã ghi nhật ký kiểm toán">
            Việc mở tin nhắn và báo giá của cuộc trò chuyện này được ghi lại. Sau khi hoàn tất, bạn không còn xem được nội dung.
          </Alert>
          <Alert tone="info" title="Khi bấm Hoàn tất">
            Trợ lý AI trả lời lại khách. Nếu còn việc chưa xong (ví dụ đơn chưa rõ kết quả), hệ thống mở yêu cầu tiếp theo và AI vẫn tạm dừng.
          </Alert>
        </aside>
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
    </>
  );
}

function busyOr(error: unknown): string {
  if (error instanceof ApiError && error.status === 409) return 'Hệ thống đang bận. Vui lòng thử lại sau ít giây.';
  return failureText(error);
}

const AUTHORS: Record<Message['direction'], string> = { CUSTOMER: 'Khách hàng', BOT: 'Trợ lý AI', STAFF: 'Nhân viên tư vấn' };

// F3 artboard: the design-system ChatMessage. The customer's bubble shows no author, so the author is given to screen
// readers; a staff message has the staff author and no "Gợi ý, cần kiểm tra" label (it is not an AI suggestion).
function MessageRow({ message }: { message: Message }) {
  const time = message.sentAt ? formatDateTime(message.sentAt) : undefined;
  if (message.direction === 'CUSTOMER') {
    return (
      <ChatMessage role="user" time={time}>
        <span className="ap-visually-hidden">{AUTHORS.CUSTOMER}: </span>
        <span style={{ overflowWrap: 'anywhere' }}>{message.text}</span>
      </ChatMessage>
    );
  }
  return (
    <ChatMessage role="assistant" author={AUTHORS[message.direction]} time={time} label={message.direction === 'STAFF' ? false : undefined}>
      <span style={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{message.text}</span>
    </ChatMessage>
  );
}

const QUOTE_CARD = {
  background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-card)', borderRadius: 'var(--radius-lg)',
  padding: 'var(--space-5)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)',
} as const;

function QuoteBox({ quote, onRetry }: { quote: Quote; onRetry: () => void }) {
  return (
    <section aria-labelledby="held-quote-title" style={QUOTE_CARD}>
      {quote.phase !== 'ready' && <h2 id="held-quote-title" style={{ margin: 0, font: 'var(--type-card-title)' }}>Báo giá của cuộc trò chuyện</h2>}
      {quote.phase === 'loading' && <Skeleton height={64} />}
      {quote.phase === 'none' && <p style={{ margin: 0, color: 'var(--text-secondary)', font: 'var(--type-body)' }}>Chưa có báo giá.</p>}
      {quote.phase === 'failed' && (quote.error instanceof ApiError && quote.error.status === 503 ? (
        // F6 "Tạm không xem được · 503 · F3": the audit row of this price read could not be written.
        <Alert tone="warning" icon="server-off" title="Tạm thời không hiển thị báo giá"
          action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={onRetry}>Thử lại</Button>}>
          Hệ thống chưa ghi được nhật ký cho lần xem này nên không hiện báo giá. Vui lòng thử lại.
          {referenceText(quote.error) && <span style={{ display: 'block' }}>{referenceText(quote.error)}</span>}
        </Alert>
      ) : (
        <Alert tone="error" title="Chưa hiển thị được báo giá"
          action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={onRetry}>Thử lại</Button>}>
          {busyOr(quote.error)}
          {referenceText(quote.error) && <span style={{ display: 'block' }}>{referenceText(quote.error)}</span>}
        </Alert>
      ))}
      {quote.phase === 'ready' && <QuoteDetails quote={quote.quote} />}
    </section>
  );
}

function QuoteDetails({ quote }: { quote: StaffQuote }) {
  const expired = quote.status === 'ISSUED' && quote.validUntil.getTime() <= Date.now();
  return (
    <>
      <div style={{ display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 'var(--space-2)' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-0-5)', minWidth: 0 }}>
          <h2 id="held-quote-title" style={{ margin: 0, font: 'var(--type-card-title)' }}>Báo giá <span className="ap-code">{quote.quoteRef}</span></h2>
          <span style={{ font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>Phiên bản {quote.version} · Hiệu lực đến {formatDateTime(quote.validUntil)}</span>
        </div>
        <Badge tone={ATTENTION_STATUSES.includes(quote.status) ? 'warning' : expired ? 'neutral' : 'info'} shape="pill" size="sm">
          {expired ? 'Hết hiệu lực' : staffQuoteStatusLabel(quote.status)}
        </Badge>
      </div>
      <ul aria-label={`Các dòng của báo giá ${quote.quoteRef}`} style={{ listStyle: 'none', margin: 0, padding: 0 }}>
        {quote.lines.map((line, i) => (
          <li key={i} style={{ display: 'flex', justifyContent: 'space-between', gap: 'var(--space-3)', font: 'var(--type-table)', fontVariantNumeric: 'tabular-nums',
            padding: 'var(--space-2) 0', borderTop: 'var(--border-width-default) solid var(--border-subtle)', borderBottom: i === quote.lines.length - 1 ? 'var(--border-width-default) solid var(--border-subtle)' : undefined }}>
            <span style={{ display: 'flex', flexDirection: 'column', minWidth: 0 }}>
              <span><span className="ap-code" style={{ overflowWrap: 'anywhere' }}>{line.sku}</span> × {line.quantity}</span>
              {/* Not on the artboard (it shows the line total only); staff need the unit price to answer the customer. */}
              <span style={{ font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>Đơn giá {formatMoney(line.unitPrice, quote.currency)}</span>
            </span>
            <span style={{ whiteSpace: 'nowrap' }}>{formatMoney(line.lineTotal, quote.currency)}</span>
          </li>
        ))}
      </ul>
      <div style={{ display: 'flex', justifyContent: 'space-between', gap: 'var(--space-3)', fontVariantNumeric: 'tabular-nums' }}>
        <span style={{ font: 'var(--type-label)' }}>Tạm tính</span>
        <span style={{ font: 'var(--type-card-title)', whiteSpace: 'nowrap' }}>{formatMoney(quote.total, quote.currency)}</span>
      </div>
      {quote.notes.length > 0 && (
        <ul style={{ margin: 0, paddingLeft: 'var(--space-5)', font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>
          {quote.notes.map((n, i) => <li key={i}>{n}</li>)}
        </ul>
      )}
      <div><Badge tone="restricted" size="sm" icon="lock">Chỉ xem, không sửa giá tại đây</Badge></div>
    </>
  );
}
