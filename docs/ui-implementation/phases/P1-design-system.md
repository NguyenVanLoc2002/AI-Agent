# P1 · Nền tảng Design System trong app

> **Đạt quality gate 2026-11-26, chưa commit** · worktree `AI-Agent-fe`, nhánh `feat/FE-UI-P1-design-system` · bằng chứng `../evidence/P1-quality-gate.md`.
> Khác kế hoạch: chép thêm `Checkbox` (dependency của DataTable, D10); `Tooltip` (D11) và `DataTable` (D14) là bản sao đã sửa vì a11y; test CSS ở `config/foundationCss.test.ts` (cần kiểu Node); `ds:verify` bắt thêm file chép không khai báo; P1-T09 (trang tham chiếu) không làm; `money.ts` (P1-T08) chưa rà — chuyển P3.

## Scope
Đưa đủ phần design system mà P2–P4 cần vào `frontend/src/design-system/`, theo quy tắc bản sao đã có (decision 3A, `sources.json`, `npm run ds:verify`), và thêm nền CSS/breakpoint cho layout. Không đổi giao diện màn hình nào trong phase này.

Ngoài scope: component chỉ batch A–E dùng (SearchField, Drawer, AIPanel, ActionConfirmation, ERPResult, SourceTag, MetricCard, ChartCard, AvatarGroup) — chép ở P5 khi mở khóa.

## Đầu vào bắt buộc đọc
`design-system/README.md`, `EXPORT-NOTES.md`, `CHANGELOG.md`, `guidelines/writing.md`, `tokens.css`, `components/<nhóm>/<Tên>.jsx|.d.ts|.prompt.md` và `components/<Tên>/README.md` của từng component được chép.

## Tasks
| ID | Task | Phụ thuộc | Chi tiết |
|---|---|---|---|
| P1-T01 | Đối chiếu `tokens.css`, `bundle.css`, font trong app với `design-system/` | P0 | `ds:verify` xanh; ghi khác biệt nếu có |
| P1-T02 | Export `Field`, `Breadcrumb` có sẵn trong `index.ts` | — | Không sửa nội dung file |
| P1-T03 | Chép `Toast` (+ `.d.ts`) | T01 | Review: `role`/`aria-live`, thời gian tự đóng, toast lỗi không tự đóng |
| P1-T04 | Chép `PageHeader`, `Tabs`, `Tag`, `Card`, `Tooltip`, `DataTable` | T01 | Review từng file như mã sản phẩm: không `dangerouslySetInnerHTML`, không `localStorage`, chỉ import `react`; ARIA tabs + phím mũi tên; tooltip qua bàn phím |
| P1-T05 | Ghi hash vào `sources.json`; bản nào phải sửa → `modified` + `copySha256` + lý do | T03, T04 | Không sửa nếu không bắt buộc |
| P1-T06 | `src/app/breakpoints.ts`: `CUSTOMER_MIN=390`, `STAFF_MIN=1024`, `PANEL_PUSH=1280`, `WIDE=1440` (QĐ 8) | P0 | Chú thích nguồn artboard |
| P1-T07 | `src/app/layout.css` (QĐ 3): class khung, lưới, gutter 28–32, chỉ dùng `var(--…)` | T06 | Test chặn mã màu/px cứng ngoài danh sách cho phép |
| P1-T08 | Rà `money.ts`/định dạng thời gian theo writing rule (VND `1.250.000 ₫`, `16/11/2026 09:42`, "5 phút trước") | — | Bổ sung test nếu thiếu |
| P1-T09 | Trang tham chiếu component chỉ cho dev/test (không vào bản build) — tùy chọn | T04 | Dùng để so ảnh với `preview.html` |

## Dependencies
P0 (QĐ 3, 8). Không phụ thuộc backend.

## Acceptance Criteria
- Mọi component mới có `.jsx` + `.d.ts`, hash đúng trong `sources.json`, `npm run ds:verify` exit 0.
- Không có màu, cỡ chữ, khoảng cách hard-code trong `layout.css` (chỉ token); test kiểm tự động.
- Mỗi component mới có ít nhất một test render + axe không vi phạm; Tabs và Tooltip có test bàn phím.
- Không màn hình nào đổi hành vi: 224 unit test cũ vẫn xanh.
- Bản build không có inline script/style mới (CSP A-73 không đổi).

## Testing
```sh
cd frontend
npm run ds:verify
npm run typecheck
npx vitest run
npm run build
```
E2E: NOT RUN trừ khi phase đổi DOM của màn hình hiện có (không dự kiến).

## Rollback
Revert commit; chỉ thêm file và export, không migration.
