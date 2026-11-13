# Security invariants: apply across code, tests, prompts, integrations
## Information flow
- Classify: RESTRICTED = cost/margin, individual customer price, debt/credit, directory, purchase history, credentials, personal details; INTERNAL = stock quantities/location, quote internal state; APPROVED_TECHNICAL = validated VIN/OE, product category, public technical descriptors, sanitized part photo.
- Only gateway may invoke public model provider. Classify every input segment incl. history/tool outputs/errors/traces/images; explicit allowlist on DTO fields, no blind object-to-JSON.
- For a suspected leak: prevent outbound call, emit redacted security event, hand off. Never 'send first then inspect'.
- Approved VIN can still be sensitive/identifying in context; do not declare VIN automatically anonymous or compliant with transfer rules.
## Authorization
- Resolve authenticated principal, customer/tenant and channel binding before tool execution. No client/provider-supplied customer ID used for authorization; reject cross-tenant and unlinked identity.
- Permissions on read and write, for each tool and each resource, independent of model instructions and frontend control state. Admin role is not automatically business-data reader.
- Reject unknown tool names, missing/extra arguments, oversized fan-out, pagination/aggregation attempts and prompt-injected tool output. Return sanitized error codes.
## Execution
- Fail closed on unknown fields/model output, catalog claims, missing source, stale quote, changed pricing/stock, debt status or outage. Human fallback, with reason code.
- No raw document uploads for vehicle registration; local OCR, structured safe extraction, metadata stripping for allowed part images, retention review.
- Logs, tracing, APM, exception stack dumps and test snapshots may contain data: record event ID, pseudonymous trace, policy code, field names only. Never raw message bodies, quote prices, debt, secrets.
- No guarantees of zero leakage from prompts alone. Validate gateway egress, network routing and provider retention settings separately.
## Security acceptance
- Include SEC-01 (200 chats/honeytokens), SEC-02 (20 cross-customer attempts), SEC-03 (50 adversarial prompts), SEC-04 (10 concurrent confirmations + unknown ERP outcome), SEC-05 (7 days logs), SEC-06 (kill switch <=1 minute), SEC-07 (key revocation and safe fallback).
