import * as React from 'react';

export interface ChatMessageProps {
  role?: 'assistant' | 'user' | 'system';
  author?: string;
  time?: string;
  /** assistant only: thinking shows a spinner, error renders red text */
  status?: 'thinking' | 'error';
  /** Nhãn cạnh tên trợ lý. Mặc định 'Gợi ý, cần kiểm tra'; truyền false để gắn nhãn nguồn theo từng phần nội dung. */
  label?: string | false;
  children?: React.ReactNode;
  style?: React.CSSProperties;
}

export function ChatMessage(props: ChatMessageProps): JSX.Element;
