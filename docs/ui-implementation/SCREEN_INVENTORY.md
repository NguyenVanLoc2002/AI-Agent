# Danh mục màn hình, route, component và phụ thuộc

> 2026-11-26 · DESIGN PROPOSAL. Route là đề xuất (quyết định 1, 2, 4 ở `IMPLEMENTATION_PLAN.md` §4); ví dụ viết theo hash route. Ánh xạ UC lấy từ `docs/design/specs/90-danh-muc-man-hinh.md` §UC; xung đột từ `docs/DECISIONS_REQUIRED.md` §UX.

Trạng thái: **DONE** = đã có mã + test (trước kế hoạch này) · **READY** = có thiết kế đã duyệt và API · **GATED** = có API, thiếu thiết kế được duyệt · **BLOCKED** = thiếu API/UC hoặc xung đột UX-xx chưa giải quyết · **OUT** = ngoài phạm vi PoC.

## 1. Khung ứng dụng và màn hình dùng chung

| Mã | Màn hình | Route đề xuất | Vai trò | Thiết kế | API | Trạng thái | Phase | Ghi chú / chặn bởi |
|---|---|---|---|---|---|---|---|---|
| SH-01 | Khung: sidebar thu gọn, header, vùng làm việc | (bao mọi route nhân viên) | SALES, SECREV, OPS | `AppShell.dc.html`, `R-1024/1280` | — | READY (đang có bản đơn giản) | P2 | Bảng Trợ lý AI trong khung: BLOCKED (UX-01) — không dựng |
| SH-01c | Khung khách (không sidebar, 390 px) | `#/chat` | CUST | `F1-Chat.dc.html` | — | DONE (EP-10b) | P3 | Chuyển vào layout mới |
| SH-02 | Tìm nhanh SKU/OE/VIN trên header | — | — | `AppShell` | không có | BLOCKED | P5 | Thuộc batch A |
| SH-03 / E4 | Không có quyền truy cập | (hiện ngay tại route bị chặn, không route riêng) | mọi vai trò | `E4-Denied.dc.html` | — | READY | P2 | Nút "Yêu cầu cấp quyền" (R10) là điểm nối: OUT trong PoC |
| SH-04 / E6 | Lỗi hệ thống / không tìm thấy trang | `*` | mọi vai trò | `E6-Error.dc.html` | — | READY | P2 | |
| SH-05 | Băng ERP gián đoạn toàn cục | — | — | `A5-ErpDown` | không có tín hiệu toàn cục | BLOCKED | P5 | |
| SH-06 / E7 | Đăng nhập | (khi chưa có phiên) | mọi vai trò | `E7-Login.dc.html`, `E7-LoginError` | Spring form login | DONE (EP-10a), chưa đối chiếu E7 | P3 | `E7-LoginLocked`: OUT (UX-12 khóa tài khoản OPEN) |
| SH-07 / E5 | Phiên hết hạn | (lớp phủ) | mọi vai trò | `E5-Expired`, `E5-ExpiredMax`, `E5-Inflight` | 401 từ mọi API | READY một phần | P2 | `E5-Warning` (5 phút trước): OUT (UX-12 OPEN). 30 phút/8 giờ do backend (ADR-010) |
| SH-08 | Toast kết quả thao tác | (viewport toàn cục) | mọi vai trò | `InteractionSpec.dc.html` | — | READY | P2 | Cần chép `Toast` |

## 2. Màn hình nghiệp vụ

