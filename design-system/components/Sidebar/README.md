Collapsible left navigation (248 → 64px) with grouped module links.

```jsx
<Sidebar activeKey="lookup" collapsed={c} onToggleCollapsed={()=>setC(!c)} sections={[{title:"Vận hành",items:[{key:"lookup",label:"Tra cứu phụ tùng",icon:"search"}]}]} />
```

Active item = soft blue fill (--bg-selected) + blue icon + 600 weight; no left-border bar, no shadow. `locked` shows a lock (presentation only — backend still authorizes).
