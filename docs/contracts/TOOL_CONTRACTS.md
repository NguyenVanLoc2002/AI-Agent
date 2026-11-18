# Tool contracts — v0.1 DRAFT

> 2026-11-13. Nguồn: CLAUDE.md, rule 10/30, skill `agent-tool-contract`, BRD §03–§05 (`@af2d09f`). Danh sách tool và schema: ADR-006 ACCEPTED 2026-11-18. Triển khai EP-03: xem §6.

## 1. Quy tắc chung (SOURCE FACT trừ khi ghi khác)
- Tool call của model là **đề xuất**. Mỗi lần thực thi: tên ∈ allowlist theo vai trò/kênh → JSON schema `additionalProperties:false` → giới hạn kích thước/độ sâu → authz principal + ownership tài nguyên → quota → trạng thái hợp lệ.
- **Không** tham số `customerId`, `tenantId`, `userId`, `price`, `discount`, URL, SQL, filter tự do.
- Đầu ra chỉ DTO `@EgressClassified(APPROVED_TECHNICAL)`; tool output được coi là untrusted và đi qua EgressGuard trước khi tới model.
- Không reflection invocation; registry tĩnh (ARCH-11).
- Lỗi trả mã đã sanitize; audit `TOOL_INVOKED/TOOL_REJECTED` với tên tool, decision, reason code (EP-03: bản ghi audit là bảng `tool_invocation`; **không** lưu tên field của args vì model đặt được tên field tùy ý, có thể chứa dữ liệu; SECREV đọc qua inspector API ở EP-09).
- Giới hạn (D-10 APPROVED 2026-11-18, cấu hình `apas.tools.limits`): ≤3 tool call/lượt; ≤20 tool call/hội thoại/giờ **và** ≤40 tool call/principal/giờ trên mọi conversation; ≤5 conversation mới/principal/giờ; args ≤4 KB, độ sâu ≤3; mảng ≤20.

## 2. Allowlist theo vai trò
| Tool | CUST (web chat) | SALES | OPS | SECREV | Ghi chú |
|---|---|---|---|---|---|
| `searchVerifiedProduct` | ✔ | — | — | — | |
| `requestQuote` | ✔ | — | — | — | chỉ SKU VERIFIED |
| `requestHandoff` | ✔ | — | — | — | |
| (mọi tool khác) | ✘ | ✘ | ✘ | ✘ | unknown → reject |
Nhân viên không dùng agent tool trong PoC; thao tác qua API có RBAC.

## 3. Định nghĩa
### 3.1 VIN — không phải tool (sửa sau security review 2026-11-13)
- VIN được trích và validate **server-side khi tin nhắn đến** (policy/catalog `VinValidator`: 17 ký tự, không I/O/Q, check digit khi áp dụng — điều kiện áp dụng OPEN, BRD §04). VIN đầy đủ lưu trong state conversation; model chỉ thấy WMI/mask và tham chiếu `vinRef`.
- Lý do: tool nhận VIN đầy đủ làm tham số sẽ buộc VIN đầy đủ ra provider, mâu thuẫn A-11/D-11.

