import React from 'react';
import { Icon } from '../core/Icon.jsx';
import { IconButton } from '../core/IconButton.jsx';

export function AIPanel({ title = 'Trợ lý AI', context, status = 'online', children, composer, onClose, actions, width = 'var(--ai-panel-w)', style }) {
  const st = { online: ['var(--status-success)', 'Sẵn sàng'], degraded: ['var(--status-warning)', 'Phản hồi chậm'], offline: ['var(--status-error)', 'Không khả dụng'] }[status] || ['var(--status-success)', 'Sẵn sàng'];
  return (
    <aside aria-label={title} style={{ width, flexShrink: 0, height: '100%', display: 'flex', flexDirection: 'column', background: 'var(--bg-surface)', borderLeft: '1px solid var(--border-default)', ...style }}>
      <div style={{ height: 'var(--header-h)', display: 'flex', alignItems: 'center', gap: 10, padding: '0 12px 0 16px', borderBottom: '1px solid var(--border-default)', flexShrink: 0 }}>
        <span style={{ width: 28, height: 28, borderRadius: 8, background: 'var(--source-ai-bg)', border: '1px solid var(--source-ai-border)', color: 'var(--source-ai)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}><Icon name="sparkles" size={16} /></span>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ font: '600 14px/1.3 var(--font-sans)' }}>{title}</div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 5, font: '400 12px/1.3 var(--font-sans)', color: 'var(--text-secondary)' }}>
            <span style={{ width: 6, height: 6, borderRadius: '50%', background: st[0] }} />{st[1]}
          </div>
        </div>
        {actions}
        {onClose && <IconButton icon="x" label="Đóng trợ lý" size="sm" onClick={onClose} />}
      </div>
      {context && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, padding: '8px 16px', borderBottom: '1px solid var(--border-subtle)', background: 'var(--bg-subtle)', font: '400 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)' }}>
          <Icon name="eye" size={14} />Ngữ cảnh: <span style={{ color: 'var(--text-primary)', fontWeight: 500, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{context}</span>
        </div>
      )}
      <div style={{ flex: 1, overflowY: 'auto', padding: 16, display: 'flex', flexDirection: 'column', gap: 16 }}>{children}</div>
      {composer && <div style={{ padding: '12px 16px 14px', borderTop: '1px solid var(--border-subtle)' }}>{composer}</div>}
    </aside>
  );
}
