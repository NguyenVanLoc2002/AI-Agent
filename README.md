# AutoParts Agent Sandbox (APAS)
**AI Agent for Automotive Parts & ERP Workflows** — Independent Secure AI Agent & ERP Integration Demo

> AutoParts Agent Sandbox is an independent technical demonstration project using synthetic business scenarios and mock ERP data. It is not affiliated with, endorsed by, or commissioned by any automotive parts business or ERP vendor.

| | |
|---|---|
| Project code | APAS |
| Type | Independent technical demo / proof of concept |
| Domain | Automotive spare parts |
| ERP integration | Generic, vendor-neutral ERP connector; mock-first (Mock ERP, synthetic data) |
| Technology | Java 21, Spring Boot modular monolith (`backend/`) |
| Purpose | Show secure AI agent workflows for parts lookup, pricing, inventory, quotation and draft order management |

This repository holds the engineering kit (CLAUDE.md, `.claude/` rules, skills and reviewers), the design documentation (`docs/`) and the POC-001 backend skeleton. It is not a working application and not a certified security implementation.

## Proposed capabilities (none approved, none implemented)
VIN/OE part lookup; part identification or suggestion from images; price and stock lookup through the ERP connector; versioned quotes; draft orders with idempotency and audit; a permission-controlled staff UI; external channel integrations only with a valid, approved scope. Status and traceability: `docs/TRACEABILITY.md`, `docs/backlog/IMPLEMENTATION_PLAN.md`.

## Installation
Extract at the repository root and keep the hidden `.claude` directory. Merge any existing settings and CLAUDE.md rather than overwriting them. Read `docs/DECISIONS_REQUIRED.md`. Use the Mock ERP and synthetic data only. Run `claude` from the root and check `/doctor`, `/status`, `/permissions` and the installed skills. Review the tool permission settings for your OS and version before trusting them.

## Responsibility boundaries
- CLAUDE.md and rules = developer behavior/instructions, not runtime enforcement.
- Skills = repeatable work with input/output criteria.
- Subagents = read-only risk reviewers, not automatic certifiers.
- settings.json = CLI permission defense-in-depth, not security policy enforcement for every external tool.
- Actual security = backend authorization, strict outbound gateway, DB constraints, network egress, secret management, CI and manual audit.

## First vertical slice
SEC-01: a Mock ERP with synthetic cost/debt sentinels, a typed gateway safe payload, a field allowlist, a capturing provider transport, and an automatic 200-dialogue leakage suite that fails on forbidden fields or tokens. The slice covers tool results, history and failure paths.

## Source vs proposed
The business scenario and SEC-01..07 come from a private reference requirements document. It is not in this repository, is reference-only and is NEEDS_REVIEW for usage rights. Its content is **proposed behavior**: no customer or ERP vendor has approved, commissioned or verified it. The following are UNVERIFIED and must not be presented as facts: whether any real ERP API exists, external idempotency, an OEM catalog, messaging-channel (Zalo OA) details and provider retention. Design guidance in this kit is an engineering proposal until approved. In the demo context the Mock ERP is the authoritative data source. After a real integration, the integrated ERP becomes the source of truth for prices, stock and transactions, under a verified API contract.

Rebranding record: `docs/REBRANDING_REPORT.md`.
