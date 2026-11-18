import * as React from 'react';

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
