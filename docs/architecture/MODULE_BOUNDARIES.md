# Module boundaries & dependency rules

> DESIGN PROPOSAL (ADR-001, ADR-005) — 2026-11-13, sửa sau architecture review cùng ngày. Enforcement đề xuất: Spring Modulith `ApplicationModules.verify()` + ArchUnit trong `test` (build fail khi vi phạm). Chưa VERIFIED.
> Module gốc theo CLAUDE.md (SOURCE FACT): identity, conversation, agent, gateway, tools, catalog, erp, quote, order, audit. Module bổ sung (DESIGN PROPOSAL): policy, handoff, securityinspector, platform, bootstrap.
> `web` **không** phải module: controller nằm trong `adapter.web` của module sở hữu endpoint.

## 1. Trách nhiệm
| Module | Trách nhiệm | Sở hữu port / API | Lớp dữ liệu được chạm |
|---|---|---|---|
| identity | Principal, vai trò, phiên đăng nhập (EP-04), liên kết kênh → `AuthenticatedCustomerRef` | `CurrentIdentity` (phiên → `SessionIdentity`), `LinkedCustomers`, `CustomerLinkPort` + `ChannelIdentity` (spi) | RESTRICTED (identity) — không xuất giá trị thô |
| conversation | Endpoint chat (profile demo), dedup, một lượt đang chạy/conversation, trạng thái hội thoại (`status` ACTIVE_BOT/HANDOFF_PENDING + `row_version`), ghép response + render quote card | `Chat` (API cho `adapter.web`), hiện thực `tools.spi.ConversationAccessPort`; `ChannelPort` (Zalo, EP-12) | INTERNAL, sanitized text |
| agent | Vòng lặp agent: dựng `ModelRequest`, history summary, gọi ToolManager, OutputValidator | `Agent.reply(AgentTurn) -> AgentReply` | APPROVED_TECHNICAL |
| gateway | Duy nhất gọi provider: serialize, EgressGuard, transport, timeout/breaker | `ModelProviderPort`, `EgressTransport` | APPROVED_TECHNICAL |
| policy | DLP inbound/outbound, phát hiện VIN để mask/chặn (không validate, không lưu; EP-05 chuyển validate VIN sang catalog), honeytoken registry, normalizer (marker phân loại nằm ở platform, xem §7) | `EgressPolicy`, `InboundSanitizer` | đọc để **chặn**; không lưu giá trị |
| tools | Registry, schema, authz per call, quota, mapping kết quả sang DTO an toàn | `ToolManager` (api), `ConversationAccessPort` (spi) | APPROVED_TECHNICAL |
| catalog | Tra cứu sản phẩm, provenance, bucket tồn thô; validate + lưu VIN hiện tại của conversation (EP-05) | `VerifiedProductPort` (spi), `AvailabilityBucketPort` (spi, chỉ enum), `VehicleContext`, `CatalogSearch` | APPROVED_TECHNICAL; VIN đầy đủ: nhạy cảm theo ngữ cảnh, không ra khỏi catalog/erp |
| quote | Giá, tồn, eligibility, quote/version, handle, render, **endpoint confirm** | `quote.spi`: `PricingPort`, `AvailabilityPort`, `CreditEligibilityPort`; `QuoteApi`, `QuoteHandleService` | RESTRICTED, INTERNAL |
| order | Idempotency, draft order, UNKNOWN, đối soát | `order.spi.DraftOrderPort`; `DraftOrders` (`register`/`dispatch`/`stateOf`), `OrderReconciliation` (EP-07; tên dự kiến cũ: `OrderApi.submit`) | RESTRICTED, INTERNAL |
| handoff | Queue, reason code, claim/resolve (EP-08); EP-04: chỉ bản ghi OPEN | `Handoffs.open(conversation, reason)` (EP-04, gọi trực tiếp; event `HandoffOpened/Resolved` ở EP-08) | INTERNAL |
| audit | Sink sự kiện field-names-only **và** chủ sở hữu bảng `egress_record` | `EgressAuditLog` (port đồng bộ cho gateway, SEC-01a), `AuditSink`, `AuditQueryApi` (read) | metadata |
| securityinspector | Đọc audit/egress qua `AuditQueryApi` cho SECREV | — | metadata |
| platform | KillSwitch, Clock, correlation ID, error mapping, marker `@EgressClassified`/`DataClassification` | `KillSwitch`, `Clock` | — |
| erp | **Leaf adapter:** Mock ERP (PoC), ERP thật (MVP, gated) hiện thực các spi | — | RESTRICTED (strip tại biên) |
| bootstrap | Ứng dụng Spring Boot + wiring; module duy nhất được phụ thuộc mọi module | — | — |

