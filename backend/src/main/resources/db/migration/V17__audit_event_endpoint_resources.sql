-- EP-09d (A-45 3f, owner decisions 2026-11-20 "Đồng ý theo đề xuất" = 1A–5A, DECISIONS_REQUIRED A-47). Module audit.
-- URL-level refusals of Spring Security (401, 403, CSRF) are audited by the area of the URL they hit, never the URL:
--   ENDPOINT_INTERNAL /api/internal/**, ENDPOINT_OPS /api/ops/**, ENDPOINT_SECURITY /api/demo/security/**,
--   ENDPOINT_DEMO the rest of /api/demo/**, ENDPOINT_OTHER anything else.
-- Constraint widening only (as V12): the closed list of resource types gets five values; no row changes.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume (`docker compose down -v`).
alter table audit_event drop constraint audit_event_resource_type_check;
alter table audit_event add constraint audit_event_resource_type_check check (resource_type in ('HANDOFF',
    'CONVERSATION', 'QUOTE', 'DRAFT_ORDER', 'KILL_SWITCH', 'SECURITY_TRACE', 'SECURITY_EVENTS',
    'ENDPOINT_INTERNAL', 'ENDPOINT_OPS', 'ENDPOINT_SECURITY', 'ENDPOINT_DEMO', 'ENDPOINT_OTHER'));
