import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { readSwitch, setSwitch, type DisabledReason, type SwitchState } from '../api/killSwitch';
import { failureText, formatDateTime, referenceText } from '../app/texts';
import { Alert, Badge, Button, DescriptionList, Dialog, Spinner, Switch } from '../design-system';

// F5 · OP-01 · AI kill switch (UC-08; docs/design/specs/50-batch-f-man-hinh-thieu.md, prototype
// F5-KillSwitch.dc.html as behavior reference only). The backend decides who may read or change the switch
// (OPS only, A-44); this screen only shows its answers. A state the screen cannot read is shown as
// "treated as off", never as on. Turning the AI off needs a confirmation; turning it on does not.

export const REASON_LABELS: Record<DisabledReason, string> = {
  OPERATOR: 'Do người vận hành tắt',
  PROVIDER_AUTH: 'Nhà cung cấp AI từ chối khóa truy cập',
  UNREADABLE: 'Không đọc được trạng thái',
  UNKNOWN: 'Không rõ lý do',
};

const PROPAGATION = 'Máy chủ này áp dụng ngay. Các máy chủ khác áp dụng trong tối đa 30 giây. Lượt đang chạy không bị hủy giữa chừng.';

type Load =
  | { phase: 'loading' }
  | { phase: 'ready'; state: SwitchState }
  | { phase: 'unreadable'; error: unknown }
  | { phase: 'forbidden' };

interface Notice {
  tone: 'success' | 'warning' | 'error';
  title: string;
  body?: string;
  reference?: string | null;
}

interface Props {
  onUnauthenticated: () => void;
}

export function KillSwitchPage({ onUnauthenticated }: Props) {
  const [load, setLoad] = useState<Load>({ phase: 'loading' });
  const [confirmOff, setConfirmOff] = useState(false);
  const [pending, setPending] = useState<boolean | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const mounted = useRef(true);
  // Set again on every mount: React 18 StrictMode (dev) mounts, unmounts and remounts effects.
  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  const handleError = useCallback((error: unknown): boolean => {
    if (error instanceof ApiError && error.unauthenticated) {
      onUnauthenticated();
      return true;
    }
    if (error instanceof ApiError && error.forbidden) {
      setLoad({ phase: 'forbidden' });
      return true;
    }
    return false;
  }, [onUnauthenticated]);

  const reload = useCallback(async () => {
    setLoad({ phase: 'loading' });
    try {
      const state = await readSwitch();
      if (mounted.current) setLoad({ phase: 'ready', state });
    } catch (error) {
      if (mounted.current && !handleError(error)) setLoad({ phase: 'unreadable', error });
    }
  }, [handleError]);

  useEffect(() => { void reload(); }, [reload]);

  async function change(enabled: boolean) {
    if (pending !== null) {
      return;
    }
    setPending(enabled);
    setNotice(null);
    try {
      const state = await setSwitch(enabled);
      if (!mounted.current) return;
      setConfirmOff(false);
      setLoad({ phase: 'ready', state });
      setNotice({ tone: 'success', title: enabled ? 'Đã bật lại trợ lý AI.' : 'Đã tắt trợ lý AI.' });
    } catch (error) {
      if (!mounted.current) return;
      setConfirmOff(false);
      if (handleError(error)) return;
      const action = enabled ? 'bật' : 'tắt';
      if (error instanceof ApiError && error.kind === 'http' && error.status !== null && error.status < 500) {
        // A 4xx answer: the server refused the request before changing anything.
        setNotice({ tone: 'error', title: `Chưa ${action} được trợ lý AI`, body: `Chưa có thay đổi nào được ghi. ${failureText(error)}`, reference: referenceText(error) });
      } else {
        // Timeout, network failure, 5xx or an unreadable answer: the change may have been stored (the backend
        // answers 503 also for a commit whose outcome is unknown). Never retry the write; read the state
        // again (a safe GET), so the screen never keeps showing the state from before the request.
        setNotice({ tone: 'warning', title: 'Chưa xác nhận được kết quả', body: 'Trạng thái có thể đã thay đổi. Màn hình đã đọc lại trạng thái hiện tại.', reference: referenceText(error) });
        await reload();
      }
    } finally {
      if (mounted.current) setPending(null);
    }
  }

  return (
    <section aria-labelledby="kill-switch-title" style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-5)', maxWidth: 760 }}>
      <div>
        <h1 id="kill-switch-title" style={{ margin: 0, font: 'var(--type-page-title)' }}>Công tắc trợ lý AI</h1>
        <p style={{ margin: '4px 0 0', font: 'var(--type-body)', color: 'var(--text-secondary)' }}>
          Tắt khẩn cấp việc gọi nhà cung cấp AI cho toàn bộ hệ thống. Màn hình này không hiển thị dữ liệu kinh doanh.
        </p>
      </div>

      {notice && (
        <Alert tone={notice.tone} title={notice.title} onDismiss={() => setNotice(null)}>
          {notice.body || notice.reference ? <>
            {notice.body}
            {notice.reference && <span style={{ display: 'block' }}>{notice.reference}</span>}
          </> : undefined}
        </Alert>
      )}

      {load.phase === 'loading' && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', color: 'var(--text-secondary)', font: 'var(--type-body)' }}>
          <Spinner label="Đang tải trạng thái" /> Đang tải trạng thái…
        </div>
      )}

      {load.phase === 'forbidden' && (
        <Alert tone="neutral" icon="lock" title="Bạn không có quyền xem công tắc trợ lý AI.">
          Liên hệ quản trị hệ thống để được cấp quyền.
        </Alert>
      )}

      {load.phase === 'unreadable' && (
        <Alert tone="error" title="AI được coi là đang tắt"
          action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={() => void reload()}>Tải lại</Button>}>
          Không đọc được trạng thái. Vui lòng thử tải lại; nếu vẫn lỗi, báo đội vận hành hệ thống.
          {referenceText(load.error) && <span style={{ display: 'block' }}>{referenceText(load.error)}</span>}
        </Alert>
      )}

      {load.phase === 'ready' && (
        <StatePanel state={load.state} pending={pending}
          onToggle={(next) => (next ? void change(true) : setConfirmOff(true))} />
      )}

      <Dialog open={confirmOff} tone="danger" title="Tắt trợ lý AI?"
        description="Khách hàng vẫn nhắn được, nhưng mọi yêu cầu sẽ chờ nhân viên kinh doanh. Thao tác được ghi vào nhật ký kiểm toán."
        onClose={pending === null ? () => setConfirmOff(false) : undefined}
        footer={<>
          <Button variant="secondary" disabled={pending !== null} onClick={() => setConfirmOff(false)}>Hủy</Button>
          <Button variant="danger" loading={pending === false} onClick={() => void change(false)}>
            {pending === false ? 'Đang tắt…' : 'Tắt AI'}
          </Button>
        </>}>
        <p style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>{PROPAGATION}</p>
      </Dialog>
    </section>
  );
}

