# Data flow — Chat → Agent → Tool Manager → Mock ERP → Quote → Response

> DESIGN PROPOSAL chi tiết hóa luồng SOURCE FACT tại BRD §03 (`docs/01_BRD_SRS_Security.md@af2d09f`). 2026-11-13, sửa sau review cùng ngày. Chưa VERIFIED.
> Phân loại: `docs/security/DATA_CLASSIFICATION.md`. Lớp: R = RESTRICTED, I = INTERNAL, T = APPROVED_TECHNICAL.

## 1. Luồng chính (UC-01 → UC-03)
| # | Bước | Module | Dữ liệu vào → ra | Kiểm soát | Lỗi → |
|---|---|---|---|---|---|
| 1 | `POST /api/demo/conversations/{id}/messages` | conversation | raw text (untrusted) | session + CSRF; principal → `AuthenticatedCustomerRef`; ownership; dedup `clientEventId`; kích thước/JSON hợp lệ | 400/401/404; trùng → trả kết quả cũ |
| 2 | Kill switch (**chưa làm**: EP-09, ADR-013 PROPOSED; EP-04 không có bước này) | platform | — | `KillSwitch.isAiEnabled()`; không đọc được trạng thái → coi như OFF (fail-closed) | OFF → handoff `AI_DISABLED` |
| 3 | Inbound sanitize | policy | raw → `SafeMessage` (T) | VIN trích + validate server-side, lưu VIN đầy đủ trong state, chỉ WMI/mask đi tiếp; phát hiện tiền/SĐT/email/ID/honeytoken → redact hoặc reject | reject → handoff `DLP_INBOUND`; VIN sai → hỏi lại (không gọi model hay tool) |
| 4 | Dựng request | agent | `SafeMessage`, history **summary** (T), `ToolSpec` | summary từ structured state; **loại trừ** lượt của nhân viên và quote card | — |
| 5 | Egress preflight | gateway + policy | `ModelRequest` → bytes | serialize allowlist; scan bytes cuối; publish `EgressDecision` (field names, honeytoken ID/policy code, length) → audit | hit/unknown → **không gửi**, handoff `DLP_EGRESS` |
| 6 | Gọi provider | gateway.adapter | bytes → provider | egress proxy; timeout; breaker | 401/403 → `PROVIDER_AUTH`; timeout → `PROVIDER_ERROR` |
| 7 | Model output | gateway → agent | `ModelResponse` (untrusted) | parse strict: text hoặc `ToolProposal` | parse lỗi → handoff |
| 8 | Tool proposal | tools | `ToolProposal` → `ToolInvocation` | allowlist; schema strict; size/depth; authz + ownership; quota conversation + principal | `TOOL_REJECTED`; lặp → handoff |
| 9a | `searchVerifiedProduct` | catalog → `VerifiedProductPort`, `AvailabilityBucketPort` (Mock ERP) | query (T) → `ProductCandidate` + bucket enum (T) | ≤5 kết quả; regex cho sku/oe; strip field R/I tại adapter; bucket không phụ thuộc qty | `CATALOG_UNAVAILABLE` |
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
4. `OrderApi.submit(quoteId, version)` (EP-07: `DraftOrders.register` + `dispatch`, xem §2a): `INSERT draft_order_request` với `idempotency_key = quoteId:version` **do server sinh**, UNIQUE(quote_id, quote_version); trùng → trả trạng thái request hiện có (request lặp/double click/callback trùng không tạo bản mới).
5. Quote → `ORDER_SUBMITTING`; outbox gọi `DraftOrderPort.create(cmd, key)` ngoài transaction DB.
6. `Created` → `DRAFT_ORDER_CREATED`; `Rejected` → `ORDER_FAILED`; timeout/không rõ → `ORDER_OUTCOME_UNKNOWN` + handoff `ERP_UNKNOWN`; đối soát bằng `findByExternalRef` **chỉ khi** cấu hình capability (đặt tĩnh từ tài liệu đã duyệt, OPEN D-03) cho phép; ngược lại SALES đối soát thủ công. Mỗi bước đối soát ghi audit (bằng chứng reconciliation).
7. Không có đường nào để AI gọi bước 1–6.

