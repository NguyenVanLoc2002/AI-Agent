# ERP connector contract (ports) — v0.1 DRAFT

> **Đây KHÔNG phải tài liệu API của bất kỳ ERP vendor nào.** Không có tài liệu API ERP thật nào được cung cấp. Mọi khả năng của ERP đích là **OPEN** (D-01..D-03, `docs/DECISIONS_REQUIRED.md`). Tài liệu này định nghĩa các *port nội bộ* vendor-neutral. Mock ERP connector (PoC) và một adapter ERP thật trong tương lai (MVP, gated G4) phải hiện thực các port này.
> Tên thiết kế mục tiêu: `ErpConnector` (tên khái niệm cho tập port, theo BRD §12; ADR-005 vẫn tách port, A-07), `MockErpConnector`, `RealErpConnector`. `MockErpConnector` tồn tại từ SEC-01a (`erp.internal`; port catalog, từ EP-06 thêm giá/tồn/công nợ; đơn nháp là `MockErpDraftOrders`, EP-07); `RealErpConnector` **chưa tồn tại**.
> 2026-11-13. Nhãn mặc định: DESIGN PROPOSAL (ADR-005, ADR-009).

## 1. Nguồn và khác biệt với BRD
- **SOURCE FACT (BRD §12, mức "API contract dự kiến"):** `ErpConnector` gồm `findLinkedCustomer(context)`, `searchVerifiedProduct(query)`, `getCustomerPrice(serverCustomerId, sku)`, `getAvailability(sku)`, `getCreditEligibility(serverCustomerId)`, `createDraftOrder(command, idempotencyKey)`, `findDraftByExternalRef(externalRef)`.
- **DESIGN PROPOSAL (ADR-005):** tách thành các port nhỏ, mỗi port thuộc module tiêu thụ, để module `tools`/`agent`/`gateway` không thể tiếp cận port giá/công nợ (ARCH-02). Ánh xạ:

| BRD §12 | Port đề xuất | Module sở hữu | Ai được phụ thuộc |
|---|---|---|---|
| findLinkedCustomer | `CustomerLinkPort.findByChannelBinding` (trong `identity :: spi`, erp hiện thực; EP-04) | identity | identity |
| searchVerifiedProduct | `VerifiedProductPort.search` | catalog | catalog |
| getCustomerPrice | `PricingPort.priceFor` | quote | quote, order |
| getAvailability | `AvailabilityPort.quantityAvailable` (chi tiết, INTERNAL) | quote | quote, order |
| getAvailability | `AvailabilityBucketPort.bucket` (chỉ enum) | catalog | catalog |
| getCreditEligibility | `CreditEligibilityPort.check` | quote | quote, order |
| createDraftOrder, findDraftByExternalRef | `DraftOrderPort` | order | order |

## 2. Kiểu dùng chung
| Kiểu | Định nghĩa | Ghi chú |
|---|---|---|
| `AuthenticatedCustomerRef` | opaque, chỉ identity tạo được (constructor package-private / factory) | không String/Long thô; không nhận từ request |
| `Sku` | value object, regex bounded | |
| `Money` | `BigDecimal amount` + `Currency` (VND) | EP-06: `platform.Money` (DESIGN PROPOSAL, ARCH-13b giới hạn module dùng); VND scale 0, `HALF_UP` theo dòng (ADR-014 ACCEPTED); `toString()` không in số tiền |
| `ErpCustomerLink` | mã khách theo định dạng của ERP, chuỗi mờ | EP-06, A-19: `identity.spi.ErpCustomerLink`; chỉ identity, quote, order (từ EP-07, A-41(a)), erp dùng (ARCH-08d); không liên quan tới định dạng `AuthenticatedCustomerRef` |
| `PriceSource` | EP-06: chuỗi mã nguồn giá (ví dụ mã bảng giá) + `revision` (string opaque, **tùy chọn**, có thể null) trong `quote.spi.PriceQuote` | ERP đích có trả revision hay không: OPEN D-01. Fallback: so sánh `unitPrice` bằng `compareTo` khi recheck (EP-07) |
| `EligibilityStatus` | `ELIGIBLE / NOT_ELIGIBLE / REVIEW_REQUIRED` (EP-06; bản nháp cũ gọi là `NEEDS_REVIEW`) | **không** trả số dư/hạn mức |
| Tồn kho | quote: `OptionalInt quantityAvailable` (INTERNAL, chỉ quote dùng); catalog: `AvailabilityBucket` `CON_HANG / HET_HANG / CAN_KIEM_TRA` | bucket là SOURCE FACT BRD §08; EP-06 không dùng kiểu gộp `AvailabilityResult` của bản nháp |

