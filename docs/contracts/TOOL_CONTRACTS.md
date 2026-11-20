# Tool contracts — v0.1 DRAFT

> 2026-11-13. Nguồn: CLAUDE.md, rule 10/30, skill `agent-tool-contract`, BRD §03–§05 (`@af2d09f`). Danh sách tool và schema: ADR-006 ACCEPTED 2026-11-18. Triển khai EP-03: xem §6.

## 1. Quy tắc chung (SOURCE FACT trừ khi ghi khác)
- Tool call của model là **đề xuất**. Mỗi lần thực thi: tên ∈ allowlist theo vai trò/kênh → JSON schema `additionalProperties:false` → giới hạn kích thước/độ sâu → authz principal + ownership tài nguyên → quota → trạng thái hợp lệ.
- **Không** tham số `customerId`, `tenantId`, `userId`, `price`, `discount`, URL, SQL, filter tự do.
- Đầu ra chỉ DTO `@EgressClassified(APPROVED_TECHNICAL)`; tool output được coi là untrusted và đi qua EgressGuard trước khi tới model.
- Không reflection invocation; registry tĩnh (ARCH-11).
- Lỗi trả mã đã sanitize; audit `TOOL_INVOKED/TOOL_REJECTED` với tên tool, decision, reason code (EP-03: bản ghi audit là bảng `tool_invocation`; **không** lưu tên field của args vì model đặt được tên field tùy ý, có thể chứa dữ liệu; SECREV đọc qua inspector API: EP-09b, `tools :: history`, không gồm principal/conversation; cột `request_id` (V15) để ghép theo request).
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
- VIN được trích và validate **server-side khi tin nhắn đến** (`catalog.VehicleContext`, `catalog.spi.Vin`: 17 ký tự, không I/O/Q; check digit bắt buộc khi ký tự đầu là 1–5 (Bắc Mỹ), vùng khác chỉ kiểm định dạng — A-27 APPROVED 2026-11-18). VIN đầy đủ lưu ở bảng `conversation_vin` (một VIN hiện tại/conversation, TTL 2 h, A-28); model chỉ thấy WMI/mask và gọi tool với hằng `{"vinRef":"current"}` (không có token VIN, A-26).
- Lý do: tool nhận VIN đầy đủ làm tham số sẽ buộc VIN đầy đủ ra provider, mâu thuẫn A-11/D-11.

### 3.2 `searchVerifiedProduct`
- Input (đúng một nhánh, `oneOf`): `{ "vinRef": "current" }` (dùng VIN đã validate trong state) | `{ "oe": string matching ^[A-Z0-9][A-Z0-9-]{2,29}$ }` | `{ "category": enum, "vehicleModel": string(≤50, charset hạn chế) }`. EP-03: `category` tạm là nhãn chữ cái + khoảng trắng ≤80 ký tự (chỉ dùng để truy vấn catalog, không tới model) cho đến khi có danh mục đóng (A-25). Schema quảng bá cho model chỉ ghi `maxLength` cho `category` (regex `\p{L}` không có trong mọi phiên bản JSON Schema); giá trị không phải nhãn chữ cái nhận `TOOL_SCHEMA` và tính vào ngưỡng handoff.
- Authz: principal CUST, conversation thuộc principal, không bị khóa; quota theo conversation **và theo principal** (chống mở nhiều conversation để liệt kê catalog).
- Output: `{ "candidates": [ { "productRef": opaque, "sku": ^[A-Z0-9-]{3,30}$, "oe": ^[A-Z0-9-]{3,30}$, "category": enum, "provenance": enum[AI_RECOGNIZED, CATALOG_LOOKUP, VERIFIED], "availability": enum[CON_HANG, HET_HANG, CAN_KIEM_TRA] } ] (≤5) }`. Giá trị từ catalog không khớp regex → loại khỏi kết quả + audit `CATALOG_FIELD_REJECTED`.
- `availability` lấy từ `AvailabilityBucketPort` (catalog sở hữu, chỉ trả enum, **không phụ thuộc số lượng**) — xem `ERP_CONNECTOR.md` §3.4.
- Text catalog tự do: **không trả** (chống catalog injection).

