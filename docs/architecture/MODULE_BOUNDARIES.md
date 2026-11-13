# Module boundaries & dependency rules

> DESIGN PROPOSAL (ADR-001, ADR-005) — 2026-11-13, sửa sau architecture review cùng ngày. Enforcement đề xuất: Spring Modulith `ApplicationModules.verify()` + ArchUnit trong `test` (build fail khi vi phạm). Chưa VERIFIED.
> Module gốc theo CLAUDE.md (SOURCE FACT): identity, conversation, agent, gateway, tools, catalog, erp, quote, order, audit. Module bổ sung (DESIGN PROPOSAL): policy, handoff, securityinspector, platform, bootstrap.
> `web` **không** phải module: controller nằm trong `adapter.web` của module sở hữu endpoint.

## 1. Trách nhiệm
| Module | Trách nhiệm | Sở hữu port / API | Lớp dữ liệu được chạm |
|---|---|---|---|
| identity | Principal, vai trò, liên kết kênh → `AuthenticatedCustomerRef` | `CurrentPrincipalProvider`, `CustomerLinkPort` (spi) | RESTRICTED (identity) — không xuất giá trị thô |
| conversation | Endpoint chat, dedup, trạng thái hội thoại (kể cả `bot_locked`), ghép response | `ChannelPort` | INTERNAL, sanitized text |
| agent | Vòng lặp agent: dựng `ModelRequest`, history summary, gọi ToolManager, OutputValidator | `AgentApi.handleTurn(...)` | APPROVED_TECHNICAL |
| gateway | Duy nhất gọi provider: serialize, EgressGuard, transport, timeout/breaker | `ModelProviderPort`, `EgressTransport` | APPROVED_TECHNICAL |
| policy | Phân loại, DLP inbound/outbound, VIN extractor/validator, honeytoken registry, normalizer | `EgressPolicy`, `InboundSanitizer` | đọc để **chặn**; không lưu giá trị |
| tools | Registry, schema, authz per call, quota, mapping kết quả sang DTO an toàn | `ToolRegistry`, `ToolAuthorizationPolicy` | APPROVED_TECHNICAL |
| catalog | Tra cứu sản phẩm, provenance, bucket tồn thô | `VerifiedProductPort` (spi), `AvailabilityBucketPort` (spi, chỉ enum) | APPROVED_TECHNICAL |
| quote | Giá, tồn, eligibility, quote/version, handle, render, **endpoint confirm** | `quote.spi`: `PricingPort`, `AvailabilityPort`, `CreditEligibilityPort`; `QuoteApi`, `QuoteHandleService` | RESTRICTED, INTERNAL |
| order | Idempotency, draft order, UNKNOWN, đối soát | `order.spi.DraftOrderPort`; `OrderApi.submit(quoteId, version)` | RESTRICTED, INTERNAL |
| handoff | Queue, reason code, claim/resolve; publish `HandoffOpened/Resolved` | `HandoffApi` | INTERNAL |
| audit | Sink sự kiện field-names-only **và** chủ sở hữu bảng `egress_record` | `AuditSink`, `AuditQueryApi` (read) | metadata |
| securityinspector | Đọc audit/egress qua `AuditQueryApi` cho SECREV | — | metadata |
| platform | KillSwitch, Clock, correlation ID, error mapping | `KillSwitch`, `Clock` | — |
| erp | **Leaf adapter:** Mock Arito (PoC), Arito thật (MVP, gated) hiện thực các spi | — | RESTRICTED (strip tại biên) |
| bootstrap | Ứng dụng Spring Boot + wiring; module duy nhất được phụ thuộc mọi module | — | — |

## 2. Hướng phụ thuộc cho phép
```
conversation ─► agent ─► gateway ─► policy
      │           └────► tools ─► catalog
      │                    └────► quote (QuoteApi.issueQuote → QuoteHandle)
      ├─► handoff (HandoffApi.open)          handoff ──(event HandoffOpened/Resolved)──► conversation (listener)
      └─► quote (render quote card)
quote ─► order (OrderApi.submit)
gateway ──(event EgressDecision)──► audit       securityinspector ─► audit (AuditQueryApi)
mọi module ─► identity.api, platform.api, audit.api (publish)
erp ─► (implements) identity.spi, catalog.spi, quote.spi, order.spi
bootstrap ─► mọi module (wiring)
```
- Không vòng: handoff không gọi conversation trực tiếp; khóa bot qua event. gateway không ghi bảng audit; publish event.

