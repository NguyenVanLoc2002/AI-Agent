---
paths:
  - "backend/**/gateway/**"
  - "backend/**/agent/**"
  - "backend/**/tools/**"
  - "**/prompts/**"
---
# AI gateway and tools
1. Accept `ModelRequest` containing typed `SafeMessage`, `TechnicalContext` and approved `ToolSpec`; never accept JPA entity, DTO inheritance from ERP or arbitrary JSON blob.
2. Treat both user turns AND previous assistant turns as potential price/PII containers. Construct conversation history with policy-safe summaries, not raw replay.
3. Sanitization pipeline: content-type/size → structural separation → detect and redact/reject private fields → schema allowlist → final serialized payload preflight → egress log of field names + HMAC/length only.
4. Define allowlisted tool names per principal/channel. Tool suggestions are nonbinding and must be validated again on execution. Prevent coercion through aliases and unexpectedly nested keys.
5. Tool responses to model expose technical reference + provenance + verified status + coarse availability + opaque quote handle. No personalized price, stock number, internal cost, debt flag, customer identifier.
6. Model output is untrusted. Accept only validated structured intent or safe text; reject invented OE/price claims, unauthorized links, invalid placeholder tokens. Generate user-visible quote server-side with bound template, never naive global string replace.
7. Feature switch emergency-disable, per-tenant quota, timeout, circuit breaker, bounded retries on *read-only* tools; no hidden changes of provider or data region.
8. Tests must capture actual serialized request at provider adapter and fail on honeytokens (including nested metadata, error branches, tool results and retry paths).
9. `store=false` does not imply provider ZDR. Record provider configuration, retention and legal approval outside prompts.
