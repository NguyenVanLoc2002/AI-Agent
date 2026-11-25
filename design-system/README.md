# AutoParts Agent Sandbox — Design System

**AutoParts Agent Sandbox** is a demo platform for AI Agents in the automotive spare-parts (aftermarket) industry. Employees search parts by VIN / OE / SKU, check ERP-backed price and stock, build quotations, review draft orders and work alongside a secure, contextual AI assistant. It should feel like a **premium operational workspace**, not a chatbot and not a marketing site.

> **Export status (17/11/2026).** This folder is an export of the Claude Design artifact, not a buildable package. Missing from the export (see `EXPORT-NOTES.md`): `styles.css` and `tokens/*.css` (use `tokens.css` + `components/bundle.css`), `ui_kits/app/`, `screens/`, `thumbnail.html`, `guidelines/*.html` (only `guidelines/writing.md`), `assets/notes/`. All `components/<group>/*.jsx` sources incl. `core/Icon.jsx` and `core/a11y.jsx` were added on 25/11/2026 (A-18(b), A-66). `components/bundle.js` is an IIFE on `window.AutoPartsAgentDesignSystem_aa8546` that also **self-renders the demo UI kit into `#root`**: do not include it in the product frontend (A-18). Paths below that are missing are kept as a record of the source artifact.
>
> **Logo.** A proposed logo now exists in `assets/logo/` (PROPOSED, A-17). Until approved, the rule below ("No logo exists… Do not draw a mark") still applies to product UI.
>
> **Line-height.** "≥ 1.4 everywhere" has token exceptions: `--type-page-title` 26px/1.3 and `--type-metric` 32px/1.2.

