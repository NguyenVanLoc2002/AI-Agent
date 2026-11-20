---
name: implement-issue
description: Implement one issue with proof and rollback
---
# Workflow
Inputs: issue ID, BRD link, acceptance examples, branch. Without an issue ID, pick the next one from `docs/SESSION_HANDOFF.md` ("Next" of the current section) and the deferred queue `docs/backlog/IMPLEMENTATION_PLAN.md` §2a (first OPEN row whose dependencies are met; skip BLOCKED; PROPOSED rows need an owner decision first); say which one and why. When an item is deferred during an issue, add it to §2a with its source decision. Read git status, relevant source, tests and contracts first. Plan changed modules, security risks, fixtures, migrations and rollback. Implement a minimal vertical slice; add positive, negative, permission and failure tests. Run actual checks, record exact command and exit code. Review diff for secrets and scope creep. Output: issue, changed files, verified tests, review findings, unresolved risk, next issue. No autonomous commit/push.

## Required result
Separate VERIFIED, NOT RUN, BLOCKED and PROPOSED. Include exact paths and relevant decision IDs.
