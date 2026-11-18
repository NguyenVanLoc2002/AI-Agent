import * as React from 'react';

export interface BreadcrumbProps {
  items: Array<{ label: string; onClick?: () => void }>;
  style?: React.CSSProperties;
}

export function Breadcrumb(props: BreadcrumbProps): JSX.Element;
