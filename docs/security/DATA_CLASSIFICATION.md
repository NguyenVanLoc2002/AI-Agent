# Data classification & LLM egress policy — v0.1 DRAFT

> 2026-11-13. Đây là bản versioned của danh sách DENY/ALLOW trong BRD §08 (`docs/01_BRD_SRS_Security.md@af2d09f`) và rule 10. Khi mâu thuẫn, ADR đã duyệt và `docs/contracts/` thắng văn xuôi (CLAUDE.md).
> Pháp lý chuyển dữ liệu ra nước ngoài, VIN như dữ liệu cá nhân, retention: **OPEN** (D-07, D-09, D-11). Chưa VERIFIED.

## 1. Lớp dữ liệu (SOURCE FACT rule 10)
| Lớp | Định nghĩa | Ví dụ |
|---|---|---|
| RESTRICTED | cost/margin, giá theo khách, công nợ/tín dụng, danh bạ khách, lịch sử mua, credentials, thông tin cá nhân | purchaseCost, margin, customerPrice, unitPrice/total của quote, creditBalance, creditLimit, eligibility, customerId, tên/SĐT/địa chỉ, API key, ảnh đăng kiểm gốc |
| INTERNAL | số lượng tồn/vị trí, trạng thái nội bộ quote | inventory.qty, warehouse, quoteId, version, idempotency key, external ref |
| APPROVED_TECHNICAL | VIN/OE đã validate, nhóm sản phẩm, mô tả kỹ thuật công khai, ảnh phụ tùng đã sanitize | xem §2 |

## 2. Ma trận egress tới LLM
| Trường / dữ liệu | Lớp | Tới LLM | Nhãn | Ghi chú |
|---|---|---|---|---|
| purchaseCost, margin | R | ✘ | SOURCE FACT | sentinel 987654321 |
| customerPrice, unitPrice, lineTotal, totalAmount | R | ✘ | SOURCE FACT | render server-side |
| creditBalance, creditLimit | R | ✘ | SOURCE FACT | sentinel 123456789 |
| credit eligibility flag | R | ✘ | SOURCE FACT rule 30 §5 | tool chỉ trả `NOT_QUOTED` chung cho mọi lý do (không phân biệt công nợ / hết hàng) |
| Lý do không báo giá (công nợ, tồn theo số lượng) | R/I | ✘ | DESIGN PROPOSAL (review F1/F2) | chỉ trong handoff nội bộ và audit |
| Tin nhắn của nhân viên | R tiềm năng | ✘ | DESIGN PROPOSAL (review F3) | không vào history model |
| customerDirectory, purchaseHistory | R | ✘ | SOURCE FACT | không có port |
| customer identity (id, tên, SĐT, email, địa chỉ, Zalo id) | R | ✘ | SOURCE FACT | |
| secrets/tokens | R | ✘ | SOURCE FACT | |
| Ảnh đăng kiểm | R | ✘ | SOURCE FACT BRD §04 | OCR nội bộ (MVP) |
| Số lượng tồn, kho | I | ✘ | SOURCE FACT rule 30 §5 | chỉ bucket |
| quoteId, version, idempotency key | I | ✘ | DESIGN PROPOSAL | chỉ opaque handle |
| Tin nhắn đã lọc DLP | T | ✔ có điều kiện | SOURCE FACT BRD §08 | sau inbound sanitize |
| VIN | T (nhạy cảm theo ngữ cảnh) | ✔ **chỉ dạng mask/WMI** trong PoC; VIN đầy đủ validate server-side, không là tham số tool | SOURCE FACT (allow) + DESIGN PROPOSAL (mask) + OPEN D-07/D-11 | BRD §08 lưu ý VIN có thể là dữ liệu cá nhân |
| Mã OE do khách đưa / nguồn hợp lệ | T | ✔ | SOURCE FACT | |
| Tên nhóm phụ tùng, model xe | T | ✔ | SOURCE FACT | |
| Số lượng yêu cầu | T | ✔ | SOURCE FACT | |
| SKU đã xác minh + provenance | T | ✔ | SOURCE FACT | |
| Tồn enum `CON_HANG/HET_HANG/CAN_KIEM_TRA` | T | ✔ | SOURCE FACT | chống dò: D-12 |
| Opaque quote handle | T | ✔ | SOURCE FACT BRD §05 | random, bind, TTL |
| Ảnh phụ tùng (đã xóa EXIF) | T | ✘ trong PoC | SOURCE FACT cho phép (BRD §08) / DESIGN PROPOSAL chặn trong PoC (A-11) | MVP sau D-07 |
| Trường chưa phân loại | — | ✘ | SOURCE FACT "reject on unclassified fields" | fail-closed |

## 3. Sink khác ngoài LLM
| Sink | Được phép ghi | Cấm |
|---|---|---|
| Log ứng dụng | event ID, traceId pseudonymous, policy code, field names, status code | body request/response, giá, công nợ, PII, secrets, exception cause thô |
| audit_event / egress_record | như trên + honeytoken ID + length | giá trị gốc; HMAC/hash theo từng giá trị (cho phép so sánh giá trị và brute-force số entropy thấp) |
| Security inspector UI | dữ liệu từ egress_record/audit | raw payload, kể cả với admin (rule 50) |
| Test artifacts | fixture synthetic | dữ liệu thật |
| Thông báo lỗi tới khách | mã lỗi + thông điệp chung | chi tiết nội bộ |

## 4. Honeytoken registry (PoC) — DESIGN PROPOSAL
| Token | Ý nghĩa | Nguồn |
|---|---|---|
| `987654321` | giá vốn sentinel | SOURCE FACT SEC-01 |
| `123456789` | công nợ sentinel | SOURCE FACT SEC-01 |
| giá theo khách sentinel riêng cho khách A (vd `555444333`) và khách B (vd `666777888`) | customerPrice — phân biệt rò của ai (SEC-02) | DESIGN PROPOSAL |
| tên/SĐT khách giả có marker | identity | DESIGN PROPOSAL |
| API key giả có prefix nhận diện | secrets | DESIGN PROPOSAL |
Biến thể bắt buộc phát hiện: xem `docs/contracts/AI_GATEWAY.md` §2.3.

## 5. Inbound (khách tự gõ) — DESIGN PROPOSAL, OPEN A-10
Số tiền/SĐT/email trong tin khách → redact + gắn cờ; trùng honeytoken → chặn + handoff. Trường hợp khách nhắc lại giá đã báo (không phải sentinel) chỉ bị chặn nhờ pattern tiền tệ — có test riêng (T-SEC01-USERMONEY với giá không phải sentinel). Nhắc: regex masking không đảm bảo ẩn danh (SOURCE FACT BRD §08).