## 2. Hướng phụ thuộc cho phép
```
conversation ─► agent ─► gateway ─► policy
      │           └────► tools ─► catalog
      │                    └────► quote (EP-06: QuoteService.request với SKU + khách của phiên)
      ├─► handoff (HandoffApi.open)          handoff ──(event HandoffOpened/Resolved)──► conversation (listener)
      └─► quote (render quote card)
quote ─► order (DraftOrders; order ─(event DraftOrderStateChanged)─► quote, quote ─(event QuoteStaffRequired)─► conversation)
gateway ─► audit (EgressAuditLog.record, gọi đồng bộ)   securityinspector ─► audit (AuditQueryApi)
mọi module ─► identity.api, platform.api, audit.api (publish)
erp ─► (implements) identity.spi, catalog.spi, quote.spi, order.spi (EP-06: erp dùng identity :: spi chỉ cho ErpCustomerLink)
quote ─► identity (CustomerLinks), identity :: spi, catalog :: spi (chỉ Sku, ARCH-13c)
bootstrap ─► mọi module (wiring)
```
- Không vòng: handoff không gọi conversation trực tiếp; khóa bot qua event. gateway không ghi bảng audit trực tiếp: nó gọi port `audit.EgressAuditLog`, do `audit.internal` hiện thực (audit không phụ thuộc gateway, ARCH-10).

