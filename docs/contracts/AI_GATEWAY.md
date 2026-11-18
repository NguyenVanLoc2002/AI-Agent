# AI Gateway contract — v0.1 DRAFT

> 2026-11-13. Nguồn: rule `.claude/rules/30-llm-gateway.md` (SOURCE FACT cho repo), BRD §03, §08 (`@af2d09f`). Thiết kế chi tiết: DESIGN PROPOSAL (ADR-003, ADR-004, ADR-011, ADR-013, ADR-016). Chưa VERIFIED.
> Provider cụ thể, region, ZDR/DPA, retention: **OPEN D-07**. PoC dùng Fake provider + WireMock (A-05).

## 1. Interface
```
ModelProviderPort.complete(ModelRequest) -> ModelResponse   // chỉ gateway.adapter hiện thực
```
| Kiểu | Trường cho phép (allowlist) | Nhãn |
|---|---|---|
| `ModelRequest` | `traceId` (pseudonymous), `systemPromptId` (tham chiếu template, không nội dung động), `messages: List<SafeMessage>`, `context: TechnicalContext`, `toolResults: List<ToolResultDto>`, `tools: List<ToolSpec>`, `limits` (max tokens, timeout) | DESIGN PROPOSAL |
| `SafeMessage` | `role` (CUSTOMER / CONTEXT_SUMMARY), `text` (đã sanitize, ≤ N ký tự), `classification=APPROVED_TECHNICAL`. **Không** có role cho nhân viên hay kết quả tool dạng text tự do | DESIGN PROPOSAL (sửa sau review F3/F4) |
| `ToolResultDto` | union typed: `ProductSearchResult(candidates[])`, `QuoteRequestResult(status, quoteHandle?, lineCount)`, `HandoffResult(accepted)` — chỉ field enum/mã bounded | DESIGN PROPOSAL |
| `TechnicalContext` | `maskedVin` (WMI-only, A-11), `vinRef`, `vehicleModel`, `partCategory`, `requestedQuantity`, `verifiedProducts: List<ProductRef(productRef, sku, oe, category, provenance, availabilityBucket)>`, `quoteHandle?` | DESIGN PROPOSAL; trường SOURCE FACT BRD §08 ALLOW |
| `ToolSpec` | `name`, `jsonSchema` (từ registry, không do model định nghĩa) | DESIGN PROPOSAL |
| `ModelResponse` | `text?` hoặc `toolProposals[]` (name, argumentsJson ≤ 4 KB); untrusted | DESIGN PROPOSAL |

**Cấm tuyệt đối trong mọi kiểu trên (SOURCE FACT BRD §08 DENY):** purchaseCost, margin, customerPrice, creditBalance, creditLimit, customerDirectory, purchaseHistory, customer identity, secrets/tokens. **Bổ sung (DESIGN PROPOSAL):** số tồn, quoteId/version thật, idempotency key, ảnh (PoC), `Map`/`Object`/`JsonNode`, JPA entity (ARCH-05..07).

## 2. Pipeline egress (DESIGN PROPOSAL chi tiết hóa nguyên tắc rule 30 §3; thứ tự bước là đề xuất)
1. **Type gate:** chỉ nhận `ModelRequest`; build fail nếu DTO thiếu `@EgressClassified` (ARCH-06).
2. **Serialize allowlist:** ObjectMapper riêng của gateway: FAIL_ON_UNKNOWN_PROPERTIES, không default typing, không `@JsonAnyGetter`.
3. **Final-bytes scan (policy):** trên đúng byte sẽ gửi:
   - Chuẩn hóa: NFKC, bỏ zero-width/NBSP, chữ số Unicode → ASCII, gộp dấu phân cách số (`. , space _`), bỏ ký hiệu tiền (₫, VND, đ).
   - Giải mã một tầng các đoạn nghi vấn: base64, hex, percent, `\uXXXX`, HTML entity.
   - Đối chiếu: honeytoken registry; pattern tiền tệ gần ngữ cảnh giá; SĐT/email; số dạng mũ (`9.87654321e8`); chữ số đọc bằng tiếng Việt (danh sách giới hạn cho sentinel).
   - Quét cả payload ghép và cửa sổ trượt theo conversation (chống tách qua nhiều field/tin).
