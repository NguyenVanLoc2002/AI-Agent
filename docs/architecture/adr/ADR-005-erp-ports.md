# ADR-005 Tách ERP ports
- **Status:** PROPOSED (sửa sau review 2026-11-13) · **Open:** A-07, D-01..D-03
- **Context:** SOURCE FACT BRD §12 đề xuất `ErpConnector` gộp (gồm giá theo khách, công nợ). architecture-reviewer: port gộp cho phép mọi module được inject đọc dữ liệu RESTRICTED; denylist không đủ; bucket tồn trong kết quả tìm kiếm cần nguồn hợp lệ.
- **Decision:** spi theo module tiêu thụ: `identity.spi.CustomerLinkPort`, `catalog.spi.VerifiedProductPort`, `catalog.spi.AvailabilityBucketPort` (chỉ enum, không nhận số lượng), `quote.spi.PricingPort`/`AvailabilityPort`/`CreditEligibilityPort`, `order.spi.DraftOrderPort`. Module `erp` là leaf adapter. Allowlist phụ thuộc (ARCH-02, ARCH-12). Capability ERP là cấu hình tĩnh, không tự khai báo. Các chi tiết chữ ký vượt BRD §12 (revision giá, Rejected có mã) được đánh dấu OPEN với fallback. Chi tiết `docs/contracts/ERP_CONNECTOR.md`.
- **Consequences:** + ArchUnit cấm tools/agent/gateway/catalog chạm giá và số tồn; − nhiều interface hơn; adapter Arito tương lai phải hiện thực nhiều spi.
- **Evidence required:** ARCH-02/03/09/12, T-ERP-01..06. NOT RUN.
