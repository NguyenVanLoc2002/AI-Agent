import React, { useState } from 'react';
import { Icon } from '../core/Icon.jsx';
import { useFocusVisible } from '../core/a11y.jsx';

export function Tag({ children, onRemove, selected = false, onClick, icon, removeLabel = 'Bỏ bộ lọc', style }) {
  const [hover, setHover] = useState(false);
  const [focus, focusHandlers] = useFocusVisible();
  const clickable = !!onClick;
  const keyProps = clickable ? { role: 'button', tabIndex: 0, 'aria-pressed': selected, onKeyDown: (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(); } }, ...focusHandlers } : {};
  return (
    <span onClick={onClick} onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)} {...keyProps}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 28, padding: onRemove ? '0 4px 0 10px' : '0 10px', borderRadius: 'var(--radius-full)', outline: 'none',
        border: `var(--border-width-default) solid ${selected ? 'var(--blue-200)' : 'var(--border-default)'}`, background: selected ? 'var(--bg-selected)' : hover && clickable ? 'var(--bg-hover)' : 'var(--bg-surface)',
        boxShadow: focus ? 'var(--focus-ring)' : 'none', color: selected ? 'var(--action-primary-hover)' : 'var(--text-primary)', font: 'var(--type-button-sm)', cursor: clickable ? 'pointer' : 'default', whiteSpace: 'nowrap', ...style }}>
      {selected && clickable && !icon ? <Icon name="check" size={16} /> : icon ? <Icon name={icon} size={16} /> : null}
      {children}
      {onRemove && (
        <button type="button" aria-label={removeLabel + ': ' + (typeof children === 'string' ? children : '')} onClick={(e) => { e.stopPropagation(); onRemove(); }}
          style={{ display: 'inline-flex', border: 0, background: 'transparent', padding: 4, borderRadius: 999, cursor: 'pointer', color: 'var(--text-secondary)' }}>
          <Icon name="x" size={14} />
        </button>
      )}
    </span>
  );
}
