# RBAC matrix — v0.1 DRAFT

> 2026-11-13. Vai trò SOURCE FACT: BRD §02 (`@af2d09f`), UI personas. Ma trận quyền cụ thể: DESIGN PROPOSAL (ADR-010). Assignment logic, trưởng bộ phận, break-glass: **OPEN D-08**. Chưa VERIFIED.
> Mọi kiểm tra ở service boundary bằng danh tính server-side (SOURCE FACT); frontend ẩn nút **không** phải kiểm soát.

## 1. Vai trò
| Vai trò | PoC | Nguồn |
|---|---|---|
| CUSTOMER (đã liên kết) | ✔ | SOURCE FACT |
| SALES | ✔ (team seed) | SOURCE FACT |
| SECURITY_REVIEWER | ✔ | SOURCE FACT (UI persona) |
| OPERATOR (quản trị hệ thống) | ✔ | SOURCE FACT |
| DEPT_LEAD | ✘ (MVP) | SOURCE FACT tồn tại / OPEN quyền |
| AGENT (service account) | nội bộ — không có endpoint | SOURCE FACT |

Viết tắt dùng trong các tài liệu khác: CUST = CUSTOMER, OPS = OPERATOR, SECREV = SECURITY_REVIEWER.

## 2. Ma trận quyền (✔ = cho phép với điều kiện ownership ghi trong ô)
| Tài nguyên / hành động | CUSTOMER | SALES | SECREV | OPERATOR | AGENT |
|---|---|---|---|---|---|
| Tạo/khôi phục conversation | ✔ của mình | ✘ | ✘ | ✘ | ✘ |
| Gửi tin chat | ✔ conversation của mình, bot không khóa | ✔ chỉ khi HUMAN_ACTIVE và đã claim | ✘ | ✘ | ✘ |
| Xem nội dung conversation | ✔ của mình | ✔ được giao/đã claim | ✘ | ✘ (BR-24) | ✘ |
| Xem quote (có giá) | ✔ của mình | ✔ conversation được giao | ✘ | ✘ | ✘ (chỉ handle) |
| Xác nhận quote | ✔ của mình, version hiện hành | ✘ | ✘ | ✘ | ✘ |
| Tạo draft order | gián tiếp qua xác nhận | ✘ | ✘ | ✘ | ✘ |
| Phê duyệt đơn | ✘ | trong ERP đích (ngoài hệ thống) | ✘ | ✘ | ✘ |
| Sửa giá / chiết khấu | ✘ | ✘ trong PoC (OPEN BRD §15) | ✘ | ✘ | ✘ |
| Danh sách handoff | ✘ | ✔ team mình | ✘ | ✘ | ✘ |
| Claim/resolve handoff | ✘ | ✔ team mình | ✘ | ✘ | ✘ |
| Xử lý ORDER_OUTCOME_UNKNOWN | ✘ | ✔ team mình (đánh dấu kết quả đối soát) | ✘ | ✘ | ✘ |
| Xem security trace (redacted) | ✘ | ✘ | ✔ | ✘ | ✘ |
| Dashboard SEC | ✘ | ✘ | ✔ | ✘ | ✘ |
| Bật/tắt kill switch | ✘ | ✘ | ✘ | ✔ (audit) | ✘ |
| Cấu hình provider/key | ✘ | ✘ | ✘ | ✔ (ngoài ứng dụng — secret manager) | ✘ |
| Agent tools | ✔ theo `TOOL_CONTRACTS.md` §2 (thực thi thay CUSTOMER, trong ngữ cảnh của CUSTOMER) | ✘ | ✘ | ✘ | — |

