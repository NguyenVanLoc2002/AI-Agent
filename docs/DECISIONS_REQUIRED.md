# Decision register — do not silently guess
| ID | Open decision | Owner | Impact until resolved | Safe PoC default |
|---|---|---|---|---|
| D-01 | Does the target ERP (vendor-neutral) provide the six required API operations, auth, sandbox and limits? | ERP vendor / integration owner | Real integration blocked | Mock connector only |
| D-02 | Can the target-ERP integration principal be denied cost access and approve-order? | ERP vendor / integration owner | Need connector+network compensating controls | Never connect live |
| D-03 | Does the target ERP support idempotency/external order lookup? | ERP vendor | Unknown-outcome retry dangerous | mark UNKNOWN + manual reconcile |
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
| A-04 | Build tool: POC-001 instruction chose **Maven** + Spring Boot 4.1.2 + Spring Modulith 2.1.2 + ArchUnit 1.5.1 (2026-11-13). Remaining: owner signs ADR-002 **APPROVED 2026-11-18: owner signed ADR-002 (Maven).** | ADR-002 |
| A-05 | PoC uses only Fake/WireMock LLM provider until D-07 resolved **APPROVED 2026-11-18 (ADR-004 ACCEPTED); D-07 still OPEN.** | ADR-004 |
| A-06 | Demo auth: form login + session cookie + synthetic users | ADR-010 |
| A-07 | Split ERP ports instead of single BRD `ErpConnector` **APPROVED 2026-11-18 (ADR-005 ACCEPTED).** | ADR-005 |
| A-08 | Provisional limits: 20 lines/quote, quote TTL, tool quotas | D-04, D-10, ADR-006 |
| A-09 | Allow branch creation, `backend/` + `frontend/` directories, local Docker/Testcontainers **APPROVED 2026-11-18.** | ADR-002 |
| A-10 | Customer-typed money/phone: redact + flag; honeytoken → block + handoff **APPROVED 2026-11-18; implemented in SEC-01a (`policy.InboundSanitizer`).** | DATA_CLASSIFICATION §5 |
| A-11 | Masked/WMI-only VIN and no images to provider in PoC **APPROVED 2026-11-18; enforced in SEC-01a (`TechnicalContext.maskedVin`, sanitizer, egress `FULL_VIN` block). D-11 still OPEN.** | D-11 |
| A-12 | Quantity-independent stock bucket design (`AvailabilityBucketPort`) **APPROVED 2026-11-18 (ADR-005 ACCEPTED); bucket thresholds in the Mock are DESIGN PROPOSAL, D-12 still OPEN.** | D-12, ADR-005 |
| A-13 | Branch naming for first slice: `feat/SEC-01-egress-guard` (rule 70) vs issue-ID based **APPROVED 2026-11-18: rule-70 style with slice suffix; first slice `feat/SEC-01a-egress-guard`.** | rule 70 |
| A-14 | Bot never tells the customer *why* a quote was not issued (single `NOT_QUOTED` + staff handoff) — business acceptance of this UX consequence | ADR-006, BR-35 |
| A-16 | Independent demo rebranding to AutoParts Agent Sandbox (APAS), 2026-11-16 (PROPOSED). Confirm: (a) project identity and disclaimer; (b) the reference BRD/proposal stay private and reference-only (NEEDS_REVIEW for usage rights); (c) git history still contains the reference customer/vendor names, and the GitHub repository is already public (checked 2026-11-16), so the owner must decide how to handle it, e.g. make it private or get legal review (history was **not** rewritten). **(c) DECIDED 2026-11-18:** the owner made the GitHub repository **private** (unauthenticated GitHub API returned 404 on 2026-11-18). The owner states that the sample data (customers, prices, phone numbers, emails, orders, VINs in prototypes, design-system bundle and fixtures) is fake/synthetic; this statement does **not** cover the reference company name, the ERP vendor name or the reference proposal content in git history. History is still not rewritten; copies cloned, forked or cached while it was public cannot be recalled. Before any return to public: decide between a history-free export and a history rewrite, and finish (d) first; (d) a separate code issue renames the remaining vendor names in code/config | `docs/REBRANDING_REPORT.md` |
| A-17 | Proposed logo (`design-system/assets/logo/`, option A) vs design-system README "No logo exists". It also uses AI-violet, which the README reserves for AI provenance. Trademark search not done | `docs/design/specs/95-logo-phuong-an.md`, `96-logo-su-dung.md` |
| A-18 | Claude Design replaces the Stitch plan (2026-11-17). Confirm: (a) `design-system/` + `docs/design/` are the UI design source (DESIGN PROPOSAL); (b) request a full export with component `.jsx` sources before `frontend/` starts, because the current bundle self-renders a demo app and is not importable; (c) `docs/design/evidence/*.png` stay out of git (they show the former vendor name) and are re-captured from the neutralised canvas | `docs/design/DESIGN_GUIDE.md`, `design-system/EXPORT-NOTES.md` |
| A-15 | SALES access after handoff resolved (proposed PoC default: content access ends at resolve) and SALES manual recording of staff approve/reject (no ERP read-back) | D-08, D-01, ADR-007 |

