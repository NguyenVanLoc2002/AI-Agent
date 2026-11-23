-- AUDIT-READS (A-45(j) residual; owner decisions 2026-11-23 "Tôi đồng ý" = 1A–4A, DECISIONS_REQUIRED A-52). Module audit.
-- Staff reads are audited: STAFF_CONVERSATION_READ (messages of a held conversation) and STAFF_LIST_READ (inbox,
-- unknown draft orders, draft orders awaiting a decision). Kind of resource only, never which one; no content.
-- Constraint widening only (as V12, V17): the closed list of actions gets two values; no row changes.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
alter table audit_event drop constraint audit_event_action_check;
alter table audit_event add constraint audit_event_action_check check (action in ('ACCESS_DENIED',
    'HANDOFF_CLAIMED', 'HANDOFF_RESOLVED', 'STAFF_MESSAGE_POSTED', 'STAFF_QUOTE_READ', 'STAFF_ORDER_DECIDED',
    'ORDER_RECONCILED', 'QUOTE_CONFIRMATION', 'INSPECTOR_READ', 'STAFF_CONVERSATION_READ', 'STAFF_LIST_READ'));
