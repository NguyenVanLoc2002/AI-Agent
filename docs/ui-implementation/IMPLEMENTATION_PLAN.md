# Kế hoạch triển khai Frontend theo thiết kế Claude Design

> 2026-11-26 · Kế hoạch **được chủ dự án duyệt 2026-11-26** (A-80, chi tiết `DECISIONS.md`). Nội dung thiết kế vẫn là DESIGN PROPOSAL. P1 đã triển khai trên worktree `AI-Agent-fe`, nhánh `feat/FE-UI-P1-design-system` (chưa commit) — bằng chứng `evidence/P1-quality-gate.md`.
> Thứ tự ưu tiên vẫn theo `CLAUDE.md`: quyền hệ thống → ADR và `docs/contracts/` → BRD → tài liệu khác. Thiết kế (`design-system/`, `docs/design/`) không thắng ADR, contract hay `docs/security/RBAC_MATRIX.md`; điểm lệch ở `docs/DECISIONS_REQUIRED.md` §UX.
>
> AutoParts Agent Sandbox is an independent technical demonstration project using synthetic business scenarios and mock ERP data. It is not affiliated with, endorsed by, or commissioned by any automotive parts business or ERP vendor.

Tài liệu cùng thư mục: `DECISIONS.md` (quyết định + quy tắc dùng component) · `SCREEN_INVENTORY.md` (page, route, component, phụ thuộc) · `PROGRESS.md` (checklist) · `CONFLICT_RESOLUTION_MATRIX.md` (workstream W-BIZ cho batch A–E) · `evidence/` (bằng chứng test) · `phases/` (chi tiết từng phase).

## 1. Hiện trạng (khảo sát 2026-11-26)

### 1.1 Frontend đã có (`frontend/`)
- React 18.3.1 (từ FE-REACT19, 2026-11-26: React 19.3.1) + TypeScript + Vite 8, Vitest + Testing Library + axe-core, Playwright với backend thật (A-71..A-75). Không router, không thư viện state, không CSS riêng: layout bằng `style={{}}` dùng token.
- Màn hình đã làm (batch F, đã duyệt 23/11/2026): F1 CH-01..04 chat khách (EP-10b), F2 HO-01 + F3 HO-02 hộp chờ/hội thoại (EP-10c), F4 SI-01 Security Inspector (EP-10d), F5 OP-01 công tắc AI (EP-10a), đăng nhập + khung theo vai trò (EP-10a).
- Dùng lại được: `api/client.ts` (CSRF, problem+json → `code` + `traceId`), `app/texts.ts` (`failureText`, `referenceText`), `app/money.ts`, `app/staffLabels.ts` (16 `HandoffReason`, nhãn quote UX-06), `app/inspectorLabels.ts`, `test/fakeBackend.ts`, toàn bộ `e2e/`.
- Design system trong app: 20 trên 41 component đã chép vào `frontend/src/design-system/` (hash ở `sources.json`, `npm run ds:verify`); 2 bản đã sửa có review (`Icon.jsx` A-66, `AIComposer.jsx` A-68). Danh sách đủ: `SCREEN_INVENTORY.md` §3.

### 1.2 Backend: API có thật (không có API nào khác)
| Vùng | Endpoint | Vai trò |
|---|---|---|
| Phiên | `GET /api/auth/csrf`, `GET /api/auth/session`, đăng nhập/đăng xuất | mọi vai trò |
| Chat khách | `POST /api/demo/conversations`, `GET /api/demo/conversations/{id}` (state), `GET/POST …/{id}/messages` | CUST |
| Báo giá khách | `GET /api/demo/quotes/{quoteRef}`, `POST …/{quoteRef}/confirm` | CUST |
| Hộp chờ | `GET /api/internal/handoffs`, `POST …/{id}/claim`, `POST …/{id}/resolve` | SALES |
| Hội thoại NV | `GET/POST /api/internal/conversations/{id}/messages`, `GET …/{id}/quote` | SALES (đang giữ) |
| Đơn hàng NV | `GET /api/internal/orders/awaiting-decision`, `POST …/{requestId}/staff-decision`, `GET …/orders/unknown`, `POST …/{requestId}/reconcile` | SALES team khách |
| Inspector | `GET /api/demo/security/events`, `GET …/traces/{traceId}` | SECREV |
| Công tắc AI | `/api/ops/kill-switch` | OPS |

### 1.3 Khoảng cách giữa thiết kế và nguồn chuẩn
Bộ thiết kế có khoảng 180 artboard (batch A–F). Chỉ **batch F** đã được phê duyệt và có API. Batch A–E là không gian làm việc của nhân viên (tra cứu phụ tùng, Trợ lý AI cho nhân viên, lập báo giá, tạo đơn nháp, tổng quan, nhật ký, hồ sơ):
- **không có endpoint** nào trong backend (không có tra cứu danh mục cho nhân viên, danh sách báo giá, soạn báo giá, tổng quan, nhật ký nghiệp vụ, hồ sơ);
- **xung đột chưa giải quyết** UX-01, 02, 03, 04, 08, 09, 10, 11, 12 (một phần), 14, 15, 16, 18;
- nhiều câu hỏi nghiệp vụ còn mở (R4 VAT, E1/E2 bản lưu, R9/AI4 timeout, Q2 hạn báo giá…).

