-- EP-04, ADR-010 (ACCEPTED) and A-37 (APPROVED 2026-11-19): stable server identities per channel binding.
-- Owned by module identity. A channel user (channel + SHA-256 of the provider user ID; the user name
-- itself is never stored) gets one pseudonymous principal and, when the ERP knows the user, one stable
-- customer reference that is reused at every login.
-- Additive: one new table and two nullable columns; EP-06 rows without a binding stay valid.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09).

-- INTERNAL: pseudonymous principal for quotas, audit and conversation ownership (PrincipalId).
create table principal_binding (
    principal_ref      varchar(26) primary key check (principal_ref ~ '^usr_[A-Za-z0-9_-]{22}$'),
    channel            varchar(20) not null check (channel ~ '^[A-Z_]{1,20}$'),
    provider_user_hash char(64)    not null check (provider_user_hash ~ '^[0-9a-f]{64}$'),
    created_at         timestamptz not null,
    constraint principal_binding_channel_user_uq unique (channel, provider_user_hash)
);

-- RESTRICTED (customer identity): the channel binding behind a customer reference (A-37).
alter table customer_link
    add column channel            varchar(20) check (channel ~ '^[A-Z_]{1,20}$'),
    add column provider_user_hash char(64)    check (provider_user_hash ~ '^[0-9a-f]{64}$'),
    add constraint customer_link_binding_complete check ((channel is null) = (provider_user_hash is null)),
    add constraint customer_link_channel_user_uq unique (channel, provider_user_hash);
