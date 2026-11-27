import { useCallback, useEffect, useRef, useState } from 'react';
import {
  createConversation, MAX_MESSAGE_CHARS, newClientEventId, postMessage, readMessages, readState,
  type ChatMessage as Message, type CustomerState,
} from '../api/chat';
import { ApiError } from '../api/client';
import { readQuote } from '../api/quotes';
import { conversationMemory } from '../app/conversationMemory';
import { PageState } from '../app/PageState';
import { useCustomerStatus, type CustomerStatus } from '../app/shells';
import { failureText, formatDateTime, referenceText } from '../app/texts';
import { AIComposer, Alert, Button, ChatMessage, Icon } from '../design-system';
import { QuotePanel, type QuoteState } from './chat/QuotePanel';

// F1 · CH-01..CH-04 customer chat over the local channel simulator (UC-01, UC-03/04 customer side, D-06;
// docs/design/specs/50-batch-f-man-hinh-thieu.md, prototypes F1-*.dc.html as behavior reference only).
// The backend owns every decision: who answers, whether a message is stored, whether a quote exists.
// This page shows what the backend returns and never shows why a conversation was handed off (A-14).
// FE-UI-P3: layout and copy of the approved artboards F1-Chat, F1-Handoff and F1-SendStates (390 px): the state badge
// sits in the customer header, the composer stays at the bottom of the screen.

/** Header badge per state (F1-Chat "Trợ lý AI đang trả lời", F1-Handoff "Chờ nhân viên"). */
const STATUS: Record<CustomerState, CustomerStatus> = {
  BOT: { tone: 'success', label: 'Trợ lý AI đang trả lời' },
  WAITING_STAFF: { tone: 'warning', label: 'Chờ nhân viên' },
  // Not drawn: F1-Handoff shows the "staff joined" divider under the same badge; this names the new state.
  STAFF: { tone: 'info', label: 'Nhân viên đang hỗ trợ' },
};

/** A-68 (4A): while staff handle the conversation, the page reads it again this often (visible tab only). */
export const STAFF_POLL_MS = 5_000;

type Phase =
  | { kind: 'starting' }
  | { kind: 'ready'; conversationId: string }
  | { kind: 'not-linked' }
  | { kind: 'failed'; error: unknown };

type SendNotice =
  | { kind: 'busy' }
  | { kind: 'rate' }
  | { kind: 'unknown'; reference: string | null }
  | { kind: 'invalid' }
  | { kind: 'restarted' }
  | { kind: 'card-unreadable' }
  | { kind: 'failed'; text: string; reference: string | null };

interface Props {
  customerLinked: boolean;
  onUnauthenticated: () => void;
}