## 3. Quy tắc
- Admin/Operator **không** mặc nhiên đọc dữ liệu nghiệp vụ (SOURCE FACT BRD §02, rule 10).
- Ownership kiểm tra mỗi lần truy cập hàng dữ liệu, kể cả URL trực tiếp và worker bất đồng bộ (rule 20).
- Tài nguyên của người khác trả `404` (không lộ tồn tại) — DESIGN PROPOSAL; audit `FORBIDDEN_RESOURCE`.
- Một người dùng chỉ có một vai trò trong PoC (DESIGN PROPOSAL, đơn giản hóa).
- Quyền của SALES với conversation sau khi resolve: **A-15 APPROVED 2026-11-19 (owner)**: mất quyền đọc nội dung khi handoff RESOLVED (EP-08a: chỉ người đang giữ handoff CLAIMED mới đọc/ghi; kiểm lại ở mỗi lời gọi). Xem lại bản ghi handoff đã resolve: chưa có endpoint.
- **Team (D-08, mặc định PoC, owner 2026-11-19):** mỗi SALES thuộc một team, mỗi khách do một team phục vụ, cả hai lấy từ cấu hình demo. "Team mình" = team của khách. Team khác nhận 404 như tài nguyên không tồn tại. Khách đã liên kết nhưng không có team: không tạo được conversation (403 `CUSTOMER_NOT_ASSIGNED`, owner 2026-11-19); phần còn lại cho dữ liệu cũ: A-42(e).
- **EP-08b (2026-11-20, A-43):** "Xem quote (có giá)" cho SALES = chỉ người đang giữ handoff của conversation (cùng quy tắc A-15), chỉ đọc, không cấp token (`GET /api/internal/conversations/{id}/quote`). Ghi quyết định duyệt/từ chối đơn = SALES team của khách (`POST /api/internal/orders/{id}/staff-decision`, chỉ từ CREATED / RECONCILED_CREATED, quyết định cuối cùng, audit ở `draft_order_event`). Lý do chi tiết của NOT_QUOTED (công nợ) chưa hiện cho SALES (OPEN).
- **EP-08a (2026-11-19):** `/api/internal/**` chỉ SALES ngay ở mức URL; các dòng "Danh sách handoff", "Claim/resolve handoff", "Gửi tin chat" (SALES khi HUMAN_ACTIVE và đã claim), "Xem nội dung conversation" (SALES đã claim), "Xử lý ORDER_OUTCOME_UNKNOWN" (team mình) đã có mã và test. "Xem quote (có giá)" cho SALES và ghi quyết định duyệt đơn: EP-08b.
- Ghi nhận quyết định duyệt/từ chối đơn (`staff-decision`) và đối soát UNKNOWN: chỉ SALES của team được giao; mọi thao tác audit.

## 4. Tests (EP-08a 2026-11-19: T-UC06-XTEAM, T-UC06-OPS, T-RBAC-AFTER-RESOLVE VERIFIED — `StaffInboxIT`, `HandoffInboxIT`; T-RBAC-MATRIX VERIFIED cho 12 endpoint hiện có × 4 vai trò + ẩn danh trong một test (`StaffInboxIT.everyEndpointAnswersEveryRoleAsTheMatrixSays`), chưa gồm trace/dashboard SEC/kill switch (EP-09); EP-08b 2026-11-20: ma trận thêm 3 endpoint (15 × 4 vai trò + ẩn danh), SALES xem quote và quyết định duyệt đơn VERIFIED — `StaffInboxIT.theHolderSeesTheConversationsQuoteWithoutAConfirmationToken`, `StaffDecisionIT`; T-SEC02 xem TOOL_CONTRACTS §9; T-UC07-RBAC NOT RUN, EP-09)
T-SEC02-* (20 cross-customer), T-UC06-XTEAM, T-UC06-OPS, T-UC07-RBAC, T-RBAC-MATRIX (test tham số hóa từng ô ma trận × vai trò; tài nguyên của người khác → 404, chức năng ngoài vai trò → 403), T-RBAC-AFTER-RESOLVE (SALES đọc conversation sau resolve → 404 theo mặc định PoC).
