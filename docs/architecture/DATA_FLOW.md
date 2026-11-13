# Data flow — Chat → Agent → Tool Manager → Mock ERP → Quote → Response

> DESIGN PROPOSAL chi tiết hóa luồng SOURCE FACT tại BRD §03 (`docs/01_BRD_SRS_Security.md@af2d09f`). 2026-11-13, sửa sau review cùng ngày. Chưa VERIFIED.
> Phân loại: `docs/security/DATA_CLASSIFICATION.md`. Lớp: R = RESTRICTED, I = INTERNAL, T = APPROVED_TECHNICAL.

## 1. Luồng chính (UC-01 → UC-03)
| # | Bước | Module | Dữ liệu vào → ra | Kiểm soát | Lỗi → |
|---|---|---|---|---|---|
| 1 | `POST /api/demo/conversations/{id}/messages` | conversation | raw text (untrusted) | session + CSRF; principal → `AuthenticatedCustomerRef`; ownership; dedup `clientEventId`; kích thước/JSON hợp lệ | 400/401/404; trùng → trả kết quả cũ |
| 2 | Kill switch | platform | — | `KillSwitch.isAiEnabled()`; không đọc được trạng thái → coi như OFF (fail-closed) | OFF → handoff `AI_DISABLED` |
| 3 | Inbound sanitize | policy | raw → `SafeMessage` (T) | VIN trích + validate server-side, lưu VIN đầy đủ trong state, chỉ WMI/mask đi tiếp; phát hiện tiền/SĐT/email/ID/honeytoken → redact hoặc reject | reject → handoff `DLP_INBOUND`; VIN sai → hỏi lại (không gọi model hay tool) |
| 4 | Dựng request | agent | `SafeMessage`, history **summary** (T), `ToolSpec` | summary từ structured state; **loại trừ** lượt của nhân viên và quote card | — |
| 5 | Egress preflight | gateway + policy | `ModelRequest` → bytes | serialize allowlist; scan bytes cuối; publish `EgressDecision` (field names, honeytoken ID/policy code, length) → audit | hit/unknown → **không gửi**, handoff `DLP_EGRESS` |
| 6 | Gọi provider | gateway.adapter | bytes → provider | egress proxy; timeout; breaker | 401/403 → `PROVIDER_AUTH`; timeout → `PROVIDER_ERROR` |
| 7 | Model output | gateway → agent | `ModelResponse` (untrusted) | parse strict: text hoặc `ToolProposal` | parse lỗi → handoff |
| 8 | Tool proposal | tools | `ToolProposal` → `ToolInvocation` | allowlist; schema strict; size/depth; authz + ownership; quota conversation + principal | `TOOL_REJECTED`; lặp → handoff |
| 9a | `searchVerifiedProduct` | catalog → `VerifiedProductPort`, `AvailabilityBucketPort` (Mock Arito) | query (T) → `ProductCandidate` + bucket enum (T) | ≤5 kết quả; regex cho sku/oe; strip field R/I tại adapter; bucket không phụ thuộc qty | `CATALOG_UNAVAILABLE` |
| 9b | `requestQuote` | quote | productRef + qty (T) → nội bộ `quote.spi` (R/I) → Quote v1 → `{status, handle?}` (T) | chỉ VERIFIED; qty>0; ≤ giới hạn dòng; mọi lý do không báo giá → `NOT_QUOTED` duy nhất + handoff với reason nội bộ | handoff `QUOTE_NOT_ISSUED` |
| 10 | Tool result → model | tools → gateway | `ToolResultDto` typed (T) | **đi lại qua bước 5** | như bước 5 |
| 11 | Final output | agent | text (untrusted) | `OutputValidator`: không số tiền, số tồn, OE ngoài tool result lượt này, URL, handle lạ, "đã duyệt đơn" | câu trả lời an toàn mặc định/handoff |
| 12 | Compose response | conversation + quote | text + `QuoteCard` (R, chỉ tới đúng khách) | render server-side; resolve handle theo conversation/khách/version/TTL | handle sai → bỏ card, audit |
| 13 | Audit | audit | metadata | traceId, actor pseudonym, tool, decision, policy code, field names | — |

**Bất biến:** dữ liệu R/I đi theo nhánh 9b → 12, không quay lại nhánh 4 → 6. Quote card và tin nhắn của nhân viên **không** vào history gửi model.

