import * as React from 'react';

export interface PaginationProps {
  page: number;
  pageSize?: number;
  total: number;
  onPageChange?: (page: number) => void;
  style?: React.CSSProperties;
}

export function Pagination(props: PaginationProps): JSX.Element;
