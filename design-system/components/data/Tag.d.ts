import * as React from 'react';

export interface TagProps {
  children?: React.ReactNode;
  /** Shows an × and calls back when clicked */
  onRemove?: () => void;
  selected?: boolean;
  onClick?: () => void;
  icon?: string;
  /** Accessible label prefix for the remove button. Default "Bỏ bộ lọc" */
  removeLabel?: string;
  style?: React.CSSProperties;
}

export function Tag(props: TagProps): JSX.Element;
