import React, { useState } from 'react';
import { Icon } from '../core/Icon.jsx';
import { useFocusVisible } from '../core/a11y.jsx';

function NavItem({ item, active, collapsed, onSelect }) {
  const [hover, setHover] = useState(false);
  const [focus, focusHandlers] = useFocusVisible();
  return (
    <button type="button" {...focusHandlers} aria-label={collapsed ? item.label : undefined} onClick={() => onSelect && onSelect(item.key)} title={collapsed ? item.label : undefined} aria-current={active ? 'page' : undefined}
      disabled={item.disabled} onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{ display: 'flex', alignItems: 'center', gap: 10, width: '100%', height: 36, padding: collapsed ? 0 : '0 10px', justifyContent: collapsed ? 'center' : 'flex-start',
        border: 0, borderRadius: 'var(--radius-md)', cursor: item.disabled ? 'not-allowed' : 'pointer', opacity: item.disabled ? 0.5 : 1,
        background: active ? 'var(--bg-selected)' : hover ? 'var(--bg-hover)' : 'transparent', boxShadow: focus ? 'var(--focus-ring)' : 'none', outline: 'none',
        color: active ? 'var(--text-primary)' : 'var(--text-body)', font: `${active ? 600 : 500} 14px/1.4 var(--font-sans)`, textAlign: 'left', transition: 'background var(--duration-fast)' }}>
      <Icon name={item.icon} size={20} color={active ? 'var(--action-primary)' : 'currentColor'} />
      {!collapsed && <span style={{ flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.label}</span>}
      {!collapsed && item.badge != null && (
        <span style={{ minWidth: 20, height: 20, padding: '0 6px', borderRadius: 999, background: item.badgeTone === 'ai' ? 'var(--source-ai-bg)' : 'var(--bg-muted)',
          color: item.badgeTone === 'ai' ? 'var(--source-ai-fg)' : 'var(--text-secondary)', font: '600 12px/20px var(--font-sans)', textAlign: 'center' }}>{item.badge}</span>
      )}
      {!collapsed && item.locked && <Icon name="lock" size={16} color="var(--status-restricted-fg)" title="Không có quyền" />}
    </button>
  );
}

export function Sidebar({ sections = [], activeKey, onSelect, collapsed = false, onToggleCollapsed, brand = 'AutoParts Agent', env = 'Thử nghiệm', footer, style }) {
  return (
    <nav aria-label="Điều hướng chính" style={{ width: collapsed ? 'var(--sidebar-w-collapsed)' : 'var(--sidebar-w)', flexShrink: 0, height: '100%', display: 'flex', flexDirection: 'column',
      background: 'var(--bg-subtle)', borderRight: '1px solid var(--border-default)', transition: 'width var(--duration-slow) var(--ease-standard)', overflow: 'hidden', ...style }}>
      <div style={{ height: 'var(--header-h)', display: 'flex', alignItems: 'center', gap: 8, padding: collapsed ? 0 : '0 16px', justifyContent: collapsed ? 'center' : 'space-between', borderBottom: '1px solid var(--border-default)', flexShrink: 0 }}>
        {!collapsed && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 0 }}>
            <span style={{ font: '700 15px/1.3 var(--font-sans)', whiteSpace: 'nowrap' }}>{brand}</span>
            {env && <span style={{ font: 'var(--type-badge)', padding: '0 6px', borderRadius: 'var(--radius-sm)', background: 'var(--bg-muted)', color: 'var(--text-secondary)' }}>{env}</span>}
          </div>
        )}
        <button type="button" aria-label={collapsed ? 'Mở rộng thanh bên' : 'Thu gọn thanh bên'} onClick={onToggleCollapsed}
          style={{ width: 28, height: 28, border: 0, borderRadius: 6, background: 'transparent', color: 'var(--text-secondary)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center', cursor: 'pointer' }}>
          <Icon name="panel-left" size={16} />
        </button>
      </div>
      <div style={{ flex: 1, overflowY: 'auto', padding: collapsed ? '12px 10px' : 12, display: 'flex', flexDirection: 'column', gap: 16 }}>
        {sections.map((s, i) => (
          <div key={i} style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            {s.title && !collapsed && <div style={{ font: 'var(--type-overline)', letterSpacing: '.04em', textTransform: 'uppercase', color: 'var(--text-secondary)', padding: '0 10px 6px' }}>{s.title}</div>}
            {s.title && collapsed && i > 0 && <div style={{ height: 1, background: 'var(--border-default)', margin: '0 6px 8px' }} />}
            {s.items.map((it) => <NavItem key={it.key} item={it} active={it.key === activeKey} collapsed={collapsed} onSelect={onSelect} />)}
          </div>
        ))}
      </div>
      {footer && <div style={{ borderTop: '1px solid var(--border-default)', padding: 12 }}>{footer}</div>}
    </nav>
  );
}
