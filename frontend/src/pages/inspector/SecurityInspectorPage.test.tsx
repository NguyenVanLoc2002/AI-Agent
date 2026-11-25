import { StrictMode } from 'react';
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { axeViolations, fakeBackend, hang, json, networkFailure, problem, type Handler, type RecordedCall } from '../../test/fakeBackend';
import { SecurityInspectorPage } from './SecurityInspectorPage';

// F4 · SI-01 Security Inspector (UC-07, SECREV; A-70). Synthetic codes, pseudonyms and trace IDs only.
const EVENTS = '/api/demo/security/events';
const T1 = '7c1e2f00-0000-4000-8000-00000000a904';
const T2 = '19ab0000-0000-4000-8000-0000000022f0';
const T3 = 'c3d20000-0000-4000-8000-000000000b18';
const STAFF = 'usr_AAAAAAAAAAAAAAAAAAAAAA';
const OPERATOR = 'usr_BBBBBBBBBBBBBBBBBBBBBB';
const CURSOR = '1795580419.000000000A42';

const event = (extra: Record<string, unknown> = {}) => ({
  action: 'HANDOFF_CLAIMED', resource: 'HANDOFF', outcome: 'CLAIMED', actorRole: 'SALES', actor: STAFF, traceId: T2,
  occurredAt: '2026-11-23T08:05:00Z', occurrences: 1, lastOccurredAt: null, lastTraceId: null, ...extra,
});
const PAGE_1 = {
  events: [
    event({ action: 'ACCESS_DENIED', resource: 'QUOTE', outcome: 'FORBIDDEN_RESOURCE', actorRole: 'CUST', actor: null, traceId: T1, occurredAt: '2026-11-23T08:58:00Z', occurrences: 3, lastOccurredAt: '2026-11-23T08:58:40Z', lastTraceId: T3 }),
    event({ action: 'KILL_SWITCH_CHANGED', resource: 'KILL_SWITCH', outcome: 'OFF_PROVIDER_AUTH', actorRole: null, actor: null, traceId: null, occurredAt: '2026-11-23T08:40:00Z' }),
    event({ action: 'KILL_SWITCH_CHANGED', resource: 'KILL_SWITCH', outcome: 'ON', actorRole: 'OPS', actor: OPERATOR, traceId: null, occurredAt: '2026-11-23T08:41:00Z' }),
    event(),
  ],
  next: CURSOR,
};
const PAGE_2 = { events: [event({ action: 'STAFF_LIST_READ', outcome: 'INBOX', occurredAt: '2026-11-23T07:00:00Z' })], next: null };
const TRACE = {
  traceId: T1,
  egress: [
    { decision: 'PASS', attempt: 1, policyCode: null, honeytokenId: null, blockedFieldPath: null, fieldNames: ['messages', 'tools'], byteLength: 1284, occurredAt: '2026-11-23T08:57:59Z', direction: 'REQUEST' },
    { decision: 'BLOCK', attempt: 1, policyCode: 'HONEYTOKEN', honeytokenId: 'HT-03', blockedFieldPath: '$.messages[2].content', fieldNames: ['messages'], byteLength: 512, occurredAt: '2026-11-23T08:58:00Z', direction: 'RESPONSE' },
    { decision: 'BLOCK', attempt: 0, policyCode: 'DLP_PHONE', honeytokenId: null, blockedFieldPath: '$.messages[0].content', fieldNames: ['messages'], byteLength: 90, occurredAt: '2026-11-23T08:58:01Z', direction: 'REQUEST' },
  ],
  tools: [{ toolName: 'searchParts', decision: 'REJECTED', reasonCode: 'ARGUMENT_INVALID', occurredAt: '2026-11-23T08:58:00Z' }],
  events: [],
  truncated: false,
};

function setup(routes: Record<string, Handler> = {}) {
  const backend = fakeBackend({
    [`GET ${EVENTS}?*`]: () => json(200, PAGE_1),
    [`GET /api/demo/security/traces/${T1}`]: () => json(200, TRACE),
    ...routes,
  });
  const onUnauthenticated = vi.fn();
  const view = render(<SecurityInspectorPage onUnauthenticated={onUnauthenticated} />);
  return { backend, view, onUnauthenticated, user: userEvent.setup() };
}

