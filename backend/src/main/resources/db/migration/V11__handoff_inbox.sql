-- EP-08a staff inbox (UC-05, UC-06; DATA_FLOW §3; RBAC_MATRIX; owner decisions 2026-11-19: D-08 PoC
-- default "team of the customer", A-15 "content access ends at resolve").
-- Module handoff: a handoff is OPEN → CLAIMED → RESOLVED; it belongs to the team of the customer
--   (null only for rows of conversations created before this migration: nobody is assigned, fail closed;
--   a new conversation needs a team, owner decision 2026-11-19). At most one handoff that is not
--   RESOLVED per conversation. claimed_by / resolved_by are pseudonymous principals of staff.
-- Module conversation: HUMAN_ACTIVE while a staff member holds the handoff; the conversation keeps
--   the team it was created for. message.direction 'STAFF' already exists (V9).
-- Module order: a draft order request belongs to the team of the customer who confirmed, so the
--   reconciliation queue is scoped like the inbox (A-41(k)).
-- Change to existing data: constraints on handoff.status and conversation.status are widened; the
--   partial unique index on handoff is replaced by one that also covers CLAIMED. New columns are
--   nullable; existing rows keep a null team (visible to no staff member).
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09).
alter table handoff drop constraint handoff_status_check;
alter table handoff add constraint handoff_status_check check (status in ('OPEN', 'CLAIMED', 'RESOLVED'));
alter table handoff
    add column team        varchar(21) check (team ~ '^team-[a-z0-9]{1,16}$'),
    add column claimed_by  varchar(26) check (claimed_by ~ '^usr_[A-Za-z0-9_-]{22}$'),
    add column claimed_at  timestamptz,
    add column resolved_by varchar(26) check (resolved_by ~ '^usr_[A-Za-z0-9_-]{22}$'),
    add column resolved_at timestamptz,
    add column row_version integer not null default 0,
    -- Another reason raised while this handoff was already open or claimed (e.g. a blocked quote
    -- confirmation while staff hold the conversation): resolving opens a new handoff with it instead of
    -- unlocking the bot.
    add column followup_reason varchar(40) check (followup_reason ~ '^[A-Z_]{1,40}$'),
    add constraint handoff_claim_complete check ((status = 'OPEN') = (claimed_by is null)),
    add constraint handoff_resolve_complete check ((status = 'RESOLVED') = (resolved_at is not null));

drop index handoff_open_uq;
create unique index handoff_active_uq on handoff (conversation_id) where status in ('OPEN', 'CLAIMED');
create index handoff_team_idx on handoff (team, status, opened_at);

alter table conversation drop constraint conversation_status_check;
alter table conversation add constraint conversation_status_check
    check (status in ('ACTIVE_BOT', 'HANDOFF_PENDING', 'HUMAN_ACTIVE'));
alter table conversation add column team varchar(21) check (team ~ '^team-[a-z0-9]{1,16}$');

alter table draft_order_request add column team varchar(21) check (team ~ '^team-[a-z0-9]{1,16}$');
create index draft_order_request_team_idx on draft_order_request (team, status);