### 2a. Triển khai EP-07 (2026-11-19)
- `{id}` trong URL là mã báo giá công khai (`BG-…`); id nội bộ không ra ngoài. Thứ tự thật trong `DefaultQuoteConfirmations`: vai trò CUST + khách đã liên kết (403) → **giữ chỗ lần thử** dưới khóa theo khách (quá 20 lần/giờ → 429) → đọc báo giá theo mã + version + digest khách và so token (không khớp bất kỳ điều gì → cùng một 404) → trạng thái (không còn ISSUED: 409/410 hoặc trả lại trạng thái hiện tại, không tạo đơn thứ hai) → hết hạn (410) → recheck công nợ, giá, tồn ngoài transaction.
- Recheck: không đạt → `CONFIRMATION_BLOCKED` + dòng NOT_QUOTED (khóa A-34) + handoff `QUOTE_NOT_ISSUED`, khách không được biết lý do; giá đổi → version cũ `SUPERSEDED`, version mới `ISSUED` cùng mã, 409 `QUOTE_REPRICED`, khách lấy card mới bằng `GET /api/demo/quotes/{ref}`; ERP không trả lời → 503 + handoff `CATALOG_UNAVAILABLE`, báo giá giữ nguyên ISSUED.
- Đạt: một transaction ghi ISSUED → CONFIRMED → ORDER_SUBMITTING (kiểm hạn theo giờ **sau** recheck) và `DraftOrders.register` (dòng `draft_order_request` PENDING + digest của đơn). Sau commit `DraftOrders.dispatch`: claim PENDING → SUBMITTED bằng UPDATE có điều kiện, gọi ERP một lần ngoài transaction, ghi kết quả.
- Kết quả: `Created` → `DRAFT_ORDER_CREATED`; `Rejected` → `ORDER_FAILED` + handoff `ORDER_NOT_CREATED`; timeout/không phân loại được/không ghi được kết quả/quá `submit-timeout` → `ORDER_OUTCOME_UNKNOWN` + handoff `ERP_UNKNOWN`, không gửi lại. SALES xem `GET /api/internal/orders/unknown` và ghi kết quả đối soát (`CREATED` kèm số đơn nháp ERP, hoặc `ABSENT`); mỗi bước ghi vào `draft_order_event` kèm người thực hiện.
- Khác với bản thiết kế ở trên: không có tiến trình outbox riêng (gửi ngay trong request xác nhận; request bỏ dở chỉ được phát hiện khi có người đọc lại, A-41(l)); handoff đi qua sự kiện `QuoteStaffRequired` do conversation xử lý.

### 1a. Triển khai EP-04 (2026-11-19)
- Thứ tự thật trong `ChatService`: owner (404) + khách gắn với conversation (A-35, 403) → sanitizer (A-21) → **tiếp nhận nguyên tử** (advisory lock theo conversation: dedup `clientEventId`, tối đa một lượt đang chạy → 409, giới hạn theo conversation + principal → 429, đọc `status`, insert tin khách đã sanitize) → conversation đang chờ nhân viên → câu cố định → VIN intake với text gốc (A-29; Invalid/Multiple → hỏi lại, Unavailable → handoff, không gọi model) → agent (≤3 lần gọi model, history đến đúng tin của lượt này) → render card phía server.
- Mọi lượt đã tiếp nhận đều kết thúc bằng một câu trả lời đã lưu; lỗi bất ngờ → handoff `PROVIDER_ERROR`; lượt bỏ dở quá `turn-timeout` (2 phút) được hoàn tất thành handoff khi client gửi lại cùng event.
- Bước 2 (kill switch) chưa có; bước 11: OutputValidator loại cả câu trả lời và handoff `OUTPUT_REJECTED` (quyết định 2026-11-19).

