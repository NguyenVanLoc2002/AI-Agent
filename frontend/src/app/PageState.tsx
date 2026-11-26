import type { ReactNode } from 'react';
import { ApiError } from '../api/client';
import { EmptyState, Skeleton } from '../design-system';
import { failureText } from './texts';

// FE-UI-P2 (P2-T06): the shared page states of the approved artboard F6-StaffStates (docs/design/specs/50 §F6):
// EmptyState `compact` with the artboard's variant per state. A 409 conflict is not a page state there but a
// warning toast (see toast.tsx). Default copy is the artboard's; a page passes its own title/description where the
// artboard words it per screen (empty, not found). The reference is the server trace ID only (ApiError.reference,
// "REQ-…"); never an HTTP status, endpoint or server message (rule 10, writing.md). Forbidden is grey with a lock.

export type PageStateKind = 'loading' | 'empty' | 'forbidden' | 'not-found' | 'timeout' | 'error';

interface Common {
  /** Icon for the empty state (the artboard uses the screen's sidebar icon, e.g. "users" for F2). */
  icon?: string;
  /** The failure behind the state; only its reference code is shown. */
  error?: unknown;
  action?: ReactNode;
  /** Screen-reader text while loading. */
  loadingLabel?: string;
}

// Empty and not-found are worded per screen on the artboard, and EmptyState would otherwise fall back to its
// batch-A presets ("…mã OE, SKU…"): the page must pass both texts.
type Props = Common & (
  | { kind: 'empty' | 'not-found'; title: string; description: string }
  | { kind: 'loading' | 'forbidden' | 'timeout' | 'error'; title?: string; description?: string }
);

type Variant = 'empty' | 'unauthorized' | 'no-results' | 'timeout' | 'error';

const DEFAULTS: Record<Exclude<PageStateKind, 'loading'>, { variant: Variant; title: string; description?: string }> = {
  empty: { variant: 'empty', title: 'Chưa có dữ liệu' },
  forbidden: {
    variant: 'unauthorized', title: 'Bạn không có quyền xem trang này',
    description: 'Trang này dành cho nhóm khác. Liên hệ người quản lý tài khoản nếu bạn cần truy cập.',
  },
  'not-found': { variant: 'no-results', title: 'Không tìm thấy' },
  timeout: { variant: 'timeout', title: 'Yêu cầu quá thời gian', description: 'Chưa có thay đổi nào được ghi. Vui lòng thử lại.' },
  error: { variant: 'error', title: 'Không tải được dữ liệu' },
};

export function PageState(props: Props) {
  const { kind, title, description, icon, error, action, loadingLabel = 'Đang tải…' } = props;
  if (kind === 'loading') {
    return (
      <div role="status" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' }}>
        <span className="ap-visually-hidden">{loadingLabel}</span>
        <Skeleton height={20} width="40%" />
        {/* Single skeletons: `lines` would add a second role=status inside this one. */}
        <Skeleton height={44} />
        <Skeleton height={44} />
        <Skeleton height={44} />
      </div>
    );
  }
  const d = DEFAULTS[kind];
  const code = error instanceof ApiError ? error.reference ?? undefined : undefined;
  const text = description ?? d.description ?? (kind === 'error' ? failureText(error) : undefined);
  return <EmptyState compact variant={d.variant} icon={icon} title={title ?? d.title} description={text} code={code} action={action} />;
}
