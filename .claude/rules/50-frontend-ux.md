---
paths:
  - "frontend/**"
  - "**/*.tsx"
  - "**/*.jsx"
---
# Frontend & design review
- Source of screens: approved user flows and role matrix, not generated visual mockups alone. Design tool is **Claude Design**: `design-system/` (tokens, components, writing rules) and `docs/design/` (specs, decisions, prototypes; entry `docs/design/DESIGN_GUIDE.md`) are reference/DESIGN PROPOSAL, not production-ready code. `docs/design/prototypes/*.dc.html` scripts are canvas simulation logic; never copy them as product code.
- Where a design spec conflicts with an ADR, `docs/contracts/` or RBAC_MATRIX, the ADR/contract wins; check `docs/DECISIONS_REQUIRED.md` §UX before implementing a screen.
- Use design-system tokens and components; no hard-coded colors/sizes; Be Vietnam Pro; Lucide icons only; Vietnamese UI copy per `design-system/guidelines/writing.md`. Proposed logo (`design-system/assets/logo/`) only after A-17 is approved.
- Required views: Customer Chat, Employee Inbox/Handoff, Quote Details/Version, Security Inspector (synthetic only), Audit Test Dashboard. Customer Chat, Employee Inbox/Handoff and Security Inspector have **no Claude Design screens yet** (UX-13). Layout states: loading, empty, success, validation, forbidden, network timeout, staff takeover, quote expired/superseded.
- Backend authorization is mandatory even when routes/components are hidden. Use typed API client; no business policy duplicated as frontend authority.
- Security Inspector never displays raw customer PII or secrets even for admins; synthetic sample payloads only. Do not store bearer tokens in localStorage without approved security design.
- Design tokens for colors/spacing/typography; keyboard and screen-reader usability; confirmation on draft-order creation; visible provenance labels: AI recognized / Lookup / Verified.
- Mobile responsive for customer-facing; desktop dense workflow for staff; no unsanctioned buttons that imply AI approval of orders.
- Frontend tests cover unauthorized route, quote version mismatch, disabled AI/fallback, retries and accessibility basics.
