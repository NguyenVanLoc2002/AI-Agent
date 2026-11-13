# Arito Secure ERP Agent — repository operating contract

> Scope: security-first PoC (Mock Arito) that evolves into an integrated MVP. The 15-page customer proposal dated 2026-10-14 is **proposed behavior**, not evidence of a live Arito API. Never claim verified integration without approved API documentation and tests.

## Precedence and source of truth
1. System/developer permissions and approved security controls outrank this file.
2. Approved ADRs and versioned `docs/contracts/` outrank prose assumptions.
3. Approved BRD and original proposal supply business intent; where ambiguous, record an OPEN DECISION before coding.
4. Existing code is not necessarily correct. Diagnose rather than copy defects.
5. Do not invent API routes, ERP authorization features, VIN/OE matching confidence, Zalo conditions, discounts or legal compliance.

## Repository reconnaissance, every new issue
- `git status --short`, inspect relevant source, build file, tests, architecture decisions and latest issue/acceptance criteria.
- State scope, affected modules, threat/data classification, tests and rollback before edits. For ambiguous or destructive changes ask approval.
- One issue = one verifiable vertical slice; no unrelated formatting or refactors.
- Never claim a test passed unless it ran and exit status was inspected. Record NOT RUN with reason.

## Mandatory security invariants (runtime code, not prompting)
- The LLM receives only provider-approved technical input (after policy checks) and strictly allowlisted tool response DTOs; no cost, personalized prices, debt/credit, customer list/history, IDs, secrets, raw registration images.
- Even anonymized input can contain personal/confidential data. Outbound inspection is fail-closed; redact, reject or request human handling. Model provider storage/ZDR must be separately verified.
- Identity, active customer, tenant and role derive from authenticated server-side context, never user text or tool arguments.
- AI tool calls are proposals; schema validation, authorization, resource ownership, quotas and state transitions execute server-side **for every call**.
- Pricing/stock/debt checks stay internal. AI sees only permitted part identity, verified provenance, coarse availability and opaque quote token; customer quote rendered by backend after model completion.
- AI cannot approve ERP orders, modify prices, read costs, create customers, bulk enumerate customers/products or override staff.
- Quote confirmation requires authenticated action tied to quote ID/version, not vague chat agreement; recheck price/stock/eligibility, atomic idempotency + reconciliation on unknown ERP outcome.
- Input, tool outputs, catalog strings, images, retrievals and model responses are untrusted. Never use prompt instructions as an authorization boundary.
- Stop on DLP uncertainty, provider outage, missing source, invalid VIN/OE provenance, expired quote or ambiguous customer; surface safe error and handoff.

## Architecture & coding
- Preferred: Java 21, Spring Boot modular monolith, Postgres/Flyway, JUnit 5, Testcontainers; detect actual build before changing.
- Package boundaries: identity, conversation, agent, gateway, tools, catalog, erp, quote, order, audit. External services behind ports; mock and real adapters use same typed contracts.
- Domain uses `BigDecimal` with explicit currency/rounding; UTC `Instant`, explicit transaction boundaries, optimistic versioning, DB unique constraints, validation at boundaries.
- No generic `Map<String,Object>` to LLM, no direct entity serialization, no arbitrary model-driven SQL, no provider SDK outside gateway.
- Existing source changes require tests for success/failure/permission/replay/timeout and migration impact when applicable.

## Development permissions & delivery
- Work in sandbox/local with synthetic data only. Never access production Arito/Zalo, export DB, install extensions, run destructive migrations, change auth/network policy, alter repo settings, push, merge, publish or create remote issues without explicit approval.
- Never commit secrets, customer data, raw request/response logs, image metadata, dotenvs or screenshots showing live information.
- For nontrivial changes, request reviews by `architecture-reviewer`, `security-auditor` or `qa-gatekeeper` as relevant; reviewer agents are advisory, not proof of compliance.
- Git: branch `feat/<issue>-slug`, `fix/<issue>-slug`, `docs/<issue>-slug`; conventional commit e.g. `feat(quote): enforce versioned confirmation`; no fabricated references, unnecessary AI attribution or auto-commit.
- Final handoff: issue/scope; changed files; decisions; tests+exact commands/outcomes; threat cases; migration/rollback; unresolved risks; next task.

## Where to read next
- `docs/TRACEABILITY.md` maps business proposal → controls → test evidence.
- `docs/DECISIONS_REQUIRED.md` identifies externally unverified dependencies.
- `.claude/rules/` applies focused engineering requirements; `.claude/skills/` contains operator workflows; `.claude/agents/` contains reviewers.
- `.claude/settings.json` sets conservative tool permission hints. It is defense in depth; enforce enterprise policies outside the repository.
