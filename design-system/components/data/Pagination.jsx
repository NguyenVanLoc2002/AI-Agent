import React from 'react';
import { IconButton } from '../core/IconButton.jsx';
import { useFocusVisible } from '../core/a11y.jsx';

function PageBtn({ n, current, onClick }) {
  const [focus, focusHandlers] = useFocusVisible();
  return (
    <button type="button" onClick={onClick} aria-current={current ? 'page' : undefined} aria-label={'Trang ' + n} {...focusHandlers}
      style={{ minWidth: 28, height: 28, padding: '0 6px', borderRadius: 'var(--radius-sm)', border: current ? 'var(--border-width-default) solid var(--border-default)' : 'var(--border-width-default) solid transparent', outline: 'none',
        background: current ? 'var(--bg-surface)' : 'transparent', boxShadow: focus ? 'var(--focus-ring)' : current ? 'var(--shadow-xs)' : 'none', font: `${current ? 600 : 500} 13px/1.4 var(--font-sans)`,
        color: current ? 'var(--text-primary)' : 'var(--text-secondary)', cursor: 'pointer', fontVariantNumeric: 'tabular-nums' }}>{n}</button>
  );
}

export function Pagination({ page = 1, pageSize = 20, total = 0, onPageChange, style }) {
  const pages = Math.max(1, Math.ceil(total / pageSize));
  const from = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = Math.min(total, page * pageSize);
  const nums = [];
  for (let p = 1; p <= pages; p++) if (p === 1 || p === pages || Math.abs(p - page) <= 1) nums.push(p); else if (nums[nums.length - 1] !== '…') nums.push('…');
  const fmt = (n) => n.toLocaleString('vi-VN');
  return (
    <nav aria-label="Phân trang" style={{ display: 'flex', alignItems: 'center', gap: 12, font: '400 13px/1.4 var(--font-sans)', color: 'var(--text-secondary)', ...style }}>
      <span style={{ flex: 1 }} aria-live="polite">Hiển thị <b style={{ color: 'var(--text-primary)', fontWeight: 500 }}>{fmt(from)}–{fmt(to)}</b> trên {fmt(total)} kết quả</span>
      <div style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
        <IconButton icon="chevron-left" label="Trang trước" size="sm" disabled={page <= 1} onClick={() => onPageChange && onPageChange(page - 1)} />
        {nums.map((n, i) => n === '…' ? <span key={'e' + i} aria-hidden="true" style={{ width: 28, textAlign: 'center' }}>…</span> : <PageBtn key={n} n={n} current={n === page} onClick={() => onPageChange && onPageChange(n)} />)}
        <IconButton icon="chevron-right" label="Trang sau" size="sm" disabled={page >= pages} onClick={() => onPageChange && onPageChange(page + 1)} />
      </div>
    </nav>
  );
}
