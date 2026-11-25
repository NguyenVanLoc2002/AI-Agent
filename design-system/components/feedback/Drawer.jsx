import React, { useId } from 'react';
import { IconButton } from '../core/IconButton.jsx';
import { useModalA11y } from '../core/a11y.jsx';

export function Drawer({ open = true, title, subtitle, children, footer, onClose, width = 480, inline = false, style }) {
  const tid = useId();
  const ref = useModalA11y(open && !inline, onClose);
  if (!open) return null;
  const panel = (
    <aside ref={ref} tabIndex={-1} role="dialog" aria-modal={!inline} aria-labelledby={tid}
      style={{ width, maxWidth: '100%', height: '100%', background: 'var(--bg-surface)', borderLeft: 'var(--border-width-default) solid var(--border-default)', boxShadow: inline ? 'none' : 'var(--shadow-lg)', outline: 'none',
        display: 'flex', flexDirection: 'column', animation: 'ap-slide-in-right var(--duration-slow) var(--ease-standard)', ...style }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, padding: '16px 20px', borderBottom: 'var(--border-width-default) solid var(--border-default)' }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <h2 id={tid} style={{ margin: 0, font: '600 16px/1.4 var(--font-sans)' }}>{title}</h2>
          {subtitle && <div style={{ font: 'var(--type-helper)', color: 'var(--text-secondary)', marginTop: 2 }}>{subtitle}</div>}
        </div>
        {onClose && <IconButton icon="x" label="Đóng" size="sm" onClick={onClose} />}
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: 20 }}>{children}</div>
      {footer && <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, padding: '14px 20px', borderTop: 'var(--border-width-default) solid var(--border-default)', background: 'var(--bg-subtle)' }}>{footer}</div>}
    </aside>
  );
  if (inline) return panel;
  return (
    <div onClick={(e) => { if (e.target === e.currentTarget && onClose) onClose(); }} style={{ position: 'fixed', inset: 0, background: 'var(--overlay-scrim)', display: 'flex', justifyContent: 'flex-end', zIndex: 'var(--z-drawer)' }}>
      {panel}
    </div>
  );
}
