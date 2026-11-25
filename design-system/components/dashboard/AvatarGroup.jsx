import React from 'react';

const FILLS = [['var(--blue-100)', 'var(--blue-800)'], ['var(--gray-150)', 'var(--gray-700)']];
const initialsOf = (n) => n.split(/\s+/).filter(Boolean).slice(-2).map((w) => w[0]).join('').toUpperCase();

export function AvatarGroup({ people = [], max = 4, size = 'md', label = 'Người phụ trách', style }) {
  // Overlap + 2px ring must stay below the side padding left by two initials, so text is never clipped.
  const d = size === 'sm' ? 28 : 36;
  const overlap = size === 'sm' ? 1 : 4;
  const shown = people.slice(0, max);
  const extra = people.length - shown.length;
  const names = people.map((p) => p.name + (p.role ? ' (' + p.role + ')' : '')).join(', ');
  const circle = (key, content, fill, title, i) => (
    <span key={key} title={title} style={{ width: d, height: d, borderRadius: '50%', background: fill[0], color: fill[1], display: 'inline-flex', alignItems: 'center', justifyContent: 'center',
      font: `600 ${size === 'sm' ? 12 : 13}px/1.4 var(--font-sans)`, boxShadow: '0 0 0 2px var(--bg-surface)', marginLeft: i ? -overlap : 0, position: 'relative', zIndex: i + 1, flexShrink: 0 }}>{content}</span>
  );
  return (
    <span role="img" aria-label={label + ': ' + names} style={{ display: 'inline-flex', alignItems: 'center', ...style }}>
      {shown.map((p, i) => circle(p.name, p.initials || initialsOf(p.name), FILLS[i % FILLS.length], p.name + (p.role ? ' · ' + p.role : ''), i))}
      {extra > 0 && circle('more', '+' + extra, ['var(--bg-muted)', 'var(--text-secondary)'], people.slice(max).map((p) => p.name).join(', '), shown.length)}
    </span>
  );
}
