import * as React from 'react';

export interface AppHeaderUser { name: string; role?: string; initials: string; }
export interface AppHeaderProps {
  breadcrumb?: Array<{ label: string; onClick?: () => void }>;
  /** Provide to show the global search trigger */
  onSearch?: () => void;
  searchPlaceholder?: string;
  actions?: React.ReactNode;
  user?: AppHeaderUser;
  style?: React.CSSProperties;
}

export function AppHeader(props: AppHeaderProps): JSX.Element;
