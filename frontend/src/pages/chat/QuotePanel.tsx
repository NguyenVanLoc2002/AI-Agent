import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { confirmQuote, isPendingOutcome, readQuote, type QuoteCard, type QuoteStatus } from '../../api/quotes';
import { conversationMemory } from '../../app/conversationMemory';
import { formatMoney } from '../../app/money';
import { failureText, formatDateTime, referenceText } from '../../app/texts';
import { Alert, Badge, Button } from '../../design-system';

// F1 · CH-01 quote card and CH-03 confirmation states (UC-03/04, customer side; specs/50 §F1, UX-06 labels).
// The card is the backend's rendering (AC-03.2); the button confirms exactly this quote reference, version
// and token (UX-05). A confirmation whose answer did not arrive is never sent again from here (UX-08,
// ADR-009): the customer can only read the quote again. Reasons for a handoff are never shown (A-14).

export const CUSTOMER_STATUS_LABELS: Record<QuoteStatus, string | null> = {
  ISSUED: 'Chờ xác nhận',
  ORDER_SUBMITTING: 'Đang kiểm tra',
  ORDER_OUTCOME_UNKNOWN: 'Đang kiểm tra',
  CONFIRMATION_BLOCKED: 'Chờ nhân viên',
  ORDER_FAILED: 'Chờ nhân viên',
  DRAFT_ORDER_CREATED: 'Đã gửi yêu cầu đặt hàng',
  STAFF_APPROVED: 'Đã được duyệt',
  REJECTED: 'Không được duyệt',
  EXPIRED: 'Hết hiệu lực',
  SUPERSEDED: 'Đã có phiên bản mới',
  CANCELLED: 'Đã hủy',
  DRAFT: null,
  CONFIRMED: null,
};

const TONES: Partial<Record<QuoteStatus, 'info' | 'success' | 'warning' | 'error' | 'neutral'>> = {
  ISSUED: 'info', ORDER_SUBMITTING: 'warning', ORDER_OUTCOME_UNKNOWN: 'warning', CONFIRMATION_BLOCKED: 'warning',
  ORDER_FAILED: 'warning', DRAFT_ORDER_CREATED: 'success', STAFF_APPROVED: 'success', REJECTED: 'error',
};

export interface QuoteState {
  quoteRef: string;
  version: number;
  status: QuoteStatus;
  card: QuoteCard | null;
}

type Notice =
  | { kind: 'changed'; previousVersion: number }
  | { kind: 'expired' }
  | { kind: 'pending' }
  | { kind: 'unknown'; reference: string | null }
  | { kind: 'handed-off' }
  | { kind: 'created' }
  | { kind: 'failed'; text: string; reference: string | null }
  | { kind: 'not-found' };

interface Props {
  quote: QuoteState;
  onQuoteChange: (quote: QuoteState | null) => void;
  /** The confirmation handed the conversation to staff: the page reads the conversation state again. */
  onHandedOff: () => void;
  onUnauthenticated: () => void;
}

