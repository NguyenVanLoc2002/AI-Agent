# UI/UX SPECIFICATION & STITCH HANDOFF

## Recommendation
Use Stitch for rapid design exploration and connected prototypes; keep Figma (optional) for review/design-system control, and implement with React + TypeScript + component library. Generated UI does not enforce authorization or domain correctness. Do NOT upload real customer/ERP records to a design AI.

## Personas and sitemap
1. Customer / Web-chat Demo: chat, verified product suggestion, quote card, explicit confirmation.
2. Sales agent: inbox, handoff queue, conversation/verified evidence, quote detail, exceptions.
3. Security reviewer: sanitized trace timeline, egress fields, policy decisions, SEC-01..07 test dashboard (NO raw secrets/prices in outbound trace).
4. System operator: provider toggles and kill switch (no business-data read by default).

## Core flows / acceptance
- Chat: compose → sanitized processing state → verified/not-verified suggestion → quote card → confirmation action bound to quote version → receipt `pending employee approval`.
- Handoff: reason badge, latest evidence/source, assign, agent pause, return to bot only after explicit resolution.
- Security inspector: incoming category (not raw PII), redaction categories, tool name, policy decision, provider request field names, correlation ID; deny unauthorized viewer.
- Quote: show qty, unit price, total, currency, valid-until and version; backend only determines amounts; stale version disables confirm.
- All states: loading, empty, no matches, partial data, blocked, provider failure, authorization denied, expired, duplicate action, mobile/responsive.

## DESIGN.md mini design-system guidance
- Aesthetic: restrained enterprise SaaS; clear hierarchy, ample whitespace, high contrast; no fake charts or security status.
- Colors: navy/slate neutrals, subtle blue interactive, semantic red error/green verified/amber manual review. Check WCAG contrast.
- Components: app shell, navigation, data grid, filter chip, conversation panel, quote card, side drawer, trace stepper, table, status pill, confirmation modal.
- Safety: never use color alone for status; quotes display sourced verification; distinguish AI-recognized from ERP-verified; never include actual PII in design references.

## Stitch prompt
Design a responsive Vietnamese-language enterprise SaaS web application for a secure automotive-parts AI Agent. Roles: customer, sales staff, security reviewer. Main views: (1) customer chat with verified spare-part result and versioned quote confirmation; (2) staff handoff inbox with conversation, verified source and exception reason; (3) security inspector timeline with data masking summary, tool permissions, LLM egress field names, and pass/fail security test grid. Use realistic FICTIONAL data only. Clearly label AI-recognized, catalog-lookup, and verified results. Do not imply the AI can view cost, customer debt, other-customer prices, or approve orders. Enterprise-ready minimal design, accessible contrast, responsive desktop/tablet/mobile. Create connected clickable prototype including loading, permission denied, expired quote, blocked tool call, handoff, and successful draft-order states. Export consistent DESIGN.md tokens and reusable component guidance.

## UI review gates
1. Business owner checks UCs and states. 2. Security reviewer validates absence of privileged actions in wrong roles. 3. Frontend reviews accessibility/responsiveness. 4. Implement from approved screens, not direct blind paste from AI-generated markup. 5. UI E2E tests against backend authorization.
