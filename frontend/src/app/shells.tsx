import { useEffect, useRef, useState, type MouseEvent, type ReactNode, type RefObject } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { AppHeader, Button, Sidebar } from '../design-system';
import { below } from './breakpoints';
import { APP_NAME, type ScreenRoute } from './routes';
import { useMediaQuery } from './useMediaQuery';

// FE-UI-P2 (P2-T04, P2-T05, P2-T13). Staff shell SH-01 as the approved batch F artboards (F2–F5): Sidebar with the
// role's group as section title, AppHeader with breadcrumb group › screen, main area that scrolls by itself, page
// body .ap-page. Below 1280 px the sidebar starts collapsed (docs/design/specs/01 T13; FE-UI-D08); the user can
// still open it. Staff widths below 1024 px have no artboard (PROPOSED). The customer shell keeps the EP-10b layout:
// no sidebar, one centred column that works at 390 px (F1).

export const MAIN_ID = 'noi-dung-chinh';

interface ShellProps {
  /** Role label as people read it; the session has no person name. */
  userLabel: string;
  onSignOut: () => void;
  children: ReactNode;
}

interface StaffShellProps extends ShellProps {
  routes: ScreenRoute[];
  current: ScreenRoute | null;
  /** The role's group, for the breadcrumb of the no-access and not-found screens. */
  group: string;
}

export function StaffShell({ routes, current, group, userLabel, onSignOut, children }: StaffShellProps) {
  const navigate = useNavigate();
  const narrow = useMediaQuery(below('panelPush'), false);
  const [choice, setChoice] = useState<boolean | null>(null);
  // Crossing 1280 px returns to that width's default; in between the user's choice holds.
  useEffect(() => setChoice(null), [narrow]);
  const collapsed = choice ?? narrow;
  const main = useRef<HTMLElement>(null);
  useFocusOnRouteChange(main);

  const sections = [{ title: group, items: routes.map((r) => ({ key: r.key, label: r.label, icon: r.icon ?? 'circle' })) }];
  const breadcrumb = [{ label: group }, ...(current ? [{ label: current.label }] : [])];

  return (
    <div className="ap-shell">
      <SkipLink target={main} />
      <Sidebar sections={sections} activeKey={current?.key} brand="AutoParts Agent" env="Demo"
        onSelect={(key) => { const r = routes.find((x) => x.key === key); if (r) navigate(r.path); }}
        collapsed={collapsed} onToggleCollapsed={() => setChoice(!collapsed)} />
      <div className="ap-shell__body">
        <AppHeader breadcrumb={breadcrumb} actions={<SignOutButton onSignOut={onSignOut} />}
          user={{ name: userLabel, initials: initials(userLabel) }} />
        <main id={MAIN_ID} ref={main} tabIndex={-1} aria-label="Nội dung chính" className="ap-shell__main">
          <div className="ap-page">{children}</div>
        </main>
      </div>
    </div>
  );
}

export function CustomerShell({ userLabel, onSignOut, children }: ShellProps) {
  const main = useRef<HTMLElement>(null);
  useFocusOnRouteChange(main);
  return (
    <div className="ap-customer">
      <SkipLink target={main} />
      <AppHeader breadcrumb={[{ label: APP_NAME }]} actions={<SignOutButton onSignOut={onSignOut} />}
        user={{ name: userLabel, initials: initials(userLabel) }} style={{ padding: '0 var(--space-4)' }} />
      <main id={MAIN_ID} ref={main} tabIndex={-1} aria-label="Nội dung chính" className="ap-page--customer">{children}</main>
    </div>
  );
}

function SignOutButton({ onSignOut }: { onSignOut: () => void }) {
  return <Button variant="ghost" size="sm" iconLeft="log-out" onClick={onSignOut}>Đăng xuất</Button>;
}

// WCAG 2.4.1. With hash routes a "#id" link would be read by the router as a page change, so the link moves focus
// itself (the href keeps it a real link for assistive technology).
function SkipLink({ target }: { target: RefObject<HTMLElement | null> }) {
  const go = (event: MouseEvent<HTMLAnchorElement>) => {
    event.preventDefault();
    target.current?.focus();
  };
  return <a className="ap-skip-link" href={`#${MAIN_ID}`} onClick={go}>Bỏ qua tới nội dung chính</a>;
}

// After a route change focus goes to the page's h1, or to main, so keyboard and screen-reader users start at the new
// page (P1 usage rule for PageHeader). On the first render only when focus is lost (on <body>): after signing in the
// focused sign-in button is gone, and Chromium would continue Tab from where it was (WCAG 2.4.3).
function useFocusOnRouteChange(main: RefObject<HTMLElement | null>) {
  const { pathname } = useLocation();
  const first = useRef(true);
  useEffect(() => {
    const initial = first.current;
    first.current = false;
    if (initial && document.activeElement !== null && document.activeElement !== document.body) return;
    const node = main.current;
    if (!node) return;
    const heading = node.querySelector<HTMLElement>('h1');
    if (heading) {
      if (!heading.hasAttribute('tabindex')) heading.setAttribute('tabindex', '-1');
      heading.focus();
    } else {
      node.focus();
    }
  }, [pathname, main]);
}

function initials(label: string): string {
  return label.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w.charAt(0).toUpperCase()).join('');
}
