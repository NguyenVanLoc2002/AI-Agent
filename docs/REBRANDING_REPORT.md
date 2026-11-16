# Rebranding report — AutoParts Agent Sandbox (APAS)
> 2026-11-16 · Change type: **Independent demo rebranding** (documentation and context only) · Status: **PROPOSED**, pending owner approval A-16 (`docs/DECISIONS_REQUIRED.md`).
> Internal, project-authored record. It names the former ERP vendor only where code impact requires it. The reference customer's name is withheld on purpose. Not a demo-facing artifact.

> AutoParts Agent Sandbox is an independent technical demonstration project using synthetic business scenarios and mock ERP data. It is not affiliated with, endorsed by, or commissioned by any automotive parts business or ERP vendor.

## 1. Old identity → new identity
| Aspect | Before | After |
|---|---|---|
| Name | "Arito Secure ERP Agent", "Arito Claude Code engineering kit v2", "Secure ERP AI Agent" | **AutoParts Agent Sandbox** (code **APAS**) — *AI Agent for Automotive Parts & ERP Workflows* |
| Framing | PoC for a named customer, based on that customer's 15-page proposal | Independent technical demo / PoC with synthetic scenarios. No assigned customer, no ERP vendor dependency |
| ERP | A named ERP vendor; "Mock Arito" | Generic vendor-neutral ERP connector, "ERP đích" (target ERP), "Mock ERP". Target design names `ErpConnector` / `MockErpConnector` / `RealErpConnector` (**not in code**) |
| Source document | "customer proposal dated …" | Private reference requirements document: reference-only, NEEDS_REVIEW for usage rights, not in the repository |

## 2. Scope and reason
- Reason: the project was analysed from reference business requirements, but no customer commissioned or approved it. Project-authored documents must not imply a customer engagement or a vendor partnership.
- In scope: project-authored docs (category B) and the Claude Code configuration text.
- Out of scope, by rule: code, build and runtime config, package/class/API/migration names, git history, third-party/original source material, `.claude/settings.json`, hooks.
- Unchanged: architecture, business capability meaning, BIZ/UC/BR/FR/EP/SEC/ADR/D/A IDs, every PROPOSED status, security invariants, SEC-01..07 definitions, test requirements, tool permissions, git workflow.

## 3. Inventory (discovery, `rg` 14.1.1, working tree incl. hidden files, excluding `.git`)
- Tracked files: 141 (no untracked files). Files that mentioned the ERP vendor name: **29** (19 docs/config text, 10 backend files). Files that mentioned the customer name: **0 in the working tree**. In git history it appears only in line 3 of `docs/01_BRD_SRS_Security.md` (commits 2cd3c82, af2d09f, 26d3eca, 2b10755).
- Not present in the repo: frontend / Stitch implementation, OpenAPI files, CI (`.github/` absent), scripts, fixtures with real data, logos/images, `CURRENT_STATE` file, doc linters.
- Outside the repo: the original reference proposal (not in the repo) and the ChatGPT Project documents (§8). The parent directory `D:\Project_Private` has its own `.git`. It was not inspected or changed.

| Category | Files | Action |
|---|---|---|
| A. Original reference material | `docs/01_BRD_SRS_Security.md`, `docs/03_UI_UX_Stitch.md`, `docs/04_Implementation_Backlog.md` (deleted in the working tree by the owner, uncommitted; still at `af2d09f`/`2b10755`); the external original proposal | Preserved unchanged. Deletions **not** restored or committed. **NEEDS_REVIEW**: the BRD is a close summary of a third-party proposal and names the customer and vendor. Keep it private and reference-only |
| B. Project-authored docs | README, CLAUDE.md, `.claude/*`, `docs/**` | Updated (§4) |
| C. Demo-facing artifacts | None exist (no UI, demo scripts, screenshots or sample outputs). Backend fixtures are synthetic sentinels (`987654321`, `SKU-SYNTH-1`) | Nothing to change. Future demo assets must use synthetic data only |
| D. External sources | ChatGPT Project documents | Listed for manual sync (§8). Not claimed as updated |

