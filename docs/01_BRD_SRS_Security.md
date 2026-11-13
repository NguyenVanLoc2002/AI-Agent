# BRD / SRS – Secure ERP AI Agent

Căn cứ: Đề xuất giải pháp AI Agent tích hợp Arito ERP – Đắc Yến, v1.0, 14/10/2026 (15 trang).

**Trạng thái:** Draft for implementation planning; giả định chưa xác minh được ghi rõ.
**Phạm vi:** Security PoC → MVP tích hợp.

## 01. Phạm vi và nguyên tắc

- Mục tiêu PoC: chứng minh tách biệt dữ liệu nhạy cảm khỏi LLM, tool security, báo giá và đơn nháp chống trùng trên Mock ERP. MVP sau PoC: VIN/OE, ảnh phụ tùng, Arito, Zalo OA, giao diện nhân viên. Ngoài MVP: phân tích lịch sử, KPI, dự báo.
- Nguyên tắc bắt buộc: không gửi giá vốn, giá theo khách, công nợ, danh sách khách, lịch sử mua và thông tin xác định khách tới LLM. Backend gửi giá trực tiếp qua kênh trả lời, không vòng lại LLM.
- Tài liệu gốc chưa xác minh Arito REST API, sandbox, API tạo đơn nháp, field-level permission, nguồn catalog VIN/OE; mọi tích hợp thật đều có gate xác nhận.

## 02. Vai trò và phân quyền

- Khách đã liên kết: hỏi hàng, xem báo giá chính mình, xác nhận báo giá cụ thể. Khách chưa liên kết: xác minh SĐT qua quy trình được phê duyệt, không suy luận customerId từ câu chat.
- Nhân viên kinh doanh: xem các hội thoại được giao, báo giá trong quyền; chuyển giao và xử lý ngoại lệ. Trưởng bộ phận: phạm vi bộ phận. Quản trị hệ thống: cấu hình kỹ thuật, mặc định không có quyền đọc giá/đoạn chat. Agent service account: không có quyền phê duyệt hoặc sửa giá.
- Quyền triển khai nhiều tầng: ERP integration account; tool registry; egress DLP; business write policy; human approval. Kiểm tra tenant/customer/assignment ở từng API bằng danh tính server-side.

## 03. Quy trình UC-01: tiếp nhận hội thoại

- Trigger: tin nhắn web chat demo hoặc Zalo OA gửi đến. Preconditions: webhook được xác thực; request dedup theo provider event ID.
- Flow: tạo/khôi phục conversation → liên kết customerId đã xác minh ở server → chuẩn hóa input → lọc dữ liệu theo chính sách → gửi LLM qua Gateway → nhận tool request → kiểm tra tool allowlist, schema, conversation ownership → gọi adapter → chỉ trả DTO đã phân loại → câu trả lời cuối được kiểm tra → gửi kênh.
- Ngoại lệ: service down, AI sai công cụ, dữ liệu không đầy đủ, prompt injection → chặn / chuyển nhân viên; tuyệt đối không đưa chi tiết bí mật vào thông báo lỗi.

## 04. Quy trình UC-02: nhận diện phụ tùng VIN/OE

- Input: tên phụ tùng, xe, VIN, ảnh phụ tùng, số lượng. VIN 17 ký tự, không I/O/Q và check digit khi áp dụng. OCR ảnh đăng kiểm chạy nội bộ; không gửi ảnh giấy đăng kiểm nguyên bản ra LLM.
- AI chỉ nhận diện nhóm phụ tùng từ ảnh; không tự tạo mã OE. Mã OE chỉ từ catalog có bản quyền hoặc mã khách cung cấp rồi đối chiếu ERP. Kết quả phân thành AI nhận diện / Dữ liệu tra cứu / Đã xác minh. Chỉ loại Đã xác minh được dùng để báo giá tự động.
- Ngoại lệ: nhiều mã OE, VIN không hợp lệ, catalog không có độ phủ, ảnh mờ → hỏi làm rõ hoặc handoff. PoC giả lập catalog, không khẳng định mã là đúng sản phẩm thực tế.

## 05. Quy trình UC-03: báo giá

