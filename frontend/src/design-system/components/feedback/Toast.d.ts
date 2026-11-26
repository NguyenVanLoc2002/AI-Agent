import * as React from 'react';

export interface ToastProps {
  tone?: 'success' | 'error' | 'warning' | 'info' | 'loading';
  title: React.ReactNode;
  description?: React.ReactNode;
  action?: React.ReactNode;
  onClose?: () => void;
  style?: React.CSSProperties;
}

export function Toast(props: ToastProps): JSX.Element;

export function ToastViewport(props: { children?: React.ReactNode; style?: React.CSSProperties }): JSX.Element;