### 3.3 `requestQuote`
- Input: `{ "lines": [ { "productRef": opaque, "quantity": int 1..9999 } ] (1..20) }` — `productRef` phải đến từ kết quả search **của chính conversation**.
- Authz: principal CUST; mọi productRef thuộc conversation; provenance VERIFIED (chỉ kết quả tìm OE chính xác, A-31); khách của phiên phải là khách đã gắn với conversation (A-35, lệch → `FORBIDDEN_RESOURCE` + handoff); cùng SKU hai lần → `TOOL_SCHEMA`. Mỗi conversation chỉ có một báo giá ISSUED: báo giá mới thay báo giá cũ (ADR-007), thay cho đề xuất cũ "không có quote ISSUED đang chờ cho cùng tập dòng".
- Xử lý nội bộ: tools đổi productRef → SKU và gọi `quote.QuoteService` với conversation, khách của phiên và các dòng SKU; quote lấy liên kết ERP từ identity (A-19) rồi lấy giá/tồn/eligibility. Khách chưa liên kết → `NOT_QUOTED`. Sau một `NOT_QUOTED` conversation không được báo giá tự động trong 24 h; tối đa 5 báo giá ISSUED/conversation/giờ (A-34, kiểm tra trước mọi lời gọi ERP).
- Output: `{ "status": enum[ISSUED, NOT_QUOTED], "quoteHandle": opaque? , "lineCount": int }`.
  - `NOT_QUOTED` là **một** giá trị duy nhất cho mọi lý do (hết hàng theo số lượng, công nợ không đạt, cần xem xét, giá thiếu, ERP hoặc DB lỗi, đã chuyển nhân viên, vượt giới hạn, thua cuộc đua đồng thời). Một `NOT_QUOTED` thu hồi báo giá ISSUED cũ hơn của conversation (A-33). Lý do chi tiết chỉ ghi trong handoff nội bộ (reason code) và audit, **không** tới model. `NOT_QUOTED` luôn kèm handoff.
  - Lý do (security review F1/F2): trả `INELIGIBLE`/`OUT_OF_STOCK` riêng biệt cho phép dò hạn mức công nợ (vary quantity → orderTotal) và dò số tồn; eligibility là RESTRICTED (rule 30 §5).

### 3.4 `requestHandoff`
- Input: `{ "reason": enum[CUSTOMER_REQUEST, UNSURE, OUT_OF_SCOPE] }`.
- Output: `{ "accepted": true }`. Khóa bot.
- **EP-08b (2026-11-20, A-43 mục 6):** đã có executor. Lý do model đưa ra thành reason của handoff (`CUSTOMER_REQUEST` / `UNSURE` / `OUT_OF_SCOPE`), chỉ đi qua `ToolOutcome.requestedHandoff` (không nằm trong kết quả model thấy); lượt kết thúc ngay, không gọi model lần hai, nên `{accepted:true}` không được gửi. Lý do ngoài enum → `TOOL_SCHEMA`.

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
| T-TOOL-07 | Mock ERP outage | `CATALOG_UNAVAILABLE` + `handoffRequired` (A-40(b)), không lộ message |
| T-TOOL-08 | Model gọi `approveOrder` | `TOOL_UNKNOWN` (SEC-03) |
| T-TOOL-09 | Khách vượt hạn mức công nợ một chút; model thay đổi `quantity` để dò | mọi phản hồi tới model giống hệt trường hợp hết hàng (`NOT_QUOTED`); egress không có `INELIGIBLE` |
| T-TOOL-10 | Binary-search `quantity` qua `requestQuote` để dò tồn | phản hồi không đổi theo quantity ngoài `ISSUED/NOT_QUOTED`; quota chặn |
| T-TOOL-11 | Catalog `oe` chứa chỉ dẫn/ký tự ngoài regex | bị loại, audit `CATALOG_FIELD_REJECTED` |
| T-TOOL-12 | 50 conversation mới để liệt kê catalog | quota theo principal chặn |
| T-TOOL-13 | Model truyền VIN 17 ký tự trong bất kỳ arg nào | reject `TOOL_SCHEMA`; egress không có VIN đầy đủ |

