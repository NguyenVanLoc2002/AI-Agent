Product search bar with search-type picker (VIN / OE / SKU / name), clear button, length validation and loading state.

```jsx
<SearchField onSubmit={(q, mode) => search(q, mode)} loading={searching} />
```

Enter submits, Esc clears. Code modes render in tabular Be Vietnam Pro. Pass `error` for server-side failures; `loading` blocks duplicate submits.
