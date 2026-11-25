import { useCallback, useEffect, useState } from 'react';
import { currentSession, logout, type Role, type Session } from '../api/session';
import { Alert, AppHeader, Button, Sidebar, Spinner } from '../design-system';
import { CustomerChatPage } from '../pages/CustomerChatPage';
import { KillSwitchPage } from '../pages/KillSwitchPage';
import { SecurityInspectorPage } from '../pages/inspector/SecurityInspectorPage';
import { StaffWorkspace } from '../pages/staff/StaffWorkspace';
import { LoginPage } from '../pages/LoginPage';
import { conversationMemory } from './conversationMemory';
import { failureText, referenceText, ROLE_LABELS, SESSION_EXPIRED } from './texts';

// Application root (EP-10a). The screens offered per role are presentation only: every API call is
// authorized again by the backend, which is the authority (CLAUDE.md, rule 10). OPS get the kill switch (F5,
// EP-10a), SALES the inbox (F2/F3, EP-10c), SECREV the Security Inspector (F4, EP-10d). The customer gets the
// chat (F1, EP-10b) without the staff sidebar, laid out for phone width.

type ScreenKey = 'kill-switch' | 'inbox' | 'inspector';

interface ScreenDef { key: ScreenKey; label: string; icon: string }

const SCREENS_BY_ROLE: Record<Role, ScreenDef[]> = {
  OPS: [{ key: 'kill-switch', label: 'Công tắc AI', icon: 'shield-alert' }],
  CUST: [],
  SALES: [{ key: 'inbox', label: 'Hộp chờ nhân viên', icon: 'users' }],
  SECREV: [{ key: 'inspector', label: 'Sự kiện bảo mật', icon: 'shield-check' }],
};

type Boot =
  | { phase: 'checking' }
  | { phase: 'signing-out' }
  | { phase: 'error'; error: unknown }
  | { phase: 'anonymous'; notice: string | null }
  | { phase: 'signed-in'; session: Session };

export function App() {
  const [boot, setBoot] = useState<Boot>({ phase: 'checking' });

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

  const expired = useCallback(() => {
    conversationMemory.reset();
    setBoot({ phase: 'anonymous', notice: SESSION_EXPIRED });
  }, []);

  const signOut = useCallback(async () => {
    conversationMemory.reset();
    // Nothing of the signed-in screens (prices, messages) stays visible while the logout answer is awaited.
    setBoot({ phase: 'signing-out' });
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
  }, []);

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
          <Alert tone="error" title="Chưa kiểm tra được phiên đăng nhập"
            action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={() => void check()}>Thử lại</Button>}>
            {failureText(boot.error)}
            {referenceText(boot.error) && <span style={{ display: 'block' }}>{referenceText(boot.error)}</span>}
          </Alert>
        </main>
      );
    case 'anonymous':
      return <LoginPage notice={boot.notice} onLoggedIn={(session) => {
        // A new sign-in never inherits what an earlier user of this tab left behind (review M-1).
        conversationMemory.reset();
        setBoot({ phase: 'signed-in', session });
      }} />;
    case 'signed-in':
      return <Shell session={boot.session} onSignOut={() => void signOut()} onUnauthenticated={expired} />;
  }
}

function Shell({ session, onSignOut, onUnauthenticated }: { session: Session; onSignOut: () => void; onUnauthenticated: () => void }) {
  const screens = SCREENS_BY_ROLE[session.role];
  const [active, setActive] = useState<ScreenKey | null>(screens[0]?.key ?? null);
  const [collapsed, setCollapsed] = useState(false);
  const roleLabel = ROLE_LABELS[session.role];
  const current = screens.find((s) => s.key === active) ?? null;

  const signOutButton = <Button variant="ghost" size="sm" iconLeft="log-out" onClick={onSignOut}>Đăng xuất</Button>;
  const content = current?.key === 'kill-switch'
    ? <KillSwitchPage onUnauthenticated={onUnauthenticated} />
    : current?.key === 'inbox'
      ? <StaffWorkspace onUnauthenticated={onUnauthenticated} />
    : current?.key === 'inspector'
      ? <SecurityInspectorPage onUnauthenticated={onUnauthenticated} />
    : session.role === 'CUST'
      ? <CustomerChatPage customerLinked={session.customerLinked} onUnauthenticated={onUnauthenticated} />
      : <NotAvailable />;

  if (screens.length === 0) {
    // Customer-facing and other roles without a screen yet: no sidebar, works at phone width.
    return (
      <div style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', background: 'var(--bg-app)' }}>
        <AppHeader breadcrumb={[{ label: 'AutoParts Agent Sandbox' }]} actions={signOutButton}
          user={{ name: roleLabel, initials: initials(roleLabel) }} style={{ padding: '0 var(--space-4)' }} />
        <main style={{ padding: 'var(--space-6) var(--space-4)', maxWidth: 760, width: '100%', margin: '0 auto' }}>{content}</main>
      </div>
    );
  }

  return (
    <div style={{ height: '100vh', display: 'flex', background: 'var(--bg-app)' }}>
      <Sidebar sections={[{ items: screens.map((s) => ({ key: s.key, label: s.label, icon: s.icon })) }]}
        activeKey={active ?? undefined} onSelect={(key) => setActive(screens.find((s) => s.key === key)?.key ?? null)}
        collapsed={collapsed} onToggleCollapsed={() => setCollapsed((c) => !c)} brand="AutoParts Agent" env="Demo" />
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
        <AppHeader breadcrumb={[{ label: current?.label ?? 'AutoParts Agent Sandbox' }]} actions={signOutButton}
          user={{ name: roleLabel, initials: initials(roleLabel) }} />
        <main style={{ flex: 1, overflowY: 'auto', padding: 'var(--space-6)' }}>{content}</main>
      </div>
    </div>
  );
}

function NotAvailable() {
  return (
    <Alert tone="info" title="Màn hình cho tài khoản này chưa có trong bản demo.">
      Vui lòng đăng xuất nếu bạn không cần dùng tiếp.
    </Alert>
  );
}

function initials(label: string): string {
  return label.split(/\s+/).filter(Boolean).slice(0, 2).map((w) => w.charAt(0).toUpperCase()).join('');
}
