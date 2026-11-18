import * as React from 'react';

export interface AvatarPerson { name: string; /** Defaults to the last two words' initials (Nguyễn Minh Anh → MA) */ initials?: string; role?: string; }

export interface AvatarGroupProps {
  people: AvatarPerson[];
  /** Visible avatars before "+N" */
  max?: number;
  size?: 'sm' | 'md';
  /** Accessible group label, e.g. "Người duyệt". Default "Người phụ trách" */
  label?: string;
  style?: React.CSSProperties;
}

export function AvatarGroup(props: AvatarGroupProps): JSX.Element;
