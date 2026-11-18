Per-value provenance label — attach to any single price, stock figure or field value to say where it came from.

```jsx
<span>1.250.000 ₫ <SourceTag source="erp" time="09:42" /></span>
<SourceTag source="ai" variant="inline" />
```

Rules: ERP values without a timestamp are not "verified". AI-suggested values always carry `source="ai"` and are never saved automatically — the user must accept them.
