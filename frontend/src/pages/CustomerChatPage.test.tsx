import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { axeViolations, fakeBackend, json, networkFailure, problem, type Handler } from '../test/fakeBackend';
import { conversationMemory } from '../app/conversationMemory';
import { CustomerChatPage, STAFF_POLL_MS } from './CustomerChatPage';

// F1 · CH-01..CH-04 (UC-01, UC-03/04 customer side). Synthetic conversation, quote and token values only.
const CONV = 'cv_AAAAAAAAAAAAAAAAAAAAAA';
const CONV_2 = 'cv_BBBBBBBBBBBBBBBBBBBBBB';
const REF = 'BG-ABCDEFGH';
const TOKEN_1 = 'qc_1111111111111111111111';
const TOKEN_2 = 'qc_2222222222222222222222';
const MESSAGES = `/api/demo/conversations/${CONV}/messages`;
const STATE = `/api/demo/conversations/${CONV}`;
const CONFIRM = `/api/demo/quotes/${REF}/confirm`;

const card = (version = 1, token = TOKEN_1) => ({
  quoteRef: REF, version, currency: 'VND', validUntil: '2026-11-26T09:41:00Z', confirmationToken: token,
  lines: [{ sku: 'SKU-BRK-PAD-A', quantity: 2, unitPrice: version === 1 ? '1250000' : '1290000', lineTotal: version === 1 ? '2500000' : '2580000' }],
  total: version === 1 ? '2500000' : '2580000',
  notes: ['Giá chưa gồm VAT', 'Báo giá theo mã phụ tùng; vui lòng kiểm tra phụ tùng phù hợp với xe trước khi đặt hàng'],
});
const turn = (extra: Record<string, unknown> = {}) => json(200, { turnId: 't', reply: 'Đây là báo giá cho bạn.', handedOff: false, duplicate: false, quoteCard: null, ...extra });
const message = (direction: string, text: string, sentAt = '2020-01-02T08:00:00Z') => ({ direction, text, sentAt });

function setup(routes: Record<string, Handler>, { remembered = false, linked = true } = {}) {
  if (remembered) window.sessionStorage.setItem('apas.chat.conversation', CONV);
  const backend = fakeBackend({
    'POST /api/demo/conversations': () => json(201, { conversationId: CONV }),
    [`GET ${MESSAGES}`]: () => json(200, []),
    [`GET ${STATE}`]: () => json(200, { state: 'BOT' }),
    ...routes,
  });
  const onUnauthenticated = vi.fn();
  const view = render(<CustomerChatPage customerLinked={linked} onUnauthenticated={onUnauthenticated} />);
  return { backend, view, onUnauthenticated, user: userEvent.setup() };
}

const posts = (backend: ReturnType<typeof fakeBackend>, path: string) => backend.apiCalls().filter((c) => c.method === 'POST' && c.path === path);

async function sendText(user: ReturnType<typeof userEvent.setup>, text: string) {
  await user.type(await screen.findByLabelText('Nội dung tin nhắn'), text);
  await user.click(screen.getByRole('button', { name: 'Gửi' }));
}

/** A conversation with an issued quote card on screen. */
async function withCard(routes: Record<string, Handler> = {}) {
  const ctx = setup({ [`POST ${MESSAGES}`]: () => turn({ quoteCard: card() }), ...routes });
  await sendText(ctx.user, 'Báo giá 2 bộ má phanh trước');
  await screen.findByRole('button', { name: 'Xác nhận báo giá' });
  return ctx;
}

afterEach(() => { vi.useRealTimers(); });

