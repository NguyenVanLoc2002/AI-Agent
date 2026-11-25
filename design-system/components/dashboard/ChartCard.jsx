import React from 'react';
import { Tabs } from '../navigation/Tabs.jsx';
import { Skeleton } from '../feedback/Skeleton.jsx';
import { EmptyState } from '../feedback/EmptyState.jsx';
import { SourceTag } from '../ai/SourceTag.jsx';

export function ChartCard({ title, subtitle, value, periods, period, onPeriodChange, legend = [], source, state = 'ready', errorVariant = 'erp-unavailable', emptyTitle, onRetry, retryAction, height = 220, actions, children, summary, style }) {
  return (
    <section aria-label={typeof title === 'string' ? title : undefined} style={{ background: 'var(--bg-surface)', border: 'var(--border-width-default) solid var(--border-card)', borderRadius: 'var(--radius-lg)', boxShadow: 'var(--shadow-card)',
      padding: 'var(--space-card-lg)', display: 'flex', flexDirection: 'column', gap: 16, minWidth: 0, ...style }}>
      <header style={{ display: 'flex', alignItems: 'flex-start', gap: 12, flexWrap: 'wrap' }}>
        <div style={{ flex: '1 1 200px', minWidth: 0 }}>
          <h3 style={{ margin: 0, font: 'var(--type-card-title)' }}>{title}</h3>
          {subtitle && <div style={{ font: 'var(--type-helper)', color: 'var(--text-secondary)', marginTop: 2 }}>{subtitle}</div>}
          {value != null && state === 'ready' && <div style={{ font: 'var(--type-metric)', fontSize: 28, fontVariantNumeric: 'tabular-nums', marginTop: 8 }}>{value}</div>}
        </div>
        {periods && <Tabs variant="pill" aria-label="Kỳ báo cáo" tabs={periods} value={period} onChange={onPeriodChange} />}
        {actions}
      </header>
      <div style={{ height, position: 'relative' }} aria-busy={state === 'loading' || undefined}>
        {state === 'loading' && <div style={{ display: 'flex', alignItems: 'flex-end', gap: 8, height: '100%' }}>{[40, 65, 50, 80, 60, 90, 70].map((h, i) => <Skeleton key={i} height={(h / 100) * height} width="100%" radius={4} />)}</div>}
        {state === 'error' && <EmptyState compact variant={errorVariant} action={retryAction} style={{ height: '100%', justifyContent: 'center' }} />}
        {state === 'restricted' && <EmptyState compact variant="unauthorized" style={{ height: '100%', justifyContent: 'center' }} />}
        {state === 'empty' && <EmptyState compact variant="empty" title={emptyTitle || 'Chưa có dữ liệu trong kỳ này'} style={{ height: '100%', justifyContent: 'center' }} />}
        {state === 'ready' && children}
      </div>
      {summary && state === 'ready' && <p style={{ position: 'absolute', width: 1, height: 1, overflow: 'hidden', clip: 'rect(0 0 0 0)', margin: 0 }}>{summary}</p>}
      {(legend.length > 0 || source) && (
        <footer style={{ display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap', paddingTop: 12, borderTop: 'var(--border-width-default) solid var(--border-subtle)' }}>
          {legend.map((l) => (
            <span key={l.label} style={{ display: 'inline-flex', alignItems: 'center', gap: 6, font: 'var(--type-caption)', color: 'var(--text-secondary)' }}>
              <span aria-hidden="true" style={{ width: 10, height: l.line ? 2 : 10, borderRadius: l.line ? 1 : 2, background: l.color, borderTop: l.dashed ? '2px dashed ' + l.color : undefined }} />{l.label}
            </span>
          ))}
          {source && <SourceTag variant="inline" {...source} style={{ marginLeft: 'auto' }} />}
        </footer>
      )}
    </section>
  );
}