## 3. Quy tắc (mỗi quy tắc = một ArchUnit/Modulith test, ID ARCH-xx)
| ID | Quy tắc | Lý do / nguồn |
|---|---|---|
| ARCH-01 | Chỉ `gateway.adapter..` được import SDK/HTTP client provider | SOURCE FACT CLAUDE.md |
| ARCH-02 | **Allowlist:** chỉ `quote..`, `order..`, `erp..`, `bootstrap..` được phụ thuộc `quote.spi..`; chỉ `order..`, `erp..`, `bootstrap..` được phụ thuộc `order.spi..` | SOURCE FACT giá/công nợ nội bộ; review: denylist không đủ |
| ARCH-03 | Không module nào import `erp..` trừ `bootstrap` | DESIGN PROPOSAL |
| ARCH-04 | Chỉ `conversation..` (và `bootstrap`) được phụ thuộc `agent..` | DESIGN PROPOSAL |
| ARCH-05 | Kiểu trong `gateway.api..` và `tools.api.dto..` (kể cả field lồng nhau, generic type argument): không `BigDecimal`, `Money`, `AuthenticatedCustomerRef`, `Map`, `Object`, `JsonNode`, entity; field số (`int/long/double/Integer/...`) chỉ được phép cho cặp `Type.field` đã duyệt, tính theo package gốc (`gateway.api.TechnicalContext.requestedQuantity`, `gateway.api.QuoteRequestResult.lineCount`). Bổ sung SEC-01a: ARCH-05b (hình dạng đóng), 05c (annotation Jackson), 05d (tên field miễn quét chỉ trên cặp đã duyệt), 05e (`@JsonTypeInfo` chỉ `Id.NAME`, property `type`, kể cả trên field) | SOURCE FACT rule 30 §1; DESIGN PROPOSAL |
| ARCH-06 | Mọi record trong `gateway.api..`, `tools.api.dto..` có `@EgressClassified(APPROVED_TECHNICAL)` | DESIGN PROPOSAL ADR-003 |
| ARCH-07 | JPA `@Entity` chỉ trong `..internal..` của module sở hữu | SOURCE FACT |
| ARCH-08 | `AuthenticatedCustomerRef` chỉ được khởi tạo trong `identity..`; controller không có tham số kiểu `AuthenticatedCustomerRef`; không tham số/field body (kể cả lồng nhau, mảng, tên `@JsonProperty`/`@JsonAlias`) mang định danh khách (`customer`/`tenant`/`account`/`buyer`/`user`/`khachHang` + `Id`/`Ref`/`Code`/`No`/`Number`/`Key`); không `Map`, `HttpServletRequest`, `HttpHeaders`, `WebRequest`, `HttpEntity`, `InputStream`/`Reader`, `JsonNode`, `byte[]`. Principal phía server (`@AuthenticationPrincipal`, `Principal`, `Authentication`) và `@ExceptionHandler`/`@InitBinder` được miễn | SOURCE FACT; review: chặn theo tên dễ bị né |
| ARCH-09 | Port ERP không có phương thức `getCost`, `list*Customers`, `approve*`, `updatePrice` | SOURCE FACT rule 20 |
| ARCH-10 | `audit` không phụ thuộc module nghiệp vụ | DESIGN PROPOSAL |
| ARCH-11 | Không `Method.invoke`/reflection gọi tool trong `tools..` | SOURCE FACT rule 20 |
| ARCH-12 | `catalog..` chỉ phụ thuộc `AvailabilityBucketPort` (enum), không `quote.spi.AvailabilityPort` | review F6 |
| ARCH-13 | Modulith `verify()` không có cycle | review |
| ARCH-08d | `identity.spi.ErpCustomerLink` chỉ dùng trong identity, quote, order, erp (mọi kiểu phụ thuộc, kể cả method reference). `order` thêm ở EP-07: đơn nháp phải nêu khách ERP (A-41(a), chờ owner xác nhận) | A-19, EP-06, EP-07 |
| ARCH-08e | `identity.CustomerLinks` (đọc liên kết khách) chỉ dùng trong identity, quote | EP-06 review vòng 2 |
| ARCH-08f | `identity.CustomerLinkBinding` (tạo ref từ mã ERP) chỉ dùng trong identity | EP-06 review vòng 2 |
| ARCH-13a | `QuoteCard`/`QuoteCards` (có giá) chỉ dùng trong quote và conversation (render) | EP-06 architecture review M1 |
| ARCH-13b | `Money` chỉ dùng trong platform, quote, order, erp, conversation | EP-06 architecture review M2 |
| ARCH-13c | quote chỉ dùng `Sku` từ `catalog.spi` | EP-06 architecture review L2 |

## 4. Giao tiếp
- Đồng bộ qua `api` (typed command/result); spi do module sở hữu định nghĩa và erp hiện thực.
- Sự kiện (audit, handoff) qua Spring application events; quyết định egress **không** qua event mà qua lời gọi đồng bộ `audit.EgressAuditLog` (thiếu listener/bean không được phép làm mất bản ghi, review SEC-01a vòng 2); outbox cho thao tác ERP stateful (rule 20; ADR-009).
- Mỗi module sở hữu bảng của mình; không đọc bảng chéo.

## 5. Bằng chứng cần có (NOT RUN)
`ModuleStructureTest` (Modulith verify) + `ArchitectureRulesTest` (ARCH-01..13); mỗi rule có fixture vi phạm cố ý (vd DTO có `long unitPrice`, `tools` inject `PricingPort`) chứng minh rule bắt được — chạy trong EP-00.

## 6. Ghi chú triển khai POC-001 (2026-11-13)
- `bootstrap` là root package `com.secureerp.agent`, vì Modulith yêu cầu lớp `@SpringBootApplication` nằm ở root.
- §2 ghi "mọi module → identity.api…". Để tránh vòng identity ↔ audit, `audit` **không** phụ thuộc `identity`, chỉ phụ thuộc `platform`; actor truyền vào audit dưới dạng chuỗi pseudonymous. Đây là DESIGN PROPOSAL, chờ ký ADR-001.
- Đã cưỡng chế: allowlist module (Modulith `allowedDependencies`), ARCH-01/02/03/04/07/09/10/11/12/13. Hoãn sang SEC-01a: ARCH-05/06/08.