export function QuotePanel({ quote, onQuoteChange, onHandedOff, onUnauthenticated }: Props) {
  const [busy, setBusy] = useState<'confirm' | 'reload' | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const mounted = useRef(true);
  const section = useRef<HTMLElement>(null);
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  async function reload(after?: Notice) {
    setBusy('reload');
    try {
      const fresh = await readQuote(quote.quoteRef);
      if (!mounted.current) return;
      onQuoteChange({ quoteRef: fresh.quoteRef, version: fresh.version, status: fresh.status, card: fresh.card });
      setNotice(after ?? (isPendingOutcome(fresh.status) ? { kind: 'pending' } : null));
    } catch (error) {
      if (!mounted.current) return;
      if (error instanceof ApiError && error.unauthenticated) { onUnauthenticated(); return; }
      if (error instanceof ApiError && error.status === 404) {
        // UX review L5: a quote that is gone is not shown again after a reload.
        conversationMemory.forgetQuote();
        setNotice({ kind: 'not-found' });
        return;
      }
      setNotice({ kind: 'failed', text: `Chưa tải được báo giá. ${failureText(error)}`, reference: referenceText(error) });
    } finally {
      if (mounted.current) setBusy(null);
    }
  }

  async function confirm(card: QuoteCard) {
    if (busy !== null) return;
    setBusy('confirm');
    setNotice(null);
    try {
      const result = await confirmQuote(card);
      if (!mounted.current) return;
      // The card's token is spent: the quote is shown with its new status and no confirm control.
      onQuoteChange({ quoteRef: result.quoteRef, version: result.version, status: result.status, card: null });
      if (isPendingOutcome(result.status)) setNotice({ kind: 'pending' });
      else if (result.handedOff) setNotice({ kind: 'handed-off' });
      else if (result.status === 'DRAFT_ORDER_CREATED') setNotice({ kind: 'created' });
      if (result.handedOff) onHandedOff();
    } catch (error) {
      if (!mounted.current) return;
      if (error instanceof ApiError && error.unauthenticated) { onUnauthenticated(); return; }
      const code = error instanceof ApiError ? error.code : null;
      if (code === 'QUOTE_EXPIRED') {
        onQuoteChange({ ...quote, status: 'EXPIRED', card: null });
        setNotice({ kind: 'expired' });
      } else if (code === 'QUOTE_STALE_VERSION' || code === 'QUOTE_REPRICED') {
        await reload({ kind: 'changed', previousVersion: card.version });
      } else if (error instanceof ApiError && error.status === 404) {
        // The token of this card is no longer the current one (the quote was rendered again elsewhere):
        // read the quote, which renders a card with a fresh token if it can still be confirmed.
        await reload();
      } else if (error instanceof ApiError && !error.sent) {
        // The CSRF read failed: the confirmation itself was never sent (review L-3).
        setNotice({ kind: 'failed', text: `Chưa gửi được xác nhận. Chưa có thay đổi nào được ghi. ${failureText(error)}`, reference: referenceText(error) });
      } else if (error instanceof ApiError && error.kind === 'http' && error.status !== null && error.status < 500) {
        // 409 busy, 429, 403, 400: refused before anything changed; the same card can be confirmed again.
        setNotice({ kind: 'failed', text: confirmRefusal(error), reference: referenceText(error) });
      } else {
        // Timeout, network failure, 5xx or an answer this client cannot read: the request may have been
        // stored. Never send it again; the customer can read the latest state.
        onQuoteChange({ ...quote, card: null });
        setNotice({ kind: 'unknown', reference: referenceText(error) });
      }
    } finally {
      if (mounted.current) {
        setBusy(null);
        // UX review M2: the confirm button is gone or changed; focus moves to the quote and its notice.
        section.current?.focus();
      }
    }
  }

  const label = CUSTOMER_STATUS_LABELS[quote.status];
  const card = quote.card && quote.status === 'ISSUED' ? quote.card : null;
  const changed = notice?.kind === 'changed';

  return (
    <section ref={section} tabIndex={-1} aria-label={`Báo giá ${quote.quoteRef}`} style={{ outline: 'none',  background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-card)',
      borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-card)', padding: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, font: 'var(--type-card-title)' }}>Báo giá <span className="ap-code">{quote.quoteRef}</span></h2>
        {label && <Badge tone={TONES[quote.status] ?? 'neutral'} shape="pill">{label}</Badge>}
      </div>

      {notice && <QuoteNotice notice={notice} quote={quote} />}

      {card ? <CardBody card={card} /> : (
        <p style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>Phiên bản {quote.version}</p>
      )}

      {card && (
        <Button fullWidth loading={busy === 'confirm'} disabled={busy !== null} onClick={() => void confirm(card)}>
          {busy === 'confirm' ? 'Đang gửi xác nhận…' : changed ? `Xác nhận phiên bản ${card.version}` : 'Xác nhận báo giá'}
        </Button>
      )}

      {!card && (notice?.kind === 'pending' || notice?.kind === 'unknown' || isPendingOutcome(quote.status)) && (
        <Button variant="secondary" fullWidth iconLeft="refresh-cw" loading={busy === 'reload'} disabled={busy !== null} onClick={() => void reload()}>
          {busy === 'reload' ? 'Đang tải…' : 'Xem kết quả mới nhất'}
        </Button>
      )}
    </section>
  );
}

function confirmRefusal(error: ApiError): string {
  if (error.status === 429) return 'Bạn đã xác nhận quá nhiều lần. Vui lòng chờ một lát rồi thử lại. Chưa có thay đổi nào được ghi.';
  if (error.status === 403) return 'Tài khoản của bạn chưa được liên kết để đặt hàng. Vui lòng liên hệ cửa hàng. Chưa có thay đổi nào được ghi.';
  if (error.status === 409) return 'Hệ thống đang bận. Vui lòng thử lại sau ít giây. Chưa có thay đổi nào được ghi.';
  return 'Chưa gửi được xác nhận. Chưa có thay đổi nào được ghi. Vui lòng thử lại.';
}

