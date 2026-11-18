-- EP-03 tool registry (TOOL_CONTRACTS, ADR-006 ACCEPTED 2026-11-18). Owned by module tools.
-- tool_invocation: quota counter and audit trail; metadata only (principal pseudonym, conversation,
--   turn, registered tool name or UNKNOWN, decision, reason code). Never arguments or results.
--   conversation_id is null for a call on a conversation the caller does not own: it counts against
--   the caller only and cannot use up or lock someone else's conversation.
-- product_ref: server-issued productRef tokens bound to the conversation that searched them
--   (A-26 for productRef, ADR-008: random + stored). sku is APPROVED_TECHNICAL catalog data.
-- Additive: two new tables, nothing else changes.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09); no purge job in the PoC.
create table tool_invocation (
    id              bigint generated always as identity primary key,
    principal_key   varchar(26) not null,
    conversation_id varchar(25),
    turn_id         varchar(36) not null,
    tool_name       varchar(40) not null,
    decision        varchar(10) not null check (decision in ('ADMITTED', 'INVOKED', 'REJECTED', 'FILTERED')),
    reason_code     varchar(40),
    occurred_at     timestamptz not null
);

create index tool_invocation_principal_idx on tool_invocation (principal_key, occurred_at);
create index tool_invocation_conversation_idx on tool_invocation (conversation_id, occurred_at);
create index tool_invocation_turn_idx on tool_invocation (turn_id);

create table product_ref (
    token           varchar(25) primary key,
    conversation_id varchar(25) not null,
    sku             varchar(32) not null,
    provenance      varchar(20) not null check (provenance in ('AI_RECOGNIZED', 'CATALOG_LOOKUP', 'VERIFIED')),
    issued_at       timestamptz not null,
    expires_at      timestamptz not null check (expires_at > issued_at)
);

create index product_ref_conversation_idx on product_ref (conversation_id);
