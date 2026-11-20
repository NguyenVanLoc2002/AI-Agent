-- EP-09b central audit_event (UC-07, ADR-012 ACCEPTED; owner decisions 2026-11-20, DECISIONS_REQUIRED A-45).
-- Module audit. Metadata only: action, resource type, outcome code, actor pseudonym and role, trace ID (the HTTP
-- correlation ID), time. No identifier of the resource (conversation, quote, draft order request, customer key),
-- no bodies, no values, no per-value hashes (decision 6A, TH-15). The actor of a customer is the customer's
-- pseudonymous principal: stored, never shown by the inspector (A-45(d)); retention D-09.
-- Kill switch changes stay in feature_switch_event (decision 4A).
-- Writes of a business change join its transaction (rolled back together, fail closed); denials and reads are
-- written in their own transaction (decision 2A).
-- Additive: one new table and one nullable column (below).
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09, decision 7A); no purge job; the inspector reads at most 7 days per query.
create table audit_event (
    id            bigint      generated always as identity primary key,
    trace_id      varchar(36) check (trace_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
    action        varchar(40) not null check (action in ('ACCESS_DENIED', 'HANDOFF_CLAIMED', 'HANDOFF_RESOLVED',
                      'STAFF_MESSAGE_POSTED', 'STAFF_QUOTE_READ', 'STAFF_ORDER_DECIDED', 'ORDER_RECONCILED',
                      'QUOTE_CONFIRMATION', 'INSPECTOR_READ')),
    resource_type varchar(20) not null check (resource_type in ('HANDOFF', 'CONVERSATION', 'QUOTE', 'DRAFT_ORDER',
                      'KILL_SWITCH', 'SECURITY_TRACE', 'SECURITY_EVENTS')),
    outcome       varchar(40) not null check (outcome ~ '^[A-Z][A-Z_]{1,39}$'),
    actor         varchar(26) check (actor ~ '^usr_[A-Za-z0-9_-]{22}$'),
    actor_role    varchar(10) check (actor_role in ('CUST', 'SALES', 'OPS', 'SECREV')),
    occurred_at   timestamptz not null,
    check ((actor is null) = (actor_role is null))
);

create index audit_event_occurred_at_idx on audit_event (occurred_at, id);
create index audit_event_trace_id_idx on audit_event (trace_id);
create index audit_event_action_idx on audit_event (action, occurred_at);

-- A-45(h), DESIGN PROPOSAL: the request ID (the HTTP correlation ID, server-generated, never sent to the provider)
-- on egress rows, so one trace groups what a request did. egress_record.trace_id stays the per-call ID the
-- provider sees; it is not replaced by the turn ID (that would hand the provider an ID the customer sees).
-- The same column on tool_invocation (module tools) is V15.
-- Additive: one nullable column; rows written before V14 keep null.
alter table egress_record add column request_id varchar(36)
    check (request_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$');
create index egress_record_request_id_idx on egress_record (request_id);
