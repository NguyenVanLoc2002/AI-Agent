import * as React from 'react';

export interface ActionConfirmationProps {
  title: React.ReactNode;
  description?: React.ReactNode;
  items?: Array<{ label: React.ReactNode; value?: React.ReactNode }>;
  state?: 'pending' | 'confirmed' | 'rejected' | 'failed' | 'unauthorized';
  confirmLabel?: string;
  rejectLabel?: string;
  onConfirm?: () => void;
  onReject?: () => void;
  /** Pending submit — disables both buttons */
  loading?: boolean;
  note?: string;
  style?: React.CSSProperties;
}

export function ActionConfirmation(props: ActionConfirmationProps): JSX.Element;
