import React, { useState, useRef } from 'react';
import { Icon } from '../core/Icon.jsx';
import { useFocusVisible } from '../core/a11y.jsx';

function Tab({ t, active, onClick, onKeyDown, variant, btnRef }) {
  const [hover, setHover] = useState(false);
  const [focus, focusHandlers] = useFocusVisible();
  const seg = variant === 'segmented';
  const pill = variant === 'pill';
  if (pill) {
    return (
      <button ref={btnRef} type="button" role="tab" aria-selected={active} tabIndex={active ? 0 : -1} onClick={onClick} onKeyDown={onKeyDown} disabled={t.disabled}
        onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)} {...focusHandlers}
        style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: 28, padding: '0 12px', borderRadius: 'var(--radius-full)', outline: 'none', cursor: t.disabled ? 'not-allowed' : 'pointer',
          background: active ? 'var(--gray-900)' : hover ? 'var(--bg-hover)' : 'var(--bg-surface)', color: active ? 'var(--text-inverse)' : 'var(--text-primary)',
          border: `var(--border-width-default) solid ${active ? 'var(--gray-900)' : 'var(--border-default)'}`, boxShadow: focus ? 'var(--focus-ring)' : 'none',
          font: `${active ? 600 : 500} 13px/1.4 var(--font-sans)`, opacity: t.disabled ? 0.5 : 1, whiteSpace: 'nowrap', transition: 'background var(--duration-fast)' }}>
        {t.icon && <Icon name={t.icon} size={16} />}
        {t.label}
      </button>
    );
  }
  return (
    <button ref={btnRef} type="button" role="tab" aria-selected={active} tabIndex={active ? 0 : -1} onClick={onClick} onKeyDown={onKeyDown} disabled={t.disabled}
      onMouseEnter={() => setHover(true)} onMouseLeave={() => setHover(false)} {...focusHandlers}
      style={{ display: 'inline-flex', alignItems: 'center', gap: 6, height: seg ? 28 : 40, padding: seg ? '0 12px' : '0 2px', border: 0, cursor: t.disabled ? 'not-allowed' : 'pointer', outline: 'none',
        background: seg ? (active ? 'var(--bg-surface)' : 'transparent') : 'transparent', borderRadius: seg ? 'var(--radius-sm)' : 4, boxShadow: focus ? 'var(--focus-ring)' : seg && active ? 'var(--shadow-sm)' : 'none',
        borderBottom: seg ? 0 : `2px solid ${active ? 'var(--action-primary)' : 'transparent'}`, marginBottom: seg ? 0 : -1,
        color: active || hover ? 'var(--text-primary)' : 'var(--text-secondary)', font: `${active ? 600 : 500} ${seg ? 13 : 14}px/1.4 var(--font-sans)`, opacity: t.disabled ? 0.5 : 1, whiteSpace: 'nowrap' }}>
      {t.icon && <Icon name={t.icon} size={16} />}
      {t.label}
      {t.count != null && <span style={{ font: '600 12px/1.4 var(--font-sans)', padding: '0 6px', borderRadius: 999, background: active ? 'var(--blue-50)' : 'var(--bg-muted)', color: active ? 'var(--action-primary-hover)' : 'var(--text-secondary)' }}>{t.count}</span>}
    </button>
  );
}

export function Tabs({ tabs = [], value, onChange, variant = 'line', 'aria-label': ariaLabel, style }) {
  const seg = variant === 'segmented';
  const pill = variant === 'pill';
  const refs = useRef([]);
  const enabled = tabs.map((t, i) => (t.disabled ? -1 : i)).filter((i) => i >= 0);
  const onKey = (i) => (e) => {
    const pos = enabled.indexOf(i);
    let next = null;
    if (e.key === 'ArrowRight') next = enabled[(pos + 1) % enabled.length];
    if (e.key === 'ArrowLeft') next = enabled[(pos - 1 + enabled.length) % enabled.length];
    if (e.key === 'Home') next = enabled[0];
    if (e.key === 'End') next = enabled[enabled.length - 1];
    if (next == null) return;
    e.preventDefault();
    refs.current[next] && refs.current[next].focus();
    onChange && onChange(tabs[next].value);
  };
  return (
    <div role="tablist" aria-label={ariaLabel} style={{ display: seg || pill ? 'inline-flex' : 'flex', gap: pill ? 6 : seg ? 2 : 24, padding: seg ? 2 : pill ? 0 : '0 2px', borderRadius: seg ? 'var(--radius-md)' : 0, background: seg ? 'var(--bg-muted)' : 'transparent',
      borderBottom: seg || pill ? 0 : 'var(--border-width-default) solid var(--border-default)', overflowX: 'auto', ...style }}>
      {tabs.map((t, i) => <Tab key={t.value} t={t} variant={variant} active={t.value === value} btnRef={(el) => (refs.current[i] = el)} onKeyDown={onKey(i)} onClick={() => onChange && onChange(t.value)} />)}
    </div>
  );
}
