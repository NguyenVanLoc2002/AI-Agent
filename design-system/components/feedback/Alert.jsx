import React from 'react';
import { Icon } from '../core/Icon.jsx';

const T = {
  info: ['info', 'var(--status-info-bg)', 'var(--status-info-border)', 'var(--status-info)'],
  success: ['circle-check', 'var(--status-success-bg)', 'var(--status-success-border)', 'var(--status-success)'],
  warning: ['triangle-alert', 'var(--status-warning-bg)', 'var(--status-warning-border)', 'var(--status-warning)'],
  error: ['circle-x', 'var(--status-error-bg)', 'var(--status-error-border)', 'var(--status-error)'],
  neutral: ['info', 'var(--bg-subtle)', 'var(--border-default)', 'var(--text-secondary)'],
};

export function Alert({ tone = 'info', title, children, icon, action, onDismiss, style }) {
  const [ic, bg, bd, ac] = T[tone] || T.info;
  return (
    <div role={tone === 'error' || tone === 'warning' ? 'alert' : 'status'}
      style={{ display: 'flex', alignItems: 'flex-start', gap: 10, padding: '12px 14px', borderRadius: 'var(--radius-md)', background: bg, border: `1px solid ${bd}`, ...style }}>
      <Icon name={icon || ic} size={20} color={ac} style={{ marginTop: 1 }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        {title && <div style={{ font: '600 14px/1.45 var(--font-sans)', color: 'var(--text-primary)' }}>{title}</div>}
        {children && <div style={{ font: '400 13px/1.55 var(--font-sans)', color: 'var(--text-body)', marginTop: title ? 2 : 0 }}>{children}</div>}
      </div>
      {action && <div style={{ display: 'flex', gap: 8, alignSelf: 'center' }}>{action}</div>}
      {onDismiss && (
        <button type="button" aria-label="Đóng" onClick={onDismiss} style={{ border: 0, background: 'transparent', padding: 2, cursor: 'pointer', color: 'var(--text-secondary)', display: 'inline-flex' }}>
          <Icon name="x" size={16} />
        </button>
      )}
    </div>
  );
}
