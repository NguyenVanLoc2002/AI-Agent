# ADR-003 Phân loại dữ liệu & type-level egress boundary
- **Status:** PROPOSED
- **Context:** SOURCE FACT BRD §08, rule 10: DENY/ALLOW list; "reject on unclassified fields"; regex masking không đảm bảo ẩn danh. security-auditor (2026-11-13): honeytoken scan chỉ chứng minh vắng sentinel, không chặn giá thật.
- **Decision:** Boundary chính = kiểu dữ liệu: các kiểu egress (`gateway.api`, `tools.api.dto`) không có field tiền/ID/Map/Object (ARCH-05) và bắt buộc `@EgressClassified(APPROVED_TECHNICAL)` (ARCH-06). Scanner trên bytes cuối là lớp thứ hai. Danh sách versioned tại `docs/security/DATA_CLASSIFICATION.md`.
- **Consequences:** thêm field mới cần review phân loại; không thể gửi nhanh object có sẵn.
- **Evidence required:** T-SEC01-SCHEMA, ArchUnit. NOT RUN.
