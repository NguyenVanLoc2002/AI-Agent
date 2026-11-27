import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { ApiError } from '../api/client';
import { readSwitch, setSwitch, type DisabledReason, type SwitchState } from '../api/killSwitch';
import { failureText, formatDateTime, referenceText } from '../app/texts';
import { PageState } from '../app/PageState';
import { Alert, Badge, Button, DescriptionList, Dialog, PageHeader, Spinner, Switch } from '../design-system';

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

// F5 artboard switch copy.
const SWITCH_LABEL = 'Cho phép trợ lý AI trả lời khách';
const SWITCH_HINT = 'Khi tắt, mọi lượt chat mới được chuyển cho nhân viên với lý do “Trợ lý AI đang tắt” và không gọi nhà cung cấp AI.';

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

  // P3 review UX M2: what is on screen stays while the state is read again (the switch keeps the focus it got back
  // from the dialog); the spinner is for the first read only. `focusTitle`: the control that asked is replaced.
  const reload = useCallback(async (focusTitle = false) => {
    setLoad((current) => (current.phase === 'ready' || current.phase === 'unreadable' ? current : { phase: 'loading' }));
    try {
      const state = await readSwitch();
      if (mounted.current) setLoad({ phase: 'ready', state });
    } catch (error) {
      if (mounted.current && !handleError(error)) setLoad({ phase: 'unreadable', error });
    }
    if (focusTitle && mounted.current) window.setTimeout(() => document.getElementById('kill-switch-state')?.focus(), 0);
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
      if (error instanceof ApiError && error.kind === 'http' && error.status !== null && error.status < 500) {
        // A 4xx answer: the server refused the request before changing anything.
        // F6 artboard "Đổi công tắc · F5"; a 429 keeps its own advice.
        setNotice({ tone: 'error', title: 'Không đổi được trạng thái AI',
          body: error.status === 429 ? `Trạng thái hiện tại giữ nguyên. ${failureText(error)}` : 'Trạng thái hiện tại giữ nguyên. Tải lại trang để xem trạng thái mới nhất rồi thử lại.',
          reference: referenceText(error) });
      } else {
        // Timeout, network failure, 5xx or an unreadable answer: the change may have been stored (the backend
        // answers 503 also for a commit whose outcome is unknown). Never retry the write; read the state
        // again (a safe GET), so the screen never keeps showing the state from before the request.
        setNotice({ tone: 'warning', title: 'Chưa xác nhận được kết quả', body: 'Trạng thái có thể đã thay đổi. Màn hình đã đọc lại trạng thái hiện tại.', reference: referenceText(error) });
        await reload();
      }
    } finally {
      if (mounted.current) {
        setPending(null);
        // The dialog closed while the switch was still locked, so the focus could not go back to it (P3 review UX
        // M2): once unlocked, the switch (or the card title, if the state is no longer readable) takes it.
        window.setTimeout(() => {
          if (document.activeElement !== null && document.activeElement !== document.body) return;
          const card = document.getElementById('kill-switch-state')?.closest('section');
          (card?.querySelector<HTMLElement>('[role="switch"]') ?? document.getElementById('kill-switch-state'))?.focus();
        }, 0);
      }
    }
  }

  return (
    <>
      <PageHeader title="Công tắc trợ lý AI"
        description="Tắt khẩn cấp việc gọi nhà cung cấp AI cho toàn bộ hệ thống. Màn hình này không hiển thị dữ liệu kinh doanh." />

      {notice && (
        <Alert tone={notice.tone} title={notice.title} onDismiss={() => setNotice(null)}>
          {notice.body || notice.reference ? <>
            {notice.body}
            {notice.reference && <span style={{ display: 'block' }}>{notice.reference}</span>}
          </> : undefined}
        </Alert>
      )}

      {load.phase === 'forbidden' ? (
        <PageState kind="forbidden" />
      ) : (
        // F5 artboard: the state card is half of the page body (flex 1 1 420); the confirmation is the Dialog below.
        <div className="ap-cluster" style={{ gap: 'var(--space-5)', alignItems: 'flex-start' }}>
          <section aria-labelledby="kill-switch-state" style={CARD}>
            {load.phase === 'loading' && (
              <div style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)', color: 'var(--text-secondary)', font: 'var(--type-body)' }}>
                <Spinner label="" /> Đang tải trạng thái…
              </div>
            )}
            {load.phase === 'unreadable' && <UnreadablePanel error={load.error} onReload={() => void reload(true)} />}
            {load.phase === 'ready' && (
              <StatePanel state={load.state} pending={pending}
                onToggle={(next) => (next ? void change(true) : setConfirmOff(true))} />
            )}
          </section>
        </div>
      )}

      <Dialog open={confirmOff} tone="danger" title="Tắt trợ lý AI?"
        description="Khách hàng vẫn nhắn được, nhưng mọi yêu cầu sẽ chờ nhân viên kinh doanh. Thao tác được ghi vào nhật ký kiểm toán."
        onClose={pending === null ? () => setConfirmOff(false) : undefined}
        footer={<>
          <Button variant="secondary" disabled={pending !== null} onClick={() => setConfirmOff(false)}>Hủy</Button>
          <Button variant="danger" iconLeft="circle-pause" loading={pending === false} onClick={() => void change(false)}>
            {pending === false ? 'Đang tắt…' : 'Tắt AI'}
          </Button>
        </>}>
        <p style={{ margin: 0, font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>{PROPAGATION}</p>
      </Dialog>
    </>
  );
}