### 3.2 `searchVerifiedProduct`
- Input (đúng một nhánh, `oneOf`): `{ "vinRef": "current" }` (dùng VIN đã validate trong state) | `{ "oe": string matching ^[A-Z0-9][A-Z0-9-]{2,29}$ }` | `{ "category": enum, "vehicleModel": string(≤50, charset hạn chế) }`. EP-03: `category` tạm là nhãn chữ cái + khoảng trắng ≤80 ký tự (chỉ dùng để truy vấn catalog, không tới model) cho đến khi có danh mục đóng (A-25). Schema quảng bá cho model chỉ ghi `maxLength` cho `category` (regex `\p{L}` không có trong mọi phiên bản JSON Schema); giá trị không phải nhãn chữ cái nhận `TOOL_SCHEMA` và tính vào ngưỡng handoff.
- Authz: principal CUST, conversation thuộc principal, không bị khóa; quota theo conversation **và theo principal** (chống mở nhiều conversation để liệt kê catalog).
- Output: `{ "candidates": [ { "productRef": opaque, "sku": ^[A-Z0-9-]{3,30}$, "oe": ^[A-Z0-9-]{3,30}$, "category": enum, "provenance": enum[AI_RECOGNIZED, CATALOG_LOOKUP, VERIFIED], "availability": enum[CON_HANG, HET_HANG, CAN_KIEM_TRA] } ] (≤5) }`. Giá trị từ catalog không khớp regex → loại khỏi kết quả + audit `CATALOG_FIELD_REJECTED`.
- `availability` lấy từ `AvailabilityBucketPort` (catalog sở hữu, chỉ trả enum, **không phụ thuộc số lượng**) — xem `ERP_CONNECTOR.md` §3.4.
- Text catalog tự do: **không trả** (chống catalog injection).

### 3.3 `requestQuote`
- Input: `{ "lines": [ { "productRef": opaque, "quantity": int 1..9999 } ] (1..20) }` — `productRef` phải đến từ kết quả search **của chính conversation**.
- Authz: principal CUST; mọi productRef thuộc conversation; provenance VERIFIED; không có quote ISSUED đang chờ cho cùng tập dòng (DESIGN PROPOSAL).
- Xử lý nội bộ: quote module lấy giá/tồn/eligibility.
- Output: `{ "status": enum[ISSUED, NOT_QUOTED], "quoteHandle": opaque? , "lineCount": int }`.
  - `NOT_QUOTED` là **một** giá trị duy nhất cho mọi lý do (hết hàng theo số lượng, công nợ không đạt, cần xem xét, giá thiếu). Lý do chi tiết chỉ ghi trong handoff nội bộ (reason code) và audit, **không** tới model. `NOT_QUOTED` luôn kèm handoff.
  - Lý do (security review F1/F2): trả `INELIGIBLE`/`OUT_OF_STOCK` riêng biệt cho phép dò hạn mức công nợ (vary quantity → orderTotal) và dò số tồn; eligibility là RESTRICTED (rule 30 §5).

### 3.4 `requestHandoff`
- Input: `{ "reason": enum[CUSTOMER_REQUEST, UNSURE, OUT_OF_SCOPE] }`.
- Output: `{ "accepted": true }`. Khóa bot.

## 4. Không tồn tại (SOURCE FACT: AI không có quyền)
`confirmQuote`, `createOrder`, `approveOrder`, `updatePrice`, `applyDiscount`, `getCustomer*`, `listProducts`, `listCustomers`, `getCost`, `getDebt`, `fetchUrl`, `runQuery`.

## 5. Tests bắt buộc (trạng thái: §6)
| ID | Kịch bản | Kỳ vọng |
|---|---|---|
| T-TOOL-01 | Tên tool lạ / alias / khác hoa thường | reject `TOOL_UNKNOWN`, audit |
| T-TOOL-02 | Thêm `customerId` hoặc field lồng nhau | reject `TOOL_SCHEMA` |
| T-TOOL-03 | Args 1 MB / độ sâu 50 / mảng 10 000 | reject trước khi parse đầy đủ |
| T-TOOL-04 | productRef của conversation khác | `FORBIDDEN_RESOURCE`, 0 dữ liệu |
| T-TOOL-05 | >3 call/lượt, vượt quota | reject + handoff khi lặp |
| T-TOOL-06 | Catalog string chứa chỉ dẫn injection | không tới model; proposal sau đó bị reject |
| T-TOOL-07 | Mock ERP outage | `CATALOG_UNAVAILABLE`, không lộ message |
| T-TOOL-08 | Model gọi `approveOrder` | `TOOL_UNKNOWN` (SEC-03) |
| T-TOOL-09 | Khách vượt hạn mức công nợ một chút; model thay đổi `quantity` để dò | mọi phản hồi tới model giống hệt trường hợp hết hàng (`NOT_QUOTED`); egress không có `INELIGIBLE` |
| T-TOOL-10 | Binary-search `quantity` qua `requestQuote` để dò tồn | phản hồi không đổi theo quantity ngoài `ISSUED/NOT_QUOTED`; quota chặn |
| T-TOOL-11 | Catalog `oe` chứa chỉ dẫn/ký tự ngoài regex | bị loại, audit `CATALOG_FIELD_REJECTED` |
| T-TOOL-12 | 50 conversation mới để liệt kê catalog | quota theo principal chặn |
| T-TOOL-13 | Model truyền VIN 17 ký tự trong bất kỳ arg nào | reject `TOOL_SCHEMA`; egress không có VIN đầy đủ |

