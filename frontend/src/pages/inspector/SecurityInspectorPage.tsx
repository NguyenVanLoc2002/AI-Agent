import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { ApiError } from '../../api/client';
import {
  ACTOR_ROLES, EVENT_TYPES, MAX_TRACE_ROWS, MAX_WINDOW_MS, PAGE_SIZE, readEvents, readTrace, TRACE_ID,
  type ActorRole, type EgressCheck, type EventQuery, type EventType, type SecurityEvent, type Trace,
} from '../../api/inspector';
import {
  actorParts, EVENT_TYPE_LABELS, formatSeconds, fromLocalInput, outcomeTone, roleLabel, shortTrace, toLocalInput, utcOffset,
} from '../../app/inspectorLabels';
import { PageState } from '../../app/PageState';
import { failureText, referenceText } from '../../app/texts';
import { Alert, Badge, Button, DescriptionList, EmptyState, Input, PageHeader, Select } from '../../design-system';

// F4 · SI-01 Security Inspector (UC-07, SECREV; specs/50 §F4, A-70). Metadata only: codes, field names, tool
// names, roles, staff pseudonyms and trace IDs. Every read is audited by the backend (INSPECTOR_READ), so the
// events are read on entry, on "Áp dụng" and on "Tải thêm", and a trace only when the reviewer asks for one
// that is not already loading or shown (A-70 3A; security review L-1): a changed filter is not read until
// applied. The backend checks the role, the window and the cursor again; the checks here only give feedback
// at the field.
// FE-UI-P3: layout and copy of the approved artboard F4-Inspector; page states of F6-StaffStates. The trace keeps
// tables (a trace has several checks and tool calls, each with time and codes) inside the artboard's nested boxes,
// one above the other: side by side (artboard) a 9-column table would scroll at every width.

type Range = '24h' | '7d' | 'custom';

interface Draft {
  type: '' | EventType;
  role: '' | ActorRole;
  range: Range;
  from: string;
  to: string;
}

interface FieldErrors { from?: string; to?: string }

type List =
  | { phase: 'loading' }
  | { phase: 'ready'; events: SecurityEvent[]; next: string | null; refreshing: boolean; more: 'idle' | 'loading' | { error: unknown } }
  | { phase: 'failed'; error: unknown };

type TraceState =
  | { phase: 'idle' }
  | { phase: 'loading'; traceId: string }
  | { phase: 'ready'; trace: Trace }
  | { phase: 'not-found'; traceId: string }
  | { phase: 'failed'; traceId: string; error: unknown };

/** What moves the focus once an events read answers (UX review H1: a busy button loses the focus). */
type FocusAfter = 'none' | 'summary' | { row: number };

const HOUR_MS = 60 * 60 * 1000;
/** A custom end this far past the viewer's clock is refused at the field (the backend allows one day of skew). */
const FUTURE_SLACK_MS = 5 * 60 * 1000;
const RANGE_OPTIONS = [
  { value: '24h', label: '24 giờ qua' },
  { value: '7d', label: '7 ngày qua' },
  { value: 'custom', label: 'Tùy chọn' },
];
const TYPE_OPTIONS = [{ value: '', label: 'Tất cả' }, ...EVENT_TYPES.map((t) => ({ value: t, label: `${EVENT_TYPE_LABELS[t]} (${t})` }))];
const ROLE_OPTIONS = [{ value: '', label: 'Tất cả' }, ...ACTOR_ROLES.map((r) => ({ value: r, label: roleLabel(r) }))];
// Cells 12 px wide-padded, not the artboard's 16: these tables have 7–9 columns of codes (deviation table).
const cell = { padding: 'var(--space-2) var(--space-3)', verticalAlign: 'top', borderBottom: 'var(--border-width-default) solid var(--border-subtle)' } as const;
const head = {
  padding: 'var(--space-3)', whiteSpace: 'nowrap', font: 'var(--type-table-header)', textTransform: 'uppercase',
  letterSpacing: 'var(--tracking-caps)', borderBottom: 'var(--border-width-default) solid var(--border-default)',
} as const;
const headRow = { color: 'var(--text-secondary)', textAlign: 'left' } as const;
/** A nested box of the trace card (artboard: bg-nested, radius md, padding 16, overline title). */
const nested = { background: 'var(--bg-nested)', borderRadius: 'var(--radius-md)', padding: 'var(--space-4)', display: 'flex', flexDirection: 'column', gap: 'var(--space-2)', minWidth: 0 } as const;
const overline = { margin: 0, font: 'var(--type-overline)', textTransform: 'uppercase', color: 'var(--text-secondary)' } as const;
const hidden = { position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)', whiteSpace: 'nowrap' } as const;
const panel = { background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-card)', borderRadius: 'var(--radius-lg)' } as const;

