import type { ActorRole, EventType, SecurityEvent } from '../api/inspector';
import { ROLE_LABELS } from './texts';

// Labels of the Security Inspector (F4, A-70 4A/5A). The table shows the backend codes as they are; Vietnamese
// labels are for the filters only, so no meaning is invented for an outcome code. Not an authorization source.

export const EVENT_TYPE_LABELS: Record<EventType, string> = {
  ACCESS_DENIED: 'Từ chối truy cập',
  HANDOFF_CLAIMED: 'Nhận xử lý',
  HANDOFF_RESOLVED: 'Hoàn tất xử lý',
  STAFF_MESSAGE_POSTED: 'Nhân viên gửi tin',
  STAFF_QUOTE_READ: 'Nhân viên xem báo giá',
  STAFF_ORDER_DECIDED: 'Nhân viên quyết định đơn nháp',
  ORDER_RECONCILED: 'Đối soát đơn',
  QUOTE_CONFIRMATION: 'Khách xác nhận báo giá',
  INSPECTOR_READ: 'Xem sự kiện bảo mật',
  STAFF_CONVERSATION_READ: 'Nhân viên đọc hội thoại',
  STAFF_LIST_READ: 'Nhân viên xem danh sách',
  KILL_SWITCH_CHANGED: 'Đổi công tắc AI',
};

export type OutcomeTone = 'restricted' | 'success' | 'warning' | 'neutral';

/** Colour only, for the outcome codes whose meaning is known; every other code is neutral. */
export function outcomeTone(outcome: string): OutcomeTone {
  if (outcome === 'FORBIDDEN' || outcome === 'FORBIDDEN_RESOURCE') return 'restricted';
  if (outcome === 'ON') return 'success';
  if (outcome.startsWith('OFF_')) return 'warning';
  return 'neutral';
}

export function roleLabel(role: ActorRole): string {
  return ROLE_LABELS[role];
}

/** "Kinh doanh · usr_…" for staff, the role only for a customer, "Hệ thống" for the system actor. */
export function actorParts(event: SecurityEvent): { role: string; pseudonym: string | null } {
  if (event.actorRole === null) return { role: 'Hệ thống', pseudonym: null };
  return { role: roleLabel(event.actorRole), pseudonym: event.actorRole === 'CUST' ? null : event.actor };
}

/** "7c1e…a904": first 4 and last 4 characters; screen readers and the trace panel get the full ID. */
export function shortTrace(traceId: string): string {
  return `${traceId.slice(0, 4)}…${traceId.slice(-4)}`;
}

const two = (n: number) => String(n).padStart(2, '0');

/** "23/11/2026 08:40:12" in the viewer's time zone (security events need seconds). */
export function formatSeconds(value: Date): string {
  return `${two(value.getDate())}/${two(value.getMonth() + 1)}/${value.getFullYear()} ${two(value.getHours())}:${two(value.getMinutes())}:${two(value.getSeconds())}`;
}

/** Value of an `<input type="datetime-local">` in the viewer's time zone: "2026-11-23T08:40". */
export function toLocalInput(value: Date): string {
  return `${value.getFullYear()}-${two(value.getMonth() + 1)}-${two(value.getDate())}T${two(value.getHours())}:${two(value.getMinutes())}`;
}

const LOCAL_INPUT = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/;

/** A datetime-local value read as the viewer's local time; null when it is empty or not a date. */
export function fromLocalInput(value: string): Date | null {
  if (!LOCAL_INPUT.test(value)) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

/** "UTC+07:00": the viewer's offset, so the shown times can be matched with server logs (UX review L13). */
export function utcOffset(at: Date): string {
  const minutes = -at.getTimezoneOffset();
  const sign = minutes >= 0 ? '+' : '-';
  const abs = Math.abs(minutes);
  return `UTC${sign}${two(Math.floor(abs / 60))}:${two(abs % 60)}`;
}
