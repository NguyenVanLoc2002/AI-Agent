import React, { useState, useId } from 'react';
import { Icon } from '../core/Icon.jsx';
import { Button } from '../core/Button.jsx';
import { IconButton } from '../core/IconButton.jsx';
import { CODE_STYLE } from '../core/a11y.jsx';

const DEFAULT_MODES = [
  { value: 'vin', label: 'Số VIN', code: true, length: 17, placeholder: 'VD: MR8YB3JF5N0123456' },
  { value: 'oe', label: 'Mã OE', code: true, placeholder: 'VD: 04465-0D130' },
  { value: 'sku', label: 'SKU', code: true, placeholder: 'VD: BP-1042' },
  { value: 'name', label: 'Tên sản phẩm', placeholder: 'VD: má phanh Vios' },
];

export function SearchField({ modes = DEFAULT_MODES, mode, onModeChange, value, onChange, onSubmit, loading = false, error, minLength = 3, size = 'lg', submitLabel = 'Tìm kiếm', label, style }) {
  const uid = useId();
  const [innerMode, setInnerMode] = useState(modes[0] && modes[0].value);
  const [innerVal, setInnerVal] = useState('');
  const [localErr, setLocalErr] = useState('');
  const [focus, setFocus] = useState(false);
  const m = mode ?? innerMode;
  const setM = onModeChange ?? setInnerMode;
  const v = value ?? innerVal;
  const setV = onChange ?? setInnerVal;
  const cur = modes.find((x) => x.value === m) || modes[0] || {};
  const err = error || localErr;
  const h = size === 'lg' ? 'var(--control-h-lg)' : 'var(--control-h-md)';
  const submit = () => {
    if (loading) return;
    const q = v.trim();
    if (q.length < minLength) { setLocalErr('Nhập tối thiểu ' + minLength + ' ký tự để tìm kiếm.'); return; }
    if (cur.length && q.length !== cur.length) { setLocalErr(cur.label + ' phải gồm đúng ' + cur.length + ' ký tự.'); return; }
    setLocalErr('');
    onSubmit && onSubmit(q, m);
  };
  return (
    <div role="search" style={{ display: 'flex', flexDirection: 'column', gap: 6, ...style }}>
      {label && <label htmlFor={uid} style={{ font: 'var(--type-label)' }}>{label}</label>}
      <div style={{ display: 'flex', gap: 8 }}>
        <div style={{ flex: 1, minWidth: 0, display: 'flex', alignItems: 'center', height: h, borderRadius: 'var(--radius-md)', background: 'var(--bg-surface)',
          border: `var(--border-width-default) solid ${err ? 'var(--status-error)' : focus ? 'var(--border-focus)' : 'var(--border-input)'}`,
          boxShadow: focus ? (err ? 'var(--focus-ring-error)' : 'var(--focus-ring)') : 'var(--shadow-xs)' }}>
          {modes.length > 1 && (
            <span style={{ position: 'relative', display: 'flex', alignItems: 'center', height: '100%', borderRight: 'var(--border-width-default) solid var(--border-default)' }}>
              <select aria-label="Loại tìm kiếm" value={m} onChange={(e) => { setM(e.target.value); setLocalErr(''); }} onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
                style={{ appearance: 'none', WebkitAppearance: 'none', height: '100%', border: 0, background: 'transparent', padding: '0 30px 0 12px', font: 'var(--type-label)', color: 'var(--text-primary)', outline: 'none', cursor: 'pointer' }}>
                {modes.map((x) => <option key={x.value} value={x.value}>{x.label}</option>)}
              </select>
              <Icon name="chevron-down" size={16} color="var(--text-secondary)" style={{ position: 'absolute', right: 10, pointerEvents: 'none' }} />
            </span>
          )}
          <Icon name="search" size={16} color="var(--text-tertiary)" style={{ marginLeft: 12 }} />
          <input id={uid} type="search" value={v} placeholder={cur.placeholder} aria-invalid={!!err || undefined} aria-describedby={err ? uid + '-err' : undefined}
            onChange={(e) => { setV(e.target.value); if (localErr) setLocalErr(''); }} onKeyDown={(e) => { if (e.key === 'Enter') submit(); if (e.key === 'Escape') setV(''); }}
            onFocus={() => setFocus(true)} onBlur={() => setFocus(false)}
            style={{ flex: 1, minWidth: 0, height: '100%', border: 0, outline: 'none', background: 'transparent', padding: '0 8px', color: 'var(--text-primary)', ...(cur.code ? { ...CODE_STYLE, fontSize: 14 } : { font: '400 14px/1.4 var(--font-sans)' }) }} />
          {v && <IconButton icon="x" label="Xóa nội dung tìm kiếm" size="sm" onClick={() => setV('')} style={{ marginRight: 4 }} />}
        </div>
        <Button size={size} onClick={submit} loading={loading}>{loading ? 'Đang tìm…' : submitLabel}</Button>
      </div>
      {err && <div id={uid + '-err'} role="alert" style={{ display: 'flex', alignItems: 'center', gap: 6, font: 'var(--type-helper)', color: 'var(--status-error-fg)' }}><Icon name="circle-alert" size={14} />{err}</div>}
    </div>
  );
}
