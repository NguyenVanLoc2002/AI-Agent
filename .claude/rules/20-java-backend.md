---
paths:
  - "backend/**"
  - "**/src/main/java/**"
---
# Java backend practices
- Feature modules expose narrow interfaces and typed commands/results; use controllers for transport only, services for transactions, policies for decisions, ports for external systems.
- Controller validates payload size and syntactic validity; domain validates state and invariant; DB uniqueness/transactions handle concurrency. Avoid catch-and-ignore, hidden retry, silent defaults.
- Money: `BigDecimal` and explicit VND policy; compare with `compareTo`, not `equals` where scale differs. No float/double for price/totals.
- IDs: internal opaque identifiers are server-controlled; tenant/customer ownership checked on every row access, including direct URL and asynchronous worker.
- Quote snapshots have immutable line items, price source, validity, version and state. Reprice and retest stock/debt on confirmation; never mutate issued price silently.
- Transactional outbox or equivalent for stateful external operations; Arito timeouts are UNKNOWN, not automatic failure. Reconcile using external reference/idempotency support when available. Without it, require human resolution rather than blind retry.
- Connector must not have `getCost`, `getAllCustomers`, `approveOrder` capability at its API surface. If ERP response has sensitive fields, strip at connector boundary before persistence/telemetry.
- Provider SDK used only in AI Gateway; tools operate on typed sanitized results. No arbitrary model-generated URL/SQL or reflection-based tool invocation.
- Use Flyway forward-only migrations with rollback notes and data retention strategy; no schema auto-update in nonlocal environments.
- Include correlation ID, sanitized error classification, auth audit event; never log request/response bodies or arbitrary exception causes containing secrets.
