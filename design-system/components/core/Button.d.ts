import * as React from 'react';

/**
 * @startingPoint section="Core" subtitle="Buttons — variants, sizes, loading" viewport="700x260"
 */
export interface ButtonProps {
  variant?: 'primary' | 'secondary' | 'ghost' | 'subtle' | 'danger';
  size?: 'sm' | 'md' | 'lg';
  /** Lucide icon name shown before the label */
  iconLeft?: string;
  iconRight?: string;
  /** Shows spinner and disables the button — use to prevent duplicate submissions. */
  loading?: boolean;
  disabled?: boolean;
  /** Presentation-only permission lock: neutral style + lock icon + tooltip reason. Backend must still authorize. */
  restricted?: boolean;
  restrictedReason?: string;
  fullWidth?: boolean;
  type?: 'button' | 'submit' | 'reset';
  children?: React.ReactNode;
  onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
  style?: React.CSSProperties;
}

export function Button(props: ButtonProps): JSX.Element;
