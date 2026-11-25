import React from 'react';
import { Icon } from '../core/Icon.jsx';
import { Breadcrumb } from './Breadcrumb.jsx';

export function AppHeader({ breadcrumb = [], onSearch, searchPlaceholder = 'Tìm VIN, mã OE, SKU…', actions, user, style }) {
  return (
    <header style={{ height: 'var(--header-h)', display: 'flex', alignItems: 'center', gap: 16, padding: '0 24px', background: 'var(--bg-surface)', borderBottom: '1px solid var(--border-default)', flexShrink: 0, ...style }}>
      <div style={{ flex: 1, minWidth: 0 }}><Breadcrumb items={breadcrumb} /></div>
      {onSearch !== undefined && (
        <button type="button" onClick={onSearch} style={{ display: 'flex', alignItems: 'center', gap: 8, width: 300, maxWidth: '32vw', height: 34, padding: '0 10px', borderRadius: 'var(--radius-md)',
          border: '1px solid var(--border-default)', background: 'var(--bg-subtle)', color: 'var(--text-secondary)', font: '400 13px/1.4 var(--font-sans)', cursor: 'pointer' }}>
          <Icon name="search" size={16} />
          <span style={{ flex: 1, textAlign: 'left', overflow: 'hidden', whiteSpace: 'nowrap', textOverflow: 'ellipsis' }}>{searchPlaceholder}</span>
          <kbd style={{ font: '500 12px/1.4 var(--font-sans)', padding: '3px 5px', borderRadius: 4, border: '1px solid var(--border-default)', background: 'var(--bg-surface)', color: 'var(--text-secondary)' }}>Ctrl K</kbd>
        </button>
      )}
      {actions && <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>{actions}</div>}
      {user && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 10, paddingLeft: 12, borderLeft: '1px solid var(--border-default)' }}>
          <span style={{ width: 30, height: 30, borderRadius: '50%', background: 'var(--blue-100)', color: 'var(--blue-800)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', font: '600 12px/1.4 var(--font-sans)' }}>{user.initials}</span>
          <div style={{ display: 'flex', flexDirection: 'column', lineHeight: 1.3 }}>
            <span style={{ font: '500 13px/1.3 var(--font-sans)' }}>{user.name}</span>
            {user.role && <span style={{ font: '400 12px/1.3 var(--font-sans)', color: 'var(--text-secondary)' }}>{user.role}</span>}
          </div>
        </div>
      )}
    </header>
  );
}
