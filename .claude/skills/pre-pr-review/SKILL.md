---
name: pre-pr-review
description: Gate changes before any PR
---
# Workflow
Review diff for scope, secrets, sensitive logs, role/ownership, provider egress, tool authorization, pricing and unknown ERP outcome, DB migration, dependencies and test coverage. Report findings with severity and code locations, exact test commands and missing proof. Do not create/push PR or amend commits without approval.

## Required result
Separate VERIFIED, NOT RUN, BLOCKED and PROPOSED. Include exact paths and relevant decision IDs.