export function CustomerChatPage({ customerLinked, onUnauthenticated }: Props) {
  const [phase, setPhase] = useState<Phase>(customerLinked ? { kind: 'starting' } : { kind: 'not-linked' });
  const [messages, setMessages] = useState<Message[]>([]);
  const [state, setState] = useState<CustomerState>('BOT');
  const [quote, setQuote] = useState<QuoteState | null>(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);
  const [notice, setNotice] = useState<SendNotice | null>(null);
  /** The event ID of a message whose answer did not arrive: a resend of the same text reuses it (AC-01.2). */
  const pendingEvent = useRef<{ id: string; text: string } | null>(null);
  const mounted = useRef(true);
  /** Writes to the tab memory carry the epoch of this page; after sign-out they are dropped (review M-1). */
  const epoch = useRef(conversationMemory.epoch());
  /** Only the latest read of the conversation is applied, and reads never overlap (review L-2). */
  const readSeq = useRef(0);
  const reading = useRef(false);
  const container = useRef<HTMLDivElement>(null);
  /** UX review M1: only a newly arrived bot or staff message is announced, not the whole list. */
  const [announcement, setAnnouncement] = useState('');
  const seen = useRef<number | null>(null);
  useCustomerStatus(phase.kind === 'ready' ? STATUS[state] : null);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  /** 401 → login; 403 not linked → explanation. True when the error was handled here. */
  const common = useCallback((error: unknown): boolean => {
    if (!(error instanceof ApiError)) return false;
    if (error.unauthenticated) { onUnauthenticated(); return true; }
    if (error.forbidden) { setPhase({ kind: 'not-linked' }); return true; }
    return false;
  }, [onUnauthenticated]);

  const refresh = useCallback(async (conversationId: string) => {
    const seq = ++readSeq.current;
    reading.current = true;
    try {
      const [fresh, current] = await Promise.all([readMessages(conversationId), readState(conversationId)]);
      if (!mounted.current || seq !== readSeq.current) return;
      setMessages(fresh);
      setState(current);
    } finally {
      if (seq === readSeq.current) reading.current = false;
    }
  }, []);

  const start = useCallback(async () => {
    setPhase({ kind: 'starting' });
    try {
      let conversationId = conversationMemory.conversationId();
      if (conversationId) {
        try {
          await refresh(conversationId);
        } catch (error) {
          if (!(error instanceof ApiError && error.status === 404)) throw error;
          conversationMemory.forgetConversation();
          conversationId = null;
        }
      }
      if (!conversationId) {
        conversationId = await createConversation();
        if (!mounted.current) return;
        conversationMemory.rememberConversation(conversationId, epoch.current);
        setMessages([]);
        setState('BOT');
      }
      const quoteRef = conversationMemory.quoteRef();
      if (quoteRef) {
        try {
          const found = await readQuote(quoteRef);
          if (mounted.current) setQuote({ quoteRef: found.quoteRef, version: found.version, status: found.status, card: found.card });
        } catch (error) {
          if (error instanceof ApiError && error.unauthenticated) throw error;
          // The quote cannot be shown again (gone or unreadable); the chat still works.
          conversationMemory.forgetQuote();
        }
      }
      if (mounted.current) setPhase({ kind: 'ready', conversationId });
    } catch (error) {
      if (mounted.current && !common(error)) setPhase({ kind: 'failed', error });
    }
  }, [common, refresh]);

  useEffect(() => {
    if (customerLinked) void start();
  }, [customerLinked, start]);

  useEffect(() => {
    if (phase.kind !== 'ready') return;
    const previous = seen.current;
    seen.current = messages.length;
    const last = messages[messages.length - 1];
    if (previous === null || messages.length <= previous || !last || last.direction === 'CUSTOMER') return;
    setAnnouncement(`${last.direction === 'STAFF' ? 'Nhân viên tư vấn' : 'Trợ lý AI'}: ${last.text}`);
  }, [messages, phase.kind]);

  // A-68 (4A): staff replies arrive by reading again, only while staff handle the conversation.
  const conversationId = phase.kind === 'ready' ? phase.conversationId : null;
  useEffect(() => {
    if (!conversationId || state === 'BOT') return undefined;
    const timer = setInterval(() => {
      if (document.visibilityState !== 'visible' || sending || reading.current) return;
      refresh(conversationId).catch((error: unknown) => {
        if (!mounted.current || common(error)) return;
        if (error instanceof ApiError && error.status === 404) {
          // The conversation is no longer this customer's: stop reading it and open a new one (review L-1).
          conversationMemory.forgetConversation();
          setQuote(null);
          void start();
        }
        // Other failures: the next tick tries again; the last good view stays on screen.
      });
    }, STAFF_POLL_MS);
    return () => clearInterval(timer);
  }, [conversationId, state, sending, refresh, common, start]);

  async function send(text: string) {
    if (phase.kind !== 'ready' || sending) return;
    const trimmed = text.trim();
    if (trimmed === '' || trimmed.length > MAX_MESSAGE_CHARS) return;
    const event = pendingEvent.current && pendingEvent.current.text === trimmed
      ? pendingEvent.current
      : { id: newClientEventId(), text: trimmed };
    pendingEvent.current = event;
    setSending(true);
    setNotice(null);
    try {
      const turn = await postMessage(phase.conversationId, event.id, trimmed);
      if (!mounted.current) return;
      pendingEvent.current = null;
      setDraft('');
      // UX review M2: keyboard focus returns to the message box after a send.
      container.current?.querySelector<HTMLTextAreaElement>('textarea')?.focus();
      if (turn.card) {
        setQuote({ quoteRef: turn.card.quoteRef, version: turn.card.version, status: 'ISSUED', card: turn.card });
        conversationMemory.rememberQuote(turn.card.quoteRef, epoch.current);
      }
      if (turn.cardUnreadable) setNotice({ kind: 'card-unreadable' });
      try {
        await refresh(phase.conversationId);
      } catch {
        // The answer arrived but the history could not be read again: show it locally.
        setMessages((current) => [...current, { direction: 'CUSTOMER', text: trimmed, sentAt: null }, { direction: 'BOT', text: turn.reply, sentAt: null }]);
        if (turn.handedOff) setState('WAITING_STAFF');
      }
    } catch (error) {
      if (!mounted.current) return;
      if (common(error)) return;
      if (error instanceof ApiError && error.status === 404) {
        // The conversation is gone for this customer: open a new one and keep what they typed.
        pendingEvent.current = null;
        conversationMemory.forgetConversation();
        setQuote(null);
        await start();
        setNotice({ kind: 'restarted' });
      } else if (error instanceof ApiError && error.status === 409) {
        setNotice({ kind: 'busy' });
      } else if (error instanceof ApiError && error.status === 429) {
        setNotice({ kind: 'rate' });
      } else if (error instanceof ApiError && (error.status === 400 || error.status === 413)) {
        pendingEvent.current = null;
        setNotice({ kind: 'invalid' });
      } else if (error instanceof ApiError && error.kind === 'http' && error.status !== null && error.status < 500) {
        pendingEvent.current = null;
        setNotice({ kind: 'failed', text: failureText(error), reference: referenceText(error) });
      } else {
        // Timeout, network failure, 5xx or an unreadable answer: the message may be stored. The same
        // event ID is kept, so "Gửi lại" cannot make the backend handle it twice (AC-01.2).
        setNotice({ kind: 'unknown', reference: referenceText(error) });
      }
    } finally {
      if (mounted.current) setSending(false);
    }
  }

  const staff = state !== 'BOT';
  return (
    <div ref={container} style={{ flex: '1 1 auto', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
      {/* The F1 artboard has no page title (the brand is in the header); the page heading is for screen readers. */}
      <h1 className="ap-visually-hidden">Tư vấn phụ tùng</h1>
      <PhaseView phase={phase} onRetry={() => void start()} />
      {phase.kind === 'ready' && (
        <>
          <StateBanner state={state} />
          <p role="status" style={VISUALLY_HIDDEN}>{announcement}</p>
          <ol aria-label="Tin nhắn" style={{ listStyle: 'none', margin: 0, padding: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
            {messages.length === 0 && !sending && (
              <li style={{ font: 'var(--type-body)', color: 'var(--text-secondary)', textAlign: 'center' }}>
                Hãy nhắn mã OE, mã phụ tùng hoặc mô tả phụ tùng bạn cần.
              </li>
            )}
            {messages.map((message, i) => <li key={i}><MessageView message={message} /></li>)}
            {sending && state === 'BOT' && (
              <li><ChatMessage role="assistant" status="thinking" label={false}>Đang tra cứu…</ChatMessage></li>
            )}
          </ol>
          {quote && <QuotePanel key={quote.quoteRef} quote={quote} onQuoteChange={(next) => setQuote(next)} onUnauthenticated={onUnauthenticated}
            onHandedOff={() => { refresh(phase.conversationId).catch(() => setState('WAITING_STAFF')); }} />}
          <div className="ap-customer__composer">
            {notice && <SendNoticeView notice={notice} onResend={() => void send(pendingEvent.current?.text ?? draft)} sending={sending} onDismiss={() => setNotice(null)} />}
            <AIComposer value={draft} onChange={setDraft} onSubmit={(text) => void send(text)} busy={sending}
              maxLength={MAX_MESSAGE_CHARS} label="Nội dung tin nhắn"
              placeholder={staff ? 'Nhắn cho nhân viên tư vấn…' : 'Nhập mã OE, số VIN hoặc câu hỏi…'}
              disclaimer={sending ? 'Đang gửi… Nút gửi khóa cho đến khi có phản hồi.'
                : staff ? 'Tối đa 2.000 ký tự. Tin nhắn được lưu cho nhân viên; trợ lý AI không trả lời cho đến khi nhân viên hoàn tất.'
                  : 'Tối đa 2.000 ký tự. Không gửi mật khẩu, số thẻ hay thông tin cá nhân không cần thiết.'} />
            {sending && state !== 'BOT' && <p role="status" style={VISUALLY_HIDDEN}>Đang gửi…</p>}
          </div>
        </>
      )}
    </div>
  );
}

const VISUALLY_HIDDEN = { position: 'absolute', width: 1, height: 1, margin: -1, padding: 0, overflow: 'hidden', clip: 'rect(0 0 0 0)', whiteSpace: 'nowrap', border: 0 } as const;

function PhaseView({ phase, onRetry }: { phase: Phase; onRetry: () => void }) {
  if (phase.kind === 'starting') {
    return <PageState kind="loading" loadingLabel="Đang mở cuộc trò chuyện" />;
  }
  if (phase.kind === 'not-linked') {
    return (
      <PageState kind="forbidden" title="Bạn chưa dùng được kênh chat này"
        description="Tài khoản của bạn chưa được liên kết với một khách hàng. Vui lòng liên hệ cửa hàng để được hỗ trợ." />
    );
  }
  if (phase.kind === 'failed') {
    const timeout = phase.error instanceof ApiError && phase.error.kind === 'timeout';
    return (
      <PageState kind={timeout ? 'timeout' : 'error'} error={phase.error} title="Chưa mở được cuộc trò chuyện"
        description={failureText(phase.error)}
        action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={onRetry}>Thử lại</Button>} />
    );
  }
  return null;
}

function StateBanner({ state }: { state: CustomerState }) {
  if (state === 'WAITING_STAFF') {
    return (
      <Alert tone="info" icon="circle-pause" title="Đang chờ nhân viên tư vấn">
        Trợ lý AI tạm dừng trong cuộc trò chuyện này. Bạn vẫn có thể nhắn; nhân viên sẽ đọc khi tiếp nhận.
      </Alert>
    );
  }
  if (state === 'STAFF') {
    return (
      <Alert tone="info" icon="user" title="Nhân viên tư vấn đã tham gia">
        Tin nhắn được lưu cho nhân viên; trợ lý AI không trả lời cho đến khi nhân viên hoàn tất.
      </Alert>
    );
  }
  return null;
}

function messageTime(sentAt: Date | null): string | undefined {
  if (!sentAt) return undefined;
  const now = new Date();
  const full = formatDateTime(sentAt);
  return sentAt.toDateString() === now.toDateString() ? full.slice(-5) : full;
}

function MessageView({ message }: { message: Message }) {
  const time = messageTime(message.sentAt);
  if (message.direction === 'CUSTOMER') {
    // F1 artboard author "Bạn": the user bubble shows no author, so it is given to screen readers.
    return <ChatMessage role="user" time={time}><span className="ap-visually-hidden">Bạn: </span><span>{message.text}</span></ChatMessage>;
  }
  if (message.direction === 'BOT') {
    return <ChatMessage role="assistant" time={time}>{message.text}</ChatMessage>;
  }
  return (
    <div style={{ display: 'flex', gap: 10 }}>
      <span aria-hidden="true" style={{ width: 26, height: 26, borderRadius: 'var(--radius-md)', flexShrink: 0, background: 'var(--bg-muted)',
        border: '1px solid var(--border-default)', color: 'var(--text-secondary)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
        <Icon name="user" size={16} />
      </span>
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, font: '600 12px/1.4 var(--font-sans)', color: 'var(--text-primary)' }}>
          Nhân viên tư vấn
          {time && <span style={{ marginLeft: 'auto', font: '400 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)' }}>{time}</span>}
        </div>
        <div style={{ font: '400 14px/1.6 var(--font-sans)', color: 'var(--text-primary)', whiteSpace: 'pre-wrap' }}>{message.text}</div>
      </div>
    </div>
  );
}

function SendNoticeView({ notice, onResend, sending, onDismiss }: { notice: SendNotice; onResend: () => void; sending: boolean; onDismiss: () => void }) {
  switch (notice.kind) {
    case 'busy':
      return <Alert tone="info" icon="clock" title="Tin trước đang được xử lý" onDismiss={onDismiss}>Vui lòng chờ câu trả lời rồi gửi tiếp. Nội dung bạn đang gõ vẫn được giữ.</Alert>;
    case 'rate':
      return <Alert tone="warning" title="Bạn đã gửi nhiều yêu cầu trong thời gian ngắn" onDismiss={onDismiss}>Vui lòng thử lại sau ít phút. Chưa có tin nào bị mất.</Alert>;
    case 'unknown':
      return (
        <Alert tone="warning" icon="timer-off" title="Chưa nhận được phản hồi"
          action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" loading={sending} onClick={onResend}>Gửi lại</Button>}>
          Tin của bạn có thể đã được ghi nhận. Bấm Gửi lại để gửi lại đúng tin đó; hệ thống không xử lý trùng một tin.
          {notice.reference && <span style={{ display: 'block' }}>{notice.reference}</span>}
        </Alert>
      );
    case 'invalid':
      return <Alert tone="error" title="Tin nhắn không gửi được" onDismiss={onDismiss}>Tin nhắn trống hoặc quá dài. Vui lòng sửa rồi gửi lại.</Alert>;
    case 'restarted':
      return <Alert tone="info" title="Không tìm thấy cuộc trò chuyện" onDismiss={onDismiss}>Đã mở cuộc trò chuyện mới. Nội dung bạn đang gõ vẫn được giữ; bấm gửi để tiếp tục.</Alert>;
    case 'card-unreadable':
      return <Alert tone="warning" title="Chưa hiển thị được báo giá" onDismiss={onDismiss}>Vui lòng nhắn lại yêu cầu để nhận báo giá, hoặc chờ nhân viên hỗ trợ.</Alert>;
    case 'failed':
      return (
        <Alert tone="error" title="Chưa gửi được tin nhắn" onDismiss={onDismiss}>
          {notice.text}
          {notice.reference && <span style={{ display: 'block' }}>{notice.reference}</span>}
        </Alert>
      );
  }
}
