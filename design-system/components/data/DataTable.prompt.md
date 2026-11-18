Operational data table with sorting, row selection, skeleton loading and empty state.

```jsx
<DataTable selectable columns={[{key:"sku",header:"SKU",sortable:true},{key:"price",header:"Giá bán",align:"right",render:r=>fmtVND(r.price)}]} rows={rows} />
```

Right-align numbers (auto tabular). Pair with Pagination below and a filter toolbar above. Pass EmptyState into `emptyState`.
