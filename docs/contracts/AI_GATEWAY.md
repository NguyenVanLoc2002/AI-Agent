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

## 7. Tests (NOT RUN)
T-SEC01-SCHEMA, T-SEC01-VARIANTS (≥20 biến thể × 5 nguồn), T-SEC01-HISTORY, T-SEC01-STAFF, T-SEC01-ERR, T-SEC01-CAPTURE, T-SEC01-SUITE200, T-SEC06, T-SEC07, T-GW-OUTPUT (OutputValidator). Định nghĩa: `docs/backlog/IMPLEMENTATION_PLAN.md` §4.
