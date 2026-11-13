# ERP connector contract (ports) — v0.1 DRAFT

> **Đây KHÔNG phải tài liệu Arito API.** Không có tài liệu API Arito nào được cung cấp. Mọi khả năng của Arito là **OPEN** (D-01..D-03, `docs/DECISIONS_REQUIRED.md`). Tài liệu này định nghĩa *port nội bộ* mà Mock Arito (PoC) và một adapter Arito tương lai (MVP, gated G4) phải hiện thực.
> 2026-11-13. Nhãn mặc định: DESIGN PROPOSAL (ADR-005, ADR-009).

## 1. Nguồn và khác biệt với BRD
- **SOURCE FACT (BRD §12, mức "API contract dự kiến"):** `ErpConnector` gồm `findLinkedCustomer(context)`, `searchVerifiedProduct(query)`, `getCustomerPrice(serverCustomerId, sku)`, `getAvailability(sku)`, `getCreditEligibility(serverCustomerId)`, `createDraftOrder(command, idempotencyKey)`, `findDraftByExternalRef(externalRef)`.
- **DESIGN PROPOSAL (ADR-005):** tách thành các port nhỏ, mỗi port thuộc module tiêu thụ, để module `tools`/`agent`/`gateway` không thể tiếp cận port giá/công nợ (ARCH-02). Ánh xạ:

| BRD §12 | Port đề xuất | Module sở hữu | Ai được phụ thuộc |
|---|---|---|---|
| findLinkedCustomer | `CustomerLinkPort.findByChannelBinding` | identity | identity |
| searchVerifiedProduct | `VerifiedProductPort.search` | catalog | catalog |
| getCustomerPrice | `PricingPort.priceFor` | quote | quote, order |
| getAvailability | `AvailabilityPort.check` (chi tiết, INTERNAL) | quote | quote, order |
| getAvailability | `AvailabilityBucketPort.bucket` (chỉ enum) | catalog | catalog |
| getCreditEligibility | `CreditEligibilityPort.check` | quote | quote, order |
| createDraftOrder, findDraftByExternalRef | `DraftOrderPort` | order | order |

## 2. Kiểu dùng chung
| Kiểu | Định nghĩa | Ghi chú |
|---|---|---|
| `AuthenticatedCustomerRef` | opaque, chỉ identity tạo được (constructor package-private / factory) | không String/Long thô; không nhận từ request |
| `Sku` | value object, regex bounded | |
| `Money` | `BigDecimal amount` + `Currency` (VND) | scale/rounding: ADR-014, OPEN D-04 |
| `PriceSource` | enum + `revision` (string opaque, **tùy chọn**) | Arito có trả revision hay không: OPEN D-01. Fallback: so sánh `unitPrice` bằng `compareTo` khi recheck |
| `EligibilityStatus` | `ELIGIBLE / NOT_ELIGIBLE / NEEDS_REVIEW` | **không** trả số dư/hạn mức |
| `AvailabilityResult` | `bucket: CON_HANG / HET_HANG / CAN_KIEM_TRA` + `quantityAvailable` (INTERNAL, chỉ quote dùng) | bucket là SOURCE FACT BRD §08 |

## 3. Port
### 3.1 `CustomerLinkPort` (identity)
- `Optional<AuthenticatedCustomerRef> findByChannelBinding(ChannelIdentity binding)`
- Chỉ tra theo binding đã xác thực (kênh + provider user hash). **Không** tìm theo tên, SĐT, tiền tố (chống enumerate — rule 10).
- PoC: seed tĩnh. Khách chưa liên kết: MVP (OPEN D-06/D-08).

### 3.2 `VerifiedProductPort` (catalog)
- `List<ProductCandidate> search(ExactProductQuery q)` — q gồm đúng một trong: VIN (đã validate), OE, (category + model xe); `maxResults ≤ 5` (DESIGN PROPOSAL).
- `ProductCandidate`: sku, oe, category, `Provenance` (`AI_RECOGNIZED / CATALOG_LOOKUP / VERIFIED`), sourceRef. Không giá, không tồn số.
- **OPEN D-05:** "VERIFIED" trong PoC = catalog synthetic; không suy ra Arito/catalog thật xác minh.

### 3.3 `PricingPort` (quote)
- `PriceQuote priceFor(AuthenticatedCustomerRef c, Sku s)` → `Money unitPrice`, `PriceSource`, `revision`.
- Lỗi: `PRICE_NOT_FOUND`, `UNAVAILABLE` (không trả message gốc của ERP).

