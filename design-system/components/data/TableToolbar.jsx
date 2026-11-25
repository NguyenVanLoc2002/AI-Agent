import React from 'react';
import { Input } from '../forms/Input.jsx';
import { Button } from '../core/Button.jsx';
import { Tag } from './Tag.jsx';

export function TableToolbar({ search, filters = [], onRemoveFilter, onClearFilters, onAddFilter, actions, selectedCount = 0, bulkActions, onClearSelection, style }) {
  if (selectedCount > 0) {
    return (
      <div role="toolbar" aria-label="Thao tác hàng loạt" style={{ display: 'flex', alignItems: 'center', gap: 8, minHeight: 32, flexWrap: 'wrap', ...style }}>
        <span style={{ font: 'var(--type-label)', color: 'var(--text-primary)' }} aria-live="polite">Đã chọn {selectedCount.toLocaleString('vi-VN')}</span>
        <span style={{ width: 1, height: 20, background: 'var(--border-default)' }} />
        {bulkActions}
        <span style={{ flex: 1 }} />
        <Button size="sm" variant="ghost" onClick={onClearSelection}>Bỏ chọn</Button>
      </div>
    );
  }
  return (
    <div role="toolbar" aria-label="Tìm kiếm và bộ lọc" style={{ display: 'flex', alignItems: 'center', gap: 8, minHeight: 32, flexWrap: 'wrap', ...style }}>
      {search && <Input size="sm" iconLeft="search" type="search" aria-label={search.placeholder || 'Tìm kiếm'} placeholder={search.placeholder || 'Tìm kiếm…'} value={search.value} onChange={(e) => search.onChange && search.onChange(e.target.value)} style={{ width: 260 }} />}
      {onAddFilter && <Button size="sm" variant="secondary" iconLeft="list-filter" onClick={onAddFilter}>Bộ lọc</Button>}
      {filters.map((f) => <Tag key={f.key} onRemove={onRemoveFilter ? () => onRemoveFilter(f.key) : undefined}>{f.label}</Tag>)}
      {filters.length > 1 && onClearFilters && <Button size="sm" variant="ghost" onClick={onClearFilters}>Xóa tất cả bộ lọc</Button>}
      <span style={{ flex: 1 }} />
      {actions}
    </div>
  );
}
