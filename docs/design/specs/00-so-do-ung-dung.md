<!-- Trích xuất tự động từ bảng canvas `Main.dc.html`. Bản gốc: docs/design/prototypes/Main.dc.html -->

AutoParts Agent — Employee Workspace

# Sơ đồ ứng dụng cho nhân viên

Mã màn hình theo App UX Plan trong design system. Năm luồng công việc A–E dùng chung một khung ứng dụng. Trợ lý AI là bảng bên phải, không phải một mục điều hướng; bảng mở rộng được thành không gian hội thoại (Batch B).

Đang thiết kếWorkflow A, Batch B, C, D và E Có sẵnĐã có trong design system, được dùng lại Bước sauThiết kế sau khi duyệt Workflow A Cần xác nhậnPhạm vi chưa có trong yêu cầu nghiệp vụ

### Khung ứng dụng · SH Đang thiết kế

  * SH-01Khung chính: sidebar thu gọn được, header, vùng làm việc, bảng Trợ lý AI
  * SH-02Tìm nhanh SKU / OE / VIN trên header

  * SH-05Băng thông báo ERP gián đoạn (toàn cục)
  * SH-08Toast thông báo kết quả thao tác

  * SH-03Không có quyền truy cập
  * SH-04Lỗi hệ thống / không tìm thấy trang

  * SH-07Phiên đăng nhập hết hạn Cần xác nhận
  * SH-06Đăng nhập Cần xác nhận

### A · Tìm kiếm sản phẩm Đang thiết kế

  * PL-01Tra cứu phụ tùng (SKU, mã OE, số VIN, tên)
  * PL-02Xác định xe từ số VIN
  * PL-03Chi tiết phụ tùng (drawer + trang đầy đủ)
  * PL-04Giá bán & tồn kho theo chi nhánh (ERP)
  * PL-05Mã tương đương (gợi ý AI, cần kiểm tra ERP)
  * A4–A7Trạng thái tìm kiếm: đang tải, không có kết quả, lỗi, ERP gián đoạn, không có quyền
  * PL-06Thêm vào báo giá — điểm nối sang luồng C Bước sau

### B · Trợ lý AI Đang thiết kế · Batch B

  * AI-01Bảng Trợ lý AI theo ngữ cảnh
  * AI-01Cuộc trò chuyện
  * AI-03Kết quả công cụ có cấu trúc
  * AI-03Lỗi công cụ & trạng thái không có quyền
  * AI-02Xác nhận hành động do AI đề xuất

### C · Báo giá Đang thiết kế · Batch C

  * QT-01Danh sách báo giá
  * QT-02Chi tiết báo giá
  * QT-02Soạn báo giá
  * QT-09Xung đột phiên bản
  * QT-04Lịch sử phiên bản

### D · Đơn hàng nháp Đang thiết kế · Batch D

  * DO-02Xem lại đơn hàng nháp
  * DO-05Xác nhận tạo trên ERP
  * DO-06Đang xử lý, kết quả, kiểm tra trạng thái sau quá thời gian

### E · Màn hình hỗ trợ Đang thiết kế · Batch E

  * DB-01Tổng quan Cần xác nhận
  * AU-01Lịch sử hoạt động
  * PF-01Hồ sơ cá nhân Cần xác nhận
  * SH-03Không có quyền truy cập
  * SH-07Phiên đăng nhập hết hạn

### Điều hướng chính (sidebar)

  * 1Tổng quan — chỉ khi DB-01 được duyệt
  * 2Tra cứu phụ tùng — trang mặc định
  * 3Báo giá
  * 4Đơn hàng nháp
  * 5Hoạt động

Mục người dùng không có quyền được ẩn; mở bằng đường dẫn trực tiếp sẽ dẫn tới SH-03. Header: tìm nhanh, thông báo, bật/tắt Trợ lý AI, tài khoản.

### Luồng A · từ tìm kiếm tới chi tiết

PL-01 Nhập SKU / OE / VIN → PL-02 Xác định xe (VIN) → Kết quả + giá, tồn kho ERP có giờ đồng bộ → PL-03 Drawer chi tiết → PL-03 Trang đầy đủ → PL-06 Thêm vào báo giá (luồng C)
