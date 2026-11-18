import * as React from 'react';

export interface IconProps {
  /** Lucide icon name (kebab-case), e.g. "search", "shield-check". See assets/icons/. */
  name: string;
  /** 20 standard (default) · 16 dense tables & compact controls · 14 only inline with 12px text */
  size?: 20 | 16 | 14 | number;
  /** Default 2 (Lucide standard). Do not change except checkmarks inside 16px controls. */
  strokeWidth?: number;
  color?: string;
  title?: string;
  style?: React.CSSProperties;
}

export function Icon(props: IconProps): JSX.Element;
