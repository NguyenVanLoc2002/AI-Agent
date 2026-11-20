-- EP-08b (UC-04, UC-06; RBAC_MATRIX; ADR-007; owner decisions 2026-11-20, DECISIONS_REQUIRED A-43).
-- Module order: SALES of the customer's team record the staff decision on a created draft order
--   (A-15: the approval itself happens in the ERP; no read-back, D-01). draft_order_request.status gains
--   STAFF_APPROVED and STAFF_REJECTED; both are final and keep the ERP draft number of the created draft.
--   The deciding staff member and the time are a draft_order_event row (existing table).
-- Module quote: quote_lock_lift records that staff resolved a conversation's handoff (without a
--   follow-up). The business refusals (A-34) of that conversation up to up_to_request_id (the highest
--   quote_request id the resolve saw) no longer lock the customer; refusals made in other conversations,
--   and later ones, still do. Ids, not timestamps: a refusal is stamped when its request started and may
--   commit after the resolve (EP-08b reviews); quote_request inserts of one conversation are serialised
--   by the quote advisory lock and quote_request.id is an identity with the default CACHE 1, so a later
--   commit always has a higher id (do not raise that cache). No customer key, no reason, no
--   staff identity here (the handoff row holds resolved_by).
--   quote_conversation_idx serves the staff quote view (newest quote of a conversation).
-- Change to existing data: the status check and the created-reference check on draft_order_request are
--   replaced by wider ones; existing rows satisfy both. One table and three indexes are added. Nothing
--   is dropped.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09).
alter table draft_order_request drop constraint draft_order_request_status_check;
alter table draft_order_request add constraint draft_order_request_status_check
    check (status in ('PENDING', 'SUBMITTED', 'CREATED', 'REJECTED', 'OUTCOME_UNKNOWN', 'RECONCILED_CREATED',
                      'RECONCILED_ABSENT', 'STAFF_APPROVED', 'STAFF_REJECTED'));
alter table draft_order_request drop constraint draft_order_request_created_ref;
alter table draft_order_request add constraint draft_order_request_created_ref
    check (status not in ('CREATED', 'RECONCILED_CREATED', 'STAFF_APPROVED', 'STAFF_REJECTED')
           or erp_order_ref is not null);
create index draft_order_request_awaiting_idx on draft_order_request (team, status, completed_at);

create table quote_lock_lift (
    id               bigint      generated always as identity primary key,
    conversation_id  varchar(25) not null,
    up_to_request_id bigint      not null,
    lifted_at        timestamptz not null
);

create index quote_lock_lift_conversation_idx on quote_lock_lift (conversation_id, up_to_request_id);
create index quote_conversation_idx on quote (conversation_id, id);
