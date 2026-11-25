import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { claimHandoff, listHandoffs, type Handoff } from '../../api/staff';
import { reasonLabel, shortConversation } from '../../app/staffLabels';
import { failureText, formatDateTime, referenceText } from '../../app/texts';
import { Alert, Badge, Button, EmptyState, Icon, Skeleton } from '../../design-system';

// F2 · HO-01 staff inbox (UC-06; specs/50 §F2). The backend lists the team's open work only and records
// every read (STAFF_LIST_READ), so the list is read on entry, after a claim or a resolve and on "Tải lại"
// only (A-69, 1A). No customer identity or message content is ever part of a row.

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
  notice: InboxNotice | null;
  onNotice: (notice: InboxNotice | null) => void;
  onOpen: (handoff: Handoff) => void;
  onUnauthenticated: () => void;
}

export function StaffInboxPage({ notice, onNotice, onOpen, onUnauthenticated }: Props) {
  const [load, setLoad] = useState<Load>({ phase: 'loading' });
  const [claiming, setClaiming] = useState<number | null>(null);
  const mounted = useRef(true);
  const heading = useRef<HTMLHeadingElement>(null);
  const table = useRef<HTMLDivElement>(null);
  /** After a claim, focus moves to that row's "Mở" button once the list is read again (UX review M3). */
  const focusAfterReload = useRef<number | null>(null);
  useEffect(() => {
    mounted.current = true;
    // Coming back from a conversation (a notice is shown): focus starts at the page heading.
    if (notice) heading.current?.focus();
    return () => { mounted.current = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const reload = useCallback(async () => {
    // Rows already on screen stay while the list is read again; the skeleton is for the first read only.
    setLoad((current) => (current.phase === 'ready' ? { ...current, refreshing: true } : { phase: 'loading' }));
    try {
      const items = await listHandoffs();
      if (mounted.current) setLoad({ phase: 'ready', items, refreshing: false });
    } catch (error) {
      if (!mounted.current) return;
      if (error instanceof ApiError && error.unauthenticated) { onUnauthenticated(); return; }
      if (error instanceof ApiError && error.forbidden) { setLoad({ phase: 'forbidden' }); return; }
      setLoad({ phase: 'failed', error });
    }
  }, [onUnauthenticated]);

  useEffect(() => {
    if (load.phase !== 'ready' || load.refreshing || focusAfterReload.current === null) return;
    const id = focusAfterReload.current;
    focusAfterReload.current = null;
    const target = table.current?.querySelector<HTMLButtonElement>(`[data-handoff="${id}"] button`) ?? heading.current;
    target?.focus();
  }, [load]);

  useEffect(() => { void reload(); }, [reload]);

  async function claim(item: Handoff) {
    if (claiming !== null) return;
    setClaiming(item.handoffId);
    onNotice(null);
    try {
      await claimHandoff(item.handoffId);
      if (!mounted.current) return;
      focusAfterReload.current = item.handoffId;
      onNotice({ tone: 'success', title: 'Đã nhận xử lý.', body: 'Bấm "Mở" để xem cuộc trò chuyện.' });
    } catch (error) {
      if (!mounted.current) return;
      if (error instanceof ApiError && error.unauthenticated) { onUnauthenticated(); return; }
      if (error instanceof ApiError && error.status === 409) {
        // 409 is "taken by another member" or "busy": the reloaded list shows which (UX review M4).
        onNotice({ tone: 'warning', title: 'Chưa nhận được yêu cầu này.', body: 'Có thể nhân viên khác vừa nhận hoặc hệ thống đang bận. Danh sách đã được tải lại.' });
      } else if (error instanceof ApiError && error.status === 404) {
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
    if (mounted.current) await reload();
  }

  return (
    <section aria-labelledby="inbox-title" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      <div style={{ display: 'flex', alignItems: 'flex-end', justifyContent: 'space-between', gap: 'var(--space-4)', flexWrap: 'wrap' }}>
        <div>
          <h1 id="inbox-title" ref={heading} tabIndex={-1} style={{ margin: 0, font: 'var(--type-page-title)', outline: 'none' }}>Hộp chờ nhân viên</h1>
          <p style={{ margin: '4px 0 0', font: 'var(--type-body)', color: 'var(--text-secondary)' }}>
            Yêu cầu chưa hoàn tất của nhóm bạn, cũ nhất ở trên. Danh sách không có tên khách hay nội dung tin nhắn.
          </p>
        </div>
        <Button variant="secondary" iconLeft="refresh-cw" loading={load.phase === 'loading' || (load.phase === 'ready' && load.refreshing)}
          onClick={() => void reload()}>Tải lại</Button>
      </div>

      {notice && (
        <Alert tone={notice.tone} title={notice.title} onDismiss={() => onNotice(null)}>{notice.body ?? undefined}</Alert>
      )}

      {load.phase === 'loading' && <Skeleton lines={4} />}
      {load.phase === 'forbidden' && (
        <Alert tone="neutral" icon="lock" title="Bạn không có quyền xem hộp chờ nhân viên.">Liên hệ quản trị hệ thống để được cấp quyền.</Alert>
      )}
      {load.phase === 'failed' && (
        <Alert tone="error" title="Chưa tải được hộp chờ"
          action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={() => void reload()}>Thử lại</Button>}>
          {load.error instanceof ApiError && load.error.status === 409 ? 'Hệ thống đang bận. Vui lòng thử lại sau ít giây.' : failureText(load.error)}
          {referenceText(load.error) && <span style={{ display: 'block' }}>{referenceText(load.error)}</span>}
        </Alert>
      )}
      {load.phase === 'ready' && load.items.length === 0 && (
        <EmptyState icon="users" title="Chưa có yêu cầu nào" description="Khi trợ lý AI chuyển một cuộc trò chuyện cho nhóm bạn, yêu cầu sẽ hiện ở đây." />
      )}
      {load.phase === 'ready' && load.items.length > 0 && (
        <div ref={table} aria-busy={load.refreshing || undefined} style={{ overflowX: 'auto', background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-card)', borderRadius: 'var(--radius-lg)' }}>
          <table style={{ width: '100%', borderCollapse: 'collapse', font: 'var(--type-table)' }}>
            <caption style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>Yêu cầu chuyển nhân viên của nhóm</caption>
            <thead>
              <tr style={{ font: 'var(--type-table-header)', color: 'var(--text-secondary)', textAlign: 'left', background: 'var(--bg-subtle)' }}>
                {['Cuộc trò chuyện', 'Lý do', 'Việc tiếp theo', 'Trạng thái', 'Mở lúc', 'Thao tác'].map((h) => (
                  <th key={h} scope="col" style={{ padding: 'var(--space-2) var(--space-3)' }}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {load.items.map((item) => (
                <tr key={item.handoffId} data-handoff={item.handoffId} style={{ borderTop: '1px solid var(--border-subtle)' }}>
                  <td className="ap-code" style={{ padding: 'var(--space-2) var(--space-3)' }}>{shortConversation(item.conversationId)}</td>
                  <td style={{ padding: 'var(--space-2) var(--space-3)' }}>{reasonLabel(item.reasonCode)}</td>
                  <td style={{ padding: 'var(--space-2) var(--space-3)', color: 'var(--text-secondary)' }}>{item.followUpReasonCode ? reasonLabel(item.followUpReasonCode) : '—'}</td>
                  <td style={{ padding: 'var(--space-2) var(--space-3)' }}><StatusBadge item={item} /></td>
                  <td style={{ padding: 'var(--space-2) var(--space-3)', whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>{formatDateTime(item.openedAt)}</td>
                  <td style={{ padding: 'var(--space-2) var(--space-3)' }}>
                    {item.status === 'OPEN' && (
                      <Button size="sm" loading={claiming === item.handoffId} disabled={claiming !== null} onClick={() => void claim(item)}
                        aria-label={claiming === item.handoffId ? 'Đang nhận…' : `Nhận xử lý cuộc trò chuyện ${shortConversation(item.conversationId)}`}>
                        {claiming === item.handoffId ? 'Đang nhận…' : 'Nhận xử lý'}
                      </Button>
                    )}
                    {item.status === 'CLAIMED' && item.mine && (
                      <Button size="sm" variant="secondary" iconRight="chevron-right" onClick={() => onOpen(item)}
                        aria-label={`Mở cuộc trò chuyện ${shortConversation(item.conversationId)}`}>Mở</Button>
                    )}
                    {item.status === 'CLAIMED' && !item.mine && (
                      <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: 'var(--text-secondary)' }}><Icon name="lock" size={14} />Đã có người nhận</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function StatusBadge({ item }: { item: Handoff }) {
  if (item.status === 'OPEN') return <Badge tone="warning" shape="pill">Chờ nhận</Badge>;
  if (item.mine) return <Badge tone="info" shape="pill">Bạn đang xử lý</Badge>;
  return <Badge tone="neutral" shape="pill">Nhân viên khác đang xử lý</Badge>;
}
