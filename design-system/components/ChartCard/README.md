Dashboard chart container: title, headline value, period pill-tabs, chart area, legend and data provenance, with loading / empty / error / restricted states built in.

```jsx
<ChartCard title="Giá trị báo giá" value="412,6 triệu ₫" periods={[{value:'7d',label:'7 ngày'},{value:'30d',label:'30 ngày'}]} period={p} onPeriodChange={setP}
  legend={[{label:'Kỳ này',color:'var(--chart-primary)'},{label:'Kỳ trước',color:'var(--chart-neutral)'}]}
  source={{ source: 'erp', time: '09:42' }} summary="Giá trị báo giá tăng 8,4% so với kỳ trước." state="ready">
  {/* your chart, drawn with --chart-* tokens */}
</ChartCard>
```

Chart color rule: one accent (`--chart-primary`) + neutrals for comparison; status colors only for status data. Always pass `summary` and a legend — never color-only encoding.
