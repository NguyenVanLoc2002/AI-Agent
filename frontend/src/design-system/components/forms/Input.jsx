import React, { useState, useId } from 'react';
import { Icon } from '../core/Icon.jsx';
import { Spinner } from '../core/Spinner.jsx';
import { CODE_STYLE } from '../core/a11y.jsx';
import { Field } from './Field.jsx';

const H = { sm: 'var(--control-h-sm)', md: 'var(--control-h-md)', lg: 'var(--control-h-lg)' };

export function Input({ label, helper, error, success, required, size = 'md', iconLeft, suffix, disabled, readOnly, restricted = false, restrictedReason, loading = false, mono = false, id, style, inputStyle, ...rest }) {
  const auto = useId();
  const inputId = id || auto;
  const [focus, setFocus] = useState(false);
  const borderColor = error ? 'var(--status-error)' : focus ? 'var(--border-focus)' : success ? 'var(--status-success)' : 'var(--border-input)';
  const ring = focus ? (error ? 'var(--focus-ring-error)' : 'var(--focus-ring)') : 'var(--shadow-xs)';
  const bg = disabled ? 'var(--bg-disabled)' : restricted || readOnly ? 'var(--bg-subtle)' : 'var(--bg-surface)';
  const trailing = loading ? <Spinner size={16} color="var(--text-secondary)" /> : restricted ? <Icon name="lock" size={16} color="var(--status-restricted-fg)" /> : success && !error ? <Icon name="circle-check" size={16} color="var(--status-success)" /> : null;
  return (
    <Field label={label} htmlFor={inputId} required={required} helper={helper} error={error} success={success} restricted={restricted} restrictedReason={restrictedReason} style={style}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, height: H[size] || H.md, padding: '0 12px', borderRadius: 'var(--radius-md)',
        border: `var(--border-width-default) solid ${borderColor}`, boxShadow: ring, background: bg, transition: 'border-color var(--duration-fast), box-shadow var(--duration-fast)' }}>
        {iconLeft && <Icon name={iconLeft} size={16} color="var(--text-tertiary)" />}
        <input id={inputId} disabled={disabled} readOnly={readOnly || restricted} aria-invalid={!!error || undefined} aria-busy={loading || undefined}
          aria-describedby={error || helper || success || restricted ? inputId + '-msg' : undefined} required={required}
          onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
          style={{ flex: 1, minWidth: 0, border: 0, outline: 'none', background: 'transparent', height: '100%', padding: 0,
            ...(mono ? CODE_STYLE : { font: '400 14px/1.4 var(--font-sans)' }), color: disabled ? 'var(--text-disabled)' : 'var(--text-primary)', ...inputStyle }} {...rest} />
        {trailing}
        {suffix}
      </div>
    </Field>
  );
}
