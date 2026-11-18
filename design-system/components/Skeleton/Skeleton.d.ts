import * as React from 'react';

export interface SkeletonProps {
  width?: number | string;
  height?: number;
  radius?: number;
  /** Render a stack of N lines */
  lines?: number;
  gap?: number;
  style?: React.CSSProperties;
}

export function Skeleton(props: SkeletonProps): JSX.Element;
