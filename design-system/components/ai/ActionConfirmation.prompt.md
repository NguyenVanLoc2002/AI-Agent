Human-in-the-loop card: the AI proposes an action and the user must explicitly confirm or reject.

```jsx
<ActionConfirmation title="Thêm 2 sản phẩm vào báo giá BG-2026-0412" items={[{label:"Má phanh trước · 04465-0D130",value:"x2"}]} onConfirm={add} />
```

Never imply the AI acted on its own. Use for adding lines, creating drafts, sending quotations.