- Input: verified SKU, quantity nguyên dương, customerId lấy từ phiên xác thực. AI không được cung cấp customerId như tham số điều khiển truy vấn giá.
- Backend lấy giá theo khách, stock status và eligibility flag; không nhận giá từ AI. Backend tạo quote ID, version, hiệu lực, dòng hàng, nguồn xác minh; tổng tiền tính bằng Decimal/BigDecimal, quy tắc tiền tệ được cấu hình. Lưu và gửi thông qua server-rendered template.
- LLM chỉ nhận reference opaque token, không nhận giá hay số tồn. Token bind với conversation, customer, quoteId, version, TTL, và chỉ render trong server output cùng phiên. Giá vốn và công nợ không có trong tool response.
- Ngoại lệ: không đủ tồn, công nợ không đạt, giá đổi, nhiều lựa chọn → không tự báo giá / chuyển nhân viên theo rule.

## 06. Quy trình UC-04: xác nhận và tạo đơn nháp

- Chỉ nhận xác nhận tường minh theo quoteId + version (button/action có chữ ký server-side); câu chat tự do như “ok” không đủ để tạo đơn.
- Before write: quote còn hạn, đúng trạng thái/khách/phiên bản, kiểm tra lại giá, tồn kho, điều kiện công nợ. Nếu thay đổi giá → phát hành quote version mới, yêu cầu xác nhận lại.
- Idempotency key = quoteId-version; lưu unique constraint trong DB. Với timeout không rõ ERP có ghi hay chưa, chuyển trạng thái UNKNOWN và đối soát theo external reference; không tạo lần hai mù quáng.
- Agent không phê duyệt đơn: human review trong Arito. Trạng thái: DRAFT → SENT → ACCEPTED → DRAFT_ORDER_CREATED → HUMAN_APPROVED/REJECTED; nhánh EXPIRED/SUPERSEDED/CANCELLED.

## 07. Quy trình UC-05: chuyển nhân viên

- Handoff trigger: thông tin không xác minh được, phát hiện prompt injection, yêu cầu ngoài quyền, đổi giá, vượt giới hạn báo giá, provider lỗi, kill switch.
- Tạo queue item với reason code, conversation ID, verified evidence, actor, timestamp. LLM ngừng trả lời cho tới khi nhân viên mở khóa; không gửi thông tin nhạy cảm trong notification ngoài nội bộ.

## 08. Phân loại dữ liệu và egress contract

- DENY tuyệt đối: purchaseCost, margin, customerPrice, creditBalance, creditLimit, customerDirectory, purchaseHistory, customer identity, secrets/tokens. Egress payload không được có các tên trường hay giá trị nhạy cảm.
- ALLOW có điều kiện: tin nhắn đã lọc, VIN, mã OE được khách đưa/nguồn hợp lệ, tên nhóm phụ tùng, số lượng, sản phẩm đã xác minh, tình trạng tồn enum con_hang / het_hang / can_kiem_tra.
- Ảnh phụ tùng phải xóa EXIF; ảnh đăng kiểm chỉ OCR nội bộ. DLP gồm allowlisted typed DTO, redaction, reject on unclassified fields, outbound TLS proxy/egress restrictions, safe logging. Kiểm tra cả user-provided và tool-fed content.
- Lưu ý: masking regex không bảo đảm vô danh hoàn toàn; VIN và thông tin xe có thể trở thành dữ liệu cá nhân khi liên kết với chủ xe. Cần đánh giá pháp lý trước dữ liệu thật.

## 09. Functional requirements – truy vết

- FR-001 xác thực phiên và ánh xạ khách tại server; FR-002 nhận chat và dedup event; FR-003 lọc DLP và AI egress; FR-004 tool registry/schema auth; FR-005 tra mã hàng/cross-reference; FR-006 giá theo đúng khách; FR-007 tạo/version báo giá; FR-008 xác nhận báo giá có bằng chứng; FR-009 tạo đơn nháp idempotent; FR-010 handoff; FR-011 audit; FR-012 kill switch; FR-013 RBAC; FR-014 Mock ERP; FR-015 kiểm thử security; FR-016 VIN/OE; FR-017 tích hợp Zalo tùy gate.
- Mỗi FR phải có test ID, endpoint/service owner, negative tests và trace tới UC. Không đánh dấu DONE khi chỉ có happy-path.

## 10. Non-functional requirements

- NFR-SEC: deny-by-default, least privilege, secrets không trong git/log/prompt, TLS, encrypted backups, authorization ở service boundary; provider key cấp riêng sandbox.
- NFR-OBS: structured audit không body payload; traceId, actor pseudonym, tool name, policy decision, redaction category, time, correlation ID; chỉ lưu hash/fingerprint nếu thực sự cần và đã đánh giá.
- NFR-REL: retry chỉ với tác vụ idempotent; circuit breaker; fallback human handoff; healthchecks; backup/restore test. RPO 24h và RTO 4h là mục tiêu đề xuất cần chủ hệ thống xác nhận.
- NFR-PERF: latency, concurrency và token budget đo thực tế ở PoC; không tự đặt SLA chưa có load profile.

