import React from 'react';
import { Icon } from '../core/Icon.jsx';

const SRC = {
  erp: ['shield-check', 'ERP', 'var(--source-erp-bg)', 'var(--source-erp-fg)', 'var(--source-erp-border)', 'Dữ liệu đã xác minh từ ERP'],
  ai: ['sparkles', 'Gợi ý AI', 'var(--source-ai-bg)', 'var(--source-ai-fg)', 'var(--source-ai-border)', 'Giá trị do AI gợi ý — cần kiểm tra trước khi dùng'],
  cache: ['clock', 'Bản lưu', 'var(--status-warning-bg)', 'var(--status-warning-fg)', 'var(--status-warning-border)', 'Dữ liệu lưu tạm, chưa xác minh lại với ERP'],
  manual: ['pencil', 'Nhập tay', 'var(--status-neutral-bg)', 'var(--status-neutral-fg)', 'var(--status-neutral-border)', 'Giá trị do người dùng nhập'],
  lookup: ['database', 'Dữ liệu tra cứu', 'var(--status-info-bg)', 'var(--status-info-fg)', 'var(--status-info-border)', 'Kết quả tra cứu từ catalog VIN/OE hoặc ERP — chưa phải dữ liệu đã xác minh'],
  verified: ['shield-check', 'Đã xác minh', 'var(--source-erp-bg)', 'var(--source-erp-fg)', 'var(--source-erp-border)', 'Đã đạt quy tắc đối chiếu được duyệt'],
};

export function SourceTag({ source = 'erp', time, label, variant = 'chip', style }) {
  const [ic, def, bg, fg, bd, desc] = SRC[source] || SRC.erp;
  const text = label || def;
  const title = desc + (time ? ' · ' + time : '');
  if (variant === 'inline') {
    return (
      <span title={title} style={{ display: 'inline-flex', alignItems: 'center', gap: 4, font: 'var(--type-caption)', color: fg, whiteSpace: 'nowrap', ...style }}>
        <Icon name={ic} size={14} />{text}{time && <span style={{ color: 'var(--text-secondary)' }}>· {time}</span>}
      </span>
    );
  }
  return (
    <span title={title} style={{ display: 'inline-flex', alignItems: 'center', gap: 4, height: 22, padding: '0 7px', borderRadius: 'var(--radius-sm)', background: bg, color: fg,
      border: `var(--border-width-default) solid ${bd}`, font: 'var(--type-badge)', whiteSpace: 'nowrap', ...style }}>
      <Icon name={ic} size={14} />{text}{time && <span style={{ fontWeight: 400 }}>· {time}</span>}
    </span>
  );
}
