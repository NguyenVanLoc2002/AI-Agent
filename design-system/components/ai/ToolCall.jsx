import React, { useState } from 'react';
import { Icon } from '../core/Icon.jsx';

const S = {
  running: ['loader-circle', 'var(--text-secondary)', 'Đang chạy', 'var(--border-default)'],
  success: ['circle-check', 'var(--status-success-fg)', 'Hoàn tất', 'var(--border-default)'],
  error: ['circle-x', 'var(--status-error-fg)', 'Thất bại', 'var(--status-error-border)'],
  unauthorized: ['lock', 'var(--status-restricted-fg)', 'Không có quyền', 'var(--status-restricted-border)'],
  timeout: ['timer-off', 'var(--status-warning-fg)', 'Quá thời gian', 'var(--status-warning-border)'],
  unavailable: ['server-off', 'var(--status-warning-fg)', 'ERP không khả dụng', 'var(--status-warning-border)'],
  pending: ['circle', 'var(--text-tertiary)', 'Chưa gửi', 'var(--border-default)'],
  unknown: ['circle-help', 'var(--status-warning-fg)', 'Chưa rõ kết quả', 'var(--status-warning-border)'],
};

export function ToolCall({ name, label, status = 'success', duration, detail, message, style }) {
  const [open, setOpen] = useState(false);
  const [ic, c, txt, bd] = S[status] || S.success;
  return (
    <div style={{ border: `var(--border-width-default) solid ${bd}`, borderRadius: 'var(--radius-md)', background: 'var(--bg-subtle)', overflow: 'hidden', ...style }}>
      <button type="button" onClick={() => detail && setOpen(!open)} aria-expanded={detail ? open : undefined}
        style={{ display: 'flex', alignItems: 'flex-start', gap: 8, width: '100%', padding: '7px 10px', border: 0, background: 'transparent', cursor: detail ? 'pointer' : 'default', textAlign: 'left' }}>
        <Icon name="cpu" size={14} color="var(--text-tertiary)" style={{ marginTop: 2 }} />
        <span style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column' }}>
          <span style={{ font: '500 12px/1.4 var(--font-sans)', color: 'var(--text-primary)' }}>{label}</span>
          {(name || duration) && (
            <span style={{ font: '400 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {name && <code style={{ font: 'inherit', letterSpacing: 'var(--tracking-code)' }}>{name}</code>}{name && duration ? ' · ' : ''}{duration && <span style={{ fontVariantNumeric: 'tabular-nums' }}>{duration}</span>}
            </span>
          )}
        </span>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4, font: '500 12px/1.4 var(--font-sans)', color: c, whiteSpace: 'nowrap', flexShrink: 0 }}>
          <Icon name={ic} size={14} style={{ animation: status === 'running' ? 'ap-spin 0.9s linear infinite' : undefined }} />{txt}
        </span>
        {detail && <Icon name={open ? 'chevron-up' : 'chevron-down'} size={14} color="var(--text-tertiary)" style={{ marginTop: 2 }} />}
      </button>
      {message && <div style={{ padding: '0 10px 8px 32px', marginTop: -2, font: '400 12px/1.5 var(--font-sans)', color: 'var(--text-body)' }}>{message}</div>}
      {open && detail && <pre style={{ margin: 0, padding: '8px 10px', borderTop: '1px solid var(--border-default)', background: 'var(--bg-surface)', font: '400 12px/1.5 var(--font-sans)', letterSpacing: 'var(--tracking-code)', color: 'var(--text-body)', whiteSpace: 'pre-wrap' }}>{detail}</pre>}
    </div>
  );
}
