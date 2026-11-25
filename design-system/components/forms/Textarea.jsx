import React, { useState, useId } from 'react';
import { Field } from './Field.jsx';

export function Textarea({ label, helper, error, success, required, rows = 3, disabled, restricted = false, restrictedReason, id, style, ...rest }) {
  const auto = useId();
  const tid = id || auto;
  const [focus, setFocus] = useState(false);
  const border = error ? 'var(--status-error)' : focus ? 'var(--border-focus)' : success ? 'var(--status-success)' : 'var(--border-input)';
  return (
    <Field label={label} htmlFor={tid} required={required} helper={helper} error={error} success={success} restricted={restricted} restrictedReason={restrictedReason} style={style}>
      <textarea id={tid} rows={rows} disabled={disabled} readOnly={restricted} aria-invalid={!!error || undefined}
        aria-describedby={error || helper || success || restricted ? tid + '-msg' : undefined}
        onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
        style={{ resize: 'vertical', padding: '8px 12px', borderRadius: 'var(--radius-md)', font: 'var(--type-body)', color: 'var(--text-primary)',
          border: `var(--border-width-default) solid ${border}`, boxShadow: focus ? (error ? 'var(--focus-ring-error)' : 'var(--focus-ring)') : 'var(--shadow-xs)', outline: 'none',
          background: disabled ? 'var(--bg-disabled)' : restricted ? 'var(--bg-subtle)' : 'var(--bg-surface)' }} {...rest} />
    </Field>
  );
}
