import { useState, useEffect, useRef } from 'react';

// Shared accessibility helpers (not part of the public namespace).
export function useFocusVisible() {
  const [fv, setFv] = useState(false);
  return [fv, {
    onFocus: (e) => { let v = true; try { v = e.target.matches(':focus-visible'); } catch (_) {} setFv(v); },
    onBlur: () => setFv(false),
  }];
}

export const CODE_STYLE = { font: 'var(--type-code)', fontVariantNumeric: 'tabular-nums', letterSpacing: 'var(--tracking-code)' };

const FOCUSABLE = 'button:not([disabled]),[href],input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])';

// Dialog/Drawer: focus on open, trap Tab, close on Esc, restore focus on close.
export function useModalA11y(active, onClose) {
  const ref = useRef(null);
  const closeRef = useRef(onClose);
  closeRef.current = onClose;
  useEffect(() => {
    if (!active) return undefined;
    const prev = document.activeElement;
    const node = ref.current;
    if (node) node.focus();
    const onKey = (e) => {
      if (e.key === 'Escape' && closeRef.current) { e.stopPropagation(); closeRef.current(); return; }
      if (e.key !== 'Tab' || !node) return;
      const f = Array.from(node.querySelectorAll(FOCUSABLE));
      if (!f.length) { e.preventDefault(); return; }
      const first = f[0], last = f[f.length - 1];
      if (e.shiftKey && (document.activeElement === first || document.activeElement === node)) { e.preventDefault(); last.focus(); }
      else if (!e.shiftKey && document.activeElement === last) { e.preventDefault(); first.focus(); }
    };
    document.addEventListener('keydown', onKey);
    return () => { document.removeEventListener('keydown', onKey); if (prev && prev.focus) prev.focus(); };
  }, [active]);
  return ref;
}
