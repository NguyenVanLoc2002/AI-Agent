# System architecture — Security-first PoC

> DRAFT 2026-11-13 — toàn bộ tài liệu này là **DESIGN PROPOSAL** trừ khi ghi khác. Chưa có mã; không mục nào VERIFIED.
> Nguồn nghiệp vụ: `docs/01_BRD_SRS_Security.md@af2d09f` (BRD), `docs/TRACEABILITY.md`. Quyết định kiến trúc chi tiết: `docs/architecture/adr/`.

## 1. Mục tiêu và phạm vi
- **SOURCE FACT (BRD §01):** PoC chứng minh tách dữ liệu nhạy cảm khỏi LLM, tool security, báo giá và đơn nháp chống trùng trên Mock ERP. MVP: VIN/OE, ảnh phụ tùng, Arito, Zalo OA, giao diện nhân viên. Ngoài MVP: phân tích lịch sử, KPI, dự báo.
- **SOURCE FACT (BRD §14):** Gates G0 (schema + phân loại dữ liệu) → G1 (Mock ERP + egress policy + SEC-01) → G2 (quote + idempotency) → G3 (SEC-01..07 PASS) → G4 (xác nhận Arito/catalog/Zalo/legal) → G5 (UAT/go-live).

| PoC (G1–G3, synthetic) | MVP (sau G4) | Ngoài MVP |
|---|---|---|
| Web chat simulator, khách đã liên kết seed sẵn | Zalo OA (D-06), xác minh SĐT khách chưa liên kết | Phân tích lịch sử, KPI, dự báo |
| Mock Arito với sentinel | Arito adapter thật (D-01..D-03) | |
| AI Gateway + egress guard + **Fake LLM provider** | Provider thật (D-07) | |
| Tool registry, catalog giả lập, VIN validator | Catalog VIN/OE có bản quyền (D-05), OCR, ảnh | |
| Quote versioned + opaque token + render server-side | Chính sách giá/VAT/đa kho (D-04) | |
| Draft order idempotent + UNKNOWN + đối soát thủ công | Đối soát theo external ref thật (D-03) | |
| Handoff, staff inbox tối thiểu, RBAC 4 vai trò | Assignment, trưởng bộ phận, break-glass (D-08) | |
| Audit, kill switch, key revocation | Retention/backup production (D-09) | |

## 2. Kiểu kiến trúc
- **SOURCE FACT (CLAUDE.md; BRD §01; tham chiếu trang sơ bộ trong TRACEABILITY pp.3–4 — chưa đối chiếu được bản gốc):** modular monolith, một AI Gateway duy nhất; Java 21, Spring Boot, PostgreSQL/Flyway, JUnit 5, Testcontainers là *preferred stack*.
- **DESIGN PROPOSAL (ADR-001, ADR-002):** Spring Boot 3.x, Spring Modulith + ArchUnit kiểm tra ranh giới trong build; hexagonal trong từng module (`api` / `internal` / `adapter`). Build tool: OPEN (A-04).
- **DESIGN PROPOSAL:** Frontend React + TypeScript (Vite), typed client sinh từ OpenAPI, cookie session HttpOnly (ADR-010). Chỉ triển khai sau khi wireflow được duyệt (EP-10).

## 3. Context diagram (logical)
```
[Browser: Customer chat | Staff inbox | Security inspector | Ops]
          │ HTTPS, session cookie + CSRF
          ▼
┌───────────────────────── Spring Boot modular monolith ─────────────────────────┐
│ conversation → agent → tools → catalog / quote → order                         │
│                  │                                                              │
│                  └→ gateway ─(EgressGuard, policy)→ [EgressTransport] ─────────┼──► egress proxy ──► LLM provider
│ identity · handoff · audit · securityinspector · platform(KillSwitch)          │     (PoC: WireMock / Fake)
│ erp (leaf adapter) ── implements spi ── Mock Arito (schema mock_erp)           │
│ bootstrap (wiring)                                                             │
└───────────────────────────────────┬────────────────────────────────────────────┘
                                    ▼
                               PostgreSQL
```
- Chỉ `gateway.adapter` có đường ra provider; mọi egress khác (Zalo, VIN service, APM) là MVP và phải đi qua review riêng (skill `privacy-egress-review`).
- **DESIGN PROPOSAL (ADR-016):** network egress chỉ qua egress proxy; PoC mô phỏng bằng proxy local + WireMock. Kiểm soát mạng production nằm **ngoài** ứng dụng (rule 10).