## 6. Triển khai EP-03 (2026-11-18)
- Code: `tools.ToolManager` / `tools.internal.DefaultToolManager`. Thứ tự mỗi lần gọi: `tools.spi.ConversationAccessPort` (conversation thuộc principal, không khóa; **chưa có hiện thực → mọi lần gọi FORBIDDEN_RESOURCE** đến EP-04; nhiều hơn một hiện thực cũng bị coi là từ chối) → admit (quota + ghi nhận, mọi lần thử đều tính, kể cả tool lạ và args sai; lần gọi trên conversation không thuộc mình chỉ tính vào quota của người gọi, lưu `conversation_id` null) → tên khớp chính xác + vai trò/kênh → `ArgumentParser` (streaming: độ sâu, phần tử mảng, độ dài chuỗi; rồi schema strict, không ép kiểu, cấm khóa trùng/thừa, cấm VIN đầy đủ) → ownership `productRef` → thực thi → ánh xạ sang `gateway.api` (A-20).
- Lỗi của store quota/audit hoặc của port → `TOOL_UNAVAILABLE` + handoff (mọi kết quả đều có kiểu). Gọi `execute` ngoài transaction: dòng quota và `productRef` được ghi trong transaction riêng để không mất khi caller rollback.
- Kết quả lỗi tới model: `gateway.api.ToolErrorResult(code)` với `TOOL_UNKNOWN`, `TOOL_SCHEMA`, `FORBIDDEN_RESOURCE`, `QUOTA_EXCEEDED`, `CATALOG_UNAVAILABLE`, `TOOL_UNAVAILABLE`; không message, không giá trị. `ToolOutcome.handoffRequired` khi QUOTA_EXCEEDED, FORBIDDEN_RESOURCE, CATALOG_UNAVAILABLE (A-40(b), 2026-11-19: ERP lỗi/timeout → chuyển nhân viên ngay, model không được gọi lại), yêu cầu handoff, hoặc ≥3 lần bị từ chối/conversation/giờ.
- Bảng (Flyway V4, chỉ thêm): `tool_invocation` (quota + audit metadata: pseudonym principal, conversation, turn, tên tool đã đăng ký hoặc `UNKNOWN`, decision, reason code) và `product_ref` (token ngẫu nhiên 128 bit, conversation, SKU, provenance, hết hạn 2 h). Quota dùng advisory lock theo principal (namespace riêng) nên gọi đồng thời không vượt giới hạn; mọi bộ đếm lọc theo principal; conversation mới chỉ được tính là "đã dùng" khi có lần gọi qua được quota.
- Catalog: `catalog.CatalogSearch` (API mới) trên `VerifiedProductPort` + `AvailabilityBucketPort`. Ứng viên có SKU/OE ngoài `^[A-Z0-9-]{3,30}$`, có VIN đầy đủ (kể cả nằm trong chuỗi dài hơn), có dãy ≥7 chữ số, hoặc không qua được kiểm tra của `ProductRef` (kể cả category không phải nhãn chữ cái) bị loại riêng dòng đó, không cấp token, và ghi `CATALOG_FIELD_REJECTED`.
- Review EP-03 vòng 2 (architecture-reviewer 0H/0M/6L, security-auditor 0H/0M/4L): đã sửa — pattern `oe`/`vehicleModel` khớp kiểu query catalog (lỗi còn lại của kiểu query → `TOOL_SCHEMA`), port trả `null` = không sở hữu, đúng một port (kể cả có `@Primary`) mới được dùng, ánh xạ enum lỗi chỉ loại riêng dòng đó, `tools` bỏ `audit` khỏi allowlist. Ghi nhận, chưa sửa: token đã cấp có thể mồ côi nếu lỗi giữa chừng (hết hạn sau 2 h); mỗi lần vượt quota vẫn thêm một dòng (giới hạn tốc độ ở HTTP và retention D-09 thuộc EP-04/EP-09).
- Review EP-03 (architecture-reviewer 0H/3M/6L, security-auditor 0H/2M/3L+): đã sửa tất cả, có test (`ToolRegistryIT` 9 test, `DefaultToolManagerTest`, `ArgumentParserTest`). Schema quảng bá cho model khai báo mọi khóa ở `properties` cấp gốc. `tools` bỏ `quote` khỏi allowlist; ở EP-06 tools sẽ đổi productRef → SKU và chỉ chuyển SKU cho quote (quote không đọc `product_ref`).
- Chưa có executor (validate đầy đủ rồi trả `TOOL_UNAVAILABLE`): ~~tìm theo `vinRef` (EP-05)~~ (đã có, xem §7), `requestQuote` (EP-06, ownership `productRef` đã kiểm), ~~`requestHandoff` (EP-08, kèm `handoffRequired`)~~ (đã có, EP-08b, §3.4).
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

