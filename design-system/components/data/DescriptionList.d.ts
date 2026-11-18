import * as React from 'react';

export interface DescriptionListItem { label: string; value?: React.ReactNode; mono?: boolean; span?: number; }
export interface DescriptionListProps {
  items: DescriptionListItem[];
  columns?: number;
  style?: React.CSSProperties;
}

export function DescriptionList(props: DescriptionListProps): JSX.Element;