## 3. Port
### 3.1 `CustomerLinkPort` (identity)
- EP-04 (theo A-19, A-37): `Optional<ErpCustomerLink> findByChannelBinding(ChannelIdentity binding)` — trả mã khách ERP (RESTRICTED), **không** trả `AuthenticatedCustomerRef` (erp không tạo được ref, ARCH-08a). Identity tìm hoặc tạo ref ổn định cho binding đó (A-37) rồi mới đưa ra ngoài.
- Chỉ tra theo binding đã xác thực (kênh + provider user hash). **Không** tìm theo tên, SĐT, tiền tố (chống enumerate — rule 10).
- PoC: seed tĩnh. Khách chưa liên kết: MVP (OPEN D-06/D-08).
- **A-19 (APPROVED, triển khai EP-06):** port trả về một `ErpCustomerLink` mờ; `identity.CustomerLinks.bind` tạo `AuthenticatedCustomerRef` ngẫu nhiên (`cref_` + 128 bit) và lưu ánh xạ trong `customer_link` (Flyway V7). `erp` không bao giờ thấy hay tạo ref; quote hỏi identity lấy `ErpCustomerLink` rồi truyền cho port. EP-04 hiện thực `CustomerLinkPort`.

### 3.2 `VerifiedProductPort` (catalog)
- `List<ProductCandidate> search(ExactProductQuery q)` — q gồm đúng một trong: VIN (đã validate), OE, (category + model xe); `maxResults ≤ 5` (DESIGN PROPOSAL).
- `ProductCandidate`: sku, oe, category, `Provenance` (`AI_RECOGNIZED / CATALOG_LOOKUP / VERIFIED`), sourceRef. Không giá, không tồn số.
- **OPEN D-05:** "VERIFIED" trong PoC = catalog synthetic; không suy ra ERP/catalog thật đã xác minh.
- **EP-05 (2026-11-18):** `ExactProductQuery.ByVin(Vin)` — `catalog.spi.Vin` đã validate (BR-04), `toString()` chỉ in WMI + mask. Chỉ `catalog` tạo truy vấn này từ VIN đã lưu của conversation; không bao giờ từ output của model. Kết quả tìm theo VIN (fitment: VIN → model xe → sản phẩm) mang provenance `CATALOG_LOOKUP` ("Dữ liệu tra cứu", BR-06), **không** `VERIFIED`: model xe chỉ thu hẹp catalog, không xác định đúng mã phụ tùng; theo BR-07 kết quả này không được báo giá tự động (DESIGN PROPOSAL).

### 3.3 `PricingPort` (quote)
- EP-06: `Optional<PriceQuote> priceFor(ErpCustomerLink c, Sku s)` → `Money unitPrice`, `priceSource`, `revision?`. Rỗng = `PRICE_NOT_FOUND`.
- Lỗi: `QuoteSourceUnavailableException` (không message, không cause; không trả message gốc của ERP).

### 3.4 `AvailabilityPort` (quote) và `AvailabilityBucketPort` (catalog)
- EP-06: `OptionalInt AvailabilityPort.quantityAvailable(Sku s)` (INTERNAL) — chỉ quote/order; rỗng = không biết số lượng = không đủ hàng. Việc so sánh với số lượng yêu cầu làm trong quote. BRD §12 chỉ có `getAvailability(sku)` (SOURCE FACT); ERP đích trả số lượng hay chỉ trạng thái: OPEN D-01.
- `AvailabilityBucketPort.bucket(Sku s)` → `CON_HANG / HET_HANG / CAN_KIEM_TRA` — catalog dùng cho kết quả tìm kiếm; **không nhận số lượng** (chống dò, D-12). Adapter tự map, catalog không bao giờ thấy số lượng.

### 3.5 `CreditEligibilityPort` (quote)
- EP-06: `EligibilityStatus check(ErpCustomerLink c)` — chỉ enum; khách lạ hoặc trạng thái lạ → `REVIEW_REQUIRED` (fail closed), theo chữ ký BRD §12 (chỉ customer). Việc xét theo tổng đơn (`orderTotal`) là mở rộng **OPEN** (D-01, điều kiện công nợ BRD §15); nếu có, chỉ quote dùng và kết quả **không bao giờ** tới model (tool trả `NOT_QUOTED` chung).

