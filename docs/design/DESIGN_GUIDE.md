# Design guide — AutoParts Agent Sandbox · Employee Workspace

> Tài liệu tham khảo cho việc dựng frontend từ bộ thiết kế Claude Design. **Không phải** file chỉ dẫn cho Claude Code và không có quyền ưu tiên. Thứ tự ưu tiên vẫn theo `CLAUDE.md` gốc: quyền hệ thống → ADR đã duyệt và `docs/contracts/` → BRD/proposal → các tài liệu khác. Khi tài liệu này lệch với ADR, contract hoặc `docs/security/RBAC_MATRIX.md` thì ADR/contract thắng, và điểm lệch được ghi ở `docs/DECISIONS_REQUIRED.md` §UX.
>
> AutoParts Agent Sandbox is an independent technical demonstration project using synthetic business scenarios and mock ERP data. It is not affiliated with, endorsed by, or commissioned by any automotive parts business or ERP vendor.

## Phạm vi
Không gian làm việc cho **nhân viên** bán phụ tùng: tra cứu phụ tùng (VIN / OE / SKU), xem giá và tồn kho từ ERP đích (Mock ERP trong PoC), lập báo giá, đơn hàng nháp, làm việc với Trợ lý AI. Kênh chat phía khách (Zalo hoặc kênh khác, OPEN D-06) **chưa được thiết kế**; không tự dựng giao diện cho kênh đó.

## Đọc theo thứ tự trước khi dựng UI
1. `design-system/README.md` — luật hình ảnh, nội dung, trạng thái, truy cập, nguồn dữ liệu AI/ERP, icon. Xem thêm `design-system/EXPORT-NOTES.md` (những gì bản xuất **không** có).
2. `design-system/guidelines/writing.md` — luật viết giao diện tiếng Việt.
3. `design-system/CHANGELOG.md` — thay đổi mới nhất của component.
4. `docs/design/decisions/` — phản hồi nghiệp vụ của chủ dự án (01 → 05). Là **DESIGN PROPOSAL** cho tới khi được đưa vào ADR/register.
5. `docs/design/specs/` — đặc tả từng màn hình; `specs/90-danh-muc-man-hinh.md` có ánh xạ màn hình → UC/BR.
6. `docs/design/specs/92-phu-thuoc-va-cau-hoi-mo.md` và `docs/DECISIONS_REQUIRED.md` §UX — những gì **chưa chốt** hoặc đang lệch.

## Nhãn trạng thái trong bộ thiết kế
- **PROPOSED** = đề xuất · **BLOCKED** = chờ nghiệp vụ · **UNKNOWN** = chưa biết · "CÓ CĂN CỨ" = có cơ sở trong tài liệu tham chiếu, **không** có nghĩa đã được duyệt.
- Mã `Dxx` trong `specs/91` là mã nhật ký thiết kế; khi dẫn chiếu ngoài `docs/design/` viết `UX-Dxx` để không nhầm với `D-xx` của `docs/DECISIONS_REQUIRED.md`.
- Không tự biến các mục trên thành luật. Gặp mục chưa chốt thì dừng và hỏi.

## Ghi chú thiết kế đã đối chiếu với nguồn chuẩn
| Chủ đề | Bộ thiết kế nói | Nguồn chuẩn |
|---|---|---|
| VAT | R4 BLOCKED: không giả định đã/chưa gồm VAT, không tự tính tổng thanh toán | ADR-014, D-13 (đang lệch → UX-09) |
| Mock | `MockErpConnector`; kênh chat dùng mô phỏng cục bộ | ADR-005, `docs/contracts/ERP_CONNECTOR.md`, D-06 |
| Quyền | UI ẩn/khóa chỉ để hiển thị; backend tự kiểm tra quyền mọi API | `CLAUDE.md`, `.claude/rules/10-security-boundaries.md`, RBAC_MATRIX |
| AI | Không nhận giá, tồn kho số lượng, công nợ, dữ liệu khách; không tự đổi giá, duyệt, tạo đơn; thao tác ghi do AI đề xuất phải qua `ActionConfirmation` | `CLAUDE.md`, `docs/contracts/TOOL_CONTRACTS.md` |
| Chống tạo trùng | Khóa idempotency do server sinh; quá thời gian → "Chờ đối soát", không báo thất bại | ADR-009 (khóa `quoteId:version`, mặc định đối soát thủ công → UX-08) |
| Thành công | "Đang …" khi chờ, "Đã …" chỉ sau phản hồi thành công | `design-system/guidelines/writing.md` |
| Lỗi | Không lộ stack trace, mã HTTP, endpoint, SQL, token, tên vai trò nội bộ; chỉ hiện mã tham chiếu | rule 10 (logs/exception) |
| Phiên đăng nhập | Đề xuất 8 giờ tối đa / 30 phút không hoạt động; không tự tiếp tục thao tác ghi sau khi đăng nhập lại | ADR-010 chưa quy định → UX-12 (PROPOSED) |
| Audit | Mọi thao tác ghi và mọi lần thao tác thay người khác | ADR-012 |

## Giao diện
- Dùng component trong `design-system/components/` (kiểu trong `components/index.d.ts`). Không dùng `components/bundle.js` (IIFE tự render demo); dùng mã nguồn `components/<nhóm>/*.jsx`, đủ từ 25/11/2026 (A-18(b), A-66) — xem `design-system/EXPORT-NOTES.md`.
- Token: `design-system/tokens.json` / `tokens.css`. Không hard-code màu, cỡ chữ, khoảng cách.
- Font Be Vietnam Pro (`design-system/assets/fonts/`). Icon chỉ Lucide 0.460.0 (`assets/icons/`).
- Toàn bộ chữ trên giao diện bằng tiếng Việt, kiểu đặt dấu mới (Hủy, Xóa, Khóa).
- Prototype `docs/design/prototypes/*.dc.html`: mã trong `<script type="text/x-dc">` là logic mô phỏng của canvas, **không phải** mã sản phẩm; không mở trực tiếp được trong trình duyệt.
- Logo `design-system/assets/logo/`: **PROPOSED** (A-17), chưa duyệt.

## Cách làm việc
- Theo batch: A (tra cứu) → B (Trợ lý AI) → C (báo giá) → D (đơn nháp) → E (đăng nhập, dashboard, nhật ký, hồ sơ) → F (màn hình UX-13: chat khách qua trình mô phỏng, hộp chờ và hội thoại nhân viên, Security Inspector, công tắc AI; `specs/50-batch-f-man-hinh-thieu.md`, prototype `F*.dc.html`, đã duyệt 23/11/2026). Mỗi batch là một issue theo `.claude/skills/implement-issue` và `ui-from-claude-design`: kế hoạch → duyệt → code → test.
- Trước khi code một batch, giải quyết các mục UX-xx liên quan trong `docs/DECISIONS_REQUIRED.md`.
- Test cho các trạng thái trong đặc tả (đang tải, rỗng, lỗi ERP, timeout, không có quyền, chờ đối soát). Không ghi "đã kiểm tra" khi chưa chạy test thật.
