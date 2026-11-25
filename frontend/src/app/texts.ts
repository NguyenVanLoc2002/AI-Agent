import { ApiError } from '../api/client';
import type { Role } from '../api/session';

// Screen copy shared by several screens (design-system/guidelines/writing.md: what happened, consequence,
// next step; only a reference code, never an HTTP status, endpoint or server message).

/** Role names as people read them. Not an authorization source. */
export const ROLE_LABELS: Record<Role, string> = {
  CUST: 'Khách hàng',
  SALES: 'Nhân viên kinh doanh',
  OPS: 'Vận hành hệ thống',
  SECREV: 'Kiểm tra bảo mật',
};

export const SESSION_EXPIRED = 'Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.';

/** Message for a failed read or write the screen does not handle in a more specific way. */
export function failureText(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.kind === 'timeout' || error.kind === 'network') {
      return 'Không kết nối được máy chủ. Vui lòng kiểm tra kết nối rồi thử lại.';
    }
    if (error.status === 429) {
      return 'Có quá nhiều yêu cầu. Vui lòng chờ một lát rồi thử lại.';
    }
  }
  return 'Hệ thống đang gặp sự cố. Vui lòng thử lại sau.';
}

export function referenceText(error: unknown): string | null {
  const reference = error instanceof ApiError ? error.reference : null;
  return reference === null ? null : `Mã tham chiếu: ${reference}`;
}

/** "23/11/2026 08:40" in the viewer's time zone. */
export function formatDateTime(value: Date): string {
  const parts = new Intl.DateTimeFormat('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
  }).formatToParts(value);
  const part = (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? '';
  return `${part('day')}/${part('month')}/${part('year')} ${part('hour')}:${part('minute')}`;
}
