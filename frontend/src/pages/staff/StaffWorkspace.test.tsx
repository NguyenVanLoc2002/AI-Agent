import { act, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { decodeStaffQuote } from '../../api/staff';
import { axeViolations, fakeBackend, json, networkFailure, problem, type Handler } from '../../test/fakeBackend';
import { ToastProvider } from '../../app/toast';
import { StaffWorkspace } from './StaffWorkspace';

// F2 · HO-01 and F3 · HO-02 (UC-05/06, SALES). Synthetic handoffs, conversations and quotes only.
const CONV = 'cv_AAAAAAAAAAAAAAAAAAAAAA';
const CONV_2 = 'cv_BBBBBBBBBBBBBBBBBBBBBB';
const CONV_3 = 'cv_CCCCCCCCCCCCCCCCCCCCCC';
const LIST = '/api/internal/handoffs';
const MESSAGES = `/api/internal/conversations/${CONV}/messages`;
const QUOTE = `/api/internal/conversations/${CONV}/quote`;

const handoff = (extra: Record<string, unknown> = {}) => ({
  handoffId: 7, conversationId: CONV, reasonCode: 'CUSTOMER_REQUEST', status: 'CLAIMED', openedAt: '2020-01-02T08:00:00Z', mine: true, followUpReasonCode: null, ...extra,
});
const INBOX = [
  handoff({ handoffId: 1, conversationId: CONV_2, reasonCode: 'PROVIDER_ERROR', status: 'OPEN', mine: false }),
  handoff(),
  handoff({ handoffId: 9, conversationId: CONV_3, reasonCode: 'QUOTE_NOT_ISSUED', mine: false, followUpReasonCode: 'DLP_INBOUND' }),
];
const STAFF_QUOTE = {
  quoteRef: 'BG-ABCDEFGH', version: 2, status: 'ISSUED', currency: 'VND', validUntil: '2099-01-03T08:00:00Z', total: '2580000',
  lines: [{ sku: 'SKU-BRK-PAD-A', quantity: 2, unitPrice: '1290000', lineTotal: '2580000' }], notes: ['Giá chưa gồm VAT'],
};
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
const message = (direction: string, text: string) => ({ direction, text, sentAt: '2020-01-02T08:01:00Z' });

function setup(routes: Record<string, Handler>) {
  const backend = fakeBackend({
    [`GET ${LIST}`]: () => json(200, INBOX),
    [`GET ${MESSAGES}`]: () => json(200, [message('CUSTOMER', 'Cho tôi giá tốt hơn nếu lấy 50 bộ'), message('BOT', 'Yêu cầu của bạn đã được chuyển cho nhân viên tư vấn.'), message('CUSTOMER', '[tin nhắn đã bị chặn]')]),
    [`GET ${QUOTE}`]: () => json(200, STAFF_QUOTE),
    ...routes,
  });
  const onUnauthenticated = vi.fn();
  // As in the app: inside the shell's <main> and the signed-in ToastProvider (App.tsx).
  const view = render(<ToastProvider><main><StaffWorkspace onUnauthenticated={onUnauthenticated} /></main></ToastProvider>);
  return { backend, view, onUnauthenticated, user: userEvent.setup() };
}

const count = (backend: ReturnType<typeof fakeBackend>, method: string, path: string) =>
  backend.calls.filter((c) => c.method === method && c.path === path).length;

async function openHeld(routes: Record<string, Handler> = {}) {
  const ctx = setup(routes);
  await ctx.user.click(await screen.findByRole('button', { name: /^Mở cuộc trò chuyện/ }));
  await screen.findByRole('heading', { name: /Cuộc trò chuyện/ });
  return ctx;
}

afterEach(() => { vi.useRealTimers(); });

describe('F2 · staff inbox', () => {
  it('lists the team handoffs with reason labels and states, without the full conversation ID', async () => {
    const { view } = setup({});
    const table = await screen.findByRole('table', { name: 'Yêu cầu chuyển nhân viên của nhóm' });
    const rows = within(table).getAllByRole('row').slice(1);

    expect(within(rows[0] as HTMLElement).getByText('Dịch vụ AI gián đoạn')).toBeInTheDocument();
    expect(within(rows[0] as HTMLElement).getByText('Chờ nhận')).toBeInTheDocument();
    expect(within(rows[0] as HTMLElement).getByRole('button', { name: /^Nhận xử lý cuộc trò chuyện/ })).toBeInTheDocument();
    expect(within(rows[1] as HTMLElement).getByText('Bạn đang xử lý')).toBeInTheDocument();
    expect(within(rows[1] as HTMLElement).getByRole('button', { name: /^Mở cuộc trò chuyện/ })).toBeInTheDocument();
    expect(within(rows[2] as HTMLElement).getByText('Nhân viên khác đang xử lý')).toBeInTheDocument();
    expect(within(rows[2] as HTMLElement).getByRole('button', { name: 'Đã có người nhận' })).toBeDisabled();
    expect(within(rows[2] as HTMLElement).getByText('Sau khi hoàn tất: mở tiếp vì Tin nhắn chứa dữ liệu nhạy cảm')).toBeInTheDocument();
    expect(within(rows[1] as HTMLElement).getByText('…AAAAAA')).toBeInTheDocument();
    expect(view.container.textContent).not.toContain(CONV);
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('shows an empty inbox', async () => {
    setup({ [`GET ${LIST}`]: () => json(200, []) });

    expect(await screen.findByText('Chưa có yêu cầu nào')).toBeInTheDocument();
  });

  it('labels a reason it does not know neutrally', async () => {
    setup({ [`GET ${LIST}`]: () => json(200, [handoff({ reasonCode: 'SOMETHING_NEW' })]) });

    expect(await screen.findByText('Lý do khác')).toBeInTheDocument();
  });

  it('fails closed on a row it cannot read, with a retry', async () => {
    const { backend, user } = setup({ [`GET ${LIST}`]: () => json(200, [handoff({ status: 'ASSIGNED' })]) });

    expect(await screen.findByText('Chưa tải được hộp chờ')).toBeInTheDocument();
    backend.set(`GET ${LIST}`, () => json(200, []));
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByText('Chưa có yêu cầu nào')).toBeInTheDocument();
  });

  it('claims a handoff and reads the inbox again', async () => {
    const { backend, user } = setup({ [`POST ${LIST}/1/claim`]: () => json(200, handoff({ handoffId: 1, conversationId: CONV_2 })) });
    await user.click(await screen.findByRole('button', { name: /^Nhận xử lý cuộc trò chuyện/ }));

    expect(await screen.findByText('Đã nhận xử lý.')).toBeInTheDocument();
    await waitFor(() => expect(count(backend, 'GET', LIST)).toBe(2));
    expect(count(backend, 'POST', `${LIST}/1/claim`)).toBe(1);
  });

  it('on 409 reads the inbox again and, when the row is now another member’s, shows the "taken" toast (F6)', async () => {
    const { backend, user } = setup({ [`POST ${LIST}/1/claim`]: () => problem(409, 'CONFLICT') });
    const claimButton = await screen.findByRole('button', { name: /^Nhận xử lý cuộc trò chuyện/ });
    backend.set(`GET ${LIST}`, () => json(200, [handoff({ handoffId: 1, conversationId: CONV_2, status: 'CLAIMED', mine: false })]));
    await user.click(claimButton);

    expect(await screen.findByText('Yêu cầu đã có người nhận')).toBeInTheDocument();
    expect(screen.getByText('Một nhân viên khác vừa nhận yêu cầu này. Danh sách đã được tải lại.')).toBeInTheDocument();
    expect(count(backend, 'GET', LIST)).toBe(2);
    expect(screen.queryByText('Cuộc trò chuyện đang bận')).not.toBeInTheDocument();
  });

  it('on 409 with the row still waiting, shows the "busy" toast instead (one code for both, UX M4)', async () => {
    const { backend, user } = setup({ [`POST ${LIST}/1/claim`]: () => problem(409, 'CONFLICT') });
    await user.click(await screen.findByRole('button', { name: /^Nhận xử lý cuộc trò chuyện/ }));

    expect(await screen.findByText('Cuộc trò chuyện đang bận')).toBeInTheDocument();
    expect(count(backend, 'GET', LIST)).toBe(2);
    expect(screen.queryByText('Yêu cầu đã có người nhận')).not.toBeInTheDocument();
  });

  it.each([
    ['gone from the list', [] as unknown[], 'Yêu cầu không còn trong hộp chờ.'],
    ['now this member’s', [handoff({ handoffId: 1, conversationId: CONV_2, status: 'CLAIMED', mine: true })], null],
  ])('on 409 with the row %s, shows no toast that contradicts the list (security L-1)', async (_label, rows, notice) => {
    const { backend, user } = setup({ [`POST ${LIST}/1/claim`]: () => problem(409, 'CONFLICT') });
    const claimButton = await screen.findByRole('button', { name: /^Nhận xử lý cuộc trò chuyện/ });
    backend.set(`GET ${LIST}`, () => json(200, rows));
    await user.click(claimButton);

    await waitFor(() => expect(count(backend, 'GET', LIST)).toBe(2));
    if (notice) expect(await screen.findByText(notice)).toBeInTheDocument();
    else expect(await screen.findByRole('button', { name: /^Mở cuộc trò chuyện/ })).toHaveFocus();
    expect(screen.queryByText('Cuộc trò chuyện đang bận')).not.toBeInTheDocument();
    expect(screen.queryByText('Yêu cầu đã có người nhận')).not.toBeInTheDocument();
  });

  it('sends one claim when the button is pressed twice', async () => {
    let answer: (r: Response) => void = () => {};
    const { backend, user } = setup({ [`POST ${LIST}/1/claim`]: () => new Promise<Response>((resolve) => { answer = resolve; }) });
    await user.click(await screen.findByRole('button', { name: /^Nhận xử lý cuộc trò chuyện/ }));
    await user.click(await screen.findByRole('button', { name: /Đang nhận/ }));
    answer(json(200, handoff({ handoffId: 1, conversationId: CONV_2 })));

    await screen.findByText('Đã nhận xử lý.');
    expect(count(backend, 'POST', `${LIST}/1/claim`)).toBe(1);
  });

  it('shows a permission message on 403 and hands a 401 back to the app', async () => {
    setup({ [`GET ${LIST}`]: () => problem(403, 'FORBIDDEN') });
    expect(await screen.findByText('Bạn không có quyền xem trang này')).toBeInTheDocument();

    const { onUnauthenticated } = setup({ [`GET ${LIST}`]: () => problem(401, 'UNAUTHENTICATED') });
    await waitFor(() => expect(onUnauthenticated).toHaveBeenCalledTimes(1));
  });

  it('reads the inbox only on request, never on a timer (decision 1A)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { backend, user } = setup({});
    await screen.findByRole('table');

    await act(async () => { await vi.advanceTimersByTimeAsync(120_000); });
    expect(count(backend, 'GET', LIST)).toBe(1);
    await user.click(screen.getByRole('button', { name: 'Tải lại' }));
    await waitFor(() => expect(count(backend, 'GET', LIST)).toBe(2));
  });
});

describe('F3 · held conversation', () => {
  it('shows the messages by author, the blocked placeholder as stored, and the quote read-only', async () => {
    const { view } = await openHeld();

    expect(await screen.findByText('Cho tôi giá tốt hơn nếu lấy 50 bộ')).toBeInTheDocument();
    const list = screen.getByRole('list', { name: 'Tin nhắn' });
    expect(within(list).getAllByText(/^Khách hàng:/)).toHaveLength(2);
    expect(within(list).getByText('Trợ lý AI')).toBeInTheDocument();
    expect(screen.getByText('[tin nhắn đã bị chặn]')).toBeInTheDocument();
    expect(screen.getByText('Lý do: Khách yêu cầu nhân viên')).toBeInTheDocument();
    expect(screen.getByText('Trợ lý AI đang tạm dừng')).toBeInTheDocument();

    const box = await screen.findByRole('region', { name: 'Báo giá BG-ABCDEFGH' });
    expect(within(box).getByText(/^Phiên bản 2 · Hiệu lực đến \d\d\/01\/2099 \d\d:\d\d$/)).toBeInTheDocument();
    expect(within(box).getByText('Đã gửi khách')).toBeInTheDocument();
    expect(within(box).getByText('Đơn giá 1.290.000 ₫')).toBeInTheDocument();
    expect(within(box).getAllByText('2.580.000 ₫')).toHaveLength(2);
    expect(within(box).getByText('Tạm tính')).toBeInTheDocument();
    expect(within(box).getByText('Chỉ xem, không sửa giá tại đây')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Xác nhận/ })).not.toBeInTheDocument();
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('labels an issued quote past its validity as expired, so staff do not repeat a stale price (security L-2)', async () => {
    await openHeld({ [`GET ${QUOTE}`]: () => json(200, { ...STAFF_QUOTE, validUntil: '2020-01-03T08:00:00Z' }) });

    const box = await screen.findByRole('region', { name: 'Báo giá BG-ABCDEFGH' });
    expect(within(box).getByText('Hết hiệu lực')).toBeInTheDocument();
    expect(within(box).queryByText('Đã gửi khách')).not.toBeInTheDocument();
  });

  it('marks quote states that need staff with a warning label', async () => {
    await openHeld({ [`GET ${QUOTE}`]: () => json(200, { ...STAFF_QUOTE, status: 'ORDER_OUTCOME_UNKNOWN' }) });

    const badge = await screen.findByText('Chưa rõ kết quả');
    expect(badge.getAttribute('style') ?? '').toContain('--status-warning');
  });

  it('says there is no quote yet (404 after a readable conversation)', async () => {
    await openHeld({ [`GET ${QUOTE}`]: () => problem(404, 'NOT_FOUND') });

    expect(await screen.findByText('Chưa có báo giá.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Cuộc trò chuyện/ })).toBeInTheDocument();
  });

  it('shows no prices when the price read could not be recorded (503)', async () => {
    await openHeld({ [`GET ${QUOTE}`]: () => problem(503, 'SERVICE_UNAVAILABLE') });

    expect(await screen.findByText('Tạm thời không hiển thị báo giá')).toBeInTheDocument();
    expect(screen.getByText(/chưa ghi được nhật ký cho lần xem này nên không hiện báo giá/)).toBeInTheDocument();
    expect(screen.getByText('Mã tham chiếu: REQ-1A2B3C4D')).toBeInTheDocument();
    expect(screen.queryByText(/₫/)).not.toBeInTheDocument();
  });

  it('shows "not found" when the access ended (404 on the messages), and goes back to the inbox on request (F6)', async () => {
    const { backend, user } = setup({ [`GET ${MESSAGES}`]: () => problem(404, 'NOT_FOUND') });
    await user.click(await screen.findByRole('button', { name: /^Mở cuộc trò chuyện/ }));

    expect(await screen.findByText('Không tìm thấy cuộc trò chuyện')).toBeInTheDocument();
    expect(screen.getByText('Yêu cầu đã hoàn tất hoặc không còn do bạn xử lý. Quay lại hộp chờ để xem yêu cầu khác.')).toBeInTheDocument();
    expect(screen.getByText(/REQ-1A2B3C4D/)).toBeInTheDocument();
    expect(count(backend, 'GET', QUOTE)).toBe(0);
    expect(count(backend, 'GET', LIST)).toBe(1);

    await user.click(screen.getByRole('button', { name: 'Về hộp chờ' }));
    expect(screen.getByRole('heading', { name: 'Hộp chờ nhân viên' })).toHaveFocus();
    await waitFor(() => expect(count(backend, 'GET', LIST)).toBe(2));
  });

  it('sends a message, says so only after the answer, and reads the messages again', async () => {
    const { backend, user } = await openHeld({ [`POST ${MESSAGES}`]: () => new Response(null, { status: 204 }) });
    await user.type(screen.getByLabelText('Trả lời khách hàng'), 'Chào bạn, tôi hỗ trợ tiếp.');
    await user.click(screen.getByRole('button', { name: 'Gửi' }));

    expect(await screen.findByText('Đã gửi tin cho khách.')).toBeInTheDocument();
    expect(JSON.parse(backend.calls.find((c) => c.method === 'POST' && c.path === MESSAGES)?.body ?? '{}'))
      .toEqual({ clientEventId: expect.stringMatching(UUID), text: 'Chào bạn, tôi hỗ trợ tiếp.' });
    expect(screen.getByLabelText('Trả lời khách hàng')).toHaveValue('');
    expect(count(backend, 'GET', MESSAGES)).toBe(2);
    expect(count(backend, 'GET', QUOTE)).toBe(1);
  });

  it('keeps the text when the conversation is busy (409)', async () => {
    const { user } = await openHeld({ [`POST ${MESSAGES}`]: () => problem(409, 'CONFLICT') });
    await user.type(screen.getByLabelText('Trả lời khách hàng'), 'Chờ tôi chút.');
    await user.click(screen.getByRole('button', { name: 'Gửi' }));

    expect(await screen.findByText('Cuộc trò chuyện đang bận')).toBeInTheDocument();
    expect(screen.getByLabelText('Trả lời khách hàng')).toHaveValue('Chờ tôi chút.');
  });

  it('never resends by itself when the answer did not arrive, and reads nothing (STAFF-MSG-ID 4A)', async () => {
    const { backend, user } = await openHeld({ [`POST ${MESSAGES}`]: () => networkFailure() });
    await user.type(screen.getByLabelText('Trả lời khách hàng'), 'Chờ tôi chút.');
    await user.click(screen.getByRole('button', { name: 'Gửi' }));

    expect(await screen.findByText('Chưa rõ tin đã gửi được chưa.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gửi lại' })).toBeInTheDocument();
    expect(count(backend, 'POST', MESSAGES)).toBe(1);
    expect(count(backend, 'GET', MESSAGES)).toBe(1);
    expect(screen.getByLabelText('Trả lời khách hàng')).toHaveValue('Chờ tôi chút.');
  });

  it('removes the conversation from the screen when the access ended before a send (404)', async () => {
    const { user } = await openHeld({ [`POST ${MESSAGES}`]: () => problem(404, 'NOT_FOUND') });
    await user.type(screen.getByLabelText('Trả lời khách hàng'), 'Chờ tôi chút.');
    await user.click(screen.getByRole('button', { name: 'Gửi' }));

    expect(await screen.findByText('Không tìm thấy cuộc trò chuyện')).toBeInTheDocument();
    expect(screen.queryByText('Cho tôi giá tốt hơn nếu lấy 50 bộ')).not.toBeInTheDocument();
    expect(screen.queryByText(/₫/)).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Về hộp chờ' })).toHaveFocus();
  });

  it('reads new messages on request without reading the prices again (decision 2A)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { backend, user } = await openHeld();
    await screen.findByRole('region', { name: 'Báo giá BG-ABCDEFGH' });

    await act(async () => { await vi.advanceTimersByTimeAsync(60_000); });
    expect(count(backend, 'GET', MESSAGES)).toBe(1);
    await user.click(screen.getByRole('button', { name: 'Tải tin mới' }));
    await waitFor(() => expect(count(backend, 'GET', MESSAGES)).toBe(2));
    expect(count(backend, 'GET', QUOTE)).toBe(1);
  });

  it('asks before resolving, and Hủy changes nothing (decision 3A)', async () => {
    const { backend, user } = await openHeld();
    await user.click(screen.getByRole('button', { name: 'Hoàn tất và mở lại AI' }));
    const dialog = screen.getByRole('alertdialog', { name: 'Hoàn tất và mở lại trợ lý AI?' });
    expect(await axeViolations(document.body)).toEqual([]);

    await user.click(within(dialog).getByRole('button', { name: 'Hủy' }));
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument();
    expect(count(backend, 'POST', `${LIST}/7/resolve`)).toBe(0);
  });

  it('resolves and returns to the inbox, saying the AI answers again', async () => {
    const { backend, user } = await openHeld({ [`POST ${LIST}/7/resolve`]: () => json(200, handoff({ status: 'RESOLVED' })) });
    await user.click(screen.getByRole('button', { name: 'Hoàn tất và mở lại AI' }));
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Hoàn tất và mở lại AI' }));

    expect(await screen.findByText('Trợ lý AI trả lời khách trở lại.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Hộp chờ nhân viên' })).toBeInTheDocument();
    await waitFor(() => expect(count(backend, 'GET', LIST)).toBe(2));
  });

  it('says a follow-up handoff was opened and the AI stays paused', async () => {
    const { user } = await openHeld({ [`POST ${LIST}/7/resolve`]: () => json(200, handoff({ status: 'RESOLVED', followUpReasonCode: 'ERP_UNKNOWN' })) });
    await user.click(screen.getByRole('button', { name: 'Hoàn tất và mở lại AI' }));
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Hoàn tất và mở lại AI' }));

    expect(await screen.findByText('Hệ thống đã mở yêu cầu mới (Chưa rõ kết quả ERP); trợ lý AI vẫn tạm dừng.')).toBeInTheDocument();
  });

  it('does not claim a resolve it could not confirm', async () => {
    const { user } = await openHeld({ [`POST ${LIST}/7/resolve`]: () => networkFailure() });
    await user.click(screen.getByRole('button', { name: 'Hoàn tất và mở lại AI' }));
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Hoàn tất và mở lại AI' }));

    expect(await screen.findByText('Chưa xác nhận được việc hoàn tất.')).toBeInTheDocument();
    expect(screen.queryByText('Đã hoàn tất.')).not.toBeInTheDocument();
  });

  it('stays on the conversation when the resolve is refused as busy (409)', async () => {
    const { user } = await openHeld({ [`POST ${LIST}/7/resolve`]: () => problem(409, 'CONFLICT') });
    await user.click(screen.getByRole('button', { name: 'Hoàn tất và mở lại AI' }));
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Hoàn tất và mở lại AI' }));

    expect(await screen.findByText('Chưa hoàn tất được.')).toBeInTheDocument();
    expect(screen.queryByText('Cuộc trò chuyện đang bận')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: /Cuộc trò chuyện/ })).toBeInTheDocument();
  });
});

