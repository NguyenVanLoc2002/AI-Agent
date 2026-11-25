import React from 'react';
import { Icon } from '../core/Icon.jsx';
import { Button } from '../core/Button.jsx';

const STATE = {
  pending: ['circle-pause', 'var(--status-warning-fg)', 'Chờ bạn xác nhận', 'var(--status-warning-border)'],
  confirmed: ['circle-check', 'var(--status-success-fg)', 'Đã xác nhận', 'var(--status-success-border)'],
  rejected: ['ban', 'var(--text-secondary)', 'Đã từ chối', 'var(--border-default)'],
  failed: ['circle-x', 'var(--status-error-fg)', 'Thực hiện thất bại', 'var(--status-error-border)'],
  unauthorized: ['lock', 'var(--status-restricted-fg)', 'Không đủ quyền thực hiện', 'var(--status-restricted-border)'],
};

export function ActionConfirmation({ title, description, items, state = 'pending', confirmLabel = 'Xác nhận', rejectLabel = 'Từ chối', onConfirm, onReject, loading = false, note = 'AI chỉ đề xuất. Thao tác chỉ được thực hiện sau khi bạn xác nhận.', style }) {
  const [ic, c, txt, bd] = STATE[state] || STATE.pending;
  return (
    <div style={{ border: `1px solid ${bd}`, borderRadius: 'var(--radius-lg)', background: 'var(--bg-surface)', boxShadow: 'var(--shadow-xs)', overflow: 'hidden', ...style }}>
      <div style={{ padding: '12px 14px', display: 'flex', flexDirection: 'column', gap: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, font: '600 12px/1.4 var(--font-sans)', letterSpacing: '.04em', textTransform: 'uppercase', color: c }}>
          <Icon name={ic} size={14} />{txt}
        </div>
        <div style={{ font: '600 14px/1.45 var(--font-sans)' }}>{title}</div>
        {description && <div style={{ font: '400 13px/1.55 var(--font-sans)', color: 'var(--text-secondary)' }}>{description}</div>}
        {items && items.length > 0 && (
          <ul style={{ margin: 0, padding: 0, listStyle: 'none', display: 'flex', flexDirection: 'column', border: '1px solid var(--border-subtle)', borderRadius: 'var(--radius-md)' }}>
            {items.map((it, i) => (
              <li key={i} style={{ display: 'flex', gap: 8, padding: '7px 10px', borderTop: i ? '1px solid var(--border-subtle)' : 0, font: '400 13px/1.45 var(--font-sans)' }}>
                <span style={{ flex: 1, minWidth: 0 }}>{it.label}</span>
                {it.value && <span style={{ fontWeight: 500, fontVariantNumeric: 'tabular-nums' }}>{it.value}</span>}
              </li>
            ))}
          </ul>
        )}
      </div>
      {state === 'pending' && (
        <div style={{ padding: '10px 14px', background: 'var(--bg-subtle)', borderTop: '1px solid var(--border-subtle)', display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
          <span style={{ flex: '1 1 160px', display: 'flex', gap: 6, font: '400 12px/1.45 var(--font-sans)', color: 'var(--text-secondary)' }}><Icon name="shield-check" size={14} style={{ marginTop: 1 }} />{note}</span>
          <Button size="sm" variant="secondary" onClick={onReject} disabled={loading}>{rejectLabel}</Button>
          <Button size="sm" onClick={onConfirm} loading={loading}>{confirmLabel}</Button>
        </div>
      )}
    </div>
  );
}