## 6. Triển khai EP-03 (2026-11-18)
- Code: `tools.ToolManager` / `tools.internal.DefaultToolManager`. Thứ tự mỗi lần gọi: `tools.spi.ConversationAccessPort` (conversation thuộc principal, không khóa; **chưa có hiện thực → mọi lần gọi FORBIDDEN_RESOURCE** đến EP-04; nhiều hơn một hiện thực cũng bị coi là từ chối) → admit (quota + ghi nhận, mọi lần thử đều tính, kể cả tool lạ và args sai; lần gọi trên conversation không thuộc mình chỉ tính vào quota của người gọi, lưu `conversation_id` null) → tên khớp chính xác + vai trò/kênh → `ArgumentParser` (streaming: độ sâu, phần tử mảng, độ dài chuỗi; rồi schema strict, không ép kiểu, cấm khóa trùng/thừa, cấm VIN đầy đủ) → ownership `productRef` → thực thi → ánh xạ sang `gateway.api` (A-20).
- Lỗi của store quota/audit hoặc của port → `TOOL_UNAVAILABLE` + handoff (mọi kết quả đều có kiểu). Gọi `execute` ngoài transaction: dòng quota và `productRef` được ghi trong transaction riêng để không mất khi caller rollback.
- Kết quả lỗi tới model: `gateway.api.ToolErrorResult(code)` với `TOOL_UNKNOWN`, `TOOL_SCHEMA`, `FORBIDDEN_RESOURCE`, `QUOTA_EXCEEDED`, `CATALOG_UNAVAILABLE`, `TOOL_UNAVAILABLE`; không message, không giá trị. `ToolOutcome.handoffRequired` khi QUOTA_EXCEEDED, FORBIDDEN_RESOURCE, yêu cầu handoff, hoặc ≥3 lần bị từ chối/conversation/giờ.
- Bảng (Flyway V4, chỉ thêm): `tool_invocation` (quota + audit metadata: pseudonym principal, conversation, turn, tên tool đã đăng ký hoặc `UNKNOWN`, decision, reason code) và `product_ref` (token ngẫu nhiên 128 bit, conversation, SKU, provenance, hết hạn 2 h). Quota dùng advisory lock theo principal (namespace riêng) nên gọi đồng thời không vượt giới hạn; mọi bộ đếm lọc theo principal; conversation mới chỉ được tính là "đã dùng" khi có lần gọi qua được quota.
- Catalog: `catalog.CatalogSearch` (API mới) trên `VerifiedProductPort` + `AvailabilityBucketPort`. Ứng viên có SKU/OE ngoài `^[A-Z0-9-]{3,30}$`, có VIN đầy đủ (kể cả nằm trong chuỗi dài hơn), có dãy ≥7 chữ số, hoặc không qua được kiểm tra của `ProductRef` (kể cả category không phải nhãn chữ cái) bị loại riêng dòng đó, không cấp token, và ghi `CATALOG_FIELD_REJECTED`.
- Review EP-03 vòng 2 (architecture-reviewer 0H/0M/6L, security-auditor 0H/0M/4L): đã sửa — pattern `oe`/`vehicleModel` khớp kiểu query catalog (lỗi còn lại của kiểu query → `TOOL_SCHEMA`), port trả `null` = không sở hữu, đúng một port (kể cả có `@Primary`) mới được dùng, ánh xạ enum lỗi chỉ loại riêng dòng đó, `tools` bỏ `audit` khỏi allowlist. Ghi nhận, chưa sửa: token đã cấp có thể mồ côi nếu lỗi giữa chừng (hết hạn sau 2 h); mỗi lần vượt quota vẫn thêm một dòng (giới hạn tốc độ ở HTTP và retention D-09 thuộc EP-04/EP-09).
- Review EP-03 (architecture-reviewer 0H/3M/6L, security-auditor 0H/2M/3L+): đã sửa tất cả, có test (`ToolRegistryIT` 9 test, `DefaultToolManagerTest`, `ArgumentParserTest`). Schema quảng bá cho model khai báo mọi khóa ở `properties` cấp gốc. `tools` bỏ `quote` khỏi allowlist; ở EP-06 tools sẽ đổi productRef → SKU và chỉ chuyển SKU cho quote (quote không đọc `product_ref`).
- Chưa có executor (validate đầy đủ rồi trả `TOOL_UNAVAILABLE`): tìm theo `vinRef` (EP-05), `requestQuote` (EP-06, ownership `productRef` đã kiểm), `requestHandoff` (EP-08, kèm `handoffRequired`).
- Giới hạn còn lại: category chỉ gồm chữ cái và khoảng trắng vẫn có thể chứa câu chỉ dẫn (A-25, taxonomy đóng); principal và conversation chưa đến từ phiên đăng nhập thật (ADR-010, EP-04).