## OPEN DECISIONS from the SEC-01a reviews (2026-11-18)
architecture-reviewer and security-auditor (advisory) on the SEC-01a diff. Code in SEC-01a does not depend on these; the next issues do.

| ID | Decision needed | Blocks | Proposed default |
|---|---|---|---|
| A-19 | How identity builds `AuthenticatedCustomerRef` from a `CustomerLinkPort` result. ERP_CONNECTOR §3.1 returns the ref from an spi that `erp` implements, but only `identity` can construct it (ARCH-08a). | ADR-010, EP-04 | spi returns an opaque link result; identity wraps it |
| A-20 | How `tools` reaches the egress DTOs: map to `gateway.api` types (`tools` allowlist gains `gateway :: api`) or return internal results that `agent` maps. `tools.api.dto` cannot extend the sealed `ToolResultDto` from another package. | EP-03 | tools maps to `gateway.api` |
| A-21 | Which module calls `policy.InboundSanitizer` (`conversation` or `agent`) and gets `policy` in its allowlist. | EP-04 | conversation, at message intake |
| A-22 | A separate marker/package for untrusted model output (`ModelResponse`, `ToolProposal`), so it cannot be mistaken for approved egress data or replayed into history. | EP-04 | `gateway.api.inbound` without `@EgressClassified` |
| A-23 | ERP adapter selection: explicit property (e.g. `apas.erp.adapter=mock`) with startup failure when none is chosen, before a second adapter exists. | EP-11 | explicit property |
| A-24 | Real OE/SKU values that look like phone numbers (e.g. 10-digit part numbers starting `09`/`03`) are BLOCKed as PHONE in `ProductRef.oe`/`sku`. Keep scanning ERP-sourced codes (current, fail-closed) or exempt them. | EP-05 | keep scanning; revisit with real catalog data |
| A-25 | Category taxonomy: `ProductRef.category` / `partCategory` now accept letters and spaces only (DESIGN PROPOSAL). Replace with a closed server-side category code list. | EP-05 | closed code list |