### 2.1 Batch F (đã duyệt 23/11/2026, `specs/50`)
| Mã | Màn hình | Route đề xuất | Vai trò | UC | API | Artboard | Trạng thái | Phase |
|---|---|---|---|---|---|---|---|---|
| F1 CH-01 | Chat khách + thẻ báo giá | `#/chat` | CUST | UC-01, UC-03/04 | conversations, messages, quotes | `F1-Chat` | DONE (EP-10b) | P3 |
| F1 CH-02 | Chờ nhân viên tư vấn | `#/chat` | CUST | UC-05 | `GET conversations/{id}` state | `F1-Handoff` | DONE | P3 |
| F1 CH-03 | Trạng thái báo giá phía khách | `#/chat` | CUST | UC-04 | `POST quotes/{ref}/confirm` | `F1-QuoteStates` | DONE | P3 |
| F1 CH-04 | Trạng thái gửi tin | `#/chat` | CUST | UC-01 | messages | `F1-SendStates` | DONE | P3 |
| F2 HO-01 | Hộp chờ nhân viên | `#/hop-cho` | SALES | UC-05/06 | handoffs, claim | `F2-Inbox`, `F6-StaffStates` | DONE (EP-10c) | P3 |
| F3 HO-02 | Hội thoại đang xử lý | `#/hop-cho` (mở trong bộ nhớ, QĐ 4) | SALES | UC-06 | internal messages, quote, resolve | `F3-Conversation` | DONE | P3 |
| F4 SI-01 | Security Inspector | `#/su-kien-bao-mat` | SECREV | UC-07 | security events, traces | `F4-Inspector` | DONE (EP-10d) | P3 |
| F5 OP-01 | Công tắc trợ lý AI | `#/cong-tac-ai` | OPS | UC-08 | kill-switch | `F5-KillSwitch` | DONE (EP-10a) | P3 |

### 2.2 Đơn hàng của nhân viên (EP-10e)
| Mã | Màn hình | Route đề xuất | Vai trò | UC | API | Thiết kế | Trạng thái | Phase |
|---|---|---|---|---|---|---|---|---|
| G1 (tạm) | Đơn chờ ghi quyết định duyệt | `#/don-hang/cho-duyet` | SALES team khách | UC-04 | `GET orders/awaiting-decision`, `POST …/staff-decision` | chưa có (D1/D4 lệch UX-03, UX-10) | GATED | P4 |
| G2 (tạm) | Đơn chưa rõ kết quả ERP — đối soát | `#/don-hang/doi-soat` | SALES team khách | UC-04 | `GET orders/unknown`, `POST …/reconcile` | chưa có (D3 lệch UX-08) | GATED | P4 |