describe('CustomerChatPage · conversation', () => {
  it('opens a new conversation, remembers it in this tab, and passes the axe check', async () => {
    const { backend, view } = setup({});

    expect(await screen.findByText('Hãy nhắn mã OE, mã phụ tùng hoặc mô tả phụ tùng bạn cần.')).toBeInTheDocument();
    expect(posts(backend, '/api/demo/conversations')).toHaveLength(1);
    expect(window.sessionStorage.getItem('apas.chat.conversation')).toBe(CONV);
    expect(window.localStorage.length).toBe(0);
    expect(screen.getByLabelText('Nội dung tin nhắn')).toHaveAttribute('maxLength', '2000');
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('reopens the remembered conversation with its messages, authors and times', async () => {
    const { backend } = setup({
      [`GET ${MESSAGES}`]: () => json(200, [message('CUSTOMER', 'Cần má phanh trước'), message('BOT', 'Bạn cho tôi mã OE nhé.'), message('STAFF', 'Chào bạn, tôi hỗ trợ tiếp.')]),
    }, { remembered: true });

    expect(await screen.findByText('Cần má phanh trước')).toBeInTheDocument();
    expect(screen.getByText('Bạn cho tôi mã OE nhé.')).toBeInTheDocument();
    expect(screen.getByText('Trợ lý AI')).toBeInTheDocument();
    expect(screen.getByText('Nhân viên tư vấn')).toBeInTheDocument();
    expect(screen.getAllByText('02/01/2020 08:00').length).toBe(3);
    expect(posts(backend, '/api/demo/conversations')).toHaveLength(0);
  });

  it('shows only the time for a message sent today', async () => {
    const now = new Date();
    now.setUTCSeconds(0, 0);
    setup({ [`GET ${MESSAGES}`]: () => json(200, [message('CUSTOMER', 'Hôm nay', now.toISOString())]) }, { remembered: true });

    const hhmm = `${String(now.getUTCHours()).padStart(2, '0')}:${String(now.getUTCMinutes()).padStart(2, '0')}`;
    expect(await screen.findByText(hhmm)).toBeInTheDocument();
  });

  it('opens a new conversation when the remembered one is no longer known', async () => {
    const { backend } = setup({
      [`GET ${MESSAGES}`]: () => problem(404, 'NOT_FOUND'),
      'POST /api/demo/conversations': () => json(201, { conversationId: CONV_2 }),
    }, { remembered: true });

    await screen.findByLabelText('Nội dung tin nhắn');
    expect(posts(backend, '/api/demo/conversations')).toHaveLength(1);
    expect(window.sessionStorage.getItem('apas.chat.conversation')).toBe(CONV_2);
  });

  it('explains an unlinked account without calling the chat API', async () => {
    const { backend } = setup({}, { linked: false });

    expect(screen.getByText('Bạn chưa dùng được kênh chat này')).toBeInTheDocument();
    expect(screen.queryByLabelText('Nội dung tin nhắn')).not.toBeInTheDocument();
    expect(backend.calls).toHaveLength(0);
  });

  it('explains an unlinked account when the backend refuses it', async () => {
    setup({ 'POST /api/demo/conversations': () => problem(403, 'CUSTOMER_NOT_LINKED') });

    expect(await screen.findByText('Bạn chưa dùng được kênh chat này')).toBeInTheDocument();
    expect(screen.queryByLabelText('Nội dung tin nhắn')).not.toBeInTheDocument();
  });

  it('fails closed on a message it cannot attribute, with a retry', async () => {
    const { backend, user } = setup({ [`GET ${MESSAGES}`]: () => json(200, [message('SYSTEM', 'x')]) }, { remembered: true });

    expect(await screen.findByText('Chưa mở được cuộc trò chuyện')).toBeInTheDocument();
    expect(screen.queryByText('x')).not.toBeInTheDocument();
    backend.set(`GET ${MESSAGES}`, () => json(200, []));
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByLabelText('Nội dung tin nhắn')).toBeInTheDocument();
  });

  it('hands an expired session back to the app', async () => {
    const { onUnauthenticated } = setup({ 'POST /api/demo/conversations': () => problem(401, 'UNAUTHENTICATED') });

    await waitFor(() => expect(onUnauthenticated).toHaveBeenCalledTimes(1));
  });
});

describe('CustomerChatPage · sending (CH-04)', () => {
  it('sends the text with a client event ID, shows the lookup state, then the stored history', async () => {
    let answer: (r: Response) => void = () => {};
    const { backend, user } = setup({ [`POST ${MESSAGES}`]: () => new Promise<Response>((resolve) => { answer = resolve; }) });
    await sendText(user, 'Cần má phanh trước');

    expect(await screen.findByText('Đang tra cứu…')).toBeInTheDocument();
    expect(screen.queryByText('Đang gửi…')).not.toBeInTheDocument();
    const body = JSON.parse(posts(backend, MESSAGES)[0]?.body ?? '{}') as { clientEventId: string; text: string };
    expect(body.text).toBe('Cần má phanh trước');
    expect(body.clientEventId).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);

    backend.set(`GET ${MESSAGES}`, () => json(200, [message('CUSTOMER', 'Cần má phanh trước'), message('BOT', 'Bạn cho tôi mã OE nhé.')]));
    answer(turn({ reply: 'Bạn cho tôi mã OE nhé.' }));
    expect(await screen.findByText('Bạn cho tôi mã OE nhé.')).toBeInTheDocument();
    expect(screen.getByLabelText('Nội dung tin nhắn')).toHaveValue('');
  });

  it.each<[string, Handler, string]>([
    ['409', () => problem(409, 'CONFLICT'), 'Tin trước đang được xử lý'],
    ['429', () => problem(429, 'TOO_MANY_REQUESTS'), 'Bạn đã gửi nhiều yêu cầu trong thời gian ngắn'],
  ])('keeps what the customer typed on %s', async (_status, failure, title) => {
    const { user } = setup({ [`POST ${MESSAGES}`]: failure });
    await sendText(user, 'Còn hàng không?');

    expect(await screen.findByText(title)).toBeInTheDocument();
    expect(screen.getByLabelText('Nội dung tin nhắn')).toHaveValue('Còn hàng không?');
  });

  it('resends a message without an answer under the same client event ID', async () => {
    const { backend, user } = setup({ [`POST ${MESSAGES}`]: () => networkFailure() });
    await sendText(user, 'Còn hàng không?');
    expect(await screen.findByText('Chưa nhận được phản hồi')).toBeInTheDocument();
    expect(screen.getByLabelText('Nội dung tin nhắn')).toHaveValue('Còn hàng không?');

    backend.set(`POST ${MESSAGES}`, () => turn({ reply: 'Còn hàng.', duplicate: true }));
    await user.click(screen.getByRole('button', { name: 'Gửi lại' }));
    await waitFor(() => expect(screen.getByLabelText('Nội dung tin nhắn')).toHaveValue(''));

    const ids = posts(backend, MESSAGES).map((c) => (JSON.parse(c.body ?? '{}') as { clientEventId: string }).clientEventId);
    expect(ids).toHaveLength(2);
    expect(ids[1]).toBe(ids[0]);
  });

  it('uses a new client event ID once the text is changed', async () => {
    const { backend, user } = setup({ [`POST ${MESSAGES}`]: () => problem(503, 'SERVICE_UNAVAILABLE') });
    await sendText(user, 'Còn hàng không?');
    await screen.findByText('Chưa nhận được phản hồi');

    await user.type(screen.getByLabelText('Nội dung tin nhắn'), ' Cho tôi 2 bộ.');
    await user.click(screen.getByRole('button', { name: 'Gửi' }));
    await waitFor(() => expect(posts(backend, MESSAGES)).toHaveLength(2));
    const ids = posts(backend, MESSAGES).map((c) => (JSON.parse(c.body ?? '{}') as { clientEventId: string }).clientEventId);
    expect(ids[1]).not.toBe(ids[0]);
  });

  it('opens a new conversation and keeps the text when the conversation is gone', async () => {
    const { backend, user } = setup({ [`POST ${MESSAGES}`]: () => problem(404, 'NOT_FOUND') });
    await sendText(user, 'Còn hàng không?');

    expect(await screen.findByText('Không tìm thấy cuộc trò chuyện')).toBeInTheDocument();
    expect(posts(backend, '/api/demo/conversations')).toHaveLength(2);
    expect(screen.getByLabelText('Nội dung tin nhắn')).toHaveValue('Còn hàng không?');
  });

  it('does not send on Enter while an input method is still composing a character', async () => {
    const { backend, user } = setup({ [`POST ${MESSAGES}`]: () => turn() });
    const field = await screen.findByLabelText('Nội dung tin nhắn');
    await user.type(field, 'má phanh');

    fireEvent.keyDown(field, { key: 'Enter', isComposing: true });
    // Sending is asynchronous (CSRF read first): give a send that should not exist time to show up.
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 50)); });
    expect(backend.calls.filter((c) => c.method === 'POST' && c.path !== '/api/demo/conversations')).toHaveLength(0);
    expect(screen.queryByText('Đang gửi…')).not.toBeInTheDocument();
    fireEvent.keyDown(field, { key: 'Enter' });
    await waitFor(() => expect(posts(backend, MESSAGES)).toHaveLength(1));
  });

  it('does not send a message longer than 2,000 characters even if the field is bypassed', async () => {
    const { backend, user } = setup({});
    const field = await screen.findByLabelText('Nội dung tin nhắn');
    field.removeAttribute('maxLength');
    await user.click(field);
    await user.paste('x'.repeat(2001));
    await user.keyboard('{Enter}');

    expect(posts(backend, MESSAGES)).toHaveLength(0);
  });
});

