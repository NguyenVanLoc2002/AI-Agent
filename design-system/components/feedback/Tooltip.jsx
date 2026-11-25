import React, { useState, useId } from 'react';

export function Tooltip({ content, side = 'top', children, style }) {
  const [open, setOpen] = useState(false);
  const tid = useId();
  const pos = { top: { bottom: '100%', left: '50%', transform: 'translate(-50%,-6px)' }, bottom: { top: '100%', left: '50%', transform: 'translate(-50%,6px)' },
    left: { right: '100%', top: '50%', transform: 'translate(-6px,-50%)' }, right: { left: '100%', top: '50%', transform: 'translate(6px,-50%)' } }[side];
  return (
    <span style={{ position: 'relative', display: 'inline-flex', ...style }} onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)} onFocus={() => setOpen(true)} onBlur={() => setOpen(false)} onKeyDown={(e) => e.key === 'Escape' && setOpen(false)} aria-describedby={open ? tid : undefined}>
      {children}
      {open && content && (
        <span id={tid} role="tooltip" style={{ position: 'absolute', ...pos, zIndex: 'var(--z-tooltip)', maxWidth: 260, width: 'max-content', padding: '6px 9px', borderRadius: 'var(--radius-sm)',
          background: 'var(--bg-inverse)', color: 'var(--text-inverse)', font: '500 12px/1.45 var(--font-sans)', boxShadow: 'var(--shadow-md)', pointerEvents: 'none' }}>{content}</span>
      )}
    </span>
  );
}
