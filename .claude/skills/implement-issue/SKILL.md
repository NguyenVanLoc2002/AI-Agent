---
name: implement-issue
description: Implement one issue with proof and rollback
---
# Workflow
Inputs: issue ID, BRD link, acceptance examples, branch. Read git status, relevant source, tests and contracts first. Plan changed modules, security risks, fixtures, migrations and rollback. Implement a minimal vertical slice; add positive, negative, permission and failure tests. Run actual checks, record exact command and exit code. Review diff for secrets and scope creep. Output: issue, changed files, verified tests, review findings, unresolved risk, next issue. No autonomous commit/push.

## Required result
Separate VERIFIED, NOT RUN, BLOCKED and PROPOSED. Include exact paths and relevant decision IDs.
