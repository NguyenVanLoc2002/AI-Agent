// Design-system components copied from the repo-root `design-system/` (EP-10a, decision 3A).
// Provenance and hashes: ./sources.json, checked by `npm run ds:verify`. The imports are extensionless
// so TypeScript reads the `.d.ts` next to each `.jsx`, and Vite bundles the `.jsx` itself.
export { Icon } from './components/core/Icon';
export { Spinner } from './components/core/Spinner';
export { Button } from './components/core/Button';
export { IconButton } from './components/core/IconButton';
export { Switch } from './components/forms/Switch';
export { Input } from './components/forms/Input';
export { Select } from './components/forms/Select';
export { Textarea } from './components/forms/Textarea';
export { EmptyState } from './components/feedback/EmptyState';
export { Alert } from './components/feedback/Alert';
export { Dialog } from './components/feedback/Dialog';
export { Badge } from './components/data/Badge';
export { ChatMessage } from './components/ai/ChatMessage';
export { AIComposer } from './components/ai/AIComposer';
export { Skeleton } from './components/feedback/Skeleton';
export { DescriptionList } from './components/data/DescriptionList';
export { Sidebar } from './components/navigation/Sidebar';
export { AppHeader } from './components/navigation/AppHeader';
// FE-UI-P1 (A-80): foundation for the app shell and the staff pages. `Checkbox` is copied only because DataTable
// imports it (FE-UI-D10); it is not exported until a page needs it on its own.
export { Field } from './components/forms/Field';
export { Breadcrumb } from './components/navigation/Breadcrumb';
export { PageHeader } from './components/navigation/PageHeader';
export { Tabs } from './components/navigation/Tabs';
export { Toast, ToastViewport } from './components/feedback/Toast';
export { Tooltip } from './components/feedback/Tooltip';
export { Card } from './components/data/Card';
export { Tag } from './components/data/Tag';
export { DataTable } from './components/data/DataTable';
export type { DataTableColumn, DataTableProps } from './components/data/DataTable';
export type { TabItem } from './components/navigation/Tabs';