### 2.3 Batch A–E (thiết kế 16–17/11/2026, chưa phê duyệt)
| Mã | Màn hình | Route dự kiến | UC / BR | API cần | Xung đột / câu hỏi mở | Trạng thái |
|---|---|---|---|---|---|---|
| A1–A7 PL-01 | Tra cứu phụ tùng (SKU, OE, VIN, tên) + trạng thái | `#/tra-cuu` | UC-02 (phía NV chưa có UC); BR-04..07 | tra cứu danh mục cho NV | UX-13, UX-15; E1, E2, R9 | BLOCKED |
| PL-02 | Xác định xe từ VIN | `#/tra-cuu` | như trên | giải mã VIN (`vinRef`) | UX-01 (VIN không là tham số tool), B3 fixtures | BLOCKED |
| A2 PL-03 | Chi tiết phụ tùng (drawer + trang) | `#/tra-cuu` + drawer; trang `#/phu-tung` | như trên | chi tiết sản phẩm | UX-15 | BLOCKED |
| PL-04 | Giá & tồn theo chi nhánh | (trong PL-03) | BR-34 OPEN | `priceFor` cần khách đã xác thực | UX-15 (chỉ mức khả dụng) | BLOCKED |
| PL-05 | Mã tương đương (gợi ý AI) | (trong PL-03) | — | — | R7, UX-01 | BLOCKED |
| A8 PL-06 | Thêm vào báo giá | — | UC-03 | — | UX-04 | BLOCKED |
| B1–B9 AI-01..03 | Không gian / bảng Trợ lý AI cho nhân viên | `#/tro-ly` + panel | chưa có UC | agent cho nhân viên | UX-01, UX-13, UX-16, UX-17; AI2, AI4 | BLOCKED |
| C1 QT-01 | Danh sách báo giá | `#/bao-gia` | UC-03; BR-14..17 | danh sách quote cho NV | UX-06 (nhãn đã duyệt), UX-07, UX-09, UX-14 | BLOCKED |
| C2/C3 QT-02/09 | Chi tiết, tính giá, phát hành & gửi | `#/bao-gia` (chi tiết trong bộ nhớ) | UC-03 | soạn/tính giá/phát hành | UX-04, UX-09, UX-14 | BLOCKED |
| C4 QT-04 | Lịch sử phiên bản | (trong C2) | UC-03 | lịch sử quote | UX-06 | BLOCKED |
| C5 | Soạn báo giá, tra cứu khách | — | chưa có UC | `searchCustomers` (không có) | UX-02, UX-04 | BLOCKED (OUT theo safe default) |
| C6 | Trợ lý lập báo giá | — | UC-03 (AGENT) | — | UX-01, UX-07 | BLOCKED |
| C7 | Khách xác nhận / kiểm tra trước tạo đơn | — | UC-04 | — | UX-05, UX-06 | BLOCKED (phía khách đã có ở F1) |
| D1–D3 DO-01..05 | Danh sách, xem lại, NV tạo đơn trên ERP | `#/don-hang-nhap` | UC-04 | — | UX-03 (SALES không tạo đơn), UX-08, UX-18 | BLOCKED (phần hợp lệ chuyển sang P4) |
| D4 DO-06 | Trạng thái duyệt từ ERP, thông báo | — | UC-04 | đọc duyệt từ ERP (không có) | UX-10 | BLOCKED |
| D5 | Nhờ Trợ lý duyệt/tạo đơn (từ chối) | — | BR-20, BR-21 | — | UX-01 | BLOCKED |
| D2-Acting | Trưởng bộ phận xử lý thay | — | chưa có UC | — | UX-11 | OUT |
| E1 DB-01 | Tổng quan theo vai trò | `#/tong-quan` | chưa có UC | số liệu tổng hợp | UX-11, UX-13; Q8 có điều kiện | BLOCKED |
| E2 AU-01 | Hoạt động / nhật ký | `#/hoat-dong` | UC-07 một phần | audit nghiệp vụ | UX-11 (SECREV chỉ trace đã che — đã có ở F4) | BLOCKED |
| E3 PF-01 | Hồ sơ cá nhân, đổi mật khẩu | `#/ho-so` | chưa có UC | hồ sơ, đổi mật khẩu | UX-11, UX-12 | BLOCKED |
| — | Audit Test Dashboard (rule 50) | — | UC-07 | `GET …/sec-results` | SEC-DASH (A-45 hoãn) | BLOCKED |

## 3. Component design system

`có` = đã chép vào `frontend/src/design-system/` · `có*` = đã chép, đã sửa có review (sau P1: 28 component + `a11y.jsx`; 4 bản đã sửa: Icon A-66, AIComposer A-68, Tooltip và DataTable A-80) · `chưa` = chưa chép. Phase = nơi lần đầu cần tới (chép khi cần, không chép trước).

