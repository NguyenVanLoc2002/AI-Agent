import * as React from 'react';

export interface TextareaProps extends React.TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: string;
  helper?: string;
  error?: string;
  success?: string;
  restricted?: boolean;
  restrictedReason?: string;
  required?: boolean;
  rows?: number;
}

export function Textarea(props: TextareaProps): JSX.Element;