describe('CustomerChatPage · handoff (CH-02)', () => {
  it('shows the waiting banner after a handed-off turn and never a reason', async () => {
    const { backend, user } = setup({ [`POST ${MESSAGES}`]: () => turn({ reply: 'Yêu cầu của bạn đã được chuyển cho nhân viên tư vấn.', handedOff: true }) });
    backend.set(`GET ${STATE}`, () => json(200, { state: 'WAITING_STAFF' }));
    await sendText(user, 'Cho tôi giá tốt hơn nếu lấy 50 bộ');

    expect(await screen.findByText('Đang chờ nhân viên tư vấn')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/PROVIDER|DLP|PROMPT|reason|lý do/i);
  });

  it('reads the conversation again every 5 s while staff handle it, and stops when the AI is back', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { backend } = setup({ [`GET ${STATE}`]: () => json(200, { state: 'WAITING_STAFF' }) }, { remembered: true });
    await screen.findByText('Đang chờ nhân viên tư vấn');

    backend.set(`GET ${MESSAGES}`, () => json(200, [message('STAFF', 'Chào bạn, tôi là nhân viên tư vấn.')]));
    backend.set(`GET ${STATE}`, () => json(200, { state: 'STAFF' }));
    await act(async () => { await vi.advanceTimersByTimeAsync(STAFF_POLL_MS); });
    expect(await screen.findByText('Chào bạn, tôi là nhân viên tư vấn.')).toBeInTheDocument();
    expect(screen.getByText('Nhân viên tư vấn đã tham gia')).toBeInTheDocument();

    backend.set(`GET ${STATE}`, () => json(200, { state: 'BOT' }));
    await act(async () => { await vi.advanceTimersByTimeAsync(STAFF_POLL_MS); });
    await waitFor(() => expect(screen.queryByText('Nhân viên tư vấn đã tham gia')).not.toBeInTheDocument());
    const reads = backend.calls.filter((c) => c.path === STATE).length;
    await act(async () => { await vi.advanceTimersByTimeAsync(STAFF_POLL_MS * 3); });
    expect(backend.calls.filter((c) => c.path === STATE).length).toBe(reads);
  });

  it('does not read the conversation again while the assistant answers', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { backend } = setup({}, { remembered: true });
    await screen.findByLabelText('Nội dung tin nhắn');
    const reads = backend.calls.length;

    await act(async () => { await vi.advanceTimersByTimeAsync(STAFF_POLL_MS * 3); });
    expect(backend.calls.length).toBe(reads);
  });
});

