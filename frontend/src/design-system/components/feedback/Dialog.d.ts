import * as React from 'react';

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
