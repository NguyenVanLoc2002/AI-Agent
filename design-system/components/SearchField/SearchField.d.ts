import * as React from 'react';

export interface SearchMode { value: string; label: string; /** Render input as code (tabular, tracked) */ code?: boolean; /** Exact required length, e.g. 17 for VIN */ length?: number; placeholder?: string; }

export interface SearchFieldProps {
  /** Defaults: VIN (17 ký tự), Mã OE, SKU, Tên sản phẩm */
  modes?: SearchMode[];
  mode?: string;
  onModeChange?: (mode: string) => void;
  value?: string;
  onChange?: (value: string) => void;
  onSubmit?: (query: string, mode: string) => void;
  /** Disables submit and shows spinner — prevents duplicate requests */
  loading?: boolean;
  /** External error (e.g. server-side); local length validation is built in */
  error?: string;
  minLength?: number;
  size?: 'md' | 'lg';
  submitLabel?: string;
  label?: string;
  style?: React.CSSProperties;
}

export function SearchField(props: SearchFieldProps): JSX.Element;