describe('CustomerChatPage · quote card (CH-01) and confirmation (CH-03)', () => {
  it('shows the server-rendered card with exact amounts, the server notes and no payable total', async () => {
    const { view } = await withCard();
    const panel = screen.getByRole('region', { name: `Báo giá ${REF}` });

    expect(within(panel).getByText('Chờ xác nhận')).toBeInTheDocument();
    expect(within(panel).getByText('SKU-BRK-PAD-A')).toBeInTheDocument();
    expect(within(panel).getByText('2 × 1.250.000 ₫')).toBeInTheDocument();
    expect(within(panel).getAllByText('2.500.000 ₫')).toHaveLength(2);
    expect(within(panel).getByText('Tạm tính')).toBeInTheDocument();
    expect(within(panel).getByText('Giá chưa gồm VAT')).toBeInTheDocument();
    expect(within(panel).getByText(/Giá do hệ thống lập/)).toBeInTheDocument();
    expect(within(panel).getByText(/Phiên bản 1 · Hiệu lực đến 26\/11\/2026 09:41/)).toBeInTheDocument();
    expect(panel.textContent).not.toMatch(/Tổng thanh toán|gồm VAT:|qc_/);
    expect(window.sessionStorage.getItem('apas.chat.quote')).toBe(REF);
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('never confirms from chat text such as "ok"', async () => {
    const { backend, user } = await withCard();
    await sendText(user, 'ok');

    await waitFor(() => expect(posts(backend, MESSAGES)).toHaveLength(2));
    expect(posts(backend, CONFIRM)).toHaveLength(0);
  });

  it('confirms exactly this reference, version and token, then shows the request as recorded', async () => {
    const { backend, user } = await withCard({ [`POST ${CONFIRM}`]: () => json(200, { quoteRef: REF, version: 1, status: 'DRAFT_ORDER_CREATED', handedOff: false }) });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    expect(await screen.findAllByText('Đã gửi yêu cầu đặt hàng')).toHaveLength(2);
    expect(JSON.parse(posts(backend, CONFIRM)[0]?.body ?? '{}')).toEqual({ version: 1, confirmationToken: TOKEN_1 });
    expect(screen.queryByRole('button', { name: /Xác nhận/ })).not.toBeInTheDocument();
  });

  it('sends one confirmation when the button is pressed twice', async () => {
    let answer: (r: Response) => void = () => {};
    const { backend, user } = await withCard({ [`POST ${CONFIRM}`]: () => new Promise<Response>((resolve) => { answer = resolve; }) });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));
    await user.click(await screen.findByRole('button', { name: /Đang gửi xác nhận/ }));
    answer(json(200, { quoteRef: REF, version: 1, status: 'DRAFT_ORDER_CREATED', handedOff: false }));

    await screen.findAllByText('Đã gửi yêu cầu đặt hàng');
    expect(posts(backend, CONFIRM)).toHaveLength(1);
  });

  it('shows "Đang kiểm tra" for a pending outcome, never offers a resend, and only reads again', async () => {
    const { backend, user } = await withCard({
      [`POST ${CONFIRM}`]: () => json(202, { quoteRef: REF, version: 1, status: 'ORDER_OUTCOME_UNKNOWN', handedOff: true }),
      [`GET /api/demo/quotes/${REF}`]: () => json(200, { quoteRef: REF, version: 1, status: 'ORDER_OUTCOME_UNKNOWN', quoteCard: null }),
    });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    expect(await screen.findByText('Chưa nhận được kết quả từ hệ thống')).toBeInTheDocument();
    expect(screen.getByText('Đang kiểm tra')).toBeInTheDocument();
    expect(screen.getByText(`${REF} · phiên bản 1`)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Xác nhận/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Xem kết quả mới nhất' }));
    await waitFor(() => expect(backend.calls.filter((c) => c.path === `/api/demo/quotes/${REF}`)).toHaveLength(1));
    expect(posts(backend, CONFIRM)).toHaveLength(1);
  });

  it('never resends a confirmation whose answer did not arrive', async () => {
    const { backend, user } = await withCard({
      [`POST ${CONFIRM}`]: () => networkFailure(),
      [`GET /api/demo/quotes/${REF}`]: () => json(200, { quoteRef: REF, version: 1, status: 'DRAFT_ORDER_CREATED', quoteCard: null }),
    });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    expect(await screen.findByText('Chưa nhận được kết quả')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Xác nhận/ })).not.toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Xem kết quả mới nhất' }));
    expect(await screen.findByText('Đã gửi yêu cầu đặt hàng')).toBeInTheDocument();
    expect(posts(backend, CONFIRM)).toHaveLength(1);
  });

  it('locks an expired quote (410)', async () => {
    const { user } = await withCard({ [`POST ${CONFIRM}`]: () => problem(410, 'QUOTE_EXPIRED') });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    expect(await screen.findByText(/Báo giá này không còn dùng để đặt hàng/)).toBeInTheDocument();
    // F1-QuoteStates "Hết hiệu lực · 410": the button stays, locked.
    expect(screen.getByRole('button', { name: 'Xác nhận báo giá' })).toBeDisabled();
    expect(screen.getAllByRole('button', { name: /Xác nhận/ }).every((b) => (b as HTMLButtonElement).disabled)).toBe(true);
  });

  it.each(['QUOTE_REPRICED', 'QUOTE_STALE_VERSION'])('loads the new version after %s and confirms it with its own token', async (code) => {
    const { backend, user } = await withCard({
      [`POST ${CONFIRM}`]: () => problem(409, code),
      [`GET /api/demo/quotes/${REF}`]: () => json(200, { quoteRef: REF, version: 2, status: 'ISSUED', quoteCard: card(2, TOKEN_2) }),
    });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    expect(await screen.findByText('Phiên bản 1 không còn dùng được. Xem phiên bản 2 bên dưới trước khi xác nhận.')).toBeInTheDocument();
    expect(screen.getByText('2 × 1.290.000 ₫')).toBeInTheDocument();
    backend.set(`POST ${CONFIRM}`, () => json(200, { quoteRef: REF, version: 2, status: 'DRAFT_ORDER_CREATED', handedOff: false }));
    await user.click(screen.getByRole('button', { name: 'Xác nhận phiên bản 2' }));

    await screen.findAllByText('Đã gửi yêu cầu đặt hàng');
    expect(JSON.parse(posts(backend, CONFIRM)[1]?.body ?? '{}')).toEqual({ version: 2, confirmationToken: TOKEN_2 });
  });

  it('says staff will help when the confirmation is handed off, without the reason', async () => {
    const { backend, user } = await withCard({ [`POST ${CONFIRM}`]: () => json(200, { quoteRef: REF, version: 1, status: 'CONFIRMATION_BLOCKED', handedOff: true }) });
    backend.set(`GET ${STATE}`, () => json(200, { state: 'WAITING_STAFF' }));
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    expect(await screen.findByText(/Yêu cầu xác nhận của bạn đã được chuyển cho nhân viên tư vấn/)).toBeInTheDocument();
    expect(screen.getByText('Chờ nhân viên')).toBeInTheDocument();
    expect(await screen.findByText('Đang chờ nhân viên tư vấn')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/CONFIRMATION_BLOCKED|công nợ|tồn kho/);
  });

  it('keeps the card confirmable after a refusal that changed nothing (409 busy)', async () => {
    const { backend, user } = await withCard({ [`POST ${CONFIRM}`]: () => problem(409, 'CONFLICT') });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    expect(await screen.findByText(/Hệ thống đang bận. Vui lòng thử lại sau ít giây. Chưa có thay đổi nào được ghi./)).toBeInTheDocument();
    backend.set(`POST ${CONFIRM}`, () => json(200, { quoteRef: REF, version: 1, status: 'DRAFT_ORDER_CREATED', handedOff: false }));
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));
    await screen.findAllByText('Đã gửi yêu cầu đặt hàng');
  });

  it('reads the quote again when the card token is no longer current (404)', async () => {
    const { backend, user } = await withCard({
      [`POST ${CONFIRM}`]: () => problem(404, 'NOT_FOUND'),
      [`GET /api/demo/quotes/${REF}`]: () => json(200, { quoteRef: REF, version: 1, status: 'ISSUED', quoteCard: card(1, TOKEN_2) }),
    });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    await waitFor(() => expect(backend.calls.filter((c) => c.path === `/api/demo/quotes/${REF}`)).toHaveLength(1));
    backend.set(`POST ${CONFIRM}`, () => json(200, { quoteRef: REF, version: 1, status: 'DRAFT_ORDER_CREATED', handedOff: false }));
    await user.click(await screen.findByRole('button', { name: 'Xác nhận báo giá' }));
    await screen.findAllByText('Đã gửi yêu cầu đặt hàng');
    expect(JSON.parse(posts(backend, CONFIRM)[1]?.body ?? '{}')).toEqual({ version: 1, confirmationToken: TOKEN_2 });
  });

  it('shows the remembered quote again after a reload, with the fresh token', async () => {
    window.sessionStorage.setItem('apas.chat.quote', REF);
    const { backend, user } = setup({
      [`GET /api/demo/quotes/${REF}`]: () => json(200, { quoteRef: REF, version: 1, status: 'ISSUED', quoteCard: card(1, TOKEN_2) }),
      [`POST ${CONFIRM}`]: () => json(200, { quoteRef: REF, version: 1, status: 'DRAFT_ORDER_CREATED', handedOff: false }),
    }, { remembered: true });
    await user.click(await screen.findByRole('button', { name: 'Xác nhận báo giá' }));

    await screen.findAllByText('Đã gửi yêu cầu đặt hàng');
    expect(JSON.parse(posts(backend, CONFIRM)[0]?.body ?? '{}')).toEqual({ version: 1, confirmationToken: TOKEN_2 });
  });

  it('does not show a card it cannot read, and says so', async () => {
    const { user } = setup({ [`POST ${MESSAGES}`]: () => turn({ quoteCard: { ...card(), unitPrice: undefined, total: '1e9' } }) });
    await sendText(user, 'Báo giá má phanh');

    expect(await screen.findByText('Chưa hiển thị được báo giá')).toBeInTheDocument();
    expect(screen.queryByRole('region', { name: /Báo giá BG-/ })).not.toBeInTheDocument();
    expect(window.sessionStorage.getItem('apas.chat.quote')).toBeNull();
  });
});

