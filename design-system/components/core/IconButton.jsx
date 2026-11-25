import React, { useState } from 'react';
import { Icon } from './Icon.jsx';
import { useFocusVisible } from './a11y.jsx';

const DIM = { sm: [28, 16], md: [36, 20], lg: [40, 20] };

export function IconButton({ icon, label, variant = 'ghost', size = 'md', active = false, disabled = false, onClick, style, ...rest }) {
  const [hover, setHover] = useState(false);
  const [focus, focusHandlers] = useFocusVisible();
  const [d, i] = DIM[size] || DIM.md;
  const outlined = variant === 'secondary';
  const bg = active ? 'var(--bg-selected)' : hover && !disabled ? 'var(--bg-hover)' : outlined ? 'var(--bg-surface)' : 'transparent';
  return (
    <button type="button" aria-label={label} title={label} disabled={disabled} aria-pressed={active || undefined}
      onClick={disabled ? undefined : onClick} onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)} {...focusHandlers}
      style={{ width: d, height: d, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', borderRadius: 'var(--radius-md)',
        border: outlined ? 'var(--border-width-default) solid var(--border-default)' : 'var(--border-width-default) solid transparent', background: bg, outline: 'none',
        color: active ? 'var(--action-primary)' : 'var(--text-secondary)', cursor: disabled ? 'not-allowed' : 'pointer', opacity: disabled ? 0.45 : 1,
        boxShadow: focus ? 'var(--focus-ring)' : outlined ? 'var(--shadow-xs)' : 'none', padding: 0, transition: 'background var(--duration-fast)', ...style }} {...rest}>
      <Icon name={icon} size={i} />
    </button>
  );
}
