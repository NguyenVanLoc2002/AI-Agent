import * as React from 'react';

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
