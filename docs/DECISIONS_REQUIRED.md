# Decision register — do not silently guess
| ID | Open decision | Owner | Impact until resolved | Safe PoC default |
|---|---|---|---|---|
| D-01 | Arito has six required API operations, auth, sandbox, limits? | Arito/IT | Real integration blocked | Mock connector only |
| D-02 | Can Arito principal exclude cost and approve-order? | Arito/IT | Need connector+network compensating controls | Never connect live |
| D-03 | Supports idempotency/external order lookup? | Arito | Unknown-outcome retry dangerous | mark UNKNOWN + manual reconcile |
| D-04 | Pricing for newly unlinked customer and quote expiry? | Sales | Quote rules ambiguous | no automatic quotes |
| D-05 | Product/VIN/OE catalog license and provenance? | Purchasing | Automated matching unavailable | synthetic catalog only |
| D-06 | Zalo OA message/consent/webhook contract? | Business/Legal | Live channel blocked | local chat simulator |
| D-07 | Provider region, ZDR/DPA, retention, legal impact assessment? | Legal/Security | Real PII transfer blocked | synthetic messages only |
| D-08 | Roles, assignment logic and emergency break-glass? | Business/IT | RBAC policy incomplete | least privilege |
| D-09 | Data retention (12mo messages/30d images proposal), backup, deletion? | Privacy officer | Storage compliance undefined | synthetic local short retention |
| D-10 | Quote line limit proposed 20 and customer rate limit? | Business | Abuse controls | restrictive config + handoff |
| D-11 | May VIN (full or masked) and part photos be sent to an external model provider? (proposed 2026-11-13) | Legal/Privacy | VIN treated as context-identifying | send WMI/masked VIN only; no images in PoC |
| D-12 | Stock availability buckets independent of requested quantity + per-conversation query quota? (proposed 2026-11-13) | Business/Security | Stock-oracle risk | quantity-independent bucket + restrictive quota |
| D-13 | VND amount scale (VND normally has no minor unit), rounding mode, VAT, unit of measure (BRD §15)? (proposed 2026-11-13) | Finance/Sales | Totals provisional | scale/rounding from configuration, not hard-coded; display "excl. VAT / not determined" |

## Pre-coding approvals (from G0-1 documentation, 2026-11-13)
Engineering decisions awaiting the project owner. Details: `docs/architecture/adr/`.
| ID | Approval needed | Related |
|---|---|---|
| A-01 | Were `docs/01_BRD_SRS_Security.md`, `03_UI_UX_Stitch.md`, `04_Implementation_Backlog.md` deleted intentionally? They remain the cited source at commit `af2d09f`. Should the 15-page original proposal be added to the repo? | all docs |
| A-02 | PoC/MVP scope and backlog order (`docs/backlog/IMPLEMENTATION_PLAN.md`) | ADR-001 |
| A-03 | Canonical quote state names (proposed: rule 40 + ORDER_SUBMITTING/ORDER_OUTCOME_UNKNOWN/ORDER_FAILED) | ADR-007 |
| A-04 | Build tool: POC-001 instruction chose **Maven** + Spring Boot 4.1.2 + Spring Modulith 2.1.2 + ArchUnit 1.5.1 (2026-11-13). Remaining: owner signs ADR-002 | ADR-002 |
| A-05 | PoC uses only Fake/WireMock LLM provider until D-07 resolved | ADR-004 |
| A-06 | Demo auth: form login + session cookie + synthetic users | ADR-010 |
| A-07 | Split ERP ports instead of single BRD `ErpConnector` | ADR-005 |
| A-08 | Provisional limits: 20 lines/quote, quote TTL, tool quotas | D-04, D-10, ADR-006 |
| A-09 | Allow branch creation, `backend/` + `frontend/` directories, local Docker/Testcontainers | ADR-002 |
| A-10 | Customer-typed money/phone: redact + flag; honeytoken → block + handoff | DATA_CLASSIFICATION §5 |
| A-11 | Masked/WMI-only VIN and no images to provider in PoC | D-11 |
| A-12 | Quantity-independent stock bucket design (`AvailabilityBucketPort`) | D-12, ADR-005 |
| A-13 | Branch naming for first slice: `feat/SEC-01-egress-guard` (rule 70) vs issue-ID based | rule 70 |
| A-14 | Bot never tells the customer *why* a quote was not issued (single `NOT_QUOTED` + staff handoff) — business acceptance of this UX consequence | ADR-006, BR-35 |
| A-15 | SALES access after handoff resolved (proposed PoC default: content access ends at resolve) and SALES manual recording of staff approve/reject (no Arito read-back) | D-08, D-01, ADR-007 |
