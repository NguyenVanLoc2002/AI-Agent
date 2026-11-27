import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { claimHandoff, listHandoffs, type Handoff } from '../../api/staff';
import { PageState } from '../../app/PageState';
import { reasonLabel, shortConversation } from '../../app/staffLabels';
import { formatDateTime, referenceText } from '../../app/texts';
import { useToast } from '../../app/toast';
import { Alert, Badge, Button, DataTable, PageHeader } from '../../design-system';

// F2 · HO-01 staff inbox (UC-06; specs/50 §F2). The backend lists the team's open work only and records
// every read (STAFF_LIST_READ), so the list is read on entry, after a claim or a resolve and on "Tải lại"
// only (A-69, 1A). No customer identity or message content is ever part of a row.
// FE-UI-P3: layout and copy of the approved artboard F2-Inbox; page states and the 409 toasts of F6-StaffStates.

export interface InboxNotice {
  tone: 'success' | 'info' | 'warning' | 'error';
  title: string;
  body?: string | null;
}

type Load =
  | { phase: 'loading' }
  | { phase: 'ready'; items: Handoff[]; refreshing: boolean }
  | { phase: 'failed'; error: unknown }
  | { phase: 'forbidden' };

interface Props {
  /** Shown again after a conversation: focus starts at the page heading. */
  focusHeading?: boolean;
  notice: InboxNotice | null;
  onNotice: (notice: InboxNotice | null) => void;
  onOpen: (handoff: Handoff) => void;
  onUnauthenticated: () => void;
}

