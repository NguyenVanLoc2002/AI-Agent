import React, { useId } from 'react';
import { Icon } from '../core/Icon.jsx';
import { IconButton } from '../core/IconButton.jsx';
import { useModalA11y } from '../core/a11y.jsx';

const TONE = { danger: ['triangle-alert', 'var(--status-error-bg)', 'var(--status-error)'], warning: ['shield-alert', 'var(--status-warning-bg)', 'var(--status-warning)'], info: ['info', 'var(--status-info-bg)', 'var(--status-info)'] };

export function Dialog({ open = true, title, description, tone, children, footer, onClose, width = 480, inline = false, style }) {
  const tid = useId(), did = useId();
  const ref = useModalA11y(open && !inline, onClose);
  if (!open) return null;
  const t = tone && TONE[tone];
  const panel = (
    <div ref={ref} tabIndex={-1} role={tone === 'danger' || tone === 'warning' ? 'alertdialog' : 'dialog'} aria-modal={!inline} aria-labelledby={tid} aria-describedby={description ? did : undefined}
      style={{ width, maxWidth: 'calc(100vw - 32px)', background: 'var(--bg-surface)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-xl)', border: 'var(--border-width-default) solid var(--border-subtle)', outline: 'none',
        animation: 'ap-fade-in var(--duration-slow) var(--ease-standard)', display: 'flex', flexDirection: 'column', maxHeight: 'calc(100vh - 64px)', ...style }}>
      <div style={{ display: 'flex', gap: 14, padding: '20px 20px 0' }}>
        {t && <span aria-hidden="true" style={{ width: 36, height: 36, borderRadius: '50%', background: t[1], display: 'inline-flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}><Icon name={t[0]} size={20} color={t[2]} /></span>}
        <div style={{ flex: 1, minWidth: 0 }}>
          <h2 id={tid} style={{ margin: 0, font: '600 17px/1.4 var(--font-sans)' }}>{title}</h2>
          {description && <p id={did} style={{ margin: '4px 0 0', font: 'var(--type-body)', color: 'var(--text-secondary)' }}>{description}</p>}
        </div>
        {onClose && <IconButton icon="x" label="Đóng" size="sm" onClick={onClose} style={{ marginTop: -4, marginRight: -6 }} />}
      </div>
      {children && <div style={{ padding: '16px 20px 0', overflowY: 'auto' }}>{children}</div>}
      {footer && <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8, padding: 20 }}>{footer}</div>}
      {!footer && <div style={{ height: 20 }} />}
    </div>
  );
  if (inline) return panel;
  return (
    <div onClick={(e) => { if (e.target === e.currentTarget && onClose) onClose(); }}
      style={{ position: 'fixed', inset: 0, background: 'var(--overlay-scrim)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 'var(--z-modal)', padding: 16 }}>
      {panel}
    </div>
  );
}
