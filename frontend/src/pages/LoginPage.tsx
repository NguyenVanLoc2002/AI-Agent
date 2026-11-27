import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { login, type Session } from '../api/session';
import { failureText, referenceText } from '../app/texts';
import { Alert, Button, Input } from '../design-system';

// E7 · SH-06 sign-in (artboards E7-Login, E7-LoginError; docs/design/specs/40 §E7) for the synthetic demo users
// (ADR-010, form login with CSRF). The password lives only in this component's state and is cleared after every
// attempt; nothing is written to browser storage. Wrong credentials get one message that does not say which field
// was wrong (A-71). The artboard's locked state (AUTH-423) has no backend: there is no lockout yet (A-39(b)).

interface Props {
  /** Shown above the form, e.g. after the session expired. */
  notice?: string | null;
  onLoggedIn: (session: Session) => void;
}

export function LoginPage({ notice = null, onLoggedIn }: Props) {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({});
  const [failure, setFailure] = useState<{ title: string; body: string; reference: string | null } | null>(null);
  const [pending, setPending] = useState(false);
  const failureRef = useRef<HTMLDivElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const noticeRef = useRef<HTMLDivElement>(null);
  const headingRef = useRef<HTMLHeadingElement>(null);

  // P2 review UX M2: after sign-out, expiry or a failed session check the control that had focus is gone. Focus moves
  // to the notice (so it is read out) or to the page heading, never left on <body> (WCAG 2.4.3).
  useEffect(() => {
    const target = noticeRef.current ?? headingRef.current;
    if (document.activeElement === null || document.activeElement === document.body) target?.focus();
  }, []);

  // After a failed attempt, focus moves to the message so screen readers announce it.
  useEffect(() => { if (failure) failureRef.current?.focus(); }, [failure]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) {
      return;
    }
    const errors: { username?: string; password?: string } = {};
    if (username.trim() === '') errors.username = 'Bắt buộc';
    if (password === '') errors.password = 'Bắt buộc';
    setFieldErrors(errors);
    if (errors.username || errors.password) {
      formRef.current?.querySelector<HTMLInputElement>(`input[name="${errors.username ? 'username' : 'password'}"]`)?.focus();
      return;
    }
    setPending(true);
    setFailure(null);
    try {
      const session = await login(username.trim(), password);
      setPassword('');
      onLoggedIn(session);
    } catch (error) {
      setPassword('');
      if (error instanceof ApiError && error.unauthenticated) {
        setFailure({ title: 'Tài khoản hoặc mật khẩu không đúng', body: 'Vui lòng nhập lại.', reference: null });
      } else {
        setFailure({ title: 'Chưa đăng nhập được', body: failureText(error), reference: referenceText(error) });
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <main style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 'var(--space-6)', boxSizing: 'border-box', background: 'var(--bg-app)' }}>
      {/* E7 card: 400 wide, gap 16, border-card, shadow-md. Padding 28 and radius 16 have no token: space-card-lg (24), radius-xl (12). */}
      <div style={{ width: 400, maxWidth: '100%', boxSizing: 'border-box', background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-card)',
        borderRadius: 'var(--radius-xl)', boxShadow: 'var(--shadow-md)', padding: 'var(--space-card-lg)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' }}>
          {/* The artboard shows the brand as the card title. The page heading stays "Đăng nhập" for screen readers
              (and the security E2E that look for it), hidden because the artboard has no such line. */}
          <h1 ref={headingRef} tabIndex={-1} className="ap-visually-hidden">Đăng nhập</h1>
          <span aria-hidden="true" style={{ font: 'var(--type-section-title)', color: 'var(--text-primary)' }}>AutoParts Agent</span>
          {/* The artboard says "tài khoản nhân viên nội bộ"; this demo also signs in the customer (F1). */}
          <span style={{ font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>Đăng nhập bằng tài khoản demo · dữ liệu mô phỏng</span>
        </div>
        {notice && <div ref={noticeRef} tabIndex={-1} style={{ outline: 'none' }}><Alert tone="info">{notice}</Alert></div>}
        {failure && (
          <div ref={failureRef} tabIndex={-1} style={{ outline: 'none' }}>
            <Alert tone="error" title={failure.title}>
              {failure.body}
              {failure.reference && <span style={{ display: 'block' }}>{failure.reference}</span>}
            </Alert>
          </div>
        )}
        <form ref={formRef} noValidate onSubmit={submit} aria-label="Đăng nhập" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <Input label="Tài khoản" name="username" autoComplete="username" value={username} required
            error={fieldErrors.username} onChange={(e) => setUsername(e.target.value)} readOnly={pending} />
          <Input label="Mật khẩu" name="password" type="password" autoComplete="current-password" value={password} required
            error={fieldErrors.password} onChange={(e) => setPassword(e.target.value)} readOnly={pending} />
          <Button type="submit" loading={pending} fullWidth>{pending ? 'Đang xác thực…' : 'Đăng nhập'}</Button>
        </form>
        <span style={{ font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>
          Không tự đăng ký tài khoản. Quên mật khẩu: liên hệ quản lý trực tiếp hoặc bộ phận IT.
        </span>
      </div>
    </main>
  );
}
