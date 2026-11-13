# Use cases — Secure ERP AI Agent (PoC → MVP)

> Trạng thái: DRAFT, chưa được stakeholder phê duyệt. Ngày: 2026-11-13.
> Nguồn: `BRD` = `docs/01_BRD_SRS_Security.md` tại commit `af2d09f` (file đang bị xóa trong working tree — xem OPEN A-01; đọc bằng `git show af2d09f:docs/01_BRD_SRS_Security.md`). `UI` = `docs/03_UI_UX_Stitch.md@af2d09f`. Đề xuất khách hàng 15 trang (14/10/2026) **không có trong repo**; BRD là bản tóm tắt của nó.

## Nhãn bằng chứng
- **SOURCE FACT** — có trong BRD/UI@af2d09f hoặc tài liệu gốc; không đảm bảo hành vi của Arito/provider.
- **DESIGN PROPOSAL** — đề xuất kỹ thuật của nhóm, chưa phê duyệt.
- **VERIFIED** — đã chứng minh bằng lệnh/test/log trên môi trường xác định. *Hiện chưa có mục nào VERIFIED (chưa có mã).*
- **OPEN** — cần Arito/business/security/legal xác nhận; tham chiếu `docs/DECISIONS_REQUIRED.md`.

## Actors
| ID | Actor | Nhãn | Ghi chú |
|---|---|---|---|
| CUST | Khách đã liên kết | SOURCE FACT (BRD §02) | Hỏi hàng, xem báo giá của mình, xác nhận báo giá cụ thể |
| CUST-U | Khách chưa liên kết | SOURCE FACT (BRD §02) | Xác minh SĐT qua quy trình được duyệt — **ngoài PoC** (OPEN D-06/D-08) |
| SALES | Nhân viên kinh doanh | SOURCE FACT (BRD §02) | Hội thoại được giao, báo giá trong quyền, handoff |
| LEAD | Trưởng bộ phận | SOURCE FACT (BRD §02) | Phạm vi bộ phận — **ngoài PoC** (OPEN D-08) |
| OPS | Quản trị hệ thống | SOURCE FACT (BRD §02) | Cấu hình kỹ thuật; mặc định **không** đọc giá/chat |
| SECREV | Security reviewer | SOURCE FACT (UI persona 3) | Xem trace đã redaction, dashboard SEC |
| AGENT | Agent service account | SOURCE FACT (BRD §02) | Không phê duyệt, không sửa giá |
| ERP | Arito ERP (PoC: Mock Arito) | SOURCE FACT tồn tại; API **OPEN** (D-01..D-03) | |
| LLM | Model provider | SOURCE FACT (BRD §01) | Untrusted; PoC dùng Fake provider (DESIGN PROPOSAL, A-05) |

## Danh sách use case
| UC | Tên | Actor chính | Nguồn | Phạm vi |
|---|---|---|---|---|
| UC-01 | Tiếp nhận hội thoại | CUST | SOURCE FACT BRD §03 | PoC (web chat demo); Zalo = MVP |
| UC-02 | Nhận diện phụ tùng VIN/OE | CUST | SOURCE FACT BRD §04 | PoC với catalog giả lập |
| UC-03 | Báo giá | CUST (AGENT đề xuất) | SOURCE FACT BRD §05 | PoC |
| UC-04 | Xác nhận báo giá và tạo đơn nháp | CUST | SOURCE FACT BRD §06 | PoC trên Mock ERP |
| UC-05 | Chuyển nhân viên (handoff) | hệ thống, SALES | SOURCE FACT BRD §07 | PoC |
| UC-06 | Staff inbox / claim / resolve | SALES | DESIGN PROPOSAL, dẫn xuất từ BRD §02, §07, UI persona 2 | PoC tối thiểu |
| UC-07 | Security inspector | SECREV | DESIGN PROPOSAL, dẫn xuất từ UI persona 3, BRD §12 `/security/traces` | PoC (synthetic only) |
| UC-08 | Kill switch & thu hồi key provider | OPS | DESIGN PROPOSAL — UC dẫn xuất từ FR-012, SEC-06, SEC-07 (BRD §09, §13; BRD chỉ định nghĩa UC-01..05) | PoC |

---

