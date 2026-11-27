import { StrictMode } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { axeViolations, fakeBackend, json, networkFailure, problem, type Handler } from '../test/fakeBackend';
import { KillSwitchPage } from './KillSwitchPage';

// F5 · OP-01 (UC-08). Synthetic switch states only.
const ON = { aiEnabled: true, reason: null, since: null };
const OFF_OPERATOR = { aiEnabled: false, reason: 'OPERATOR', since: '2026-11-23T08:40:00Z' };
const OFF_PROVIDER = { aiEnabled: false, reason: 'PROVIDER_AUTH', since: '2026-11-23T08:40:00Z' };
const OFF_UNREADABLE = { aiEnabled: false, reason: 'UNREADABLE', since: null };

function setup(read: Handler, write?: Handler) {
  const backend = fakeBackend({
    'GET /api/ops/kill-switch': read,
    ...(write ? { 'POST /api/ops/kill-switch': write } : {}),
  });
  const onUnauthenticated = vi.fn();
  // The shell puts every page inside <main> (shells.tsx); the page itself has no landmark of its own.
  const view = render(<main><KillSwitchPage onUnauthenticated={onUnauthenticated} /></main>);
  return { backend, onUnauthenticated, view, user: userEvent.setup() };
}

const writes = (backend: ReturnType<typeof fakeBackend>) => backend.apiCalls().filter((c) => c.method === 'POST');

