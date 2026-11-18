ACCEPTED 2026-11-18 | — |ACCEPTED 2026-11-18 | A-05 ✔, D-07 (mở) |ACCEPTED 2026-11-18 | — |ACCEPTED 2026-11-18 (Maven, theo POC-001) | A-04 ✔ |ACCEPTED 2026-11-18 | A-02 (còn mở) |# Architecture Decision Records

Trạng thái: PROPOSED = chờ phê duyệt; ACCEPTED/REJECTED/SUPERSEDED chỉ khi owner ký trong file. Tất cả ADR dưới đây được tạo 2026-11-13 ở trạng thái **PROPOSED** (DESIGN PROPOSAL). ADR đã ACCEPTED thắng văn xuôi (CLAUDE.md).

2026-11-16, Independent demo rebranding (AutoParts Agent Sandbox): ADR-005, ADR-007 và ADR-009 chỉ đổi tên ERP vendor thành "ERP đích". Không ADR nào đổi quyết định hay trạng thái. Xem `docs/REBRANDING_REPORT.md`.

2026-11-18, G0-2 (một phần): owner duyệt ADR-001, 002, 003, 004, 016 cùng A-04, A-05, A-09, A-13 (nhóm chặn SEC-01a). Các ADR khác vẫn PROPOSED.

2026-11-18, lập kế hoạch SEC-01a: owner duyệt ADR-005, ADR-012 cùng A-07, A-10, A-11, A-12.

| ADR | Tiêu đề | Trạng thái | Quyết định cần duyệt |
|---|---|---|---|
| [001](ADR-001-modular-monolith.md) | Modular monolith & enforcement ranh giới | ACCEPTED 2026-11-18 | A-02 (còn mở) |
| [002](ADR-002-build-baseline.md) | Build tool & baseline | ACCEPTED 2026-11-18 (Maven, theo POC-001) | A-04 ✔ |
| [003](ADR-003-data-classification-egress.md) | Phân loại dữ liệu & type-level egress boundary | ACCEPTED 2026-11-18 | — |
| [004](ADR-004-ai-gateway.md) | AI Gateway & Fake provider cho PoC | ACCEPTED 2026-11-18 | A-05 ✔, D-07 (mở) |
| [005](ADR-005-erp-ports.md) | Tách ERP ports + bucket tồn | ACCEPTED 2026-11-18 | A-07 ✔, A-12 ✔ |
| [006](ADR-006-tool-contract.md) | Tool contract & per-call authz | PROPOSED | D-10, A-14 |
| [007](ADR-007-quote-state-machine.md) | Quote/draft order state machine | PROPOSED | A-03, D-01 |
| [008](ADR-008-quote-handle.md) | Opaque quote handle & server render | PROPOSED | — |
| [009](ADR-009-idempotency-unknown-outcome.md) | Idempotency & unknown ERP outcome | PROPOSED | D-03 |
| [010](ADR-010-identity-session.md) | Identity/session demo & RBAC | PROPOSED | A-06, D-08 |
| [011](ADR-011-history-summary.md) | History policy-safe summary | PROPOSED | — |
| [012](ADR-012-logging-audit.md) | Logging/audit/observability | ACCEPTED 2026-11-18 | D-09 (mở) |
| [013](ADR-013-kill-switch-provider-failure.md) | Kill switch & provider failure | PROPOSED | — |
| [014](ADR-014-money-vnd.md) | Money/VND | PROPOSED | D-04, D-13 |
| [015](ADR-015-endpoint-exposure.md) | Phơi bày endpoint demo/inspector/ops | PROPOSED | — |
| [016](ADR-016-egress-capture.md) | Egress capture & network route | ACCEPTED 2026-11-18 | — |
