# Architecture Decision Records

Trạng thái: PROPOSED = chờ phê duyệt; ACCEPTED/REJECTED/SUPERSEDED chỉ khi owner ký trong file. Tất cả ADR dưới đây được tạo 2026-11-13 ở trạng thái **PROPOSED** (DESIGN PROPOSAL). ADR đã ACCEPTED thắng văn xuôi (CLAUDE.md).

| ADR | Tiêu đề | Trạng thái | Quyết định cần duyệt |
|---|---|---|---|
| [001](ADR-001-modular-monolith.md) | Modular monolith & enforcement ranh giới | PROPOSED | A-02 |
| [002](ADR-002-build-baseline.md) | Build tool & baseline | PROPOSED | A-04 |
| [003](ADR-003-data-classification-egress.md) | Phân loại dữ liệu & type-level egress boundary | PROPOSED | — |
| [004](ADR-004-ai-gateway.md) | AI Gateway & Fake provider cho PoC | PROPOSED | A-05, D-07 |
| [005](ADR-005-erp-ports.md) | Tách ERP ports + bucket tồn | PROPOSED | A-07, A-12 |
| [006](ADR-006-tool-contract.md) | Tool contract & per-call authz | PROPOSED | D-10, A-14 |
| [007](ADR-007-quote-state-machine.md) | Quote/draft order state machine | PROPOSED | A-03, D-01 |
| [008](ADR-008-quote-handle.md) | Opaque quote handle & server render | PROPOSED | — |
| [009](ADR-009-idempotency-unknown-outcome.md) | Idempotency & unknown ERP outcome | PROPOSED | D-03 |
| [010](ADR-010-identity-session.md) | Identity/session demo & RBAC | PROPOSED | A-06, D-08 |
| [011](ADR-011-history-summary.md) | History policy-safe summary | PROPOSED | — |
| [012](ADR-012-logging-audit.md) | Logging/audit/observability | PROPOSED | D-09 |
| [013](ADR-013-kill-switch-provider-failure.md) | Kill switch & provider failure | PROPOSED | — |
| [014](ADR-014-money-vnd.md) | Money/VND | PROPOSED | D-04, D-13 |
| [015](ADR-015-endpoint-exposure.md) | Phơi bày endpoint demo/inspector/ops | PROPOSED | — |
| [016](ADR-016-egress-capture.md) | Egress capture & network route | PROPOSED | — |
