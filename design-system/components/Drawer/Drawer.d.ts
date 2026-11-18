import * as React from 'react';

export interface DrawerProps {
  open?: boolean;
  title: React.ReactNode;
  subtitle?: React.ReactNode;
  children?: React.ReactNode;
  footer?: React.ReactNode;
  onClose?: () => void;
  width?: number;
  inline?: boolean;
  style?: React.CSSProperties;
}

export function Drawer(props: DrawerProps): JSX.Element;
