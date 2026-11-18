import * as React from 'react';

export interface SelectOption { value: string; label: string; }
export interface SelectProps {
  label?: string;
  helper?: string;
  error?: string;
  success?: string;
  restricted?: boolean;
  restrictedReason?: string;
  /** Options loading — disables and shows spinner */
  loading?: boolean;
  required?: boolean;
  options?: Array<string | SelectOption>;
  placeholder?: string;
  size?: 'sm' | 'md' | 'lg';
  disabled?: boolean;
  value?: string;
  defaultValue?: string;
  onChange?: (e: React.ChangeEvent<HTMLSelectElement>) => void;
  id?: string;
  style?: React.CSSProperties;
}

export function Select(props: SelectProps): JSX.Element;