## 4. Module (tóm tắt — chi tiết `MODULE_BOUNDARIES.md`)
identity, conversation, agent, gateway, policy, tools, catalog, quote, order, handoff, audit, securityinspector, platform, erp, bootstrap. Controller nằm trong `adapter.web` của module sở hữu (không có module `web`).
- **SOURCE FACT (CLAUDE.md):** identity, conversation, agent, gateway, tools, catalog, erp, quote, order, audit.
- **DESIGN PROPOSAL (architecture-reviewer 2026-11-13):** thêm handoff, policy, securityinspector, platform, bootstrap để mọi endpoint/control có module sở hữu và không có vòng phụ thuộc.

## 5. Nguyên tắc bảo mật kiến trúc
| Nguyên tắc | Nhãn | Ở đâu |
|---|---|---|
| LLM chỉ nhận dữ liệu APPROVED_TECHNICAL qua DTO allowlist | SOURCE FACT (CLAUDE.md) | gateway, `docs/contracts/AI_GATEWAY.md` |
| Boundary chính là **type-level** (DTO không có field tiền/ID); DLP scan là lớp phụ | DESIGN PROPOSAL (ADR-003) | gateway, policy, ArchUnit |
| Tool call là đề xuất; validate + authorize mỗi lần | SOURCE FACT | tools, `TOOL_CONTRACTS.md` |
| Giá/tồn/công nợ chỉ trong quote/order | SOURCE FACT | quote, `ERP_CONNECTOR.md` |
| Quote render server-side sau khi model xong | SOURCE FACT | quote, conversation |
| Fail-closed + handoff khi DLP nghi ngờ, provider lỗi, thiếu nguồn | SOURCE FACT | gateway, handoff |
| Không tin prompt như ranh giới ủy quyền | SOURCE FACT | toàn hệ thống |

## 6. Dữ liệu (tóm tắt) — DESIGN PROPOSAL
Schema ứng dụng `app` và schema giả lập `mock_erp` tách biệt; Flyway forward-only. Bảng chính: customer_link, app_user, conversation, message, quote, quote_line, quote_handle, draft_order_request, handoff, audit_event, egress_record, feature_switch. Chi tiết và ràng buộc: `docs/contracts/ERP_CONNECTOR.md` §5 và `DATA_FLOW.md` §5. Gợi ý BRD §11 (SOURCE FACT, mức đề xuất) được giữ tên thực thể tương ứng.

## 7. State machines — DESIGN PROPOSAL, tên trạng thái OPEN (A-03, ADR-007)
- **Mâu thuẫn nguồn:** BRD §06 `DRAFT→SENT→ACCEPTED→DRAFT_ORDER_CREATED→HUMAN_APPROVED/REJECTED`; rule 40 `DRAFT→ISSUED→CONFIRMED→DRAFT_ORDER_CREATED→STAFF_APPROVED/REJECTED`.
- **Đề xuất (theo rule 40 + trạng thái thiếu):**
  - Quote: `DRAFT → ISSUED → CONFIRMED → ORDER_SUBMITTING → DRAFT_ORDER_CREATED → STAFF_APPROVED | STAFF_REJECTED`; `ISSUED → EXPIRED | SUPERSEDED | CANCELLED | CONFIRMATION_BLOCKED` (recheck tồn/công nợ không đạt khi xác nhận → handoff); `ORDER_SUBMITTING → ORDER_OUTCOME_UNKNOWN → DRAFT_ORDER_CREATED | ORDER_FAILED` (đối soát/nhân viên); `ORDER_SUBMITTING → ORDER_FAILED`.
  - `STAFF_APPROVED/STAFF_REJECTED`: phê duyệt diễn ra trong Arito (SOURCE FACT); việc đọc ngược trạng thái duyệt từ Arito **OPEN D-01**. PoC: SALES ghi nhận thủ công qua API nội bộ.
  - DraftOrderRequest (order, nguồn sự thật về kết quả ERP): `PENDING → SUBMITTED → CREATED | REJECTED | OUTCOME_UNKNOWN`; `OUTCOME_UNKNOWN → RECONCILED_CREATED | RECONCILED_ABSENT`. Quote phản chiếu: CREATED/RECONCILED_CREATED ⇒ `DRAFT_ORDER_CREATED`; REJECTED/RECONCILED_ABSENT ⇒ `ORDER_FAILED`; OUTCOME_UNKNOWN ⇒ `ORDER_OUTCOME_UNKNOWN`.
  - Conversation: `ACTIVE_BOT → HANDOFF_PENDING → HUMAN_ACTIVE → ACTIVE_BOT (chỉ khi resolve) | CLOSED`.
  - Handoff: `OPEN → CLAIMED → RESOLVED | CANCELLED`.
