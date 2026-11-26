import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { Role } from '../api/session';
import { axeViolations, fakeBackend, json, problem, type Handler } from '../test/fakeBackend';
import { App } from './App';
import { homeRoute, resolve, SCREEN_ROUTES } from './routes';

// FE-UI-P2: hash routes, the role shell, the no-access and not-found screens. Synthetic sessions only. The shell
// decides what is shown; every API answer still comes from the (fake) backend, which stays the authority.

const SCREEN_API: Record<Role, Record<string, Handler>> = {
  OPS: { 'GET /api/ops/kill-switch': () => json(200, { aiEnabled: true, reason: null, since: null }) },
  SALES: { 'GET /api/internal/handoffs': () => json(200, []) },
  SECREV: { 'GET /api/demo/security/events?*': () => json(200, { events: [], next: null }) },
  CUST: {
    'POST /api/demo/conversations': () => json(201, { conversationId: 'cv_AAAAAAAAAAAAAAAAAAAAAA' }),
    'GET /api/demo/conversations/cv_AAAAAAAAAAAAAAAAAAAAAA': () => json(200, { state: 'BOT' }),
    'GET /api/demo/conversations/cv_AAAAAAAAAAAAAAAAAAAAAA/messages': () => json(200, []),
  },
};

const H1: Record<Role, string> = {
  OPS: 'Công tắc trợ lý AI', SALES: 'Hộp chờ nhân viên', SECREV: 'Sự kiện bảo mật', CUST: '',
};

function start(role: Role, hash = '', extra: Record<string, Handler> = {}) {
  window.history.replaceState(null, '', `/${hash}`);
  const backend = fakeBackend({
    'GET /api/auth/session': () => json(200, { role, customerLinked: role === 'CUST' }),
    'POST /api/auth/logout': () => new Response(null, { status: 204 }),
    ...SCREEN_API[role],
    ...extra,
  });
  const view = render(<App />);
  return { backend, view, user: userEvent.setup() };
}

const otherRoleApis = (role: Role, paths: string[]) => paths.filter((p) => {
  const own = Object.keys(SCREEN_API[role]).map((k) => k.split(' ')[1]!.replace('?*', ''));
  return p !== '/api/auth/session' && p !== '/api/auth/csrf' && !own.some((o) => p.startsWith(o));
});

afterEach(() => { vi.unstubAllGlobals(); });

describe('routes', () => {
  it('give every role exactly one screen, at its own path', () => {
    for (const role of ['OPS', 'SALES', 'SECREV', 'CUST'] as const) {
      expect(SCREEN_ROUTES.filter((r) => r.role === role)).toHaveLength(1);
      expect(resolve(homeRoute(role).path, role)).toEqual({ kind: 'screen', route: homeRoute(role) });
    }
    expect(new Set(SCREEN_ROUTES.map((r) => r.path)).size).toBe(SCREEN_ROUTES.length);
  });

  it('resolve another role\'s screen as forbidden, an unknown address as not found, and never carry an ID', () => {
    expect(resolve('/cong-tac-ai', 'SALES')).toEqual({ kind: 'forbidden' });
    expect(resolve('/hop-cho/123', 'SALES')).toEqual({ kind: 'not-found' });
    expect(resolve('/', 'OPS')).toEqual({ kind: 'home' });
    for (const r of SCREEN_ROUTES) expect(r.path).toMatch(/^\/[a-z-]+$/);
  });
});