## 11. Thiết kế dữ liệu đề xuất (PoC)

- CustomerLink(providerUserHash,customerId,verifiedAt,status); Conversation(id,channel,customerId,status,ownerId); Message(id,conversationId,direction,sanitizedRef,createdAt); Product(sku,oe,verifiedSource); Inventory(sku,quantity,warehouse); Price(customerId,sku,unitPrice,currency); CreditFlag(customerId,eligible).
- Quote(id,customerId,conversationId,version,status,expiresAt,totalAmount,currency); QuoteLine(quoteId,sku,qty,unitPrice,source); DraftOrder(id,quoteId,version,externalRef,status,idempotencyKey); ToolAudit(traceId,tool,decision,fields,createdAt); Handoff(id,conversationId,reason,status).
- PoC không cần giữ bản chat gốc nếu không có yêu cầu nghiệp vụ; nếu lưu, dùng retention/encryption tách biệt, không hiển thị trong Security Inspector cho người không được phép.

## 12. API contract dự kiến (không phải Arito API xác thực)

- POST /api/demo/conversations; POST /api/demo/conversations/{id}/messages; GET /api/demo/quotes/{id}; POST /api/demo/quotes/{id}/confirm {version,confirmationToken}; GET /api/demo/security/traces/{traceId}; POST /api/internal/handoffs/{id}/claim.
- ErpConnector: findLinkedCustomer(context), searchVerifiedProduct(query), getCustomerPrice(serverCustomerId,sku), getAvailability(sku), getCreditEligibility(serverCustomerId), createDraftOrder(command,idempotencyKey), findDraftByExternalRef(externalRef).
- Các endpoint riêng cho admin/security phải được RBAC và redaction; không bật public debug payload. Thêm OpenAPI sau khi xác nhận DTO với owner nghiệp vụ.

## 13. Kiểm thử nghiệm thu

- SEC-01: 200 hội thoại có giá vốn 987.654.321 và công nợ 123.456.789, 0 xuất hiện ở provider egress; SEC-02: 20 truy vấn vượt quyền khách B, 0 leakage; SEC-03: 50 injection, 0 tool ngoài allowlist và 0 leak.
- SEC-04: 10 xác nhận song song + timeout, đúng 1 draft; SEC-05: quét 7 ngày log, 0 secrets/PII/giá; SEC-06: kill switch chuyển handoff trong 1 phút; SEC-07: key revoked, fallback và không mất dữ liệu.
- Thêm tests: tool result injected instruction; user đưa số tiền; placeholder replay/cross-session; expired quote; changed price; optimistic locking; webhook replay; API brute force; SSRF / arbitrary URLs; malformed JSON.

## 14. Kế hoạch release và gates

- Gate G0: chốt schema và data classification; G1: Mock ERP + egress policy + SEC-01 chạy trên dữ liệu giả; G2: quote flow + idempotency; G3: SEC-01..07 tất cả PASS; G4: xác nhận Arito API/catalog/Zalo, legal and production controls; G5: UAT và go-live.
- Các việc ngoài phạm vi demo: Zalo OA thật, bản quyền VIN/OE, Arito thật, OCR ảnh đăng kiểm thật, xin ZDR, chứng nhận pentest. Không bỏ qua security workstream chỉ để kịp demo.

## 15. Câu hỏi mở và giả định

- Arito: auth, API sáu nghiệp vụ, field-level permission, external idempotency, sandbox và SLA vẫn UNVERIFIED.
- Business: chính sách khách mới, thời hạn báo giá, thuế/VAT, làm tròn tiền, đơn vị hàng, xử lý đơn nhiều kho, điều kiện công nợ, phê duyệt, quyền chỉnh giá cần xác nhận.
- Security/legal: chính sách xử lý VIN/PII, retention, quyền truy cập dữ liệu audit, provider data processing region và các thủ tục chuyển dữ liệu ra nước ngoài cần bên pháp lý rà soát.
- UI: danh sách màn hình, logic trạng thái, ngôn ngữ, ưu tiên desktop/mobile và persona phải chốt tại workshop trước khi freeze Stitch design.
