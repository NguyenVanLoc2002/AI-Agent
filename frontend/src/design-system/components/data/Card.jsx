import React from 'react';

const VARIANTS = {
  default: { background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-card)', boxShadow: 'var(--shadow-xs)' },
  elevated: { background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-card)', boxShadow: 'var(--shadow-card)' },
  nested: { background: 'var(--bg-nested)', border: 'var(--border-width-default) solid transparent', boxShadow: 'none' },
};

export function Card({ title, subtitle, actions, footer, variant = 'default', padding, children, style, bodyStyle }) {
  const v = VARIANTS[variant] || VARIANTS.default;
  const pad = padding ?? (variant === 'elevated' ? 24 : variant === 'nested' ? 16 : 20);
  const hasHeader = title || actions;
  return (
    <section style={{ ...v, borderRadius: 'var(--radius-lg)', minWidth: 0, display: 'flex', flexDirection: 'column', ...style }}>
      {hasHeader && (
        <header style={{ display: 'flex', alignItems: 'center', gap: 12, padding: `${pad === 0 ? 16 : Math.min(pad, 20)}px ${pad || 20}px ${children ? 0 : 16}px` }}>
          <div style={{ flex: 1, minWidth: 0 }}>
            {title && <h3 style={{ margin: 0, font: 'var(--type-card-title)', color: 'var(--text-primary)' }}>{title}</h3>}
            {subtitle && <div style={{ font: 'var(--type-helper)', color: 'var(--text-secondary)', marginTop: 2 }}>{subtitle}</div>}
          </div>
          {actions && <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>{actions}</div>}
        </header>
      )}
      {children && <div style={{ padding: pad, flex: 1, minWidth: 0, ...bodyStyle }}>{children}</div>}
      {footer && <footer style={{ padding: `12px ${pad || 20}px`, borderTop: 'var(--border-width-default) solid var(--border-subtle)', background: 'var(--bg-subtle)', borderRadius: '0 0 var(--radius-lg) var(--radius-lg)' }}>{footer}</footer>}
    </section>
  );
}
