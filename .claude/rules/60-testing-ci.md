---
paths:
  - "**/src/test/**"
  - "**/tests/**"
  - "security-tests/**"
  - ".github/workflows/**"
---
# Test/release policy
- Testing pyramid: domain unit → repository/contract integration → Mock ERP/provider boundary → web E2E → adversarial suites. Deterministic synthetic fixtures only.
- Critical security tests must fail the pipeline. Never mark skip, relax assertion, update golden files or disable suites to obtain green builds without review and recorded rationale.
- Replay and concurrency tests must use real DB unique constraints/transactions in integration tests, not just in-memory mocks.
- Negative tests: cross-customer IDOR, unlinked Zalo, new/unknown tool, excessive query breadth, sensitive value in prompt/output/tool/error/trace, unknown ERP write outcome, tampered quote version.
- Test evidence includes command, environment, timestamp, test counts, failures and relevant redacted logs; distinguish verified from proposed coverage.
- Before PR: formatter/lint, unit tests, integration tests, security checks, dependency scanning where installed, migration check, change-risk review. Do not claim tools are installed without inspection.
