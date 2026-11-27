-- STAFF-MSG-ID (owner decisions 2026-11-27 "theo đề xuất" = 1A–4A, DECISIONS_REQUIRED A-91). Module conversation.
-- A staff message carries the client's event ID like a customer message (AC-01.2), so a resend after an unknown
-- outcome stores it once; the existing unique (conversation_id, client_event_id) is the final guard.
-- CUSTOMER rows keep a required ID, BOT rows never have one; STAFF rows written before this migration have none,
-- so the replaced check allows a null ID for STAFF; the second check (NOT VALID: not checked against existing rows,
-- enforced for every new or updated row; messages are never updated) requires it for every STAFF row written from now.
-- Additive: one check constraint replaced and one added, no row changes.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
alter table message drop constraint message_client_event_customer;
alter table message add constraint message_client_event_direction check (
    (direction = 'CUSTOMER' and client_event_id is not null)
    or (direction = 'BOT' and client_event_id is null)
    or direction = 'STAFF');
alter table message add constraint message_staff_client_event check (direction <> 'STAFF' or client_event_id is not null)
    not valid;
