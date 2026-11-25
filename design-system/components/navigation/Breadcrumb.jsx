import React from 'react';
import { Icon } from '../core/Icon.jsx';

export function Breadcrumb({ items = [], style }) {
  return (
    <ol aria-label="Đường dẫn" style={{ display: 'flex', alignItems: 'center', gap: 6, listStyle: 'none', margin: 0, padding: 0, font: '500 13px/1.4 var(--font-sans)', minWidth: 0, ...style }}>
      {items.map((it, i) => {
        const last = i === items.length - 1;
        return (
          <li key={i} style={{ display: 'flex', alignItems: 'center', gap: 6, minWidth: 0 }}>
            {it.onClick && !last ? (
              <a onClick={it.onClick} style={{ color: 'var(--text-secondary)', cursor: 'pointer', whiteSpace: 'nowrap' }}>{it.label}</a>
            ) : (
              <span aria-current={last ? 'page' : undefined} style={{ color: last ? 'var(--text-primary)' : 'var(--text-secondary)', fontWeight: last ? 600 : 500, whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{it.label}</span>
            )}
            {!last && <Icon name="chevron-right" size={16} color="var(--text-tertiary)" />}
          </li>
        );
      })}
    </ol>
  );
}
