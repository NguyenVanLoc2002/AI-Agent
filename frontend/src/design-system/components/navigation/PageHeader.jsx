import React from 'react';

export function PageHeader({ title, description, meta, actions, style }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16, flexWrap: 'wrap', ...style }}>
      <div style={{ flex: '1 1 320px', minWidth: 0 }}>
        <h1 style={{ margin: 0, font: 'var(--type-page-title)', color: 'var(--text-primary)', textWrap: 'pretty' }}>{title}</h1>
        {description && <p style={{ margin: '4px 0 0', font: '400 14px/1.55 var(--font-sans)', color: 'var(--text-secondary)', textWrap: 'pretty' }}>{description}</p>}
        {meta && <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap', marginTop: 10 }}>{meta}</div>}
      </div>
      {actions && <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexShrink: 0 }}>{actions}</div>}
    </div>
  );
}
