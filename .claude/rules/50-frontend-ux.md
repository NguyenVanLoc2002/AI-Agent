---
paths:
  - "frontend/**"
  - "**/*.tsx"
  - "**/*.jsx"
---
# Frontend & design review
- Source of screens: approved user flows and role matrix, not generated visual mockups alone. Stitch/Figma export is reference, not production-ready code.
- Required views: Customer Chat, Employee Inbox/Handoff, Quote Details/Version, Security Inspector (synthetic only), Audit Test Dashboard. Layout states: loading, empty, success, validation, forbidden, network timeout, staff takeover, quote expired/superseded.
- Backend authorization is mandatory even when routes/components are hidden. Use typed API client; no business policy duplicated as frontend authority.
- Security Inspector never displays raw customer PII or secrets even for admins; synthetic sample payloads only. Do not store bearer tokens in localStorage without approved security design.
- Design tokens for colors/spacing/typography; keyboard and screen-reader usability; confirmation on draft-order creation; visible provenance labels: AI recognized / Lookup / Verified.
- Mobile responsive for customer-facing; desktop dense workflow for staff; no unsanctioned buttons that imply AI approval of orders.
- Frontend tests cover unauthorized route, quote version mismatch, disabled AI/fallback, retries and accessibility basics.