## 7. Triển khai EP-05 (2026-11-18)
- Quyết định owner 2026-11-18: A-27 (check digit chỉ bắt buộc với ký tự đầu 1–5), A-26 phần `vinRef` (không token, hằng `"current"`), A-28 (bảng `conversation_vin`, TTL 2 h, tối đa 24 h), A-30 (thiếu VIN → `TOOL_UNAVAILABLE`, không thêm mã), A-25 ngoài phạm vi.
- Luồng: tin nhắn đến → `catalog.VehicleContext.register` (người gọi: EP-04, A-29) → `VinIntake` `None`/`Accepted(maskedVin)`/`Invalid`/`Multiple`/`Unavailable`; `Accepted` lưu VIN, `Invalid`/`Multiple` xóa VIN hiện tại, lỗi store → `Unavailable` (thử xóa VIN cũ, log chỉ tên lớp exception), mỗi lần gọi xóa các dòng đã hết hạn. Tool `{"vinRef":"current"}` → `CatalogSearch.searchByConversationVin(conversation)` → `ExactProductQuery.ByVin` → adapter; VIN không đi qua `tools`.
- Kết quả theo VIN: provenance tối đa `CATALOG_LOOKUP` (catalog giới hạn, không phụ thuộc adapter), nên `requestQuote` trả `FORBIDDEN_RESOURCE`. Tìm lại bằng OE hoặc category + model vẫn ra `VERIFIED` cho cùng SKU: A-31 OPEN.
- Phát hiện VIN ở intake dùng cùng quy tắc nhóm ký tự với sanitizer (parity: `VinSearchIT.whenTheSanitizerMasksAVinIntakeSeesOne`). Chuỗi chữ + số 15–19 ký tự không có gạch được coi là VIN gõ sai (A-32).
- Review (advisory) EP-05: security-auditor 0H/2M/5L(+1 info), architecture-reviewer 0H/5M/7L. Đã sửa có test: parity intake/sanitizer (sec M-1, arch M3), giới hạn provenance trong catalog (arch M1), `search(ByVin)` bị từ chối (arch M2), `TechnicalContext.vinRef`/AI_GATEWAY ghi rõ (arch M4, chưa đổi code gateway), tài liệu (arch M5), `Unavailable` + log không VIN (sec L-2/L-6, arch L2), purge dòng hết hạn (sec L-4, arch L5), TTL ≤24 h (arch L6), ghi chú VIN tới ERP thật (arch L7). Ghi nhận, chưa sửa: A-31 (sec M-2), ràng buộc ownership ở người gọi (sec L-1, arch L1 → A-29), `"current"` chưa gắn với lượt (sec L-3), false positive (sec L-5, arch L4 → A-32), kiểu trả về `Optional<List>` (arch L3).

| ID | Trạng thái | Bằng chứng |
|---|---|---|
| T-UC02-VIN | VERIFIED (unit + PostgreSQL), phần validate/intake | `VinTest`, `VinExtractorTest`, `DefaultVehicleContextTest`, `VinSearchIT.invalidOrAmbiguousVinClearsTheCurrentVin`; "không gọi model" thuộc EP-04: NOT RUN |
| T-UC02-PROVENANCE | VERIFIED ở mức tool (mỗi ứng viên đúng một nhãn) | `VinSearchIT.vinFromIntakeDrivesTheVinRefSearch`, `DefaultCatalogSearchTest`, `MockErpConnectorTest.vinSearchReturnsFitmentAsCatalogLookup`; hiển thị UI (EP-10): NOT RUN |
| T-UC02-OE-FABRICATED | NOT RUN | cần OutputValidator (EP-04) |
| T-UC02-AMBIG | NOT RUN | từ chối báo giá thuộc EP-06 |
| T-TOOL-13 (end to end) | VERIFIED (PostgreSQL) | `VinSearchIT.theModelCannotPassAVinAsAnArgument` |

