import React, { useState } from 'react';
import { Icon } from './Icon.jsx';
import { Spinner } from './Spinner.jsx';
import { useFocusVisible } from './a11y.jsx';

const SIZES = {
  sm: { height: 'var(--control-h-sm)', padding: '0 10px', font: 'var(--type-button-sm)', gap: 6, icon: 16 },
  md: { height: 'var(--control-h-md)', padding: '0 14px', font: 'var(--type-button)', gap: 8, icon: 16 },
  lg: { height: 'var(--control-h-lg)', padding: '0 18px', font: 'var(--type-button)', gap: 8, icon: 20 },
};
const VARIANTS = {
  primary: { bg: 'var(--action-primary)', hover: 'var(--action-primary-hover)', active: 'var(--action-primary-active)', color: 'var(--text-on-primary)', border: 'transparent', shadow: 'var(--shadow-xs)' },
  secondary: { bg: 'var(--bg-surface)', hover: 'var(--bg-hover)', active: 'var(--bg-active)', color: 'var(--text-primary)', border: 'var(--border-default)', shadow: 'var(--shadow-xs)' },
  ghost: { bg: 'transparent', hover: 'var(--bg-hover)', active: 'var(--bg-active)', color: 'var(--text-primary)', border: 'transparent', shadow: 'none' },
  subtle: { bg: 'var(--action-primary-subtle)', hover: 'var(--blue-100)', active: 'var(--blue-200)', color: 'var(--action-primary-hover)', border: 'transparent', shadow: 'none' },
  danger: { bg: 'var(--status-error)', hover: 'var(--red-700)', active: 'var(--red-700)', color: 'var(--text-on-primary)', border: 'transparent', shadow: 'var(--shadow-xs)' },
};

export function Button({ variant = 'primary', size = 'md', iconLeft, iconRight, loading = false, disabled = false, restricted = false, restrictedReason = 'Bạn không có quyền thực hiện thao tác này', fullWidth = false, type = 'button', children, onClick, style, ...rest }) {
  const [hover, setHover] = useState(false);
  const [press, setPress] = useState(false);
  const [focus, focusHandlers] = useFocusVisible();
  const s = SIZES[size] || SIZES.md;
  const v = restricted
    ? { bg: 'var(--status-restricted-bg)', hover: 'var(--status-restricted-bg)', active: 'var(--status-restricted-bg)', color: 'var(--status-restricted-fg)', border: 'var(--status-restricted-border)', shadow: 'none' }
    : VARIANTS[variant] || VARIANTS.primary;
  const isDisabled = disabled || loading || restricted;
  const bg = isDisabled ? v.bg : press ? v.active : hover ? v.hover : v.bg;
  return (
    <button type={type} disabled={isDisabled} aria-busy={loading || undefined} aria-disabled={isDisabled || undefined}
      title={restricted ? restrictedReason : rest.title}
      onClick={isDisabled ? undefined : onClick}
      onMouseEnter={() => setHover(true)} onMouseLeave={() => { setHover(false); setPress(false); }}
      onMouseDown={() => setPress(true)} onMouseUp={() => setPress(false)} {...focusHandlers}
      style={{
        display: fullWidth ? 'flex' : 'inline-flex', width: fullWidth ? '100%' : undefined, alignItems: 'center', justifyContent: 'center',
        gap: s.gap, height: s.height, padding: s.padding, borderRadius: 'var(--radius-md)', font: s.font, whiteSpace: 'nowrap',
        cursor: isDisabled ? 'not-allowed' : 'pointer', background: bg, color: v.color, border: `var(--border-width-default) solid ${v.border}`,
        boxShadow: focus ? 'var(--focus-ring)' : v.shadow, opacity: disabled && !loading && !restricted ? 0.5 : 1, outline: 'none',
        transition: 'background var(--duration-fast) var(--ease-standard), box-shadow var(--duration-fast)', ...style,
      }} {...rest}>
      {loading ? <Spinner size={s.icon} /> : restricted ? <Icon name="lock" size={s.icon} /> : iconLeft ? <Icon name={iconLeft} size={s.icon} /> : null}
      {children}
      {iconRight && !loading && !restricted ? <Icon name={iconRight} size={s.icon} /> : null}
    </button>
  );
}
