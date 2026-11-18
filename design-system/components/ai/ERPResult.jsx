import React from 'react';
import { Icon } from '../core/Icon.jsx';
import { Skeleton } from '../feedback/Skeleton.jsx';

const STATES = {
  verified: { icon: 'shield-check', label: (s) => 'Đã xác minh từ ' + s, bg: 'var(--source-erp-bg)', bd: 'var(--source-erp-border)', fg: 'var(--source-erp-fg)', ic: 'var(--source-erp)' },
  stale: { icon: 'clock', label: () => 'Bản lưu — chưa xác minh lại', bg: 'var(--status-warning-bg)', bd: 'var(--status-warning-border)', fg: 'var(--status-warning-fg)', ic: 'var(--status-warning)' },
  error: { icon: 'server-off', label: (s) => 'Không đọc được dữ liệu ' + s, bg: 'var(--status-error-bg)', bd: 'var(--status-error-border)', fg: 'var(--status-error-fg)', ic: 'var(--status-error)' },
  loading: { icon: 'loader-circle', label: (s) => 'Đang đọc dữ liệu ' + s + '…', bg: 'var(--bg-subtle)', bd: 'var(--border-default)', fg: 'var(--text-secondary)', ic: 'var(--text-secondary)' },
  lookup: { icon: 'database', label: (s) => 'Dữ liệu tra cứu · ' + s, bg: 'var(--status-info-bg)', bd: 'var(--status-info-border)', fg: 'var(--status-info-fg)', ic: 'var(--status-info-fg)' },
  restricted: { icon: 'lock', label: () => 'Không có quyền xem dữ liệu này', bg: 'var(--status-restricted-bg)', bd: 'var(--status-restricted-border)', fg: 'var(--status-restricted-fg)', ic: 'var(--status-restricted-fg)' },
};

export function ERPResult({ title, syncedAt, source = 'ERP', label, state, stale = false, children, footer, style }) {
  const st = STATES[state || (stale ? 'stale' : 'verified')] || STATES.verified;
  const k = state || (stale ? 'stale' : 'verified');
  return (
    <div aria-busy={k === 'loading' || undefined} style={{ border: `var(--border-width-default) solid ${st.bd}`, borderRadius: 'var(--radius-lg)', background: 'var(--bg-surface)', overflow: 'hidden', ...style }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '8px 12px', background: st.bg, borderBottom: `var(--border-width-default) solid ${st.bd}`, flexWrap: 'wrap' }}>
        <Icon name={st.icon} size={14} color={st.ic} style={{ animation: k === 'loading' ? 'ap-spin 0.9s linear infinite' : undefined }} />
        <span style={{ font: '600 12px/1.4 var(--font-sans)', color: st.fg }}>{label || st.label(source)}</span>
        {title && <span style={{ font: '500 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)' }}>· {title}</span>}
        {syncedAt && k !== 'loading' && (
          <span style={{ marginLeft: 'auto', display: 'inline-flex', alignItems: 'center', gap: 4, font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>
            <Icon name="clock" size={14} />{syncedAt}
          </span>
        )}
      </div>
      <div style={{ padding: 12 }}>{k === 'loading' ? <Skeleton lines={2} /> : children}</div>
      {footer && <div style={{ padding: '8px 12px', borderTop: 'var(--border-width-default) solid var(--border-subtle)', display: 'flex', gap: 8, justifyContent: 'flex-end' }}>{footer}</div>}
    </div>
  );
}
