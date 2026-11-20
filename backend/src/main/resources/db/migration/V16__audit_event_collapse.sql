-- AUDIT-FLOOD (A-45(z), approved by the owner 2026-11-20). Module audit.
-- Separate writes (denials, reads, refused confirmation outcomes) of the same action, resource type, outcome, actor
-- and role in the same minute are collapsed into one row with an occurrence count, so a flood of identical events
-- (e.g. a customer probing quote references) cannot bury a staff event or grow the table one row per request.
-- Writes that commit with a business change (claim, resolve, staff message, staff quote read, decision,
-- reconciliation) and an accepted confirmation are never collapsed: bucket_start stays null for them.
--   occurrences:      how many events the row stands for (1 for a row that is not collapsed)
--   last_occurred_at: the latest of them (occurred_at stays the first)
--   last_trace_id:    the trace ID of the latest of them (trace_id stays the first); the ones in between are counted,
--                     not traced (DESIGN PROPOSAL, A-45(z))
--   bucket_start:     the UTC minute the row collects, null for a row that never collapses
-- Additive: four columns with defaults for existing rows, one check, one partial unique index, one index.
-- Production rollout note (EP-09b reviews L7): on a large table add the check NOT VALID + VALIDATE and build the
-- indexes CONCURRENTLY outside a transaction; for the synthetic PoC database the plain statements are fine.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09).
alter table audit_event add column occurrences integer not null default 1 check (occurrences >= 1);
alter table audit_event add column last_occurred_at timestamptz;
alter table audit_event add column last_trace_id varchar(36)
    check (last_trace_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$');
alter table audit_event add column bucket_start timestamptz;
alter table audit_event add constraint audit_event_collapse_shape check (
    (bucket_start is null and last_occurred_at is null and last_trace_id is null and occurrences = 1)
    or (bucket_start is not null and last_occurred_at is not null
        and bucket_start = date_trunc('minute', occurred_at, 'UTC')
        and last_occurred_at >= occurred_at and last_occurred_at < bucket_start + interval '1 minute'));

create unique index audit_event_collapse_uq
    on audit_event (action, resource_type, outcome, (coalesce(actor, '')), (coalesce(actor_role, '')), bucket_start)
    where bucket_start is not null;
-- Keyset paging with an action filter (EP-09b reviews L1).
create index audit_event_action_keyset_idx on audit_event (action, occurred_at, id);
-- Trace lookup of the latest collapsed event.
create index audit_event_last_trace_id_idx on audit_event (last_trace_id);
