import * as React from 'react';

export interface InputProps extends Omit<React.InputHTMLAttributes<HTMLInputElement>, 'size'> {
  label?: string;
  helper?: string;
  error?: string;
  success?: string;
  /** Read-only with lock icon + reason (presentation only) */
  restricted?: boolean;
  restrictedReason?: string;
  /** Trailing spinner, e.g. while validating against ERP */
  loading?: boolean;
  required?: boolean;
  size?: 'sm' | 'md' | 'lg';
  /** Lucide icon name at the start of the field */
  iconLeft?: string;
  /** Node rendered at the end (unit, kbd hint, clear button) */
  suffix?: React.ReactNode;
  /** Code styling for VIN/OE/SKU (Be Vietnam Pro, tabular-nums, +0.02em) */
  mono?: boolean;
  style?: React.CSSProperties;
  inputStyle?: React.CSSProperties;
}

export function Input(props: InputProps): JSX.Element;