function QuoteNotice({ notice, quote }: { notice: Notice; quote: QuoteState }) {
  switch (notice.kind) {
    case 'changed':
      return <Alert tone="warning" title="Báo giá đã thay đổi">Phiên bản {notice.previousVersion} không còn dùng được. Xem phiên bản {quote.version} bên dưới trước khi xác nhận.</Alert>;
    case 'expired':
      return <Alert tone="neutral" icon="timer-off" title="Hết hiệu lực">Báo giá này không còn dùng để đặt hàng. Nhắn yêu cầu trong khung chat để nhận báo giá mới.</Alert>;
    case 'pending':
      return (
        <Alert tone="warning" icon="circle-pause" title="Đang kiểm tra">
          Yêu cầu đặt hàng đã được ghi nhận. Nhân viên sẽ kiểm tra và liên hệ bạn; bạn không cần gửi lại.
          <span style={{ display: 'block' }}>Mã tham chiếu: {quote.quoteRef} · phiên bản {quote.version}</span>
        </Alert>
      );
    case 'unknown':
      return (
        <Alert tone="warning" icon="circle-pause" title="Chưa nhận được kết quả">
          Yêu cầu có thể đã được ghi nhận. Không cần gửi lại; bấm "Xem kết quả mới nhất" để kiểm tra.
          <span style={{ display: 'block' }}>Mã tham chiếu: {quote.quoteRef} · phiên bản {quote.version}</span>
          {notice.reference && <span style={{ display: 'block' }}>{notice.reference}</span>}
        </Alert>
      );
    case 'handed-off':
      return <Alert tone="info" title="Chuyển nhân viên">Yêu cầu xác nhận của bạn đã được chuyển cho nhân viên tư vấn. Nhân viên sẽ phản hồi trong khung chat này.</Alert>;
    case 'created':
      return <Alert tone="success" title="Đã gửi yêu cầu đặt hàng">Nhân viên sẽ kiểm tra và liên hệ bạn; bạn không cần gửi lại.</Alert>;
    case 'not-found':
      return <Alert tone="neutral" title="Không tìm thấy báo giá">Nhắn yêu cầu trong khung chat để nhận báo giá mới.</Alert>;
    case 'failed':
      return (
        <Alert tone="error" title="Chưa thực hiện được">
          {notice.text}
          {notice.reference && <span style={{ display: 'block' }}>{notice.reference}</span>}
        </Alert>
      );
  }
}

function CardBody({ card }: { card: QuoteCard }) {
  return (
    <>
      <p style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>
        Phiên bản {card.version} · Hiệu lực đến {formatDateTime(card.validUntil)}
      </p>
      <div style={{ overflowX: 'auto' }}>
      <table style={{ width: '100%', borderCollapse: 'collapse', font: 'var(--type-table)' }}>
        <caption style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>Các dòng của báo giá {card.quoteRef}</caption>
        <thead>
          <tr style={{ font: 'var(--type-table-header)', color: 'var(--text-secondary)', textAlign: 'left' }}>
            <th scope="col" style={{ padding: 'var(--space-1) 0' }}>SKU</th>
            <th scope="col" style={{ padding: 'var(--space-1) 0', textAlign: 'right' }}>Số lượng × Đơn giá</th>
            <th scope="col" style={{ padding: 'var(--space-1) 0', textAlign: 'right' }}>Thành tiền</th>
          </tr>
        </thead>
        <tbody>
          {card.lines.map((line, i) => (
            <tr key={i} style={{ borderTop: '1px solid var(--border-subtle)' }}>
              <td className="ap-code" style={{ padding: 'var(--space-2) 0', overflowWrap: 'anywhere' }}>{line.sku}</td>
              <td style={{ padding: 'var(--space-2) 0', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{line.quantity} × {formatMoney(line.unitPrice, card.currency)}</td>
              <td style={{ padding: 'var(--space-2) 0', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{formatMoney(line.lineTotal, card.currency)}</td>
            </tr>
          ))}
        </tbody>
        <tfoot>
          <tr style={{ borderTop: '1px solid var(--border-default)', fontWeight: 600 }}>
            <th scope="row" colSpan={2} style={{ padding: 'var(--space-2) 0', textAlign: 'left' }}>Tạm tính</th>
            <td style={{ padding: 'var(--space-2) 0', textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>{formatMoney(card.total, card.currency)}</td>
          </tr>
        </tfoot>
      </table>
      </div>
      {card.notes.length > 0 && (
        <ul style={{ margin: 0, paddingLeft: 'var(--space-5)', font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>
          {card.notes.map((note, i) => <li key={i}>{note}</li>)}
        </ul>
      )}
      <p style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>
        Giá do hệ thống lập. Trả lời "ok" trong khung chat không tạo đơn; chỉ nút bên dưới gửi yêu cầu đặt hàng cho phiên bản này.
      </p>
    </>
  );
}
