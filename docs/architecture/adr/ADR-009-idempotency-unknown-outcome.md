# ADR-009 Idempotency & unknown ERP outcome
- **Status:** PROPOSED (sửa sau review 2026-11-13) · **Open:** D-03
- **Context:** SOURCE FACT BRD §06: key = quoteId-version unique trong DB; timeout → UNKNOWN, đối soát theo external ref; không tạo lần hai mù quáng. Arito có hỗ trợ idempotency/lookup hay không: OPEN D-03.
- **Decision:** `idempotency_key = quoteId:version` do server sinh (không nhận header từ client); UNIQUE(quote_id, quote_version) trong `draft_order_request`; outbox cho lệnh ERP; capability `supportsIdempotencyKey`/`supportsExternalRefLookup` là **cấu hình tĩnh** đặt theo tài liệu Arito đã duyệt (mặc định false/false), không do adapter tự khai báo; không hỗ trợ → `OUTCOME_UNKNOWN` + hàng đợi đối soát thủ công cho SALES, mỗi thao tác audit. Không retry tự động lệnh ghi.
- **Consequences:** nếu Arito không hỗ trợ lookup, mọi timeout cần người xử lý.
- **Evidence required:** T-SEC04-PARALLEL, -DUPCALLBACK, -UNKNOWN với cả hai chế độ capability (Testcontainers); T-ERP-05. NOT RUN.
