-- SEC-01a second review (M3/L3): one PASS row per send attempt, written before that attempt, so every
-- request the provider receives has a row (rows >= requests; ADR-016). BLOCK rows have attempt 0.
-- Additive: one column and one check constraint on a table created in V2.
-- Backfill: rows written before V3 get attempt 1 for PASS; a pre-V3 PASS that was followed by a
-- retry stays one row, so historical rows undercount sends (third review L2; synthetic data only).
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
alter table egress_record add column attempt smallint not null default 0;
update egress_record set attempt = 1 where decision = 'PASS';
alter table egress_record alter column attempt drop default;
alter table egress_record add constraint egress_record_attempt_matches_decision check (
    (decision = 'BLOCK' and attempt = 0) or (decision = 'PASS' and attempt between 1 and 9));