## UX — Claude Design output vs approved sources (found 2026-11-17)
`docs/design/` (specs, decisions, prototypes) is DESIGN PROPOSAL. Until each item is decided, the ADR / contract / RBAC column wins and the screen is BLOCKED for implementation. Design log IDs `Dxx` in `docs/design/specs/91` are written `UX-Dxx` outside that folder; they are not `D-xx` above.
| ID | Conflict | Design source | Approved source | Safe default until decided | Related |
|---|---|---|---|---|---|
| UX-01 | Staff AI assistant (Batch B) calls agent tools; tool names differ from the contract; a VIN lookup is a tool argument | `specs/10-batch-b-khong-gian-ai.md`, `decisions/02` | `contracts/TOOL_CONTRACTS.md` ("Nhân viên không dùng agent tool trong PoC"; allowlist; VIN not a tool) | Staff AI is read-only explanation over backend-provided DTOs; no staff tools; VIN via `vinRef` only | ADR-006, D-11 |
| UX-02 | Staff search a customer by phone number / customer code | `specs/20` C5, `decisions/03` | `contracts/ERP_CONNECTOR.md` (no lookup by name/phone; no `searchCustomers`) | Not in PoC | D-08, rule 10 |
| UX-03 | SALES presses "create draft order" after customer confirmation (new "Chờ tạo đơn" step) | `decisions/04` B4, `specs/30` D3 | `security/RBAC_MATRIX.md` (SALES ✘ create draft order); UC-04 actor = customer | Draft order only via the customer's authenticated confirmation | ADR-007, A-03 |
| UX-04 | Staff author a quote for any customer (C5 new quote, edit lines, reprice) | `specs/20` C5 | `requirements/USE_CASES.md` UC-03 (customer from authenticated session); RBAC_MATRIX | Not in PoC; needs a new UC + permission | D-08 |
| UX-05 | Customer confirms with a chat text "Xác nhận BG-… vN" | `decisions/02`, `specs/20` C7 | BR-14, rule 40, `CLAUDE.md` (authenticated action bound to quote ID/version) | Chat text only leads to the authenticated confirm control | ADR-007 |
| UX-06 | Quote state labels differ from ADR-007; adds "Đã phát hành · chưa xác nhận gửi", "Chờ tạo đơn"; lacks CONFIRMATION_BLOCKED, ORDER_FAILED | `decisions/03` B1, `specs/20` | ADR-007 | ADR-007 enum; UI label mapping table required | A-03 |
| UX-07 | Model sees sequential placeholders `{BAO_GIA_1}` | `decisions/02`, `specs/20` | ADR-008 (random 128-bit handle, bound, TTL); rule 30 | Opaque handle per ADR-008; placeholder is display wording only | ADR-008 |
| UX-08 | Unknown ERP outcome: "look up by key, then retry with the same key"; retry button on ERP 5xx | `specs/30` D3, `specs/91` UX-D57 | ADR-009 (capabilities default false → OUTCOME_UNKNOWN + manual reconcile, no write retry) | Manual reconcile queue, no "create again" | D-03 |
| UX-09 | VAT: design keeps R4 BLOCKED (no assumption either way) | `decisions/03` R4, `specs/02` | ADR-014 / D-13 display "chưa gồm VAT" | Do not compute a payable total; wording decided by owner, then ADR-014 or design updated | D-13 |
| UX-10 | Approval/rejection status, reason and notifications read back from the ERP; "Mở trên ERP" deep link | `specs/30` D4, `decisions/04` | `contracts/ERP_CONNECTOR.md` (no approval read port; SALES records manually) | SALES records staff decision manually | D-01, A-15 |
| UX-11 | Extra roles/permissions: department lead acting for staff and creating orders, "Giám đốc" approver, Restricted Employee persona, SECREV reading business audit, admin "special permission" | `decisions/04`, `decisions/05`, `specs/20`, `specs/30`, `specs/40` | RBAC_MATRIX (LEAD ✘ MVP; SECREV only redacted traces) | RBAC_MATRIX as is | D-08 |
| UX-12 | Session: 8 h max, 30 min idle, warning 5 min before, lockout after failed logins, password change ends other sessions | `decisions/05`, `specs/40` | ADR-010 (no timeout rules) | PROPOSED; add to ADR-010 with T-AUTH tests before coding | A-06 |
| UX-13 | Coverage: no screens for Customer Chat (UC-01), Inbox/Handoff (UC-05/06), Security Inspector (UC-07), kill switch (UC-08); screens B1–B9, C5, D1–D4, E1, E3, E4 have no UC | `specs/90`, `README.md` "Chưa thiết kế" | `requirements/USE_CASES.md`, rule 50 required views | Missing screens designed before EP-10; UC-less screens stay PROPOSED | A-02 |
| UX-14 | Zalo OA marked "CÓ CĂN CỨ" as main send channel; invents `ZOA-*` error codes and send states | `decisions/03` Q6, `specs/20`, `specs/92` | D-06 OPEN (local chat simulator) | Generic "kênh gửi" + simulator; no Zalo codes | D-06 |
| UX-15 | Staff see list price and per-branch stock quantities | `specs/00` PL-04, `specs/92` | `contracts/ERP_CONNECTOR.md` (`priceFor(AuthenticatedCustomerRef, Sku)`; stock quantity INTERNAL); BR-34 OPEN | Availability bucket only | D-12, A-12 |
| UX-16 | Opening another user's resource shows "Không có quyền" | `specs/10`, `specs/20`, `specs/90` | RBAC_MATRIX (other user's resource → 404, existence not revealed) | "Không tìm thấy" | rule 10 |
| UX-17 | Handoff reasons: 4 in design vs 3 in contract | `specs/10` | `contracts/TOOL_CONTRACTS.md` `requestHandoff` enum; THREAT_MODEL | Contract enum | ADR-006 |
| UX-18 | Idempotency key shown as display code `BG-…-v2` | `specs/30` | ADR-009 (`quoteId:version`, server-generated); BR-17 | Server key per ADR-009; UI shows a reference only | ADR-009 |