## 8. Triển khai EP-06 (2026-11-18/19)
- Quyết định owner: ADR-007/008/014 ACCEPTED; A-03, A-14, A-31 (VERIFIED = đúng mã phụ tùng), D-04/D-13 một phần; sau review vòng 1: A-19 giữ nguyên và được hiện thực, A-33, A-34, A-35 APPROVED, A-26 phần `quoteHandle` DECIDED; sau review vòng 2 (2026-11-19): A-33/A-34 thu hẹp (chỉ từ chối nghiệp vụ mới khóa và thu hồi; khóa và giới hạn theo khách trên mọi conversation), A-37 (ref ổn định theo binding kênh, EP-04); A-36 ghi các điểm còn mở.
- Code: `quote.QuoteService` / `quote.internal.DefaultQuoteService`, `quote.QuoteCards` (render card phía server), `quote.spi` (giá, tồn, công nợ), `platform.Money`, `identity.CustomerLinks` + `identity.spi.ErpCustomerLink`, Flyway V6 (`quote`, `quote_line`, `quote_handle`, `quote_request`) và V7 (`customer_link`). Executor `requestQuote` trong `tools`; `ToolCallContext.customer` (do EP-04 điền từ phiên); `ConversationAccessPort.boundCustomer` (mặc định: không có → không báo giá).
- Model chỉ nhận `QuoteRequestResult(status, quoteHandle?, lineCount)`; `NOT_QUOTED` luôn kèm handoff, kể cả khi DB/ERP lỗi; lỗi bất ngờ của quote → `TOOL_UNAVAILABLE` + handoff. Lý do chi tiết chỉ nằm trong `quote_request.reason_code` cho nhân viên (A-14), không trong log.
- Sửa lỗi từ EP-03: khoảng 28 % token `productRef` ngẫu nhiên chứa chuỗi giống VIN nên chính lệnh `requestQuote` của model bị từ chối `TOOL_SCHEMA` (T-TOOL-13). Token và quote handle giờ được rút lại khi giống VIN (`ProductRefRegistryIT`, `QuoteTokensTest`).
- Review (advisory): backend-reviewer 0H/4M/8L, security-auditor 0H/3M/6L, architecture-reviewer 1H/8M/9L. Đã sửa có test: race báo giá cũ thắng (newest wins), lỗi sau bước ERP → NOT_QUOTED + handoff, Mock đọc giá không qua `double`, revision tùy chọn, record lưu trữ không in số tiền, handle phải khớp cả conversation của quote, số tiền trên card đúng scale, ràng buộc DB, ARCH-08d (cả method reference), ARCH-13a (QuoteCard), ARCH-13b (Money), ARCH-13c (quote chỉ dùng `Sku` của catalog.spi), A-19/A-33/A-34/A-35. Ghi nhận, chưa sửa: A-36.

