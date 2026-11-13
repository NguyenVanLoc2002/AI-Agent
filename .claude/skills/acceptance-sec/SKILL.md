---
name: acceptance-sec
description: Execute SEC-01 through SEC-07 with proof
---
# Workflow
Only synthetic fixtures; do not target production. Read docs/SECURITY_TEST_MATRIX.md. Identify actual commands and capture provider egress. Run: 200 chats with cost/debt sentinel, 20 cross-customer prompts, 50 injection variants, 10 parallel confirmations + timeout, redacted logs, kill switch ≤60s and key revocation. For each record command/environment/count/result/evidence. Absent executable harness means NOT RUN, not PASS. Any leakage or duplicate write blocks release; do not weaken tests.

## Required result
Separate VERIFIED, NOT RUN, BLOCKED and PROPOSED. Include exact paths and relevant decision IDs.
