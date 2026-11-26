import * as React from 'react';

export interface CardProps {
  title?: React.ReactNode;
  subtitle?: React.ReactNode;
  /** Right-aligned header actions */
  actions?: React.ReactNode;
  footer?: React.ReactNode;
  /** default (border + shadow-xs) · elevated (dashboard KPI/chart: border + shadow-card, padding 24) · nested (inside another card: bg-nested, no border) */
  variant?: 'default' | 'elevated' | 'nested';
  /** Defaults: 20 default · 24 elevated · 16 nested */
  padding?: number;
  children?: React.ReactNode;
  style?: React.CSSProperties;
  bodyStyle?: React.CSSProperties;
}

export function Card(props: CardProps): JSX.Element;