| ID | Trạng thái | Bằng chứng |
|---|---|---|
| T-TOOL-09 | VERIFIED (PostgreSQL) | `QuoteServiceIT.creditStockAndMissingCustomerLookIdenticalToTheModel` (công nợ bị chặn, cần xem xét, hết hàng, thiếu số lượng → cùng một kết quả + handoff; không có khách → `FORBIDDEN_RESOURCE` trước khi tới quote, A-35) |
| T-TOOL-10 | VERIFIED (PostgreSQL), có rủi ro còn lại | `QuoteServiceIT.quantityProbingRevealsNoStockNumber`, `aBusinessRefusalLocksTheCustomerInOtherConversationsToo`, `refusalsWhileLockedDoNotExtendTheLock`, `theSixthQuoteInAnHourIsRefused`; `JdbcQuoteStoreIT` (thứ tự, cửa sổ 24 h/1 h, theo khách); `DefaultQuoteServiceTest.theGateRefusesBeforeAnyErpCall`. Còn lộ được "tồn < q" qua một NOT_QUOTED; các yêu cầu đồng thời đều có thể tới ERP trước khi lần từ chối đầu được ghi (A-34) |
| T-SEC01-QUOTE | VERIFIED (PostgreSQL + unit) | `QuoteServiceIT.issuedQuoteGivesTheModelAHandleAndTheCustomerACard`, `DefaultToolManagerTest.quoteUsesTheSessionCustomerAndTheSkusBehindTheRefs`; sentinel giá: 0 lần trong log build và báo cáo test |
| T-UC03-MONEY | VERIFIED (unit + PostgreSQL) | `MoneyTest`, `QuoteCalculatorTest`, `QuoteServiceIT.halfUnitPriceIsRoundedUpPerLine`, `cardAmountsUseTheQuoteMoneyScale`, `MockErpConnectorTest.pricesAreParsedExactly` |
| T-UC03-LIMITS | VERIFIED (unit + PostgreSQL) | `QuoteRequest` (1..20 dòng, 1..9999), `ArgumentParserTest`, `QuoteServiceIT.databaseConstraintsBackTheLimits` |
| T-UC03-INELIGIBLE | VERIFIED (unit + PostgreSQL) | `DefaultQuoteServiceTest.everyReasonIsTheSameNotQuotedAndOnlyTheStoreKnowsWhy`, `QuoteServiceIT.creditStockAndMissingCustomerLookIdenticalToTheModel` |
| T-UC03-UNVERIFIED | VERIFIED (PostgreSQL) | `VinSearchIT.onlyExactOeResultsPassTheQuoteCheck`, `DefaultToolManagerTest.quoteNeedsProductRefsFromThisConversationAndVerified` |
| T-UC03-RENDER | VERIFIED ở mức service | `QuoteServiceIT.issuedQuoteGivesTheModelAHandleAndTheCustomerACard`; endpoint HTTP/UI: NOT RUN (EP-04/EP-10) |
| T-UC03-TOKEN-REPLAY | VERIFIED (PostgreSQL) | `QuoteServiceIT.handleOnlyRendersForItsConversationCustomerAndValidity`, `revokedOrStaleVersionHandlesDoNotRender`, `aHandleBoundToAnotherConversationThanItsQuoteDoesNotRender`, `aNewQuoteSupersedesTheOldOne`, `aNotQuotedRequestRetiresTheOlderQuote` |
| T-AUTH-02 | Một phần | handle 128 bit, chỉ lưu SHA-256, kết quả rỗng đồng nhất; rate limit HTTP: NOT RUN (EP-04) |
| SEC-02 (một phần) | VERIFIED (unit + PostgreSQL) | `DefaultToolManagerTest.aSessionCustomerOtherThanTheBoundOneIsForbidden`, `aSessionWithoutCustomerIsForbiddenBeforeQuoting`, `QuoteServiceIT.aSessionCustomerOtherThanTheBoundOneGetsNoQuote`, `JdbcQuoteStoreIT.anotherCustomersQuoteIsLeftAlone`; ARCH-08d/e/f; bộ 20 lần thử chéo khách: NOT RUN (EP-04) |
- Review vòng 2 (advisory, 2026-11-19): security-auditor 0H/2M/7L, architecture-reviewer 0H/4M/9L, backend-reviewer 0H/2M/8L. Đã sửa có test: khóa/giới hạn theo khách (sec M-1), tách `CustomerLinks`/`CustomerLinkBinding` + ARCH-08e/08f (sec M-2, arch M-C), chỉ từ chối nghiệp vụ mới khóa/thu hồi và không tự gia hạn (arch M-B, backend M-1, sec L-2), quote store không đụng báo giá của khách khác (sec L-1), lần thua cuộc đua tính vào giới hạn (sec L-3), A-35 từ chối rõ khi không có binding (sec L-4, arch L-3), render dừng khi liên kết bị thu hồi và trả rỗng khi dữ liệu lỗi (sec L-5, arch L-7, backend L-3), `bind` báo lỗi khi trùng ref (sec L-6), log transaction/JDBC ≥ INFO (sec L-7), lỗi tra liên kết = STORE_UNAVAILABLE (backend L-4), `lock_timeout` (backend L-6), seed test không ép kiểu (arch L-1), test đồng thời khẳng định không có lỗi kỹ thuật (backend M-2), `JdbcQuoteStoreIT` với thời điểm cố định, tài liệu ERP_CONNECTOR §1–§3.1 và DATA_FLOW §5 (arch M-D, L-8), bỏ `order` khỏi allowlist quote (arch L-5), javadoc package và ngày quyết định (arch L-4, L-6). Ghi nhận: A-36, A-37 (EP-04), rủi ro còn lại của A-34.
- Kiểm tra đột biến (nguồn khôi phục nguyên vẹn): tắt kiểm tra A-35 → `DefaultToolManagerTest` fail (exit 1); tắt khóa theo khách → `JdbcQuoteStoreIT` fail (exit 1).

