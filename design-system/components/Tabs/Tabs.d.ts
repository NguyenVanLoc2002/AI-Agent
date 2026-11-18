import * as React from 'react';

export interface TabItem { value: string; label: string; icon?: string; count?: number; disabled?: boolean; }
export interface TabsProps {
  tabs: TabItem[];
  value: string;
  onChange?: (value: string) => void;
  /** line = page sections · segmented = compact view toggle · pill = dashboard period / chart view switcher (dark active) */
  variant?: 'line' | 'segmented' | 'pill';
  'aria-label'?: string;
  style?: React.CSSProperties;
}

export function Tabs(props: TabsProps): JSX.Element;
