import React from 'react';
import { Icon } from '../core/Icon.jsx';

const TONES = {
  neutral: ['var(--status-neutral-bg)', 'var(--status-neutral-fg)', 'var(--status-neutral-border)', 'var(--gray-500)'],
  info: ['var(--status-info-bg)', 'var(--status-info-fg)', 'var(--status-info-border)', 'var(--status-info)'],
  success: ['var(--status-success-bg)', 'var(--status-success-fg)', 'var(--status-success-border)', 'var(--status-success)'],
  warning: ['var(--status-warning-bg)', 'var(--status-warning-fg)', 'var(--status-warning-border)', 'var(--status-warning)'],
  error: ['var(--status-error-bg)', 'var(--status-error-fg)', 'var(--status-error-border)', 'var(--status-error)'],
  restricted: ['var(--status-restricted-bg)', 'var(--status-restricted-fg)', 'var(--status-restricted-border)', 'var(--status-restricted-fg)'],
  ai: ['var(--source-ai-bg)', 'var(--source-ai-fg)', 'var(--source-ai-border)', 'var(--source-ai)'],
  erp: ['var(--source-erp-bg)', 'var(--source-erp-fg)', 'var(--source-erp-border)', 'var(--source-erp)'],
};
const DEFAULT_ICON = { success: 'circle-check', warning: 'triangle-alert', error: 'circle-x', info: 'info', restricted: 'lock', ai: 'sparkles', erp: 'shield-check' };

// shape: 'rounded' (6px) = category / provenance · 'pill' = record status (dot by default)
export function Badge({ tone = 'neutral', shape = 'rounded', icon, dot, size = 'md', children, style }) {
  const [bg, fg, bd, dc] = TONES[tone] || TONES.neutral;
  const pill = shape === 'pill';
  const showDot = dot ?? (pill && !icon);
  const ic = icon === false ? null : icon || (showDot ? null : DEFAULT_ICON[tone]);
  const sm = size === 'sm';
  return (
    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 5, height: sm ? 20 : 24, padding: pill ? (sm ? '0 8px' : '0 10px') : (sm ? '0 6px' : '0 8px'),
      borderRadius: pill ? 'var(--radius-full)' : 'var(--radius-sm)', background: bg, color: fg, border: `var(--border-width-default) solid ${bd}`,
      font: 'var(--type-badge)', whiteSpace: 'nowrap', ...style }}>
      {showDot && <span aria-hidden="true" style={{ width: 6, height: 6, borderRadius: '50%', background: dc, flexShrink: 0 }} />}
      {ic && <Icon name={ic} size={14} />}
      {children}
    </span>
  );
}
