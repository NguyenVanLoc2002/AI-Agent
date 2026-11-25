import React, { useState, useId } from 'react';
import { useFocusVisible } from '../core/a11y.jsx';

export function Switch({ checked = false, onChange, label, description, disabled = false, size = 'md', style }) {
  const [hover, setHover] = useState(false);
  const [focus, focusHandlers] = useFocusVisible();
  const lid = useId();
  const w = size === 'sm' ? 28 : 36, h = size === 'sm' ? 16 : 20, k = h - 4;
  const track = checked ? (hover && !disabled ? 'var(--action-primary-hover)' : 'var(--action-primary)') : hover && !disabled ? 'var(--gray-500)' : 'var(--border-control)';
  return (
    <span onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{ display: 'inline-flex', alignItems: 'flex-start', gap: 10, opacity: disabled ? 0.5 : 1, ...style }}>
      <button type="button" role="switch" aria-checked={checked} aria-labelledby={label ? lid : undefined} disabled={disabled} onClick={() => onChange && onChange(!checked)} {...focusHandlers}
        style={{ position: 'relative', width: w, height: h, flexShrink: 0, borderRadius: 999, border: 0, padding: 0, marginTop: label ? 1 : 0, cursor: disabled ? 'not-allowed' : 'pointer', outline: 'none',
          background: track, boxShadow: focus ? 'var(--focus-ring)' : 'none', transition: 'background var(--duration-fast)' }}>
        <span style={{ position: 'absolute', top: 2, left: checked ? w - k - 2 : 2, width: k, height: k, borderRadius: '50%', background: 'var(--bg-surface)',
          boxShadow: '0 1px 2px rgba(16,24,40,.2)', transition: 'left var(--duration-fast) var(--ease-standard)' }} />
      </button>
      {(label || description) && (
        <span style={{ display: 'flex', flexDirection: 'column', gap: 2, cursor: disabled ? 'not-allowed' : 'pointer' }} onClick={() => !disabled && onChange && onChange(!checked)}>
          {label && <span id={lid} style={{ font: '500 14px/1.4 var(--font-sans)' }}>{label}</span>}
          {description && <span style={{ font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>{description}</span>}
        </span>
      )}
    </span>
  );
}
