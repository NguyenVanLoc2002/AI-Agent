-- EP-06 quote service (UC-03; ADR-007, ADR-008, ADR-014 ACCEPTED 2026-11-18). Owned by module quote.
-- quote / quote_line: issued quotes with an immutable price snapshot (rule 40). RESTRICTED (customer
--   prices). customer_digest is a SHA-256 of the customer reference, never the reference itself.
--   Status names follow ADR-007; EP-06 writes ISSUED, EXPIRED and SUPERSEDED only.
-- quote_handle: SHA-256 of the 128-bit handle the model sees, bound to conversation, customer,
--   quote and version, expiring with the quote; revoked when superseded (ADR-008).
-- quote_request: one row per request (audit): outcome and the internal NOT_QUOTED reason code for
--   staff (A-14). Never prices, stock numbers or the handle. customer_digest (null without a customer)
--   scopes the A-34 lock and issue limit to the customer across conversations (owner 2026-11-19).
-- Note: V6 was edited before merge (EP-06 reviews). A local database migrated from commit 3ba5ca6 fails
--   checksum validation; reset it with `docker compose down -v`.
-- Additive: four new tables, nothing else changes.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09); no purge job in the PoC.
create table quote (
    id              bigint generated always as identity primary key,
    public_ref      varchar(16) not null unique check (public_ref ~ '^BG-[A-HJ-NP-Z2-9]{8}$'),
    conversation_id varchar(25) not null,
    customer_digest char(64)    not null,
    version         integer     not null default 1 check (version >= 1),
    status          varchar(30) not null check (status in ('DRAFT', 'ISSUED', 'CONFIRMED', 'CONFIRMATION_BLOCKED',
                        'ORDER_SUBMITTING', 'ORDER_OUTCOME_UNKNOWN', 'ORDER_FAILED', 'DRAFT_ORDER_CREATED',
                        'STAFF_APPROVED', 'REJECTED', 'EXPIRED', 'SUPERSEDED', 'CANCELLED')),
    currency        char(3)     not null check (currency ~ '^[A-Z]{3}$'),
    money_scale     smallint    not null check (money_scale between 0 and 4),
    rounding_mode   varchar(12) not null check (rounding_mode in ('UP', 'DOWN', 'CEILING', 'FLOOR', 'HALF_UP',
                        'HALF_DOWN', 'HALF_EVEN')),
    total           numeric(19, 4) not null check (total >= 0),
    issued_at       timestamptz not null,
    valid_until     timestamptz not null check (valid_until > issued_at),
    row_version     integer     not null default 0
);

-- ADR-007 (owner 2026-11-18): at most one ISSUED quote per conversation.
create unique index quote_one_issued_per_conversation on quote (conversation_id) where status = 'ISSUED';

create table quote_line (
    quote_id       bigint      not null references quote (id),
    line_no        smallint    not null check (line_no between 1 and 20),
    sku            varchar(32) not null,
    quantity       integer     not null check (quantity between 1 and 9999),
    unit_price     numeric(19, 4) not null check (unit_price >= 0),
    line_total     numeric(19, 4) not null check (line_total >= 0),
    price_source   varchar(64) not null,
    price_revision varchar(64),            -- optional: the ERP may not version prices (D-01, ERP_CONNECTOR §3.3)
    primary key (quote_id, line_no),
    unique (quote_id, sku)
);

create table quote_handle (
    token_hash      char(64)    primary key,
    quote_id        bigint      not null references quote (id),
    quote_version   integer     not null,
    conversation_id varchar(25) not null,
    customer_digest char(64)    not null,
    issued_at       timestamptz not null,
    expires_at      timestamptz not null check (expires_at > issued_at),
    revoked_at      timestamptz
);

create index quote_handle_quote_idx on quote_handle (quote_id);

create table quote_request (
    id              bigint generated always as identity primary key,
    conversation_id varchar(25) not null,
    outcome         varchar(12) not null check (outcome in ('ISSUED', 'NOT_QUOTED')),
    reason_code     varchar(40) check (reason_code in ('NO_CUSTOMER', 'CREDIT_NOT_ELIGIBLE',
                        'CREDIT_REVIEW_REQUIRED', 'PRICE_NOT_FOUND', 'PRICE_CURRENCY_MISMATCH', 'INSUFFICIENT_STOCK',
                        'SOURCE_UNAVAILABLE', 'STORE_UNAVAILABLE', 'STALE_REQUEST', 'HANDED_OFF', 'REISSUE_LIMIT',
                        'CONVERSATION_MISMATCH')),
    customer_digest char(64),
    line_count      smallint    not null check (line_count between 0 and 20),
    quote_id        bigint references quote (id),
    occurred_at     timestamptz not null,
    check ((outcome = 'ISSUED') = (quote_id is not null)),
    check ((outcome = 'NOT_QUOTED') = (reason_code is not null))
);

create index quote_request_conversation_idx on quote_request (conversation_id, occurred_at);
create index quote_request_customer_idx on quote_request (customer_digest, occurred_at);
