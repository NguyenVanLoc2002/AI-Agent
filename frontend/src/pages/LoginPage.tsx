import { useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { login, type Session } from '../api/session';
import { failureText, referenceText } from '../app/texts';
import { Alert, Button, Input } from '../design-system';

// Login for the synthetic demo users (ADR-010, form login with CSRF). The password lives only in this
// component's state and is cleared after every attempt; nothing is written to browser storage.

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

  // After a failed attempt, focus moves to the message so screen readers announce it.
  useEffect(() => { if (failure) failureRef.current?.focus(); }, [failure]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) {
      return;
    }
    const errors: { username?: string; password?: string } = {};
    if (username.trim() === '') errors.username = 'Nhập tên đăng nhập.';
    if (password === '') errors.password = 'Nhập mật khẩu.';
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
        setFailure({ title: 'Tên đăng nhập hoặc mật khẩu không đúng.', body: 'Vui lòng nhập lại.', reference: null });
      } else {
        setFailure({ title: 'Chưa đăng nhập được', body: failureText(error), reference: referenceText(error) });
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <main style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 'var(--space-4)', background: 'var(--bg-app)' }}>
      <div style={{ width: '100%', maxWidth: 400, background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-default)',
        borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-sm)', padding: 'var(--space-6)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
        <div>
          <h1 style={{ margin: 0, font: 'var(--type-section-title)' }}>Đăng nhập</h1>
          <p style={{ margin: '4px 0 0', font: 'var(--type-body)', color: 'var(--text-secondary)' }}>
            AutoParts Agent Sandbox · bản demo dùng dữ liệu mô phỏng
          </p>
        </div>
        {notice && <Alert tone="info">{notice}</Alert>}
        {failure && (
          <div ref={failureRef} tabIndex={-1} style={{ outline: 'none' }}>
            <Alert tone="error" title={failure.title}>
              {failure.body}
              {failure.reference && <span style={{ display: 'block' }}>{failure.reference}</span>}
            </Alert>
          </div>
        )}
        <form ref={formRef} noValidate onSubmit={submit} style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
          <Input label="Tên đăng nhập" name="username" autoComplete="username" value={username} required
            error={fieldErrors.username} onChange={(e) => setUsername(e.target.value)} disabled={pending} />
          <Input label="Mật khẩu" name="password" type="password" autoComplete="current-password" value={password} required
            error={fieldErrors.password} onChange={(e) => setPassword(e.target.value)} disabled={pending} />
          <Button type="submit" loading={pending} fullWidth>{pending ? 'Đang đăng nhập…' : 'Đăng nhập'}</Button>
        </form>
      </div>
    </main>
  );
}
