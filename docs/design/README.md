# Tài liệu thiết kế — AutoParts Agent · Employee Workspace

Xuất ngày 17/11/2026 từ canvas thiết kế và design system trên **Claude Design** (thay cho kế hoạch Stitch trước đây). Đọc trước: [`DESIGN_GUIDE.md`](DESIGN_GUIDE.md).

> AutoParts Agent Sandbox is an independent technical demonstration project using synthetic business scenarios and mock ERP data. It is not affiliated with, endorsed by, or commissioned by any automotive parts business or ERP vendor.

| Thư mục / file | Nội dung |
|---|---|
| `DESIGN_GUIDE.md` | Điểm vào: thứ tự đọc, nhãn trạng thái, đối chiếu với ADR/contract, cách làm việc theo batch. |
| `specs/` | Đặc tả dạng Markdown, trích từ các bảng đặc tả trên canvas: sơ đồ ứng dụng, Workflow A, Batch B–E, danh mục màn hình (kèm ánh xạ UC), nhật ký quyết định, phụ thuộc và câu hỏi mở, logo. |
| `decisions/` | Phản hồi nghiệp vụ của chủ dự án. 01–03 là tài liệu chủ dự án gửi; 04–05 ghi lại từ hội thoại. Là DESIGN PROPOSAL cho tới khi vào ADR / `docs/DECISIONS_REQUIRED.md`. |
| `prototypes/` | 175 bảng `.dc.html` của canvas và `canvas.json`. Chỉ để tham khảo hành vi: file cần trình chạy của canvas nên **không mở trực tiếp được** trong trình duyệt. |
| `evidence/` | Ảnh chụp lúc chạy thử prototype trong Chrome. **Không đưa vào git** (`.gitignore`): ảnh được chụp trước khi trung lập tên vendor; cần chụp lại (A-18). |

## Trạng thái
- "Đã thiết kế" = có trên canvas. "Đã rà soát" = đã chạy thử thật trong Chrome. **Chưa màn hình nào "Đã phê duyệt".**
- Mọi tích hợp (Quote Service, ERP, kênh chat, phân quyền, idempotency) trong prototype đều là **mock**, chưa phải bằng chứng hoạt động.
- Tên ERP vendor và doanh nghiệp tham chiếu đã được thay bằng "ERP đích" / "ERP" / "chủ nghiệp vụ" (17/11/2026) theo `docs/REBRANDING_REPORT.md`. Bản canvas gốc trên Claude Design vẫn còn tên cũ; khi xuất lại cần trung lập lần nữa.
- Điểm lệch với ADR, contract và RBAC: `docs/DECISIONS_REQUIRED.md` §UX (UX-01..UX-18).

## Chưa thiết kế
Kênh chat phía khách hàng (Zalo hoặc kênh khác, OPEN D-06) và hộp thư chuyển giao cho nhân viên; Security Inspector; kill switch; quên mật khẩu / SSO; màn quản trị người dùng và phân công lại; hệ thống tiếp nhận yêu cầu cấp quyền (UX-13).
