# ADR-007 Quote / draft order state machine
- **Status:** PROPOSED (sửa sau review 2026-11-13) · **Open:** A-03 (tên trạng thái), D-01 (đọc trạng thái duyệt từ Arito)
- **Context:** Mâu thuẫn nguồn: BRD §06 (SOURCE FACT) `DRAFT→SENT→ACCEPTED→DRAFT_ORDER_CREATED→HUMAN_APPROVED/REJECTED` vs rule 40 & skill quote-lifecycle `DRAFT→ISSUED→CONFIRMED→DRAFT_ORDER_CREATED→STAFF_APPROVED/REJECTED`. Cả hai thiếu trạng thái đang gửi, không rõ kết quả và nhánh recheck thất bại.
- **Decision (đề xuất):** tên theo rule 40, thêm `CONFIRMATION_BLOCKED`, `ORDER_SUBMITTING`, `ORDER_OUTCOME_UNKNOWN`, `ORDER_FAILED`. `draft_order_request` là nguồn sự thật cho kết quả ERP; quote phản chiếu theo bảng ánh xạ trong `docs/architecture/SYSTEM_ARCHITECTURE.md` §7. Ánh xạ BRD: SENT≡ISSUED, ACCEPTED≡CONFIRMED, HUMAN_APPROVED≡STAFF_APPROVED. `STAFF_APPROVED/REJECTED` trong PoC do SALES ghi nhận thủ công (không đọc ngược Arito — OPEN D-01).
- **Consequences:** BRD cần cập nhật hoặc ghi chú ánh xạ khi được khôi phục (A-01).
- **Evidence required:** test bảng transition (mọi cặp hợp lệ/không hợp lệ, gồm nhánh recheck thất bại), SEC-04. NOT RUN.