/** The query of a draft, with the window frozen at "now" so every page of it reads the same window. */
function toQuery(draft: Draft, now: Date): { query: EventQuery } | { errors: FieldErrors } {
  const type = draft.type === '' ? null : draft.type;
  const role = draft.role === '' ? null : draft.role;
  if (draft.range !== 'custom') {
    const span = draft.range === '24h' ? 24 * HOUR_MS : MAX_WINDOW_MS;
    return { query: { type, role, from: new Date(now.getTime() - span), to: now } };
  }
  const from = fromLocalInput(draft.from);
  const to = fromLocalInput(draft.to);
  const errors: FieldErrors = {};
  if (from === null) errors.from = 'Chọn ngày và giờ bắt đầu.';
  if (to === null) errors.to = 'Chọn ngày và giờ kết thúc.';
  else if (to.getTime() > now.getTime() + FUTURE_SLACK_MS) errors.to = 'Thời điểm kết thúc không được ở tương lai.';
  if (from !== null && to !== null && !errors.to) {
    if (from.getTime() >= to.getTime()) errors.to = 'Thời điểm kết thúc phải sau thời điểm bắt đầu.';
    else if (to.getTime() - from.getTime() > MAX_WINDOW_MS) errors.to = 'Mỗi lần xem tối đa 7 ngày. Hãy rút ngắn khoảng thời gian.';
  }
  return from !== null && to !== null && !errors.to ? { query: { type, role, from, to } } : { errors };
}

function describe(query: EventQuery): string {
  return [
    `${formatSeconds(query.from)} – ${formatSeconds(query.to)} (${utcOffset(query.to)})`,
    query.type === null ? 'mọi loại sự kiện' : EVENT_TYPE_LABELS[query.type],
    query.role === null ? 'mọi vai trò' : roleLabel(query.role),
  ].join(' · ');
}

const sameDraft = (a: Draft, b: Draft) => a.type === b.type && a.role === b.role && a.range === b.range
  && (a.range !== 'custom' || (a.from === b.from && a.to === b.to));
const invalidFilter = (error: unknown) => error instanceof ApiError && error.status === 400;

