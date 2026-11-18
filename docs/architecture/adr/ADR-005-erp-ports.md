# ADR-005 Tách ERP ports
- **Status:** ACCEPTED (2026-11-18, project owner) · A-07, A-12 APPROVED · **Open:** D-01..D-03 (ERP đích thật)
- **Triển khai:** SEC-01a (2026-11-18) hiện thực `catalog.spi.VerifiedProductPort`, `AvailabilityBucketPort` (named interface `catalog :: spi`) và `erp.internal.MockErpConnector`; các port khác theo issue riêng.
- **Context:** SOURCE FACT BRD §12 đề xuất `ErpConnector` gộp (gồm giá theo khách, công nợ). architecture-reviewer: port gộp cho phép mọi module được inject đọc dữ liệu RESTRICTED; denylist không đủ; bucket tồn trong kết quả tìm kiếm cần nguồn hợp lệ.
- **Decision:** spi theo module tiêu thụ: `identity.spi.CustomerLinkPort`, `catalog.spi.VerifiedProductPort`, `catalog.spi.AvailabilityBucketPort` (chỉ enum, không nhận số lượng), `quote.spi.PricingPort`/`AvailabilityPort`/`CreditEligibilityPort`, `order.spi.DraftOrderPort`. Module `erp` là leaf adapter. Allowlist phụ thuộc (ARCH-02, ARCH-12). Capability ERP là cấu hình tĩnh, không tự khai báo. Các chi tiết chữ ký vượt BRD §12 (revision giá, Rejected có mã) được đánh dấu OPEN với fallback. Chi tiết `docs/contracts/ERP_CONNECTOR.md`.
- **Consequences:** + ArchUnit cấm tools/agent/gateway/catalog chạm giá và số tồn; − nhiều interface hơn; adapter ERP thật tương lai phải hiện thực nhiều spi.
- **Rebranding note (2026-11-16):** tên ERP vendor cụ thể được thay bằng "ERP đích" (Independent demo rebranding, `docs/REBRANDING_REPORT.md`). Quyết định, trạng thái và lịch sử **không đổi**.
- **Evidence required:** ARCH-02/03/09/12, T-ERP-01..06. NOT RUN.
