import * as React from 'react';

export interface SpinnerProps {
  size?: number;
  color?: string;
  /** Accessible label. Default "Đang tải". */
  label?: string;
  style?: React.CSSProperties;
}

export function Spinner(props: SpinnerProps): JSX.Element;
