import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useEffect } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/client';
import { axeViolations, TRACE_ID } from '../test/fakeBackend';
import { PageState } from './PageState';
import { RenderErrorBoundary } from './ShellPages';
import { ToastProvider, useToast, type ToastId } from './toast';

// FE-UI-P2: PageState (F6-StaffStates), the toast policy and the render-error boundary. Synthetic values only.

const SECRET = 'SELECT price FROM quote WHERE customer = 42';
const serverError = () => new ApiError('http', 500, 'INTERNAL', TRACE_ID);

describe('PageState', () => {
  it('loading is a status with a spoken label', async () => {
    const view = render(<PageState kind="loading" loadingLabel="Đang tải hộp chờ…" />);

    expect(screen.getByRole('status')).toHaveTextContent('Đang tải hộp chờ…');
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('forbidden uses the F6 artboard copy and shows the reference only when the server refused', async () => {
    const view = render(<PageState kind="forbidden" error={new ApiError('http', 403, 'FORBIDDEN', TRACE_ID)} />);

    expect(screen.getByText('Bạn không có quyền xem trang này')).toBeInTheDocument();
    expect(screen.getByText('Mã tham chiếu: REQ-1A2B3C4D')).toBeInTheDocument();
    expect(view.container.textContent).not.toMatch(/403|FORBIDDEN/);
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('timeout says nothing was saved', () => {
    render(<PageState kind="timeout" />);

    expect(screen.getByText('Yêu cầu quá thời gian')).toBeInTheDocument();
    expect(screen.getByText('Chưa có thay đổi nào được ghi. Vui lòng thử lại.')).toBeInTheDocument();
  });

  it('error shows the fixed failure text and the reference, never the HTTP status, code or message', async () => {
    const error = serverError();
    const view = render(<PageState kind="error" error={error} />);

    expect(screen.getByText('Hệ thống đang gặp sự cố. Vui lòng thử lại sau.')).toBeInTheDocument();
    expect(screen.getByText('Mã tham chiếu: REQ-1A2B3C4D')).toBeInTheDocument();
    expect(view.container.textContent).not.toMatch(/500|INTERNAL|api http/);
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('empty and not-found show the screen words, not the design-system presets', async () => {
    const view = render(<>
      <PageState kind="empty" icon="users" title="Chưa có yêu cầu nào" description="Khi trợ lý AI chuyển một cuộc trò chuyện cho nhóm bạn, yêu cầu sẽ hiện ở đây." />
      <PageState kind="not-found" title="Không tìm thấy cuộc trò chuyện" description="Yêu cầu đã hoàn tất hoặc không còn do bạn xử lý. Quay lại hộp chờ để xem yêu cầu khác." />
    </>);

    expect(screen.getByText('Chưa có yêu cầu nào')).toBeInTheDocument();
    expect(screen.getByText('Không tìm thấy cuộc trò chuyện')).toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(/OE|SKU/);
    expect(await axeViolations(view.container)).toEqual([]);
  });
});

function Trigger({ run }: { run: (api: ReturnType<typeof useToast>) => void }) {
  const api = useToast();
  useEffect(() => { run(api); }, [api, run]);
  return null;
}

describe('toast', () => {
  afterEach(() => { vi.useRealTimers(); });

  it('shows only the fixed text of an entry (F6: 409 is a warning that stays until closed)', async () => {
    const user = userEvent.setup();
    const view = render(<ToastProvider><Trigger run={(t) => t.show('claimed-by-other')} /></ToastProvider>);

    const toast = await screen.findByRole('alert');
    expect(toast).toHaveTextContent('Yêu cầu đã có người nhận');
    expect(toast).toHaveTextContent('Một nhân viên khác vừa nhận yêu cầu này. Danh sách đã được tải lại.');
    expect(await axeViolations(view.container)).toEqual([]);
    await user.click(screen.getByRole('button', { name: 'Đóng thông báo' }));
    expect(screen.queryByRole('alert')).toBeNull();
  });

  it('a failure shows the fixed text and the reference, never the error message', async () => {
    const error = Object.assign(serverError(), { message: SECRET });
    const plain = new Error(SECRET);
    render(<ToastProvider><Trigger run={(t) => { t.failure(error); t.failure(plain); }} /></ToastProvider>);

    const toasts = await screen.findAllByRole('alert');
    expect(toasts).toHaveLength(2);
    expect(toasts[0]).toHaveTextContent('Hệ thống đang gặp sự cố. Vui lòng thử lại sau.');
    expect(toasts[0]).toHaveTextContent('Mã tham chiếu: REQ-1A2B3C4D');
    expect(toasts[1]).toHaveTextContent('Hệ thống đang gặp sự cố. Vui lòng thử lại sau.');
    expect(document.body.textContent).not.toContain(SECRET);
  });

  it('shows the same text once and keeps at most three toasts', async () => {
    render(<ToastProvider><Trigger run={(t) => {
      for (let i = 0; i < 5; i += 1) t.show('conversation-busy');
      t.failure(new ApiError('timeout'));
      t.failure(new ApiError('network'));
      t.show('claimed-by-other');
    }} /></ToastProvider>);

    const toasts = await screen.findAllByRole('alert');
    expect(toasts.map((t) => t.textContent)).toEqual([
      expect.stringContaining('Chưa nhận được phản hồi'), expect.stringContaining('Không kết nối được máy chủ'), expect.stringContaining('Yêu cầu đã có người nhận'),
    ]);
  });

  it('accepts no free text at compile time', () => {
    // @ts-expect-error a toast is an entry of TOAST_TEXT, not a string from the caller
    const id: ToastId = 'Tùy ý';
    expect(id).toBe('Tùy ý');
  });

  it('does not close by itself', () => {
    vi.useFakeTimers();
    render(<ToastProvider><Trigger run={(t) => t.show('conversation-busy')} /></ToastProvider>);
    act(() => { vi.advanceTimersByTime(60_000); });
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });
});

function Broken(): never {
  throw new Error(SECRET);
}

describe('RenderErrorBoundary', () => {
  it('replaces a failed screen with the E6 message, never the error text, and offers a reload', async () => {
    const quiet = vi.spyOn(console, 'error').mockImplementation(() => {});
    const view = render(<RenderErrorBoundary><Broken /></RenderErrorBoundary>);

    expect(screen.getByText('Không hiển thị được trang này')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tải lại trang' })).toBeInTheDocument();
    expect(view.container.textContent).not.toContain(SECRET);
    quiet.mockRestore();
    expect(await axeViolations(view.container)).toEqual([]);
  });
});
