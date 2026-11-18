Overlapping initials cluster for assignees / approvers on quotations, draft orders and audit rows.

```jsx
<AvatarGroup label="Người duyệt" people={[{ name: 'Nguyễn Minh Anh', role: 'Kinh doanh' }, { name: 'Trần Quốc Bảo', role: 'Trưởng nhóm' }]} />
```

Initials only (no photos), blue/neutral fills — never status or provenance colors. Circles 28px (sm) / 36px (md), 2px white ring, 1px (sm) / 4px (md) overlap — overlap + ring always smaller than the side padding of two initials, each later avatar stacks above the previous one so initials and the "+N" circle (always last, topmost) stay fully readable, "+N" overflow; full names are exposed via aria-label and title.