### 3.6 `DraftOrderPort` (order)
- Capabilities `supportsIdempotencyKey`, `supportsExternalRefLookup` (cả hai **OPEN D-03**) là **cấu hình tĩnh** (`apas.erp.capabilities.*`, do module `order` đọc) đặt từ tài liệu API ERP đích đã được phê duyệt, không phải adapter tự khai báo lúc chạy. Mặc định an toàn: false/false. Mock chạy được cả hai chế độ.
- EP-07: `DraftOrderOutcome create(DraftOrderCommand cmd, Optional<IdempotencyKey> key)` → sealed: `Created(erpOrderRef)` (số đơn nháp của ERP), `Rejected(code)` (enum `CUSTOMER_NOT_ALLOWED`/`INVALID_ORDER`, adapter map từ allowlist), `Unknown(reason)` (`TIMEOUT`/`UNCLASSIFIED`). `key` chỉ có khi cấu hình capability cho phép (BR-18); `cmd` gồm `ErpCustomerLink`, `ExternalRef` (tham chiếu của ta: `<mã báo giá>:<version>`, để nhân viên tìm đơn khi đối soát; ERP đích có nhận trường này hay không: OPEN D-01/D-03) và các dòng (sku, số lượng, đơn giá đã chốt). Adapter gọi đúng một lần, không retry, không ném exception cho lỗi ERP. Timeout = `Unknown`, **không** = thất bại. `Rejected` có mã cấu trúc hay không phụ thuộc ERP đích (OPEN D-01); nếu không phân biệt được lỗi nghiệp vụ với lỗi kỹ thuật → coi là `Unknown`.
- Không có port đọc trạng thái phê duyệt đơn từ ERP đích (OPEN D-01); PoC ghi nhận thủ công.
- `Optional<DraftOrderStatus> findByExternalRef(ExternalRef ref)` — chỉ gọi khi `supportsExternalRefLookup`. EP-07: gọi đúng một lần ngay sau `Unknown`; tìm thấy → `RECONCILED_CREATED`, không thấy → vẫn `OUTCOME_UNKNOWN` (không thấy ngay sau timeout không chứng minh được gì).
- **Không có** `approve`, `cancel` thay người, `updatePrice` (SOURCE FACT: agent không phê duyệt).

## 4. Phương thức bị cấm ở mọi port (ARCH-09)
`getCost`, `getMargin`, `listCustomers`, `searchCustomers`, `getPurchaseHistory`, `getDebtBalance`, `approveOrder`, `updatePrice`, mọi phương thức nhận SQL/URL/filter tự do.

## 5. Mock ERP connector (PoC, tên mục tiêu `MockErpConnector`) — DESIGN PROPOSAL
- Schema `mock_erp` tách khỏi `app`; chỉ module `erp` truy cập.
- Dữ liệu synthetic có **sentinel** (SOURCE FACT SECURITY_TEST_MATRIX): giá vốn `987654321`, công nợ `123456789`; thêm sentinel giá theo khách **riêng cho từng khách** (A, B — để SEC-02 phân biệt được rò giá của ai) và tên khách giả (honeytoken registry trong `policy`).
- Fault injection có cấu hình: timeout trước/sau commit, lỗi với message chứa sentinel, trả field thừa (để chứng minh strip tại biên), duplicate response.
- Adapter phải **strip field nhạy cảm tại biên** trước khi persist/log (rule 20).
- **SEC-01a (2026-11-18):** `erp.internal.MockErpConnector` hiện thực `VerifiedProductPort` + `AvailabilityBucketPort` trên backend giả **in-memory** (trả JSON thô có field thừa và sentinel); schema `mock_erp` chưa tạo (cần khi có draft order, EP-07). Fault mode: `ERROR_WITH_SENTINEL`, `TIMEOUT`, `DUPLICATE_RESPONSE`, `MALFORMED_ROWS` (chỉ test đặt; không có trong cấu hình chung). Provenance do adapter gán từ cấu hình, **không** đọc từ nội dung dòng ERP (review M7). Backend giả trả toàn bộ dòng rồi adapter lọc: đây là giả định của Mock, adapter thật **không** được liệt kê toàn bộ catalog. Ngưỡng bucket (≥3 CON_HANG, 1–2 CAN_KIEM_TRA, 0 HET_HANG): DESIGN PROPOSAL, D-12 OPEN. T-ERP-01..03 đã chạy.
- **EP-06 (2026-11-18/19):** Mock hiện thực thêm `PricingPort`, `AvailabilityPort`, `CreditEligibilityPort`. Khách synthetic theo mã ERP `SYNTH-CUST-A` (công nợ tốt), `SYNTH-CUST-B` (bị chặn công nợ), `SYNTH-CUST-C` (không có hồ sơ công nợ → REVIEW_REQUIRED, không có giá); identity ánh xạ ref → mã này. Giá là sentinel SEC-01 (`555444333`, `666777888`) để chứng minh không bao giờ tới model; `SKU-BRK-PAD-B` có nửa đơn vị (kiểm tra làm tròn), `SKU-AIR-FLT-A` có 19 chữ số có nghĩa (kiểm tra đọc chính xác, `USE_BIG_DECIMAL_FOR_FLOATS`). Dòng giá/công nợ thô kèm giá vốn, margin, số dư, hạn mức, người liên hệ; adapter chỉ đọc `unitPrice/currency/priceList/revision` và `creditStatus`. Fault mode `ERROR_WITH_SENTINEL`/`TIMEOUT` → `QuoteSourceUnavailableException`; `MALFORMED_ROWS` → không có giá. `quantityAvailable` đọc cả bảng sản phẩm: giả định của Mock. Một bean hiện thực mọi port: tách theo nhóm port trước khi có `RealErpConnector` (EP-06 review L7). Adapter thật cần deadline/bulkhead cho tối đa 41 lời gọi mỗi yêu cầu (A-36b).
- **EP-05 (2026-11-18):** backend giả có bảng fitment VIN synthetic (WMI `5ZZ`/`RLZ`, không phải xe thật) trả dòng thô kèm tên chủ xe, số điện thoại và công nợ sentinel; adapter chỉ đọc `vehicleModel` (≤80 ký tự), dòng sai dạng → không fitment (log `erp.vehicle.row_skipped`, không VIN). Lỗi backend → `CatalogUnavailableException` (log `erp.vehicle.unavailable`, tên lớp exception). Đây là giả định của Mock: adapter thật cần API giải mã VIN/fitment đã được duyệt (D-01..D-03, D-05). VIN đầy đủ chỉ được gửi tới nguồn ERP/fitment nội bộ đã duyệt, không đặt trong URL/query string (log proxy), và không gửi tới dịch vụ giải mã VIN công cộng khi chưa được duyệt (EP-05 reviews L7). Tìm theo VIN không có category trả tối đa 5 sản phẩm đầu tiên của model xe: rộng, cần xem lại cùng A-25.

