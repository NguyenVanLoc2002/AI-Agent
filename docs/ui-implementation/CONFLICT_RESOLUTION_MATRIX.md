# Conflict Resolution Matrix · Batch A–E (workstream W-BIZ)

> 2026-11-26 · **PLANNING ONLY — chưa có quyết định nào ở đây.** Mọi phương án là đề xuất để chủ dự án chọn; cho tới khi chọn, cột "Safe default" của `docs/DECISIONS_REQUIRED.md` §UX vẫn áp dụng và màn hình liên quan BLOCKED (FE-UI-D07). Không ADR, contract hay RBAC nào bị thay đổi bởi tài liệu này.
> Nguồn: `docs/DECISIONS_REQUIRED.md` §UX, `docs/design/specs/90-danh-muc-man-hinh.md` §UC, `docs/requirements/USE_CASES.md`, `docs/requirements/BUSINESS_RULES.md`, `docs/contracts/*.md`, `docs/security/RBAC_MATRIX.md`, backend `@RestController` (kiểm 2026-11-26: không endpoint nào cho batch A–E).
> Đã giải quyết, không lặp lại: UX-06 (nhãn quote), UX-13 (batch F), UX-17 (nhãn lý do) — APPROVED 2026-11-23.

## 1. Ma trận

| UX | Artboard / màn hình | UC | Xung đột | Nguồn nghiệp vụ & bằng chứng API | Phương án (chủ dự án chọn) | Quyết định cần | Phụ thuộc backend | Tác động bảo mật | Acceptance criteria | Điều kiện mở khóa |
|---|---|---|---|---|---|---|---|---|---|---|
| UX-15 | A1–A7, PL-01, PL-03, PL-04 (`A*.dc.html`, `PartDetail`), B5 | UC-02 (phía NV chưa có UC); BR-04..07, BR-34 OPEN | Thiết kế cho NV thấy giá niêm yết và **số lượng** tồn theo chi nhánh | `ERP_CONNECTOR.md`: giá chỉ qua `priceFor(ErpCustomerLink, Sku)` (cần khách đã xác thực); tồn số lượng là INTERNAL (rule 10). Không có endpoint tra cứu cho NV | (a) NV chỉ thấy mức khả dụng (Còn/Sắp hết/Hết) và không giá; (b) NV thấy giá theo một khách đang phục vụ (cần ngữ cảnh khách, liên quan UX-02/04); (c) NV thấy giá niêm yết + số lượng (cần ADR mới, phân loại lại dữ liệu) | Phạm vi dữ liệu giá/tồn của SALES | port tra cứu danh mục cho NV + DTO allowlist; nếu (b)/(c): port giá mới | Cao: RESTRICTED/INTERNAL ra UI NV; không bao giờ tới LLM | DTO chỉ trường được duyệt; test RBAC mỗi vai trò; không trường giá/tồn số trong phản hồi khi không có quyền | Chọn phương án + UC NV + contract + dòng RBAC |
| UX-13 (phần A–E) | A, B1–B9, C5, D1–D4, E1, E3, E4 | không UC | Màn hình chưa có use case | `USE_CASES.md` chỉ UC-01..08; NV chỉ có UC-05/06 | (a) viết UC cho từng nhóm được mở; (b) giữ ngoài PoC | Nhóm nào được vào PoC | — | Phụ thuộc phạm vi | UC có actor, quyền, luồng lỗi, audit | UC được duyệt |
| UX-01 | B1–B9, C6, D5, AI panel trong SH-01 | không UC (TOOL_CONTRACTS: NV không dùng agent tool) | Trợ lý NV gọi tool; tên tool khác contract; VIN là tham số | `TOOL_CONTRACTS.md` §1: "Nhân viên không dùng agent tool trong PoC"; VIN chỉ qua `vinRef: "current"` | (a) Trợ lý NV chỉ giải thích dữ liệu backend đã đưa (không tool); (b) bộ tool riêng cho NV, chỉ đọc, contract mới; (c) bỏ Trợ lý NV khỏi PoC | Có Trợ lý cho NV không, quyền gì | AI gateway ngữ cảnh NV (egress, phân loại) | Rất cao: đường egress mới tới provider; chưa kiểm SEC-01..03 cho NV | egress guard + honeytoken cho luồng NV; không giá/tồn/khách tới model | Chọn phương án + threat model + contract + SEC test |
| UX-16 | B1 (phiên người khác), C2-OtherDenied, mọi màn NV | — | Mở tài nguyên người khác hiện "Không có quyền" | RBAC_MATRIX §3: trả 404, không lộ tồn tại | (a) dùng "Không tìm thấy" như batch F (đề xuất nhất quán); (b) giữ chữ thiết kế (lệch RBAC) | Chữ hiển thị | không (backend đã 404) | Lộ sự tồn tại nếu chọn (b) | UI 404 → "Không tìm thấy" | Quyết định chữ (nhẹ, có thể mở sớm) |
| UX-04 | C2/C3 soạn, sửa dòng, tính giá, C5 | UC-03 (khách từ phiên xác thực) | NV tự lập báo giá cho bất kỳ khách | UC-03 actor = khách qua agent; RBAC: SALES không tạo quote | (a) NV chỉ xem quote của hội thoại đang giữ (đã có ở F3); (b) NV lập quote cho khách được giao, UC + quyền mới; (c) ngoài PoC | NV có lập báo giá không | quote service API cho NV, phiên bản, idempotency | Cao: ghi giá, tranh chấp phiên bản | version check, audit, test cross-team 404 | UC + RBAC + contract quote NV |
| UX-02 | C5-New, C5-NewMulti, C5-NewNone | không UC | Tìm khách theo SĐT/mã | `ERP_CONNECTOR.md` cấm `searchCustomers`, `listCustomers` | (a) không có (safe default); (b) chọn khách từ danh sách khách được giao (không tìm tự do) | Có chọn khách không | port khách được giao (không tìm kiếm) | Rất cao: liệt kê khách, dữ liệu cá nhân | không endpoint tìm tự do; test chống liệt kê | Chỉ khi UX-04 = (b) |
| UX-07 | C6, B8 điểm nối | UC-03 | Model thấy chỗ giữ tuần tự `{BAO_GIA_1}` | ADR-008: handle ngẫu nhiên 128-bit, có TTL | (a) handle ADR-008, chữ `{BAO_GIA_1}` chỉ là cách hiển thị | Xác nhận (a) | không (đã có ADR-008) | Đoán được handle nếu tuần tự | handle không đoán được | Đi cùng UX-01 |
| UX-09 | C2, C3, C7, D2, D4 (tổng tiền) | UC-03/04; BR-34 OPEN | Thiết kế giữ R4 BLOCKED; ADR-014/D-13 ghi "chưa gồm VAT" | ADR-014 | (a) hiện "chưa gồm VAT", không tổng thanh toán; (b) chờ ERP đích rồi quyết | Chữ VAT | không | Thấp (hiển thị) | không tính tổng thanh toán | Quyết định chữ (nhẹ) |
| UX-14 | C2 gửi, C3-Send*, C4 | UC-03 | Zalo OA là kênh gửi, mã lỗi `ZOA-*` tự đặt | D-06 OPEN: trình mô phỏng cục bộ | (a) "kênh gửi" chung + mô phỏng, không mã Zalo; (b) chờ D-06 | Kênh gửi | port gửi (chưa có) | Trung bình: tích hợp ngoài | không gọi Zalo thật; trạng thái "đã gửi" chỉ sau xác nhận | D-06 hoặc chấp nhận (a) |
| UX-05 | C7 (phía NV) | UC-04; BR-14 | Khách xác nhận bằng chữ chat | rule 40, ADR-007: xác nhận có xác thực gắn quote ID + version (đã làm ở F1) | (a) giữ như F1; màn NV chỉ hiển thị kết quả | Xác nhận (a) | không | Cao nếu chữ chat tạo đơn | chữ "ok" không tạo đơn (đã có test F1) | Đi cùng nhóm C |
| UX-03 | D1-Todo, D2, D3 "Tạo đơn trên ERP" | UC-04 (actor = khách) | NV bấm tạo đơn nháp sau khi khách xác nhận | RBAC_MATRIX: SALES ✘ tạo draft order; đơn tạo qua xác nhận của khách | (a) đơn chỉ từ xác nhận của khách (hiện trạng; NV xem/ghi quyết định ở P4); (b) thêm bước NV tạo đơn — UC + quyền + ADR-007 đổi | Ai tạo đơn | nếu (b): API tạo đơn cho NV | Cao: ghi ERP | idempotency ADR-009, audit | Chọn; (a) không cần backend |
| UX-08 | D3-Unknown, D3-NotFound, D3-Error | UC-04 | "tra cứu theo khóa rồi tạo lại cùng khóa"; nút thử lại khi ERP 5xx | ADR-009: OUTCOME_UNKNOWN + đối soát thủ công, không thử ghi lại | (a) hàng đợi đối soát thủ công (đã có API, P4 G2); (b) đổi ADR-009 để cho thử lại | Chính sách thử lại | (b) cần capability ERP | Cao: đơn trùng | không nút "tạo lại" | (a) đã đủ cho P4 |
| UX-18 | D3 khóa chống trùng hiển thị | — | Khóa idempotency hiện dạng `BG-…-v2` | ADR-009: khóa server `quoteId:version` | (a) UI chỉ hiện mã tham chiếu, không hiện khóa | Xác nhận (a) | không | Thấp | không lộ khóa | Đi cùng P4/nhóm D |
| UX-10 | D4-*, D4-Notify, chuông | UC-04 | Đọc trạng thái duyệt/lý do từ ERP, thông báo, "Mở trên ERP" | `ERP_CONNECTOR.md`: không có port đọc duyệt; SALES ghi tay (A-15, `staff-decision`) | (a) NV ghi quyết định (P4 G1); (b) port đọc duyệt + thông báo (contract mới, D-01) | Nguồn trạng thái duyệt | (b) port ERP mới | Trung bình | — | (a) đã đủ cho P4 |
| UX-11 | D2-Acting, E1-DashTeam, E2-AuditAuditor/Admin, E3-ProfileSupervisor/Restricted | không UC | Vai trò ngoài RBAC: trưởng bộ phận, giám đốc, NV hạn chế, SECREV đọc audit nghiệp vụ | RBAC_MATRIX: DEPT_LEAD ✘ MVP; SECREV chỉ trace đã che; D-08 | (a) giữ RBAC hiện có, ẩn các biến thể; (b) thêm vai trò (ADR-010 + RBAC + test ma trận) | Vai trò PoC | identity, RBAC | Cao | ma trận T-RBAC-MATRIX mở rộng | Chọn; (a) mở được E2 cho SALES nếu có API |
| UX-12 (phần OPEN) | E5-Warning, E7-LoginLocked, E3-Password | hạ tầng | cảnh báo 5 phút, khóa tài khoản, đổi mật khẩu kết thúc phiên khác | ADR-010 chỉ có 30 phút / 8 giờ (A-06) | (a) giữ ngoài PoC; (b) issue identity riêng | Có làm không | identity | Trung bình | T-AUTH-* | Chọn |

