import React, { useState } from 'react';
import { useFocusVisible } from '../core/a11y.jsx';

function RadioOption({ name, opt, on, dis, onChange }) {
  const [hover, setHover] = useState(false);
  const [focus, focusHandlers] = useFocusVisible();
  return (
    <label onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)}
      style={{ display: 'inline-flex', alignItems: 'flex-start', gap: 8, cursor: dis ? 'not-allowed' : 'pointer', opacity: dis ? 0.5 : 1 }}>
      <span style={{ position: 'relative', width: 16, height: 16, marginTop: 2, borderRadius: '50%', flexShrink: 0, boxShadow: focus ? 'var(--focus-ring)' : 'none',
        border: `var(--border-width-strong) solid ${on ? 'var(--action-primary)' : hover && !dis ? 'var(--gray-500)' : 'var(--border-control)'}`, background: 'var(--bg-surface)', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}>
        <input type="radio" name={name} value={opt.value} checked={on} disabled={dis} onChange={() => onChange && onChange(opt.value)} {...focusHandlers}
          style={{ position: 'absolute', inset: 0, opacity: 0, margin: 0, cursor: 'inherit' }} />
        {on && <span style={{ width: 8, height: 8, borderRadius: '50%', background: 'var(--action-primary)' }} />}
      </span>
      <span style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <span style={{ font: '500 14px/1.4 var(--font-sans)' }}>{opt.label}</span>
        {opt.description && <span style={{ font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>{opt.description}</span>}
      </span>
    </label>
  );
}

export function Radio({ name, options = [], value, onChange, direction = 'column', disabled = false, 'aria-label': ariaLabel, style }) {
  return (
    <div role="radiogroup" aria-label={ariaLabel} style={{ display: 'flex', flexDirection: direction, gap: direction === 'row' ? 20 : 10, ...style }}>
      {options.map((o) => {
        const opt = typeof o === 'string' ? { value: o, label: o } : o;
        return <RadioOption key={opt.value} name={name} opt={opt} on={value === opt.value} dis={disabled || opt.disabled} onChange={onChange} />;
      })}
    </div>
  );
}