- Mọi transition qua guard tường minh + optimistic version; transition sai → lỗi có mã.

## 8. Demo API (không phải Arito API) — DESIGN PROPOSAL dựa trên BRD §12
| Method/Path | Vai trò | Ghi chú |
|---|---|---|
| `POST /api/demo/conversations` | CUST | tạo/khôi phục theo principal |
| `POST /api/demo/conversations/{id}/messages` `{clientEventId, text}` | CUST (owner) | `423` khi bot khóa |
| `GET /api/demo/quotes/{id}` | CUST owner, SALES được giao | nơi duy nhất (cùng quote card) có giá |
| `POST /api/demo/quotes/{id}/confirm` `{version, confirmationToken}` | CUST owner | module quote; idempotency server-side theo quoteId:version; `409` stale/repriced, `410` expired, `404` không phải chủ; rate limit |
| `GET /api/internal/handoffs`; `POST /api/internal/handoffs/{id}/claim`, `/resolve` | SALES | BRD §12 có `claim`; `resolve` là bổ sung |
| `POST /api/internal/conversations/{id}/messages` | SALES (đã claim, HUMAN_ACTIVE) | bổ sung; tin direction STAFF |
| `GET /api/internal/orders/unknown`; `POST /api/internal/orders/{requestId}/reconcile` `{outcome}` | SALES | bổ sung; ghi audit đối soát |
| `POST /api/internal/quotes/{id}/staff-decision` `{APPROVED|REJECTED}` | SALES | bổ sung PoC; thay cho đọc ngược Arito (OPEN D-01) |
| `GET /api/demo/security/traces/{traceId}` | SECREV | **giữ đường dẫn BRD §12**; chỉ field names, policy codes |
| `GET /api/demo/security/sec-results` | SECREV | dashboard SEC (UI persona 3); bổ sung |
| `PUT /api/ops/switches/ai` | OPS | bổ sung; audit; hiệu lực ≤60s |
- Đường dẫn trong BRD §12 là SOURCE FACT ở mức "dự kiến"; các dòng ghi "bổ sung" là DESIGN PROPOSAL.
- `/api/demo/**` chỉ bật ở profile `local`/`demo` (ADR-015). Lỗi theo RFC 7807 với `code` ổn định, không stack/nội dung. OpenAPI chốt sau khi owner nghiệp vụ duyệt DTO (SOURCE FACT BRD §12).

## 9. NFR
| NFR | Nhãn | Ghi chú |
|---|---|---|
| Deny-by-default, least privilege, không secrets trong git/log/prompt, TLS, backup mã hóa, key sandbox riêng | SOURCE FACT (BRD §10) | |
| Audit có cấu trúc, không body payload | SOURCE FACT (BRD §10) | `ADR-012` |
| Retry chỉ với tác vụ idempotent; circuit breaker; handoff fallback | SOURCE FACT (BRD §10) | |
| RPO 24h / RTO 4h | OPEN (BRD §10: mục tiêu đề xuất, cần chủ hệ thống xác nhận) | |
| Latency, concurrency, token budget | OPEN — đo trong PoC, không đặt SLA (BRD §10) | |

## 10. Những gì kiến trúc này KHÔNG khẳng định
- Arito có REST API, sandbox, API đơn nháp, field-level permission, idempotency, lookup theo external ref: **OPEN** (D-01..D-03).
- Provider LLM có ZDR/region phù hợp: **OPEN** (D-07). `store=false` ≠ ZDR (rule 30).
- Catalog VIN/OE có bản quyền: **OPEN** (D-05).
- Kết quả PoC với Fake provider không chứng minh kiểm soát mạng hay retention của provider thật.
