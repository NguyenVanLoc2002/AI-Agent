import * as React from 'react';

export interface AIComposerProps {
  value?: string;
  onChange?: (v: string) => void;
  onSubmit?: (v: string) => void;
  placeholder?: string;
  /** Attached context chips, e.g. ["SKU BP-1042"] */
  context?: string[];
  disabled?: boolean;
  busy?: boolean;
  suggestions?: string[];
  onSuggestion?: (s: string) => void;
  /** Dòng lưu ý dưới ô nhập; chuỗi rỗng để ẩn. */
  disclaimer?: string;
  /** APAS (A-68): accessible name of the text box. Default "Nội dung tin nhắn". */
  label?: string;
  /** APAS (A-68): maximum number of characters. */
  maxLength?: number;
  style?: React.CSSProperties;
}

export function AIComposer(props: AIComposerProps): JSX.Element;