### 3.4 `AvailabilityPort` (quote) và `AvailabilityBucketPort` (catalog)
- `AvailabilityPort.check(Sku s)` → `quantityAvailable` (INTERNAL) — chỉ quote/order. Việc so sánh với số lượng yêu cầu làm trong quote. BRD §12 chỉ có `getAvailability(sku)` (SOURCE FACT); Arito trả số lượng hay chỉ trạng thái: OPEN D-01.
- `AvailabilityBucketPort.bucket(Sku s)` → `CON_HANG / HET_HANG / CAN_KIEM_TRA` — catalog dùng cho kết quả tìm kiếm; **không nhận số lượng** (chống dò, D-12). Adapter tự map, catalog không bao giờ thấy số lượng.

### 3.5 `CreditEligibilityPort` (quote)
- `EligibilityStatus check(AuthenticatedCustomerRef c)` — chỉ enum, theo chữ ký BRD §12 (chỉ customer). Việc xét theo tổng đơn (`orderTotal`) là mở rộng **OPEN** (D-01, điều kiện công nợ BRD §15); nếu có, chỉ quote dùng và kết quả **không bao giờ** tới model (tool trả `NOT_QUOTED` chung).

### 3.6 `DraftOrderPort` (order)
- Capabilities `supportsIdempotencyKey`, `supportsExternalRefLookup` (cả hai **OPEN D-03**) là **cấu hình tĩnh** (`erp.capabilities.*`) đặt từ tài liệu Arito đã được phê duyệt, không phải adapter tự khai báo lúc chạy. Mặc định an toàn: false/false. Mock chạy được cả hai chế độ.
- `DraftOrderOutcome create(DraftOrderCommand cmd, IdempotencyKey key)` → sealed: `Created(externalRef)`, `Rejected(code)`, `Unknown(reason)`. Timeout = `Unknown`, **không** = thất bại. `Rejected` có mã cấu trúc hay không phụ thuộc Arito (OPEN D-01); nếu không phân biệt được lỗi nghiệp vụ với lỗi kỹ thuật → coi là `Unknown`.
- Không có port đọc trạng thái phê duyệt đơn từ Arito (OPEN D-01); PoC ghi nhận thủ công.
- `Optional<DraftOrderStatus> findByExternalRef(ExternalRef ref)` — chỉ gọi khi `supportsExternalRefLookup`.
- **Không có** `approve`, `cancel` thay người, `updatePrice` (SOURCE FACT: agent không phê duyệt).

## 4. Phương thức bị cấm ở mọi port (ARCH-09)
`getCost`, `getMargin`, `listCustomers`, `searchCustomers`, `getPurchaseHistory`, `getDebtBalance`, `approveOrder`, `updatePrice`, mọi phương thức nhận SQL/URL/filter tự do.

## 5. Mock Arito (PoC) — DESIGN PROPOSAL
- Schema `mock_erp` tách khỏi `app`; chỉ module `erp` truy cập.
- Dữ liệu synthetic có **sentinel** (SOURCE FACT SECURITY_TEST_MATRIX): giá vốn `987654321`, công nợ `123456789`; thêm sentinel giá theo khách **riêng cho từng khách** (A, B — để SEC-02 phân biệt được rò giá của ai) và tên khách giả (honeytoken registry trong `policy`).
- Fault injection có cấu hình: timeout trước/sau commit, lỗi với message chứa sentinel, trả field thừa (để chứng minh strip tại biên), duplicate response.
- Adapter phải **strip field nhạy cảm tại biên** trước khi persist/log (rule 20).

## 6. Adapter Arito thật — OPEN, gated G4
Điều kiện tối thiểu trước khi viết: tài liệu API được Arito/IT phê duyệt (D-01), tài khoản tích hợp không có quyền giá vốn/duyệt đơn hoặc có compensating control (D-02), xác nhận idempotency/lookup (D-03), sandbox, contract tests chạy cùng bộ test của Mock.

## 7. Contract tests bắt buộc cho mọi adapter (NOT RUN)
| Test | Mô tả |
|---|---|
| T-ERP-01 | Không phương thức cấm (reflection trên interface) |
| T-ERP-02 | Field thừa từ ERP bị strip, không có trong DTO/log |
| T-ERP-03 | Exception message chứa sentinel → lỗi trả ra chỉ có code |
| T-ERP-04 | Timeout sau commit → `Unknown` |
| T-ERP-05 | Capabilities false → order không gọi `findByExternalRef`; giá trị capability đọc từ cấu hình, không từ adapter |
| T-ERP-06 | `findByChannelBinding` với binding sai/không tồn tại → empty, không lộ khác biệt timing đáng kể (best effort) |
