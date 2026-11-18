import * as React from 'react';

export interface IconButtonProps {
  /** Lucide icon name */
  icon: string;
  /** Required accessible label (also tooltip) */
  label: string;
  variant?: 'ghost' | 'secondary';
  size?: 'sm' | 'md' | 'lg';
  active?: boolean;
  disabled?: boolean;
  onClick?: (e: React.MouseEvent<HTMLButtonElement>) => void;
  style?: React.CSSProperties;
}

export function IconButton(props: IconButtonProps): JSX.Element;