// Reviews of EP-10c (2026-11-25): UX M1–M3, L2; security M-1, M-2, L-1, L-3, L-4.
describe('EP-10c review fixes', () => {
  it('returns focus to the message box after a send (UX M1)', async () => {
    const { user } = await openHeld({ [`POST ${MESSAGES}`]: () => new Response(null, { status: 204 }) });
    await user.type(screen.getByLabelText('Trả lời khách hàng'), 'Chào bạn.');
    await user.click(screen.getByRole('button', { name: 'Gửi' }));

    await screen.findByText('Đã gửi tin cho khách.');
    await waitFor(() => expect(screen.getByLabelText('Trả lời khách hàng')).toHaveFocus());
  });

  it('says what a read on request found (UX M2)', async () => {
    const { backend, user } = await openHeld();
    await user.click(screen.getByRole('button', { name: 'Tải tin mới' }));
    expect(await screen.findByText('Chưa có tin mới.')).toBeInTheDocument();

    backend.set(`GET ${MESSAGES}`, () => json(200, [message('CUSTOMER', 'a'), message('BOT', 'b'), message('CUSTOMER', 'c'), message('CUSTOMER', 'Còn đó không?')]));
    await user.click(screen.getByRole('button', { name: 'Tải tin mới' }));
    expect(await screen.findByText('Có 1 tin mới.')).toBeInTheDocument();
  });

  it('sends one read when "Tải tin mới" is pressed repeatedly (security M-1)', async () => {
    const { backend, user } = await openHeld();
    let answer: (r: Response) => void = () => {};
    backend.set(`GET ${MESSAGES}`, () => new Promise<Response>((resolve) => { answer = resolve; }));
    const before = count(backend, 'GET', MESSAGES);

    await user.click(screen.getByRole('button', { name: 'Tải tin mới' }));
    await user.click(screen.getByRole('button', { name: /Đang tải/ }));
    await user.click(screen.getByRole('button', { name: /Đang tải/ }));
    expect(count(backend, 'GET', MESSAGES)).toBe(before + 1);
    answer(json(200, []));
  });

  const sentBodies = (backend: ReturnType<typeof fakeBackend>) =>
    backend.calls.filter((c) => c.method === 'POST' && c.path === MESSAGES).map((c) => JSON.parse(c.body ?? '{}') as { clientEventId: string; text: string });

  it('resends an unknown send with the same event ID, so it is stored once (STAFF-MSG-ID, security M-2)', async () => {
    const { backend, user } = await openHeld({ [`POST ${MESSAGES}`]: () => networkFailure() });
    await user.type(screen.getByLabelText('Trả lời khách hàng'), 'Chờ tôi chút.');
    await user.click(screen.getByRole('button', { name: 'Gửi' }));
    await screen.findByText('Chưa rõ tin đã gửi được chưa.');

    backend.set(`POST ${MESSAGES}`, () => new Response(null, { status: 204 }));
    await user.click(screen.getByRole('button', { name: 'Gửi lại' }));

    expect(await screen.findByText('Đã gửi tin cho khách.')).toBeInTheDocument();
    const [first, second] = sentBodies(backend);
    expect(first?.clientEventId).toMatch(UUID);
    expect(second).toEqual(first);
    expect(screen.getByLabelText('Trả lời khách hàng')).toHaveValue('');
  });

  it('uses a new event ID once the text changed or the last send succeeded', async () => {
    const { backend, user } = await openHeld({ [`POST ${MESSAGES}`]: () => networkFailure() });
    const box = screen.getByLabelText('Trả lời khách hàng');
    await user.type(box, 'Chờ tôi chút.');
    await user.click(screen.getByRole('button', { name: 'Gửi' }));
    await screen.findByText('Chưa rõ tin đã gửi được chưa.');
    await user.type(box, ' Cảm ơn.');
    expect(screen.getByRole('button', { name: 'Gửi' })).toBeInTheDocument();

    backend.set(`POST ${MESSAGES}`, () => new Response(null, { status: 204 }));
    await user.click(screen.getByRole('button', { name: 'Gửi' }));
    await screen.findByText('Đã gửi tin cho khách.');
    await user.type(box, 'Chờ tôi chút. Cảm ơn.');
    await user.click(screen.getByRole('button', { name: 'Gửi' }));
    await waitFor(() => expect(sentBodies(backend)).toHaveLength(3));

    const ids = sentBodies(backend).map((b) => b.clientEventId);
    expect(new Set(ids).size).toBe(3);
  });

  it.each([
    ['still claimed', () => json(200, handoff({ status: 'CLAIMED' }))],
    ['about another handoff', () => json(200, handoff({ handoffId: 99, status: 'RESOLVED' }))],
  ])('never says "Đã hoàn tất" for a resolve answer %s (security L-1)', async (_label, answerWith) => {
    const { user } = await openHeld({ [`POST ${LIST}/7/resolve`]: answerWith });
    await user.click(screen.getByRole('button', { name: 'Hoàn tất và mở lại AI' }));
    await user.click(within(screen.getByRole('alertdialog')).getByRole('button', { name: 'Hoàn tất và mở lại AI' }));

    expect(await screen.findByText('Chưa xác nhận được việc hoàn tất.')).toBeInTheDocument();
    expect(screen.queryByText('Đã hoàn tất.')).not.toBeInTheDocument();
  });

  it('never says "Đã nhận xử lý" for a claim answer about another handoff (security L-1)', async () => {
    const { user } = setup({ [`POST ${LIST}/1/claim`]: () => json(200, handoff({ handoffId: 2 })) });
    await user.click(await screen.findByRole('button', { name: /^Nhận xử lý cuộc trò chuyện/ }));

    expect(await screen.findByText('Chưa xác nhận được việc nhận xử lý.')).toBeInTheDocument();
    expect(screen.queryByText('Đã nhận xử lý.')).not.toBeInTheDocument();
  });

  it('moves focus to the claimed row after the reload (UX M3)', async () => {
    let reads = 0;
    const { user } = setup({
      [`POST ${LIST}/1/claim`]: () => json(200, handoff({ handoffId: 1, conversationId: CONV_2 })),
      [`GET ${LIST}`]: () => json(200, ++reads === 1 ? INBOX : [handoff({ handoffId: 1, conversationId: CONV_2 })]),
    });
    await user.click(await screen.findByRole('button', { name: /^Nhận xử lý cuộc trò chuyện/ }));

    await waitFor(() => expect(screen.getByRole('button', { name: 'Mở cuộc trò chuyện …BBBBBB' })).toHaveFocus());
  });

  it('warns in the resolve dialog that an unsent draft is dropped (UX L2)', async () => {
    const { user } = await openHeld();
    await user.type(screen.getByLabelText('Trả lời khách hàng'), 'Bản nháp');
    await user.click(screen.getByRole('button', { name: 'Hoàn tất và mở lại AI' }));

    expect(within(screen.getByRole('alertdialog')).getByText('Tin đang soạn chưa được gửi và sẽ không được gửi.')).toBeInTheDocument();
  });

  it('shows "not found" and no messages when the quote read is refused (403, security L-3)', async () => {
    const { user } = setup({ [`GET ${QUOTE}`]: () => problem(403, 'FORBIDDEN') });
    await user.click(await screen.findByRole('button', { name: /^Mở cuộc trò chuyện/ }));

    expect(await screen.findByText('Không tìm thấy cuộc trò chuyện')).toBeInTheDocument();
    expect(screen.queryByText('Cho tôi giá tốt hơn nếu lấy 50 bộ')).not.toBeInTheDocument();
  });

  it('refuses an inbox row with a field it does not know (security L-4)', async () => {
    setup({ [`GET ${LIST}`]: () => json(200, [handoff({ customerName: 'Khách A' })]) });

    expect(await screen.findByText('Chưa tải được hộp chờ')).toBeInTheDocument();
    expect(screen.queryByText('Khách A')).not.toBeInTheDocument();
  });
});