## UC-01 Tiếp nhận hội thoại — SOURCE FACT (BRD §03)
- **Trigger:** tin nhắn từ web chat demo (PoC) hoặc Zalo OA (MVP, OPEN D-06).
- **Preconditions:** phiên xác thực (PoC: session demo — DESIGN PROPOSAL A-06); với webhook: xác thực + dedup theo event ID (SOURCE FACT).
- **Happy path (SOURCE FACT, chi tiết hóa DESIGN PROPOSAL):**
  1. Tạo/khôi phục conversation; liên kết customer **ở server** từ principal.
  2. Chuẩn hóa input → lọc theo chính sách (DLP inbound).
  3. Gửi LLM qua AI Gateway (egress guard fail-closed).
  4. Nhận tool proposal → kiểm tra allowlist, schema, ownership → gọi adapter.
  5. Chỉ trả DTO đã phân loại cho model; câu trả lời cuối được kiểm tra → gửi kênh.
- **Alternative/failure (SOURCE FACT):** service down, AI chọn sai tool, dữ liệu thiếu, prompt injection → chặn / handoff; không đưa chi tiết bí mật vào thông báo lỗi.
- **Authorization:** customer chỉ từ server-side context, không từ text/tool args (SOURCE FACT CLAUDE.md, BRD §02).
- **Side effects:** message (đã sanitize), audit event. **Audit:** traceId, decision, policy code, field names.
- **Acceptance:**
  - AC-01.1 Given CUST-A đăng nhập, When tin chứa "customerId=B" hoặc tool arg `customerId`, Then tool chạy với A hoặc bị reject do extra arg; audit deny. → T-SEC02-*
  - AC-01.2 Given cùng `clientEventId` gửi 2 lần, Then chỉ 1 message được xử lý. → T-UC01-DEDUP
  - AC-01.3 Given Mock ERP lỗi, Then trả lỗi an toàn + handoff, không lộ exception. → T-SEC01-ERR

## UC-02 Nhận diện phụ tùng VIN/OE — SOURCE FACT (BRD §04)
- **Input:** tên phụ tùng, xe, VIN, ảnh phụ tùng, số lượng.
- **Rules:** BR-04..BR-07 (VIN 17 ký tự, không I/O/Q, check digit khi áp dụng; AI không tạo OE; nhãn AI nhận diện / Dữ liệu tra cứu / Đã xác minh; chỉ Đã xác minh được báo giá tự động).
- **PoC:** catalog giả lập; không khẳng định mã đúng sản phẩm thực tế (SOURCE FACT). Ảnh: **không gửi provider trong PoC** (DESIGN PROPOSAL A-11). OCR đăng kiểm: MVP, nội bộ.
- **Failure:** nhiều OE, VIN không hợp lệ, catalog không phủ, ảnh mờ → hỏi làm rõ hoặc handoff.
- **Acceptance:**
  - AC-02.1 VIN được trích và validate server-side khi tin đến; sai định dạng/check digit → hỏi lại, không gọi model hay tool tra cứu; VIN đầy đủ không bao giờ là tham số tool. → T-UC02-VIN, T-TOOL-13
  - AC-02.2 Model trả OE không có trong tool result → reject, không hiển thị. → T-UC02-OE-FABRICATED
  - AC-02.3 Nhiều OE khớp → yêu cầu làm rõ/handoff, không báo giá. → T-UC02-AMBIG
  - AC-02.4 Mỗi kết quả mang đúng một nhãn provenance (AI nhận diện / Dữ liệu tra cứu / Đã xác minh) và UI hiển thị nhãn đó. → T-UC02-PROVENANCE

## UC-03 Báo giá — SOURCE FACT (BRD §05)
- **Input:** SKU đã xác minh, quantity nguyên dương, customer từ phiên xác thực.
- **Flow:** backend lấy giá theo khách, trạng thái tồn, eligibility; tạo quote ID, version, hiệu lực, dòng hàng, nguồn xác minh; tính tổng BigDecimal; lưu và render bằng template server-side. LLM chỉ nhận opaque token (SOURCE FACT).
- **Failure:** không đủ tồn, công nợ không đạt, giá đổi, nhiều lựa chọn → không tự báo giá / handoff (SOURCE FACT).
- **Acceptance:**
  - AC-03.1 Payload egress tới LLM không chứa giá/tổng/tồn số/eligibility. → T-SEC01-QUOTE
  - AC-03.2 Quote card chỉ render server-side từ quote đã lưu. → T-UC03-RENDER
  - AC-03.3 Token báo giá dùng ở conversation/khách/version khác → không render. → T-UC03-TOKEN-REPLAY
  - AC-03.4 Credit không đạt / hết hàng / cần xem xét → không báo giá tự động, handoff; model chỉ nhận `NOT_QUOTED` chung, không biết lý do. → T-UC03-INELIGIBLE, T-TOOL-09
  - AC-03.5 Tổng tiền = Σ(qty × unitPrice) tính bằng BigDecimal theo quy tắc làm tròn cấu hình; so sánh bằng `compareTo`. → T-UC03-MONEY
  - AC-03.6 qty ≤ 0, > 9999, hoặc > giới hạn dòng (20, tạm) → từ chối có mã lỗi. → T-UC03-LIMITS
  - AC-03.7 Sản phẩm không có provenance VERIFIED → không tạo quote. → T-UC03-UNVERIFIED