function StatePanel({ state, pending, onToggle }: { state: SwitchState; pending: boolean | null; onToggle: (next: boolean) => void }) {
  const items = [
    { label: 'Trạng thái', value: state.aiEnabled ? 'Đang bật' : 'Đang tắt' },
    ...(state.aiEnabled ? [] : [{ label: 'Lý do', value: REASON_LABELS[state.reason ?? 'UNKNOWN'] }]),
    { label: state.aiEnabled ? 'Bật từ' : 'Tắt từ', value: state.since ? formatDateTime(state.since) : '—' },
  ];
  return (
    <div style={{ background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-card)', borderRadius: 'var(--radius-lg)',
      boxShadow: 'var(--shadow-card)', padding: 'var(--space-5)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-4)', flexWrap: 'wrap' }}>
        <h2 style={{ margin: 0, font: 'var(--type-card-title)' }}>Trạng thái hiện tại</h2>
        <Badge tone={state.aiEnabled ? 'success' : 'error'} shape="pill">{state.aiEnabled ? 'AI đang bật' : 'AI đang tắt'}</Badge>
      </div>
      <Switch checked={state.aiEnabled} disabled={pending !== null} label="Trợ lý AI"
        description={pending === true ? 'Đang bật…' : state.aiEnabled ? 'Gạt để tắt. Cần xác nhận.' : 'Gạt để bật lại.'}
        onChange={onToggle} />
      <DescriptionList columns={3} items={items} />
      {!state.aiEnabled && state.reason === 'PROVIDER_AUTH' && (
        <Alert tone="warning" title={REASON_LABELS.PROVIDER_AUTH}>
          Hệ thống tự tắt. Thay khóa trong kho bí mật, rồi bật lại bằng công tắc.
        </Alert>
      )}
      {!state.aiEnabled && state.reason === 'UNREADABLE' && (
        <Alert tone="error" title="AI được coi là đang tắt">
          Không đọc được cấu hình. Vui lòng thử tải lại; nếu vẫn lỗi, báo đội vận hành hệ thống.
        </Alert>
      )}
      <p style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>{PROPAGATION}</p>
    </div>
  );
}
