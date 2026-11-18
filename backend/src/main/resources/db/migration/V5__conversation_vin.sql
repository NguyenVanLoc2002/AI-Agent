-- EP-05 server-side VIN (UC-02 AC-02.1, TOOL_CONTRACTS §3.1, BR-37). Owned by module catalog.
-- conversation_vin: the conversation's current validated VIN (owner decision 2026-11-18: one per
--   conversation, a newer VIN replaces it, 2 h TTL). A full VIN is context-identifying (BR-30,
--   DATA_CLASSIFICATION §2) and never goes to the model (WMI + mask only). Plain text in the local
--   synthetic database; encryption at rest and retention are OPEN (EP-09, D-09). No purge job in the PoC.
-- Additive: one new table, nothing else changes.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
create table conversation_vin (
    conversation_id varchar(25) primary key,
    vin             char(17)    not null check (vin ~ '^[A-HJ-NPR-Z0-9]{17}$'),
    registered_at   timestamptz not null,
    expires_at      timestamptz not null check (expires_at > registered_at)
);

create index conversation_vin_expires_idx on conversation_vin (expires_at);
