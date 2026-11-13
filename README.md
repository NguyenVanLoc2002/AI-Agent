# Arito Claude Code engineering kit v2
This **replaces** the shallow prior kit; it is not a working application or a certified security implementation.

## Installation
Extract at repository root, retain hidden `.claude`. Merge rather than overwrite any existing settings and CLAUDE.md. Read `docs/DECISIONS_REQUIRED.md`. Use Mock ERP and only synthetic data until approvals. Run `claude` from root, check `/doctor`, `/status`, `/permissions` and installed skills; review the tool permission settings for your OS/version before trusting them.

## Responsibility boundaries
- CLAUDE.md and rules = developer behavior/instructions, not runtime enforcement.
- Skills = repeatable work with input/output criteria.
- Subagents = read-only risk reviewers, not automatic certifiers.
- settings.json = CLI permission defense-in-depth, not security policy enforcement for every external tool.
- Actual security = backend authorization, strict outbound gateway, DB constraints, network egress, secret management, CI and manual audit.

## First vertical slice
SEC-01: Mock ERP with synthetic cost/debt sentinels, typed gateway safe payload, field allowlist, capture provider transport, automatic 200-dialogue leakage suite with failure on forbidden fields or tokens. Include tool results, history and failure paths.

## Source vs proposed
Original customer document dated 2026-10-14 defines intent and SEC-01..07. Arito API existence, external idempotency, OEM catalog, Zalo OA details and provider retention are UNVERIFIED; do not mislabel these as facts. Design guidance in this kit is engineering proposal until approved.
