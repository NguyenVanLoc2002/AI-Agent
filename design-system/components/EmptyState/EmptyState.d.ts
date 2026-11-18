import * as React from 'react';

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
