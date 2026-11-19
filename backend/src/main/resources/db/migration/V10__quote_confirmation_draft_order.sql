-- EP-07 confirmation and draft order (UC-04; ADR-007 ACCEPTED, ADR-009 ACCEPTED 2026-11-19; DATA_FLOW §2).
-- Module quote:
--   quote: a repriced quote keeps its public reference and gets a new row with version + 1 (BR-16), so the
--     reference alone is no longer unique: UNIQUE(public_ref, version) replaces UNIQUE(public_ref).
--     confirm_token_hash is the SHA-256 of the confirmation token shown with the server-rendered card
--     (TH-26; only the hash is stored). confirmed_at is set when the customer's confirmation is accepted.
--   quote_confirmation: one row per confirmation attempt (audit and the per-customer rate limit).
--     reason_code is the internal reason of a blocked confirmation, for staff only (A-14). No prices,
--     no stock numbers, no token.
-- Module order (INTERNAL; no customer key, no prices):
--   draft_order_request: the outbox row of one draft order (rule 20, ADR-009). idempotency_key is
--     server-derived (quote reference ':' version, BR-17); UNIQUE(quote_ref, quote_version) lets a
--     quote version create at most one request. status is the source of truth for the ERP outcome.
--   draft_order_event: every state change with its actor (reconciliation evidence, SEC-04).
-- Change to existing data: one constraint on quote is replaced by a weaker-per-column, stricter-per-pair
--   one; existing rows (all version 1) satisfy it. Two nullable columns are added. Nothing is dropped.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09); no purge job in the PoC.
alter table quote drop constraint quote_public_ref_key;
alter table quote add constraint quote_ref_version_uq unique (public_ref, version);
alter table quote add column confirm_token_hash char(64) check (confirm_token_hash ~ '^[0-9a-f]{64}$');
alter table quote add column confirmed_at timestamptz;

create table quote_confirmation (
    id              bigint generated always as identity primary key,
    quote_id        bigint references quote (id),
    customer_digest char(64)    not null check (customer_digest ~ '^[0-9a-f]{64}$'),
    -- STARTED: the attempt was admitted (it counts toward the limit) and has no recorded outcome yet.
    outcome         varchar(30) not null check (outcome in ('STARTED', 'NOT_FOUND', 'STALE_VERSION', 'EXPIRED',
                        'SOURCE_UNAVAILABLE', 'BLOCKED', 'REPRICED', 'ACCEPTED', 'REPLAY', 'CONFLICT')),
    reason_code     varchar(40) check (reason_code in ('CREDIT_NOT_ELIGIBLE', 'CREDIT_REVIEW_REQUIRED',
                        'PRICE_NOT_FOUND', 'PRICE_CURRENCY_MISMATCH', 'INSUFFICIENT_STOCK')),
    occurred_at     timestamptz not null,
    check (reason_code is null or outcome = 'BLOCKED')
);

create index quote_confirmation_customer_idx on quote_confirmation (customer_digest, occurred_at);

create table draft_order_request (
    id              bigint generated always as identity primary key,
    quote_ref       varchar(16) not null check (quote_ref ~ '^BG-[A-HJ-NP-Z2-9]{8}$'),
    quote_version   integer     not null check (quote_version >= 1),
    idempotency_key varchar(40) not null unique check (idempotency_key ~ '^BG-[A-HJ-NP-Z2-9]{8}:[0-9]{1,9}$'),
    status          varchar(20) not null check (status in ('PENDING', 'SUBMITTED', 'CREATED', 'REJECTED',
                        'OUTCOME_UNKNOWN', 'RECONCILED_CREATED', 'RECONCILED_ABSENT')),
    -- SHA-256 of the confirmed order (ERP customer and lines) taken when the request was registered; a
    -- dispatch with anything else is refused. The order itself is not stored here.
    payload_digest  char(64)    not null check (payload_digest ~ '^[0-9a-f]{64}$'),
    -- BR-18 evidence: whether the key was sent to the ERP (only when the configured capability allows it).
    key_forwarded   boolean     not null default false,
    erp_order_ref   varchar(64) check (erp_order_ref ~ '^[A-Za-z0-9:_.-]{1,64}$'),
    -- What the ERP answered after the request was already counted as unknown: evidence for the staff
    -- member who reconciles, never a state change by itself.
    late_erp_order_ref varchar(64) check (late_erp_order_ref ~ '^[A-Za-z0-9:_.-]{1,64}$'),
    -- Structured rejection or unknown-outcome code from the adapter; never an ERP message.
    detail_code     varchar(40) check (detail_code ~ '^[A-Z_]{1,40}$'),
    created_at      timestamptz not null,
    submitted_at    timestamptz,
    completed_at    timestamptz,
    row_version     integer     not null default 0,
    constraint draft_order_request_quote_uq unique (quote_ref, quote_version),
    -- A request counted as created always names the ERP draft.
    constraint draft_order_request_created_ref check (status not in ('CREATED', 'RECONCILED_CREATED')
                                                      or erp_order_ref is not null)
);

-- erp_order_ref is deliberately not unique: staff type it during reconciliation, and a mistyped number
-- must never stop another request from recording the draft the ERP really gave it.

create index draft_order_request_status_idx on draft_order_request (status, created_at);

create table draft_order_event (
    id          bigint generated always as identity primary key,
    request_id  bigint      not null references draft_order_request (id),
    event       varchar(30) not null check (event ~ '^[A-Z_]{1,30}$'),
    -- Pseudonymous principal of the staff member who reconciled; null for the system.
    actor       varchar(26) check (actor ~ '^usr_[A-Za-z0-9_-]{22}$'),
    occurred_at timestamptz not null
);

create index draft_order_event_request_idx on draft_order_event (request_id, id);
