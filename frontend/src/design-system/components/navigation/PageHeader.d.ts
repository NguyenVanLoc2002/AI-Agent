import * as React from 'react';

export interface PageHeaderProps {
  title: React.ReactNode;
  description?: React.ReactNode;
  /** Badges / meta row under the title */
  meta?: React.ReactNode;
  actions?: React.ReactNode;
  style?: React.CSSProperties;
}

export function PageHeader(props: PageHeaderProps): JSX.Element;
