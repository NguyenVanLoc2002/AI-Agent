# Secure ERP AI Agent – Claude Code Project Memory

## Mission
Build a Java/Spring Boot modular-monolith security-first AI Agent demo from `docs/01_BRD_SRS_Security.md`. The supplied Arito proposal is a design source, NOT proof of actual ERP API capabilities. Prefer Mock ERP until API docs/sandbox are verified.

## Before coding
1. Read relevant use case, security rules and acceptance tests.
2. Inspect repository and git status; never assume files/commands exist.
3. Present a small implementation plan, file list and test strategy before any multi-file change.
4. Keep work scoped to a single issue; do not silently expand scope.

## Non-negotiable runtime invariants
- AI/LLM never receives purchase costs, individual customer pricing, credit/debt, customer directory/purchase history, customer identities, or secrets.
- customerId is exclusively server-bound to authenticated conversation; never trust model/user-supplied customerId.
- LLM tools are advisory: authorize, validate and execute each call at backend boundary.
- Quotes are calculated server-side, and sensitive values are rendered AFTER the LLM response.
- No autonomous order approval/price editing; confirmations must bind quoteId + version, check price/stock/eligibility and use idempotency.
- Default deny provider egress on unknown fields; do not send raw images of registration papers.
- Logs/telemetry must not contain keys, raw PII, prices or debt.

## Delivery conventions
- Modular monolith; ports/adapters (ErpConnector, AiProvider), Java 21, Spring Boot, PostgreSQL, Flyway, JUnit5, Testcontainers by default; verify actual project stack before changing.
- Prefer records/DTOs with minimal fields, BigDecimal for money, explicit policy decisions, UTC timestamps, stable enums.
- Write focused tests for each risk (authorized, unauthorized, replay, concurrent, expired, timeout).
- Never disable failing security tests, bypass safeguards, invent Arito APIs or fabricate test results.
- No uncontrolled external network calls, secret uploads, database migrations or destructive shell commands without user review.
- Do not auto-commit, auto-push or open PR without approval. Suggest conventional commits, never AI attribution/filler in commit messages.

## Context map
- `docs/01_BRD_SRS_Security.md`: domain + requirements.
- `docs/03_UI_UX_Stitch.md`: UI flows and approved design guidance.
- `docs/04_Implementation_Backlog.md`: issue ordering and DoD.
- `.claude/rules/`: hard engineering constraints; `.claude/skills/`: repeatable workflows; `.claude/agents/`: independent reviews.

## End-of-task response
Summarize changed files, risks, test commands and actual pass/fail outcomes, remaining assumptions and recommended next issue. If tests were not run, state NOT RUN.
