# ADR-001 Modular monolith & enforcement ranh giới
- **Status:** PROPOSED (2026-11-13, sửa sau architecture review) · **Deciders:** Tech lead, Security (chưa ký)
- **Context:** SOURCE FACT (CLAUDE.md, BRD §01): modular monolith, một AI Gateway, package boundaries identity…audit. Tham chiếu trang trong TRACEABILITY là sơ bộ. Rủi ro chính: module agent/tools chạm dữ liệu RESTRICTED.
- **Decision:** Spring Boot modular monolith; mỗi module có `api`/`spi`/`internal`/`adapter`; thêm module policy, handoff, securityinspector, platform, bootstrap (wiring duy nhất). Không có module `web`. Ranh giới kiểm bằng Spring Modulith `verify()` + ArchUnit ARCH-01..13 (`docs/architecture/MODULE_BOUNDARIES.md`), chạy trong `test`, fail build. Khóa bot và ghi egress qua event để tránh vòng phụ thuộc.
- **Alternatives:** chỉ quy ước package (không cưỡng chế được); microservices (quá nặng cho PoC, thêm egress nội bộ).
- **Consequences:** + vi phạm phát hiện sớm; − cần fixture vi phạm cố ý để chứng minh rule hoạt động.
- **Evidence required:** `ModuleStructureTest`, `ArchitectureRulesTest` (EP-00). Hiện: NOT RUN.
