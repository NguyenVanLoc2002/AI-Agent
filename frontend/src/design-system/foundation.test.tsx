import { act, fireEvent, render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { axeViolations } from '../test/fakeBackend';
import { Button, Card, DataTable, Dialog, PageHeader, Tabs, Tag, Toast, ToastViewport, Tooltip, type DataTableColumn } from '.';

// FE-UI-P1 (A-80): the design-system components copied for the app shell and staff pages. Synthetic values only.

const MARKUP = '<img src=x onerror="alert(1)">';

describe('Toast', () => {
  it('announces success politely and shows title and description as text', async () => {
    const view = render(<Toast tone="success" title="Đã lưu thay đổi" description={MARKUP} />);

    const toast = screen.getByRole('status');
    expect(toast).toHaveAttribute('aria-live', 'polite');
    expect(toast).toHaveTextContent('Đã lưu thay đổi');
    expect(toast).toHaveTextContent(MARKUP);
    expect(toast.querySelector('img')).toBeNull();
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it.each(['error', 'warning'] as const)('announces %s assertively as an alert', async (tone) => {
    const view = render(<Toast tone={tone} title="Yêu cầu quá thời gian" description="Chưa có thay đổi nào được ghi." />);

    expect(screen.getByRole('alert')).toHaveAttribute('aria-live', 'assertive');
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('closes with a labelled button and keeps an action reachable by keyboard', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    const onRetry = vi.fn();
    render(<Toast tone="error" title="Chưa gửi được" onClose={onClose}
      action={<Button size="sm" variant="secondary" onClick={onRetry}>Thử lại</Button>} />);

    await user.tab();
    expect(screen.getByRole('button', { name: 'Thử lại' })).toHaveFocus();
    await user.keyboard('{Enter}');
    await user.tab();
    expect(screen.getByRole('button', { name: 'Đóng thông báo' })).toHaveFocus();
    await user.keyboard('{Enter}');

    expect(onRetry).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('stacks toasts in one viewport', async () => {
    const view = render(<ToastViewport><Toast title="Một" /><Toast tone="info" title="Hai" /></ToastViewport>);

    expect(screen.getAllByRole('status')).toHaveLength(2);
    expect(await axeViolations(view.container)).toEqual([]);
  });
});

describe('PageHeader', () => {
  it('renders the page title as the level-one heading with meta and actions', async () => {
    const view = render(<PageHeader title="Hộp chờ nhân viên" description="Yêu cầu chuyển nhân viên của nhóm bạn."
      meta={<span>3 yêu cầu</span>} actions={<Button size="sm" variant="secondary">Tải lại</Button>} />);

    expect(screen.getByRole('heading', { level: 1, name: 'Hộp chờ nhân viên' })).toBeInTheDocument();
    expect(screen.getByText('3 yêu cầu')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Tải lại' })).toBeInTheDocument();
    expect(await axeViolations(view.container)).toEqual([]);
  });
});

const TABS = [
  { value: 'awaiting', label: 'Chờ duyệt', count: 2 },
  { value: 'unknown', label: 'Chưa rõ kết quả' },
  { value: 'off', label: 'Tạm khóa', disabled: true },
  { value: 'done', label: 'Đã xử lý' },
];

function ControlledTabs({ variant, onChange }: { variant?: 'line' | 'segmented' | 'pill'; onChange?: (v: string) => void }) {
  const [value, setValue] = useState('awaiting');
  return <Tabs aria-label="Trạng thái đơn" tabs={TABS} value={value} variant={variant}
    onChange={(v) => { setValue(v); onChange?.(v); }} />;
}

describe('Tabs', () => {
  it.each(['line', 'segmented', 'pill'] as const)('%s: one tab in the tab order, arrows move and skip disabled tabs', async (variant) => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const view = render(<ControlledTabs variant={variant} onChange={onChange} />);

    const list = screen.getByRole('tablist', { name: 'Trạng thái đơn' });
    const tabs = within(list).getAllByRole('tab');
    expect(tabs.map((t) => t.tabIndex)).toEqual([0, -1, -1, -1]);
    expect(tabs[0]).toHaveAttribute('aria-selected', 'true');

    await user.tab();
    expect(tabs[0]).toHaveFocus();
    await user.keyboard('{ArrowRight}');
    expect(tabs[1]).toHaveFocus();
    await user.keyboard('{ArrowRight}');
    expect(tabs[3]).toHaveFocus();
    await user.keyboard('{ArrowRight}');
    expect(tabs[0]).toHaveFocus();
    await user.keyboard('{ArrowLeft}');
    expect(tabs[3]).toHaveFocus();
    await user.keyboard('{Home}');
    expect(tabs[0]).toHaveFocus();
    await user.keyboard('{End}');
    expect(tabs[3]).toHaveFocus();
    expect(tabs[3]).toHaveAttribute('aria-selected', 'true');

    expect(onChange.mock.calls.map((c) => c[0])).toEqual(['unknown', 'done', 'awaiting', 'done', 'awaiting', 'done']);
    expect(tabs[2]).toBeDisabled();
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('selects a tab by click', async () => {
    const user = userEvent.setup();
    render(<ControlledTabs />);

    await user.click(screen.getByRole('tab', { name: 'Chưa rõ kết quả' }));
    expect(screen.getByRole('tab', { name: 'Chưa rõ kết quả' })).toHaveAttribute('aria-selected', 'true');
  });
});

describe('Tag', () => {
  it('a clickable tag is a toggle button for keyboard and pointer', async () => {
    const user = userEvent.setup();
    const onClick = vi.fn();
    const view = render(<Tag selected onClick={onClick}>Chờ nhận</Tag>);

    const tag = screen.getByRole('button', { name: 'Chờ nhận' });
    expect(tag).toHaveAttribute('aria-pressed', 'true');
    await user.tab();
    expect(tag).toHaveFocus();
    await user.keyboard('{Enter}');
    await user.keyboard(' ');
    await user.click(tag);
    expect(onClick).toHaveBeenCalledTimes(3);
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('a removable tag has a labelled remove button and is not itself a button', async () => {
    const user = userEvent.setup();
    const onRemove = vi.fn();
    const view = render(<Tag onRemove={onRemove}>Vai trò: SALES</Tag>);

    expect(screen.getAllByRole('button')).toHaveLength(1);
    await user.click(screen.getByRole('button', { name: 'Bỏ bộ lọc: Vai trò: SALES' }));
    expect(onRemove).toHaveBeenCalledTimes(1);
    expect(await axeViolations(view.container)).toEqual([]);
  });
});

describe('Card', () => {
  it('renders a titled section with actions, body and footer', async () => {
    const view = render(<Card title="Đơn chờ duyệt" subtitle="Cập nhật khi tải lại" actions={<Button size="sm" variant="ghost">Tải lại</Button>}
      footer={<span>2 đơn</span>}>Nội dung</Card>);

    expect(screen.getByRole('heading', { name: 'Đơn chờ duyệt' })).toBeInTheDocument();
    expect(screen.getByText('Nội dung')).toBeInTheDocument();
    expect(screen.getByText('2 đơn')).toBeInTheDocument();
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it.each(['default', 'elevated', 'nested'] as const)('renders the %s variant without a header when there is no title', (variant) => {
    render(<Card variant={variant}>Chỉ nội dung</Card>);

    expect(screen.queryByRole('heading')).toBeNull();
    expect(screen.getByText('Chỉ nội dung')).toBeInTheDocument();
  });
});

describe('Tooltip', () => {
  it('shows on keyboard focus, describes the focused control, and Esc hides it', async () => {
    const user = userEvent.setup();
    const view = render(<Tooltip content="Thu gọn thanh bên"><button type="button" aria-label="Thu gọn">≡</button></Tooltip>);

    const button = screen.getByRole('button', { name: 'Thu gọn' });
    await user.tab();
    expect(button).toHaveFocus();
    const tip = screen.getByRole('tooltip');
    expect(tip).toHaveTextContent('Thu gọn thanh bên');
    expect(button).toHaveAccessibleDescription('Thu gọn thanh bên');
    expect(await axeViolations(view.container)).toEqual([]);

    await user.keyboard('{Escape}');
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('inside an open Dialog, the first Esc closes only the tooltip and the next one the Dialog', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<Dialog open title="Tắt AI" onClose={onClose}>
      <Tooltip content="Khách sẽ được chuyển cho nhân viên"><button type="button">Chi tiết</button></Tooltip>
    </Dialog>);

    act(() => { screen.getByRole('button', { name: 'Chi tiết' }).focus(); });
    expect(screen.getByRole('tooltip')).toBeInTheDocument();
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('tooltip')).toBeNull();
    expect(onClose).not.toHaveBeenCalled();
    await user.keyboard('{Escape}');
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('stays while the pointer moves onto the tooltip itself, closes after leaving (WCAG 1.4.13 hoverable)', async () => {
    // Real timers: user-event's pointer simulation and fake timers hang together. The close delay is 120 ms.
    const pause = (ms: number) => act(() => new Promise<void>((resolve) => { setTimeout(resolve, ms); }));
    const user = userEvent.setup();
    render(<Tooltip content="Đang tắt"><span>AI</span></Tooltip>);
    const trigger = screen.getByText('AI');

    await user.hover(trigger);
    const tip = screen.getByRole('tooltip');
    await user.hover(tip);
    await pause(300);
    expect(screen.getByRole('tooltip')).toBeInTheDocument();

    await user.unhover(tip);
    await pause(300);
    expect(screen.queryByRole('tooltip')).toBeNull();
  });

  it('survives the gap between trigger and tooltip (close is delayed), and Esc hides it without focus', () => {
    vi.useFakeTimers();
    try {
      render(<Tooltip content="Đang tắt"><span>AI</span></Tooltip>);
      const trigger = screen.getByText('AI');

      fireEvent.mouseEnter(trigger);
      fireEvent.mouseLeave(trigger);
      act(() => { vi.advanceTimersByTime(50); });
      expect(screen.getByRole('tooltip')).toBeInTheDocument();
      fireEvent.mouseEnter(trigger);
      act(() => { vi.advanceTimersByTime(500); });
      expect(screen.getByRole('tooltip')).toBeInTheDocument();

      fireEvent.keyDown(document, { key: 'Escape' });
      expect(screen.queryByRole('tooltip')).toBeNull();
    } finally {
      vi.useRealTimers();
    }
  });
});

interface Row { id: string; code: string; since: string; lines: number }
const ROWS: Row[] = [
  { id: 'a', code: 'BG-0002', since: '2026-11-20T08:00:00Z', lines: 3 },
  { id: 'b', code: 'BG-0001', since: '2026-11-22T08:00:00Z', lines: 12 },
  { id: 'c', code: 'BG-0003', since: '2026-11-21T08:00:00Z', lines: 1 },
];
const COLUMNS: DataTableColumn[] = [
  { key: 'code', header: 'Mã báo giá', sortable: true },
  { key: 'since', header: 'Từ lúc', sortable: true },
  { key: 'lines', header: 'Số dòng', align: 'right' },
];
const codes = () => screen.getAllByRole('row').slice(1).map((r) => within(r).getAllByRole('cell')[0]?.textContent);

describe('DataTable', () => {
  it('renders column headers and rows, with a caption for screen readers', async () => {
    const view = render(<DataTable caption="Báo giá mẫu" columns={COLUMNS} rows={ROWS} />);

    expect(screen.getByRole('table', { name: 'Báo giá mẫu' })).toBeInTheDocument();
    expect(screen.getAllByRole('columnheader').map((h) => h.textContent?.trim())).toEqual(['Mã báo giá', 'Từ lúc', 'Số dòng']);
    expect(screen.getAllByRole('columnheader').every((h) => h.getAttribute('scope') === 'col')).toBe(true);
    expect(codes()).toEqual(['BG-0002', 'BG-0001', 'BG-0003']);
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('sorts only the rows it was given, by keyboard, and says so with aria-sort', async () => {
    const user = userEvent.setup();
    render(<DataTable caption="Báo giá mẫu" columns={COLUMNS} rows={ROWS} />);

    await user.tab();
    expect(screen.getByRole('button', { name: /Mã báo giá/ })).toHaveFocus();
    await user.keyboard('{Enter}');
    expect(codes()).toEqual(['BG-0001', 'BG-0002', 'BG-0003']);
    expect(screen.getAllByRole('columnheader')[0]).toHaveAttribute('aria-sort', 'ascending');
    await user.keyboard('{Enter}');
    expect(codes()).toEqual(['BG-0003', 'BG-0002', 'BG-0001']);
    expect(screen.getAllByRole('columnheader')[0]).toHaveAttribute('aria-sort', 'descending');
  });

  it('leaves the order to the caller when sort is controlled (no client-side or server contract of its own)', async () => {
    const user = userEvent.setup();
    const onSortChange = vi.fn();
    render(<DataTable caption="Báo giá mẫu" columns={COLUMNS} rows={ROWS} sort={null} onSortChange={onSortChange} />);

    await user.click(screen.getByRole('button', { name: /Từ lúc/ }));
    expect(onSortChange).toHaveBeenCalledWith({ key: 'since', dir: 'asc' });
    expect(codes()).toEqual(['BG-0002', 'BG-0001', 'BG-0003']);
  });

  it('shows the given empty state, and a busy table while loading', async () => {
    const empty = render(<DataTable caption="Báo giá mẫu" columns={COLUMNS} rows={[]} emptyState={<p>Chưa có đơn chờ duyệt</p>} />);
    expect(screen.getByText('Chưa có đơn chờ duyệt')).toBeInTheDocument();
    expect(await axeViolations(empty.container)).toEqual([]);
    empty.unmount();

    const loading = render(<DataTable caption="Báo giá mẫu" columns={COLUMNS} rows={ROWS} loading />);
    expect(screen.getByRole('table')).toHaveAttribute('aria-busy', 'true');
    expect(screen.queryByText('BG-0001')).toBeNull();
    expect(await axeViolations(loading.container)).toEqual([]);
  });

  it('opens a row with Enter or Space when rows are clickable', async () => {
    const user = userEvent.setup();
    const onRowClick = vi.fn();
    const view = render(<DataTable caption="Báo giá mẫu" columns={COLUMNS.map((c) => ({ ...c, sortable: false }))} rows={ROWS} onRowClick={onRowClick} />);

    await user.tab();
    await user.keyboard('{Enter}');
    await user.tab();
    await user.keyboard(' ');
    expect(onRowClick.mock.calls.map((c) => (c[0] as Row).code)).toEqual(['BG-0002', 'BG-0001']);
    expect(await axeViolations(view.container)).toEqual([]);
  });

  it('Space on a row checkbox toggles it without opening the row (review H2)', async () => {
    const user = userEvent.setup();
    const onRowClick = vi.fn();
    render(<DataTable caption="Báo giá mẫu" columns={COLUMNS} rows={ROWS} selectable onRowClick={onRowClick} />);

    const box = screen.getAllByRole('checkbox', { name: 'Chọn dòng' })[0] as HTMLInputElement;
    box.focus();
    await user.keyboard(' ');
    expect(box.checked).toBe(true);
    await user.keyboard('{Enter}');
    expect(onRowClick).not.toHaveBeenCalled();
  });

  it('selects rows and all loaded rows with labelled checkboxes', async () => {
    const user = userEvent.setup();
    const view = render(<DataTable caption="Báo giá mẫu" columns={COLUMNS} rows={ROWS} selectable />);

    await user.click(screen.getAllByRole('checkbox', { name: 'Chọn dòng' })[1]!);
    expect(screen.getByRole('checkbox', { name: 'Chọn tất cả' })).toHaveAttribute('aria-checked', 'mixed');
    await user.click(screen.getByRole('checkbox', { name: 'Chọn tất cả' }));
    expect(screen.getAllByRole('checkbox', { name: 'Chọn dòng' }).every((c) => (c as HTMLInputElement).checked)).toBe(true);
    expect(await axeViolations(view.container)).toEqual([]);
  });
});
