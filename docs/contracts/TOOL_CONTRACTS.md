# Tool contracts — v0.1 DRAFT

> 2026-11-13. Nguồn: CLAUDE.md, rule 10/30, skill `agent-tool-contract`, BRD §03–§05 (`@af2d09f`). Danh sách tool và schema: DESIGN PROPOSAL (ADR-006). Chưa VERIFIED.

## 1. Quy tắc chung (SOURCE FACT trừ khi ghi khác)
- Tool call của model là **đề xuất**. Mỗi lần thực thi: tên ∈ allowlist theo vai trò/kênh → JSON schema `additionalProperties:false` → giới hạn kích thước/độ sâu → authz principal + ownership tài nguyên → quota → trạng thái hợp lệ.
- **Không** tham số `customerId`, `tenantId`, `userId`, `price`, `discount`, URL, SQL, filter tự do.
- Đầu ra chỉ DTO `@EgressClassified(APPROVED_TECHNICAL)`; tool output được coi là untrusted và đi qua EgressGuard trước khi tới model.
- Không reflection invocation; registry tĩnh (ARCH-11).
- Lỗi trả mã đã sanitize; audit `TOOL_INVOKED/TOOL_REJECTED` với tên tool, decision, field names.
- Giới hạn (DESIGN PROPOSAL, OPEN D-10): ≤3 tool call/lượt; ≤20 tool call/hội thoại/giờ **và** ≤40 tool call/principal/giờ trên mọi conversation; ≤5 conversation mới/principal/giờ; args ≤4 KB, độ sâu ≤3; mảng ≤20.

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
- Input (đúng một nhánh, `oneOf`): `{ "vinRef": "current" }` (dùng VIN đã validate trong state) | `{ "oe": string matching ^[A-Z0-9-]{3,30}$ }` | `{ "category": enum, "vehicleModel": string(≤50, charset hạn chế) }`.
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

## 5. Tests bắt buộc (NOT RUN)
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
