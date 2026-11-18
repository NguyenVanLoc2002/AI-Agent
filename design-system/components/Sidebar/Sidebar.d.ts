import * as React from 'react';

/**
 * @startingPoint section="Navigation" subtitle="Collapsible sidebar" viewport="700x420"
 */
export interface SidebarItem { key: string; label: string; icon: string; badge?: string | number; badgeTone?: 'neutral' | 'ai'; locked?: boolean; disabled?: boolean; }
export interface SidebarSection { title?: string; items: SidebarItem[]; }
export interface SidebarProps {
  sections: SidebarSection[];
  activeKey?: string;
  onSelect?: (key: string) => void;
  collapsed?: boolean;
  onToggleCollapsed?: () => void;
  brand?: string;
  /** Environment chip next to the brand. Default "Thử nghiệm". Pass null to hide. */
  env?: string | null;
  footer?: React.ReactNode;
  style?: React.CSSProperties;
}

export function Sidebar(props: SidebarProps): JSX.Element;