## 7. Ghi chú triển khai SEC-01a (2026-11-18)
- ARCH-05/06/08 đã cưỡng chế, mỗi rule có fixture vi phạm cố ý. ARCH-05 viết dạng **allowlist** (chặt hơn bảng §3): field egress chỉ được là String, boolean, enum/record trong package egress, List/Set/Optional của các kiểu đó, hoặc `int/Integer` có tên trong allowlist số; generic argument được kiểm tra đệ quy.
- `@EgressClassified` và `DataClassification` nằm ở `platform` (không phải `policy`) để `tools.api.dto` dùng được mà không thêm phụ thuộc `tools → policy`. DESIGN PROPOSAL.
- Named interface Modulith (review M2): `catalog :: spi`, `gateway :: api`. `erp` được phép `catalog :: spi`. Khi `agent` dùng `gateway.api` (EP-04) cần thêm `gateway :: api` vào allowlist của `agent`.
- `gateway` phụ thuộc `audit` chỉ để gọi port `audit.EgressAuditLog` với `audit.EgressDecisionEvent` (định nghĩa trong audit vì ARCH-10; review vòng 2 thay event bằng lời gọi trực tiếp).
- Sau review SEC-01a: `gateway` bỏ `identity` khỏi allowlist (không dùng); `erp` chỉ còn `catalog :: spi` (bỏ `catalog` thường). Review vòng 2 (L2): `erp` bỏ luôn `quote`, `order`, `identity` (chưa dùng; cho phép gọi API của chúng tạo vòng logic). Allowlist `erp` = `catalog :: spi`, `platform`, `audit`; thêm `quote :: spi`/`order :: spi` khi các spi đó được tạo.
- Rule bổ sung: ARCH-05b (kiểu egress chỉ là record, enum, sealed interface hoặc utility final chỉ-static; chặn kế thừa, interface mở, class thường), ARCH-05c (không annotation Jackson trừ `@JsonTypeInfo`/`@JsonSubTypes`), ARCH-06 khớp tên đầy đủ `platform.EgressClassified`, ARCH-08b dùng meta-annotation + regex rộng (header `X-Customer-Id`, `custId`, `customerCode`), ARCH-08c (type trong `..adapter.web..` không có field `AuthenticatedCustomerRef`/`customerId`). Review vòng 2: ARCH-08b thêm tên `customer`/`account`/`buyer`/`user`/`khachHang` + hậu tố `Number`/`Key`, cấm tham số `Map`/`MultiValueMap`/`HttpHeaders`/`HttpServletRequest`/`WebRequest`/`HttpEntity`, và quét đệ quy mọi type đạt tới từ tham số controller (kể cả ngoài `adapter.web`); ARCH-05d (tên field miễn quét chỉ trên cặp `Type.field` đã duyệt); ARCH-05e (`@JsonTypeInfo` chỉ `Id.NAME`). `ArchitectureRulesTest` kiểm tra fixture hợp lệ tồn tại (L4); `EgressEnumAlignmentTest` giữ `Provenance`/`AvailabilityBucket` của `catalog.spi` và `gateway.api` đồng nhất (L3). Allowlist số theo cặp `Type.field`: `TechnicalContext.requestedQuantity`, `QuoteRequestResult.lineCount` (bỏ `quantity` trần).
- `EgressCaptureListener` là hook chỉ dành cho test; `ArchitectureRulesTest` cấm class production hiện thực nó.

