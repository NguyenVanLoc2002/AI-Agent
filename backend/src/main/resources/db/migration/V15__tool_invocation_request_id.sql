-- EP-09b (A-45(h), DESIGN PROPOSAL). Module tools: the request ID (HTTP correlation ID, server-generated, never
-- sent to the provider) on every tool call, so the security inspector groups the calls of one chat turn by the
-- request as well as by the turn ID. Kept apart from V14 (module audit) so each migration touches the tables of
-- one module (EP-09b architecture review L1).
-- Additive: one nullable column; rows written before V15 keep null.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
-- Retention: OPEN (D-09).
alter table tool_invocation add column request_id varchar(36)
    check (request_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$');
create index tool_invocation_request_id_idx on tool_invocation (request_id);
