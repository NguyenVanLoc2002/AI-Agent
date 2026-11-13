# Business rules

> DRAFT 2026-11-13. Nhãn: SOURCE FACT / DESIGN PROPOSAL / VERIFIED / OPEN (định nghĩa tại `docs/requirements/USE_CASES.md`). Nguồn `BRD` = `docs/01_BRD_SRS_Security.md@af2d09f`.
> Không quy tắc nào dưới đây đang VERIFIED (chưa có mã/test chạy).

| ID | Quy tắc | Nhãn | Nguồn | UC | Thực thi tại (đề xuất) | Test |
|---|---|---|---|---|---|---|
| BR-01 | Danh tính, khách hàng, tenant, vai trò chỉ lấy từ ngữ cảnh xác thực server-side; không từ text hay tool args | SOURCE FACT | CLAUDE.md; BRD §02, §05 | UC-01, UC-03 | identity, tools | T-SEC02-* |
| BR-02 | Tin nhắn đến được dedup theo provider event ID (SOURCE FACT, webhook); web chat demo dùng `clientEventId` (DESIGN PROPOSAL) | SOURCE FACT / DESIGN PROPOSAL | BRD §03 | UC-01 | conversation (UNIQUE) | T-UC01-DEDUP |
| BR-03 | Input, tool output, catalog string, ảnh, model output đều untrusted | SOURCE FACT | CLAUDE.md; rule 10 | tất cả | gateway, tools, agent | T-SEC03-* |
| BR-04 | VIN 17 ký tự, không I/O/Q, kiểm check digit khi áp dụng | SOURCE FACT | BRD §04 | UC-02 | catalog (VinValidator) | T-UC02-VIN |
| BR-05 | AI không tự tạo mã OE; OE chỉ từ catalog có bản quyền hoặc mã khách đưa rồi đối chiếu ERP | SOURCE FACT | BRD §04 | UC-02 | agent OutputValidator, catalog | T-UC02-OE-FABRICATED |
| BR-06 | Kết quả phân loại: AI nhận diện / Dữ liệu tra cứu / Đã xác minh | SOURCE FACT | BRD §04 | UC-02 | catalog (Provenance enum) | T-UC02-PROVENANCE |
| BR-07 | Chỉ sản phẩm "Đã xác minh" được báo giá tự động | SOURCE FACT | BRD §04 | UC-02, UC-03 | quote | T-UC03-UNVERIFIED |
| BR-08 | Giá, tồn kho, công nợ chỉ kiểm tra trong service bảo vệ, không gửi LLM | SOURCE FACT | BRD §01, §05; CLAUDE.md | UC-03, UC-04 | quote | T-SEC01-* |
| BR-09 | Tiền tệ dùng BigDecimal; quy tắc tiền tệ/làm tròn cấu hình | SOURCE FACT (nguyên tắc) / OPEN (rounding, VAT — D-04, D-13) | BRD §05, §15 | UC-03 | quote (Money) | T-UC03-MONEY |
| BR-10 | Số lượng nguyên dương; tối đa 20 dòng/quote | SOURCE FACT (qty>0) / OPEN (20 dòng là đề xuất — D-10) | BRD §05; rule 40 | UC-03 | quote + DB CHECK | T-UC03-LIMITS |
| BR-11 | Không đủ tồn, công nợ không đạt, giá đổi, nhiều lựa chọn → không tự báo giá, handoff theo rule | SOURCE FACT | BRD §05 | UC-03 | quote, handoff | T-UC03-INELIGIBLE |
| BR-12 | LLM chỉ nhận opaque quote token, bind conversation/customer/quoteId/version/TTL; chỉ render ở server cùng phiên | SOURCE FACT | BRD §05 | UC-03 | quote (QuoteHandleService) | T-UC03-TOKEN-REPLAY |
| BR-13 | Token là giá trị ngẫu nhiên, lưu hash ở server (không phải HMAC tự mô tả) | DESIGN PROPOSAL (ADR-008) | security-auditor review 2026-11-13 | UC-03 | quote | T-UC03-TOKEN-REPLAY |
| BR-14 | Xác nhận chỉ bằng action tường minh gắn quoteId + version; chat tự do ("ok") không đủ | SOURCE FACT | BRD §06 | UC-04 | quote API | T-UC04-CHAT-OK |
| BR-15 | Trước khi ghi: kiểm tra hạn, trạng thái, khách, version, giá, tồn, công nợ | SOURCE FACT | BRD §06 | UC-04 | order | T-UC04-GUARDS |
| BR-16 | Giá thay đổi → phát hành version mới, yêu cầu xác nhận lại | SOURCE FACT | BRD §06 | UC-04 | quote | T-UC04-REPRICE |
| BR-17 | Idempotency key = quoteId-version, unique trong DB; key do server sinh, không nhận từ client | SOURCE FACT (key) / DESIGN PROPOSAL (server-derived) | BRD §06 | UC-04 | order (UNIQUE) | T-SEC04-PARALLEL |
| BR-18 | Gửi idempotency key sang ERP chỉ khi ERP hỗ trợ | OPEN (D-03) | rule 40 | UC-04 | order/DraftOrderPort capability | T-SEC04-* (cả 2 chế độ mock) |
| BR-19 | Timeout không rõ kết quả → UNKNOWN, đối soát; không tạo lại mù quáng | SOURCE FACT | BRD §06 | UC-04 | order | T-SEC04-UNKNOWN |
| BR-20 | Agent không phê duyệt đơn; phê duyệt bởi người trong Arito | SOURCE FACT | BRD §06 | UC-04 | (không có API) | T-SEC03-APPROVE |
| BR-21 | AI không sửa giá, đọc giá vốn, tạo khách, liệt kê hàng loạt khách/sản phẩm, override nhân viên | SOURCE FACT | CLAUDE.md | tất cả | tools registry | T-SEC03-* |
| BR-22 | Handoff có reason code; LLM ngừng trả lời tới khi nhân viên mở khóa | SOURCE FACT | BRD §07 | UC-05 | handoff, conversation | T-UC05-LOCK |
| BR-23 | Notification handoff không chứa dữ liệu nhạy cảm | SOURCE FACT | BRD §07 | UC-05 | handoff | T-SEC05-* |
| BR-24 | Admin hệ thống mặc định không có quyền đọc giá/đoạn chat | SOURCE FACT | BRD §02 | UC-06, UC-07 | RBAC | T-UC06-OPS |
| BR-25 | Nhân viên chỉ xem hội thoại được giao | SOURCE FACT (nguyên tắc) / OPEN (assignment — D-08) | BRD §02 | UC-06 | RBAC + ownership | T-UC06-XTEAM |
| BR-26 | Kill switch: request mới chuyển nhân viên trong ≤60 giây, không gọi provider | SOURCE FACT | BRD §13; SEC-06 | UC-08 | platform KillSwitch | T-SEC06 |
| BR-27 | Thu hồi key: fallback an toàn, không mất dữ liệu | SOURCE FACT | BRD §13; SEC-07 | UC-08 | gateway | T-SEC07 |
| BR-28 | Ảnh phụ tùng xóa EXIF; ảnh đăng kiểm chỉ OCR nội bộ, không gửi bản gốc ra LLM | SOURCE FACT | BRD §04, §08 | UC-02 (MVP) | catalog/vision (MVP) | — (MVP) |
| BR-29 | PoC không gửi ảnh nào tới provider | DESIGN PROPOSAL (A-11) | security-auditor | UC-02 | gateway (không có field ảnh) | T-SEC01-SCHEMA |
| BR-30 | VIN được coi là có thể định danh theo ngữ cảnh; PoC gửi VIN đã mask/WMI-only | SOURCE FACT (rủi ro) BRD §08 / DESIGN PROPOSAL (mask) / OPEN (D-07, D-11) | BRD §08 | UC-02 | policy | T-SEC01-VIN |
| BR-31 | Khách tự gõ số tiền/SĐT: redact và gắn cờ; trùng honeytoken → chặn | DESIGN PROPOSAL / OPEN (A-10) | security-auditor | UC-01 | policy (InboundSanitizer) | T-SEC01-USERMONEY |
| BR-32 | Trạng thái tồn hiển thị cho LLM chỉ là enum `CON_HANG / HET_HANG / CAN_KIEM_TRA`, tính không phụ thuộc số lượng dò | SOURCE FACT (enum, BRD §08) / DESIGN PROPOSAL (chống dò — D-12) | BRD §08 | UC-03 | quote | T-SEC03-STOCK-ORACLE |
| BR-33 | Lưu tin nhắn 12 tháng / ảnh 30 ngày | OPEN (D-09, chỉ là đề xuất) | DECISIONS_REQUIRED | — | — | — |
| BR-34 | Thuế/VAT, đơn vị hàng, đa kho, chính sách khách mới, thời hạn báo giá | OPEN (D-04, BRD §15) | BRD §15 | UC-03 | — | — |
| BR-35 | Model chỉ nhận `NOT_QUOTED` chung cho mọi lý do không báo giá; lý do chi tiết (công nợ, tồn) chỉ nội bộ | DESIGN PROPOSAL (review F1/F2), dẫn xuất từ SOURCE FACT rule 30 §5 | security review 2026-11-13 | UC-03 | quote, tools | T-TOOL-09, T-TOOL-10 |
| BR-36 | Tin nhắn của nhân viên không bao giờ vào history gửi model | DESIGN PROPOSAL (review F3) | security review 2026-11-13 | UC-05, UC-06 | agent | T-SEC01-STAFF |
| BR-37 | VIN đầy đủ được validate server-side và không là tham số tool | DESIGN PROPOSAL (review F5) | security review 2026-11-13 | UC-02 | policy | T-TOOL-13 |
| BR-38 | Recheck khi xác nhận không đạt tồn/công nợ → `CONFIRMATION_BLOCKED` + handoff | DESIGN PROPOSAL (dẫn xuất BRD §06) | architecture review | UC-04 | quote | T-UC04-BLOCKED |
