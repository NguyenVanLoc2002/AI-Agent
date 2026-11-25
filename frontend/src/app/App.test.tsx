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

  it('gives a customer the chat without the staff sidebar and never calls the operator API', async () => {
    const { backend } = setup({
      'GET /api/auth/session': () => json(200, { role: 'CUST', customerLinked: true }),
      'POST /api/demo/conversations': () => json(201, { conversationId: 'cv_AAAAAAAAAAAAAAAAAAAAAA' }),
    });

    expect(await screen.findByRole('heading', { name: 'Tư vấn phụ tùng' })).toBeInTheDocument();
    expect(await screen.findByLabelText('Nội dung tin nhắn')).toBeInTheDocument();
    expect(screen.queryByRole('navigation', { name: 'Điều hướng chính' })).not.toBeInTheDocument();
    expect(screen.queryByText('Công tắc AI')).not.toBeInTheDocument();
    expect(backend.calls.some((c) => c.path.startsWith('/api/ops/') || c.path.startsWith('/api/internal/'))).toBe(false);
  });

  it('forgets the remembered conversation when the customer signs out', async () => {
    window.sessionStorage.setItem('apas.chat.conversation', 'cv_AAAAAAAAAAAAAAAAAAAAAA');
    window.sessionStorage.setItem('apas.chat.quote', 'BG-ABCDEFGH');
    const { user } = setup({
      'GET /api/auth/session': () => json(200, { role: 'CUST', customerLinked: true }),
      'GET /api/demo/conversations/cv_AAAAAAAAAAAAAAAAAAAAAA/messages': () => json(200, []),
      'GET /api/demo/conversations/cv_AAAAAAAAAAAAAAAAAAAAAA': () => json(200, { state: 'BOT' }),
      'GET /api/demo/quotes/BG-ABCDEFGH': () => problem(404, 'NOT_FOUND'),
      'POST /api/auth/logout': () => new Response(null, { status: 204 }),
    });
    await user.click(await screen.findByRole('button', { name: 'Đăng xuất' }));

    expect(await screen.findByText('Bạn đã đăng xuất.')).toBeInTheDocument();
    expect(window.sessionStorage.length).toBe(0);
  });

  it('gives SECREV a neutral page and no operator or staff screen', async () => {
    const { backend } = setup({ 'GET /api/auth/session': () => json(200, { role: 'SECREV', customerLinked: false }) });

    expect(await screen.findByText('Màn hình cho tài khoản này chưa có trong bản demo.')).toBeInTheDocument();
    expect(backend.calls.some((c) => c.path.startsWith('/api/ops/') || c.path.startsWith('/api/internal/'))).toBe(false);
  });

  it('removes prices from the screen before the logout answer arrives (EP-10c security L-2)', async () => {
    let answer: (r: Response) => void = () => {};
    const { user } = setup({
      'GET /api/auth/session': () => json(200, { role: 'SALES', customerLinked: false }),
      'GET /api/internal/handoffs': () => json(200, [{ handoffId: 7, conversationId: 'cv_AAAAAAAAAAAAAAAAAAAAAA', reasonCode: 'CUSTOMER_REQUEST', status: 'CLAIMED', openedAt: '2020-01-02T08:00:00Z', mine: true, followUpReasonCode: null }]),
      'GET /api/internal/conversations/cv_AAAAAAAAAAAAAAAAAAAAAA/messages': () => json(200, []),
      'GET /api/internal/conversations/cv_AAAAAAAAAAAAAAAAAAAAAA/quote': () => json(200, {
        quoteRef: 'BG-ABCDEFGH', version: 1, status: 'ISSUED', currency: 'VND', validUntil: '2099-01-01T00:00:00Z', total: '2500000',
        lines: [{ sku: 'SKU-BRK-PAD-A', quantity: 2, unitPrice: '1250000', lineTotal: '2500000' }], notes: [],
      }),
      'POST /api/auth/logout': () => new Promise<Response>((resolve) => { answer = resolve; }),
    });
    await user.click(await screen.findByRole('button', { name: /^Mở cuộc trò chuyện/ }));
    expect(await screen.findByText('2 × 1.250.000 ₫')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Đăng xuất' }));
    expect(await screen.findByText('Đang đăng xuất…')).toBeInTheDocument();
    expect(document.body.textContent).not.toContain('₫');
    answer(new Response(null, { status: 204 }));
    expect(await screen.findByText('Bạn đã đăng xuất.')).toBeInTheDocument();
  });

  it('gives SALES the staff inbox and never the AI switch', async () => {
    const { backend } = setup({
      'GET /api/auth/session': () => json(200, { role: 'SALES', customerLinked: false }),
      'GET /api/internal/handoffs': () => json(200, []),
    });

    expect(await screen.findByRole('heading', { name: 'Hộp chờ nhân viên' })).toBeInTheDocument();
    expect(screen.getByRole('navigation', { name: 'Điều hướng chính' })).toBeInTheDocument();
    expect(screen.queryByText('Công tắc AI')).not.toBeInTheDocument();
    expect(backend.calls.some((c) => c.path.startsWith('/api/ops/') || c.path.startsWith('/api/demo/'))).toBe(false);
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

  it('forgets what an earlier user left in this tab when someone signs in (M-1)', async () => {
    window.sessionStorage.setItem('apas.chat.conversation', 'cv_AAAAAAAAAAAAAAAAAAAAAA');
    window.sessionStorage.setItem('apas.chat.quote', 'BG-ABCDEFGH');
    const { backend, user } = setup({
      'GET /api/auth/session': () => problem(401, 'UNAUTHENTICATED'),
      'POST /api/auth/login': () => json(200, { role: 'CUST', customerLinked: true }),
      'POST /api/demo/conversations': () => json(201, { conversationId: 'cv_BBBBBBBBBBBBBBBBBBBBBB' }),
    });
    await signIn(user, 'demo-cust-b');
    await screen.findByLabelText('Nội dung tin nhắn');

    expect(backend.calls.some((c) => c.path.includes('cv_AAAA') || c.path.includes('BG-ABCDEFGH'))).toBe(false);
    expect(window.sessionStorage.getItem('apas.chat.quote')).toBeNull();
  });

  it("does not carry the last customer's conversation into the next sign-in after the session expired (M-1)", async () => {
    window.sessionStorage.setItem('apas.chat.conversation', 'cv_AAAAAAAAAAAAAAAAAAAAAA');
    window.sessionStorage.setItem('apas.chat.quote', 'BG-ABCDEFGH');
    const { backend, user } = setup({
      'GET /api/auth/session': () => json(200, { role: 'CUST', customerLinked: true }),
      'GET /api/demo/conversations/cv_AAAAAAAAAAAAAAAAAAAAAA/messages': () => problem(401, 'UNAUTHENTICATED'),
      'GET /api/demo/conversations/cv_AAAAAAAAAAAAAAAAAAAAAA': () => problem(401, 'UNAUTHENTICATED'),
    });
    expect(await screen.findByText('Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.')).toBeInTheDocument();
    expect(window.sessionStorage.length).toBe(0);

    backend.set('POST /api/auth/login', () => json(200, { role: 'CUST', customerLinked: true }));
    backend.set('POST /api/demo/conversations', () => json(201, { conversationId: 'cv_BBBBBBBBBBBBBBBBBBBBBB' }));
    const touchesOld = () => backend.calls.filter((c) => c.path.includes('cv_AAAA') || c.path.includes('BG-ABCDEFGH')).length;
    const before = touchesOld();
    await signIn(user, 'demo-cust-b');
    await screen.findByLabelText('Nội dung tin nhắn');

    expect(touchesOld()).toBe(before);
    expect(window.sessionStorage.getItem('apas.chat.conversation')).toBe('cv_BBBBBBBBBBBBBBBBBBBBBB');
  });
});