// UX review of EP-10b (2026-11-25): M1, M2, L5.
describe('CustomerChatPage · accessibility fixes', () => {
  it('announces a new staff message once, not the whole list (M1)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { backend } = setup({
      [`GET ${STATE}`]: () => json(200, { state: 'STAFF' }),
      [`GET ${MESSAGES}`]: () => json(200, [message('CUSTOMER', 'Khoảng bao lâu?')]),
    }, { remembered: true });
    await screen.findByText('Khoảng bao lâu?');
    expect(screen.queryByText(/^Nhân viên tư vấn: /)).not.toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Tin nhắn' })).not.toHaveAttribute('aria-live');

    backend.set(`GET ${MESSAGES}`, () => json(200, [message('CUSTOMER', 'Khoảng bao lâu?'), message('STAFF', 'Khoảng 5 phút.')]));
    await act(async () => { await vi.advanceTimersByTimeAsync(STAFF_POLL_MS); });
    expect(await screen.findByText('Nhân viên tư vấn: Khoảng 5 phút.')).toBeInTheDocument();
  });

  it('returns focus to the message box after a send (M2)', async () => {
    const { user } = setup({ [`POST ${MESSAGES}`]: () => turn() });
    await sendText(user, 'Cần má phanh');

    await waitFor(() => expect(screen.getByLabelText('Nội dung tin nhắn')).toHaveFocus());
  });

  it('moves focus to the quote after a confirmation (M2)', async () => {
    const { user } = await withCard({ [`POST ${CONFIRM}`]: () => json(200, { quoteRef: REF, version: 1, status: 'DRAFT_ORDER_CREATED', handedOff: false }) });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    await screen.findAllByText('Đã gửi yêu cầu đặt hàng');
    expect(screen.getByRole('region', { name: `Báo giá ${REF}` })).toHaveFocus();
  });

  it('forgets a quote that is gone when it is read again (L5)', async () => {
    const { user } = await withCard({
      [`POST ${CONFIRM}`]: () => networkFailure(),
      [`GET /api/demo/quotes/${REF}`]: () => problem(404, 'NOT_FOUND'),
    });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));
    await user.click(await screen.findByRole('button', { name: 'Xem kết quả mới nhất' }));

    expect(await screen.findByText('Không tìm thấy báo giá')).toBeInTheDocument();
    expect(window.sessionStorage.getItem('apas.chat.quote')).toBeNull();
  });
});

