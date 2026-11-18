---
name: ui-from-claude-design
description: Implement an approved Claude Design workflow screen accessibly
---
# Workflow
Review the approved BRD/role matrix/flow and the Claude Design output: `docs/design/DESIGN_GUIDE.md`, the batch spec in `docs/design/specs/`, design decisions in `docs/design/decisions/`, the screen's prototype in `docs/design/prototypes/` (behavior reference only), and `design-system/` (README, `guidelines/writing.md`, tokens, component `.d.ts`/README). Check `docs/DECISIONS_REQUIRED.md` §UX: a screen with an unresolved UX-xx conflict is BLOCKED until the owner decides; the ADR/contract/RBAC_MATRIX version wins meanwhile.

Create a screen→use-case→role→API→state→test mapping (start from `docs/design/specs/90-danh-muc-man-hinh.md` §UC mapping). Implement typed React UI with design-system tokens and components, covering loading, empty, error, forbidden, quote expired/version mismatch, takeover, unknown ERP outcome and provider outage. Security Inspector only displays redacted/synthetic traces. Backend remains authority; UI hiding/locking is presentation only. Run keyboard/accessibility and E2E tests. Never assume a generated visual prototype verifies business rules, and never copy `<script type="text/x-dc">` prototype logic as product code.

## Required result
Separate VERIFIED, NOT RUN, BLOCKED and PROPOSED. Include exact paths, screen IDs, use-case IDs and relevant decision IDs (D-xx, A-xx, UX-xx).
