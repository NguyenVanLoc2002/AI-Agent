Message input for the AI panel with context chips, quick prompts and a reliability disclaimer.

```jsx
<AIComposer context={["Toyota Vios 2019"]} suggestions={["Kiểm tra tồn kho","Tìm mã thay thế"]} onSubmit={ask} />
```

Enter sends, Shift+Enter newline. `busy` blocks duplicate sends.
