# P2 · Application Layout & hạ tầng dùng chung

> **XONG 2026-11-26 (duyệt + commit, A-83)** · worktree `AI-Agent-fe`, nhánh `feat/FE-UI-P2-app-shell` từ `main` `9ced1ca` · A-83 · bằng chứng `../evidence/P2-quality-gate.md`.
> Khác kế hoạch: `PageState` không có trạng thái 409 (F6: 409 là toast cảnh báo); toast không tự đóng (D16); mock bật bằng Vite mode `mock` (D19); khung theo artboard F2–F5 (max 1192, padding 28/32, gap 20), không theo `AppShell` của batch A–E (chưa duyệt).

## Scope
Dựng khung SH-01 theo `AppShell.dc.html`, `R-1024/1280` và `InteractionSpec.dc.html`, cùng các mảnh dùng chung mà mọi page cần: định tuyến theo vai trò, trạng thái trang chuẩn, toast, lỗi/không quyền/không tìm thấy, xử lý hết phiên, lớp mock tách biệt. Màn hình F1–F5 **chưa** chuyển (P3); trong P2 chúng vẫn chạy qua cầu nối tạm để không gãy.

Ngoài scope: bảng Trợ lý AI trong khung (UX-01, BLOCKED), tìm nhanh trên header (SH-02, batch A), chuông thông báo (UX-10), cảnh báo 5 phút trước hết phiên (UX-12 OPEN), menu tài khoản ngoài "Đăng xuất" (AppHeader chưa có, `specs/92`).

## Tasks
| ID | Task | Phụ thuộc | Chi tiết |
|---|---|---|---|
| P2-T00 | Cài `react-router-dom` ghim chính xác (đề xuất 7.18.5, FE-UI-D02): kiểm peer React 18.3.1, `npm audit`, lockfile, bản build không inline script (CSP A-73) | P1 | Có blocker → báo trước khi đổi dependency |
| P2-T01 | Router (FE-UI-D01, D02): `HashRouter` + bảng route, không ID hội thoại/đơn trong URL (D04) | T00 | Route lạ → SH-04; focus về `h1` của `PageHeader` sau khi chuyển (quy tắc P1) |
| P2-T02 | Bảng route theo vai trò `routes.ts`: mã màn hình, nhãn sidebar, icon Lucide, vai trò hiển thị, route mặc định theo vai trò | T01 | OPS → `#/cong-tac-ai`, SALES → `#/hop-cho`, SECREV → `#/su-kien-bao-mat`, CUST → `#/chat`. Chú thích: chỉ để hiển thị |
| P2-T03 | Guard hiển thị: route ngoài vai trò → SH-03 (E4-Denied) tại chỗ, không gọi API của màn hình đó | T02 | Không phải kiểm soát quyền; test 403 từ backend vẫn ở page |
| P2-T04 | `StaffShell` (SH-01): Sidebar thu gọn được, AppHeader (breadcrumb, Đăng xuất, nhãn vai trò), vùng nội dung cuộn riêng, gutter theo token | T01, P1-T07 | Dưới 1280: sidebar tự thu gọn (T13 `specs/01`); dưới 1024: QĐ 8 |
| P2-T05 | `CustomerShell`: không sidebar, rộng tối đa theo F1, an toàn 390 px | T01 | Không cuộn ngang ở 390 |
| P2-T06 | `PageState`: loading (Skeleton), empty (EmptyState), error + `REQ-` + Thử lại, forbidden (xám + khóa), notFound ("Không tìm thấy", UX-16), timeout ("Chưa có thay đổi nào được ghi"), conflict (409) | P1 | Chữ theo `F6-StaffStates` + writing rule; nhận `ApiError` từ `client.ts` |
| P2-T07 | `ToastViewport` (SH-08) + `useToast()` | P1-T03 | "Đã …" chỉ sau phản hồi thành công; vùng live tồn tại sẵn; `useToast` chỉ nhận mã thông điệp cố định (`app/texts.ts`) + `REQ-`, không nhận `error.message`/thân phản hồi (review security L2); toast lỗi không tự đóng |
| P2-T08 | `ErrorBoundary` → SH-04 (E6-Error), không in stack/thông điệp gốc | T06 | Ghi log console chỉ ở dev, không nội dung |
| P2-T09 | Hết phiên (E5-Expired, E5-ExpiredMax, E5-Inflight): mọi 401 → xóa bộ nhớ phiên (`conversationMemory`, cache trang) → màn hình đăng nhập với thông báo; không tự gửi lại thao tác ghi | T01 | Mở rộng `onUnauthenticated` hiện có thành một nơi duy nhất |
| P2-T10 | Lớp mock (QĐ 5): `src/mocks/` theo kiểu `fakeBackend.ts`, chỉ endpoint ở §1.2 kế hoạch; bật bằng `VITE_APAS_MOCK=1` chỉ khi `import.meta.env.DEV` | T01 | Test: `dist/` không chứa chuỗi đánh dấu mock; mock dùng dữ liệu tổng hợp |
| P2-T11 | Cầu nối tạm: F1–F5 hiện tại render trong khung mới không đổi nội dung | T04, T05 | Gỡ ở P3 |
| P2-T12 | Cập nhật `frontend/README.md` (router, mock, layout) | T01–T11 | |
| P2-T13 | Áp quy tắc dùng component của P1 (`../DECISIONS.md`): skip link + `main` có `id`/`tabIndex=-1`; bảng trong vùng `role=region` focus được; trạng thái tải bằng `role=status` | T04, T06 | |
| P2-T14 | Playwright: viewport 390/1024/1280/1440, `forcedColors: 'active'`, ảnh focus mỗi component dùng trong khung, phím cuộn vùng bảng | T04 | Bù phần NOT RUN của P1 |

