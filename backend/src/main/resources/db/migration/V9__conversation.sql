-- EP-04 (DATA_FLOW §1, §5; ADR-010/ADR-011 ACCEPTED; A-35 APPROVED): conversations, messages and the
-- minimal handoff record (decision 2026-11-19; queue/claim/resolve are EP-08).
-- Additive: three new tables, nothing else changes.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09; proposal 12 months for messages).

-- Module conversation. INTERNAL. Bound at creation to the owning principal and to the session customer
-- (A-35) through the customer reference digest (the reference itself is not stored here).
create table conversation (
    conversation_id varchar(25) primary key check (conversation_id ~ '^cv_[A-Za-z0-9_-]{22}$'),
    owner_principal varchar(26) not null check (owner_principal ~ '^usr_[A-Za-z0-9_-]{22}$'),
    customer_digest char(64)    not null check (customer_digest ~ '^[0-9a-f]{64}$'),
    channel         varchar(20) not null check (channel ~ '^[A-Z_]{1,20}$'),
    status          varchar(20) not null check (status in ('ACTIVE_BOT', 'HANDOFF_PENDING')),
    row_version     bigint      not null default 0,
    created_at      timestamptz not null,
    updated_at      timestamptz not null
);

create index conversation_owner_idx on conversation (owner_principal, created_at);

-- Module conversation. Customer text is stored only after the inbound sanitizer (A-21); bot text only
-- after the OutputValidator. Quote cards are never stored here (they are rendered per response).
-- STAFF is reserved for EP-08 and never enters the model history (ADR-011).
create table message (
    message_id      bigserial    primary key,
    conversation_id varchar(25)  not null references conversation (conversation_id),
    turn_id         uuid         not null,
    direction       varchar(10)  not null check (direction in ('CUSTOMER', 'BOT', 'STAFF')),
    client_event_id uuid,
    text            varchar(4000) not null,
    blocked         boolean      not null default false,
    -- BOT rows: whether this turn ended with the conversation at staff (replayed for duplicate events).
    handed_off      boolean      not null default false,
    created_at      timestamptz  not null,
    constraint message_client_event_uq unique (conversation_id, client_event_id),
    constraint message_client_event_customer check ((direction = 'CUSTOMER') = (client_event_id is not null)),
    constraint message_turn_direction_uq unique (turn_id, direction)
);

create index message_conversation_idx on message (conversation_id, message_id);
create index message_created_idx on message (conversation_id, direction, created_at);

-- Module handoff. reason_code is internal (SALES only, EP-08); one OPEN handoff per conversation. No
-- foreign key to conversation: modules own their tables (MODULE_BOUNDARIES), the id format is checked.
-- The status check allows only OPEN: EP-08 extends it (CLAIMED/RESOLVED) with a forward migration.
create table handoff (
    handoff_id      bigserial   primary key,
    conversation_id varchar(25) not null check (conversation_id ~ '^cv_[A-Za-z0-9_-]{22}$'),
    reason_code     varchar(40) not null check (reason_code ~ '^[A-Z_]{1,40}$'),
    status          varchar(10) not null check (status in ('OPEN')),
    opened_at       timestamptz not null
);

create unique index handoff_open_uq on handoff (conversation_id) where status = 'OPEN';
