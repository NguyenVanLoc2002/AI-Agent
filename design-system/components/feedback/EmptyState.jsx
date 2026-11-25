import React from 'react';
import { Icon } from '../core/Icon.jsx';

const PRESETS = {
  empty: { icon: 'box', title: 'Chưa có dữ liệu', description: 'Dữ liệu sẽ xuất hiện tại đây khi được tạo.' },
  'no-results': { icon: 'search', title: 'Không tìm thấy kết quả', description: 'Thử kiểm tra lại mã OE, SKU hoặc bỏ bớt bộ lọc.' },
  unauthorized: { icon: 'lock', title: 'Bạn không có quyền truy cập', description: 'Liên hệ quản trị viên nếu bạn cần quyền xem nội dung này.', tone: 'restricted' },
  'erp-unavailable': { icon: 'server-off', title: 'Không kết nối được ERP', description: 'Giá và tồn kho tạm thời không khả dụng. Dữ liệu hiển thị có thể chưa cập nhật.', tone: 'warning' },
  'ai-unavailable': { icon: 'bot', title: 'Trợ lý AI tạm ngưng', description: 'Bạn vẫn có thể tra cứu và lập báo giá thủ công.', tone: 'warning' },
  timeout: { icon: 'timer-off', title: 'Yêu cầu quá thời gian', description: 'Hệ thống phản hồi chậm hơn dự kiến. Vui lòng thử lại.', tone: 'warning' },
  error: { icon: 'circle-x', title: 'Đã xảy ra lỗi', description: 'Không thể tải dữ liệu. Vui lòng thử lại sau.', tone: 'error' },
  offline: { icon: 'wifi-off', title: 'Mất kết nối mạng', description: 'Kiểm tra kết nối và thử lại.', tone: 'neutral' },
};
const TONE_BG = { neutral: ['var(--bg-muted)', 'var(--text-secondary)'], restricted: ['var(--status-restricted-bg)', 'var(--status-restricted-fg)'], warning: ['var(--status-warning-bg)', 'var(--status-warning)'], error: ['var(--status-error-bg)', 'var(--status-error)'], info: ['var(--status-info-bg)', 'var(--status-info)'] };

export function EmptyState({ variant = 'empty', icon, title, description, action, code, compact = false, style }) {
  const p = PRESETS[variant] || PRESETS.empty;
  const [bg, fg] = TONE_BG[p.tone || 'neutral'];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center', padding: compact ? '24px 16px' : '48px 24px', gap: 4, ...style }}>
      <span style={{ width: compact ? 36 : 44, height: compact ? 36 : 44, borderRadius: 'var(--radius-lg)', background: bg, color: fg, display: 'inline-flex', alignItems: 'center', justifyContent: 'center', marginBottom: 8 }}>
        <Icon name={icon || p.icon} size={20} />
      </span>
      <div style={{ font: '600 15px/1.45 var(--font-sans)', color: 'var(--text-primary)' }}>{title || p.title}</div>
      <div style={{ font: '400 13px/1.55 var(--font-sans)', color: 'var(--text-secondary)', maxWidth: 380, textWrap: 'pretty' }}>{description || p.description}</div>
      {code && <div style={{ font: '400 12px/1.4 var(--font-sans)', color: 'var(--text-secondary)', marginTop: 4 }}>Mã tham chiếu: {code}</div>}
      {action && <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>{action}</div>}
    </div>
  );
}
