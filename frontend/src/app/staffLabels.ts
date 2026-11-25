import type { QuoteStatus } from '../api/quotes';

// Staff-facing labels (docs/design/specs/50-batch-f-man-hinh-thieu.md, approved 2026-11-23: UX-17 handoff
// reasons, UX-06 staff column). Reasons are internal codes for staff only; the customer never sees them
// (A-14). A code this client does not know is shown neutrally, never dropped.

export const HANDOFF_REASON_LABELS: Record<string, string> = {
  DLP_INBOUND: 'Tin nhắn chứa dữ liệu nhạy cảm',
  DLP_EGRESS: 'Chặn dữ liệu gửi ra',
  PROMPT_INJECTION: 'Nghi chèn lệnh',
  TOOL_REJECTED_REPEATED: 'Yêu cầu công cụ bị từ chối',
  PROVIDER_ERROR: 'Dịch vụ AI gián đoạn',
  PROVIDER_AUTH: 'Nhà cung cấp AI từ chối khóa',
  CATALOG_UNAVAILABLE: 'Danh mục gián đoạn',
  QUOTE_NOT_ISSUED: 'Không lập được báo giá',
  CUSTOMER_REQUEST: 'Khách yêu cầu nhân viên',
  UNSURE: 'Trợ lý AI không chắc chắn',
  OUT_OF_SCOPE: 'Ngoài phạm vi hỗ trợ',
  OUTPUT_REJECTED: 'Câu trả lời bị chặn',
  AGENT_LIMIT: 'Vượt giới hạn lượt xử lý',
  ERP_UNKNOWN: 'Chưa rõ kết quả ERP',
  ORDER_NOT_CREATED: 'Chưa tạo được đơn',
  AI_DISABLED: 'Trợ lý AI đang tắt',
};

export function reasonLabel(code: string): string {
  return Object.prototype.hasOwnProperty.call(HANDOFF_REASON_LABELS, code) ? HANDOFF_REASON_LABELS[code] as string : 'Lý do khác';
}

export const STAFF_QUOTE_STATUS_LABELS: Record<QuoteStatus, string> = {
  ISSUED: 'Đã gửi khách',
  ORDER_SUBMITTING: 'Chưa rõ kết quả',
  ORDER_OUTCOME_UNKNOWN: 'Chưa rõ kết quả',
  CONFIRMATION_BLOCKED: 'Cần nhân viên xử lý',
  ORDER_FAILED: 'Cần nhân viên xử lý',
  DRAFT_ORDER_CREATED: 'Đã tạo đơn nháp',
  STAFF_APPROVED: 'Đã duyệt',
  REJECTED: 'Bị từ chối',
  EXPIRED: 'Hết hiệu lực',
  SUPERSEDED: 'Đã có phiên bản mới',
  CANCELLED: 'Đã hủy',
  DRAFT: 'Đang xử lý',
  CONFIRMED: 'Đang xử lý',
};

export function staffQuoteStatusLabel(status: QuoteStatus): string {
  return STAFF_QUOTE_STATUS_LABELS[status] ?? 'Trạng thái khác';
}

/** Statuses that need a staff member's attention (shown with a warning tone). */
export const ATTENTION_STATUSES: readonly QuoteStatus[] = ['ORDER_SUBMITTING', 'ORDER_OUTCOME_UNKNOWN', 'CONFIRMATION_BLOCKED', 'ORDER_FAILED'];

/** "…a1B2c3": the last characters of the opaque conversation ID, enough to tell rows apart. */
export function shortConversation(conversationId: string): string {
  return `…${conversationId.slice(-6)}`;
}
