import React, { useState } from 'react';
import { Icon } from '../core/Icon.jsx';
import { useFocusVisible } from '../core/a11y.jsx';

export function Checkbox({ checked = false, indeterminate = false, onChange, label, description, disabled = false, 'aria-label': ariaLabel, style }) {
  const [hover, setHover] = useState(false);
  const [focus, focusHandlers] = useFocusVisible();
  const on = checked || indeterminate;
  const border = on ? 'var(--action-primary)' : hover && !disabled ? 'var(--gray-500)' : 'var(--border-control)';
  return (
    <label onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{ display: 'inline-flex', alignItems: 'flex-start', gap: 8, cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.5 : 1, ...style }}>
      <span style={{ position: 'relative', width: 16, height: 16, marginTop: label ? 2 : 0, flexShrink: 0, borderRadius: 'var(--radius-xs)',
        border: `var(--border-width-strong) solid ${border}`, background: on ? 'var(--action-primary)' : 'var(--bg-surface)', boxShadow: focus ? 'var(--focus-ring)' : 'none',
        display: 'inline-flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-on-primary)', transition: 'background var(--duration-fast), border-color var(--duration-fast)' }}>
        <input type="checkbox" checked={checked} disabled={disabled} aria-label={ariaLabel} onChange={(e) => onChange && onChange(e.target.checked)} {...focusHandlers}
          aria-checked={indeterminate ? 'mixed' : checked} style={{ position: 'absolute', inset: 0, opacity: 0, margin: 0, cursor: 'inherit' }} />
        {indeterminate ? <Icon name="minus" size={12} strokeWidth={3} /> : checked ? <Icon name="check" size={12} strokeWidth={3} /> : null}
      </span>
      {(label || description) && (
        <span style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
          {label && <span style={{ font: '500 14px/1.4 var(--font-sans)', color: 'var(--text-primary)' }}>{label}</span>}
          {description && <span style={{ font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>{description}</span>}
        </span>
      )}
    </label>
  );
}
