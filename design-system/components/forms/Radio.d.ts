import * as React from 'react';

export interface RadioOption { value: string; label: string; description?: string; disabled?: boolean; }
export interface RadioProps {
  name: string;
  options: Array<string | RadioOption>;
  value?: string;
  onChange?: (value: string) => void;
  direction?: 'row' | 'column';
  disabled?: boolean;
  'aria-label'?: string;
  style?: React.CSSProperties;
}

export function Radio(props: RadioProps): JSX.Element;