Hệ quả: kế hoạch **không** dựng batch A–E bằng mock tự đặt ra, vì như vậy là tự định nghĩa nghiệp vụ và API (`CLAUDE.md` §Precedence 5, rule 00). Mock chỉ được dùng khi đã có contract nháp được duyệt (xem P5, quyết định 7).

## 2. Nguyên tắc triển khai
1. **Bám thiết kế gốc**: layout, chữ, màu, khoảng cách, icon, tương tác lấy từ artboard + spec của màn hình; chỉ dùng token (`tokens.css`) và component design system; Be Vietnam Pro; Lucide 0.460.0; chữ tiếng Việt theo `design-system/guidelines/writing.md`. Không redesign. Chỗ thiết kế trái ADR/contract/RBAC: làm theo nguồn chuẩn và ghi lại, không tự sáng tác giải pháp thứ ba.
2. **Backend là thẩm quyền**: ẩn/khóa trên UI chỉ để hiển thị; mọi lời gọi vẫn được backend kiểm tra. Không sao chép chính sách nghiệp vụ vào frontend.
3. **Không lộ dữ liệu**: không lưu giá, token, nội dung tin vào `localStorage`; lỗi chỉ hiện thông điệp cố định + `REQ-xxxxxxxx`; tài nguyên của người khác hiện "Không tìm thấy" (UX-16).
4. **Đủ trạng thái** cho mỗi page: đang tải, rỗng, lỗi, quá thời gian, không có quyền (403, xám + khóa, không đỏ), không tìm thấy (404), xung đột (409), validation, chưa rõ kết quả, AI tắt — theo `F6-StaffStates`/`F1-SendStates` và ma trận trạng thái của batch.
5. **Responsive theo thiết kế**: khách 390 px trở lên (F1); nhân viên 1024 / 1280 / 1440 (R-1024, R-1280, RB-1280; panel/drawer đổi hành vi ở 1280).
6. **Một issue = một lát dọc** có test; mỗi phase đi qua `.claude/skills/implement-issue` + `ui-from-claude-design`; review `ux-workflow-reviewer`, `security-auditor`, `qa-gatekeeper` (advisory).
7. **Bằng chứng thật**: chỉ ghi VERIFIED khi lệnh đã chạy và đã xem exit code; còn lại NOT RUN kèm lý do.

## 3. Lộ trình

```
P0 Chốt điều kiện ──► P1 Design System ──► P2 App Layout & hạ tầng ──► P3 Chuyển F1–F5 vào khung
                                                     │                          │
                                                     ├──► P4 Đơn hàng NV (EP-10e) [cổng: thiết kế + duyệt]
                                                     └──► P5 Batch A–E [BLOCKED: UX-xx + contract]
P6 Chất lượng xuyên suốt (a11y, responsive, E2E, bảo mật) — chạy cuối mỗi phase, tổng kiểm ở cuối
W-BIZ (song song, chỉ tài liệu): giải quyết UX-xx của A–E theo nhóm → mở khóa từng phase con P5-x
```

| Phase | Tên | Phụ thuộc | Trạng thái | Tài liệu |
|---|---|---|---|---|
| P0 | Chốt điều kiện sẵn sàng (quyết định, hợp đồng, nhánh) | — | XONG 2026-11-26 (A-80) | `phases/P0-readiness.md` |
| P1 | Nền tảng Design System trong app | P0 (QĐ 3, 8) | XONG 2026-11-26 (`0bac97f`, A-81) | `phases/P1-design-system.md` |
| P2 | Application Layout & hạ tầng dùng chung | P1, P0 (QĐ 1, 2, 4, 5) | XONG 2026-11-26 (A-84) | `phases/P2-app-shell.md` |
| P3 | Đưa F1–F5 và đăng nhập vào khung mới | P2 | Sẵn sàng sau P2 | `phases/P3-batch-f-migration.md` |
| P4 | Đơn hàng của nhân viên: chờ duyệt, đối soát (EP-10e) | P2, thiết kế được duyệt (QĐ 6) | GATED | `phases/P4-staff-orders.md` |
| P5 | Batch A–E: tra cứu, Trợ lý NV, báo giá, đơn nháp, hỗ trợ | P2, UX-xx + contract (QĐ 7) | BLOCKED | `phases/P5-blocked-batches.md` |
| P6 | Chất lượng xuyên suốt và nghiệm thu | mỗi phase | Liên tục | `phases/P6-quality.md` |
| W-BIZ | Giải quyết xung đột UX batch A–E theo nhóm (chỉ tài liệu) | chủ dự án | Song song, chờ quyết định | `CONFLICT_RESOLUTION_MATRIX.md` |

