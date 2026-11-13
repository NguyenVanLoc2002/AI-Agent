# Claude Code starter kit

Place `CLAUDE.md` in repo root and `.claude/` in repo root. Copy the three `docs/` artifacts from the delivery archive under `docs/` (renaming remains consistent with the references).

Start Claude Code from repo root: `claude`. Use `/implement-issue`, `/privacy-review`, `/agent-tool-contract`, `/quote-workflow`, `/security-acceptance`, `/ui-from-stitch`, `/session-handoff` as scoped repeatable skills. Ask Claude to delegate independent read-only review to architecture-reviewer, security-auditor, backend-reviewer, qa-planner or ux-reviewer.

Keep secret `.env`, database fixtures with real customer data and raw provider payloads outside git. Do not grant broad approval to run destructive commands or change network/security policy.

The files are initial policy/workflow scaffolding, NOT a replacement for CI checks, runtime least privilege or security assurance. Verify any Claude Code version-specific frontmatter/tool names on the installed version.
