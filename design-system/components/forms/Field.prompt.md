Label + control + helper/error wrapper; use around any custom control so forms stay consistent.

```jsx
<Field label="Khách hàng" required error="Vui lòng chọn khách hàng"><MyPicker /></Field>
```

Input, Select and Textarea already include Field — do not double wrap.
