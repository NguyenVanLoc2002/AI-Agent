Status / category / provenance label; always text + icon or dot so meaning never relies on color alone.

```jsx
<Badge shape="pill" tone="success">Còn hàng</Badge>      {/* record status */}
<Badge tone="erp">Đã xác minh ERP</Badge>                 {/* provenance */}
<Badge tone="info" icon={false}>Chính hãng</Badge>        {/* category */}
```

Shape rule: **pill = trạng thái bản ghi** (đơn, báo giá, tồn kho) with a dot; **rounded 6px = phân loại / nguồn dữ liệu**. Tones: neutral, info, success, warning, error, restricted, ai, erp.