## 9. Triển khai EP-04 (2026-11-19)
- Quyết định owner 2026-11-19 (A-38): ADR-011, ADR-015 ACCEPTED; user demo từ cấu hình + mật khẩu từ `APAS_DEMO_PASSWORD`; khách chưa liên kết không tạo được conversation; OutputValidator loại cả câu + handoff; 15 fixture SEC-03 (50 ở ACC-01); A-22 hoãn, thay bằng ARCH-14; ≤3 lần gọi model/lượt; JSON đồng bộ. Đã hiện thực: A-19, A-21, A-29, A-35, A-36c, A-37.
- Code: `identity` (Spring Security form login, `SecurityConfiguration`, `SessionLimitsFilter` 30 phút/8 giờ theo `Clock`, `SessionIdentities` → `CurrentIdentity`/`LinkedCustomers`, `JdbcPrincipals`, `DemoProfileGuard`, `identity.spi.ChannelIdentity`/`CustomerLinkPort`, `adapter.web.SessionController`), `erp.internal.MockErpCustomerLinks`, `conversation` (`Chat`, `ChatTurn`; `internal.ChatService`, `ConversationStore`, `ConversationAccess` hiện thực `ConversationAccessPort` + `boundCustomer`; `adapter.web.ConversationController`, `RequestSizeFilter`), `handoff` (`Handoffs`, `HandoffReason`, bản ghi OPEN), `agent` (`Agent`, `AgentTurn`, `AgentReply`, `internal.DefaultAgent`, `OutputValidator`), `platform.ProblemResponses`, `ErrorCode.CUSTOMER_NOT_LINKED`, ARCH-14. Flyway V8 (`principal_binding`, cột binding của `customer_link`) và V9 (`conversation`, `message`, `handoff`), chỉ thêm.
- Endpoint (profile demo): `GET /api/auth/csrf`, `POST /api/auth/login` (form), `POST /api/auth/logout`, `GET /api/auth/session`, `POST /api/demo/conversations`, `POST /api/demo/conversations/{id}/messages` `{clientEventId, text}`, `GET /api/demo/conversations/{id}/messages`. Mã lỗi: 401 `UNAUTHENTICATED`, 403 `FORBIDDEN`/`CUSTOMER_NOT_LINKED`, 404 (conversation của người khác hoặc sai định dạng), 409 (lượt khác đang chạy hoặc lượt chưa xong), 413, 415, 429.
- `requestHandoff` vẫn chưa có executor (EP-08): tool trả `TOOL_UNAVAILABLE` + handoff; agent ghi lý do `CUSTOMER_REQUEST`. **EP-08b:** đã có executor, xem §3.4 và §10.
- Review (advisory): security-auditor 0H/4M/6L, architecture-reviewer 0H/5M/8L. Đã sửa có test: tiếp nhận lượt nguyên tử bằng advisory lock 30_010 (dedup, một lượt đang chạy, giới hạn theo conversation và theo principal, đọc lại status) và tạo conversation 30_011 (sec M1, L3; arch M1, M4, L7); history giới hạn đến đúng tin của lượt (arch M1); mọi lượt đã tiếp nhận đều có câu trả lời lưu lại, lượt bỏ dở quá 2 phút hoàn tất thành handoff khi gửi lại (sec M2, arch M2); khóa bot + bản ghi handoff trong một transaction (arch M3); card không render được → handoff (sec M2); `CrossCustomerIT` với quote handle/quote ref thật (sec M3); profile demo chỉ bind loopback (sec M4); session đã xác thực mà thiếu `LOGIN_AT` → hết phiên (sec L1, arch L4); gửi lại sau handoff vẫn báo handedOff (sec L2); test đồng thời (sec L4, arch M4); `NEVER_EGRESS` thêm `cv_` và VIN đầy đủ (sec L5); `Chat`/`ChatTurn` ra package gốc (arch L1); một nguồn giới hạn text (arch L2); `EMPTY` (arch L3); ghi chú V9 về status handoff (arch L8); MODULE_BOUNDARIES, DATA_FLOW (arch M5). Ghi nhận: A-39.
- Lỗi harness tìm thấy khi sửa review: `script()` của test xóa nhật ký request của WireMock nên quét egress chỉ thấy lượt cuối; đã sửa (chỉ reset stub/scenario).
- Kiểm tra đột biến (nguồn khôi phục nguyên vẹn): bỏ lọc chủ sở hữu trong `ConversationStore.findOwned` → `ChatFlowIT` exit 1; tắt OutputValidator → `ChatFlowIT` exit 1; tắt idle timeout → `SessionSecurityIT` exit 1; tắt kiểm tra digest A-35 trong `ChatService` → `ChatFlowIT` exit 1.