describe('KillSwitchPage', () => {
  it('shows the loading state, then the AI as on', async () => {
    setup(() => json(200, ON));

    expect(screen.getByText('Đang tải trạng thái…')).toBeInTheDocument();
    expect(await screen.findByText('AI đang bật')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' })).toHaveAttribute('aria-checked', 'true');
  });

  it('asks for confirmation before turning the AI off, and Hủy changes nothing', async () => {
    const { backend, user } = setup(() => json(200, ON), () => json(200, OFF_OPERATOR));
    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));

    const dialog = screen.getByRole('alertdialog', { name: 'Tắt trợ lý AI?' });
    expect(within(dialog).getByText(/tối đa 30 giây/)).toBeInTheDocument();
    await user.click(within(dialog).getByRole('button', { name: 'Hủy' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(writes(backend)).toHaveLength(0);
    expect(screen.getByText('AI đang bật')).toBeInTheDocument();
  });

  it('turns the AI off after confirmation and shows the answer of the server', async () => {
    const { backend, user } = setup(() => json(200, ON), () => json(200, OFF_OPERATOR));
    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));
    await user.click(screen.getByRole('button', { name: 'Tắt AI' }));

    expect(await screen.findByText('Đã tắt trợ lý AI.')).toBeInTheDocument();
    expect(screen.getByText('AI đang tắt')).toBeInTheDocument();
    expect(screen.getByText('Do người vận hành tắt')).toBeInTheDocument();
    expect(screen.getByText('Tắt lúc 23/11/2026 08:40')).toBeInTheDocument();
    expect(writes(backend)).toHaveLength(1);
    expect(writes(backend)[0]?.body).toBe('{"enabled":false}');
    expect(writes(backend)[0]?.headers['x-csrf-token']).toBeTruthy();
  });

  it('sends one request when the confirm button is pressed twice', async () => {
    let answer: (r: Response) => void = () => {};
    const { backend, user } = setup(() => json(200, ON), () => new Promise<Response>((resolve) => { answer = resolve; }));
    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));
    const confirm = screen.getByRole('button', { name: 'Tắt AI' });

    await user.click(confirm);
    expect(await screen.findByRole('button', { name: /Đang tắt/ })).toBeDisabled();
    await user.click(screen.getByRole('button', { name: /Đang tắt/ }));
    expect(screen.queryByText('Đã tắt trợ lý AI.')).not.toBeInTheDocument();

    answer(json(200, OFF_OPERATOR));
    expect(await screen.findByText('Đã tắt trợ lý AI.')).toBeInTheDocument();
    expect(writes(backend)).toHaveLength(1);
  });

  it('turns the AI back on without a confirmation and explains a provider refusal', async () => {
    const { backend, user } = setup(() => json(200, OFF_PROVIDER), () => json(200, ON));

    expect(await screen.findByText('Hệ thống tự tắt. Thay khóa trong kho bí mật, rồi bật lại bằng công tắc.')).toBeInTheDocument();
    expect(screen.getAllByText('Nhà cung cấp AI từ chối khóa truy cập').length).toBeGreaterThan(0);
    await user.click(screen.getByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(await screen.findByText('Đã bật lại trợ lý AI.')).toBeInTheDocument();
    expect(writes(backend)[0]?.body).toBe('{"enabled":true}');
  });

  it('shows the server reason UNREADABLE as treated as off, with the switch still usable', async () => {
    setup(() => json(200, OFF_UNREADABLE));

    expect(await screen.findByText('Không đọc được cấu hình. Vui lòng thử tải lại; nếu vẫn lỗi, báo đội vận hành hệ thống.')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' })).toHaveAttribute('aria-checked', 'false');
  });

  it.each<[string, Handler]>([
    ['a network failure', () => networkFailure()],
    ['a server error', () => problem(503, 'SERVICE_UNAVAILABLE')],
    ['a body it cannot read', () => json(200, { aiEnabled: 'yes' })],
  ])('treats the AI as off on %s, without a switch, and reloads on request', async (_label, failure) => {
    const { backend, user } = setup(failure);

    expect(await screen.findByText('AI được coi là đang tắt')).toBeInTheDocument();
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
    expect(screen.queryByText('AI đang bật')).not.toBeInTheDocument();

    backend.set('GET /api/ops/kill-switch', () => json(200, ON));
    await user.click(screen.getByRole('button', { name: 'Tải lại' }));
    expect(await screen.findByText('AI đang bật')).toBeInTheDocument();
  });

  it('shows a reference code but no server text or status for a server error', async () => {
    setup(() => problem(503, 'SERVICE_UNAVAILABLE'));

    expect(await screen.findByText(/Mã tham chiếu: REQ-1A2B3C4D/)).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/Server title|503|SERVICE_UNAVAILABLE|kill-switch/);
  });

  it('shows a permission message when the server refuses the role', async () => {
    setup(() => problem(403, 'FORBIDDEN'));

    expect(await screen.findByText('Bạn không có quyền xem trang này')).toBeInTheDocument();
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
  });

  it('hands an expired session back to the app', async () => {
    const { onUnauthenticated } = setup(() => problem(401, 'UNAUTHENTICATED'));

    await waitFor(() => expect(onUnauthenticated).toHaveBeenCalledTimes(1));
  });

  it('says nothing was changed when the server refuses the write (4xx) and keeps the state', async () => {
    const { backend, user } = setup(() => json(200, ON), () => problem(400, 'VALIDATION_FAILED'));
    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));
    await user.click(screen.getByRole('button', { name: 'Tắt AI' }));

    expect(await screen.findByText('Không đổi được trạng thái AI')).toBeInTheDocument();
    expect(screen.getByText(/Trạng thái hiện tại giữ nguyên./)).toBeInTheDocument();
    expect(screen.getByText('Mã tham chiếu: REQ-1A2B3C4D')).toBeInTheDocument();
    expect(screen.getByText('AI đang bật')).toBeInTheDocument();
    expect(screen.queryByText(/^Đã /)).not.toBeInTheDocument();
    expect(writes(backend)).toHaveLength(1);
  });

  it('says the same for a refused attempt to turn the AI back on', async () => {
    const { user } = setup(() => json(200, OFF_OPERATOR), () => problem(409, 'CONFLICT'));
    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));

    expect(await screen.findByText('Không đổi được trạng thái AI')).toBeInTheDocument();
    expect(screen.getByText('AI đang tắt')).toBeInTheDocument();
  });

  // Reviews 2026-11-25 (UX M1/M2, security M1): the backend answers 503 also for a commit whose outcome
  // is unknown, so only a 4xx may be reported as "no change".
  it.each<[string, Handler]>([
    ['a network failure', () => networkFailure()],
    ['a 503 problem', () => problem(503, 'SERVICE_UNAVAILABLE')],
    ['a 502 HTML page from the proxy', () => new Response('<html>Bad gateway</html>', { status: 502 })],
    ['a 200 with a body it cannot read', () => json(200, { garbage: 1 })],
  ])('never claims "no change" after %s: no retry, reads the state again and shows it', async (_label, failure) => {
    let reads = 0;
    const { backend, user } = setup(() => (++reads === 1 ? json(200, ON) : json(200, OFF_OPERATOR)), failure);
    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));
    await user.click(screen.getByRole('button', { name: 'Tắt AI' }));

    expect(await screen.findByText('Chưa xác nhận được kết quả')).toBeInTheDocument();
    expect(await screen.findByText('AI đang tắt')).toBeInTheDocument();
    expect(screen.queryByText('AI đang bật')).not.toBeInTheDocument();
    expect(screen.queryByText(/Chưa có thay đổi nào được ghi/)).not.toBeInTheDocument();
    expect(writes(backend)).toHaveLength(1);
    expect(reads).toBe(2);
  });

  it('treats the AI as off when the read after an unknown write also fails', async () => {
    let reads = 0;
    const { user } = setup(() => (++reads === 1 ? json(200, ON) : networkFailure()), () => networkFailure());
    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));
    await user.click(screen.getByRole('button', { name: 'Tắt AI' }));

    expect(await screen.findByText('AI được coi là đang tắt')).toBeInTheDocument();
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
  });

  it('shows the permission message when the server refuses the write', async () => {
    const { user } = setup(() => json(200, ON), () => problem(403, 'FORBIDDEN'));
    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));
    await user.click(screen.getByRole('button', { name: 'Tắt AI' }));

    expect(await screen.findByText('Bạn không có quyền xem trang này')).toBeInTheDocument();
    expect(screen.queryByRole('switch')).not.toBeInTheDocument();
  });

  it('labels an off reason it does not know', async () => {
    setup(() => json(200, { aiEnabled: false, reason: 'SOMETHING_NEW', since: null }));

    expect(await screen.findByText('Không rõ lý do')).toBeInTheDocument();
    expect(screen.getByText('AI đang tắt')).toBeInTheDocument();
  });

  it('works under React StrictMode (mount, unmount, remount in dev)', async () => {
    fakeBackend({ 'GET /api/ops/kill-switch': () => json(200, ON), 'POST /api/ops/kill-switch': () => json(200, OFF_OPERATOR) });
    render(<StrictMode><KillSwitchPage onUnauthenticated={vi.fn()} /></StrictMode>);
    const user = userEvent.setup();

    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));
    await user.click(screen.getByRole('button', { name: 'Tắt AI' }));
    expect(await screen.findByText('Đã tắt trợ lý AI.')).toBeInTheDocument();
    expect(screen.getByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' })).not.toBeDisabled();
  });

  it('closes the confirmation with Escape and returns focus to the switch', async () => {
    const { user } = setup(() => json(200, ON));
    const toggle = await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' });
    await user.click(toggle);
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();

    await user.keyboard('{Escape}');

    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(toggle).toHaveFocus();
  });

  it('has no axe violations in the on state and in the confirmation', async () => {
    const { user, view } = setup(() => json(200, ON));
    await screen.findByText('AI đang bật');
    expect(await axeViolations(view.container)).toEqual([]);

    await user.click(screen.getByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));
    expect(await axeViolations(document.body)).toEqual([]);
  });

  // FE-UI-P3: F5 artboard copy and the F6 "Đổi công tắc" error.
  it('shows the propagation note and the time of the last change as the F5 artboard', async () => {
    setup(() => json(200, OFF_OPERATOR));

    expect(await screen.findByText('Thời gian áp dụng')).toBeInTheDocument();
    expect(screen.getByText('Thay đổi gần nhất')).toBeInTheDocument();
    expect(screen.getByText('Từ 23/11/2026 08:40. Bật lại bằng công tắc ở trên.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Trạng thái hiện tại' })).toBeInTheDocument();
  });

  it('keeps the advice of a 429 when the change is refused', async () => {
    const { user } = setup(() => json(200, ON), () => problem(429, 'RATE_LIMITED'));
    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));
    await user.click(screen.getByRole('button', { name: 'Tắt AI' }));

    expect(await screen.findByText('Không đổi được trạng thái AI')).toBeInTheDocument();
    expect(screen.getByText(/Có quá nhiều yêu cầu/)).toBeInTheDocument();
  });

  it('reads the state again from the unreadable state on request', async () => {
    const { backend, user } = setup(() => networkFailure());
    expect(await screen.findByText('AI được coi là đang tắt')).toBeInTheDocument();
    expect(screen.getAllByText('Không đọc được trạng thái').length).toBeGreaterThan(0);

    backend.set('GET /api/ops/kill-switch', () => json(200, ON));
    await user.click(screen.getByRole('button', { name: 'Tải lại' }));
    expect(await screen.findByText('AI đang bật')).toBeInTheDocument();
  });

  it('keeps the switch on screen, with the focus, while it reads the state after an unknown write (UX M2)', async () => {
    const { backend, user } = setup(() => json(200, ON), () => networkFailure());
    await user.click(await screen.findByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' }));
    let answer: (r: Response) => void = () => {};
    backend.set('GET /api/ops/kill-switch', () => new Promise<Response>((resolve) => { answer = resolve; }));
    await user.click(screen.getByRole('button', { name: 'Tắt AI' }));

    // The re-read has not answered yet: the switch is still on screen (no spinner in its place).
    await screen.findByText('Chưa xác nhận được kết quả');
    expect(screen.getByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' })).toBeInTheDocument();
    expect(screen.queryByText('Đang tải trạng thái…')).not.toBeInTheDocument();
    answer(json(200, OFF_OPERATOR));
    // Once unlocked, the switch has the focus back.
    await waitFor(() => expect(screen.getByRole('switch', { name: 'Cho phép trợ lý AI trả lời khách' })).toHaveFocus());
  });
});
