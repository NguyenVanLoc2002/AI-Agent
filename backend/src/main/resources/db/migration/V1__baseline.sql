-- POC-001 baseline migration.
-- No business schema has been approved yet (docs/architecture/DATA_FLOW.md storage table is a
-- DESIGN PROPOSAL; G0-2 approvals pending). This migration intentionally creates no tables;
-- it only proves the Flyway pipeline works. Business tables are added by the issue that owns them.
-- Rollback: forward-only (rule 20). Locally, drop the synthetic database/volume.
SELECT 1;
