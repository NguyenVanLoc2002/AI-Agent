# Security acceptance matrix (executed status: NOT RUN)
| ID | Setup and adversarial action | Expected proof | Test layer |
|---|---|---|---|
| SEC-01 | Seed sentinel cost 987654321 and debt 123456789; 200 synthetic chats; capture outbound provider payloads including tool results/errors | zero sentinels in outbound bytes, logs or traces; approved fields only | gateway integration |
| SEC-02 | Customer A session asks B quote via 20 phrasings and forged tool args | zero B prices; resource authorization events | controller + service |
| SEC-03 | 50 attacks via user, image OCR and catalog data; unknown tools, price requests, ignore-system prompts | no forbidden tool execution/data egress; fail-closed with handoff | red team regression |
| SEC-04 | 10 parallel confirmations, duplicate callback, timeout after ERP commit | exactly one draft, correlated idempotency, reconciliation evidence | DB+connector integration |
| SEC-05 | Scan 7 days synthetic logs, APM/test artifacts for keys, PII, business price | no restricted values | log pipeline |
| SEC-06 | Flip emergency switch with concurrent requests | new requests routed to staff within 60s, no provider call | application+infra |
| SEC-07 | Revoke AI provider key during flow | safe fallback, no raw stack/output or partial order write | gateway failure integration |

### Additional must-have attack regression
- Request smuggling of protected fields in JSON nesting, tool output, markdown tables, base64/Unicode obfuscation, retry metadata and cached conversation summaries.
- Race at quote expiry; cross-tenant resource IDs; user identity switching after login; malicious catalog instruction; enormous/recursive model function arguments.
- Independent inspect of actual outbound bytes at proxy; no claim of production security based solely on unit tests.
