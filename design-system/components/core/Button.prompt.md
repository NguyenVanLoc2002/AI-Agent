Primary action control; one primary per view region, secondary for alternatives, danger only for destructive confirmations.

```jsx
<Button variant="primary" iconLeft="plus" loading={saving}>Tạo báo giá</Button>
```

Variants: primary (blue), secondary (white + border), ghost, subtle (blue tint), danger. Sizes sm 28 / md 36 / lg 40. `loading` disables and shows a spinner — always set it while a request is in flight.
