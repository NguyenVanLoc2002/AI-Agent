import * as React from 'react';

export interface BadgeProps {
  tone?: 'neutral' | 'info' | 'success' | 'warning' | 'error' | 'restricted' | 'ai' | 'erp';
  /** rounded (6px) = category / provenance · pill = record status in tables & lists (shows a dot by default) */
  shape?: 'rounded' | 'pill';
  /** Lucide icon name; defaults per tone (success → circle-check …). Pass false to hide. */
  icon?: string | false;
  /** Small dot instead of icon */
  dot?: boolean;
  size?: 'sm' | 'md';
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

export function Badge(props: BadgeProps): JSX.Element;
