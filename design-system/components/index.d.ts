import * as React from 'react';

/** AutoParts Agent components — window.AutoPartsAgentDesignSystem_aa8546 */

// ---- AIComposer ----
export interface AIComposerProps {
  value?: string;
  onChange?: (v: string) => void;
  onSubmit?: (v: string) => void;
  placeholder?: string;
  /** Attached context chips, e.g. ["SKU BP-1042"] */
  context?: string[];
  disabled?: boolean;
  busy?: boolean;
  suggestions?: string[];
  onSuggestion?: (s: string) => void;
  style?: React.CSSProperties;
}

export function AIComposer(props: AIComposerProps): JSX.Element;

// ---- AIPanel ----
/**
 * @startingPoint section="AI" subtitle="Contextual AI side panel" viewport="700x620"
 */
export interface AIPanelProps {
  title?: string;
  /** What page/record the assistant sees */
  context?: React.ReactNode;
  status?: 'online' | 'degraded' | 'offline';
  children?: React.ReactNode;
  composer?: React.ReactNode;
  onClose?: () => void;
  actions?: React.ReactNode;
  width?: number | string;
  style?: React.CSSProperties;
}

export function AIPanel(props: AIPanelProps): JSX.Element;

// ---- ActionConfirmation ----
export interface ActionConfirmationProps {
  title: React.ReactNode;
  description?: React.ReactNode;
  items?: Array<{ label: React.ReactNode; value?: React.ReactNode }>;
  state?: 'pending' | 'confirmed' | 'rejected' | 'failed' | 'unauthorized';
  confirmLabel?: string;
  rejectLabel?: string;
  onConfirm?: () => void;
  onReject?: () => void;
  /** Pending submit — disables both buttons */
  loading?: boolean;
  note?: string;
  style?: React.CSSProperties;
}

export function ActionConfirmation(props: ActionConfirmationProps): JSX.Element;