## 8. Ghi chú triển khai EP-03 (2026-11-18)
- `tools` allowlist: `catalog`, `catalog :: spi`, `gateway :: api` (A-20), `identity`, `platform` (`quote` và `audit` bỏ vì chưa dùng; `quote` thêm lại ở EP-06; khi đó tools đổi productRef → SKU, quote không đọc `product_ref`). EP-04 cần: `agent` thêm `gateway :: api`, `conversation` thêm `tools :: spi`, identity mở API kiểu `CurrentPrincipalProvider` để tạo `PrincipalId`. `tools` không phụ thuộc `conversation` (tránh vòng conversation → agent → tools): quyền truy cập conversation đi qua `tools.spi.ConversationAccessPort` (named interface `tools :: spi`), do `conversation` hiện thực ở EP-04.
- Kiểu mới: `platform.ConversationId` (shared kernel), `identity.PrincipalId` (pseudonym, chỉ identity tạo được), `identity.Role`, `identity.Channel`, `catalog.CatalogSearch`/`CatalogMatch` (API của catalog), `gateway.api.ToolErrorResult`.
- `tools` sở hữu bảng `tool_invocation` và `product_ref` (Flyway V4).

## 9. Ghi chú triển khai EP-05 (2026-11-18)
- Không thêm cạnh phụ thuộc mới. `catalog` sở hữu `catalog.spi.Vin` (BR-04, quy tắc check digit A-27), `ExactProductQuery.ByVin`, API `VehicleContext`/`VinIntake` và `CatalogSearch.searchByConversationVin`, bảng `conversation_vin` (Flyway V5, A-28).
- `CatalogSearch.search` từ chối `ByVin`: truy vấn VIN chỉ được dựng từ VIN đã lưu của conversation. Kết quả theo VIN bị catalog giới hạn ở `CATALOG_LOOKUP`, không phụ thuộc adapter.
- `policy` vẫn phát hiện VIN để mask/chặn ở egress và inbound; `catalog.internal.VinExtractor` dùng cùng quy tắc nhóm ký tự (parity kiểm ở `VinSearchIT`). Hai bộ phát hiện song song là nợ kỹ thuật: hợp nhất thành một span finder dùng chung là đề xuất mở (xem A-29).
- Người gọi `VehicleContext.register` lúc nhận tin là EP-04 (A-29): `conversation` cần thêm `catalog` vào allowlist, đề xuất một named interface hẹp (`catalog :: intake`, chỉ `VehicleContext` + `VinIntake`) để không lộ `CatalogSearch`.

## 10. Ghi chú triển khai EP-06 (2026-11-18/19)
- API thực tế: `quote.QuoteService` (thay tên dự kiến `QuoteApi`), `quote.QuoteCards` (thay `QuoteHandleService`), `quote.QuoteRequest`/`QuoteOutcome`/`QuoteCard`; `quote :: spi` (named interface) gồm `PricingPort`, `AvailabilityPort`, `CreditEligibilityPort`, `PriceQuote`, `EligibilityStatus`, `QuoteSourceUnavailableException`.
- Allowlist mới: `quote` thêm `identity :: spi`, `catalog :: spi` và bỏ `order` (chưa dùng; thêm lại ở EP-07); `erp` thêm `quote :: spi`, `identity :: spi` (không phải toàn bộ `identity`: §7 L2 vẫn giữ); `tools` thêm `quote` (ARCH-13a chặn `QuoteCard`/`QuoteCards`).
- `identity`: `CustomerLinks` (đọc, ARCH-08e) + `CustomerLinkBinding` (tạo ref, ARCH-08f) + `identity :: spi` `ErpCustomerLink`; `JdbcCustomerLinks` (`@Component`) nằm trong package `identity` (package-private) vì chỉ package này tạo được `AuthenticatedCustomerRef` (ARCH-08a) — ngoại lệ có chủ đích với quy ước "hiện thực nằm trong internal" (ADR-001). Bảng `customer_link` (Flyway V7) thuộc identity; EP-04 thêm cột binding (A-37).
- `platform.Money` (DESIGN PROPOSAL, A-36d): kiểu RESTRICTED khi là giá khách; ARCH-13b giới hạn module dùng; ARCH-05 vẫn cấm trong egress.
- Bảng của quote (Flyway V6): `quote`, `quote_line`, `quote_handle`, `quote_request`; khóa advisory namespace `30_006` (tools dùng `30_003`), theo thứ tự khách rồi conversation, `lock_timeout` 5 s. Khóa/giới hạn A-34 theo khách (`quote_request.customer_digest`).
- `tools.spi.ConversationAccessPort.boundCustomer` (A-35): EP-04 hiện thực; mặc định không có khách gắn → không báo giá.

