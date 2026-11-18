Section switcher; line tabs for page sections, segmented for compact view toggles. Keyboard: ←/→, Home/End (roving tabindex). `variant="pill"` (dark active, 16:1 contrast) is for switching dashboard periods or chart views: "7 ngày / 30 ngày / Quý".

```jsx
<Tabs value={tab} onChange={setTab} tabs={[{value:"spec",label:"Thông số"},{value:"stock",label:"Tồn kho",count:4}]} />
```