## Dependencies
P1 (Toast, PageHeader, Breadcrumb, layout.css, breakpoints) · P0 QĐ 1, 2, 4, 5, 8. Backend: không đổi nếu QĐ 1 = A.

## Acceptance Criteria
- Mỗi vai trò đăng nhập vào đúng route mặc định; sidebar chỉ hiện mục của vai trò.
- Mở trực tiếp route của vai trò khác → SH-03 tại chỗ, không có lời gọi API nghiệp vụ nào (kiểm bằng fake backend).
- Route không tồn tại → SH-04; lỗi render → SH-04 không lộ thông điệp lỗi.
- 401 ở bất kỳ API nào → màn hình đăng nhập với thông báo hết phiên; bộ nhớ hội thoại và giá đã xóa.
- Bố cục khớp artboard ở 1024/1280/1440 (nhân viên) và 390 (khách): không cuộn ngang trang; sidebar thu gọn dưới 1280.
- Điều hướng bằng bàn phím: skip link tới nội dung, focus về tiêu đề sau chuyển trang, Esc đóng lớp phủ.
- Bản build không chứa mã mock; CSP không đổi.

## Testing
- Unit/component (Vitest + Testing Library + axe): router (hash hợp lệ/lạ, back/forward), guard theo 4 vai trò × mọi route, `PageState` 7 trạng thái, toast, ErrorBoundary, 401 → đăng xuất.
- Test build: tìm chuỗi đánh dấu mock trong `dist/` → phải không có.
- E2E (Playwright, backend thật): sửa selector nếu đổi; thêm test deep link `/#/hop-cho` cho SALES và cho OPS (→ SH-03); `npm run e2e` toàn bộ.
- Lệnh: `npm run typecheck && npx vitest run && npm run build && npm run ds:verify`, sau đó `npm run e2e` theo `frontend/README.md`.

## Threat cases
- Người dùng sửa hash sang route vai trò khác: UI chặn hiển thị, backend vẫn 403 (test E2E hiện có `roles.spec.ts`).
- Mock lọt vào production: test build.
- Thông điệp lỗi gốc lọt ra UI qua ErrorBoundary: test.

## Rollback
Revert commit; không migration; backend không đổi.