## EP-04 (2026-11-19)
- Allowlist: `conversation` = agent, handoff, quote, identity, policy (A-21), `catalog :: intake` (A-29; `@NamedInterface("intake")` trên `VehicleContext`, `VinIntake` và các record lồng của nó), `tools :: spi`, platform, audit. `agent` thêm `gateway :: api`.
- `identity`: `SecurityConfiguration`, `SessionIdentities`, `SessionLimitsFilter`, `JdbcPrincipals`, `DemoProfileGuard` nằm trong package gốc `identity` (package-private) vì chỉ package này tạo được `PrincipalId`/`AuthenticatedCustomerRef` — cùng ngoại lệ có chủ đích như `JdbcCustomerLinks` (§10). Controller phiên ở `identity.adapter.web`.
- `conversation`: API `Chat` + `ChatTurn` ở package gốc; hiện thực ở `conversation.internal`; controller và `RequestSizeFilter` ở `conversation.adapter.web` (profile demo, ADR-015).
- ARCH-14: chỉ gateway, agent, tools phụ thuộc `ModelResponse`/`ToolProposal` (thay cho A-22 trong lúc chờ).
- Advisory lock: 30_010 (tiếp nhận lượt theo conversation), 30_011 (tạo conversation theo principal); tools 30_003, quote 30_006.

## EP-07 (2026-11-19)
- API thực tế: `quote.QuoteConfirmations` (xác nhận + xem báo giá của chính khách), `order.DraftOrders` (`register` trong transaction xác nhận, `dispatch` ngoài transaction, `stateOf`; thay tên dự kiến `OrderApi.submit`), `order.OrderReconciliation` (SALES), `order :: spi` (`DraftOrderPort` và các kiểu của nó).
- Allowlist: `quote` thêm `order`; `order` = identity, `identity :: spi` (chỉ `ErpCustomerLink`), platform, audit; `erp` thêm `order :: spi`. `conversation` không đổi (đã có quote, handoff).
- Sự kiện đồng bộ, xử lý trong chính transaction phát ra nó: `order.DraftOrderStateChanged` (order → quote, khi đối soát hoặc request quá hạn) và `quote.QuoteStaffRequired` (quote → conversation: khóa bot + `Handoffs.open` cùng transaction với thay đổi của báo giá). Trên đường xác nhận, quote phản chiếu trạng thái từ giá trị trả về của `dispatch` và tự sửa khi đọc lại (`draft_order_request` là nguồn sự thật, ADR-007). Listener của quote ném `order.DraftOrderMirrorRefused` (rollback thay đổi của order; đối soát trả 409) khi báo giá tồn tại nhưng không chuyển được sang trạng thái đích (`DraftOrdersIT.aReconciliationTheQuoteCannotFollowIsRolledBack`). Request quá hạn được xử lý từng cái trong transaction riêng: một listener lỗi không chặn các request khác.
- Endpoint: `quote.adapter.web.QuoteController` (`GET /api/demo/quotes/{ref}`, `POST …/confirm`), `order.adapter.web.OrderReconciliationController` (`/api/internal/orders/unknown`, `…/{requestId}/reconcile`), cả hai profile demo. `conversation.adapter.web.RequestSizeFilter` nay áp cho cả `/api/internal/*` (A-41(n): nên chuyển sang platform).
- Bảng (Flyway V10): quote thêm `quote_confirmation`, cột `confirm_token_hash`, `confirmed_at`, `UNIQUE(public_ref, version)`; order sở hữu `draft_order_request`, `draft_order_event`. Khóa advisory: quote vẫn dùng namespace `30_006` cho mọi lần ghi rời khỏi ISSUED và cho việc giữ chỗ lần thử xác nhận.
- Còn mở: A-41 (k)–(v).