## 4. Files actually changed (24 + this report)
| File | Change |
|---|---|
| `README.md` | Rewritten as the APAS overview: identity table, mandatory disclaimer, proposed capabilities (not approved), private reference source, source-of-truth statement. Kit guidance kept |
| `CLAUDE.md` | Title and scope → APAS + disclaimer. "production Arito/Zalo" → "any production ERP, messaging channel (e.g. Zalo) or business system" (equally strict or stricter). All invariants unchanged |
| `.claude/rules/20-java-backend.md`, `.claude/rules/70-git-operations.md` | Vendor name → "ERP" / "real ERP/vendor or customer samples" (the review scope gets wider, not narrower) |
| `.claude/agents/architecture-reviewer.md`, `.claude/agents/qa-planner.md` | Vendor → "ERP vendor"; project name → APAS |
| `.claude/skills/quote-lifecycle/SKILL.md`, `.claude/skills/security-threat-model/SKILL.md` | Vendor → target ERP / ERP |
| `backend/README.md` | Title → APAS backend (artifact name kept); note that code and runtime names are unchanged; vendor → real ERP / Mock ERP connector |
| `docs/DECISIONS_REQUIRED.md` | D-01..D-03 restated vendor-neutrally (same question, same safe default); A-15 wording; **new A-16** (PROPOSED) for this rebranding |
| `docs/TRACEABILITY.md` | Title + independent-demo note; "Original pp." explained as private reference; vendor → ERP |
| `docs/requirements/USE_CASES.md`, `BUSINESS_RULES.md` | Title → APAS; reference-proposal wording; ERP actor vendor-neutral (REVIEW_REQUIRED); BR-20 "in ERP" |
| `docs/architecture/SYSTEM_ARCHITECTURE.md`, `DATA_FLOW.md`, `MODULE_BOUNDARIES.md` | Title → APAS; vendor → target ERP / Mock ERP; target names `MockErpConnector`/`RealErpConnector` marked as target only; ASCII diagram alignment kept |
| `docs/architecture/adr/ADR-005`, `ADR-007`, `ADR-009`, `adr/README.md` | Vendor → "ERP đích" in context/consequence text only. Rebranding note added. **Decisions, status (all PROPOSED) and history unchanged** |
| `docs/contracts/ERP_CONNECTOR.md` | "Not a vendor API doc" disclaimer made vendor-neutral; target names (`ErpConnector` concept, `MockErpConnector`, `RealErpConnector`) marked not in code; §5/§6 headings |
| `docs/backlog/IMPLEMENTATION_PLAN.md` | Vendor → ERP / Mock ERP; EP-11 title "Adapter ERP thật" (same meaning) |
| `docs/security/THREAT_MODEL.md`, `RBAC_MATRIX.md` | TB-3 and integration-account wording; approval "in target ERP" |
| `docs/SESSION_HANDOFF.md` | New current-state entry; corrected stale branch/commit facts |
| `docs/REBRANDING_REPORT.md` | New (this file) |

The owner's earlier uncommitted modifications (`qa-gatekeeper.md`, `ux-workflow-reviewer.md`, `10-security-boundaries.md`) and deletions were left untouched.

## 5. Original sources preserved
- `docs/01_BRD_SRS_Security.md`, `docs/03_UI_UX_Stitch.md`, `docs/04_Implementation_Backlog.md` at `af2d09f`: not edited, not restored, not committed. They stay the cited "SOURCE FACT" basis, and the label now means "stated in the private reference requirements", **not** "approved by a customer".
- Git history: not rewritten. **It still contains the customer and vendor names.**
- The external original proposal: not added to the repository.

## 6. Remaining references and why
| Location | Reference | Reason |
|---|---|---|
| `backend/pom.xml:4` (comment), `:22` `<description>` | vendor name | Build file. Out of scope (no runtime/config edits). → code issue |
| `backend/src/main/resources/application.yml:4` | comment | Runtime config. Out of scope → code issue |
| `backend/src/main/java/.../erp/package-info.java:2` | Javadoc | Java source. Out of scope → code issue |
| `backend/src/test/java/com/secureerp/archfixture/erp/MockAritoAdapter.java` + 3 refs in `ArchitectureRulesTest.java`, `QuoteUsesErpAdapter.java` | test fixture class name | Renaming a class changes the ArchUnit fixtures → code issue with a test run |
| `backend/src/test/java/.../ConfigurationPolicyTest.java:65` | forbidden pattern for the vendor's domain | **Intentional security guard** (blocks real vendor endpoints in config). Keep it, even after the rename |
| `docs/DECISIONS_REQUIRED.md` A-01 | "15-page original proposal" | Historical approval question about the private source. Wording is neutral |
| `docs/requirements/*`, `SYSTEM_ARCHITECTURE.md` | "BRD nêu một ERP vendor cụ thể" | States provenance honestly without naming the vendor |
| `docs/REBRANDING_REPORT.md` | vendor name, old project names | Needed to record the change and the code impact |
| Git history, `docs/01_BRD_SRS_Security.md@af2d09f` | customer and vendor names | Provenance preserved. Rewriting history is forbidden |
| Zalo / Zalo OA (many docs) | third-party messaging platform | A channel named in the reference scope, not a partner claim. Docs mark it OPEN (D-06) / MVP only. REVIEW_REQUIRED before any demo-facing use |
| Java packages `com.secureerp.*`, artifact `secure-erp-agent`, compose/app names | generic name, not a third-party identity | No legal issue. Renaming to `apas` is optional → code issue |

