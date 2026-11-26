import React from 'react';
import { Icon } from '../core/Icon.jsx';

const T = { success: ['circle-check', 'var(--status-success)'], error: ['circle-x', 'var(--status-error)'], warning: ['triangle-alert', 'var(--status-warning)'], info: ['info', 'var(--status-info)'], loading: ['loader-circle', 'var(--text-secondary)'] };

export function Toast({ tone = 'success', title, description, action, onClose, style }) {
  const [ic, c] = T[tone] || T.info;
  return (
    <div role={tone === 'error' || tone === 'warning' ? 'alert' : 'status'} aria-live={tone === 'error' || tone === 'warning' ? 'assertive' : 'polite'} style={{ display: 'flex', alignItems: 'flex-start', gap: 10, width: 360, maxWidth: '100%', padding: '12px 14px', borderRadius: 'var(--radius-lg)',
      background: 'var(--bg-surface)', border: '1px solid var(--border-default)', boxShadow: 'var(--shadow-lg)', animation: 'ap-fade-in var(--duration-base) var(--ease-standard)', ...style }}>
      <Icon name={ic} size={20} color={c} style={{ marginTop: 1, animation: tone === 'loading' ? 'ap-spin 0.9s linear infinite' : undefined }} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ font: '600 14px/1.45 var(--font-sans)' }}>{title}</div>
        {description && <div style={{ font: '400 13px/1.5 var(--font-sans)', color: 'var(--text-secondary)', marginTop: 2 }}>{description}</div>}
        {action && <div style={{ marginTop: 8 }}>{action}</div>}
      </div>
      {onClose && (
        <button type="button" aria-label="Đóng thông báo" onClick={onClose} style={{ border: 0, background: 'transparent', padding: 2, cursor: 'pointer', color: 'var(--text-secondary)', display: 'inline-flex' }}>
          <Icon name="x" size={16} />
        </button>
      )}
    </div>
  );
}

export function ToastViewport({ children, style }) {
  return <div style={{ position: 'fixed', right: 24, bottom: 24, display: 'flex', flexDirection: 'column', gap: 8, zIndex: 'var(--z-toast)', ...style }}>{children}</div>;
}