const eventReads = (backend: ReturnType<typeof fakeBackend>) => backend.calls.filter((c) => c.path.startsWith(`${EVENTS}?`));
const params = (call: RecordedCall | undefined) => new URL(`http://x${call?.path ?? ''}`).searchParams;
const table = () => screen.findByRole('table', { name: 'Sự kiện kiểm toán, mới nhất ở trên' });

describe('F4 · security events', () => {
  it('reads the last 24 hours on entry and shows codes, roles and staff pseudonyms only', async () => {
    const { backend, view } = setup();
    const rows = within(await table()).getAllByRole('row').slice(1);

    const query = params(eventReads(backend)[0]);
    expect(eventReads(backend)).toHaveLength(1);
    expect(query.get('limit')).toBe('50');
    expect(query.has('type')).toBe(false);
    expect(query.has('role')).toBe(false);
    expect(query.has('before')).toBe(false);
    expect(Date.parse(query.get('to')!) - Date.parse(query.get('from')!)).toBe(24 * 60 * 60 * 1000);

    expect(within(rows[0] as HTMLElement).getByText('FORBIDDEN_RESOURCE')).toBeInTheDocument();
    expect(within(rows[0] as HTMLElement).getByText(/Khách hàng ·/)).toBeInTheDocument();
    expect(within(rows[0] as HTMLElement).getByText('3')).toBeInTheDocument();
    expect(within(rows[0] as HTMLElement).getByRole('button', { name: 'Xem trace 7c1e…a904' })).toHaveAttribute('title', T1);
    expect(within(rows[0] as HTMLElement).getByRole('button', { name: 'Xem trace lần cuối c3d2…0b18' })).toBeInTheDocument();
    expect(within(rows[1] as HTMLElement).getByText(/Hệ thống ·/)).toBeInTheDocument();
    expect(within(rows[1] as HTMLElement).queryByRole('button')).not.toBeInTheDocument();
    expect(within(rows[2] as HTMLElement).getByText(OPERATOR)).toBeInTheDocument();
    expect(within(rows[3] as HTMLElement).getByText(STAFF)).toBeInTheDocument();
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('does not read again when a filter changes, only on "Áp dụng"', async () => {
    const { backend, user } = setup();
    await table();

    await user.selectOptions(screen.getByLabelText('Loại sự kiện'), 'ACCESS_DENIED');
    await user.selectOptions(screen.getByLabelText('Vai trò'), 'SALES');
    await user.selectOptions(screen.getByLabelText('Khoảng thời gian'), '7d');
    expect(eventReads(backend)).toHaveLength(1);

    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    await waitFor(() => expect(eventReads(backend)).toHaveLength(2));
    const query = params(eventReads(backend)[1]);
    expect(query.get('type')).toBe('ACCESS_DENIED');
    expect(query.get('role')).toBe('SALES');
    expect(Date.parse(query.get('to')!) - Date.parse(query.get('from')!)).toBe(7 * 24 * 60 * 60 * 1000);
    expect(await screen.findByText(/Từ chối truy cập · Nhân viên kinh doanh/)).toBeInTheDocument();
  });

  it('checks a custom window at the field and sends nothing while it is invalid', async () => {
    const { backend, user } = setup();
    await table();
    await user.selectOptions(screen.getByLabelText('Khoảng thời gian'), 'custom');
    const from = screen.getByLabelText(/^Từ/);
    const to = screen.getByLabelText(/^Đến/);

    await user.clear(from);
    await user.type(from, '2020-01-20T08:00');
    await user.clear(to);
    await user.type(to, '2020-01-20T07:00');
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    expect(await screen.findByText('Thời điểm kết thúc phải sau thời điểm bắt đầu.')).toBeInTheDocument();
    await waitFor(() => expect(to).toHaveFocus());

    await user.clear(to);
    await user.type(to, '2020-01-20T08:00');
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    expect(await screen.findByText('Thời điểm kết thúc phải sau thời điểm bắt đầu.')).toBeInTheDocument();

    await user.clear(to);
    await user.type(to, '2020-01-27T08:01');
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    expect(await screen.findByText('Mỗi lần xem tối đa 7 ngày. Hãy rút ngắn khoảng thời gian.')).toBeInTheDocument();

    await user.clear(from);
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    expect(await screen.findByText('Chọn ngày và giờ bắt đầu.')).toBeInTheDocument();
    await waitFor(() => expect(from).toHaveFocus());
    expect(eventReads(backend)).toHaveLength(1);

    await user.type(from, '2020-01-20T08:00');
    await user.clear(to);
    await user.type(to, '2020-01-27T08:00');
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    await waitFor(() => expect(eventReads(backend)).toHaveLength(2));
    const query = params(eventReads(backend)[1]);
    expect(Date.parse(query.get('from')!)).toBe(new Date('2020-01-20T08:00').getTime());
    expect(Date.parse(query.get('to')!)).toBe(new Date('2020-01-27T08:00').getTime());
  });

  it('says the filter was refused when the backend answers 400', async () => {
    setup({ [`GET ${EVENTS}?*`]: () => problem(400, 'VALIDATION_FAILED') });

    expect(await screen.findByText('Bộ lọc không hợp lệ.')).toBeInTheDocument();
    expect(screen.getByText('Mã tham chiếu: REQ-1A2B3C4D')).toBeInTheDocument();
  });

  it('loads the next page with the cursor and the same window, and stops at the end', async () => {
    const { backend, user } = setup();
    await table();
    backend.set(`GET ${EVENTS}?*`, () => json(200, PAGE_2));

    await user.click(screen.getByRole('button', { name: 'Tải thêm' }));
    expect(await screen.findByText('INBOX')).toBeInTheDocument();
    const [first, second] = eventReads(backend);
    expect(params(second).get('before')).toBe(CURSOR);
    expect(params(second).get('from')).toBe(params(first).get('from'));
    expect(params(second).get('to')).toBe(params(first).get('to'));
    expect(within(await table()).getAllByRole('row')).toHaveLength(6);
    await waitFor(async () => expect(within(await table()).getAllByRole('row')[5]).toHaveFocus());
    expect(screen.queryByRole('button', { name: 'Tải thêm' })).not.toBeInTheDocument();
    expect(screen.getByText(/đã hết sự kiện trong khoảng này/)).toBeInTheDocument();
  });

  it('drops a next page that arrives after a new filter was applied', async () => {
    let answer: (r: Response) => void = () => {};
    const { backend, user } = setup();
    await table();
    backend.set(`GET ${EVENTS}?*`, () => new Promise<Response>((resolve) => { answer = resolve; }));
    await user.click(screen.getByRole('button', { name: 'Tải thêm' }));
    const late = answer;

    backend.set(`GET ${EVENTS}?*`, () => json(200, { events: [event({ outcome: 'RESOLVED' })], next: null }));
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    expect(await screen.findByText('RESOLVED')).toBeInTheDocument();
    late(json(200, PAGE_2));

    await new Promise((r) => setTimeout(r, 20));
    expect(screen.queryByText('INBOX')).not.toBeInTheDocument();
  });

  it('reads once on entry under React StrictMode (security review L-2)', async () => {
    const backend = fakeBackend({ [`GET ${EVENTS}?*`]: () => json(200, PAGE_1) });
    render(<StrictMode><SecurityInspectorPage onUnauthenticated={vi.fn()} /></StrictMode>);

    expect(await table()).toBeInTheDocument();
    expect(eventReads(backend)).toHaveLength(1);
  });

  it('marks a changed filter as not applied and moves the focus to the result after "Áp dụng" (UX review M3, H1)', async () => {
    const { user } = setup();
    await table();
    expect(screen.queryByText('Bộ lọc đã đổi, chưa áp dụng.')).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Vai trò'), 'OPS');
    expect(screen.getByText('Bộ lọc đã đổi, chưa áp dụng.')).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText('Vai trò'), '');
    expect(screen.queryByText('Bộ lọc đã đổi, chưa áp dụng.')).not.toBeInTheDocument();

    await user.selectOptions(screen.getByLabelText('Vai trò'), 'OPS');
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));
    const summary = await screen.findByText(/^Đang hiện: .*Vận hành hệ thống/);
    await waitFor(() => expect(summary).toHaveFocus());
    expect(screen.queryByText('Bộ lọc đã đổi, chưa áp dụng.')).not.toBeInTheDocument();
    expect(screen.getByText('Đã tải 4 sự kiện.')).toBeInTheDocument();
  });

  it('refuses a custom end in the future at the field', async () => {
    const { backend, user } = setup();
    await table();
    await user.selectOptions(screen.getByLabelText('Khoảng thời gian'), 'custom');
    const to = screen.getByLabelText(/^Đến/);
    await user.clear(to);
    await user.type(to, '2099-01-01T00:00');
    await user.click(screen.getByRole('button', { name: 'Áp dụng' }));

    expect(await screen.findByText('Thời điểm kết thúc không được ở tương lai.')).toBeInTheDocument();
    expect(eventReads(backend)).toHaveLength(1);
  });

  it('returns to the login on a 401 for the next page', async () => {
    const { backend, user, onUnauthenticated } = setup();
    await table();
    backend.set(`GET ${EVENTS}?*`, () => problem(401, 'UNAUTHENTICATED'));
    await user.click(screen.getByRole('button', { name: 'Tải thêm' }));

    await waitFor(() => expect(onUnauthenticated).toHaveBeenCalledTimes(1));
  });

  it('refuses a whole page that holds one customer pseudonym, not only that row', async () => {
    setup({ [`GET ${EVENTS}?*`]: () => json(200, { events: [event(), event({ actorRole: 'CUST', actor: STAFF })], next: null }) });

    expect(await screen.findByText('Chưa tải được sự kiện bảo mật')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('shows an empty window', async () => {
    setup({ [`GET ${EVENTS}?*`]: () => json(200, { events: [], next: null }) });

    expect(await screen.findByText('Không có sự kiện trong khoảng này')).toBeInTheDocument();
  });

  it('fails closed on a customer pseudonym and shows nothing of the page', async () => {
    const { view } = setup({ [`GET ${EVENTS}?*`]: () => json(200, { events: [event({ actorRole: 'CUST', actor: STAFF })], next: null }) });

    expect(await screen.findByText('Chưa tải được sự kiện bảo mật')).toBeInTheDocument();
    expect(view.container.textContent).not.toContain(STAFF);
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('retries after a network failure', async () => {
    const { backend, user } = setup({ [`GET ${EVENTS}?*`]: () => networkFailure() });

    expect(await screen.findByText('Chưa tải được sự kiện bảo mật')).toBeInTheDocument();
    backend.set(`GET ${EVENTS}?*`, () => json(200, PAGE_1));
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await table()).toBeInTheDocument();
  });

  it('shows a lock, not data, for a role other than SECREV (403)', async () => {
    const { view } = setup({ [`GET ${EVENTS}?*`]: () => problem(403, 'FORBIDDEN') });

    expect(await screen.findByText('Bạn không có quyền xem sự kiện bảo mật.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('returns to the login on 401', async () => {
    const { onUnauthenticated } = setup({ [`GET ${EVENTS}?*`]: () => problem(401, 'UNAUTHENTICATED') });

    await waitFor(() => expect(onUnauthenticated).toHaveBeenCalledTimes(1));
  });
});

describe('F4 · trace', () => {
  it('reads a trace only when asked and shows its egress checks and tool calls', async () => {
    const { backend, user, view } = setup();
    await table();
    expect(backend.calls.some((c) => c.path.includes('/traces/'))).toBe(false);

    await user.click(screen.getByRole('button', { name: 'Xem trace 7c1e…a904' }));
    const heading = await screen.findByRole('heading', { name: 'Trace 7c1e…a904' });
    await waitFor(() => expect(heading).toHaveFocus());
    const egress = screen.getByRole('table', { name: 'Kiểm tra dữ liệu gửi ra của trace' });
    const blocked = within(egress).getAllByRole('row')[2] as HTMLElement;
    expect(within(blocked).getByText('BLOCK')).toBeInTheDocument();
    expect(within(blocked).getByText('HT-03')).toBeInTheDocument();
    expect(within(blocked).getByText('$.messages[2].content')).toBeInTheDocument();
    expect(within(blocked).getByText('Câu trả lời của AI')).toBeInTheDocument();
    expect(within(blocked).getAllByRole('cell')[3]).toHaveTextContent('1');
    const blockedRequest = within(egress).getAllByRole('row')[3] as HTMLElement;
    expect(within(blockedRequest).getAllByRole('cell')[3]).toHaveTextContent(/^—không có$/);
    expect(within(screen.getByRole('table', { name: 'Lần gọi công cụ của trace' })).getByText('ARGUMENT_INVALID')).toBeInTheDocument();
    expect(screen.getByText(T1)).toBeInTheDocument();
    expect(screen.getByText('Có dữ liệu bị chặn khi gửi tới AI hoặc khi AI trả về')).toBeInTheDocument();
    expect(screen.queryByText('Trace chưa đầy đủ.')).not.toBeInTheDocument();
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('flags a truncated trace', async () => {
    const { user } = setup({ [`GET /api/demo/security/traces/${T1}`]: () => json(200, { ...TRACE, truncated: true }) });
    await user.click(await screen.findByRole('button', { name: 'Xem trace 7c1e…a904' }));

    expect(await screen.findByText('Trace chưa đầy đủ.')).toBeInTheDocument();
  });

  it('draws no "no block" verdict from a cut trace (UX review M2)', async () => {
    const pass = TRACE.egress[0];
    const { user } = setup({ [`GET /api/demo/security/traces/${T1}`]: () => json(200, { ...TRACE, egress: [pass], truncated: true }) });
    await user.click(await screen.findByRole('button', { name: 'Xem trace 7c1e…a904' }));

    expect(await screen.findByText('Chưa kết luận được: trace bị cắt bớt')).toBeInTheDocument();
    expect(screen.queryByText('Không có lần chặn dữ liệu nào trong trace')).not.toBeInTheDocument();
  });

  it('reads a trace once however often its link is clicked (security review L-1)', async () => {
    let answer: (r: Response) => void = () => {};
    const { backend, user } = setup({ [`GET /api/demo/security/traces/${T1}`]: () => new Promise<Response>((resolve) => { answer = resolve; }) });
    const link = await screen.findByRole('button', { name: 'Xem trace 7c1e…a904' });
    await user.click(link);
    await user.click(link);
    await user.click(link);
    answer(json(200, TRACE));
    await screen.findByRole('heading', { name: 'Trace 7c1e…a904' });
    await user.type(screen.getByLabelText(/^Mã trace/), T1);
    await user.click(screen.getByRole('button', { name: 'Xem trace' }));

    expect(backend.calls.filter((c) => c.path === `/api/demo/security/traces/${T1}`)).toHaveLength(1);
    await waitFor(() => expect(screen.getByRole('heading', { name: 'Trace 7c1e…a904' })).toHaveFocus());
  });

  it('reads a failed trace again only on "Thử lại"', async () => {
    const { backend, user } = setup({ [`GET /api/demo/security/traces/${T1}`]: () => problem(500, 'INTERNAL') });
    await user.click(await screen.findByRole('button', { name: 'Xem trace 7c1e…a904' }));
    expect(await screen.findByText('Chưa tải được trace')).toBeInTheDocument();

    backend.set(`GET /api/demo/security/traces/${T1}`, () => json(200, TRACE));
    await user.click(screen.getByRole('button', { name: 'Thử lại' }));
    expect(await screen.findByRole('heading', { name: 'Trace 7c1e…a904' })).toBeInTheDocument();
    expect(backend.calls.filter((c) => c.path === `/api/demo/security/traces/${T1}`)).toHaveLength(2);
  });

  it('turns the whole page into the lock on a 403 trace read', async () => {
    const { user } = setup({ [`GET /api/demo/security/traces/${T1}`]: () => problem(403, 'FORBIDDEN') });
    await user.click(await screen.findByRole('button', { name: 'Xem trace 7c1e…a904' }));

    expect(await screen.findByText('Bạn không có quyền xem sự kiện bảo mật.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Sự kiện bảo mật', level: 1 })).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it('returns to the login on a 401 trace read', async () => {
    const { user, onUnauthenticated } = setup({ [`GET /api/demo/security/traces/${T1}`]: () => problem(401, 'UNAUTHENTICATED') });
    await user.click(await screen.findByRole('button', { name: 'Xem trace 7c1e…a904' }));

    await waitFor(() => expect(onUnauthenticated).toHaveBeenCalledTimes(1));
  });

  it('says a trace without records was not found (404)', async () => {
    const { user } = setup();
    await table();
    await user.type(screen.getByLabelText(/^Mã trace/), T2.toUpperCase());
    await user.click(screen.getByRole('button', { name: 'Xem trace' }));

    expect(await screen.findByText('Không tìm thấy trace')).toBeInTheDocument();
  });

  it('checks a pasted trace ID at the field and sends nothing while it is malformed', async () => {
    const { backend, user } = setup();
    await table();
    const field = screen.getByLabelText(/^Mã trace/);
    await user.type(field, '7c1e…a904');
    await user.click(screen.getByRole('button', { name: 'Xem trace' }));

    expect(await screen.findByText(/Mã trace gồm 36 ký tự/)).toBeInTheDocument();
    await waitFor(() => expect(field).toHaveFocus());
    expect(backend.calls.some((c) => c.path.includes('/traces/'))).toBe(false);
  });

  it('refuses an answer for another trace', async () => {
    const { user } = setup({ [`GET /api/demo/security/traces/${T1}`]: () => json(200, { ...TRACE, traceId: T2 }) });
    await user.click(await screen.findByRole('button', { name: 'Xem trace 7c1e…a904' }));

    expect(await screen.findByText('Chưa tải được trace')).toBeInTheDocument();
    expect(screen.queryByRole('table', { name: 'Kiểm tra dữ liệu gửi ra của trace' })).not.toBeInTheDocument();
  });

  it('shows the newest trace asked for when an older answer arrives late', async () => {
    let answer: (r: Response) => void = () => {};
    const { user } = setup({
      [`GET /api/demo/security/traces/${T1}`]: () => new Promise<Response>((resolve) => { answer = resolve; }),
      [`GET /api/demo/security/traces/${T3}`]: () => json(200, { ...TRACE, traceId: T3, egress: [], tools: [] }),
    });
    await user.click(await screen.findByRole('button', { name: 'Xem trace 7c1e…a904' }));
    await user.click(screen.getByRole('button', { name: 'Xem trace lần cuối c3d2…0b18' }));
    expect(await screen.findByRole('heading', { name: 'Trace c3d2…0b18' })).toBeInTheDocument();

    answer(json(200, TRACE));
    await new Promise((r) => setTimeout(r, 20));
    expect(screen.getByRole('heading', { name: 'Trace c3d2…0b18' })).toBeInTheDocument();
  });

  it('times out a trace read with a retry', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      const { user } = setup({ [`GET /api/demo/security/traces/${T1}`]: hang });
      await user.click(await screen.findByRole('button', { name: 'Xem trace 7c1e…a904' }));
      await vi.advanceTimersByTimeAsync(15_001);

      expect(await screen.findByText('Chưa tải được trace')).toBeInTheDocument();
      expect(screen.getByText(/Chưa nhận được phản hồi/)).toBeInTheDocument();
      expect(screen.getByRole('button', { name: 'Thử lại' })).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});
