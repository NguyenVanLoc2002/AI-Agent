-- SEC03-F3-AUDIT (owner decisions 2026-11-23 "đồng ý đề xuất" = 1A–5A, DECISIONS_REQUIRED A-59). Module audit.
-- egress_record also records a model answer the gateway withheld (SEC03-F3, A-58): direction RESPONSE, decision BLOCK,
-- the attempt that produced the answer, policy code and honeytoken ID, the structural path of the value; never the
-- answer. Existing rows and every request keep direction REQUEST, so request counts (SEC-01 evidence) filter on it.
-- Additive: one column with a default and a wider attempt check; no row changes.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
alter table egress_record add column direction varchar(8) not null default 'REQUEST'
    constraint egress_record_direction_check check (direction in ('REQUEST', 'RESPONSE'));

alter table egress_record drop constraint egress_record_attempt_matches_decision;
alter table egress_record add constraint egress_record_attempt_matches_decision check (
    (direction = 'REQUEST' and ((decision = 'BLOCK' and attempt = 0) or (decision = 'PASS' and attempt between 1 and 9)))
    or (direction = 'RESPONSE' and decision = 'BLOCK' and attempt between 1 and 9));
