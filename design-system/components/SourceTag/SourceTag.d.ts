import * as React from 'react';

export interface SourceTagProps {
  /** erp = verified (teal) · ai = AI suggestion (violet) · cache = stale copy (amber) · manual = user-entered (neutral) */
  source?: 'erp' | 'ai' | 'cache' | 'manual' | 'lookup' | 'verified';
  /** e.g. "09:42" */
  time?: string;
  /** Override default label */
  label?: string;
  /** chip (default) or inline (icon + text, no background) for dense cells */
  variant?: 'chip' | 'inline';
  style?: React.CSSProperties;
}

export function SourceTag(props: SourceTagProps): JSX.Element;
