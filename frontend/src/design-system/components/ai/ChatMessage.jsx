import React from 'react';
import { Icon } from '../core/Icon.jsx';

export function ChatMessage({ role = 'assistant', author, time, children, status, label = 'Gợi ý, cần kiểm tra', style }) {
  if (role === 'user') {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-end', gap: 4, ...style }}>
        <div style={{ maxWidth: '85%', padding: '9px 12px', borderRadius: '12px 12px 4px 12px', background: 'var(--bg-muted)', font: '400 14px/1.55 var(--font-sans)', color: 'var(--text-primary)', whiteSpace: 'pre-wrap' }}>{children}</div>
        {time && <span style={{ font: '400 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)' }}>{time}</span>}
      </div>
    );
  }
  if (role === 'system') {
    return (
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, font: '400 12px/1.5 var(--font-sans)', color: 'var(--text-secondary)', ...style }}>
        <span style={{ flex: 1, height: 1, background: 'var(--border-subtle)' }} />{children}<span style={{ flex: 1, height: 1, background: 'var(--border-subtle)' }} />
      </div>
    );
  }
  const failed = status === 'error';
  return (
    <div style={{ display: 'flex', gap: 10, ...style }}>
      <span style={{ width: 26, height: 26, borderRadius: 8, flexShrink: 0, background: 'var(--source-ai-bg)', border: '1px solid var(--source-ai-border)', color: 'var(--source-ai)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
        <Icon name="sparkles" size={16} />
      </span>
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, font: '600 12px/1.4 var(--font-sans)', color: 'var(--source-ai-fg)' }}>
          {author || 'Trợ lý AI'}
          {label ? <span style={{ font: '500 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)' }}>· {label}</span> : null}
          {time && <span style={{ marginLeft: 'auto', font: '400 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)' }}>{time}</span>}
        </div>
        {status === 'thinking' ? (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, font: '400 13px/1.5 var(--font-sans)', color: 'var(--text-secondary)' }}>
            <Icon name="loader-circle" size={16} style={{ animation: 'ap-spin 0.9s linear infinite' }} />{children || 'Đang xử lý…'}
          </div>
        ) : (
          <div style={{ font: '400 14px/1.6 var(--font-sans)', color: failed ? 'var(--status-error-fg)' : 'var(--text-primary)', display: 'flex', flexDirection: 'column', gap: 8 }}>{children}</div>
        )}
      </div>
    </div>
  );
}
