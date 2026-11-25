import React from 'react';
import { CODE_STYLE } from '../core/a11y.jsx';

export function DescriptionList({ items = [], columns = 2, style }) {
  return (
    <dl style={{ display: 'grid', gridTemplateColumns: `repeat(${columns}, minmax(0,1fr))`, gap: '14px 24px', margin: 0, ...style }}>
      {items.map((it, i) => (
        <div key={i} style={{ display: 'flex', flexDirection: 'column', gap: 2, minWidth: 0, gridColumn: it.span ? `span ${it.span}` : undefined }}>
          <dt style={{ font: '400 12px/1.5 var(--font-sans)', color: 'var(--text-secondary)' }}>{it.label}</dt>
          <dd style={{ margin: 0, font: '500 14px/1.5 var(--font-sans)', ...(it.mono ? CODE_STYLE : {}), color: 'var(--text-primary)', fontVariantNumeric: 'tabular-nums', overflowWrap: 'anywhere' }}>{it.value ?? '—'}</dd>
        </div>
      ))}
    </dl>
  );
}
