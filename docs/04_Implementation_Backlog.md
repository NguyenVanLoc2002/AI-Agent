# IMPLEMENTATION BACKLOG

| ID | Work | Prerequisite | Completion evidence |
|---|---|---|---|
| EP-01 | Mock ERP fixture, server-side identity mapping | none | cross-customer data isolation tests |
| EP-02 | Egress Gateway + typed allowlist + DLP | EP-01 | SEC-01 evidence, deny unclassified DTO |
| EP-03 | Tool Registry & per-action auth | EP-01 | unauthorized tool tests + SEC-03 |
| EP-04 | Customer chat + AI adapter fallback | EP-02,03 | trace ID and no price in provider request |
| EP-05 | Catalog simulation + verification labels | EP-01 | no unverified OE quote |
| EP-06 | Quote service and server-side rendering | EP-01,05 | version/expiry/price change tests |
| EP-07 | Confirm + draft-order idempotency | EP-06 | SEC-04 concurrent test |
| EP-08 | Staff handoff + RBAC | EP-04 | unauthorized access blocked |
| EP-09 | Audit and kill switch | EP-02 | SEC-05,06,07 |
| EP-10 | UI guided by approved Stitch wireflows | EP-04,06,08,09 | accessibility and E2E |
| EP-11 | Actual Arito adapter (separate gated release) | API docs & sandbox approved | ERP contract tests |
| EP-12 | Zalo/VIN provider integrations | provider access/licence | end-to-end/UAT |

## Standard Definition of Done
- Requirement/traceability link, threat review where relevant, happy + negative tests, schema validation, no sensitive egress, no secrets in logs, approved diff, build/test evidence, updated docs, reversible change; no unapproved production actions.
