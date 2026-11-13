---
paths:
  - "backend/**"
  - "security-tests/**"
---
# Security implementation rules
- Fail closed for unknown egress fields, model/tool outputs, roles and tokens.
- No raw JPA entity, generic Map or ERP object may be serialized to LLM. Typed DTO with explicit allowlisted fields only.
- Bind tenant/customer/ownership from verified server-side principal. Tool arguments are untrusted.
- Restrict internet egress at deployment layer; runtime tests must intercept entire provider boundary.
- Never log message bodies, customer-specific prices, secrets or raw API responses.
- Do not rely on prompts, regex-only sanitization, frontend role checks or UI-only disabling as security boundaries.
- All write operations require authorization, version checks, idempotency and audit.
