-- SEC-01a: egress decision audit (AI_GATEWAY §2.5, ADR-012 ACCEPTED 2026-11-18). Owned by module audit.
-- Metadata only: no request values, no per-value hash/HMAC (DATA_CLASSIFICATION §3, review F9).
-- Additive: creates one table, changes nothing else.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume
-- (`docker compose down -v`) or `drop table egress_record` before removing this migration.
-- Retention: OPEN (D-09); no purge job in the PoC.
create table egress_record (
    id                 bigint generated always as identity primary key,
    trace_id           varchar(36)  not null,
    decision           varchar(5)   not null check (decision in ('PASS', 'BLOCK')),
    policy_code        varchar(40),
    honeytoken_id      varchar(27),
    blocked_field_path varchar(512),
    field_names        varchar(4096) not null,
    byte_length        integer      not null check (byte_length >= 0),
    occurred_at        timestamptz  not null,
    constraint egress_record_finding_matches_decision check (
        (decision = 'PASS' and policy_code is null and honeytoken_id is null and blocked_field_path is null)
        or (decision = 'BLOCK' and policy_code is not null and blocked_field_path is not null))
);

create index egress_record_trace_id_idx on egress_record (trace_id);
create index egress_record_occurred_at_idx on egress_record (occurred_at);