describe('role shell', () => {
  it.each(['OPS', 'SALES', 'SECREV'] as const)('%s lands on its screen with sidebar group, breadcrumb and title', async (role) => {
    const { view } = start(role);
    const home = homeRoute(role);

    expect(await screen.findByRole('heading', { level: 1, name: H1[role] })).toBeInTheDocument();
    expect(window.location.hash).toBe(`#${home.path}`);
    const nav = screen.getByRole('navigation', { name: 'Điều hướng chính' });
    expect(within(nav).getByText(home.group)).toBeInTheDocument();
    expect(within(nav).getByRole('button', { name: home.label })).toHaveAttribute('aria-current', 'page');
    expect(within(nav).getAllByRole('button').filter((b) => b.hasAttribute('aria-current'))).toHaveLength(1);
    const crumbs = within(screen.getByRole('banner')).getByRole('list', { name: 'Đường dẫn' });
    expect(within(crumbs).getAllByRole('listitem').map((li) => li.textContent?.trim())).toEqual([home.group, home.label]);
    await waitFor(() => expect(document.title).toBe(`${home.label} · AutoParts Agent Sandbox`));
    expect(screen.getByRole('main')).toHaveAttribute('id', 'noi-dung-chinh');
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it.each([
    ['SALES', '#/cong-tac-ai'], ['OPS', '#/hop-cho'], ['SECREV', '#/chat'], ['CUST', '#/su-kien-bao-mat'],
  ] as const)('%s opening %s sees "no access" in its own shell and no API of that screen is called', async (role, hash) => {
    const { backend, view, user } = start(role, hash);

    expect(await screen.findByText('Bạn không có quyền xem trang này')).toBeInTheDocument();
    expect(screen.getByText('Trang này dành cho nhóm khác. Liên hệ người quản lý tài khoản nếu bạn cần truy cập.')).toBeInTheDocument();
    expect(screen.queryByText(/Mã tham chiếu/)).toBeNull();
    expect(otherRoleApis(role, backend.calls.map((c) => c.path))).toEqual([]);
    await waitFor(() => expect(document.title).toBe('Không có quyền · AutoParts Agent Sandbox'));
    expect(await axeViolations(view.container)).toEqual([]);

    await user.click(screen.getByRole('button', { name: 'Về trang chính' }));
    expect(window.location.hash).toBe(`#${homeRoute(role).path}`);
  });

  it('shows "not found" for an address that is no screen, also one carrying an ID', async () => {
    const { backend, view } = start('SALES', '#/hop-cho/42');

    expect(await screen.findByText('Không tìm thấy trang')).toBeInTheDocument();
    expect(backend.apiCalls().map((c) => c.path)).toEqual(['/api/auth/session']);
    expect(screen.getByRole('navigation', { name: 'Điều hướng chính' })).toBeInTheDocument();
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('treats an address with a query as not found, so no ID can ride along in the address', async () => {
    const { backend } = start('CUST', '#/chat?conversationId=cv_AAAAAAAAAAAAAAAAAAAAAA');

    expect(await screen.findByText('Không tìm thấy trang')).toBeInTheDocument();
    expect(backend.apiCalls().map((c) => c.path)).toEqual(['/api/auth/session']);
  });

  it('gives the customer no sidebar and the chat at #/chat', async () => {
    start('CUST');

    expect(await screen.findByRole('button', { name: 'Đăng xuất' })).toBeInTheDocument();
    expect(window.location.hash).toBe('#/chat');
    expect(screen.queryByRole('navigation', { name: 'Điều hướng chính' })).toBeNull();
    expect(screen.getByRole('main')).toHaveClass('ap-page--customer');
  });

  it('focuses the heading when the shell appears; the skip link moves focus to main without changing the address', async () => {
    const { user } = start('OPS');
    // Focus was on <body> when the shell appeared: it goes to the page heading (WCAG 2.4.3).
    expect(await screen.findByRole('heading', { level: 1, name: H1.OPS })).toHaveFocus();

    const skip = screen.getByRole('link', { name: 'Bỏ qua tới nội dung chính' });
    skip.focus();
    await user.keyboard('{Enter}');
    expect(screen.getByRole('main')).toHaveFocus();
    expect(window.location.hash).toBe('#/cong-tac-ai');
  });

  it('moves focus to the new page heading after a route change', async () => {
    const { user } = start('SALES', '#/cong-tac-ai');
    await user.click(await screen.findByRole('button', { name: 'Về trang chính' }));

    const heading = await screen.findByRole('heading', { level: 1, name: H1.SALES });
    expect(heading).toHaveFocus();
  });

  it('signing out goes back to the root address, so the next person does not land on this screen', async () => {
    const { user } = start('OPS');
    await user.click(await screen.findByRole('button', { name: 'Đăng xuất' }));

    expect(await screen.findByRole('heading', { name: 'Đăng nhập' })).toBeInTheDocument();
    expect(window.location.hash).toBe('#/');
  });

  it('keeps the address when the session expires, so the same person returns to it', async () => {
    start('OPS', '', { 'GET /api/ops/kill-switch': () => problem(401, 'UNAUTHENTICATED') });

    expect(await screen.findByText('Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại.')).toBeInTheDocument();
    expect(window.location.hash).toBe('#/cong-tac-ai');
  });
});

describe('sidebar and width', () => {
  function stubWidth(width: number) {
    vi.stubGlobal('matchMedia', (query: string) => {
      const max = /max-width:\s*([\d.]+)px/.exec(query);
      const min = /min-width:\s*([\d.]+)px/.exec(query);
      const matches = (max ? width <= Number(max[1]) : true) && (min ? width >= Number(min[1]) : true);
      return { matches, media: query, addEventListener: () => {}, removeEventListener: () => {} };
    });
  }

  it('starts expanded from 1280 px', async () => {
    stubWidth(1440);
    start('SALES');

    expect(await screen.findByRole('button', { name: 'Thu gọn thanh bên' })).toBeInTheDocument();
    expect(screen.getByText('Kinh doanh', { selector: 'nav *' })).toBeInTheDocument();
  });

  it('starts collapsed below 1280 px, keeps an accessible name per item, and opens on request', async () => {
    stubWidth(1024);
    const { user } = start('SALES');

    const toggle = await screen.findByRole('button', { name: 'Mở rộng thanh bên' });
    const nav = screen.getByRole('navigation', { name: 'Điều hướng chính' });
    expect(within(nav).getByRole('button', { name: 'Hộp chờ nhân viên' })).toBeInTheDocument();
    await user.click(toggle);
    expect(screen.getByRole('button', { name: 'Thu gọn thanh bên' })).toBeInTheDocument();
  });
});