4. **Quyết định:** PASS → gửi; HIT hoặc không phân loại được → **không gửi**, `SecurityEvent(policyCode, fieldPath, length)`, handoff `DLP_EGRESS`.
5. **Publish `EgressDecision` → audit lưu `egress_record`:** traceId, field names, decision, policy code, honeytoken ID (nếu hit), byte length. Không ghi body, **không HMAC theo từng giá trị** (tránh SECREV so sánh được giá trị giữa các khách — review F9).

Lưu ý (security-auditor): honeytoken scan chỉ chứng minh *vắng sentinel*; giá thật không phải sentinel chỉ bị chặn nhờ type gate (bước 1–2). Do đó type gate là boundary chính.

## 3. History
- **SOURCE FACT rule 30 §2:** coi cả lượt user và assistant trước là nơi có thể chứa giá/PII; dùng summary policy-safe.
- **DESIGN PROPOSAL (ADR-011):** summary sinh **deterministic từ structured state** (sản phẩm đã xác minh, trạng thái quote bằng handle, câu hỏi mở, các lượt khách đã sanitize gần nhất) — không gọi LLM để tóm tắt trong PoC. Nếu sau này dùng LLM tóm tắt, lời gọi đó là egress và đi qua cùng pipeline.
- **Không bao giờ vào history:** quote card đã render; câu trả lời assistant nguyên văn; **tin nhắn của nhân viên** (giai đoạn HUMAN_ACTIVE) — vì nhân viên có thể gõ giá (review F3).

## 4. Output handling
- Parse strict; text > giới hạn → cắt/từ chối.
- `OutputValidator` (agent): từ chối số tiền, số tồn, OE không có trong tool result của lượt này, URL, handle không thuộc conversation, chỉ dẫn "đã duyệt đơn".
- Tool proposal → `docs/contracts/TOOL_CONTRACTS.md`.

## 5. Độ tin cậy & kill switch
| Tình huống | Hành vi | Nguồn |
|---|---|---|
| Kill switch OFF | Kiểm tra trước **mỗi** provider call; hủy stream đang chạy; handoff `AI_DISABLED` (chuyển nhân viên); hiệu lực ≤60s; không đọc được trạng thái switch → coi như OFF | Yêu cầu ≤60s: SOURCE FACT SEC-06; cơ chế: DESIGN PROPOSAL ADR-013 |
| 401/403 (key revoked) | Mở breaker, handoff `PROVIDER_AUTH`, thông báo an toàn, không ghi quote/order dở | Yêu cầu: SOURCE FACT SEC-07; cơ chế: DESIGN PROPOSAL |
| Timeout/5xx | Retry có giới hạn chỉ khi request không đổi và đã PASS scan; vượt → handoff | Nguyên tắc "bounded retries on read-only": SOURCE FACT rule 30 §7; chi tiết: DESIGN PROPOSAL |
| Đổi provider/region | Không tự động; cần quyết định | SOURCE FACT rule 30 §7 |

## 6. Capture cho kiểm thử (ADR-016)
- (a) Interceptor ở HTTP client của adapter, đọc body sau nén/retry.
- (b) WireMock giả provider đứng sau proxy local — route duy nhất.
- Test đối chiếu số request (a) = (b) và quét cả hai bằng cùng normalizer.