// P3 review UX M1/M3.
describe('P3 review fixes', () => {
  it('reads the quote once the messages can be read after a failed first read (UX M1)', async () => {
    const { backend, user } = setup({ [`GET ${MESSAGES}`]: () => problem(500, 'INTERNAL') });
    await user.click(await screen.findByRole('button', { name: /^Mở cuộc trò chuyện/ }));
    expect(await screen.findByText('Chưa tải được tin nhắn')).toBeInTheDocument();
    expect(count(backend, 'GET', QUOTE)).toBe(0);

    backend.set(`GET ${MESSAGES}`, () => json(200, []));
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('region', { name: 'Báo giá BG-ABCDEFGH' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Tải tin mới' }));
    await waitFor(() => expect(count(backend, 'GET', MESSAGES)).toBe(3));
    expect(count(backend, 'GET', QUOTE)).toBe(1);
  });

  it('keeps focus off <body> after a refused claim (UX M3)', async () => {
    const { user } = setup({ [`POST ${LIST}/1/claim`]: () => problem(409, 'CONFLICT') });
    await user.click(await screen.findByRole('button', { name: /^Nhận xử lý cuộc trò chuyện/ }));

    await screen.findByText('Cuộc trò chuyện đang bận');
    await waitFor(() => expect(document.activeElement).not.toBe(document.body));
  });
});

describe('decodeStaffQuote', () => {
  it('refuses a staff quote that carries a confirmation token or any field it does not know', () => {
    expect(() => decodeStaffQuote({ ...STAFF_QUOTE, confirmationToken: 'qc_1111111111111111111111' })).toThrow();
    expect(() => decodeStaffQuote({ ...STAFF_QUOTE, costPrice: '1000000' })).toThrow();
    expect(() => decodeStaffQuote({ ...STAFF_QUOTE, version: 1_000_000 })).toThrow();
  });

  it.each([[{ total: '1e6' }], [{ status: 'PAID' }], [{ lines: [] }], [{ quoteRef: 'X' }]])('refuses %j', (change) => {
    expect(() => decodeStaffQuote({ ...STAFF_QUOTE, ...change })).toThrow();
  });
});