export function SecurityInspectorPage({ onUnauthenticated }: { onUnauthenticated: () => void }) {
  const [draft, setDraft] = useState<Draft>(() => {
    const now = new Date();
    return { type: '', role: '', range: '24h', from: toLocalInput(new Date(now.getTime() - 24 * HOUR_MS)), to: toLocalInput(now) };
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  /** The query on screen and the draft it came from (UX review M3: changed but not applied). */
  const [applied, setApplied] = useState<{ query: EventQuery; draft: Draft } | null>(null);
  const [list, setList] = useState<List>({ phase: 'loading' });
  const [trace, setTrace] = useState<TraceState>({ phase: 'idle' });
  const [lookup, setLookup] = useState('');
  const [lookupError, setLookupError] = useState<string | undefined>(undefined);
  const [forbidden, setForbidden] = useState(false);
  /** One persistent polite live region for every result (UX review M4). */
  const [announcement, setAnnouncement] = useState('');
  const mounted = useRef(true);
  /** StrictMode mounts twice in dev; the entry read is sent once (security review L-2). */
  const entryRead = useRef(false);
  /** Only the answer of the newest events read and the newest trace read is shown. */
  const listGeneration = useRef(0);
  const traceGeneration = useRef(0);
  const traceRef = useRef<TraceState>(trace);
  traceRef.current = trace;
  const focusAfter = useRef<FocusAfter>('none');
  const summary = useRef<HTMLParagraphElement>(null);
  const eventsTable = useRef<HTMLDivElement>(null);
  const traceSection = useRef<HTMLElement>(null);
  const traceHeading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    mounted.current = true;
    return () => { mounted.current = false; };
  }, []);

  /** Handles what every read has in common; true when the screen must not show anything further. */
  function stopped(error: unknown): boolean {
    if (!mounted.current) return true;
    if (error instanceof ApiError && error.unauthenticated) { onUnauthenticated(); return true; }
    if (error instanceof ApiError && error.forbidden) { setForbidden(true); return true; }
    return false;
  }

  async function load(query: EventQuery, from: Draft, focus: FocusAfter) {
    const generation = ++listGeneration.current;
    setApplied({ query, draft: from });
    // Rows on screen stay, dimmed, while the next query is read (UX review L14); a first read shows a skeleton.
    setList((current) => (current.phase === 'ready' ? { ...current, refreshing: true, more: 'idle' } : { phase: 'loading' }));
    try {
      const page = await readEvents(query);
      if (!mounted.current || generation !== listGeneration.current) return;
      focusAfter.current = focus;
      setList({ phase: 'ready', events: page.events, next: page.next, refreshing: false, more: 'idle' });
      setAnnouncement(page.events.length === 0 ? 'Không có sự kiện trong khoảng này.' : `Đã tải ${page.events.length} sự kiện.`);
    } catch (error) {
      if (generation !== listGeneration.current || stopped(error)) return;
      focusAfter.current = focus;
      setList({ phase: 'failed', error });
      setAnnouncement('Chưa tải được sự kiện bảo mật.');
    }
  }

  async function loadMore() {
    if (list.phase !== 'ready' || list.next === null || list.more === 'loading' || list.refreshing || applied === null) return;
    const generation = listGeneration.current;
    const cursor = list.next;
    const firstNew = list.events.length;
    setList({ ...list, more: 'loading' });
    try {
      const page = await readEvents(applied.query, cursor);
      if (!mounted.current || generation !== listGeneration.current) return;
      focusAfter.current = page.events.length > 0 ? { row: firstNew } : 'summary';
      setList((current) => current.phase === 'ready'
        ? { phase: 'ready', events: [...current.events, ...page.events], next: page.next, refreshing: false, more: 'idle' }
        : current);
      setAnnouncement(`Đã tải thêm ${page.events.length} sự kiện.`);
    } catch (error) {
      if (generation !== listGeneration.current || stopped(error)) return;
      focusAfter.current = 'summary';
      setList((current) => current.phase === 'ready' ? { ...current, more: { error } } : current);
      setAnnouncement('Chưa tải thêm được sự kiện.');
    }
  }

  async function openTrace(traceId: string, retry = false) {
    const current = traceRef.current;
    traceSection.current?.scrollIntoView?.({ block: 'start' });
    // A trace already loading or on screen is not read again (each read is audited); "Thử lại" is explicit.
    if (!retry && current.phase === 'loading' && current.traceId === traceId) return;
    if (!retry && current.phase === 'ready' && current.trace.traceId === traceId) { traceHeading.current?.focus(); return; }
    const generation = ++traceGeneration.current;
    setTrace({ phase: 'loading', traceId });
    setAnnouncement(`Đang tải trace ${shortTrace(traceId)}.`);
    try {
      const found = await readTrace(traceId);
      if (!mounted.current || generation !== traceGeneration.current) return;
      setTrace({ phase: 'ready', trace: found });
      setAnnouncement(`Đã tải trace ${shortTrace(traceId)}.`);
    } catch (error) {
      if (generation !== traceGeneration.current || stopped(error)) return;
      const notFound = error instanceof ApiError && error.status === 404;
      setTrace(notFound ? { phase: 'not-found', traceId } : { phase: 'failed', traceId, error });
      setAnnouncement(notFound ? 'Không tìm thấy trace.' : 'Chưa tải được trace.');
    }
  }

  // First read on entry: the last 24 hours, every type and role.
  useEffect(() => {
    if (entryRead.current) return;
    entryRead.current = true;
    const now = new Date();
    void load({ type: null, role: null, from: new Date(now.getTime() - 24 * HOUR_MS), to: now }, draft, 'none');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // After a read the reviewer asked for, the focus goes to its result, never to the page body.
  useEffect(() => {
    if (list.phase === 'loading' || (list.phase === 'ready' && (list.refreshing || list.more === 'loading'))) return;
    const target = focusAfter.current;
    focusAfter.current = 'none';
    if (target === 'none') return;
    const row = typeof target === 'object' ? eventsTable.current?.querySelector<HTMLElement>(`[data-row="${target.row}"]`) : null;
    (row ?? summary.current)?.focus();
  }, [list]);

  // The trace panel takes the focus when its answer arrives, so a keyboard user lands on it.
  useEffect(() => {
    if (trace.phase !== 'idle' && trace.phase !== 'loading') traceHeading.current?.focus();
  }, [trace]);

  function apply(event: FormEvent) {
    event.preventDefault();
    const result = toQuery(draft, new Date());
    if ('errors' in result) {
      setErrors(result.errors);
      const first = result.errors.from ? 'filter-from' : 'filter-to';
      window.setTimeout(() => document.getElementById(first)?.focus(), 0);
      return;
    }
    setErrors({});
    void load(result.query, draft, 'summary');
  }

  function lookUp(event: FormEvent) {
    event.preventDefault();
    const id = lookup.trim().toLowerCase();
    if (!TRACE_ID.test(id)) {
      setLookupError('Mã trace gồm 36 ký tự dạng 7c1e2f00-…-a904. Hãy kiểm tra và dán lại.');
      window.setTimeout(() => document.getElementById('trace-lookup')?.focus(), 0);
      return;
    }
    setLookupError(undefined);
    void openTrace(id);
  }

  const description = 'Chỉ hiện mã chính sách, tên trường, tên công cụ và mã tham chiếu. Không có nội dung tin nhắn, giá hay dữ liệu cá nhân.';
  if (forbidden) {
    return (
      <>
        <PageHeader title="Sự kiện bảo mật" description={description} />
        <PageState kind="forbidden" />
      </>
    );
  }

  const set = <K extends keyof Draft>(key: K) => (value: Draft[K]) => setDraft((d) => ({ ...d, [key]: value }));
  const busy = list.phase === 'loading' || (list.phase === 'ready' && list.refreshing);
  const changed = applied !== null && !sameDraft(draft, applied.draft);
  const shownTrace = trace.phase === 'ready' ? trace.trace.traceId : null;

  return (
    <>
      <PageHeader title="Sự kiện bảo mật" description={description}
        meta={<>
          <Badge tone="neutral" size="sm" icon="database">Dữ liệu tổng hợp (synthetic)</Badge>
          <Badge tone="neutral" size="sm" icon="history">Lần xem này được ghi nhật ký</Badge>
        </>}
        actions={
          // Not on the artboard: a way for keyboard users past the filter and the table (UX review, EP-10d).
          <Button variant="ghost" iconLeft="search" onClick={() => { traceSection.current?.scrollIntoView?.({ block: 'start' }); document.getElementById('trace-lookup')?.focus(); }}>
            Đến tra cứu trace
          </Button>
        } />

      <p role="status" aria-live="polite" style={hidden}>{announcement}</p>

      <form onSubmit={apply} noValidate aria-label="Lọc sự kiện"
        style={{ ...panel, display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start', flexWrap: 'wrap', padding: 'var(--space-4) var(--space-5)' }}>
        <Select id="filter-type" label="Loại sự kiện" options={TYPE_OPTIONS} value={draft.type}
          onChange={(e) => set('type')(e.target.value as Draft['type'])} />
        <Select id="filter-role" label="Vai trò" options={ROLE_OPTIONS} value={draft.role}
          onChange={(e) => set('role')(e.target.value as Draft['role'])} />
        <Select id="filter-range" label="Khoảng thời gian" options={RANGE_OPTIONS} value={draft.range}
          onChange={(e) => { set('range')(e.target.value as Range); setErrors({}); }} />
        {draft.range === 'custom' && (
          <>
            <Input id="filter-from" type="datetime-local" label="Từ" required value={draft.from} error={errors.from}
              onChange={(e) => set('from')(e.target.value)} />
            <Input id="filter-to" type="datetime-local" label="Đến" required value={draft.to} error={errors.to}
              helper={`Giờ theo máy của bạn (${utcOffset(new Date())}), tối đa 7 ngày.`} onChange={(e) => set('to')(e.target.value)} />
          </>
        )}
        <AlignedWithFields>
          <Button type="submit" iconLeft="list-filter" loading={busy}>Áp dụng</Button>
          {changed && <span style={{ font: 'var(--type-helper)', color: 'var(--status-warning-fg)' }}>Bộ lọc đã đổi, chưa áp dụng.</span>}
        </AlignedWithFields>
      </form>

      {applied && (
        <p ref={summary} tabIndex={-1} style={{ margin: 0, font: 'var(--type-caption)', color: 'var(--text-secondary)', outline: 'none' }}>
          {list.phase === 'failed' ? 'Chưa tải được: ' : busy ? 'Đang tải: ' : 'Đang hiện: '}{describe(applied.query)}
        </p>
      )}

      {list.phase === 'loading' && <PageState kind="loading" loadingLabel="Đang tải sự kiện bảo mật" />}
      {list.phase === 'failed' && (invalidFilter(list.error)
        ? (
          <Alert tone="warning" title="Bộ lọc không hợp lệ.">
            Hệ thống không nhận khoảng thời gian hoặc bộ lọc này. Hãy chọn khoảng tối đa 7 ngày, kiểm tra đồng hồ của máy, rồi bấm "Áp dụng".
            {referenceText(list.error) && <span style={{ display: 'block' }}>{referenceText(list.error)}</span>}
          </Alert>
        )
        : (
          <PageState kind={list.error instanceof ApiError && list.error.kind === 'timeout' ? 'timeout' : 'error'} error={list.error}
            title={list.error instanceof ApiError && list.error.kind === 'timeout' ? undefined : 'Chưa tải được sự kiện bảo mật'}
            action={applied && <Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={() => void load(applied.query, applied.draft, 'summary')}>Thử lại</Button>} />
        ))}
      {list.phase === 'ready' && list.events.length === 0 && (
        <PageState kind="empty" icon="shield-check" title="Không có sự kiện trong khoảng này" description="Hãy đổi bộ lọc hoặc chọn khoảng thời gian khác." />
      )}
      {list.phase === 'ready' && list.events.length > 0 && (
        <>
          {/* F4 artboard: the table and its footer ("Tải thêm") share one card. */}
          <div ref={eventsTable} aria-busy={list.refreshing || undefined} style={{ ...panel, opacity: list.refreshing ? 0.6 : 1 }}>
            <EventTable events={list.events} caption="Sự kiện kiểm toán, mới nhất ở trên" onTrace={(id) => void openTrace(id)} shownTrace={null} rowsFocusable bare />
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 'var(--space-3)', flexWrap: 'wrap', padding: 'var(--space-3) var(--space-4)' }}>
              <span style={{ font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>
                {list.events.length} sự kiện · {PAGE_SIZE} sự kiện mỗi trang · sự kiện lặp trong 1 phút được gộp
                {list.next === null ? ' · đã hết sự kiện trong khoảng này' : ''}
              </span>
              {list.next !== null && (
                <Button variant="secondary" size="sm" iconLeft="chevron-down" loading={list.more === 'loading'} disabled={list.refreshing} onClick={() => void loadMore()}>Tải thêm</Button>
              )}
            </div>
          </div>
          {typeof list.more === 'object' && (
            <Alert tone={invalidFilter(list.more.error) ? 'warning' : 'error'} title="Chưa tải thêm được sự kiện.">
              {invalidFilter(list.more.error) ? 'Hãy bấm "Áp dụng" để đọc lại từ đầu.' : failureText(list.more.error)}
              {referenceText(list.more.error) && <span style={{ display: 'block' }}>{referenceText(list.more.error)}</span>}
            </Alert>
          )}
        </>
      )}

      <section ref={traceSection} aria-labelledby="trace-title" style={{ ...panel, display: 'flex', flexDirection: 'column', gap: 'var(--space-4)', padding: 'var(--space-5)' }}>
        <div style={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: 'center', gap: 'var(--space-2)' }}>
          <h2 id="trace-title" ref={traceHeading} tabIndex={-1} style={{ margin: 0, font: 'var(--type-section-title)', outline: 'none' }}>
            {trace.phase === 'ready' ? <>Trace <span className="ap-code">{shortTrace(trace.trace.traceId)}</span></> : 'Tra cứu trace'}
          </h2>
          {trace.phase === 'ready' && <VerdictBadge trace={trace.trace} />}
        </div>
        <form onSubmit={lookUp} noValidate aria-label="Tra cứu trace" style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start', flexWrap: 'wrap' }}>
          <Input id="trace-lookup" label="Mã trace" mono value={lookup} error={lookupError} maxLength={64} autoComplete="off" spellCheck={false}
            helper="Bấm mã trace trong bảng, hoặc dán mã đầy đủ (36 ký tự)." onChange={(e) => setLookup(e.target.value)} />
          <AlignedWithFields>
            <Button type="submit" variant="secondary" iconLeft="search" loading={trace.phase === 'loading'}>Xem trace</Button>
          </AlignedWithFields>
        </form>
        <TracePanel state={trace} onTrace={(id) => void openTrace(id)} onRetry={(id) => void openTrace(id, true)} shownTrace={shownTrace} />
      </section>
    </>
  );
}

/** A column with an empty label line, so a button lines up with the inputs of `Field` (no magic offset). */
function AlignedWithFields({ children }: { children: ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
      <span aria-hidden="true" style={{ font: 'var(--type-label)', visibility: 'hidden' }}>&nbsp;</span>
      {children}
    </div>
  );
}

/** A horizontally scrollable table that keyboard users can reach and scroll (UX review M7). */
function Scroll({ label, bare = false, children }: { label: string; bare?: boolean; children: ReactNode }) {
  return (
    <div role="region" aria-label={label} tabIndex={0} style={{ ...(bare ? {} : panel), overflowX: 'auto' }}>{children}</div>
  );
}

function TraceButton({ traceId, last, onTrace, shownTrace }: { traceId: string; last?: boolean; onTrace: (id: string) => void; shownTrace: string | null }) {
  const text = `${last ? 'trace lần cuối ' : ''}${shortTrace(traceId)}`;
  // The trace already open is plain text: clicking it would be one more audited read (UX review L12).
  if (traceId === shownTrace) return <span className="ap-code" title={traceId}>{text}</span>;
  return (
    <button type="button" className="ap-code" onClick={() => onTrace(traceId)} title={traceId} aria-label={`Xem trace ${last ? 'lần cuối ' : ''}${shortTrace(traceId)}`}
      style={{ border: 0, padding: 0, background: 'transparent', color: 'var(--text-link)', textDecoration: 'underline', cursor: 'pointer', font: 'inherit' }}>
      {text}
    </button>
  );
}

function EventTable({ events, caption, onTrace, shownTrace, rowsFocusable = false, bare = false }: {
  events: SecurityEvent[]; caption: string; onTrace: (id: string) => void; shownTrace: string | null; rowsFocusable?: boolean; bare?: boolean;
}) {
  return (
    <Scroll label={caption} bare={bare}>
      <table style={{ width: '100%', minWidth: 900, borderCollapse: 'collapse', font: 'var(--type-table)' }}>
        <caption style={hidden}>{caption}</caption>
        <thead>
          <tr style={headRow}>
            {['Thời điểm', 'Hành động', 'Tài nguyên', 'Kết quả', 'Vai trò · Người', 'Mã trace'].map((h) => (
              <th key={h} scope="col" style={head}>{h}</th>
            ))}
            <th scope="col" style={{ ...head, textAlign: 'right' }}>Số lần</th>
          </tr>
        </thead>
        <tbody>
          {events.map((event, index) => {
            const who = actorParts(event);
            return (
              <tr key={index} data-row={index} tabIndex={rowsFocusable ? -1 : undefined} style={{ outline: 'none' }}>
                <td style={{ ...cell, whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }} title={event.occurredAt.toISOString()}>{formatSeconds(event.occurredAt)}</td>
                <td className="ap-code" style={cell}>{event.action}</td>
                <td className="ap-code" style={cell}>{event.resource}</td>
                <td style={cell}><Badge tone={outcomeTone(event.outcome)} size="sm"><span className="ap-code">{event.outcome}</span></Badge></td>
                <td style={cell}>{who.role} · {who.pseudonym === null ? <None /> : <span className="ap-code" style={{ overflowWrap: 'anywhere' }}>{who.pseudonym}</span>}</td>
                <td style={{ ...cell, whiteSpace: 'nowrap' }}>{event.traceId === null ? <None /> : <TraceButton traceId={event.traceId} onTrace={onTrace} shownTrace={shownTrace} />}</td>
                <td style={{ ...cell, textAlign: 'right', fontVariantNumeric: 'tabular-nums' }}>
                  {event.occurrences}
                  {event.occurrences > 1 && event.lastOccurredAt && (
                    <span style={{ display: 'block', font: 'var(--type-caption)', color: 'var(--text-secondary)', whiteSpace: 'nowrap' }}>
                      lần cuối {formatSeconds(event.lastOccurredAt)}
                    </span>
                  )}
                  {event.lastTraceId !== null && event.lastTraceId !== event.traceId && (
                    <span style={{ display: 'block', font: 'var(--type-caption)', whiteSpace: 'nowrap' }}>
                      <TraceButton traceId={event.lastTraceId} last onTrace={onTrace} shownTrace={shownTrace} />
                    </span>
                  )}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </Scroll>
  );
}

/** "—" with a text alternative (UX review L11). */
function None() {
  return <><span aria-hidden="true">—</span><span style={hidden}>không có</span></>;
}

function TracePanel({ state, onTrace, onRetry, shownTrace }: { state: TraceState; onTrace: (id: string) => void; onRetry: (id: string) => void; shownTrace: string | null }) {
  switch (state.phase) {
    case 'idle':
      return null;
    case 'loading':
      return <PageState kind="loading" loadingLabel="Đang tải trace" />;
    case 'not-found':
      return (
        <EmptyState compact variant="no-results" title="Không tìm thấy trace"
          description="Mã trace này không có bản ghi nào. Kiểm tra lại mã hoặc chọn từ danh sách sự kiện." />
      );
    case 'failed':
      return (
        <Alert tone="error" title="Chưa tải được trace"
          action={<Button variant="secondary" size="sm" iconLeft="refresh-cw" onClick={() => onRetry(state.traceId)}>Thử lại</Button>}>
          {failureText(state.error)}
          {referenceText(state.error) && <span style={{ display: 'block' }}>{referenceText(state.error)}</span>}
        </Alert>
      );
    case 'ready':
      return <TraceDetail trace={state.trace} onTrace={onTrace} shownTrace={shownTrace} />;
  }
}

/** The verdict as a badge beside the trace title (F4 artboard), worded by verdict(). */
function VerdictBadge({ trace }: { trace: Trace }) {
  const blocked = trace.egress.some((e) => e.decision === 'BLOCK');
  return (
    <Badge tone={blocked ? 'error' : trace.truncated ? 'warning' : 'success'} size="sm" icon={blocked ? 'shield-alert' : trace.truncated ? 'triangle-alert' : 'shield-check'}>
      {verdict(trace)}
    </Badge>
  );
}

/** A verdict only for what is on screen; a cut trace gets none (UX review M2: no false "no block"). */
function verdict(trace: Trace): string {
  if (trace.egress.some((e) => e.decision === 'BLOCK')) return 'Có dữ liệu bị chặn khi gửi tới AI hoặc khi AI trả về';
  if (trace.truncated) return 'Chưa kết luận được: trace bị cắt bớt';
  return 'Không có lần chặn dữ liệu nào trong trace';
}

/** The send attempt; a request blocked before any send has none (security review L-4). */
const attemptText = (e: EgressCheck) => (e.direction === 'REQUEST' && e.decision === 'BLOCK' ? <None /> : e.attempt);

function TraceDetail({ trace, onTrace, shownTrace }: { trace: Trace; onTrace: (id: string) => void; shownTrace: string | null }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' }}>
      {/* The verdict is the badge beside the title (VerdictBadge). */}
      <DescriptionList columns={1} items={[{ label: 'Mã trace đầy đủ', value: trace.traceId, mono: true }]} />
      {trace.truncated && (
        <Alert tone="info" title={`Chỉ hiện ${MAX_TRACE_ROWS} dòng đầu`}>
          Trace này có nhiều bản ghi hơn giới hạn hiển thị của mỗi mục.
        </Alert>
      )}

      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)' }}>
      <div style={{ ...nested, flex: '1 1 100%' }}>
      <h3 style={overline}>Kiểm tra dữ liệu gửi ra</h3>
      {trace.egress.length === 0 ? <p style={{ margin: 0, color: 'var(--text-secondary)' }}>Không có lần kiểm tra nào.</p> : (
        <Scroll label="Kiểm tra dữ liệu gửi ra của trace">
          <table style={{ width: '100%', borderCollapse: 'collapse', font: 'var(--type-table)' }}>
            <caption style={hidden}>Kiểm tra dữ liệu gửi ra của trace</caption>
            <thead>
              <tr style={headRow}>
                {['Thời điểm', 'Chiều', 'Quyết định', 'Lần gửi', 'Mã chính sách', 'Mã honeytoken', 'Trường bị chặn', 'Tên trường gửi', 'Số byte'].map((h) => (
                  <th key={h} scope="col" style={head}>{h}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {trace.egress.map((e, index) => (
                <tr key={index}>
                  <td style={{ ...cell, whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }} title={e.occurredAt.toISOString()}>{formatSeconds(e.occurredAt)}</td>
                  <td style={cell}>{e.direction === 'REQUEST' ? 'Gửi tới nhà cung cấp AI' : 'Câu trả lời của AI'}</td>
                  <td style={cell}><Badge tone={e.decision === 'BLOCK' ? 'error' : 'success'} shape="pill"><span className="ap-code">{e.decision}</span></Badge></td>
                  <td style={{ ...cell, fontVariantNumeric: 'tabular-nums' }}>{attemptText(e)}</td>
                  <td className="ap-code" style={cell}>{e.policyCode ?? <None />}</td>
                  <td className="ap-code" style={cell}>{e.honeytokenId ?? <None />}</td>
                  <td className="ap-code" style={cell}>{e.blockedFieldPath ?? <None />}</td>
                  <td className="ap-code" style={cell}>{e.fieldNames.length === 0 ? <None /> : e.fieldNames.join(', ')}</td>
                  <td style={{ ...cell, fontVariantNumeric: 'tabular-nums' }}>{e.byteLength.toLocaleString('vi-VN')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Scroll>
      )}

      </div>
      <div style={{ ...nested, flex: '1 1 100%' }}>
      <h3 style={overline}>Gọi công cụ</h3>
      {trace.tools.length === 0 ? <p style={{ margin: 0, color: 'var(--text-secondary)' }}>Không có lần gọi công cụ nào.</p> : (
        <Scroll label="Lần gọi công cụ của trace">
          <table style={{ width: '100%', borderCollapse: 'collapse', font: 'var(--type-table)' }}>
            <caption style={hidden}>Lần gọi công cụ của trace</caption>
            <thead>
              <tr style={headRow}>
                {['Thời điểm', 'Công cụ', 'Quyết định', 'Mã lý do'].map((h) => <th key={h} scope="col" style={head}>{h}</th>)}
              </tr>
            </thead>
            <tbody>
              {trace.tools.map((t, index) => (
                <tr key={index}>
                  <td style={{ ...cell, whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }} title={t.occurredAt.toISOString()}>{formatSeconds(t.occurredAt)}</td>
                  <td className="ap-code" style={cell}>{t.toolName}</td>
                  <td style={cell}>
                    <Badge tone={t.decision === 'REJECTED' || t.decision === 'FILTERED' ? 'restricted' : 'neutral'} shape="pill"><span className="ap-code">{t.decision}</span></Badge>
                  </td>
                  <td className="ap-code" style={cell}>{t.reasonCode ?? <None />}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Scroll>
      )}

      </div>
      </div>

      <h3 style={{ margin: 0, font: 'var(--type-card-title)' }}>Sự kiện kiểm toán của trace</h3>
      {trace.events.length === 0 ? <p style={{ margin: 0, color: 'var(--text-secondary)' }}>Không có sự kiện kiểm toán nào.</p>
        : <EventTable events={trace.events} caption="Sự kiện kiểm toán của trace" onTrace={onTrace} shownTrace={shownTrace} />}
    </div>
  );
}