## 3. Quy tắc (mỗi quy tắc = một ArchUnit/Modulith test, ID ARCH-xx)
| ID | Quy tắc | Lý do / nguồn |
|---|---|---|
| ARCH-01 | Chỉ `gateway.adapter..` được import SDK/HTTP client provider | SOURCE FACT CLAUDE.md |
| ARCH-02 | **Allowlist:** chỉ `quote..`, `order..`, `erp..`, `bootstrap..` được phụ thuộc `quote.spi..`; chỉ `order..`, `erp..`, `bootstrap..` được phụ thuộc `order.spi..` | SOURCE FACT giá/công nợ nội bộ; review: denylist không đủ |
| ARCH-03 | Không module nào import `erp..` trừ `bootstrap` | DESIGN PROPOSAL |
| ARCH-04 | Chỉ `conversation..` (và `bootstrap`) được phụ thuộc `agent..` | DESIGN PROPOSAL |
| ARCH-05 | Kiểu trong `gateway.api..` và `tools.api.dto..` (kể cả field lồng nhau, generic type argument): không `BigDecimal`, `Money`, `AuthenticatedCustomerRef`, `Map`, `Object`, `JsonNode`, entity; field số (`int/long/double/Integer/...`) chỉ được phép nếu có trong allowlist tên (`requestedQuantity`, `lineCount`, `quantity`) | SOURCE FACT rule 30 §1; DESIGN PROPOSAL |
| ARCH-06 | Mọi record trong `gateway.api..`, `tools.api.dto..` có `@EgressClassified(APPROVED_TECHNICAL)` | DESIGN PROPOSAL ADR-003 |
| ARCH-07 | JPA `@Entity` chỉ trong `..internal..` của module sở hữu | SOURCE FACT |
| ARCH-08 | `AuthenticatedCustomerRef` chỉ được khởi tạo trong `identity..`; controller không có tham số kiểu `AuthenticatedCustomerRef`; (lớp phụ) không tham số tên `customerId`/`tenantId` | SOURCE FACT; review: chặn theo tên dễ bị né |
| ARCH-09 | Port ERP không có phương thức `getCost`, `list*Customers`, `approve*`, `updatePrice` | SOURCE FACT rule 20 |
| ARCH-10 | `audit` không phụ thuộc module nghiệp vụ | DESIGN PROPOSAL |
| ARCH-11 | Không `Method.invoke`/reflection gọi tool trong `tools..` | SOURCE FACT rule 20 |
| ARCH-12 | `catalog..` chỉ phụ thuộc `AvailabilityBucketPort` (enum), không `quote.spi.AvailabilityPort` | review F6 |
| ARCH-13 | Modulith `verify()` không có cycle | review |

## 4. Giao tiếp
- Đồng bộ qua `api` (typed command/result); spi do module sở hữu định nghĩa và erp hiện thực.
- Sự kiện (audit, handoff, egress decision) qua Spring application events; outbox cho thao tác ERP stateful (rule 20; ADR-009).
- Mỗi module sở hữu bảng của mình; không đọc bảng chéo.

## 5. Bằng chứng cần có (NOT RUN)
`ModuleStructureTest` (Modulith verify) + `ArchitectureRulesTest` (ARCH-01..13); mỗi rule có fixture vi phạm cố ý (vd DTO có `long unitPrice`, `tools` inject `PricingPort`) chứng minh rule bắt được — chạy trong EP-00.

## 6. Ghi chú triển khai POC-001 (2026-11-13)
- `bootstrap` là root package `com.secureerp.agent`, vì Modulith yêu cầu lớp `@SpringBootApplication` nằm ở root.
- §2 ghi "mọi module → identity.api…". Để tránh vòng identity ↔ audit, `audit` **không** phụ thuộc `identity`, chỉ phụ thuộc `platform`; actor truyền vào audit dưới dạng chuỗi pseudonymous. Đây là DESIGN PROPOSAL, chờ ký ADR-001.
- Đã cưỡng chế: allowlist module (Modulith `allowedDependencies`), ARCH-01/02/03/04/07/09/10/11/12/13. Hoãn sang SEC-01a: ARCH-05/06/08.