- **EP-07 (2026-11-19):** `erp.internal.MockErpDraftOrders` hiện thực `DraftOrderPort` trên cùng backend giả. Đơn nháp giữ **trong bộ nhớ** (schema `mock_erp` vẫn chưa tạo: DESIGN PROPOSAL, A-41(i)); có khóa idempotency thì trả lại đơn đã có, không có thì mỗi lời gọi tạo một đơn mới (để lời gọi thứ hai lộ ra thành đơn trùng). Câu trả lời thô kèm mã khách, công nợ, giá vốn, tên người duyệt và message lỗi có sentinel; adapter chỉ đọc `draftNo` và `error.code`. Lỗi: `MockErpOrderFault` (`TIMEOUT_BEFORE_COMMIT`, `TIMEOUT_AFTER_COMMIT`, `REJECT`, `UNLISTED_ERROR`, `MALFORMED_RESPONSE`, `ERROR_WITH_SENTINEL`). `MockErpSimulation` (chỉ Mock): test đổi giá/tồn/công nợ giữa lúc báo giá và lúc xác nhận, đặt lỗi, đếm đơn; không endpoint nào tới được (ARCH-03). Mock bỏ qua đơn giá từng dòng (A-41(t)).

## 6. Adapter ERP thật (tên mục tiêu `RealErpConnector`) — OPEN, gated G4
Điều kiện tối thiểu trước khi viết: tài liệu API được ERP vendor/IT phê duyệt (D-01), tài khoản tích hợp không có quyền giá vốn/duyệt đơn hoặc có compensating control (D-02), xác nhận idempotency/lookup (D-03), sandbox, contract tests chạy cùng bộ test của Mock.

## 7. Contract tests bắt buộc cho mọi adapter (NOT RUN)
| Test | Mô tả |
|---|---|
| T-ERP-01 | Không phương thức cấm (reflection trên interface) |
| T-ERP-02 | Field thừa từ ERP bị strip, không có trong DTO/log |
| T-ERP-03 | Exception message chứa sentinel → lỗi trả ra chỉ có code |
| T-ERP-04 | Timeout sau commit → `Unknown` — VERIFIED 2026-11-19 (Mock): `MockErpDraftOrdersTest.tErp04…`, `DraftOrdersIT.aTimeoutAfterTheErpWroteIsUnknownAndNeverSentAgain` |
| T-ERP-05 | Capabilities false → order không gọi `findByExternalRef`; giá trị capability đọc từ cấu hình, không từ adapter — VERIFIED 2026-11-19 (Mock): `DraftOrdersIT.capabilitiesDefaultToUnsupported`, `aTimeoutAfterTheErpWroteIsUnknownAndNeverSentAgain`, `withTheCapabilitiesTheKeyIsForwardedAndALookupReconciles` |
| T-ERP-06 | `findByChannelBinding` với binding sai/không tồn tại → empty, không lộ khác biệt timing đáng kể (best effort) |
