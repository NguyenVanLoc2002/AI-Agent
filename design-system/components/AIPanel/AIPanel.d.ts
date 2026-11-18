import * as React from 'react';

/**
 * @startingPoint section="AI" subtitle="Contextual AI side panel" viewport="700x620"
 */
export interface AIPanelProps {
  title?: string;
  /** What page/record the assistant sees */
  context?: React.ReactNode;
  status?: 'online' | 'degraded' | 'offline';
  children?: React.ReactNode;
  composer?: React.ReactNode;
  onClose?: () => void;
  actions?: React.ReactNode;
  width?: number | string;
  style?: React.CSSProperties;
}

export function AIPanel(props: AIPanelProps): JSX.Element;
