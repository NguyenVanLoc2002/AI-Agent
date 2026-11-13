---
paths:
  - "backend/**/quote/**"
  - "backend/**/order/**"
  - "backend/**/erp/**"
---
# Quote / draft order state machine
- Statuses: DRAFT → ISSUED → CONFIRMED → DRAFT_ORDER_CREATED → STAFF_APPROVED / REJECTED. Side paths: EXPIRED, SUPERSEDED, CANCELLED. Guard every transition and return explicit error.
- Only verified catalog/ERP item code enters automatic pricing. AI-generated OE is NEVER source of truth.
- Quote lines include product ID, quantity, currency, unit price snapshot, total, source revision; validate quantity >0 and bounded 20 lines (proposed limit pending business approval).
- Price/stock/debt are checked inside protected service and never sent to LLM. New quote version if price differs; require fresh confirmation.
- Confirmation from authenticated UI control bound to quote ID+version+customer; free-form 'ok' not legal confirmation.
- DB unique key `(quote_id,quote_version)` for creation request; idempotency key forwarded to ERP only if API supports it. For timeout after write, reconcile by reference; if absent mark UNKNOWN and human queue.
- AI has no API to approve, edit prices, override account limits or confirm on behalf of customer.
- Test: expired, stale version, wrong customer, changed stock/price, debt limit, duplicate webhook, parallel confirm, retry after timeout, ERP collision.
