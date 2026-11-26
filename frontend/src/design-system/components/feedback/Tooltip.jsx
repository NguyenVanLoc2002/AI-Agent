import React, { useState, useId, useEffect, useRef, cloneElement, isValidElement, Children } from 'react';

// Modified copy (FE-UI-P1, A-80, FE-UI-D11), look unchanged. The original described the wrapper span instead of the
// focused control, closed only on Esc while focus was inside it, and could not be hovered (pointer-events: none and a
// 6 px gap closed it on the way). WCAG 1.4.13: dismissible (Esc anywhere), hoverable (short close delay, pointer
// events on), persistent; 4.1.2: the description belongs to the control.
const CLOSE_DELAY_MS = 120;

export function Tooltip({ content, side = 'top', children, style }) {
  const [open, setOpen] = useState(false);
  const tid = useId();
  const closeTimer = useRef(null);
  const cancelClose = () => { if (closeTimer.current) { clearTimeout(closeTimer.current); closeTimer.current = null; } };
  const show = () => { cancelClose(); setOpen(true); };
  const hide = () => { cancelClose(); setOpen(false); };
  const hideSoon = () => { cancelClose(); closeTimer.current = setTimeout(() => { closeTimer.current = null; setOpen(false); }, CLOSE_DELAY_MS); };
  useEffect(() => cancelClose, []);
  useEffect(() => {
    if (!open) return undefined;
    // Capture phase + stopPropagation: the first Esc closes only the tooltip, not a Dialog around it (review L1).
    const onKey = (e) => { if (e.key === 'Escape') { e.stopPropagation(); hide(); } };
    document.addEventListener('keydown', onKey, true);
    return () => document.removeEventListener('keydown', onKey, true);
  }, [open]);
  const pos = { top: { bottom: '100%', left: '50%', transform: 'translate(-50%,-6px)' }, bottom: { top: '100%', left: '50%', transform: 'translate(-50%,6px)' },
    left: { right: '100%', top: '50%', transform: 'translate(-6px,-50%)' }, right: { left: '100%', top: '50%', transform: 'translate(6px,-50%)' } }[side];
  const shown = open && content;
  const only = Children.count(children) === 1 && isValidElement(children) ? children : null;
  const describe = (existing) => [existing, tid].filter(Boolean).join(' ');
  const trigger = only && shown ? cloneElement(only, { 'aria-describedby': describe(only.props['aria-describedby']) }) : children;
  return (
    <span style={{ position: 'relative', display: 'inline-flex', ...style }} onMouseEnter={show} onMouseLeave={hideSoon} onFocus={show} onBlur={hide}
      aria-describedby={shown && !only ? tid : undefined}>
      {trigger}
      {shown && (
        <span id={tid} role="tooltip" style={{ position: 'absolute', ...pos, zIndex: 'var(--z-tooltip)', maxWidth: 260, width: 'max-content', padding: '6px 9px', borderRadius: 'var(--radius-sm)',
          background: 'var(--bg-inverse)', color: 'var(--text-inverse)', font: '500 12px/1.45 var(--font-sans)', boxShadow: 'var(--shadow-md)' }}>{content}</span>
      )}
    </span>
  );
}