## 7. Impact on code, API, tests and CI
- Runtime, security behavior, API paths, DB migrations, configuration: **no change**.
- No executable or config file was modified, so the backend test suite was **NOT RUN** for this task (not required, see §9).
- Proposed follow-up issue `chore/APAS-01-vendor-neutral-code-names` (OPEN, needs approval): rename the `MockAritoAdapter` fixture → `MockErpAdapter`, neutralise the pom description/comment, the `application.yml` comment and the `erp/package-info.java` Javadoc; keep the `ConfigurationPolicyTest` guard; optionally rename the artifact. It needs `./mvnw -B clean verify`.
- Roles: the generic roles Sales Staff / Sales Manager / Administrator map to the existing SALES / LEAD / OPS (RBAC_MATRIX, unchanged). **Inventory Staff** has no approved equivalent, so it was **not added**. It is an OPEN design question (D-08), not a requirement.
- Possibly company-specific rules from the reference BRD (VND/VAT handling, 20-line quote limit, 12-month/30-day retention, quote expiry, Zalo channel): **REVIEW_REQUIRED** as rules for "any business". They are already OPEN in D-04, D-06, D-09, D-10, D-13.

## 8. External documents to sync manually (ChatGPT Project; not visible from this repo, not updated)
Project Instructions · Project Charter · Decision Register · Roadmap · Current State · Prompt Library · Session Handoff, plus any uploaded copy of the reference proposal or BRD. These must be renamed to APAS, carry the disclaimer, and be marked reference-only/private.

## 9. Validation results (2026-11-16)
| Check | Command | Result |
|---|---|---|
| Residual names | `rg -n -i --hidden -g '!.git' -e <vendor> -e <customer name variants: accented, unaccented, joined, -/_ separated> -e <proposal date> .` (patterns withheld in this public file) | Remaining hits only in the §6 locations. One ADR-008 hit is a false positive (a substring inside an unrelated Vietnamese word) |
| Whitespace | `git diff --check` | exit 0 (only LF→CRLF autocrlf warnings) |
| Links/paths | scratch script: all `](...)` links and backticked `docs/`/`backend/`/`.claude/` paths in `*.md` | 56 refs checked. Only expected misses: this report (created after the check) and the deleted BRD cited `@af2d09f` (A-01) |
| Diff scope | `git diff --stat` | Only the doc and `.claude` text files listed in §4, plus the owner's earlier changes |
| Doc tooling | — | None in the repo (no markdownlint, CI or docs build) → NOT RUN |
| Backend tests | — | NOT RUN, because no executable or config file changed |

"No legal risk" is **not** claimed. Removing names does not settle usage rights to the reference content.

## 10. Risks and next actions
1. **HIGH, owner action:** git history (4 commits) contains the customer and vendor names and the near-verbatim BRD summary. The GitHub `origin` repository is **already public**: on 2026-11-16 an unauthenticated GitHub API request returned 200, and `main` already contains those commits. This rebranding removes the names from the current documents only, not from the published history. The owner decides (A-16c): e.g. make the repository private, get legal advice, or publish a fresh history-free export instead. Rewriting history needs explicit approval, and it does not remove existing forks, clones or caches.
2. NEEDS_REVIEW: usage rights to the reference BRD/UI/backlog content that the project docs derive from.
3. Owner approves A-16 and the follow-up code issue (§7).
4. Sync the ChatGPT Project documents (§8).
5. Optional: get an `architecture-reviewer`/`security-auditor` read of this diff (advisory).