export function StaffInboxPage({ focusHeading = false, notice, onNotice, onOpen, onUnauthenticated }: Props) {
  const [load, setLoad] = useState<Load>({ phase: 'loading' });
  const [claiming, setClaiming] = useState<number | null>(null);
  const toast = useToast();
  const mounted = useRef(true);
  const header = useRef<HTMLDivElement>(null);
  /** The page heading (PageHeader's h1), made focusable for the focus moves below. */
  const heading = () => {
    const h1 = header.current?.querySelector('h1') ?? null;
    h1?.setAttribute('tabindex', '-1');
    return h1;
  };
  const table = useRef<HTMLDivElement>(null);
  /** After a claim, focus moves to that row's "Mở" button once the list is read again (UX review M3). */
  const focusAfterReload = useRef<number | null>(null);
  useEffect(() => {
    mounted.current = true;
    // Coming back from a conversation: focus starts at the page heading.
    if (focusHeading || notice) heading()?.focus();
    return () => { mounted.current = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  /** Reads the list; answers the rows read, or null when the read failed. */
  const reload = useCallback(async (): Promise<Handoff[] | null> => {
    // Rows already on screen stay while the list is read again; the skeleton is for the first read only.
    setLoad((current) => (current.phase === 'ready' ? { ...current, refreshing: true } : { phase: 'loading' }));
    try {
      const items = await listHandoffs();
      if (mounted.current) setLoad({ phase: 'ready', items, refreshing: false });
      return items;
    } catch (error) {
      if (!mounted.current) return null;
      if (error instanceof ApiError && error.unauthenticated) { onUnauthenticated(); return null; }
      if (error instanceof ApiError && error.forbidden) { setLoad({ phase: 'forbidden' }); return null; }
      setLoad({ phase: 'failed', error });
      return null;
    }
  }, [onUnauthenticated]);

  useEffect(() => {
    if (load.phase !== 'ready' || load.refreshing || focusAfterReload.current === null) return;
    const id = focusAfterReload.current;
    focusAfterReload.current = null;
    const target = table.current?.querySelector<HTMLButtonElement>(`button[data-handoff="${id}"]`) ?? heading();
    target?.focus();
  }, [load]);

  useEffect(() => { void reload(); }, [reload]);

  async function claim(item: Handoff) {
    if (claiming !== null) return;
    setClaiming(item.handoffId);
    onNotice(null);
    let conflict = false;
    try {
      await claimHandoff(item.handoffId);
      if (!mounted.current) return;
      focusAfterReload.current = item.handoffId;
      onNotice({ tone: 'success', title: 'Đã nhận xử lý.', body: 'Bấm "Mở" để xem cuộc trò chuyện.' });
    } catch (error) {
      if (!mounted.current) return;
      if (error instanceof ApiError && error.unauthenticated) { onUnauthenticated(); return; }
      if (error instanceof ApiError && error.status === 409) {
        // 409 is "taken by another member" or "busy" (one code, CONFLICT); the list read again tells which (UX M4).
        conflict = true;
        focusAfterReload.current = item.handoffId;
      } else if (error instanceof ApiError && error.status === 404) {
        focusAfterReload.current = item.handoffId;
        onNotice({ tone: 'info', title: 'Yêu cầu không còn trong hộp chờ.', body: 'Danh sách đã được tải lại.' });
      } else if (error instanceof ApiError && error.forbidden) {
        setLoad({ phase: 'forbidden' });
        return;
      } else {
        // Claiming again is accepted by the backend, so the reload shows whether this one went through.
        focusAfterReload.current = item.handoffId;
        onNotice({ tone: 'warning', title: 'Chưa xác nhận được việc nhận xử lý.', body: `Danh sách đã được tải lại để kiểm tra. ${referenceText(error) ?? ''}`.trim() });
      }
    } finally {
      if (mounted.current) setClaiming(null);
    }
    if (!mounted.current) return;
    const items = await reload();
    if (conflict && mounted.current) {
      // F6 toasts. Taken: the row is now someone else's. Still waiting (or the read failed): busy. Gone, or now this
      // member's (an earlier claim went through): the re-read list says so, no toast contradicts it (security L-1).
      const row = items?.find((h) => h.handoffId === item.handoffId);
      if (items && !row) onNotice({ tone: 'info', title: 'Yêu cầu không còn trong hộp chờ.', body: 'Danh sách đã được tải lại.' });
      else if (row && row.status === 'CLAIMED' && !row.mine) toast.show('claimed-by-other');
      else if (!row || row.status === 'OPEN') toast.show('conversation-busy');
    }
  }

  const refreshing = load.phase === 'loading' || (load.phase === 'ready' && load.refreshing);
  return (
    <>
      <div ref={header} style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 'var(--space-4)', flexWrap: 'wrap' }}>
        <PageHeader title="Hộp chờ nhân viên"
          description="Cuộc trò chuyện trợ lý AI đã chuyển cho nhóm của bạn, cũ nhất ở trên." style={{ flex: '1 1 320px' }} />
        <Button variant="secondary" iconLeft="refresh-cw" loading={refreshing} onClick={() => void reload()}>Tải lại</Button>
      </div>

      {load.phase !== 'forbidden' && load.phase !== 'failed' && (
        <Alert tone="neutral" icon="lock" title="Danh sách không chứa thông tin khách hàng">
          Nội dung tin nhắn và báo giá chỉ hiện khi bạn nhận xử lý và mở cuộc trò chuyện. Mỗi lần mở được ghi vào nhật ký kiểm toán.
        </Alert>
      )}

      {notice && (
        <Alert tone={notice.tone} title={notice.title} onDismiss={() => onNotice(null)}>{notice.body ?? undefined}</Alert>
      )}

      {load.phase === 'loading' && <PageState kind="loading" loadingLabel="Đang tải hộp chờ" />}
      {load.phase === 'forbidden' && <PageState kind="forbidden" />}
      {load.phase === 'failed' && (
        <PageState kind={load.error instanceof ApiError && load.error.kind === 'timeout' ? 'timeout' : 'error'} error={load.error}
          title={load.error instanceof ApiError && load.error.kind === 'timeout' ? undefined : 'Chưa tải được hộp chờ'}
          description={load.error instanceof ApiError && load.error.status === 409 ? 'Hệ thống đang bận. Vui lòng thử lại sau ít giây.' : undefined}
          action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={() => void reload()}>Thử lại</Button>} />
      )}
      {load.phase === 'ready' && load.items.length === 0 && (
        <PageState kind="empty" icon="users" title="Chưa có yêu cầu nào"
          description="Khi trợ lý AI chuyển một cuộc trò chuyện cho nhóm bạn, yêu cầu sẽ hiện ở đây." />
      )}
      {load.phase === 'ready' && load.items.length > 0 && (
        // F2 artboard: the table keeps 860 px and scrolls inside its card when the column is narrower.
        <div ref={table} className="ap-scroll-x" aria-busy={load.refreshing || undefined}>
          <DataTable caption="Yêu cầu chuyển nhân viên của nhóm" rowKey="handoffId" rows={load.items} stickyHeader={false}
            style={{ minWidth: 860 }} columns={[
              { key: 'conversation', header: 'Cuộc trò chuyện', render: (h: Handoff) => <span className="ap-code">{shortConversation(h.conversationId)}</span> },
              { key: 'reason', header: 'Lý do chuyển', wrap: true, render: (h: Handoff) => <Reason item={h} /> },
              { key: 'status', header: 'Trạng thái', render: (h: Handoff) => <StatusBadge item={h} /> },
              { key: 'openedAt', header: 'Mở lúc', render: (h: Handoff) => <span style={{ fontVariantNumeric: 'tabular-nums', color: 'var(--text-body)' }}>{formatDateTime(h.openedAt)}</span> },
              { key: 'action', header: 'Thao tác', align: 'right', render: (h: Handoff) => (
                <RowAction item={h} claiming={claiming} onClaim={() => void claim(h)} onOpen={() => onOpen(h)} />
              ) },
            ]} />
        </div>
      )}
      {load.phase === 'ready' && (
        <p style={{ margin: 0, font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>
          Chỉ hiện yêu cầu của nhóm bạn. Yêu cầu đã hoàn tất không còn trong danh sách.
        </p>
      )}
    </>
  );
}

function Reason({ item }: { item: Handoff }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-0-5)' }}>
      <span>{reasonLabel(item.reasonCode)}</span>
      {item.followUpReasonCode && (
        <span style={{ font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>
          Sau khi hoàn tất: mở tiếp vì {reasonLabel(item.followUpReasonCode)}
        </span>
      )}
    </div>
  );
}

function RowAction({ item, claiming, onClaim, onOpen }: { item: Handoff; claiming: number | null; onClaim: () => void; onOpen: () => void }) {
  const ref = shortConversation(item.conversationId);
  if (item.status === 'OPEN') {
    const busy = claiming === item.handoffId;
    return (
      <Button size="sm" variant="secondary" iconLeft="check" loading={busy} disabled={claiming !== null} onClick={onClaim} data-handoff={item.handoffId}
        aria-label={busy ? 'Đang nhận…' : `Nhận xử lý cuộc trò chuyện ${ref}`}>
        {busy ? 'Đang nhận…' : 'Nhận xử lý'}
      </Button>
    );
  }
  if (item.mine) {
    return (
      <Button size="sm" variant="secondary" iconLeft="arrow-up-right" onClick={onOpen} data-handoff={item.handoffId}
        aria-label={`Mở cuộc trò chuyện ${ref}`}>Mở</Button>
    );
  }
  return <Button size="sm" variant="secondary" iconLeft="lock" disabled>Đã có người nhận</Button>;
}

function StatusBadge({ item }: { item: Handoff }) {
  if (item.status === 'OPEN') return <Badge tone="warning" shape="pill" size="sm">Chờ nhận</Badge>;
  if (item.mine) return <Badge tone="info" shape="pill" size="sm">Bạn đang xử lý</Badge>;
  return <Badge tone="neutral" shape="pill" size="sm">Nhân viên khác đang xử lý</Badge>;
}
