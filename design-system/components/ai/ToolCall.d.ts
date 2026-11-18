import * as React from 'react';

export interface ToolCallProps {
  /** Technical tool id, e.g. "erp.get_price" */
  name?: string;
  /** Human Vietnamese label, e.g. "Kiểm tra giá ERP" */
  label: string;
  status?: 'running' | 'success' | 'error' | 'unauthorized' | 'timeout' | 'unavailable' | 'pending' | 'unknown';
  duration?: string;
  /** Expandable raw params/response */
  detail?: string;
  /** Short explanation for failures */
  message?: string;
  style?: React.CSSProperties;
}

export function ToolCall(props: ToolCallProps): JSX.Element;
