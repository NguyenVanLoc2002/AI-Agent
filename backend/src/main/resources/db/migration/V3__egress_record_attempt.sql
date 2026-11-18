-- SEC-01a second review (M3/L3): one PASS row per send attempt, so egress_record matches what the
-- provider received (ADR-016 a=b). BLOCK rows have attempt 0 (nothing sent).
-- Additive: one column and one check constraint on a table created in V2.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
alter table egress_record add column attempt smallint not null default 0;
update egress_record set attempt = 1 where decision = 'PASS';
alter table egress_record alter column attempt drop default;
alter table egress_record add constraint egress_record_attempt_matches_decision check (
    (decision = 'BLOCK' and attempt = 0) or (decision = 'PASS' and attempt between 1 and 9));
