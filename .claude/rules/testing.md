---
paths:
  - "backend/src/test/**"
  - "security-tests/**"
  - "frontend/**"
---
# Test rules
- Test happy path plus deny, injection, cross-customer, replay, timeout and concurrent requests.
- SEC-01..07 are release gates; report counts and capture sanitized evidence.
- Never claim tests pass without executing them.
- Snapshot and fixtures must contain invented data, not customer content.
- A failure in data egress or authorization blocks release, even if UX works.
