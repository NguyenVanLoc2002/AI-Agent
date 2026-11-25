import React, { useState, useId } from 'react';
import { Icon } from '../core/Icon.jsx';
import { Spinner } from '../core/Spinner.jsx';
import { Field } from './Field.jsx';

const H = { sm: 'var(--control-h-sm)', md: 'var(--control-h-md)', lg: 'var(--control-h-lg)' };

export function Select({ label, helper, error, success, required, options = [], placeholder, size = 'md', disabled, restricted = false, restrictedReason, loading = false, value, defaultValue, onChange, id, style, ...rest }) {
  const auto = useId();
  const sid = id || auto;
  const [focus, setFocus] = useState(false);
  const readOnly = false;
  const borderColor = error ? 'var(--status-error)' : focus ? 'var(--border-focus)' : success ? 'var(--status-success)' : 'var(--border-input)';
  const ring = focus ? (error ? 'var(--focus-ring-error)' : 'var(--focus-ring)') : 'var(--shadow-xs)';
  const bg = disabled ? 'var(--bg-disabled)' : restricted || readOnly ? 'var(--bg-subtle)' : 'var(--bg-surface)';
  const trailing = loading ? <Spinner size={16} color="var(--text-secondary)" /> : restricted ? <Icon name="lock" size={16} color="var(--status-restricted-fg)" /> : success && !error ? <Icon name="circle-check" size={16} color="var(--status-success)" /> : null;
  const off = disabled || restricted || loading;
  return (
    <Field label={label} htmlFor={sid} required={required} helper={helper} error={error} success={success} restricted={restricted} restrictedReason={restrictedReason} style={style}>
      <div style={{ position: 'relative' }}>
        <select id={sid} disabled={off} value={value} defaultValue={value === undefined ? (defaultValue ?? (placeholder ? '' : undefined)) : undefined}
          onChange={onChange} onFocus={() => setFocus(true)} onBlur={() => setFocus(false)} aria-invalid={!!error || undefined} aria-busy={loading || undefined}
          aria-describedby={error || helper || success || restricted ? sid + '-msg' : undefined}
          style={{ appearance: 'none', WebkitAppearance: 'none', width: '100%', height: H[size] || H.md, padding: '0 56px 0 12px', borderRadius: 'var(--radius-md)',
            border: `var(--border-width-default) solid ${borderColor}`, boxShadow: ring, background: bg, font: '400 14px/1.4 var(--font-sans)', color: 'var(--text-primary)', outline: 'none', cursor: off ? 'not-allowed' : 'pointer' }} {...rest}>
          {placeholder && <option value="" disabled>{loading ? 'Đang tải…' : placeholder}</option>}
          {options.map((o) => { const opt = typeof o === 'string' ? { value: o, label: o } : o; return <option key={opt.value} value={opt.value}>{opt.label}</option>; })}
        </select>
        <span style={{ position: 'absolute', right: 12, top: '50%', transform: 'translateY(-50%)', pointerEvents: 'none', display: 'flex', alignItems: 'center', gap: 6 }}>
          {trailing}
          <Icon name="chevron-down" size={16} color="var(--text-secondary)" />
        </span>
      </div>
    </Field>
  );
}
