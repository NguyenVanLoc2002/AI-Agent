import * as React from 'react';

export interface FieldProps {
  label?: string;
  htmlFor?: string;
  required?: boolean;
  helper?: string;
  /** Error message — replaces helper and turns red */
  error?: string;
  /** Success / validated message (green check) */
  success?: string;
  /** Permission lock message (neutral + lock) */
  restricted?: boolean;
  restrictedReason?: string;
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

export function Field(props: FieldProps): JSX.Element;