## 2. Luồng xác nhận (UC-04) — endpoint thuộc module quote
1. `POST /api/demo/quotes/{id}/confirm {version, confirmationToken}` → quote.
2. Kiểm tra: principal là chủ quote (sai → 404); version = hiện hành (409 `QUOTE_STALE_VERSION`); trạng thái ISSUED; chưa hết hạn theo `Clock` UTC (410); confirmationToken khớp (rate limit chống brute force).
3. Recheck `PricingPort`/`AvailabilityPort`/`CreditEligibilityPort` trong cùng use case:
   - giá đổi → vN `SUPERSEDED`, vN+1 `ISSUED`, `409 QUOTE_REPRICED`;
   - tồn/công nợ không đạt → `CONFIRMATION_BLOCKED` + handoff `QUOTE_NOT_ISSUED` (lý do chỉ nội bộ);
   - đạt → quote `CONFIRMED` (optimistic version).
4. `OrderApi.submit(quoteId, version)`: `INSERT draft_order_request` với `idempotency_key = quoteId:version` **do server sinh**, UNIQUE(quote_id, quote_version); trùng → trả trạng thái request hiện có (request lặp/double click/callback trùng không tạo bản mới).
5. Quote → `ORDER_SUBMITTING`; outbox gọi `DraftOrderPort.create(cmd, key)` ngoài transaction DB.
6. `Created` → `DRAFT_ORDER_CREATED`; `Rejected` → `ORDER_FAILED`; timeout/không rõ → `ORDER_OUTCOME_UNKNOWN` + handoff `ERP_UNKNOWN`; đối soát bằng `findByExternalRef` **chỉ khi** cấu hình capability (đặt tĩnh từ tài liệu đã duyệt, OPEN D-03) cho phép; ngược lại SALES đối soát thủ công. Mỗi bước đối soát ghi audit (bằng chứng reconciliation).
7. Không có đường nào để AI gọi bước 1–6.

## 3. Luồng handoff (UC-05/06)
Trigger (reason code tại `docs/security/THREAT_MODEL.md` §5) → `HandoffApi.open` → event `HandoffOpened` → conversation `HANDOFF_PENDING` (bot khóa) → SALES claim → `HUMAN_ACTIVE` (tin của SALES lưu với direction STAFF, không vào history model) → resolve → event `HandoffResolved` → `ACTIVE_BOT`. Notification nội bộ chỉ chứa reason code + conversation ID.

## 4. Egress sinks
| Sink | PoC | Kiểm soát | Bằng chứng |
|---|---|---|---|
| LLM provider | WireMock/Fake qua proxy local | gateway + policy | SEC-01 capture kép |
| Log/console | có | logging policy, không body | SEC-05 |
| audit_event / egress_record | có | field names, honeytoken ID/policy code, length; không HMAC theo giá trị | SEC-05, T-SEC05-INSPECTOR |
| Test artifacts | có | fixture synthetic; scan | SEC-05 |
| APM/tracing | tắt trong PoC | — | OPEN trước khi bật |
| Zalo, VIN service, Arito thật | không | MVP | skill `privacy-egress-review` |

## 5. Lưu trữ (DESIGN PROPOSAL; retention OPEN D-09)
| Bảng | Module | Lớp | Ràng buộc chính |
|---|---|---|---|
| customer_link | identity | R | UNIQUE(channel, provider_user_hash) |
| conversation | conversation | I | `row_version`, `bot_locked` |
| message | conversation | I/T (đã sanitize) | UNIQUE(conversation_id, client_event_id); direction CUSTOMER/BOT/STAFF |
| quote / quote_line | quote | R | UNIQUE(quote_id, version); CHECK qty>0; kiểu số tiền NUMERIC — scale OPEN D-13 (VND thường không có phần lẻ) |
| quote_handle | quote | I | PK hash; binding; expires_at; revoked |
| draft_order_request | order | I | UNIQUE(quote_id, quote_version); idempotency_key server-derived |
| handoff | handoff | I | reason_code NOT NULL |
| audit_event, egress_record | audit | metadata | không cột body/giá trị |
| feature_switch | platform | — | audit thay đổi |
| mock_erp.* | erp | R (synthetic, sentinel) | schema tách; chỉ erp truy cập |