## 2. Nhóm theo phụ thuộc và thứ tự mở khóa đề xuất

| Thứ tự | Nhóm | Màn hình | UX phải chốt | Phụ thuộc nhóm khác | Lý do thứ tự |
|---|---|---|---|---|---|
| 0 | **Nhẹ — chỉ chữ/hiển thị** | dùng cho mọi nhóm | UX-16, UX-09, UX-07, UX-18, UX-05 | — | Không cần backend; quyết định một lần, áp dụng chung |
| 1 | **E-hỗ trợ tối thiểu** | E4-Denied, E6-Error, E7-Login (đã là SH-03/04/06 trong P2/P3) | UX-11 (a), UX-12 (a) | — | Có thể mở ngay khi giữ RBAC hiện có; phần lớn đã nằm trong P2/P3 |
| 2 | **A · Tra cứu phụ tùng (chỉ đọc)** | A1–A7, PL-01..03, SH-02 | UX-15, UX-13 (UC NV) | — | Độc lập, chỉ đọc; mở đường cho B và C; quyết định UX-15 là then chốt |
| 3 | **E · Tổng quan / Hoạt động** | E1, E2 (phạm vi SALES) | UX-11, UX-13; nguồn số liệu | P4 (đơn), batch F (handoff) | Số liệu chỉ từ dữ liệu đã có API |
| 4 | **C · Báo giá phía NV** | C1–C4 (C6 nếu UX-01) | UX-04, UX-14, UX-09, UX-05 | A (thêm vào báo giá) | Cần quyết định lớn về quyền ghi |
| 5 | **D · Đơn nháp phía NV (phần ngoài P4)** | D1, D2, D4 | UX-03, UX-08, UX-10, UX-18 | C, P4 | Phần hợp lệ đã đi qua P4 |
| 6 | **B · Trợ lý AI cho NV** | B1–B9, AI panel, C6, D5 | UX-01, UX-16, UX-07 | A (ngữ cảnh) | Rủi ro egress cao nhất; cần threat model và SEC test riêng |
| — | Ngoài PoC theo safe default | C5, D2-Acting, E5-Warning, E7-LoginLocked, R10 | UX-02, UX-11, UX-12 | — | Chỉ mở khi chủ dự án đổi phạm vi |

Mỗi nhóm mở độc lập: không cần chờ các nhóm sau. Khi một nhóm đủ điều kiện → tạo phase con P5-x (mẫu P3/P4) và cập nhật `SCREEN_INVENTORY.md`, `PROGRESS.md`.

## 3. Việc tiếp theo của workstream (chỉ tài liệu)
| ID | Việc | Đầu ra |
|---|---|---|
| W-BIZ-T01 | Chủ dự án quyết định nhóm 0 (UX-16, 09, 07, 18, 05) | A-xx |
| W-BIZ-T02 | Chủ dự án chọn phương án UX-15 (+ có viết UC tra cứu cho NV không) | A-xx |
| W-BIZ-T03 | Nếu A được mở: soạn UC mới cho tra cứu phía NV (mã do chủ dự án cấp) + contract nháp `docs/contracts/` + dòng RBAC, review architecture + security | PR tài liệu |
| W-BIZ-T04 | Lặp lại cho nhóm tiếp theo theo bảng §2 | — |
