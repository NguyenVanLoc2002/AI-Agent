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
