# Implementation plan & traceability — v0.2 DRAFT

> 2026-11-13 (v0.2 sau architecture + security review cùng ngày). Backlog gốc: `docs/04_Implementation_Backlog.md@af2d09f` — EP-01..EP-12 là SOURCE FACT và **giữ nguyên nghĩa**; issue mới dùng ID mới (G0-x, EP-00, SEC-01a/b, ACC-01). Thứ tự và tách issue: DESIGN PROPOSAL. **Trạng thái mọi test: NOT RUN** (chưa có mã).

## 1. Gates (SOURCE FACT BRD §14)
G0 schema + phân loại dữ liệu → G1 Mock ERP + egress policy + SEC-01 → G2 quote + idempotency → G3 SEC-01..07 PASS → G4 xác nhận ERP đích/catalog/Zalo/legal → G5 UAT/go-live.

## 2. Backlog theo thứ tự dependency
| # | Issue | Epic gốc | Gate | Phụ thuộc | Đầu vào | Đầu ra | Test chính | DoD bổ sung |
|---|---|---|---|---|---|---|---|---|
| 1 | G0-1 Chuẩn hóa tài liệu kỹ thuật (issue này) | — | G0 | — | BRD@af2d09f, rules | docs + ADR PROPOSED | — | review architecture-reviewer + security-auditor (đã chạy 2026-11-13) |
| 2 | G0-2 Phê duyệt ADR + A-01..A-15 (**một phần 2026-11-18**: ADR-001/002/003/004/016, A-04/05/09/13 ACCEPTED/APPROVED) | — | G0 | 1 | ADR PROPOSED | ADR ACCEPTED/REJECTED | — | owner ký trong ADR |
| 3 | EP-00 Bootstrap backend (= **POC-001**, đã triển khai 2026-11-13; ARCH-05/06/08 hoãn sang SEC-01a) | — | G1 | 2 | ADR-001/002 | build, skeleton, Flyway baseline, Testcontainers, Modulith + ARCH-01..13 | `ModuleStructureTest`, `ArchitectureRulesTest` (+ fixture vi phạm cố ý) | không entity nghiệp vụ, không secrets |
| 4 | **SEC-01a** Egress guard — mức gateway (**merged 2026-11-18, PR #6**; gateway-level evidence, xem SESSION_HANDOFF) | EP-01, EP-02 | G1 | 3 | AI_GATEWAY, DATA_CLASSIFICATION, ERP_CONNECTOR | identity tối thiểu; Mock ERP sentinel + fault injection; catalog spi + adapter mapping; policy; gateway + Fake/WireMock + proxy local; harness dựng `ModelRequest` trực tiếp | T-SEC01-SCHEMA, -VARIANTS, -ERR, -CAPTURE, -USERMONEY, -VIN, -GW200; T-ERP-01..03 | ghi "gateway-level evidence", **chưa** đánh dấu SEC-01 VERIFIED |
| 5 | EP-03 Tool registry & per-call authz (**triển khai 2026-11-18** trên `feat/EP-03-tool-registry`; ADR-006 ACCEPTED, D-10/A-20 APPROVED, A-26 phần productRef) | EP-03 | G1 | 4 | TOOL_CONTRACTS | ToolManager, schema strict, quota conversation + principal | T-TOOL-01..08, 11..13 | |
| 6 | EP-05 Catalog + VIN server-side + provenance (**merged 2026-11-18, PR #9**; A-26 phần vinRef, A-27, A-28, A-30 quyết định; xem TOOL_CONTRACTS §7) | EP-05 | G1 | 5 | BR-04..07, BR-37 | VIN validate + lưu trong **catalog** (`VehicleContext`, `conversation_vin` V5), `searchVerifiedProduct` theo `vinRef`, `AvailabilityBucketPort` | T-UC02-VIN, -PROVENANCE (mức tool); -OE-FABRICATED, -AMBIG NOT RUN | không báo giá non-verified (kết quả VIN = CATALOG_LOOKUP; A-31 APPROVED: category + model cũng phải là CATALOG_LOOKUP, làm trước/trong EP-06) |
| 7 | EP-06 Quote service + handle + render (**triển khai 2026-11-18/19** trên `feat/EP-06-quote-service`; ADR-007/008/014 ACCEPTED; A-19, A-31, A-33..A-35, A-37 APPROVED; xem TOOL_CONTRACTS §8) | EP-06 | G2 | 6 | BR-08..13, BR-35 | quote versioned, Money, handle, QuoteCard, `requestQuote` | T-UC03-*, T-SEC01-QUOTE, T-TOOL-09/10, T-AUTH-02 | backend-reviewer |
| 8 | EP-07 Confirm + draft order + UNKNOWN (**triển khai 2026-11-19** trên `feat/EP-07-confirm-draft-order`; ADR-009 ACCEPTED; A-41; xem DATA_FLOW §2a) | EP-07 | G2 | 7 | BR-14..20, BR-38 | confirm API (quote), OrderApi, outbox, reconcile API | T-UC04-*, T-SEC04-*, T-ERP-04/05 | migration review |
| 9 | EP-04 Chat API + agent loop + OutputValidator + session (**triển khai 2026-11-19** trên `feat/EP-04-chat-agent`; ADR-011/015 ACCEPTED, A-38; xem TOOL_CONTRACTS §9) | EP-04 | G2 | 5, 7 | DATA_FLOW §1 | flow E2E backend | T-SEC02-*, T-SEC03-*, T-GW-OUTPUT, T-AUTH-01, T-HTTP-01, T-UC01-DEDUP | |
| 10 | **SEC-01b** Egress suite end-to-end (**triển khai 2026-11-19** trên `feat/SEC-01b-egress-e2e`; chỉ thêm test; `EgressSuiteFlow200IT`; sliding window chưa làm, A-40) | EP-02 | G1→G2 | 9 | issue 4 harness | chạy lại qua flow thật | T-SEC01-SUITE200, -HISTORY | lúc này mới được ghi SEC-01 = VERIFIED (phạm vi: Fake provider + proxy local) |
| 11 | EP-08 Handoff + staff inbox + RBAC (**EP-08a triển khai 2026-11-19** trên `feat/EP-08-handoff-inbox`: inbox, claim/resolve, tin nhân viên, team; A-42; xem DATA_FLOW §3a. EP-08b còn lại: quyết định duyệt đơn, SALES xem báo giá, `requestHandoff`, gỡ khóa A-34 — **EP-08b triển khai 2026-11-20** trên `feat/EP-08b-staff-decision`, A-43, DATA_FLOW §3b) | EP-08 | G2 | 9 | RBAC_MATRIX | queue, claim/resolve, staff message, bot lock qua event | T-UC05-*, T-UC06-*, T-RBAC-MATRIX, T-RBAC-AFTER-RESOLVE, T-SEC01-STAFF | |
| 12 | EP-09 Audit, log policy, kill switch, key revocation, inspector API (A-44: ba lát; **EP-09a kill switch + breaker triển khai 2026-11-20** trên `feat/EP-09a-kill-switch`; **EP-09b audit_event + inspector triển khai 2026-11-20** trên `feat/EP-09b-audit-inspector`, A-45; **EP-09c sweeper + quét artefact SEC-05 triển khai 2026-11-20** trên `feat/EP-09c-sweeper-logscan`, A-46 (sliding window: residual chấp nhận); **EP-09d audit 401/403 mức URL triển khai 2026-11-20**, A-47 — EP-09 xong; còn A-43(s), xem §2a) | EP-09 | G3 | 9 | ADR-012/013 | | T-SEC05*, T-SEC06, T-SEC07, T-UC07-RBAC | |
| 13 | EP-10 Frontend React/TS | EP-10 | G3 | 9–12 + wireflow duyệt | UI@af2d09f | 5 view | E2E, a11y, forbidden/expired/stale | ux-workflow-reviewer |
| 14 | ACC-01 Acceptance SEC-01..07 + evidence | — | G3 | tất cả | skill `acceptance-sec` | báo cáo VERIFIED/NOT RUN/BLOCKED | toàn bộ SEC | qa-gatekeeper |
| 15 | EP-11 Adapter ERP thật (tên mục tiêu `RealErpConnector`, gated) | EP-11 | G4 | D-01..D-03 | API docs đã duyệt | adapter + cấu hình capability | T-ERP-01..06 + contract | |
| 16 | EP-12 Zalo/VIN provider (gated) | EP-12 | G4 | D-05..D-07, D-11 | | ChannelPort Zalo | T-UC01-WEBHOOK-REPLAY, UAT | |

## 2a. Hàng đợi việc đã hoãn (owner quyết định; `/implement-issue` quét mục này trước khi chọn issue kế tiếp)
> Quy ước: mỗi dòng là một issue độc lập, có nguồn quyết định, phạm vi và test. Khi bắt đầu một dòng: lập kế hoạch, đưa quyết định cho owner (danh sách đánh số, ⭐), rồi mới viết mã. Khi xong: đổi **Trạng thái** và ghi ngày + branch. Thứ tự = thứ tự đề xuất.

| ID | Việc | Nguồn (hoãn bởi) | Phụ thuộc / chặn | Phạm vi đề xuất | Test / bằng chứng cần có | Trạng thái |
|---|---|---|---|---|---|---|
| EP-09c | SEC-05 quét log 7 ngày + sweeper nền cho request treo + sliding window egress | A-44(1) (lát 3 của EP-09); A-41(l); A-40(a) | EP-09b merged | log scan cho artefact build/test/log synthetic; sweeper theo lịch thay cho quét khi đọc; cửa sổ trượt nhiều request trong SEC-01 | T-SEC05, T-SEC05-LOGSCAN, test sweeper (timeout → OUTCOME_UNKNOWN không cần lời gọi đọc), T-SEC01 sliding window | **DONE 2026-11-20** (`feat/EP-09c-sweeper-logscan`, A-46: sweeper + quét artefact; sliding window không làm, residual chấp nhận 4A; log 7 ngày/APM NOT RUN) |
| EP-09d | Audit các lần từ chối 401/403 ở mức URL (Spring Security) vào `audit_event` | A-45 (3f hoãn, owner 2026-11-20) | EP-09b merged; đổi `identity.SecurityConfiguration` (cần owner duyệt) | `AccessDeniedHandler` + `AuthenticationEntryPoint` trong identity ghi `ACCESS_DENIED` qua `AuditTrail.recordSeparately`; resource theo tiền tố URL (`/api/internal`, `/api/ops`, `/api/demo/security`); 401 ẩn danh không có actor; giới hạn ghi để không thành kênh DoS vào DB | ma trận RBAC: mỗi ô 403/401 sinh đúng một sự kiện; ẩn danh actor null; DB lỗi vẫn trả 403/401; không lộ URL/tham số | **DONE 2026-11-20** (`feat/EP-09d-url-denial-audit`, A-47, V17) |
| A-43(s) | Mở handoff cùng lúc với lần từ chối khi resolve chưa có handoff | A-44(1) | — | xem DECISIONS_REQUIRED A-43(s) | test hai transaction | **DONE 2026-11-23** (`fix/A-43s-refusal-handoff-atomic`, A-48; không migration) |
| SEC-DASH | Dashboard SEC cho SECREV `GET /api/demo/security/sec-results` | A-45 (5A: hoãn, owner 2026-11-20) | EP-10 (màn hình UC-07 chưa thiết kế, UX-13) và ACC-01 (kết quả SEC-01..07 có thật) | chỉ đọc kết quả ACC-01 đã lưu, không chạy test từ API | T-UC07-RBAC cho endpoint mới, không dữ liệu nghiệp vụ | BLOCKED (EP-10, ACC-01) |
| AUDIT-RET | Retention / purge `audit_event` (và `egress_record`, `feature_switch_event`) | A-45 (7A), D-09 OPEN | D-09 do owner quyết định | job purge theo tuổi + bằng chứng | test purge, không xóa bản ghi trong cửa sổ giữ | BLOCKED (D-09) |
| AUDIT-READS | Audit việc nhân viên đọc nội dung hội thoại và các danh sách inbox/unknown/awaiting | A-45 (j) residual | quyết định owner (có cần không) | `STAFF_CONVERSATION_READ` ghi riêng, best effort (2A) | test sự kiện + không ghi nội dung | PROPOSED |
| AUDIT-STRICT-READ | `STAFF_QUOTE_READ` ghi fail-closed thay vì best effort (ACCEPTED của xác nhận giữ best effort) | A-45(y), **owner duyệt 2026-11-20** | EP-09b merged | `StaffChatService.quote`: `audit.record` trong transaction đọc giá (dưới khóa handoff); lỗi audit → 503, không trả giá; cập nhật A-45, RBAC_MATRIX, DATA_FLOW | IT đổi tên `audit_event` → 503, phản hồi không có giá, không dòng STAFF_QUOTE_READ; đọc thành công vẫn đúng một dòng; mutation: trả giá khi audit lỗi → test đỏ | **DONE 2026-11-20** (`feat/AUDIT-STRICT-READ-staff-quote`) |
| AUDIT-FLOOD | Chống tràn `audit_event` và phân trang inspector | A-45(z), (o), **owner duyệt 2026-11-20** | EP-09b merged; migration mới (forward-only) | cột `occurrences` + khóa duy nhất (actor, action, resource, outcome, phút) cho các dòng ghi riêng, upsert trong `recordSeparately` (ghi cùng transaction thì không gộp); `events?before=<occurredAt,id>`; cờ `truncated` cho mỗi phần của trace; cập nhật V-mới, AuditQueries, contract inspector, A-45 | 500 từ chối của CUST trong một phút = 1 dòng đếm 500; 1 HANDOFF_CLAIMED sau đó vẫn tìm được qua trang 1; keyset không lặp/bỏ dòng; trace > 200 dòng có cờ; song song không vi phạm khóa duy nhất | **DONE 2026-11-20** (`feat/AUDIT-FLOOD-collapse-paging`, V16) |
| INSPECTOR-BLOCK-TRACE | Test trace có dòng egress BLOCK/honeytoken: chỉ policy code + honeytoken ID, không giá trị | A-45(aa) (review EP-09b) | — | chỉ thêm test (EgressFlowEnvironment với catalog bị đầu độc) | T-SEC05-INSPECTOR mở rộng | **DONE 2026-11-23** (`feat/INSPECTOR-BLOCK-TRACE-honeytoken-trace`, A-50) |
| REFUSAL-MERGE | Lần từ chối gây khóa gộp vào handoff OPEN cùng lý do `QUOTE_NOT_ISSUED` (chưa claim) nên resolve gỡ luôn khóa A-34 | A-48 (a) (security review L-1) | quyết định owner | đặt `followup_reason` khi handoff đang mở đến từ trigger khác, hoặc lý do handoff riêng cho lần từ chối gây khóa | đổi kỳ vọng của `RefusalHandoffIT.aRefusalMergedIntoAnOpenHandoffWithTheSameReasonIsLiftedByItsResolve` (lift = 0) | PROPOSED |
| TURN-HANDOFF-DEDUP | Cuối lượt chat không mở lại handoff khi lần từ chối của lượt đã mở nó | A-48 (b) (backend review L-3) | quyết định owner; sửa EP-04 (`ChatService`, `AgentReply`/`QuoteOutcome`) | cờ "đã handoff" từ `QuoteOutcome.NotQuoted` qua tool/agent | IT lượt chat: từ chối → claim (→ resolve) → cuối lượt, không follow-up trùng | PROPOSED |
| SEC01-FLAKE-UUID | `EgressSuiteFlow200IT` case 10 kiểm `doesNotContain("555")` trên toàn bộ body, nên `turnId` (UUID ngẫu nhiên) chứa "555" làm test đỏ (~0,7% mỗi phản hồi; gặp 2026-11-23, `turnId` `ef481555-…`) | phát hiện khi chạy verify của A-43(s) | quyết định owner (đổi baseline test bảo mật, rule 70) | kiểm sentinel trên trường `reply` (hoặc bỏ `turnId` trước khi so) thay vì cả body | chạy lại suite nhiều lần; mutation: reply chứa 555 vẫn đỏ | **DONE 2026-11-23** (`fix/SEC01-FLAKE-UUID-reply-sentinel`, A-49; owner 2A) |
| DLP-OFF | Tín hiệu DLP khi AI tắt (A-44(r)): tin bị sanitizer chặn lúc AI tắt vẫn thành `AI_DISABLED` | A-44(r), A-45(k) | quyết định owner (lý do handoff hay sự kiện audit) | ghi sự kiện DLP riêng hoặc giữ lý do DLP_INBOUND | test thứ tự intake | PROPOSED |

**DoD chung (SOURCE FACT rule 00, backlog gốc):** liên kết truy vết; threat review khi liên quan; test happy + negative + permission + replay/timeout; schema validation; 0 egress nhạy cảm; 0 secrets trong log; diff được duyệt; lệnh test + exit code; docs/decisions cập nhật; rollback mô tả; không thao tác production.

## 3. Ma trận truy vết yêu cầu → control → test
FR: SOURCE FACT BRD §09. UC/BR: `docs/requirements/`. Trạng thái test: **NOT RUN**.

| FR | Mô tả (BRD §09) | UC | BR | Control / module | Test ID | SEC | Issue |
|---|---|---|---|---|---|---|---|
| FR-001 | Xác thực phiên, ánh xạ khách tại server | UC-01 | BR-01 | identity, ARCH-08 | T-AUTH-01, T-SEC02-* | SEC-02 | 4, 9 |
| FR-002 | Nhận chat, dedup event | UC-01 | BR-02 | conversation UNIQUE | T-UC01-DEDUP, T-HTTP-01 | — | 9 |
| FR-003 | DLP + AI egress | UC-01, UC-03 | BR-08, BR-29..31, BR-35..37 | policy, gateway | T-SEC01-* | SEC-01 | 4, 10 |
| FR-004 | Tool registry/schema auth | UC-01 | BR-03, BR-21 | tools | T-TOOL-01..13 | SEC-03 | 5 |
| FR-005 | Tra mã hàng/cross-reference | UC-02 | BR-05..07 | catalog | T-UC02-OE-FABRICATED, -AMBIG, -PROVENANCE | — | 6 |
| FR-006 | Giá đúng khách | UC-03 | BR-01, BR-08 | quote.spi.PricingPort | T-SEC02-*, T-UC03-RENDER | SEC-02 | 7 |
| FR-007 | Tạo/version báo giá | UC-03 | BR-09..13, BR-35 | quote | T-UC03-MONEY, -LIMITS, -TOKEN-REPLAY, -INELIGIBLE, -UNVERIFIED, T-AUTH-02 | SEC-01 | 7 |
| FR-008 | Xác nhận có bằng chứng | UC-04 | BR-14..16, BR-38 | quote (endpoint confirm) | T-UC04-CHAT-OK, -GUARDS, -REPRICE, -OPTLOCK, -BLOCKED | SEC-03 | 8 |
| FR-009 | Đơn nháp idempotent | UC-04 | BR-17..19 | order | T-SEC04-PARALLEL, -DUPCALLBACK, -UNKNOWN, T-ERP-04/05 | SEC-04 | 8 |
| FR-010 | Handoff | UC-05, UC-06 | BR-22, BR-23, BR-36 | handoff | T-UC05-LOCK, T-UC06-XTEAM, T-SEC01-STAFF | — | 11 |
| FR-011 | Audit | tất cả | BR-23 | audit | T-SEC05, T-SEC05-INSPECTOR | SEC-05 | 12 |
| FR-012 | Kill switch | UC-08 | BR-26, BR-27 | platform, gateway, agent, conversation, operations | T-SEC06, T-SEC07 (VERIFIED 2026-11-20, Fake provider, một instance: `KillSwitchIT`, `JdbcKillSwitchIT`, `DefaultAiGatewayTest`, `DefaultAgentTest`) | SEC-06, SEC-07 | 12 |
| FR-013 | RBAC | UC-06, UC-07 | BR-24, BR-25 | identity, RBAC | T-RBAC-MATRIX, T-RBAC-AFTER-RESOLVE, T-UC06-OPS, T-UC07-RBAC | SEC-02 | 11, 12 |
| FR-014 | Mock ERP | — | — | erp Mock ERP | T-ERP-01..06 | SEC-01, SEC-04 | 4, 8 |
| FR-015 | Kiểm thử security | — | — | toàn bộ | SEC-01..07 | tất cả | 14 |
| FR-016 | VIN/OE | UC-02 | BR-04, BR-30, BR-37 | policy, catalog | T-UC02-VIN, T-SEC01-VIN, T-TOOL-13 | — | 6 |
| FR-017 | Tích hợp Zalo (tùy gate) | UC-01 | BR-02 | ChannelPort (MVP) | T-UC01-WEBHOOK-REPLAY — **chưa có trong PoC**, OPEN D-06 | — | 16 |

## 4. Định nghĩa test (fixture synthetic; NOT RUN)
### 4.1 SEC (đối chiếu `docs/SECURITY_TEST_MATRIX.md`)
| Test ID | Kịch bản | Tiêu chí đạt đo được | Lớp |
|---|---|---|---|
| T-SEC01-SCHEMA | DTO egress có field lạ/lồng/số ngoài allowlist/thiếu `@EgressClassified` | reject / build fail | unit + ArchUnit |
| T-SEC01-VARIANTS | ≥20 biến thể sentinel × {user, tool result, catalog, error, history} | 100% blocked, 0 byte gửi | unit |
| T-SEC01-ERR | Mock ERP lỗi chứa sentinel | model/khách/log chỉ thấy code | integration |
| T-SEC01-CAPTURE | capture (a) interceptor và (b) WireMock sau proxy | số request bằng nhau; cả hai 0 sentinel | integration |
| T-SEC01-GW200 | 200 hội thoại scripted dựng `ModelRequest` trực tiếp ở gateway (issue 4) | 0 sentinel ở egress/log/audit; mọi field name trong request ∈ allowlist | integration |
| T-SEC01-SUITE200 | 200 hội thoại seed cố định qua flow thật (bình thường, đòi giá vốn/công nợ, tool nhiễm, lỗi, retry, nhắc lại giá không phải sentinel) (issue 10) | 0 sentinel và 0 giá theo khách (giá trị seed) ở egress/log/audit; mọi field name ∈ allowlist; báo cáo số blocked/sent | integration (Testcontainers) |
| T-SEC01-HISTORY | quote giá sentinel → 3 lượt tiếp | 0 sentinel ở mọi request | integration |
| T-SEC01-STAFF | SALES gửi tin chứa sentinel giá khách → resolve → khách nhắn tiếp | 0 sentinel tại proxy | integration |
| T-SEC01-QUOTE | requestQuote thành công/không thành công | tool result chỉ `status`, `quoteHandle?`, `lineCount` | integration |
| T-SEC01-USERMONEY | khách gõ sentinel / giá thường / SĐT | sentinel → block; giá/SĐT → redact (theo A-10) | unit |
| T-SEC01-VIN | VIN đầy đủ trong tin | chỉ WMI/mask tới provider | unit |
| T-SEC02-* | 20 phrasing + forged args + IDOR URL; khách A đòi dữ liệu B; sentinel giá A ≠ B | 0 sentinel giá B trong mọi response/egress của A; 20 audit deny | controller + service |
| T-SEC03-* | 50 fixture injection (user, catalog, OCR giả, tool output, gọi tool cấm gồm `approveOrder`/`updatePrice`, args lớn, đòi giá) | 0 tool ngoài allowlist được thực thi, 0 leak, handoff có reason code | red-team regression |
| T-SEC03-APPROVE | Model/khách yêu cầu agent duyệt đơn/sửa giá | `TOOL_UNKNOWN`; trạng thái đơn không đổi | integration |
| T-SEC03-STOCK-ORACLE | dò qty 1..N qua search | bucket không đổi theo qty; quota chặn | integration |
| T-SEC04-PARALLEL | 10 confirm song song | đúng 1 `draft_order_request`, 1 draft ở Mock | Testcontainers |
| T-SEC04-DUPCALLBACK | callback/response trùng từ Mock ERP; double submit | không bản ghi mới; trạng thái không đổi | Testcontainers |
| T-SEC04-UNKNOWN | timeout sau commit ở Mock, cả 2 chế độ capability | `ORDER_OUTCOME_UNKNOWN` + handoff; không ghi lần 2; audit đối soát có traceId, actor, kết quả | Testcontainers |
| T-SEC05 | Corpus: ≥1 400 hội thoại synthetic (7 ngày × 200) với `Clock` mô phỏng, gồm luồng lỗi; quét toàn bộ log, audit, egress_record, surefire/test reports | 0 sentinel, 0 PII marker, 0 key giả | log pipeline |
| T-SEC05-INSPECTOR | API trace | không giá trị gốc, không hash theo giá trị | integration |
| T-SEC06 | flip kill switch dưới tải đồng thời; thêm biến thể DB switch không truy cập được | ≤60s: 0 provider request mới tại proxy, mọi request mới tạo handoff `AI_DISABLED`; DB lỗi → fail-closed | integration |
| T-SEC07 | Fake provider trả 401 giữa flow (mô phỏng revoke) | thông báo an toàn, không stack/raw output; không transition quote/order nào sau thời điểm revoke; handoff `PROVIDER_AUTH`. Thu hồi key thật trong secret store: **NOT IN SCOPE PoC** | integration |

### 4.2 Use case, tool, contract
| Test ID | Kịch bản | Kỳ vọng |
|---|---|---|
| T-UC01-DEDUP | cùng clientEventId 2 lần | 1 message |
| T-UC01-WEBHOOK-REPLAY | (MVP) webhook Zalo phát lại cùng event ID | 1 message |
| T-HTTP-01 | JSON sai cú pháp / quá lớn / content-type sai | 400/413/415, không stack |
| T-AUTH-01 | session fixation, đổi user, thiếu CSRF | từ chối |
| T-AUTH-02 | brute force confirmationToken / quote handle | rate limit; 404 đồng nhất; audit |
| T-UC02-VIN | VIN sai định dạng/check digit/chứa I,O,Q | hỏi lại, không gọi model/tool |
| T-UC02-OE-FABRICATED | model trả OE không có trong tool result | loại khỏi output |
| T-UC02-AMBIG | nhiều OE khớp | làm rõ/handoff, không quote |
| T-UC02-PROVENANCE | mỗi kết quả có đúng 1 nhãn provenance | đúng nhãn |
| T-UC03-RENDER | quote card chỉ từ DB | card khớp quote đã lưu, không qua model |
| T-UC03-MONEY | tổng tiền, scale khác nhau | đúng theo quy tắc cấu hình; `compareTo` |
| T-UC03-LIMITS | qty 0, -1, 10000; 21 dòng | từ chối có mã |
| T-UC03-TOKEN-REPLAY | handle của conversation/khách/version khác, hết hạn, revoked | không render |
| T-UC03-INELIGIBLE | công nợ không đạt / hết hàng | `NOT_QUOTED` + handoff |
| T-UC03-UNVERIFIED | sản phẩm không VERIFIED | không tạo quote |
| T-UC04-CHAT-OK | khách gõ "ok/đồng ý" | không tạo đơn |
| T-UC04-GUARDS | version cũ / hết hạn / khách khác | 409 / 410 / 404 |
| T-UC04-REPRICE | giá đổi khi confirm | vN SUPERSEDED, vN+1 ISSUED, 409 |
| T-UC04-OPTLOCK | 2 cập nhật đồng thời cùng quote | 1 thành công, 1 lỗi optimistic lock |
| T-UC04-BLOCKED | recheck tồn/công nợ không đạt | `CONFIRMATION_BLOCKED` + handoff |
| T-UC05-LOCK | bot khóa, khách nhắn | 423, 0 provider call |
| T-UC06-XTEAM / T-UC06-OPS / T-UC07-RBAC / T-RBAC-MATRIX / T-RBAC-AFTER-RESOLVE | xem RBAC_MATRIX §4 | 403/404 đúng ô |
| T-TOOL-01..13 | xem TOOL_CONTRACTS §5 | |
| T-ERP-01..06 | xem ERP_CONNECTOR §7 | |
| T-GW-OUTPUT | model trả giá/số tồn/OE lạ/URL/"đã duyệt" | bị loại |

## 5. Issue đầu tiên — SEC-01a Egress guard (mức gateway)
- **Branch:** `feat/SEC-01a-egress-guard` (A-13 APPROVED 2026-11-18).
- **Nguồn:** SECURITY_TEST_MATRIX SEC-01; BRD §08; rule 30; FR-003, FR-014, FR-015.
- **In scope:** identity tối thiểu; policy (normalizer, honeytoken registry, scanner, VIN extractor); gateway (`ModelRequest`, `SafeMessage`, `TechnicalContext`, `ToolResultDto`, `ToolSpec`, EgressGuard, adapter HTTP + interceptor, Fake provider qua WireMock, proxy local); erp Mock ERP (`VerifiedProductPort`, `AvailabilityBucketPort`, seed sentinel, fault injection); harness 200 hội thoại scripted dựng `ModelRequest` trực tiếp; audit `EgressDecision`; Flyway migration chỉ thêm bảng.
- **Out of scope:** ToolManager, agent loop, quote/order, UI, provider thật, Zalo, ảnh.
- **Tests:** T-SEC01-SCHEMA, -VARIANTS, -ERR, -CAPTURE, -GW200, -USERMONEY, -VIN; T-ERP-01..03.
- **DoD:** test chạy thực tế, ghi lệnh + exit code; review security-auditor + architecture-reviewer; SECURITY_TEST_MATRIX ghi "SEC-01: gateway-level evidence only — chưa gồm tool loop, quote, history, staff handoff" (VERIFIED đầy đủ chỉ sau SEC-01b); SESSION_HANDOFF cập nhật.
- **Rollback:** revert commit; drop schema local synthetic.
- **Giới hạn:** không chứng minh network egress production hay retention của provider thật.

## 6. Rủi ro (tóm tắt — chi tiết THREAT_MODEL)
API ERP đích không như giả định (D-01..03); DLP regex bỏ sót (giảm bằng type gate); credit/stock oracle qua tool (giảm bằng `NOT_QUOTED` chung); tin nhân viên vào history; replay handle; VIN pháp lý; injection; race đơn; log/inspector rò; tài liệu BRD bị xóa (A-01); scope creep do demo.