## 3. Luồng handoff (UC-05/06)
Trigger (reason code tại `docs/security/THREAT_MODEL.md` §5) → EP-04: conversation đặt `HANDOFF_PENDING` (bot khóa) và gọi `Handoffs.open` trong **một transaction** (chưa có event; event `HandoffOpened` là đề xuất cho EP-08) → SALES claim → `HUMAN_ACTIVE` (tin của SALES lưu với direction STAFF, không vào history model) → resolve → event `HandoffResolved` → `ACTIVE_BOT`. Notification nội bộ chỉ chứa reason code + conversation ID.

## 4. Egress sinks
| Sink | PoC | Kiểm soát | Bằng chứng |
|---|---|---|---|
| LLM provider | WireMock/Fake qua proxy local | gateway + policy | SEC-01 capture kép |
| Log/console | có | logging policy, không body | SEC-05 |
| audit_event / egress_record | có | field names, honeytoken ID/policy code, length; không HMAC theo giá trị | SEC-05, T-SEC05-INSPECTOR |
| Test artifacts | có | fixture synthetic; scan | SEC-05 |
| APM/tracing | tắt trong PoC | — | OPEN trước khi bật |
| Zalo, VIN service, ERP thật | không | MVP | skill `privacy-egress-review` |

## 5. Lưu trữ (DESIGN PROPOSAL; retention OPEN D-09)
| Bảng | Module | Lớp | Ràng buộc chính |
|---|---|---|---|
| customer_link | identity | R | EP-06 (Flyway V7): PK `customer_ref` (ref ngẫu nhiên), `erp_link`, `revoked_at`. EP-04 (A-37): thêm cột binding kênh + UNIQUE(channel, provider_user_hash) bằng migration mới, để mỗi binding có một ref ổn định |
| conversation | conversation | I | EP-04 (Flyway V9): chủ sở hữu principal, `customer_digest` (A-35), `status` ACTIVE_BOT/HANDOFF_PENDING, `row_version` |
| message | conversation | I/T (đã sanitize) | UNIQUE(conversation_id, client_event_id); direction CUSTOMER/BOT/STAFF |
| quote / quote_line | quote | R | EP-06 (Flyway V6): `quote.version` + `row_version`; một ISSUED mỗi conversation (unique index một phần); UNIQUE(quote_id, line_no), UNIQUE(quote_id, sku); CHECK qty 1..9999; NUMERIC(19,4), VND scale 0 (ADR-014) |
| quote_request | quote | R | EP-06: outcome, mã lý do (chỉ nhân viên), `customer_digest` (khóa/giới hạn A-34 theo khách) |
| quote_handle | quote | I | PK hash; binding; expires_at; revoked |
| quote_confirmation | quote | R | EP-07 (Flyway V10): mỗi lần thử xác nhận một dòng (kết quả, mã lý do nội bộ khi bị chặn, `customer_digest`); dùng cho giới hạn 20 lần/giờ |
| draft_order_request | order | I | EP-07 (Flyway V10): UNIQUE(quote_ref, quote_version); `idempotency_key` = `<mã báo giá>:<version>` do server sinh; `payload_digest` (SHA-256 không khóa của mã khách ERP + các dòng, A-41(x)); `key_forwarded`; `erp_order_ref`; `late_erp_order_ref` (ERP trả lời sau khi đã quá hạn); không có mã khách hay giá ở dạng đọc được |
| draft_order_event | order | I | EP-07: mọi thay đổi trạng thái + `actor` (pseudonym của nhân viên đối soát) |
| handoff | handoff | I | reason_code NOT NULL |
| audit_event, egress_record | audit | metadata | không cột body/giá trị |
| feature_switch | platform | — | audit thay đổi |
| mock_erp.* | erp | R (synthetic, sentinel) | schema tách; chỉ erp truy cập — **chưa tạo**: Mock giữ dữ liệu trong bộ nhớ (EP-07, A-41(i)) |
