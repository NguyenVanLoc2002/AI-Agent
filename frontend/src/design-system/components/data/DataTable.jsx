import React, { useState, useMemo } from 'react';
import { Icon } from '../core/Icon.jsx';
import { Checkbox } from '../forms/Checkbox.jsx';

// Modified copy (FE-UI-P1, A-80, FE-UI-D14): the row's Enter/Space handler only acts on the row itself, so keys
// pressed on a checkbox, button or link inside the row no longer open the row (WCAG 2.1.1, 4.1.2). Look unchanged.

export function DataTable({ columns = [], rows = [], rowKey = 'id', selectable = false, selected, onSelectedChange, sort, onSortChange, onRowClick, activeRowKey, loading = false, emptyState, density = 'comfortable', stickyHeader = true, toolbar, footer, caption, style }) {
  const [focusKey, setFocusKey] = useState(null);
  const [innerSel, setInnerSel] = useState([]);
  const [innerSort, setInnerSort] = useState(null);
  const [hoverKey, setHoverKey] = useState(null);
  const sel = selected ?? innerSel;
  const setSel = onSelectedChange ?? setInnerSel;
  const srt = sort !== undefined ? sort : innerSort;
  const setSrt = onSortChange ?? setInnerSort;
  const keyOf = (r, i) => (typeof rowKey === 'function' ? rowKey(r, i) : r[rowKey] ?? i);
  const sorted = useMemo(() => {
    if (!srt || onSortChange) return rows;
    const col = columns.find((c) => c.key === srt.key);
    const get = col && col.sortValue ? col.sortValue : (r) => r[srt.key];
    return [...rows].sort((a, b) => { const x = get(a), y = get(b); const c = x > y ? 1 : x < y ? -1 : 0; return srt.dir === 'desc' ? -c : c; });
  }, [rows, srt, columns, onSortChange]);
  const allKeys = sorted.map(keyOf);
  const allOn = allKeys.length > 0 && allKeys.every((k) => sel.includes(k));
  const someOn = !allOn && allKeys.some((k) => sel.includes(k));
  const rowH = density === 'compact' ? 'var(--table-row-h-compact)' : 'var(--table-row-h)';
  const cellPad = '0 12px';
  const toggleSort = (c) => { if (!c.sortable) return; const dir = srt && srt.key === c.key && srt.dir === 'asc' ? 'desc' : 'asc'; setSrt({ key: c.key, dir }); };
  return (
    <div style={{ border: 'var(--border-width-default) solid var(--border-card)', borderRadius: 'var(--radius-lg)', background: 'var(--bg-surface)', overflow: 'hidden', display: 'flex', flexDirection: 'column', ...style }}>
      {toolbar && <div style={{ padding: '10px 12px', borderBottom: 'var(--border-width-default) solid var(--border-default)' }}>{toolbar}</div>}
      <div style={{ overflow: 'auto', flex: 1 }}>
      <table aria-busy={loading || undefined} style={{ width: '100%', borderCollapse: 'separate', borderSpacing: 0, font: 'var(--type-table)', color: 'var(--text-primary)' }}>
        {caption && <caption style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)' }}>{caption}</caption>}
        <thead>
          <tr>
            {selectable && (
              <th style={{ width: 40, padding: '0 0 0 14px', height: 40, background: 'var(--bg-surface)', borderBottom: 'var(--border-width-default) solid var(--border-default)', position: stickyHeader ? 'sticky' : undefined, top: 0, zIndex: 1 }}>
                <Checkbox aria-label="Chọn tất cả" checked={allOn} indeterminate={someOn} onChange={() => setSel(allOn ? [] : allKeys)} />
              </th>
            )}
            {columns.map((c) => {
              const active = srt && srt.key === c.key;
              return (
                <th key={c.key} scope="col" aria-sort={active ? (srt.dir === 'asc' ? 'ascending' : 'descending') : undefined}
                  style={{ height: 40, padding: cellPad, textAlign: c.align || 'left', width: c.width, whiteSpace: 'nowrap', font: 'var(--type-table-header)', color: active ? 'var(--text-primary)' : 'var(--text-secondary)',
                    background: 'var(--bg-surface)', borderBottom: 'var(--border-width-default) solid var(--border-default)', userSelect: 'none', position: stickyHeader ? 'sticky' : undefined, top: 0, zIndex: 1 }}>
                  {c.sortable ? (
                    <button type="button" onClick={() => toggleSort(c)} title={'Sắp xếp theo ' + (typeof c.header === 'string' ? c.header : '')}
                      style={{ display: 'inline-flex', alignItems: 'center', gap: 4, flexDirection: c.align === 'right' ? 'row-reverse' : 'row', border: 0, background: 'transparent', padding: '2px 4px', margin: '0 -4px', borderRadius: 4, font: 'inherit', color: 'inherit', cursor: 'pointer', textTransform: 'inherit', letterSpacing: 'inherit' }}>
                      {c.header}
                      <Icon name={active ? (srt.dir === 'asc' ? 'arrow-up' : 'arrow-down') : 'chevrons-up-down'} size={14} color={active ? 'var(--action-primary)' : 'var(--text-tertiary)'} />
                    </button>
                  ) : c.header}
                </th>
              );
            })}
          </tr>
        </thead>
        <tbody>
          {loading && Array.from({ length: 5 }).map((_, i) => (
            <tr key={'sk' + i}>
              {selectable && <td style={{ height: rowH, borderBottom: '1px solid var(--border-subtle)' }} />}
              {columns.map((c, j) => (
                <td key={c.key} style={{ height: rowH, padding: cellPad, borderBottom: '1px solid var(--border-subtle)' }}>
                  <div style={{ height: 10, width: `${50 + ((i * 7 + j * 13) % 40)}%`, borderRadius: 4, background: 'linear-gradient(90deg,var(--gray-100) 0,var(--gray-150) 40%,var(--gray-100) 80%)', backgroundSize: '800px 100%', animation: 'ap-shimmer 1.4s linear infinite', marginLeft: c.align === 'right' ? 'auto' : 0 }} />
                </td>
              ))}
            </tr>
          ))}
          {!loading && sorted.length === 0 && (
            <tr><td colSpan={columns.length + (selectable ? 1 : 0)} style={{ padding: 0 }}>{emptyState || <div style={{ padding: 40, textAlign: 'center', color: 'var(--text-secondary)' }}>Không có dữ liệu</div>}</td></tr>
          )}
          {!loading && sorted.map((r, i) => {
            const k = keyOf(r, i);
            const on = sel.includes(k);
            const isActive = activeRowKey !== undefined && activeRowKey === k;
            const bg = on || isActive ? 'var(--bg-selected)' : hoverKey === k ? 'var(--bg-subtle)' : 'transparent';
            return (
              <tr key={k} onClick={onRowClick ? () => onRowClick(r) : undefined} onMouseEnter={() => setHoverKey(k)} onMouseLeave={() => setHoverKey(null)}
                tabIndex={onRowClick ? 0 : undefined} aria-selected={selectable ? on : undefined}
                onKeyDown={onRowClick ? (e) => { if (e.target !== e.currentTarget) return; if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onRowClick(r); } } : undefined}
                onFocus={onRowClick ? (e) => { let v = true; try { v = e.target.matches(':focus-visible'); } catch (_) {} if (v && e.target === e.currentTarget) setFocusKey(k); } : undefined} onBlur={() => setFocusKey(null)}
                style={{ background: bg, cursor: onRowClick ? 'pointer' : 'default', outline: 'none', boxShadow: focusKey === k ? 'inset 0 0 0 2px var(--border-focus)' : 'none', transition: 'background var(--duration-fast)' }}>
                {selectable && (
                  <td onClick={(e) => e.stopPropagation()} style={{ padding: '0 0 0 14px', height: rowH, borderBottom: '1px solid var(--border-subtle)' }}>
                    <Checkbox aria-label="Chọn dòng" checked={on} onChange={() => setSel(on ? sel.filter((x) => x !== k) : [...sel, k])} />
                  </td>
                )}
                {columns.map((c) => (
                  <td key={c.key} style={{ height: rowH, padding: cellPad, textAlign: c.align || 'left', borderBottom: '1px solid var(--border-subtle)', whiteSpace: c.wrap ? 'normal' : 'nowrap',
                    fontVariantNumeric: c.align === 'right' ? 'tabular-nums' : undefined, color: c.muted ? 'var(--text-secondary)' : undefined }}>
                    {c.render ? c.render(r) : r[c.key]}
                  </td>
                ))}
              </tr>
            );
          })}
        </tbody>
      </table>
      </div>
      {footer && <div style={{ padding: '10px 12px', borderTop: 'var(--border-width-default) solid var(--border-default)' }}>{footer}</div>}
    </div>
  );
}
