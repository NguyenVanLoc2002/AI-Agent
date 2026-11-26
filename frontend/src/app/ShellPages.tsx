import { Component, type ErrorInfo, type ReactNode } from 'react';
import { Button, EmptyState } from '../design-system';
import { PageState } from './PageState';

// FE-UI-P2 (P2-T03, P2-T08): what the shell shows instead of a screen.
// - Forbidden (SH-03): a screen of another role, opened by its address. The artboard copy of F6 (403); no reference,
//   because nothing was sent: the shell does not call that screen's API. The backend would refuse it anyway.
// - Not found (SH-04): an address that is no screen. Copy PROPOSED (no artboard words a missing page; writing.md
//   pattern "what happened + next step"), FE-UI-D15.
// - Render failure (SH-04, E6-Error): a component threw. Copy PROPOSED, FE-UI-D15. It does not claim that nothing
//   was saved: a screen can fail after a write succeeded (writing.md; review UX M3). The error is never shown on the
//   page. React itself still writes a caught render error to the browser console (review security M3, accepted risk:
//   ApiError messages are fixed; suppressing it needs React 19 root options, an owner decision).

export function ForbiddenScreen({ onHome }: { onHome: () => void }) {
  return <PageState kind="forbidden" action={<HomeButton onHome={onHome} />} />;
}

export function NotFoundScreen({ onHome }: { onHome: () => void }) {
  return (
    <PageState kind="not-found" title="Không tìm thấy trang"
      description="Đường dẫn không đúng hoặc trang không còn. Quay về trang chính để tiếp tục."
      action={<HomeButton onHome={onHome} />} />
  );
}

function HomeButton({ onHome }: { onHome: () => void }) {
  return <Button size="sm" variant="secondary" iconLeft="arrow-left" onClick={onHome}>Về trang chính</Button>;
}

interface BoundaryProps {
  children: ReactNode;
}

export class RenderErrorBoundary extends Component<BoundaryProps, { failed: boolean }> {
  state = { failed: false };

  static getDerivedStateFromError(): { failed: boolean } {
    return { failed: true };
  }

  componentDidCatch(_error: unknown, _info: ErrorInfo): void {
    // Nothing is sent or logged by the app (rule 10); see the note above about React's own console output.
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <EmptyState compact variant="error" title="Không hiển thị được trang này"
        description="Trang gặp lỗi khi hiển thị. Tải lại trang để xem trạng thái mới nhất; nếu vẫn lỗi, liên hệ bộ phận IT."
        action={<Button size="sm" variant="secondary" iconLeft="refresh-cw" onClick={() => window.location.reload()}>Tải lại trang</Button>} />
    );
  }
}
