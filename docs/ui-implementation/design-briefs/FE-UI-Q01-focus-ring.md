# Brief cho Claude Design · FE-UI-Q01 · Chỉ báo focus đủ tương phản

> 2026-11-26 · Chủ dự án chọn phương án A ("theo đề xuất của bạn"): sửa ở design system trên Claude Design, sau đó chép lại vào frontend. Frontend **không** tự đổi token (`DECISIONS.md` FE-UI-Q01). Brief này là yêu cầu thiết kế, không phải thiết kế đã duyệt.

## Vấn đề
- `--focus-ring: 0 0 0 3px rgba(37,99,235,.22)` (`design-system/tokens.css`) là quầng xanh 22 % trên nền trắng, độ tương phản khoảng **1,3:1**, thấp hơn **3:1** mà WCAG 2.2 yêu cầu cho thành phần giao diện và trạng thái (1.4.11; tham chiếu thêm 2.4.13).
- 14 component gỡ `outline` và chỉ dựa vào quầng này: `ai/AIComposer`, `core/Button`, `core/IconButton`, `data/Pagination`, `data/Tag`, `forms/Checkbox`, `forms/Input`, `forms/Radio`, `forms/SearchField`, `forms/Select`, `forms/Switch`, `forms/Textarea`, `navigation/Sidebar`, `navigation/Tabs`. Ô nhập có thêm `--border-focus` nên đỡ hơn; nút, tab, thẻ lọc, công tắc thì không.
- Ở chế độ tương phản cao của Windows (forced colors), `box-shadow` bị bỏ nên mất hẳn chỉ báo focus. Frontend đã vá tạm bằng một khối `@media (forced-colors: active)` trong `frontend/src/app/foundation.css`.
- Bằng chứng: review `ux-workflow-reviewer` P1 (H1), `docs/ui-implementation/evidence/P1-quality-gate.md`. Con số tương phản là ước tính, chưa đo bằng công cụ trên trình duyệt.

## Yêu cầu
1. Chỉ báo focus của **mọi** control tương tác đạt ≥ 3:1 so với màu liền kề, trên cả nền `--bg-surface` (trắng) và `--bg-app` (`#F8F9FA`), và trên nút primary (nền `--action-primary`).
2. Vẫn hiện ở chế độ forced colors (ví dụ dùng `outline` thay cho hoặc kèm với `box-shadow`).
3. Giữ ngôn ngữ hình ảnh hiện tại: một màu hành động (blue 600), bo góc theo token, không đổi kích thước control, không làm xô lệch bố cục.
4. Có biến thể cho trạng thái lỗi (thay `--focus-ring-error`) cũng đạt ≥ 3:1.
5. Chỉ hiện khi focus bằng bàn phím (`:focus-visible`, như `useFocusVisible` hiện có).

## Kết quả cần giao
- Token mới hoặc token sửa (giá trị + mục `usage` trong `tokens.json`), cập nhật `tokens.css`.
- Mã nguồn các component bị ảnh hưởng (`components/<nhóm>/*.jsx`) và mục `CHANGELOG.md`.
- Một artboard minh họa focus của từng loại control trên ba nền nói trên, và một ảnh ở chế độ forced colors.
- Cập nhật dòng "Focus →" trong `design-system/README.md` (Interaction, State).

## Sau khi bản thiết kế được duyệt (việc của frontend, issue riêng)
- Xuất lại `design-system/`; chép các file đã đổi vào `frontend/src/design-system/` theo `sources.json`. Riêng bốn bản đã sửa (Icon, AIComposer, Tooltip, DataTable) thì gộp tay thay đổi và review lại.
- Bỏ phần vá tạm trong `foundation.css` nếu không còn cần.
- Kiểm tra bằng Playwright: ảnh focus của từng component, `forcedColors: 'active'`, đo tương phản bằng công cụ (P2-T14).

## Ngoài phạm vi
Mọi thay đổi khác về màu, chữ, khoảng cách hay bố cục.
