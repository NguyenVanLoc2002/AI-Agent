import React from 'react';
import { Icon } from '../core/Icon.jsx';

export function MetricCard({ label, value, unit, delta, deltaDirection, deltaLabel, icon, source, loading = false, style }) {
  const dir = deltaDirection || (typeof delta === 'string' && delta.trim().startsWith('-') ? 'down' : 'up');
  const color = dir === 'down' ? 'var(--status-error-fg)' : dir === 'flat' ? 'var(--text-secondary)' : 'var(--status-success-fg)';
  return (
    <div style={{ background: 'var(--bg-surface)', border: '1px solid var(--border-default)', borderRadius: 'var(--radius-lg)', padding: 'var(--space-card-lg)', boxShadow: 'var(--shadow-card)', display: 'flex', flexDirection: 'column', gap: 10, minWidth: 0, ...style }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: 'var(--text-secondary)' }}>
        {icon && <Icon name={icon} size={16} />}
        <span style={{ font: '500 13px/1.4 var(--font-sans)', flex: 1 }}>{label}</span>
        {source && <span style={{ font: '400 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)' }}>{source}</span>}
      </div>
      {loading ? (
        <div style={{ height: 38, width: '60%', borderRadius: 6, background: 'linear-gradient(90deg,var(--gray-100) 0,var(--gray-150) 40%,var(--gray-100) 80%)', backgroundSize: '800px 100%', animation: 'ap-shimmer 1.4s linear infinite' }} />
      ) : (
        <div style={{ display: 'flex', alignItems: 'baseline', gap: 6 }}>
          <span style={{ font: 'var(--type-metric)', fontVariantNumeric: 'tabular-nums', color: 'var(--text-primary)' }}>{value}</span>
          {unit && <span style={{ font: '500 14px/1.4 var(--font-sans)', color: 'var(--text-secondary)' }}>{unit}</span>}
        </div>
      )}
      {delta != null && !loading && (
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, font: '400 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)' }}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: 2, color, fontWeight: 600 }}>
            <Icon name={dir === 'down' ? 'arrow-down' : dir === 'flat' ? 'minus' : 'arrow-up'} size={14} strokeWidth={2.25} />{String(delta).replace(/^[-+]/, '')}
          </span>
          {deltaLabel}
        </div>
      )}
    </div>
  );
}
