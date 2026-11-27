import { useCallback, useEffect, useRef, useState } from 'react';
import { HashRouter, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { currentSession, logout, type Session } from '../api/session';
import { Alert, Button, Spinner } from '../design-system';
import { CustomerChatPage } from '../pages/CustomerChatPage';
import { KillSwitchPage } from '../pages/KillSwitchPage';
import { SecurityInspectorPage } from '../pages/inspector/SecurityInspectorPage';
import { StaffWorkspace } from '../pages/staff/StaffWorkspace';
import { LoginPage } from '../pages/LoginPage';
import { conversationMemory } from './conversationMemory';
import { documentTitle, homeRoute, resolve, routesFor, type ScreenKey } from './routes';
import { ForbiddenScreen, NotFoundScreen, RenderErrorBoundary } from './ShellPages';
import { CustomerShell, StaffShell } from './shells';
import { failureText, referenceText, ROLE_LABELS, SESSION_EXPIRED } from './texts';
import { ToastProvider } from './toast';

// Application root (EP-10a; FE-UI-P2, A-80). Hash routes (D01) with react-router-dom's HashRouter (D02); no
// conversation or order ID in the address (D04: the held conversation F3 opens in memory from the inbox). The screen
// offered per role is presentation only: every API call is authorized again by the backend (CLAUDE.md, rule 10).
// A screen of another role opened by its address shows "no access" without calling that screen's API.

type Boot =
  | { phase: 'checking' }
  | { phase: 'signing-out' }
  | { phase: 'error'; error: unknown }
  | { phase: 'anonymous'; notice: string | null }
  | { phase: 'signed-in'; session: Session };

export function App() {
  return (
    <HashRouter>
      <RenderErrorBoundary>
        <Root />
      </RenderErrorBoundary>
    </HashRouter>
  );
}

function Root() {
  const [boot, setBoot] = useState<Boot>({ phase: 'checking' });
  const navigate = useNavigate();
  const errorBox = useRef<HTMLDivElement>(null);
  // P2 review UX M2: "Thử lại" disappears while checking; when the check fails again, focus returns to the message.
  useEffect(() => {
    if (boot.phase === 'error' && (document.activeElement === null || document.activeElement === document.body)) errorBox.current?.focus();
  }, [boot.phase]);

  const check = useCallback(async () => {
    setBoot({ phase: 'checking' });
    try {
      const session = await currentSession();
      setBoot(session ? { phase: 'signed-in', session } : { phase: 'anonymous', notice: null });
    } catch (error) {
      setBoot({ phase: 'error', error });
    }
  }, []);

  useEffect(() => { void check(); }, [check]);

  // Any 401 (P2-T09): forget what the tab remembers and show the sign-in page. The address stays, so the same person
  // returns to the same screen after signing in again; nothing is resent (writing.md: no write continues by itself).
  const expired = useCallback(() => {
    conversationMemory.reset();
    setBoot({ phase: 'anonymous', notice: SESSION_EXPIRED });
  }, []);

  const signOut = useCallback(async () => {
    conversationMemory.reset();
    // Nothing of the signed-in screens (prices, messages) stays visible while the logout answer is awaited.
    setBoot({ phase: 'signing-out' });
    navigate('/', { replace: true });
    let notice = 'Bạn đã đăng xuất.';
    try {
      await logout();
    } catch {
      // The server session may still exist until it times out; the screen leaves it either way and says so.
      notice = 'Chưa xác nhận được việc đăng xuất với máy chủ. Phiên có thể còn hiệu lực tới khi hết hạn. Nếu dùng máy chung, hãy đóng trình duyệt.';
    }
    // Again after the logout answer: a page answer that arrived meanwhile is from an old epoch anyway.
    conversationMemory.reset();
    setBoot({ phase: 'anonymous', notice });
  }, [navigate]);

  useEffect(() => {
    if (boot.phase !== 'signed-in') document.title = documentTitle(boot.phase === 'anonymous' ? 'Đăng nhập' : null);
  }, [boot.phase]);

  switch (boot.phase) {
    case 'signing-out':
      return (
        <main style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 'var(--space-2)', color: 'var(--text-secondary)' }}>
          <Spinner label="Đang đăng xuất" /> Đang đăng xuất…
        </main>
      );
    case 'checking':
      return (
        <main style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 'var(--space-2)', color: 'var(--text-secondary)' }}>
          <Spinner label="Đang kiểm tra phiên đăng nhập" /> Đang kiểm tra phiên đăng nhập…
        </main>
      );
    case 'error':
      return (
        <main style={{ maxWidth: 520, margin: '0 auto', padding: 'var(--space-6) var(--space-4)' }}>
          <div ref={errorBox} tabIndex={-1} style={{ outline: 'none' }}>
          <Alert tone="error" title="Chưa kiểm tra được phiên đăng nhập"
            action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={() => void check()}>Thử lại</Button>}>
            {failureText(boot.error)}
            {referenceText(boot.error) && <span style={{ display: 'block' }}>{referenceText(boot.error)}</span>}
          </Alert>
          </div>
        </main>
      );
    case 'anonymous':
      return <LoginPage notice={boot.notice} onLoggedIn={(session) => {
        // A new sign-in never inherits what an earlier user of this tab left behind (review M-1).
        conversationMemory.reset();
        setBoot({ phase: 'signed-in', session });
      }} />;
    case 'signed-in':
      return <SignedIn session={boot.session} onSignOut={() => void signOut()} onUnauthenticated={expired} />;
  }
}

