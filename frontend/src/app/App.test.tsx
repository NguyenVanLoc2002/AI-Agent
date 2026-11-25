import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { axeViolations, fakeBackend, json, networkFailure, problem, type Handler } from '../test/fakeBackend';
import { App } from './App';

// Login, role shell and session expiry. Synthetic demo user names; no real password.
const PASSWORD = 'synthetic-pass-0001';

function setup(routes: Record<string, Handler>) {
  const backend = fakeBackend(routes);
  const view = render(<App />);
  return { backend, view, user: userEvent.setup() };
}

async function signIn(user: ReturnType<typeof userEvent.setup>, name = 'demo-ops') {
  await user.type(await screen.findByLabelText(/Tên đăng nhập/), name);
  await user.type(screen.getByLabelText(/Mật khẩu/), PASSWORD);
  await user.click(screen.getByRole('button', { name: 'Đăng nhập' }));
}

describe('App', () => {
  it('shows the login page without a session and passes the axe check', async () => {
    const { view } = setup({ 'GET /api/auth/session': () => problem(401, 'UNAUTHENTICATED') });

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('checks the fields before sending anything', async () => {
    const { backend, user } = setup({ 'GET /api/auth/session': () => problem(401, 'UNAUTHENTICATED') });
    await user.click(await screen.findByRole('button', { name: 'Đăng nhập' }));

    expect(screen.getByText('Nhập tên đăng nhập.')).toBeInTheDocument();
    expect(screen.getByLabelText(/Tên đăng nhập/)).toHaveFocus();
    expect(screen.getByText('Nhập mật khẩu.')).toBeInTheDocument();
    expect(backend.apiCalls().filter((c) => c.method === 'POST')).toHaveLength(0);
  });

  it('signs an operator in with the CSRF token and opens the AI switch', async () => {
    const { backend, user } = setup({
      'GET /api/auth/session': () => problem(401, 'UNAUTHENTICATED'),
      'POST /api/auth/login': () => json(200, { role: 'OPS', customerLinked: false }),
      'GET /api/ops/kill-switch': () => json(200, { aiEnabled: true, reason: null, since: null }),
    });
    await signIn(user);

    expect(await screen.findByText('AI đang bật')).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Điều hướng chính' })).toBeInTheDocument();
    expect(screen.getByText('Vận hành hệ thống')).toBeInTheDocument();
    const loginCall = backend.apiCalls().find((c) => c.path === '/api/auth/login');
    expect(loginCall?.headers['content-type']).toBe('application/x-www-form-urlencoded');
    expect(loginCall?.headers['x-csrf-token']).toBeTruthy();
    expect(new URLSearchParams(loginCall?.body ?? '').get('username')).toBe('demo-ops');
    expect(window.localStorage.length + window.sessionStorage.length).toBe(0);
  });

  it('says the credentials are wrong and clears the password', async () => {
    const { user } = setup({
      'GET /api/auth/session': () => problem(401, 'UNAUTHENTICATED'),
      'POST /api/auth/login': () => problem(401, 'UNAUTHENTICATED'),
    });
    await signIn(user);

    expect(await screen.findByText('Tên đăng nhập hoặc mật khẩu không đúng.')).toBeInTheDocument();
    expect(screen.getByLabelText(/Mật khẩu/)).toHaveValue('');
    expect(screen.getByRole('alert').parentElement).toHaveFocus();
  });

  it('gives roles without a screen yet a neutral page and never calls the operator API', async () => {
    const { backend } = setup({ 'GET /api/auth/session': () => json(200, { role: 'CUST', customerLinked: true }) });

    expect(await screen.findByText('Màn hình cho tài khoản này chưa có trong bản demo.')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Điều hướng chính' })).not.toBeInTheDocument();
    expect(screen.queryByText('Công tắc AI')).not.toBeInTheDocument();
    expect(backend.calls.some((c) => c.path.startsWith('/api/ops/'))).toBe(false);
  });

  it('does not offer the AI switch to SALES or SECREV', async () => {
    for (const role of ['SALES', 'SECREV']) {
      const { backend, view } = setup({ 'GET /api/auth/session': () => json(200, { role, customerLinked: false }) });
      expect(await screen.findByText('Màn hình cho tài khoản này chưa có trong bản demo.')).toBeInTheDocument();
      expect(backend.calls.some((c) => c.path.startsWith('/api/ops/'))).toBe(false);
      view.unmount();
    }
  });

  it('fails closed on a session it cannot read', async () => {
    const { backend } = setup({ 'GET /api/auth/session': () => json(200, { role: 'ADMIN', customerLinked: false }) });

    expect(await screen.findByText(/Chưa kiểm tra được phiên đăng nhập/)).toBeInTheDocument();
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument();
    expect(backend.calls.some((c) => c.path.startsWith('/api/ops/'))).toBe(false);
  });

  it('offers a retry when the session check cannot reach the server', async () => {
    const { backend, user } = setup({ 'GET /api/auth/session': () => networkFailure() });

    expect(await screen.findByText(/Không kết nối được máy chủ/)).toBeInTheDocument();
    backend.set('GET /api/auth/session', () => problem(401, 'UNAUTHENTICATED'));
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
  });

  it('returns to the login page with a notice when the session expires on a screen', async () => {
    setup({
      'GET /api/auth/session': () => json(200, { role: 'OPS', customerLinked: false }),
      'GET /api/ops/kill-switch': () => problem(401, 'UNAUTHENTICATED'),
    });

    expect(await screen.findByText('Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
  });

  it('signs out with the CSRF token and returns to the login page', async () => {
    const { backend, user } = setup({
      'GET /api/auth/session': () => json(200, { role: 'OPS', customerLinked: false }),
      'GET /api/ops/kill-switch': () => json(200, { aiEnabled: true, reason: null, since: null }),
      'POST /api/auth/logout': () => new Response(null, { status: 204 }),
    });
    await user.click(await screen.findByRole('button', { name: 'Đăng xuất' }));

    expect(await screen.findByText('Bạn đã đăng xuất.')).toBeInTheDocument();
    const logoutCall = backend.apiCalls().find((c) => c.path === '/api/auth/logout');
    expect(logoutCall?.headers['x-csrf-token']).toBeTruthy();
  });

  it('warns that the session may still be valid when sign-out fails', async () => {
    const { user } = setup({
      'GET /api/auth/session': () => json(200, { role: 'OPS', customerLinked: false }),
      'GET /api/ops/kill-switch': () => json(200, { aiEnabled: true, reason: null, since: null }),
      'POST /api/auth/logout': () => networkFailure(),
    });
    await user.click(await screen.findByRole('button', { name: 'Đăng xuất' }));

    expect(await screen.findByText(/Chưa xác nhận được việc đăng xuất với máy chủ/)).toBeInTheDocument();
    expect(screen.queryByText('Bạn đã đăng xuất.')).not.toBeInTheDocument();
  });
});
