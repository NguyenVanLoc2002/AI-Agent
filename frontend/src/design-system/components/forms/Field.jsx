import React from 'react';
import { Icon } from '../core/Icon.jsx';

export function Field({ label, htmlFor, required, helper, error, success, restricted, restrictedReason = 'Bạn không có quyền chỉnh sửa trường này', children, style }) {
  const msgId = htmlFor ? htmlFor + '-msg' : undefined;
  const msg = error ? (
    <div id={msgId} role="alert" style={{ display: 'flex', alignItems: 'center', gap: 6, font: 'var(--type-helper)', color: 'var(--status-error-fg)' }}><Icon name="circle-alert" size={14} />{error}</div>
  ) : restricted ? (
    <div id={msgId} style={{ display: 'flex', alignItems: 'center', gap: 6, font: 'var(--type-helper)', color: 'var(--status-restricted-fg)' }}><Icon name="lock" size={14} />{restrictedReason}</div>
  ) : success ? (
    <div id={msgId} role="status" style={{ display: 'flex', alignItems: 'center', gap: 6, font: 'var(--type-helper)', color: 'var(--status-success-fg)' }}><Icon name="circle-check" size={14} />{success}</div>
  ) : helper ? (
    <div id={msgId} style={{ font: 'var(--type-helper)', color: 'var(--text-secondary)' }}>{helper}</div>
  ) : null;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0, ...style }}>
      {label && (
        <label htmlFor={htmlFor} style={{ font: 'var(--type-label)', color: 'var(--text-primary)' }}>
          {label}{required && <span style={{ color: 'var(--status-error-fg)', marginLeft: 2 }} aria-hidden="true">*</span>}
        </label>
      )}
      {children}
      {msg}
    </div>
  );
}
