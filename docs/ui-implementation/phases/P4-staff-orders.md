# P4 · Đơn hàng của nhân viên (EP-10e) — GATED

> DESIGN PROPOSAL · nhánh dự kiến `feat/EP-10e-staff-orders` · backlog EP-10e (A-69 4A).
> **Cổng:** chưa có thiết kế được duyệt. Batch D (D1–D4) lệch UX-03 (SALES không tạo đơn), UX-08 (không "tạo lại cùng khóa"), UX-10 (không đọc duyệt từ ERP). Không bắt đầu code trước khi có thiết kế batch G được duyệt (QĐ 6).

## Scope
Hai danh sách cho SALES của team khách, đúng các API đã có và đã test (`StaffDecisionIT`, đối soát A-41):
- **G1 · Chờ ghi quyết định duyệt**: `GET /api/internal/orders/awaiting-decision` → `AwaitingView{requestId, quoteRef, quoteVersion, erpOrderRef, since}`; ghi `POST …/{requestId}/staff-decision {decision}` → `DecidedView{requestId, status}`. Quyết định là cuối cùng; chỉ từ CREATED / RECONCILED_CREATED.
- **G2 · Đối soát kết quả chưa rõ**: `GET /api/internal/orders/unknown` → `UnknownView{requestId, quoteRef, quoteVersion, externalRef, since, …}`; ghi `POST …/{requestId}/reconcile {outcome, …}` → `ReconciledView`.

Không có: tạo đơn, gửi lại ERP, đọc trạng thái duyệt từ ERP, lý do công nợ (OPEN), giá trong danh sách (API không trả).

## Tasks
| ID | Task | Phụ thuộc | Chi tiết |
|---|---|---|---|
| P4-T00 | Brief thiết kế batch G cho Claude Design: chỉ trường API trên, trạng thái theo F6, chữ theo writing rule | P0-T05 | Duyệt bởi chủ dự án → `docs/design/specs/60-batch-g-…md` + prototype |
| P4-T01 | Đọc đủ field của `UnknownView`/`ReconcileRequest`/`Finding` trong backend; ghi bảng trường | — | Không đoán trường |
| P4-T02 | `src/api/orders.ts`: decoder fail closed (trường lạ → từ chối cả phản hồi, như A-70) | T01 | Test decoder |
| P4-T03 | Nhãn tiếng Việt cho `Decision`, `Finding`, `status` | T01, thiết kế | Bảng nhãn trong spec batch G |
| P4-T04 | Page G1 với Tabs (nếu thiết kế dùng), bảng, xác nhận trước khi ghi (Dialog, động từ lặp ở tiêu đề + nút) | P2, T02, thiết kế | Đọc chỉ khi vào/ sau thao tác/ khi bấm Tải lại (audit `STAFF_LIST_READ`) |
| P4-T05 | Page G2 tương tự, ghi kết quả đối soát | như T04 | Không có nút "tạo lại" (UX-08) |
| P4-T06 | Mock theo contract cho dev (QĐ 5) | P2-T10 | Chỉ endpoint trên |
| P4-T07 | E2E với backend thật: đơn UNKNOWN từ luồng F1 + Fake provider → đối soát; đơn CREATED → ghi quyết định; SALES team khác → không thấy | T04, T05 | Bổ sung `flows.spec.ts` |
| P4-T08 | Cập nhật TRACEABILITY (UC-04), backlog EP-10e, README | tất cả | |

## Trạng thái từng page
Đang tải · rỗng ("Chưa có đơn chờ …") · lỗi + `REQ-` · quá thời gian (không ghi gì) · 403 (vai trò khác) · 404 (đơn của team khác / đã xử lý) · 409 (đã có người ghi / trạng thái đổi) · đang ghi (nút khóa "Đang ghi…") · ghi chưa rõ kết quả (không tự gửi lại; "Tải lại để kiểm tra") · validation (chưa chọn quyết định).

## Dependencies
P2; thiết kế batch G được duyệt; không cần backend mới (nếu thiết kế đòi trường mới → issue backend riêng trước).

## Acceptance Criteria
- Chỉ hiện trường có trong view của API; không hiện giá, công nợ, tên khách.
- Mọi thao tác ghi có hộp xác nhận, khóa nút khi đang gửi, "Đã …" chỉ sau 2xx.
- 409/404 hiện thông điệp đúng và tải lại danh sách theo yêu cầu, không tự lặp.
- Decoder từ chối phản hồi có trường lạ.
- Component test mọi trạng thái + axe; E2E T-07 xanh.

## Testing
`npm run typecheck && npx vitest run && npm run build`, `npm run e2e`. Backend: `./mvnw -B verify` chỉ khi đổi backend (không dự kiến).

## Threat cases
SALES team khác mở danh sách → rỗng/404; gửi quyết định hai lần (bấm đúp, hai tab) → một lần thành công, lần kia 409; mất phiên giữa chừng → không gửi lại sau đăng nhập.

## Rollback
Revert commit; ẩn mục sidebar; không migration.
