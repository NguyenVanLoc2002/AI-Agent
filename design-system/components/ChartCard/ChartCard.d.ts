import * as React from 'react';

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
