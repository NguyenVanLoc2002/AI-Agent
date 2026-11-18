# Session handoff — AutoParts Agent Sandbox (APAS)

## Current: G0-2 partial approvals, 2026-11-18
- PR #4 (A-16c) merged into `main` (`0af580e`). Branch `docs/G0-2-adr-approvals`.
- Owner approved the set blocking SEC-01a: ADR-001, ADR-002 (Maven, as amended by POC-001), ADR-003, ADR-004, ADR-016 → ACCEPTED; A-04, A-05, A-09, A-13 → APPROVED. Recorded in each ADR, `docs/architecture/adr/README.md`, `docs/DECISIONS_REQUIRED.md`, `docs/backlog/IMPLEMENTATION_PLAN.md`.
- Still PROPOSED/OPEN: ADR-005..015, A-01..A-03, A-06..A-08, A-10..A-12, A-14..A-18, D-01..D-13, UX-01..UX-18.
- Next: SEC-01a egress guard on `feat/SEC-01a-egress-guard` (plan first, owner approval, then code).

## Previous: A-16c decision recorded (docs only), 2026-11-18
- PR #3 (Claude Design integration) merged into `main` (`3e2a528`). This branch: `docs/APAS-02-a16-repo-private`.
- A-16c decided: the owner made the GitHub repository private; verified 2026-11-18 (unauthenticated GitHub API → 404). The owner states that the sample data (customers, prices, phone numbers, emails, orders, VINs in prototypes, design-system bundle and fixtures) is fake/synthetic; this statement does **not** cover the reference company name, the ERP vendor name or the reference proposal content in git history. Recorded in `docs/DECISIONS_REQUIRED.md` A-16 and `docs/REBRANDING_REPORT.md` §10.
- Still open: A-16 (a), (b), (d); git history not rewritten; G0-2 ADR approvals; UX-01..UX-18, A-17, A-18.
- Next actions: owner approves the ADRs and approvals that block SEC-01a → `feat/SEC-01a-egress-guard`.

## Previous: Claude Design integration (docs only), 2026-11-17
- Branch `docs/APAS-01-claude-design-integration` (from `docs/APAS-00-independent-demo-rebranding`), merged as PR #3. The owner's earlier uncommitted deletions/modifications are still untouched.
- The owner added `design-system/` and `docs/design/` (Claude Design export). Claude Design replaces the Stitch plan (A-18).
- Done: vendor/customer/proposal identifiers neutralised in `docs/design/**` (.md, .dc.html, canvas.json; 411 replacements, 41 files); nested `docs/design/CLAUDE.md` replaced by `docs/design/DESIGN_GUIDE.md` (reference only, no precedence); root `CLAUDE.md`, rule 50, skill `ui-from-stitch` → `ui-from-claude-design`, TRACEABILITY, USE_CASES pointer updated; `DECISIONS_REQUIRED.md` gained A-17 (logo), A-18 (Claude Design export) and §UX (UX-01..UX-18 design-vs-ADR/contract/RBAC conflicts); screen→UC mapping in `docs/design/specs/90`; `design-system/` README export banner, EXPORT-NOTES caveats, OFL/ISC licenses; `_ds_bundle.js` and `archived/` removed from the export; root `.gitignore` excludes `docs/design/evidence/`.
- Not changed: ADRs, contracts, RBAC_MATRIX, security rules, settings, code. Conflicts are recorded, not resolved.
- Open: owner decides UX-01..UX-18, A-17, A-18; full export with `.jsx` sources before `frontend/`; re-capture evidence screenshots; the Claude Design canvas itself still shows the old vendor name.
- Next actions: owner reviews this branch → decide UX items blocking Batch A (UX-13, UX-15) → SEC-01a egress guard remains the next code issue.

