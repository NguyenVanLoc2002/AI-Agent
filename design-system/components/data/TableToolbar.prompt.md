Search + filter + bulk-action bar for DataTable; pass it to DataTable's `toolbar` slot.

```jsx
<DataTable toolbar={<TableToolbar search={{ value: q, onChange: setQ, placeholder: 'Tìm mã đơn, khách hàng…' }} filters={[{ key: 'brand', label: 'Hãng: Toyota' }]} onRemoveFilter={rm} onAddFilter={openFilters} selectedCount={sel.length} bulkActions={<Button size="sm" variant="secondary">Xuất danh sách</Button>} onClearSelection={() => setSel([])} />} … />
```

Switches to bulk mode when `selectedCount > 0`. Filtering logic stays in your data layer.
