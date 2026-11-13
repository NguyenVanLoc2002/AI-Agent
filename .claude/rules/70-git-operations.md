# Repository change discipline
- Inspect `git status` before edits; preserve user changes; no force checkout/reset, delete, branch rewrite, push, PR or mass formatting without explicit instruction.
- Changes tied to one issue; proposed branch `feat/SEC-01-egress-guard` etc.; commits scoped, concise and conventional.
- Distinguish verified tests from attempted tests; review diff for `.env`, API keys, VINs, phone/email, Arito samples and debug logs before any commit.
- Do not change security requirements, root CLAUDE.md, .claude/settings.json, security test baselines or branch protections as a side effect of implementing business features.
- `docs/SESSION_HANDOFF.md` stores decisions/status without credentials or live customer details. `/resume` and `claude --resume` support sessions but handoff file is durable across environments.
