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
| Phê duyệt đơn | ✘ | trong Arito (ngoài hệ thống) | ✘ | ✘ | ✘ |
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
- Quyền của SALES với conversation sau khi resolve: **OPEN D-08**. Mặc định PoC (least privilege): mất quyền đọc nội dung khi handoff RESOLVED; chỉ còn xem bản ghi handoff của chính mình.
- Ghi nhận quyết định duyệt/từ chối đơn (`staff-decision`) và đối soát UNKNOWN: chỉ SALES của team được giao; mọi thao tác audit.

## 4. Tests (NOT RUN)
T-SEC02-* (20 cross-customer), T-UC06-XTEAM, T-UC06-OPS, T-UC07-RBAC, T-RBAC-MATRIX (test tham số hóa từng ô ma trận × vai trò; tài nguyên của người khác → 404, chức năng ngoài vai trò → 403), T-RBAC-AFTER-RESOLVE (SALES đọc conversation sau resolve → 404 theo mặc định PoC).