| ID | Trạng thái | Bằng chứng |
|---|---|---|
| T-AUTH-01 | VERIFIED (HTTP, PostgreSQL) | `SessionSecurityIT`: CSRF thiếu/cũ khi login và logout, session mới khi login (cookie cũ 401), đổi user, cookie HttpOnly + Secure + SameSite=Lax, 401 đã sanitize |
| T-AUTH-IDLE / T-AUTH-MAX | VERIFIED (HTTP) | `SessionSecurityIT.sessionEndsAfterThirtyIdleMinutes`, `sessionEndsEightHoursAfterLoginEvenWhenActive`; `IdentityUnitTest.anAuthenticatedSessionWithoutLoginTimeIsEnded` |
| A-37 (ref ổn định) | VERIFIED (PostgreSQL) | `SessionSecurityIT.customerReferenceAndPrincipalAreStableAcrossLogins`, `aRevokedLinkEndsAtTheNextRequestAndIsNotRecreated` |
| T-UC01-DEDUP | VERIFIED (HTTP, đồng thời) | `ChatFlowIT.theSameClientEventIsProcessedOnce`, `concurrentDuplicatesRunOneTurn`, `aReplayAfterAHandoffStillSaysHandedOff`, `aTurnLeftUnansweredIsCompletedAsHandedOffOnReplay` |
| T-HTTP-01 | VERIFIED (HTTP) | `ChatFlowIT.malformedRequestsGetSanitizedErrors` (400/413/415, thiếu CSRF 403, không stack) |
| T-UC02-VIN (không gọi model) | VERIFIED (HTTP) | `ChatFlowIT.anInvalidVinIsAskedAgainWithoutCallingTheModel`, `aValidVinReachesTheModelOnlyMasked` |
| T-GW-OUTPUT | VERIFIED (unit + HTTP) | `OutputValidatorTest`, `DefaultAgentTest`, `ChatFlowIT.aPriceOrInventedCodeInTheAnswerIsDroppedAndHandedOff` |
| T-UC02-OE-FABRICATED | VERIFIED (unit + HTTP) | `OutputValidatorTest.aCodeIsAllowedOnlyWhenAToolReturnedItThisTurn`, `ChatFlowIT.aPriceOrInventedCodeInTheAnswerIsDroppedAndHandedOff` |
| T-UC03-RENDER (HTTP) | VERIFIED (HTTP) | `ChatFlowIT.anIssuedQuoteIsRenderedForTheCustomerAndNeverReachesTheModel` |
| T-SEC01-HISTORY | VERIFIED (HTTP, Fake provider, không qua proxy); **SEC-01b 2026-11-19: VERIFIED qua proxy capture kép** | cùng test: báo giá + 3 lượt sau, 0 sentinel/giá/card trong mọi body provider. SEC-01b: `EgressSuiteFlow200IT` (script 7 báo giá + 3 lượt, script 3 giá/SĐT/VIN đã che + 2 lượt) và `redactedValuesAreReplayedOnlyRedactedAndLeaveTheWindow` (ranh giới cửa sổ 6 tin) |
| T-SEC02 (20 lần thử) | VERIFIED một phần (HTTP + PostgreSQL) | `CrossCustomerIT`: 20 lần thử của B với productRef, quote handle, quote ref thật của A; 0 giá/handle/ref của A trong phản hồi cho B; từ chối tool ghi ở `tool_invocation`. Từ chối ở service ghi `audit_event` `ACCESS_DENIED` (EP-09b); từ chối mức URL (401/403 của Spring Security) chưa có (EP-09d) |
| T-SEC03 | VERIFIED một phần (15/50) | `AdversarialChatIT` (15 fixture: injection, tool cấm, args lớn/sâu, giá/duyệt đơn/link trong output); bộ 50: ACC-01 |
| A-35 (HTTP) | VERIFIED (HTTP) | `ChatFlowIT.aSessionCustomerOtherThanTheBoundOneIsRefused`, `aRevokedCustomerLinkEndsTheConversation` |
| T-SEC01-STAFF | VERIFIED 2026-11-19 (EP-08a, tại Fake provider) | `StaffInboxIT.staffMessagesNeverReachTheModel` |
| T-SEC06, T-SEC07 | NOT RUN | EP-09 (chưa có kill switch, breaker) |

## 10. Triển khai EP-08b (2026-11-20)
- `requestHandoff`: `tools.internal.DefaultToolManager` trả `HandoffResult(true)` + `handoffRequired` + `ToolOutcome.RequestedHandoff`; `agent` ánh xạ sang `AgentReply.Reason` (`UNSURE`, `OUT_OF_SCOPE` mới); `conversation.ChatService` mở handoff với reason tương ứng. Quyết định: DECISIONS_REQUIRED A-43. Sau review EP-08b: một `requestHandoff` bị **từ chối** mà vẫn chuyển nhân viên (quota, conversation bị khóa, lặp) ghi `TOOL_REJECTED_REPEATED` (trước đây `CUSTOMER_REQUEST`), `DefaultAgentTest.toolHandoffsMapToReasons`. Enum quảng bá cho model và enum nội bộ được test giữ khớp (`DefaultToolManagerTest.theAdvertisedHandoffReasonsAreExactlyTheAcceptedOnes`).

| ID | Trạng thái | Bằng chứng |
|---|---|---|
| T-TOOL-HANDOFF | VERIFIED 2026-11-20 (unit + HTTP; `./mvnw -B -o clean verify` exit 0, xem SESSION_HANDOFF) | `DefaultToolManagerTest.handoffRequestIsAcceptedWithItsReasonAndAlwaysHandsOff`, `handoffRequestOutsideTheSchemaIsRejectedWithoutAReason`; `StaffInboxIT.requestHandoffKeepsTheModelsReasonAndEndsTheTurn` (3 lý do, một lần gọi model), `requestHandoffWithAnUnknownReasonIsRejected` |
