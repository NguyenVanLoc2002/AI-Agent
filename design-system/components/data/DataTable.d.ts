import * as React from 'react';

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