// F5 artboard section "Trạng thái hiện tại": surface card, radius lg, padding 24, gap 16, flex 1 1 420.
const CARD = {
  flex: '1 1 420px', minWidth: 0, maxWidth: '100%', boxSizing: 'border-box', background: 'var(--bg-surface)',
  border: 'var(--border-width-default) solid var(--border-card)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-6)',
  display: 'flex', flexDirection: 'column', gap: 'var(--space-4)',
} as const;

function CardTitle({ badge }: { badge: ReactNode }) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-2)', flexWrap: 'wrap' }}>
      <h2 id="kill-switch-state" tabIndex={-1} style={{ margin: 0, font: 'var(--type-section-title)' }}>Trạng thái hiện tại</h2>
      {badge}
    </div>
  );
}

// Badge tone per reason, as the artboard's "Khi AI đang tắt, màn hình hiển thị lý do" cards.
const OFF_TONE: Record<DisabledReason, 'warning' | 'error' | 'restricted'> = {
  OPERATOR: 'warning', PROVIDER_AUTH: 'error', UNREADABLE: 'restricted', UNKNOWN: 'warning',
};

function reasonHint(state: SwitchState): string {
  switch (state.reason) {
    case 'PROVIDER_AUTH': return 'Hệ thống tự tắt. Thay khóa trong kho bí mật, rồi bật lại bằng công tắc.';
    case 'UNREADABLE': return 'Không đọc được cấu hình. Vui lòng thử tải lại; nếu vẫn lỗi, báo đội vận hành hệ thống.';
    default: return state.since ? `Từ ${formatDateTime(state.since)}. Bật lại bằng công tắc ở trên.` : 'Bật lại bằng công tắc ở trên.';
  }
}

// The artboard repeats the badge in each reason card because it shows the variants side by side; the screen shows one
// state, so the badge is only in the card title.
function ReasonBlock({ label, hint }: { label: string; hint: string }) {
  return (
    <div style={{ background: 'var(--bg-nested)', borderRadius: 'var(--radius-md)', padding: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', alignItems: 'flex-start' }}>
      <span style={{ font: 'var(--type-label)' }}>{label}</span>
      <span style={{ font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>{hint}</span>
    </div>
  );
}

function StatePanel({ state, pending, onToggle }: { state: SwitchState; pending: boolean | null; onToggle: (next: boolean) => void }) {
  // The API gives the time of the last change and, while off, its reason. It has no "last reason" once the AI is on
  // again, so the artboard row "Lý do tắt gần nhất" is left out (P3 deviation table).
  const items = [
    { label: 'Thay đổi gần nhất', value: state.since ? `${state.aiEnabled ? 'Bật lại lúc' : 'Tắt lúc'} ${formatDateTime(state.since)}` : '—' },
  ];
  const reason = state.reason ?? 'UNKNOWN';
  return (
    <>
      <CardTitle badge={<Badge tone={state.aiEnabled ? 'success' : OFF_TONE[reason]} shape="pill">{state.aiEnabled ? 'AI đang bật' : 'AI đang tắt'}</Badge>} />
      <Switch checked={state.aiEnabled} disabled={pending !== null} label={SWITCH_LABEL}
        description={pending === true ? 'Đang bật…' : SWITCH_HINT}
        onChange={onToggle} />
      <DescriptionList columns={1} items={items} />
      {!state.aiEnabled && (
        <ReasonBlock label={REASON_LABELS[reason]} hint={reasonHint(state)} />
      )}
      <Alert tone="info" icon="clock" title="Thời gian áp dụng">{PROPAGATION}</Alert>
    </>
  );
}

function UnreadablePanel({ error, onReload }: { error: unknown; onReload: () => void }) {
  const reference = referenceText(error);
  return (
    <>
      <CardTitle badge={<Badge tone="restricted" shape="pill">Không đọc được trạng thái</Badge>} />
      <ReasonBlock label="AI được coi là đang tắt"
        hint={`Không đọc được cấu hình. Vui lòng thử tải lại; nếu vẫn lỗi, báo đội vận hành hệ thống.${reference ? ` ${reference}` : ''}`} />
      <div><Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={onReload}>Tải lại</Button></div>
    </>
  );
}
