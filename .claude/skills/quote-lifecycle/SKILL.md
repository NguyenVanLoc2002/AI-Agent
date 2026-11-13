---
name: quote-lifecycle
description: Design safe quote and draft-order lifecycle
---
# Workflow
Map states DRAFT→ISSUED→CONFIRMED→DRAFT_ORDER_CREATED→staff decision plus EXPIRED/SUPERSEDED/CANCELLED. Verify catalog provenance; compute decimal quote in backend and snapshot price. Require explicit authenticated quote/version confirmation, recheck price/stock/debt, create a new version on price change. DB uniqueness + transaction + idempotency; on ERP timeout after write mark UNKNOWN and reconcile, never blind retry. Test wrong customer, expired, version mismatch, repricing, parallel x10, unknown outcome and duplicated webhook. Flag unresolved Arito API support.

## Required result
Separate VERIFIED, NOT RUN, BLOCKED and PROPOSED. Include exact paths and relevant decision IDs.
