# Proposal traceability — preliminary (not stakeholder-approved)
| Source | Requirement / boundary | Runtime owner | Verification |
|---|---|---|---|
| Original pp. 3–4 | Modular monolith and single AI Gateway | architecture/network | egress route review |
| Original pp. 4–5 | Restricted price/cost/debt/customer data | ERP connector + DLP | SEC-01, SEC-05 |
| Original pp. 5–6 | Server-side customer identity and safe tool response | auth + tool manager | SEC-02, SEC-03 |
| Original p. 7 | Five-layer rights, write approval human-only | IAM + quote + ERP | permission matrix + SEC-03 |
| Original p. 8 | SEC-01..SEC-07 | QA + security | executable evidence matrix |
| Original pp. 9–10 | VIN/OE provenance, quote version, reprice, idempotency | catalog + quote | domain & integration tests |
| Original pp. 12–14 | Workstreams, UAT, Arito API uncertainty | product manager | decision gates |

## Evidence vocabulary
- SOURCE FACT: explicitly in proposal; does not guarantee Arito/provider behavior.
- DESIGN PROPOSAL: engineering recommendation in this package.
- VERIFIED: demonstrated by command/log/test evidence against identified environment.
- OPEN: requires Arito/business/security/legal confirmation.
