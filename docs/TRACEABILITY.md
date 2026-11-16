# Requirements traceability — preliminary (reference scenario, not stakeholder-approved)
> AutoParts Agent Sandbox (APAS) is an independent technical demo. "Original pp." are page references to a private reference proposal that is not in this repository and is NEEDS_REVIEW for usage rights. No customer or ERP vendor has approved these requirements.

| Source | Requirement / boundary | Runtime owner | Verification |
|---|---|---|---|
| Original pp. 3–4 | Modular monolith and single AI Gateway | architecture/network | egress route review |
| Original pp. 4–5 | Restricted price/cost/debt/customer data | ERP connector + DLP | SEC-01, SEC-05 |
| Original pp. 5–6 | Server-side customer identity and safe tool response | auth + tool manager | SEC-02, SEC-03 |
| Original p. 7 | Five-layer rights, write approval human-only | IAM + quote + ERP | permission matrix + SEC-03 |
| Original p. 8 | SEC-01..SEC-07 | QA + security | executable evidence matrix |
| Original pp. 9–10 | VIN/OE provenance, quote version, reprice, idempotency | catalog + quote | domain & integration tests |
| Original pp. 12–14 | Workstreams, UAT, ERP API uncertainty | product manager | decision gates |

## Detailed traceability (added 2026-11-13, G0-1)
- Business source: `docs/01_BRD_SRS_Security.md` at commit `af2d09f` (deleted in working tree — see A-01 in `docs/DECISIONS_REQUIRED.md`). The original reference proposal is private, is not in the repository and must not be added to demo artifacts (NEEDS_REVIEW). The page references above are preliminary.
- Use cases & rules: `docs/requirements/USE_CASES.md`, `docs/requirements/BUSINESS_RULES.md`.
- FR → UC → BR → control → test → SEC matrix: `docs/backlog/IMPLEMENTATION_PLAN.md` §3–§4.
- Contracts: `docs/contracts/`; security: `docs/security/`; architecture & ADRs: `docs/architecture/`.
- All test IDs are NOT RUN until executed with recorded command and exit code.

## Evidence vocabulary
- SOURCE FACT: explicitly in proposal; does not guarantee ERP/provider behavior.
- DESIGN PROPOSAL: engineering recommendation in this package.
- VERIFIED: demonstrated by command/log/test evidence against identified environment.
- OPEN: requires ERP vendor/business/security/legal confirmation.