// ---- ChatMessage ----
export interface ChatMessageProps {
  role?: 'assistant' | 'user' | 'system';
  author?: string;
  time?: string;
  /** assistant only: thinking shows a spinner, error renders red text */
  status?: 'thinking' | 'error';
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

export function ChatMessage(props: ChatMessageProps): JSX.Element;

// ---- ERPResult ----
export interface ERPResultProps {
  title?: string;
  /** e.g. "09:42 hôm nay" */
  syncedAt?: string;
  source?: string;
  /** verified (teal) · stale (cached, amber) · error · loading · restricted (no permission, neutral) */
  state?: 'verified' | 'stale' | 'error' | 'loading' | 'restricted';
  /** @deprecated use state="stale" */
  stale?: boolean;
  children?: React.ReactNode;
  footer?: React.ReactNode;
  style?: React.CSSProperties;
}

export function ERPResult(props: ERPResultProps): JSX.Element;

// ---- SourceTag ----
export interface SourceTagProps {
  /** erp = verified (teal) · ai = AI suggestion (violet) · cache = stale copy (amber) · manual = user-entered (neutral) */
  source?: 'erp' | 'ai' | 'cache' | 'manual';
  /** e.g. "09:42" */
  time?: string;
  /** Override default label */
  label?: string;
  /** chip (default) or inline (icon + text, no background) for dense cells */
  variant?: 'chip' | 'inline';
  style?: React.CSSProperties;
}

export function SourceTag(props: SourceTagProps): JSX.Element;

// ---- ToolCall ----
export interface ToolCallProps {
  /** Technical tool id, e.g. "erp.get_price" */
  name?: string;
  /** Human Vietnamese label, e.g. "Kiểm tra giá ERP" */
  label: string;
  status?: 'running' | 'success' | 'error' | 'unauthorized' | 'timeout' | 'unavailable';
  duration?: string;
  /** Expandable raw params/response */
  detail?: string;
  /** Short explanation for failures */
  message?: string;
  style?: React.CSSProperties;
}

export function ToolCall(props: ToolCallProps): JSX.Element;

// ---- Button ----
/**
 * @startingPoint section="Core" subtitle="Buttons — variants, sizes, loading" viewport="700x260"
 */
export interface ButtonProps {
  variant?: 'primary' | 'secondary' | 'ghost' | 'subtle' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  /** Lucide icon name shown before the label */
  iconLeft?: string;
  iconRight?: string;
  /** Shows spinner and disables the button — use to prevent duplicate submissions. */
  loading?: boolean;
  disabled?: boolean;
  /** Presentation-only permission lock: neutral style + lock icon + tooltip reason. Backend must still authorize. */
  restricted?: boolean;
  restrictedReason?: string;
  fullWidth?: boolean;
  type?: 'button' | 'submit' | 'reset';
  children?: React.ReactNode;
  onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
  style?: React.CSSProperties;
}

export function Button(props: ButtonProps): JSX.Element;

// ---- Icon ----
export interface IconProps {
  /** Lucide icon name (kebab-case), e.g. "search", "shield-check". See assets/icons/. */
  name: string;
  /** 20 standard (default) · 16 dense tables & compact controls · 14 only inline with 12px text */
  size?: 20 | 16 | 14 | number;
  /** Default 2 (Lucide standard). Do not change except checkmarks inside 16px controls. */
  strokeWidth?: number;
  color?: string;
  title?: string;
  style?: React.CSSProperties;
}

export function Icon(props: IconProps): JSX.Element;

// ---- IconButton ----
export interface IconButtonProps {
  /** Lucide icon name */
  icon: string;
  /** Required accessible label (also tooltip) */
  label: string;
  variant?: 'ghost' | 'secondary';
  size?: 'sm' | 'md' | 'lg';
  active?: boolean;
  disabled?: boolean;
  onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
  style?: React.CSSProperties;
}

export function IconButton(props: IconButtonProps): JSX.Element;

// ---- Spinner ----
export interface SpinnerProps {
  size?: number;
  color?: string;
  /** Accessible label. Default "Đang tải". */
  label?: string;
  style?: React.CSSProperties;
}

export function Spinner(props: SpinnerProps): JSX.Element;

// ---- AvatarGroup ----
export interface AvatarPerson { name: string; /** Defaults to the last two words' initials (Nguyễn Minh Anh → MA) */ initials?: string; role?: string; }

export interface AvatarGroupProps {
  people: AvatarPerson[];
  /** Visible avatars before "+N" */
  max?: number;
  size?: 'sm' | 'md';
  /** Accessible group label, e.g. "Người duyệt". Default "Người phụ trách" */
  label?: string;
  style?: React.CSSProperties;
}

export function AvatarGroup(props: AvatarGroupProps): JSX.Element;

// ---- ChartCard ----
export interface ChartLegendItem { label: string; /** Use --chart-* tokens */ color: string; line?: boolean; dashed?: boolean; }

/**
 * @startingPoint section="Dashboard" subtitle="KPI chart card with period switcher, states and provenance" viewport="700x520"
 */
export interface ChartCardProps {
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  /** Headline figure shown above the chart */
  value?: React.ReactNode;
  /** Period switcher (pill tabs), e.g. [{value:'7d',label:'7 ngày'}] */
  periods?: Array<{ value: string; label: string }>;
  period?: string;
  onPeriodChange?: (v: string) => void;
  legend?: ChartLegendItem[];
  /** Provenance of the data — rendered as an inline SourceTag */
  source?: { source: 'erp' | 'ai' | 'cache' | 'manual'; time?: string; label?: string };
  state?: 'ready' | 'loading' | 'empty' | 'error' | 'restricted';
  /** EmptyState preset used when state="error" */
  errorVariant?: 'erp-unavailable' | 'timeout' | 'error' | 'offline';
  emptyTitle?: string;
  retryAction?: React.ReactNode;
  onRetry?: () => void;
  height?: number;
  actions?: React.ReactNode;
  /** Visually hidden text summary of the chart for screen readers (required for a11y) */
  summary?: string;
  /** The chart itself (any renderer); use --chart-* tokens */
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

export function ChartCard(props: ChartCardProps): JSX.Element;

// ---- Badge ----
export interface BadgeProps {
  tone?: 'neutral' | 'info' | 'success' | 'warning' | 'error' | 'restricted' | 'ai' | 'erp';
  /** rounded (6px) = category / provenance · pill = record status in tables & lists (shows a dot by default) */
  shape?: 'rounded' | 'pill';
  /** Lucide icon name; defaults per tone (success → circle-check …). Pass false to hide. */
  icon?: string | false;
  /** Small dot instead of icon */
  dot?: boolean;
  size?: 'sm' | 'md';
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

export function Badge(props: BadgeProps): JSX.Element;

// ---- Card ----
export interface CardProps {
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  /** Right-aligned header actions */
  actions?: React.ReactNode;
  footer?: React.ReactNode;
  /** default (border + shadow-xs) · elevated (dashboard KPI/chart: border + shadow-card, padding 24) · nested (inside another card: bg-nested, no border) */
  variant?: 'default' | 'elevated' | 'nested';
  /** Defaults: 20 default · 24 elevated · 16 nested */
  padding?: number;
  children?: React.ReactNode;
  style?: React.CSSProperties;
  bodyStyle?: React.CSSProperties;
}

export function Card(props: CardProps): JSX.Element;

// ---- DataTable ----
export interface DataTableColumn {
  key: string;
  header: React.ReactNode;
  render?: (row: any) => React.ReactNode;
  align?: 'left' | 'right' | 'center';
  width?: number | string;
  sortable?: boolean;
  sortValue?: (row: any) => any;
  muted?: boolean;
  wrap?: boolean;
}
export interface DataTableProps {
  columns: DataTableColumn[];
  rows: any[];
  rowKey?: string | ((row: any, index: number) => string | number);
  selectable?: boolean;
  selected?: Array<string | number>;
  onSelectedChange?: (keys: Array<string | number>) => void;
  /** Controlled sort; if onSortChange is given, rows are not sorted client-side */
  sort?: { key: string; dir: 'asc' | 'desc' } | null;
  onSortChange?: (sort: { key: string; dir: 'asc' | 'desc' }) => void;
  onRowClick?: (row: any) => void;
  activeRowKey?: string | number;
  loading?: boolean;
  emptyState?: React.ReactNode;
  density?: 'comfortable' | 'compact';
  stickyHeader?: boolean;
  /** Slot above the table — typically <TableToolbar> (search, filters, bulk actions) */
  toolbar?: React.ReactNode;
  /** Slot below the table — typically <Pagination> */
  footer?: React.ReactNode;
  /** Visually hidden caption for screen readers */
  caption?: string;
  style?: React.CSSProperties;
}

export function DataTable(props: DataTableProps): JSX.Element;

// ---- DescriptionList ----
export interface DescriptionListItem { label: string; value?: React.ReactNode; mono?: boolean; span?: number; }
export interface DescriptionListProps {
  items: DescriptionListItem[];
  columns?: number;
  style?: React.CSSProperties;
}

export function DescriptionList(props: DescriptionListProps): JSX.Element;

// ---- MetricCard ----
export interface MetricCardProps {
  label: string;
  value: React.ReactNode;
  unit?: string;
  /** e.g. "+12%" or "-3" */
  delta?: string | number;
  deltaDirection?: 'up' | 'down' | 'flat';
  deltaLabel?: string;
  icon?: string;
  /** Small data-source caption, e.g. "ERP · 09:42" */
  source?: string;
  loading?: boolean;
  style?: React.CSSProperties;
}

export function MetricCard(props: MetricCardProps): JSX.Element;

// ---- Pagination ----
export interface PaginationProps {
  page: number;
  pageSize?: number;
  total: number;
  onPageChange?: (page: number) => void;
  style?: React.CSSProperties;
}

export function Pagination(props: PaginationProps): JSX.Element;

// ---- TableToolbar ----
export interface TableToolbarFilter { key: string; /** e.g. "Hãng: Toyota" */ label: string; }

export interface TableToolbarProps {
  search?: { value?: string; onChange?: (v: string) => void; placeholder?: string };
  /** Applied filters, rendered as removable tags */
  filters?: TableToolbarFilter[];
  onRemoveFilter?: (key: string) => void;
  onClearFilters?: () => void;
  /** Shows a "Bộ lọc" button that opens your filter UI */
  onAddFilter?: () => void;
  /** Right-aligned actions (export, column settings) */
  actions?: React.ReactNode;
  /** When > 0 the toolbar switches to bulk-action mode */
  selectedCount?: number;
  bulkActions?: React.ReactNode;
  onClearSelection?: () => void;
  style?: React.CSSProperties;
}

export function TableToolbar(props: TableToolbarProps): JSX.Element;

// ---- Tag ----
export interface TagProps {
  children?: React.ReactNode;
  /** Shows an × and calls back when clicked */
  onRemove?: () => void;
  selected?: boolean;
  onClick?: () => void;
  icon?: string;
  /** Accessible label prefix for the remove button. Default "Bỏ bộ lọc" */
  removeLabel?: string;
  style?: React.CSSProperties;
}

export function Tag(props: TagProps): JSX.Element;

// ---- Alert ----
export interface AlertProps {
  tone?: 'info' | 'success' | 'warning' | 'error' | 'neutral';
  title?: React.ReactNode;
  children?: React.ReactNode;
  icon?: string;
  action?: React.ReactNode;
  onDismiss?: () => void;
  style?: React.CSSProperties;
}

export function Alert(props: AlertProps): JSX.Element;

// ---- Dialog ----
export interface DialogProps {
  open?: boolean;
  title: React.ReactNode;
  description?: React.ReactNode;
  /** Adds a leading tone icon — use danger/warning for sensitive confirmations */
  tone?: 'danger' | 'warning' | 'info';
  children?: React.ReactNode;
  footer?: React.ReactNode;
  onClose?: () => void;
  width?: number;
  /** Render without fixed overlay (for previews/docs) */
  inline?: boolean;
  style?: React.CSSProperties;
}

export function Dialog(props: DialogProps): JSX.Element;

// ---- Drawer ----
export interface DrawerProps {
  open?: boolean;
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  children?: React.ReactNode;
  footer?: React.ReactNode;
  onClose?: () => void;
  width?: number;
  inline?: boolean;
  style?: React.CSSProperties;
}

export function Drawer(props: DrawerProps): JSX.Element;

// ---- EmptyState ----
export interface EmptyStateProps {
  variant?: 'empty' | 'no-results' | 'unauthorized' | 'erp-unavailable' | 'ai-unavailable' | 'timeout' | 'error' | 'offline';
  icon?: string;
  title?: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  /** Request/trace reference shown in small mono text */
  code?: string;
  compact?: boolean;
  style?: React.CSSProperties;
}

export function EmptyState(props: EmptyStateProps): JSX.Element;

// ---- Skeleton ----
export interface SkeletonProps {
  width?: number | string;
  height?: number;
  radius?: number;
  /** Render a stack of N lines */
  lines?: number;
  gap?: number;
  style?: React.CSSProperties;
}

export function Skeleton(props: SkeletonProps): JSX.Element;

// ---- Toast ----
export interface ToastProps {
  tone?: 'success' | 'error' | 'warning' | 'info' | 'loading';
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  onClose?: () => void;
  style?: React.CSSProperties;
}

export function Toast(props: ToastProps): JSX.Element;

export function ToastViewport(props: { children?: React.ReactNode; style?: React.CSSProperties }): JSX.Element;

// ---- Tooltip ----
export interface TooltipProps {
  content: React.ReactNode;
  side?: 'top' | 'bottom' | 'left' | 'right';
  children: React.ReactNode;
  style?: React.CSSProperties;
}

export function Tooltip(props: TooltipProps): JSX.Element;

// ---- Checkbox ----
export interface CheckboxProps {
  checked?: boolean;
  indeterminate?: boolean;
  onChange?: (checked: boolean) => void;
  label?: string;
  description?: string;
  disabled?: boolean;
  /** Required when there is no visible label (e.g. table row select) */
  'aria-label'?: string;
  style?: React.CSSProperties;
}

export function Checkbox(props: CheckboxProps): JSX.Element;

// ---- Field ----
export interface FieldProps {
  label?: string;
  htmlFor?: string;
  required?: boolean;
  helper?: string;
  /** Error message — replaces helper and turns red */
  error?: string;
  /** Success / validated message (green check) */
  success?: string;
  /** Permission lock message (neutral + lock) */
  restricted?: boolean;
  restrictedReason?: string;
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

export function Field(props: FieldProps): JSX.Element;

// ---- Input ----
export interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label?: string;
  helper?: string;
  error?: string;
  success?: string;
  /** Read-only with lock icon + reason (presentation only) */
  restricted?: boolean;
  restrictedReason?: string;
  /** Trailing spinner, e.g. while validating against ERP */
  loading?: boolean;
  required?: boolean;
  size?: 'sm' | 'md' | 'lg';
  /** Lucide icon name at the start of the field */
  iconLeft?: string;
  /** Node rendered at the end (unit, kbd hint, clear button) */
  suffix?: React.ReactNode;
  /** Code styling for VIN/OE/SKU (Be Vietnam Pro, tabular-nums, +0.02em) */
  mono?: boolean;
  style?: React.CSSProperties;
  inputStyle?: React.CSSProperties;
}

export function Input(props: InputProps): JSX.Element;

// ---- Radio ----
export interface RadioOption { value: string; label: string; description?: string; disabled?: boolean; }
export interface RadioProps {
  name: string;
  options: Array<string | RadioOption>;
  value?: string;
  onChange?: (value: string) => void;
  direction?: 'row' | 'column';
  disabled?: boolean;
  'aria-label'?: string;
  style?: React.CSSProperties;
}

export function Radio(props: RadioProps): JSX.Element;

// ---- SearchField ----
export interface SearchMode { value: string; label: string; /** Render input as code (tabular, tracked) */ code?: boolean; /** Exact required length, e.g. 17 for VIN */ length?: number; placeholder?: string; }

export interface SearchFieldProps {
  /** Defaults: VIN (17 ký tự), Mã OE, SKU, Tên sản phẩm */
  modes?: SearchMode[];
  mode?: string;
  onModeChange?: (mode: string) => void;
  value?: string;
  onChange?: (value: string) => void;
  onSubmit?: (query: string, mode: string) => void;
  /** Disables submit and shows spinner — prevents duplicate requests */
  loading?: boolean;
  /** External error (e.g. server-side); local length validation is built in */
  error?: string;
  minLength?: number;
  size?: 'md' | 'lg';
  submitLabel?: string;
  label?: string;
  style?: React.CSSProperties;
}

export function SearchField(props: SearchFieldProps): JSX.Element;

// ---- Select ----
export interface SelectOption { value: string; label: string; }
export interface SelectProps {
  label?: string;
  helper?: string;
  error?: string;
  success?: string;
  restricted?: boolean;
  restrictedReason?: string;
  /** Options loading — disables and shows spinner */
  loading?: boolean;
  required?: boolean;
  options?: Array<string | SelectOption>;
  placeholder?: string;
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  value?: string;
  defaultValue?: string;
  onChange?: (e: React.ChangeEvent<HTMLSelectElement>) => void;
  id?: string;
  style?: React.CSSProperties;
}

export function Select(props: SelectProps): JSX.Element;

// ---- Switch ----
export interface SwitchProps {
  checked?: boolean;
  onChange?: (checked: boolean) => void;
  label?: string;
  description?: string;
  disabled?: boolean;
  size?: 'sm' | 'md';
  style?: React.CSSProperties;
}

export function Switch(props: SwitchProps): JSX.Element;

// ---- Textarea ----
export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  helper?: string;
  error?: string;
  success?: string;
  restricted?: boolean;
  restrictedReason?: string;
  required?: boolean;
  rows?: number;
}

export function Textarea(props: TextareaProps): JSX.Element;

// ---- AppHeader ----
export interface AppHeaderUser { name: string; role?: string; initials: string; }
export interface AppHeaderProps {
  breadcrumb?: Array<{ label: string; onClick?: () => void }>;
  /** Provide to show the global search trigger */
  onSearch?: () => void;
  searchPlaceholder?: string;
  actions?: React.ReactNode;
  user?: AppHeaderUser;
  style?: React.CSSProperties;
}

export function AppHeader(props: AppHeaderProps): JSX.Element;

// ---- Breadcrumb ----
export interface BreadcrumbProps {
  items: Array<{ label: string; onClick?: () => void }>;
  style?: React.CSSProperties;
}

export function Breadcrumb(props: BreadcrumbProps): JSX.Element;

// ---- PageHeader ----
export interface PageHeaderProps {
  title: React.ReactNode;
  description?: React.ReactNode;
  /** Badges / meta row under the title */
  meta?: React.ReactNode;
  actions?: React.ReactNode;
  style?: React.CSSProperties;
}

export function PageHeader(props: PageHeaderProps): JSX.Element;

// ---- Sidebar ----
/**
 * @startingPoint section="Navigation" subtitle="Collapsible sidebar" viewport="700x420"
 */
export interface SidebarItem { key: string; label: string; icon: string; badge?: string | number; badgeTone?: 'neutral' | 'ai'; locked?: boolean; disabled?: boolean; }
export interface SidebarSection { title?: string; items: SidebarItem[]; }
export interface SidebarProps {
  sections: SidebarSection[];
  activeKey?: string;
  onSelect?: (key: string) => void;
  collapsed?: boolean;
  onToggleCollapsed?: () => void;
  brand?: string;
  /** Environment chip next to the brand. Default "Thử nghiệm". Pass null to hide. */
  env?: string | null;
  footer?: React.ReactNode;
  style?: React.CSSProperties;
}

export function Sidebar(props: SidebarProps): JSX.Element;

// ---- Tabs ----
export interface TabItem { value: string; label: string; icon?: string; count?: number; disabled?: boolean; }
export interface TabsProps {
  tabs: TabItem[];
  value: string;
  onChange?: (value: string) => void;
  /** line = page sections · segmented = compact view toggle · pill = dashboard period / chart view switcher (dark active) */
  variant?: 'line' | 'segmented' | 'pill';
  'aria-label'?: string;
  style?: React.CSSProperties;
}

export function Tabs(props: TabsProps): JSX.Element;

export const ICON_NAMES: string[];