## 6a. Ghi chú triển khai SEC-01a (2026-11-18, DESIGN PROPOSAL trừ khi ghi khác)
- `limits` được thay bằng enum `ResponseBudget` (SHORT/STANDARD); adapter map sang timeout từ cấu hình. Lý do: giữ số ngoài kiểu egress (ARCH-05).
- Sau serialize, bytes được parse lại: mọi property name phải thuộc allowlist suy ra từ record `gateway.api` (+ `type`); khác → BLOCK `UNCLASSIFIED_FIELD`.
- Lớp giải mã chỉ quét biến thể giải mã là text hợp lệ (UTF-8, không ký tự điều khiển); lý do: UUID/handle giải mã ra rác gây false positive (ghi nhận trong lần chạy GW200 đầu).
- **Chưa làm:** cửa sổ trượt theo conversation giữa các request (cần conversation key, EP-04/SEC-01b). Trong một request, giá trị bị tách qua nhiều field/tin vẫn bị bắt (chiếu chữ số của mọi string).
- Retry: tối đa 1 lần cho timeout/I-O/5xx, gửi lại đúng mảng byte đã scan. 401/403 không retry. Kill switch, breaker, quota: chưa làm (EP-09, EP-03).
- Audit đồng bộ: nếu ghi `egress_record` lỗi thì không gửi (`AUDIT_UNAVAILABLE`).
- Wire format Fake provider là JSON riêng của gateway tới `/v1/fake-provider/complete` trên loopback; không phải API của vendor nào. URL/proxy không phải loopback → ứng dụng không khởi động (A-05).
- Tên interface trong code (khác §1, review M3): API công khai `gateway.AiGateway.complete(ModelRequest) -> GatewayResult` (Completed / Refused(reason) / Failed(reason)); `ModelProviderPort.send(byte[], ResponseBudget)` là port **nội bộ** (`gateway.internal`) do `gateway.adapter` hiện thực; chưa có `EgressTransport`.
- Sau review SEC-01a (2026-11-18): adapter luôn đặt proxy tường minh (`NO_PROXY` hoặc proxy loopback đã cấu hình) nên `-Dhttp.proxyHost`/system proxy không thể định tuyến egress; một deadline chung cho header + body, body bị cắt ở 32 KB khi đang nhận; interrupt → `CANCELLED`, không retry; `RuntimeException` từ transport → `Failed(PROVIDER_UNAVAILABLE)`.
- Định danh do server sinh (`traceId`, `systemPromptId`, `productRef`, `vinRef`, `quoteHandle`, `maskedVin`) chỉ qua kiểm tra honeytoken, không qua pattern PHONE/MONEY/VIN và không vào phép chiếu chữ số ghép (false positive với chuỗi ngẫu nhiên). `sku`/`oe` vẫn quét đủ (nội dung từ ERP; xem A-24).
- `ProductRef.category` và `TechnicalContext.partCategory` chỉ nhận chữ cái và khoảng trắng (review M2: text tự do do ERP kiểm soát trong tool result). Taxonomy đóng: A-25.
- Bất biến audit (review H2): transaction riêng `REQUIRES_NEW`, lỗi được ném lại. `GatewayEgressIT` chứng minh bằng PostgreSQL thật (trigger từ chối insert → 0 request; rollback của caller không xóa bản ghi).
- Sau review vòng 2 (2026-11-18): gateway gọi trực tiếp port `audit.EgressAuditLog` (không còn event/listener; thiếu bean → ứng dụng không khởi động). Mỗi lần gửi, kể cả retry, có một dòng PASS riêng (`attempt` 1/2, Flyway V3); BLOCK có `attempt = 0`. Số dòng PASS = số request provider nhận (ADR-016 a=b). Gọi gateway ngoài transaction: audit cần thêm một connection (L6).
- Phát hiện (review vòng 2): byte rác/không giải mã được trong base64/hex được thay bằng khoảng trắng nếu ≤25% (trước đây cả đoạn bị bỏ qua); base64 thử 4 độ lệch (ký tự dính phía trước); PHONE cho phép tối đa 3 ký tự phân cách `space . - / ( )`; VIN viết hoa tách nhóm bằng space/dot/dash được phát hiện và inbound che thành WMI + 14 `*`.
- Ref opaque (`productRef`, `vinRef`, `quoteHandle`): tiền tố + đúng 22 ký tự base64url (128 bit, ADR-008), không có dãy ≥7 chữ số; `systemPromptId` cũng không có dãy ≥7 chữ số. ARCH-05d chỉ cho tên field miễn quét trên các cặp `Type.field` đã duyệt. Nguồn gốc token: A-26.
- Endpoint/proxy chỉ nhận địa chỉ loopback dạng literal (`127.0.0.1`, `::1`); `localhost` bị từ chối vì được phân giải lúc chạy (L2).
- Giới hạn còn lại (lớp thứ hai, ghi nhận, chưa sửa): hex có khoảng trắng, base64 bị tách, homoglyph trong honeytoken TEXT, chưa có pattern secret tổng quát; allowlist tên property là hợp phẳng, không theo từng kiểu; VIN tách nhóm viết thường; số điện thoại có >3 ký tự phân cách giữa hai chữ số; nguồn gốc ref opaque (A-26).

## 7. Tests (NOT RUN)
T-SEC01-SCHEMA, T-SEC01-VARIANTS (≥20 biến thể × 5 nguồn), T-SEC01-HISTORY, T-SEC01-STAFF, T-SEC01-ERR, T-SEC01-CAPTURE, T-SEC01-SUITE200, T-SEC06, T-SEC07, T-GW-OUTPUT (OutputValidator). Định nghĩa: `docs/backlog/IMPLEMENTATION_PLAN.md` §4.