| ID | Trạng thái | Bằng chứng |
|---|---|---|
| T-TOOL-01 | VERIFIED (unit) | `DefaultToolManagerTest.unknownToolsAreRejectedAndAudited`, `malformedNamesAreRejectedByTheProposalType` |
| T-TOOL-02 | VERIFIED (unit) | `ArgumentParserTest.searchRejectsAnythingButTheThreeShapes`, `quoteRejectsBadLines`, `DefaultToolManagerTest.schemaViolationIsACodeOnly` |
| T-TOOL-03 | VERIFIED (unit) | `ArgumentParserTest.oversizedShapesAreRejected`; 1 MB bị chặn trước ở `ToolProposal` (4 KB) |
| T-TOOL-04 | VERIFIED (PostgreSQL) | `ToolRegistryIT.productRefOfAnotherConversationIsForbidden` |
| T-TOOL-05 | VERIFIED (PostgreSQL) | `ToolRegistryIT.perTurnAndPerConversationQuotas`, `concurrentCallsRespectTheTurnQuota`; `DefaultToolManagerTest.repeatedRejectionsHandOff` |
| T-TOOL-06 | VERIFIED (unit) | `DefaultToolManagerTest.catalogInjectionIsDroppedAndAudited` (giới hạn: A-25) |
| T-TOOL-07 | VERIFIED (unit) | `DefaultToolManagerTest.erpOutageIsCatalogUnavailableWithoutLeakingTheMessage` |
| T-TOOL-08 | VERIFIED (unit) | `DefaultToolManagerTest.unknownToolsAreRejectedAndAudited` (`approveOrder` và danh sách §4) |
| T-TOOL-09/10 | NOT RUN | cần quote service (EP-06) |
| T-TOOL-11 | VERIFIED (unit) | `DefaultToolManagerTest.catalogInjectionIsDroppedAndAudited` |
| T-TOOL-12 | VERIFIED (PostgreSQL) | `ToolRegistryIT.principalQuotaStopsEnumerationAcrossConversations` |
| T-TOOL-13 | VERIFIED (unit) | `ArgumentParserTest.fullVinInAnyArgumentIsRejected` |