function SignedIn({ session, onSignOut, onUnauthenticated }: { session: Session; onSignOut: () => void; onUnauthenticated: () => void }) {
  const { pathname, search } = useLocation();
  const navigate = useNavigate();
  const home = homeRoute(session.role);
  // No screen takes a query (D04: no IDs in the address); an address with one is not a screen (review security L3).
  const resolution = search === '' ? resolve(pathname, session.role) : { kind: 'not-found' as const };
  const current = resolution.kind === 'screen' ? resolution.route : null;
  const userLabel = ROLE_LABELS[session.role];

  useEffect(() => {
    document.title = documentTitle(current ? current.label : resolution.kind === 'forbidden' ? 'Không có quyền' : 'Không tìm thấy trang');
  }, [current, resolution.kind]);

  if (resolution.kind === 'home') return <Navigate to={home.path} replace />;

  const goHome = () => navigate(home.path);
  const content = current
    ? <Screen screen={current.key} session={session} onUnauthenticated={onUnauthenticated} />
    : resolution.kind === 'forbidden' ? <ForbiddenScreen onHome={goHome} /> : <NotFoundScreen onHome={goHome} />;
  // A render failure of one screen keeps the shell (navigation, sign-out) usable; a new address starts clean.
  const body = <RenderErrorBoundary key={pathname}>{content}</RenderErrorBoundary>;

  // Toasts live inside the signed-in part: sign-out and expiry remove them with the screens (review security L1).
  if (session.role === 'CUST') {
    return <ToastProvider><CustomerShell userLabel={userLabel} onSignOut={onSignOut}>{body}</CustomerShell></ToastProvider>;
  }
  return (
    <ToastProvider>
      <StaffShell routes={routesFor(session.role)} current={current} group={home.group} userLabel={userLabel} onSignOut={onSignOut}>
        {body}
      </StaffShell>
    </ToastProvider>
  );
}

function Screen({ screen, session, onUnauthenticated }: { screen: ScreenKey; session: Session; onUnauthenticated: () => void }) {
  switch (screen) {
    case 'kill-switch': return <KillSwitchPage onUnauthenticated={onUnauthenticated} />;
    case 'inbox': return <StaffWorkspace onUnauthenticated={onUnauthenticated} />;
    case 'inspector': return <SecurityInspectorPage onUnauthenticated={onUnauthenticated} />;
    case 'chat': return <CustomerChatPage customerLinked={session.customerLinked} onUnauthenticated={onUnauthenticated} />;
  }
}