## UC-04 Xác nhận và tạo đơn nháp — SOURCE FACT (BRD §06)
- **Trigger:** action tường minh theo quoteId + version (button ký server-side); "ok" trong chat **không** đủ.
- **Before write:** còn hạn, đúng trạng thái/khách/version; kiểm tra lại giá, tồn, công nợ; giá đổi → version mới, xác nhận lại.
- **Idempotency:** key = quoteId-version, unique trong DB (SOURCE FACT). Gửi key sang ERP **chỉ nếu ERP hỗ trợ** (OPEN D-03).
- **Unknown outcome:** timeout → UNKNOWN, đối soát; không tạo lần hai mù quáng (SOURCE FACT).
- **Human approval:** trong Arito; agent không phê duyệt (SOURCE FACT).
- **Acceptance:**
  - AC-04.1 10 xác nhận song song → đúng 1 draft. → T-SEC04-PARALLEL
  - AC-04.2 Version cũ → 409; hết hạn → 410; khách khác → 404; cập nhật đồng thời → optimistic lock, một bên thất bại có mã lỗi. → T-UC04-GUARDS, T-UC04-OPTLOCK
  - AC-04.6 Tồn/công nợ không đạt khi recheck → `CONFIRMATION_BLOCKED` + handoff, không tạo đơn. → T-UC04-BLOCKED
  - AC-04.3 Timeout sau khi Mock ERP đã ghi → UNKNOWN + hàng đợi đối soát, không ghi lần 2. → T-SEC04-UNKNOWN
  - AC-04.4 Giá đổi tại thời điểm xác nhận → version mới ISSUED, version cũ SUPERSEDED. → T-UC04-REPRICE
  - AC-04.5 Tin chat "ok/đồng ý" không tạo đơn. → T-UC04-CHAT-OK

## UC-05 Chuyển nhân viên — SOURCE FACT (BRD §07)
- **Triggers:** thông tin không xác minh, prompt injection, yêu cầu ngoài quyền, đổi giá, vượt giới hạn báo giá, provider lỗi, kill switch.
- **Flow:** tạo queue item (reason code, conversation ID, evidence đã xác minh, actor, timestamp); LLM ngừng trả lời tới khi nhân viên mở khóa; notification không chứa dữ liệu nhạy cảm.
- **Acceptance:**
  - AC-05.1 Phát hiện injection → handoff `PROMPT_INJECTION`, không có provider call tiếp theo cho conversation đó. → T-SEC03-*
  - AC-05.2 Khi bot khóa, tin mới của khách → 423/thông báo chờ nhân viên, không gọi provider. → T-UC05-LOCK

## UC-06 Staff inbox — DESIGN PROPOSAL
- SALES xem handoff/hội thoại được giao, claim, resolve (mở khóa bot). Không xem hội thoại ngoài phân công. Assignment logic: OPEN D-08 (PoC: theo team seed).
- AC-06.1 SALES-X claim handoff của team khác → 404 (không lộ tồn tại; quy ước RBAC_MATRIX §3). → T-UC06-XTEAM
- AC-06.2 OPS gọi API inbox → 403 (admin ≠ người đọc dữ liệu nghiệp vụ, SOURCE FACT BRD §02). → T-UC06-OPS

## UC-07 Security inspector — DESIGN PROPOSAL (UI persona 3)
- SECREV xem trace: category đầu vào, redaction category, tool name, policy decision, field names gửi provider, correlation ID. Không raw PII/giá/secrets kể cả admin.
- AC-07.1 Response trace không chứa sentinel/PII. → T-SEC05-INSPECTOR
- AC-07.2 OPS/SALES/CUST truy cập → 403. → T-UC07-RBAC

## UC-08 Kill switch & key revocation — DESIGN PROPOSAL (yêu cầu nguồn: FR-012, SEC-06, SEC-07)
- AC-08.1 Bật kill switch khi có request đồng thời → trong ≤60s mọi request mới chuyển nhân viên, 0 provider call. → T-SEC06
- AC-08.2 Thu hồi key giữa flow → fallback an toàn, không stack/raw output, không ghi đơn dở dang. → T-SEC07