| Nhóm | Component | Trong app | Dùng ở | Phase chép | Ghi chú review khi chép |
|---|---|---|---|---|---|
| core | Icon | có* | mọi nơi | — | `<title>` React (A-66) |
| core | Button, IconButton, Spinner, a11y | có | mọi nơi | — | |
| forms | Input, Select, Textarea, Switch | có | F1–F5 | — | |
| forms | Field | có · export P1 | form lọc F4, P4 | P1 ✔ | |
| forms | SearchField | chưa | SH-02, A | P5 | |
| forms | Checkbox | có (P1, không export, D10) | DataTable | P1 ✔ | `aria-checked` dư trên input gốc; chưa đặt `indeterminate` gốc |
| forms | Radio | chưa | P4 (nếu thiết kế dùng) | P4 | |
| feedback | Alert, Dialog, EmptyState, Skeleton | có | F1–F5 | — | |
| feedback | Toast, ToastViewport | có (P1) | SH-08, F2 "bị nhận trước", P4 | P1 ✔ | chỉ hiện chuỗi được truyền; chính sách ở `useToast` P2 |
| feedback | Tooltip | có* (P1, D11) | sidebar thu gọn | P1 ✔ | sửa WCAG 1.4.13/4.1.2 |
| feedback | Drawer | chưa | A2 | P5 | focus trap (`specs/92`: inline không tự giữ focus) |
| data | Badge, DescriptionList | có | F2–F5 | — | |
| data | DataTable | có* (P1, D14) | F2/F4 (nếu đổi theo artboard), P4 | P1 ✔ | không trạng thái lỗi; sắp xếp nội bộ chỉ trên dòng đã có; quy tắc dùng ở `DECISIONS.md` |
| data | Tag | có (P1) | lọc, nhãn | P1 ✔ | không dùng `onClick` + `onRemove` cùng lúc |
| data | Card | có (P1) | P4, F5 | P1 ✔ | tiêu đề luôn `h3` |
| data | Pagination, TableToolbar | chưa | P4 (nếu thiết kế dùng) | P4 | |
| data | MetricCard | chưa | E1 | P5 | |
| navigation | Sidebar, AppHeader | có | SH-01 | — | AppHeader chưa có menu tài khoản (`specs/92`) |
| navigation | Breadcrumb | có · export P1 | SH-01 | P1 ✔ | |
| navigation | PageHeader | có (P1) | mọi page nhân viên | P1 ✔ | một `h1` mỗi trang |
| navigation | Tabs | có (P1) | P4 (chờ duyệt / đối soát) | P1 ✔ | page tự render `tabpanel`; `value` phải là tab bật |
| ai | ChatMessage, AIComposer* | có / có* | F1, F3 | — | |
| ai | ToolCall | chưa | F4 (spec nêu), B | P3 nếu F4 cần | chỉ hiển thị mã, không nội dung |
| ai | AIPanel, ActionConfirmation, ERPResult, SourceTag | chưa | A–D | P5 | ActionConfirmation thiếu trạng thái hết hạn (`specs/92` M4) |
| dashboard | AvatarGroup, ChartCard | chưa | E1 | P5 | |

## 4. Mã dùng chung ở tầng ứng dụng (đã có / sẽ thêm)
| Mã | Vị trí | Trạng thái | Phase |
|---|---|---|---|
| API client (CSRF, problem+json, `traceId`) | `src/api/client.ts` | có | — |
| Thông điệp lỗi cố định + `REQ-` | `src/app/texts.ts` | có | — |
| Nhãn `HandoffReason`, `QuoteStatus` | `src/app/staffLabels.ts` | có | — |
| Định dạng VND, thời gian | `src/app/money.ts` | có (giờ: kiểm lại) | P1 |
| Breakpoint | `src/app/breakpoints.ts` | có (P1) | P1 ✔ |
| CSS nền theo token | `src/app/foundation.css` (tên cuối cùng) | có (P1) | P1 ✔ |
| Router + bảng route theo vai trò | `src/app/router.ts`, `src/app/routes.ts` | mới | P2 |
| `PageState` (loading/empty/error/forbidden/notFound/timeout/conflict) | `src/app/PageState.tsx` | mới | P2 |
| `ToastViewport` + hook | `src/app/toast.tsx` | mới | P2 |
| Error boundary | `src/app/ErrorBoundary.tsx` | mới | P2 |
| Mock theo contract (dev/test) | `src/mocks/` | mới (QĐ 5) | P2 |
| Fake backend cho unit test | `src/test/fakeBackend.ts` | có | — |

## 5. Phụ thuộc giữa các mục
```
tokens.css, bundle.css, fonts ─► P1 component chép thêm ─► P2 Shell (SH-01), PageState, Toast, Router
P2 ─► P3 F1..F5, E7 trong khung mới
P2 + thiết kế batch G được duyệt ─► P4 G1, G2
P2 + UX-xx + UC + contract ─► P5 (A ─► C ─► D; B độc lập; E1/E2/E3 độc lập)
```