// Security review of EP-10b (2026-11-25): M-1, M-2, L-1, L-2, L-3, L-5.
describe('CustomerChatPage · review fixes', () => {
  it('drops a quote that arrives after the tab memory was reset (sign-out race, M-1)', async () => {
    let answer: (r: Response) => void = () => {};
    const { user } = setup({ [`POST ${MESSAGES}`]: () => new Promise<Response>((resolve) => { answer = resolve; }) });
    await sendText(user, 'Báo giá má phanh');
    await screen.findByText('Đang tra cứu…');

    conversationMemory.reset();
    answer(turn({ quoteCard: card() }));
    await screen.findByRole('button', { name: 'Xác nhận báo giá' });
    expect(window.sessionStorage.length).toBe(0);
  });

  it('drops a new conversation that is created after the tab memory was reset (M-1)', async () => {
    let answer: (r: Response) => void = () => {};
    setup({ 'POST /api/demo/conversations': () => new Promise<Response>((resolve) => { answer = resolve; }) });
    await waitFor(() => expect(answer).not.toBe(undefined));
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 20)); });

    conversationMemory.reset();
    answer(json(201, { conversationId: CONV }));
    await screen.findByLabelText('Nội dung tin nhắn');
    expect(window.sessionStorage.length).toBe(0);
  });

  it("never shows the result of an earlier quote's confirmation on a newer quote (M-2)", async () => {
    let answer: (r: Response) => void = () => {};
    const { backend, user } = await withCard({ [`POST ${CONFIRM}`]: () => new Promise<Response>((resolve) => { answer = resolve; }) });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));
    await screen.findByRole('button', { name: /Đang gửi xác nhận/ });

    const other = { ...card(1, TOKEN_2), quoteRef: 'BG-HGFEDCBA' };
    backend.set(`POST ${MESSAGES}`, () => turn({ quoteCard: other }));
    await sendText(user, 'Báo giá thêm 1 bộ lọc dầu');
    await screen.findByRole('region', { name: 'Báo giá BG-HGFEDCBA' });

    answer(json(202, { quoteRef: REF, version: 1, status: 'ORDER_SUBMITTING', handedOff: false }));
    await act(async () => { await new Promise((resolve) => setTimeout(resolve, 50)); });
    expect(screen.getByRole('button', { name: 'Xác nhận báo giá' })).toBeEnabled();
    expect(screen.queryByText('Đang kiểm tra')).not.toBeInTheDocument();
    expect(window.sessionStorage.getItem('apas.chat.quote')).toBe('BG-HGFEDCBA');
  });

  it('treats a confirmation answer about another quote as an unknown outcome (M-2)', async () => {
    const { user } = await withCard({ [`POST ${CONFIRM}`]: () => json(200, { quoteRef: 'BG-HGFEDCBA', version: 1, status: 'DRAFT_ORDER_CREATED', handedOff: false }) });
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    expect(await screen.findByText('Chưa nhận được kết quả')).toBeInTheDocument();
    expect(screen.queryByText('Đã gửi yêu cầu đặt hàng')).not.toBeInTheDocument();
  });

  it('says "not sent" when the CSRF read fails before a confirmation (L-3)', async () => {
    const { backend, user } = await withCard();
    backend.set('GET /api/auth/csrf', () => networkFailure());
    await user.click(screen.getByRole('button', { name: 'Xác nhận báo giá' }));

    expect(await screen.findByText(/Chưa gửi được xác nhận. Chưa có thay đổi nào được ghi./)).toBeInTheDocument();
    expect(posts(backend, CONFIRM)).toHaveLength(0);
    expect(screen.getByRole('button', { name: 'Xác nhận báo giá' })).toBeEnabled();
  });

  it('resends the message whose answer is missing, not the edited text (L-5)', async () => {
    const { backend, user } = setup({ [`POST ${MESSAGES}`]: () => networkFailure() });
    await sendText(user, 'Còn hàng không?');
    await screen.findByText('Chưa nhận được phản hồi');
    await user.type(screen.getByLabelText('Nội dung tin nhắn'), ' Thêm nữa');

    backend.set(`POST ${MESSAGES}`, () => turn({ duplicate: true }));
    await user.click(screen.getByRole('button', { name: 'Gửi lại' }));
    await waitFor(() => expect(posts(backend, MESSAGES)).toHaveLength(2));
    const [first, second] = posts(backend, MESSAGES).map((c) => JSON.parse(c.body ?? '{}') as { clientEventId: string; text: string });
    expect(second).toEqual(first);
  });

  it('stops reading a conversation that is gone and opens a new one (L-1)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { backend } = setup({ [`GET ${STATE}`]: () => json(200, { state: 'STAFF' }) }, { remembered: true });
    await screen.findByText('Nhân viên tư vấn đã tham gia');

    backend.set(`GET ${MESSAGES}`, () => problem(404, 'NOT_FOUND'));
    backend.set('POST /api/demo/conversations', () => json(201, { conversationId: CONV_2 }));
    await act(async () => { await vi.advanceTimersByTimeAsync(STAFF_POLL_MS); });
    await waitFor(() => expect(window.sessionStorage.getItem('apas.chat.conversation')).toBe(CONV_2));
    const reads = backend.calls.filter((c) => c.path === MESSAGES).length;
    await act(async () => { await vi.advanceTimersByTimeAsync(STAFF_POLL_MS * 3); });
    expect(backend.calls.filter((c) => c.path === MESSAGES).length).toBe(reads);
  });

  it('never overlaps two reads of the conversation (L-2)', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { backend } = setup({ [`GET ${STATE}`]: () => json(200, { state: 'STAFF' }) }, { remembered: true });
    await screen.findByText('Nhân viên tư vấn đã tham gia');
    let answer: (r: Response) => void = () => {};
    backend.set(`GET ${MESSAGES}`, () => new Promise<Response>((resolve) => { answer = resolve; }));
    const before = backend.calls.filter((c) => c.path === MESSAGES).length;

    await act(async () => { await vi.advanceTimersByTimeAsync(STAFF_POLL_MS * 3); });
    expect(backend.calls.filter((c) => c.path === MESSAGES).length).toBe(before + 1);
    answer(json(200, []));
  });
});
