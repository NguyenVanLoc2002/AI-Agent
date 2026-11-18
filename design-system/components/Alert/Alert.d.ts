import * as React from 'react';

export interface AlertProps {
  tone?: 'info' | 'success' | 'warning' | 'error' | 'neutral';
  title?: React.ReactNode;
  children?: React.ReactNode;
  icon?: string;
  action?: React.ReactNode;
  onDismiss?: () => void;
  style?: React.CSSProperties;
}

export function Alert(props: AlertProps): JSX.Element;
