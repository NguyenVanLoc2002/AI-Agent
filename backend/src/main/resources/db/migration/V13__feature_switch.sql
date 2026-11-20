-- EP-09a kill switch and provider-auth breaker (UC-08; ADR-013 ACCEPTED 2026-11-20; owner decisions
-- 2026-11-20, DECISIONS_REQUIRED A-44: one global switch, OPS only, breaker closed by an operator).
-- Module platform:
--   feature_switch: one row per switch; the PoC has exactly one ('ai_assistant'). reason says why it is
--     off: OPERATOR (an operator switched it off) or PROVIDER_AUTH (the provider answered 401/403; the
--     breaker stays open until an operator switches the AI on again).
--   feature_switch_event: every change with its actor (pseudonymous principal of the operator; null for the
--     system, i.e. the breaker). Interim audit record until the central audit_event (EP-09b, decision 7A).
-- Seed: the AI starts enabled (the PoC behaviour before this migration).
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09).
create table feature_switch (
    name        varchar(40) primary key check (name = 'ai_assistant'),
    enabled     boolean     not null,
    reason      varchar(20) check (reason in ('OPERATOR', 'PROVIDER_AUTH')),
    updated_at  timestamptz not null,
    row_version integer     not null default 0,
    check (enabled = (reason is null))
);

insert into feature_switch (name, enabled, reason, updated_at) values ('ai_assistant', true, null, now());

create table feature_switch_event (
    id          bigint      generated always as identity primary key,
    name        varchar(40) not null references feature_switch (name),
    enabled     boolean     not null,
    reason      varchar(20) check (reason in ('OPERATOR', 'PROVIDER_AUTH')),
    actor       varchar(26) check (actor ~ '^usr_[A-Za-z0-9_-]{22}$'),
    occurred_at timestamptz not null,
    check (enabled = (reason is null)),
    -- Only the breaker is the system; every other change has an operator (EP-09a reviews, backend L8).
    check ((actor is null) = (reason is not distinct from 'PROVIDER_AUTH'))
);

create index feature_switch_event_name_idx on feature_switch_event (name, id);
