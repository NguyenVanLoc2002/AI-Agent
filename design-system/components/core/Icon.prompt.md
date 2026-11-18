Renders a Lucide icon (inline SVG) from the bundled set; the only icon source allowed in this system.

```jsx
<Icon name="search" />                         {/* 20px standard */}
<Icon name="shield-check" size={16} />         {/* dense table / compact control */}
<Icon name="circle-alert" size={14} />         {/* inline with 12px helper text */}
```

Sizes: **20** standard (nav, page/section headers, empty states, standalone), **16** dense tables, buttons ≤36px, inputs, tabs, tags; **14** only beside 12px text (badges, field messages). Stroke **2**. Color = `currentColor`. Decorative icons are `aria-hidden`; meaningful standalone icons need `title`. Gap icon→label 8px (6px in compact controls). Names: `ICON_NAMES`, files in `assets/icons/`.
