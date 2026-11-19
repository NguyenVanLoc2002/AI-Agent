-- EP-06, A-19 (APPROVED 2026-11-18): the random server reference (AuthenticatedCustomerRef) and the
-- ERP customer it stands for. Owned by module identity. The reference carries no ERP information;
-- only this table maps it to the ERP key, and only identity reads it (quote asks identity, erp only
-- ever sees the ERP link). RESTRICTED (customer identity).
-- EP-04 inserts rows after the channel binding lookup (CustomerLinkPort); revoked_at ends a binding.
-- Additive: one new table, nothing else changes.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09).
create table customer_link (
    customer_ref varchar(70) primary key check (customer_ref ~ '^cref_[A-Za-z0-9_-]{16,64}$'),
    erp_link     varchar(64) not null check (erp_link ~ '^[A-Za-z0-9._:-]{1,64}$'),
    linked_at    timestamptz not null,
    revoked_at   timestamptz
);

create index customer_link_erp_idx on customer_link (erp_link);
