import React from 'react';

export function Skeleton({ width = '100%', height = 12, radius = 4, lines, gap = 8, style }) {
  const bar = (w, k) => (
    <span key={k} aria-hidden="true" style={{ display: 'block', width: w, height, borderRadius: radius, background: 'linear-gradient(90deg,var(--gray-100) 0,var(--gray-150) 40%,var(--gray-100) 80%)',
      backgroundSize: '800px 100%', animation: 'ap-shimmer 1.4s linear infinite', ...style }} />
  );
  if (lines) return <span role="status" aria-label="Đang tải" style={{ display: 'flex', flexDirection: 'column', gap }}>{Array.from({ length: lines }).map((_, i) => bar(i === lines - 1 ? '60%' : width, i))}</span>;
  return bar(width);
}