## Sources
- The only input was the written brief from the user (company description + "Design System Direction"). No codebase, Figma, logo, screenshots or font files were provided.
- Design references (Refero style analyses of each product's **marketing homepage**, read as text; screenshots not viewed; no branding, logos or layouts copied):
  - ClickUp — https://styles.refero.design/style/efcb73cb-b84a-4ae7-9a2b-e1116f79f130. **Adopted:** border-over-shadow flatness, ≤2 elevation levels, compact density, status pills, avatar clusters. **Rejected:** pill buttons, 48–80px display type, brand violet, conic gradients, Plus Jakarta / Inter / mono fonts.
  - Dub — https://styles.refero.design/style/b0d80806-b724-4ed1-a1d1-074edd3c9bc9. **Adopted:** 1px-border-first containers, fixed radius vocabulary, one primary action per surface, soft-fill active sidebar item, pill status badge with dot, bottom-border-only table rows, nested muted card, 4px ring for floating panels. **Rejected:** Satoshi / Inter / Geist Mono, black primary buttons, dotted-grid background, multicolor feature pills.
  - Seline Analytics — https://styles.refero.design/style/7967c6d9-e50c-42b5-b4d1-74003ba41781. **Adopted:** single-accent restraint, white KPI cards with soft 16px-blur shadow and 24px padding, pill tab group with dark active state for view switching, generous 14px body line-height. **Rejected:** warm stone canvas (brief mandates #F8F9FA), cyan accent, mascot, grayscale image filters, Roobert. **Note:** the source has no chart specification — chart tokens here are our own.
- Font: **Be Vietnam Pro** (mandated). Self-hosted from Google Fonts (OFL) in `assets/fonts/`: vietnamese + latin-ext + latin subsets, weights 400/500/600/700.
- Icons: **Lucide** v0.460.0 (ISC). 82 SVGs in `assets/icons/`, inlined in `components/core/Icon.jsx`.
- **No logo exists.** The brand is set in type ("AutoParts Agent", 700, plus a gray "Thử nghiệm" chip). Do not draw a mark.

## Products
One surface: the **web app** (desktop-first operational dashboard). Modules: Dashboard, Product Lookup, Product Details, AI Assistant (side panel), Quotation Workspace, Draft Orders, Activity & Audit. See `ui_kits/app/`.

---

## CONTENT FUNDAMENTALS
- **Vietnamese-first, natural, operational.** Every label, button, dialog and toast is in Vietnamese. English appears only in technical identifiers (SKU, OE, VIN, ERP, tool ids such as `erp.get_stock`).
- **Spelling: new-style tone placement** (kiểu đặt dấu mới), applied everywhere: **Hủy, Xóa, Khóa, Hóa đơn, Tối ưu hóa, Ủy quyền**. Never mix with Huỷ/Xoá.
- **Sentence case** everywhere ("Tra cứu phụ tùng", "Thêm vào báo giá"). Uppercase only for overlines and table headers (12px/600, +0.04em).
- **Voice.** Polite and brief. The system says "bạn" ("Bạn không có quyền truy cập", "vui lòng xác nhận"). The AI speaks as "Tôi" and always states what it did and what it did *not* do.
- **Buttons use verbs, never "OK":** "Gửi duyệt", "Xác nhận tạo đơn", "Thử lại", "Hủy". Sensitive confirmations repeat the verb in the dialog title and the confirm button.
- **Errors = what happened + consequence + next step:** "Yêu cầu quá thời gian. Chưa có thay đổi nào được ghi. Vui lòng thử lại."
- **Formats:** VND `1.250.000 ₫` (dot thousands, space, ₫ suffix). Decimal comma: `0,8 giây`, `412,6 triệu ₫`. Dates `16/11/2026 09:42` (24h). Relative: "5 phút trước".
- **No emoji, no hype**, no exclamation marks. No vague marketing words ("tuyệt vời", "siêu nhanh", "thông minh vượt trội").
- **Never claim success before the backend confirms.** While waiting: "Đang gửi…", "Đang tạo…" (button locked). Only a confirmed response may produce "Đã …". A timeout is *not* a failure: say "Chưa nhận được phản hồi" and tell the user to check status before retrying, to avoid duplicates.
- **Never expose internals or sensitive data in user-facing errors.** No stack traces, HTTP codes, endpoints, SQL, tokens, internal role names or IDs, and no data outside the user's permissions. Show only a reference code ("Mã tham chiếu: REQ-7F2A").
- **Keep information types apart:** verified (ERP + time) · AI suggestion · cached copy · warning · error · pending confirmation · restricted. Each has its own label, color and icon; never mix two in one phrase.
- Full guide with examples: **`guidelines/writing.md`**. Writing cards: button labels, table headings, notifications, empty states, error messages, information types.

### Glossary (use exactly)
- **Báo giá** (quotation) · **Phiên bản** (version) · **Gửi duyệt / Duyệt / Trả lại / Bị từ chối**
- **Đơn hàng nháp** (draft order) · **Tạo trên ERP** · **Cần cấp có thẩm quyền**
- **Tồn kho khả dụng / Đã giữ / Đang về** · **Chi nhánh** · **Kho xuất**
- **Giá bán** (price, ERP) · **Chiết khấu (CK)** · **Tạm tính / VAT / Tổng thanh toán**
- **Mã OE / SKU / Số VIN** · **Hàng chính hãng / Hàng thay thế** · **Còn hàng / Sắp hết / Hết hàng**
- **Trợ lý AI** · **Gợi ý AI** · **Đã xác minh từ ERP** · **Bản lưu** (cached) · **Nhập tay**
- **Không có quyền** · **ERP gián đoạn** · **Quá thời gian** · **Đang tải…**

### State copy patterns
- Loading: "Đang tải…", "Đang gửi…", "Đang tạo…" (verb + …)
- Empty: "Chưa có {đối tượng}" + how to create one
- No results: "Không tìm thấy kết quả" + "Kiểm tra lại mã hoặc bỏ bớt bộ lọc."
- Unauthorized: "Bạn không có quyền {hành động}" + who to contact. Neutral tone, never accusatory.
- ERP unavailable: "Không kết nối được ERP" + what is hidden/locked + "Dữ liệu có thể chưa cập nhật."
- AI unavailable: "Trợ lý AI tạm ngưng" + "Bạn vẫn có thể thao tác thủ công."
- Timeout: "Yêu cầu quá thời gian" + "Chưa có thay đổi nào được ghi."
- Success: past tense + object + id: "Đã lưu báo giá BG-2026-0412"

---

## VISUAL FOUNDATIONS
- **Colors.** White surfaces on a `#F8F9FA` app background. Text `#202020` / body `#374151` / secondary `#64748B`. Borders `#E5E7EB` (cards), `#D1D5DB` (inputs), `#8792A4` (checkbox, radio, switch: 3:1).
- **Action color.** One blue, `#2563EB` (hover `#1D4ED8`, press `#1E40AF`).
- **Status colors.** Success `#16A34A`, warning `#D97706`, error `#DC2626`. Each has a 50-tint bg, a 200 border and a 700 `-fg` text color.
- **Restricted (no permission)** uses neutral gray + lock (`--status-restricted-*`), never red.
- **Provenance pair:** AI = violet `#7C3AED`, ERP-verified = teal `#0D9488`. Reserved for that meaning only.
- **Never use color alone.** Every badge, alert, toast and tool result pairs an icon with a label. Deltas carry arrows and signs.
- **Type.** Be Vietnam Pro only, including codes. Use the `--type-*` role tokens; don't hard-code sizes.
  - Weights: 400 body/table, 500 labels/nav/controls, 600 card titles/headers/sections, 700 page titles/metrics.
  - Sizes: page 26, section 18, card 15, body 14, table and button-sm 13, helper/badge/caption 12.
  - **Minimum 12px. Line-height ≥ 1.4** everywhere, including buttons and badges, to protect stacked diacritics. No negative tracking.
  - Numbers use `tabular-nums`.
- **Codes (VIN/OE/SKU):** Be Vietnam Pro 500 + `tabular-nums` + `--tracking-code` (.02em). Use the `.ap-code` class or `mono` props. There is no monospace font.
- **Spacing.** 8px grid with a 4px half-step. Card padding 20, page gutter 28–32, control gaps 8, stack gaps 12–16, section gaps 20–24.
- **Radii.** 6 small controls/badges · 8 buttons/inputs · 10 cards/tables/dialogs/drawers. `--radius-xs` 4 only for checkbox/kbd; `--radius-xl` reserved. Pills only for filter tags and avatars.
- **Borders.** `--border-width-default` 1px for surfaces and inputs; `--border-width-strong` 1.5px for checkbox and radio only.
- **Cards.**
  - `default`: white, 1px `--border-card`, `shadow-xs`, radius 10, padding 20.
  - `elevated`: dashboard KPI/chart cards, `--shadow-card`, padding 24.
  - `nested`: `--bg-nested`, no border.
  - No colored left-border cards.
- **Elevation — border first.** Containers are defined by 1px borders; shadows are secondary. **Max two elevation levels per region.**
  - Scale: xs (cards, inputs) → `--shadow-card` (dashboard KPI/chart cards) → `--shadow-ring` (popover, floating preview) → md → lg (drawer, toast) → xl (dialog).
  - Nested sub-panels use `--bg-nested` with no border and no shadow.
  - Scrim `rgba(17,24,39,.40)`, no blur.
- **One primary action per surface.** A page header, dialog or card has at most one filled primary button; everything else is secondary, ghost or subtle.
- **Badge shapes.** Pill (`shape="pill"`, dot) = record status (order, quotation, stock). Rounded 6px = category or provenance.
- **Backgrounds.** Flat. No gradients (except the skeleton shimmer), no glassmorphism, no illustrations or imagery.
- **Interaction.** Hover → `#F1F3F5` / one step darker. Press → one more step, no scale. Focus → 3px ring `--focus-ring` (error: `--focus-ring-error`).
- **Motion.** Functional only: 120ms (hover, toggle), 180ms (tabs, tooltip), 240ms (drawer, dialog, panel). Easing `cubic-bezier(.2,0,0,1)`. Fully disabled under `prefers-reduced-motion`.
- **Layout.**
  - Sidebar 248 / 64px collapsed; header 56px; content max-width 1440px.
  - AI panel 400px docked right; drawers 480px.
  - Tables: sticky white header separated by a bottom border only, 1px row dividers, 44px rows (36 compact), numerals right-aligned.
  - Sidebar active item: soft blue fill (`--bg-selected`) + blue icon, no left bar.
- **Dashboard layout.**
  - Order: page header (1 primary action) → KPI row (MetricCard, 4 columns) → primary ChartCard + side list → DataTable.
  - Gaps 16 between cards, 24 between regions.
  - **One chart accent** (`--chart-primary`) + neutrals for comparison. Status colors only when the data is a status.
  - Every chart needs a legend, a text `summary` and a data-source tag.

## COMPONENT STATES
Every interactive component supports the states below. See the "State matrix" card.
- **Default:** `--border-input` on `--bg-surface`.
- **Hover:** `--bg-hover`, or the `*-hover` token for filled controls.
- **Focus:** `--focus-ring` + `--border-focus`.
- **Disabled:** `disabled`, `--bg-disabled`, 50% opacity, `not-allowed` cursor.
- **Loading:** `loading` prop shows the Lucide spinner, sets `aria-busy` and blocks repeat submits (duplicate request prevention).
- **Error:** `error` prop gives a red border + icon + message (`role="alert"`).
- **Success:** `success` prop gives a green check + message.
- **Unauthorized:** `restricted` prop gives neutral styling + lock + reason (Button, Input, Select, Textarea). EmptyState `unauthorized`, ToolCall / ActionConfirmation / ERPResult `unauthorized` / `restricted`. **Presentation only: the backend must still authorize every request.**

## ACCESSIBILITY
- **Contrast.**
  - Text ≥ 4.5:1; large text (≥ 18px/600) and UI boundaries ≥ 3:1.
  - Status text always uses the `-fg` tokens.
  - `--text-tertiary` (2.5:1) is for icons and placeholders only, never readable text.
- **Keyboard.**
  - Every interactive element is reachable with Tab and has a visible focus ring.
  - Enter/Space activates; clickable table rows are focusable and Enter opens them.
  - Sortable headers are real buttons. Tabs use ←/→, Home, End (roving tabindex).
  - Dialog and Drawer move focus in, trap Tab, close on Esc and return focus to the trigger. Tooltip closes on Esc.
- **Semantics.**
  - Icon-only buttons need a Vietnamese `label`.
  - Checkboxes without visible text need `aria-label`; tables take a `caption`.
  - Error toasts and alerts use `role="alert"`; info uses `role="status"`.
  - Warning/danger dialogs use `role="alertdialog"`.
  - Field messages are linked via `aria-describedby`.
- **Never rely on a tooltip** for essential information.
- **Motion** respects `prefers-reduced-motion`.

## AI vs ERP PROVENANCE
- **AI-generated** (violet, `sparkles`): ChatMessage assistant header "Trợ lý AI · Gợi ý, cần kiểm tra"; Badge `tone="ai"`; SourceTag `source="ai"`.
- **ERP-verified** (teal, `shield-check`): ERPResult `state="verified"` with a sync time; Badge `tone="erp"`; SourceTag `source="erp"`. **Not "verified" without a timestamp.**
- **Cached / unverified** (amber, `clock`): ERPResult `state="stale"`, SourceTag `source="cache"`. When ERP is down, hide values that cannot be verified rather than show stale numbers as truth.
- **Pending actions** need explicit human confirmation via ActionConfirmation. The AI never changes prices, approves quotations, modifies inventory or submits transactions by itself.
- **AI-suggested values inside forms or tables** carry `SourceTag source="ai"` and are **never saved automatically**: the user must accept them.
- **Tool results** go through ToolCall: running, success, error, unauthorized, timeout, unavailable.

## ICONOGRAPHY
- **Lucide is the single icon family**, rendered as inline SVG (no icon fonts, no PNG). Outline, **stroke 2px** (`--icon-stroke`), round caps, `currentColor`.
- **Sizes** (`--icon-size-*`):
  - **20px standard** (default of `<Icon>`): sidebar, page/section headers, toasts, alerts, dialogs, empty states, icon buttons md/lg, button lg.
  - **16px dense:** tables, buttons sm/md, inputs, selects, tabs, tags, breadcrumb.
  - **14px** only inline with 12px text: badges, field helper/error lines, captions.
  - Checkmarks inside 16px checkboxes are the single exception (12px, stroke 3).
- **Alignment & spacing:** vertically centered with the text line; icon → label gap 8px (`--icon-gap`), 6px in compact controls (`--icon-gap-compact`); leading icons only (trailing only for chevrons / external link).
- **A11y:** decorative icons are `aria-hidden`; icon-only buttons need a Vietnamese `label`; a standalone meaningful icon needs `title`. Never use an icon as the only carrier of status.
- Use `<Icon name="…" />` or `assets/icons/*.svg`. Add new icons only from `lucide-static@0.460.0`. **No hand-drawn SVG**: Spinner also uses Lucide `loader-circle`.
- Semantic icons:
  - `sparkles` AI · `shield-check` ERP-verified · `clock` cached
  - `lock` no permission · `server-off` ERP unavailable · `timer-off` timeout
  - `circle-pause` awaiting confirmation · `cpu` tool call
- No emoji, no unicode glyphs as icons, no icon fonts.

---

## Index
- `styles.css` — entry point; imports `tokens/fonts.css`, `colors.css`, `typography.css`, `spacing.css`, `effects.css`, `base.css`.
- `tokens.json` — the design tokens (colors, type roles, spacing, radius, shadows, sizes, layers); `tokens.css` is generated from it. `components/bundle.css` holds the font faces, `--type-*` role shorthands, motion and base rules.
- `assets/fonts/` — Be Vietnam Pro woff2 files. `assets/icons/` — Lucide SVGs.
- `guidelines/` — specimen cards (Colors, Type, Spacing, Brand, Accessibility, Writing) + `writing.md` (full UI writing guide).
- `components/` — React primitives, one `@dsCard` per component folder (`components/<Comp>/preview.html`, beside its `README.md` and `.d.ts`); sources in `components/<group>/*.jsx`, built into `components/bundle.js`, types in `components/index.d.ts`. `components/core/a11y.jsx` holds shared focus, modal and code-style helpers (internal).
- `ui_kits/app/` — click-through web app (`index.html`).
- `CHANGELOG.md` — design-system change history.
- `thumbnail.html`, `assets/notes/SKILL.from-standalone.md` (was `SKILL.md`).

## Components
- **core:** Icon, Button, IconButton, Spinner
- **forms:** Field, Input, Select, Textarea, Checkbox, Radio, Switch, SearchField
- **data:** Badge, Tag, Card, MetricCard, DataTable, TableToolbar, Pagination, DescriptionList
- **dashboard:** ChartCard, AvatarGroup
- **navigation:** Sidebar, AppHeader, Breadcrumb, PageHeader, Tabs
- **feedback:** Alert, Toast (+ToastViewport), Tooltip, Dialog, Drawer, EmptyState, Skeleton
- **ai:** AIPanel, ChatMessage, ToolCall, ERPResult, ActionConfirmation, AIComposer, SourceTag

### Intentional additions
No source inventory existed, so this is an authored standard set. Domain additions:
- **ERPResult, ToolCall, ActionConfirmation, AIPanel, AIComposer, ChatMessage, SourceTag**: AI provenance and human confirmation.
- **SearchField**: VIN/OE/SKU search.
- **TableToolbar**: filtering and bulk actions for tables.
- **ChartCard, AvatarGroup**: dashboard charts with built-in states and provenance; assignees/approvers (from the Refero reference review).
- **EmptyState presets**: the mandated system states.
- **MetricCard / DescriptionList**: dashboards and product specs.
