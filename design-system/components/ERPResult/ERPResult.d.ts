import * as React from 'react';

export interface ERPResultProps {
  title?: string;
  /** e.g. "09:42 hôm nay" */
  syncedAt?: string;
  source?: string;
  /** verified (teal) · stale (cached, amber) · error · loading · restricted (no permission, neutral) */
  /** Nhãn tiêu đề thay cho nhãn mặc định của trạng thái. */
  label?: string;
  state?: 'verified' | 'stale' | 'error' | 'loading' | 'restricted' | 'lookup';
  /** @deprecated use state="stale" */
  stale?: boolean;
  children?: React.ReactNode;
  footer?: React.ReactNode;
  style?: React.CSSProperties;
}

export function ERPResult(props: ERPResultProps): JSX.Element;