Ước lượng (thô, một người, chưa gồm review): P1 2–3 ngày · P2 3–4 ngày · P3 2–3 ngày · P4 2–3 ngày sau khi có thiết kế · P5 không ước lượng được khi còn BLOCKED.

## 4. Quyết định (đã duyệt 2026-11-26: 1A 2B 3A 4A 5A 6A 7A 8A)
Kết quả: `DECISIONS.md` FE-UI-D01..D14 và A-80. Lưu ý **D02 = B** (`react-router-dom` `HashRouter`, cài ở P2). Bảng dưới giữ nguyên phương án đã trình để truy vết.

1. **Kiểu đường dẫn.** ⭐ A: hash route (`/#/inbox`) — backend chỉ phục vụ `/`, `/index.html`, `/assets/**` (`FrontendPaths.PATTERNS`, A-74), nên không phải đổi cấu hình bảo mật. B: path route (`/inbox`) — phải thêm SPA fallback vào `FrontendPaths`/`SecurityConfiguration` + IT mới.
2. **Router.** ⭐ A: router tối thiểu tự viết (~12 route, không thêm phụ thuộc, dễ review). B: `react-router-dom` ghim phiên bản (thêm phụ thuộc, `npm audit`).
3. **Cách viết layout.** ⭐ A: thêm file CSS theo token (`src/app/*.css`, class + media query) cho khung và lưới responsive; component vẫn từ design system. B: giữ `style={{}}` + hook `matchMedia` (khó làm responsive, trùng lặp).
4. **ID trong URL của màn hình nhân viên** (F3, P4). ⭐ A: không đưa `handoffId`/`conversationId`/`requestId` vào URL; mở từ danh sách, giữ trong bộ nhớ (như hiện tại), tải lại trang → về danh sách. B: đưa vào URL (lộ trong lịch sử trình duyệt; backend vẫn trả 404 cho người khác).
5. **Mock.** ⭐ A: mock chỉ trong `src/mocks/`, chỉ cho test và chế độ dev bật bằng `VITE_APAS_MOCK=1`; chỉ mô phỏng endpoint **đã có contract**; bản build production có test chặn mã mock. B: thêm MSW (phụ thuộc mới, service worker — CSP hiện có `worker-src 'none'`).
6. **Thiết kế cho P4 (EP-10e).** ⭐ A: vẽ bổ sung trên Claude Design một "batch G" nhỏ chỉ dùng trường `AwaitingView`/`UnknownView`/`DecidedView`/`ReconciledView`, như cách làm batch F, rồi duyệt. B: dựng từ artboard D1/D4 lược bỏ phần trái contract (UX-03, UX-08, UX-10) — rủi ro tự redesign.
7. **Batch A–E.** ⭐ A: giữ BLOCKED; mở từng nhóm khi có (i) quyết định UX-xx liên quan, (ii) use case, (iii) contract nháp trong `docs/contracts/` được duyệt — sau đó mới dựng UI mock-first trên contract đó. B: dựng ngay trên mock tự đặt (trái `CLAUDE.md` §Precedence 5).
8. **Breakpoint và bề rộng tối thiểu.** Design system không có token breakpoint. ⭐ A: hằng số lấy từ thiết kế (390, 1024, 1280, 1440) trong `src/app/breakpoints.ts` + CSS, ghi DESIGN PROPOSAL; màn hình nhân viên dưới 1024 px: sidebar thu gọn và bảng cuộn ngang trong khung (chưa có artboard). B: chờ vẽ thêm artboard dưới 1024 trước khi làm.

## 5. Ngoài phạm vi
- Kênh Zalo thật (D-06, UX-14), logo (A-17 PROPOSED), dashboard SEC (SEC-DASH BLOCKED), cảnh báo 5 phút trước hết phiên, khóa tài khoản, đổi mật khẩu (UX-12 phần còn OPEN), SSO.
- Thay đổi backend: không thuộc kế hoạch này; nếu một phase cần endpoint mới thì tách issue backend riêng, có contract trước.

## 6. Rủi ro
| Rủi ro | Ảnh hưởng | Giảm thiểu |
|---|---|---|
| Phần lớn thiết kế (A–E) không làm được trong PoC | Kỳ vọng "đủ bộ UI" không đạt | Nêu rõ ở P5; ưu tiên batch F + EP-10e; danh sách điều kiện mở khóa |
| Chép thêm component design system mang theo lỗi như `Icon.jsx` (innerHTML) | XSS, vi phạm CSP | Review từng file như mã sản phẩm, `ds:verify`, axe, test chèn HTML |
| Refactor F1–F5 làm hỏng hành vi đã có E2E | Hồi quy bảo mật/UX | P3 không đổi hành vi; chạy lại 224 unit test + 29 E2E |
| Khung mới đổi DOM làm hỏng selector E2E | E2E đỏ | Giữ role/label truy cập; sửa selector trong cùng issue |
| Route ẩn bị hiểu là kiểm soát quyền | Hiểu sai bảo mật | Guard chỉ để hiển thị; test 403/404 từ backend vẫn bắt buộc |
