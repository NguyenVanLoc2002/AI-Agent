import React from 'react';
import { Icon } from './Icon.jsx';

export function Spinner({ size = 16, color = 'currentColor', label = 'Đang tải', style }) {
  return (
    <span role="status" aria-label={label} style={{ display: 'inline-flex', width: size, height: size, ...style }}>
      <Icon name="loader-circle" size={size} color={color} style={{ animation: 'ap-spin 0.8s linear infinite' }} />
    </span>
  );
}
