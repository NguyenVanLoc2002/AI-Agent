White bordered container for grouped content on dashboards and detail pages.

```jsx
<Card title="Tồn kho theo chi nhánh" actions={<Button size="sm" variant="ghost">Xem tất cả</Button>}>…</Card>
```

Border + shadow-xs, radius 10, padding 20. Footer gets a subtle gray band.

Variants: `elevated` for dashboard KPI/chart cards (shadow-card, padding 24); `nested` for a sub-panel inside another card (bg-nested, no border). Max two elevation levels per region.