## Previous: Independent demo rebranding (docs only), 2026-11-16
- Branch `docs/APAS-00-independent-demo-rebranding`, created from `origin/main` `4d283db` (POC-001 merged via PR #1). Committed and pushed for a PR into `main`. The owner's earlier uncommitted deletions/modifications are still local and were not committed.
- Project is now **AutoParts Agent Sandbox (APAS)**, an independent technical demo with no customer and a vendor-neutral ERP connector. Disclaimer is in README and CLAUDE.md. Full record: `docs/REBRANDING_REPORT.md`. Approval A-16 is PROPOSED.
- Changed: 24 project-authored doc and `.claude` text files plus the new report. No code, config, settings, ADR decision or status changed. The owner's earlier deletions and modifications were left untouched.
- Validation: `rg` residual search, `git diff --check` (exit 0) and a markdown link/path check were run. Backend tests NOT RUN, because no executable file changed.
- Open: (1) git history still contains the reference customer and vendor names, and the GitHub repo is already public. The owner must decide (A-16c). (2) Follow-up code issue for the remaining vendor names in `pom.xml`, `application.yml`, `erp/package-info.java` and the `MockAritoAdapter` fixture. Keep the `ConfigurationPolicyTest` domain guard. (3) Sync the ChatGPT Project documents manually. (4) Inventory Staff role is not added; it is OPEN under D-08.
- Next actions: owner reviews and merges the rebranding PR and decides A-16 → SEC-01a egress guard.

## Previous: POC-001 (historical record; branch/commit facts corrected 2026-11-16: POC-001 was committed as `2b10755` on `feat/POC-001-project-bootstrap`)
- Issue: **POC-001 Project Bootstrap** (= backlog EP-00), implemented 2026-11-13. Previous issue G0-1 (docs) is complete; ADRs still PROPOSED; approvals A-01..A-15 are pending, except A-04, where the owner instruction chose Maven.
- Baseline commit: af2d09f. Working tree uncommitted. Pre-existing user deletions and modifications to the BRD/UI/backlog docs and the old kit files were left untouched.
- Branch: none created (still on `main`). No commit, no push. Suggested branch if committing: `feat/POC-001-project-bootstrap`.
- Changed (POC-001):
  - `backend/`: `pom.xml` with Spring Boot 4.1.2, Modulith 2.1.2 and ArchUnit 1.5.1; `mvnw` and `.mvn/` (Maven 3.9.11); `compose.yaml` (postgres:17.12-alpine on 127.0.0.1, password from env); `.gitignore`; `README.md`.
  - `src/main`: 14 module `package-info` files with `allowedDependencies`; `platform` (ErrorCode, ApplicationException, GlobalExceptionHandler, CorrelationIdFilter, Clock); `application.yml` and `application-local.yml`; `db/migration/V1__baseline.sql` (no tables).
  - `src/test`: ArchitectureRules + ArchitectureRulesTest with fixtures, ModuleStructureTest, GlobalExceptionHandlerTest + ValidationProbeController, ErrorCodeMappingTest, ConfigurationPolicyTest, ApplicationBootstrapIT, TestcontainersConfiguration.
  - Docs: ADR-002 update, DECISIONS_REQUIRED A-04, IMPLEMENTATION_PLAN row 3, MODULE_BOUNDARIES §6.
- Tests (VERIFIED, 2026-11-13, Java 21.0.12, Maven 3.9.11, Docker 28.3.0):
  - `cd backend && ./mvnw -B clean verify` → exit 0. Surefire ran 77 tests and failsafe ran 6; 0 failures, 0 skipped. The build log contains 0 occurrences of the sentinel 987654321.
  - Negative proof: violations planted temporarily in `identity`, `audit` and `quote` made ModuleStructureTest and ArchitectureRulesTest fail (exit 1). The planted files were removed.
  - Local smoke test: `docker compose up -d --wait` on port 55432 with a random throwaway password, then the jar with `--spring.profiles.active=local`. `/actuator/health` returned UP, `/actuator/env` returned 404, an unknown path returned the problem+code shape, and Flyway reached v1. Stopped afterwards with `docker compose down -v`.
  - Fail-closed: starting with no profile and no DB env exits with code 1.
- Review: backend-reviewer (advisory) found 2 High, 5 Medium and 5 Low. Fixed: H1 (statuses 401/403/409/429/other are preserved), H2 (Spring Security exceptions re-thrown, matched by name; **not testable until Spring Security is added**), M1 (allowlists, audit→platform only), M3 (module set asserted through package-info), M4 (ARCH-07 also covers `@Table`), M5 (trace ID always server-generated), L1 (index/map-key content stripped, class-level errors reported), L2 (ARCH-09/11 broadened; still denylists), L3 (`include-path: never`, filter on error dispatch), L4 (regex token scan), L5 (cause-chain class names, image pinned). Open: M2 (named interfaces are needed once `api`/`spi` packages exist), and L3 partly (filter exceptions still go to Boot's `/error`, which has a different shape). A second review pass was not run.
- Not done / NOT RUN: ARCH-05/06/08 (SEC-01a); auth (ADR-010); `demo` profile and endpoints (ADR-015); dependency/CVE scan (no scanner installed); a formatter or linter is not configured.
- Blockers: owner sign-off on ADR-001/002; A-01..A-03, A-05..A-15; D-01..D-13 OPEN.
- Next 3 actions: (1) the owner reviews POC-001 and decides on a commit/branch; (2) SEC-01a egress guard at gateway level, including ARCH-05/06/08 and named interfaces; (3) a second backend/security review on the SEC-01a diff.
- Resume prompt: Read CLAUDE.md and docs/SESSION_HANDOFF.md, inspect git status, then work on the next issue only.
