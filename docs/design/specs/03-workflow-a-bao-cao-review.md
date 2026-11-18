<!-- Trích xuất tự động từ bảng canvas `ReviewReport.dc.html`. Bản gốc: docs/design/prototypes/ReviewReport.dc.html -->

# Báo cáo review · Workflow A

Kết quả sau review có điều kiện. “Đã kiểm tra” nghĩa là đã thao tác thật trên canvas trong trình duyệt; “Một phần” nghĩa là chỉ kiểm tra được một phần như ghi chú.

A · Những gì đã sửa Chủ đề| Thay đổi  
---|---  
Quyền| 4 persona mô phỏng; quyền bật/tắt riêng (Đề xuất / Không / Chưa xác định); băng “Chế độ mô phỏng UX” nói rõ giao diện không thay thế kiểm tra ở máy chủ  
Nguồn dữ liệu ERP| 6 trạng thái: xác nhận mới, bản lưu, dữ liệu cũ, chưa xác minh, quá thời gian, không khả dụng. Mọi nhãn ghi “ERP mô phỏng”. Chỉ “xác nhận mới” cho chọn sản phẩm  
VIN| Bảng ánh xạ VIN mẫu cố định; 4 trạng thái; AI không suy đoán xe tương thích  
Phạm vi chi nhánh| Chỉ chi nhánh trong phạm vi được tải; bỏ hàng TP.HCM / Đà Nẵng bị khóa và thẻ công cụ AI nhắc TP.HCM  
VAT| “VAT: chưa xác định” cạnh giá; không tính thuế, không tính tổng  
Tìm kiếm| Kết quả lọc đúng theo SKU / OE (bản trước luôn trả 8 dòng)  
Báo giá| “Thêm vào báo giá” chỉ là điểm nối luồng C, không mô phỏng việc lưu  
Phát hiện khi kiểm tra và đã sửa| Khung cố định 900 px làm cắt chân drawer → khung theo chiều cao cửa sổ; thẻ ERPResult tự ghi “Đã xác minh từ ERP” → “Đã xác minh từ ERP mô phỏng”; bố cục 1280 / 1024 (sidebar tự thu gọn dưới 1360 px, AI nổi và mặc định đóng dưới 1180 px)  
  
B · Màn hình / thành phần đã sửa Tệp| Nội dung  
---|---  
WorkflowA.dc.html| Prototype chính: quyền, ERP, VIN, AI, phản hồi theo bề rộng  
PartDetail.dc.html| Khối chi tiết dùng chung: provenance, phạm vi chi nhánh, VAT  
AppShell.dc.html| Chiều cao theo cửa sổ  
19 artboard trạng thái| Cập nhật / thêm mới: A3 ×3, A5 ×5, A6 ×3, A8, R-1280, R-1024  
Tài liệu| Danh mục màn hình, Quyền & câu hỏi mở (cập nhật); Ma trận trạng thái, Đặc tả tương tác, Nhật ký quyết định, Báo cáo này (mới)  
Design system| Không sửa thành phần nào  
  
C · Tương tác đã kiểm tra thật (Chrome, canvas ở chế độ Play, 16/11/2026) #| Luồng| Kết quả| Ghi chú  
---|---|---|---  
1| Tìm theo SKU, OE, VIN mô phỏng| Đã kiểm tra| BP-1042 → 1 dòng; 43512-0D150 → 1 dòng; VIN mẫu → 8 dòng + dòng xe; VIN không hợp lệ / không hỗ trợ / không có ánh xạ đều hiện đúng  
2| SKU không tồn tại BP-9999X| Đã kiểm tra| EmptyState “Không tìm thấy” + gợi ý; AI báo không tìm thấy  
3| Chọn sản phẩm| Đã kiểm tra| Chọn qua nút trong drawer; thanh công cụ chuyển sang “Đã chọn 1”  
4| Mở drawer chi tiết| Đã kiểm tra| Bằng chuột và bằng phím Enter trên dòng  
5| Mở trang đầy đủ| Đã kiểm tra| Breadcrumb, hai cột, lựa chọn giữ nguyên  
6| Quay lại, giữ ngữ cảnh| Đã kiểm tra| Truy vấn, kết quả và lựa chọn còn nguyên, không tìm lại  
7| Đổi trạng thái ERP mô phỏng| Một phần| Đã xem cả 5 trạng thái qua artboard dùng chung tham số; Thử lại từ bản lưu → xác nhận mới + toast. Chưa thao tác trực tiếp bảng Tweaks  
8| Đổi quyền mô phỏng| Một phần| Đã xem: không có quyền giá & tồn (bảng, drawer, AI), chưa xác định quyền / phạm vi, báo giá chưa xác định và có quyền. Chưa xem artboard “không có quyền báo giá”  
9| Mở / đóng Trợ lý AI| Đã kiểm tra| Ở 1568 px và 1024 px (nổi đè nội dung)  
10| Đang tải, trống, quá thời gian, không khả dụng, không có quyền| Một phần| Trống, quá thời gian, không khả dụng, không có quyền: đã xem. Đang tải: chỉ thấy thoáng qua khi tìm và khi thử lại  
11| Bàn phím & focus| Một phần| Tab đi qua ô tìm, mã mẫu, tiêu đề cột, dòng; Enter mở drawer; Esc đóng drawer. Chưa thấy được vòng focus sau khi drawer mở; focus không quay về dòng khi đóng  
12| 1440 / 1280 / 1024 px| Một phần| Đã xem ở chế độ Fit. 1024: đạt. 1280 và 1440 (sidebar mở + AI mở): cột “Nguồn” phải cuộn ngang  
  
D · Lỗi hình ảnh / chức năng còn lại Mức| Vấn đề| Hướng xử lý  
---|---|---  
Trung bình| Cột “Nguồn” bị đẩy ra ngoài, phải cuộn ngang ở 1280–1440 px khi mở Trợ lý AI| Thu gọn cột nguồn thành biểu tượng + chú giải, hoặc thu sidebar dưới 1500 px  
Trung bình| Focus không quay về dòng đã mở khi đóng drawer; vòng focus trong drawer chưa xác nhận được| Cần Drawer hỗ trợ giữ focus trong vùng nội dung (phụ thuộc design system)  
Trung bình| Câu cảnh báo của AIComposer (design system) nói giá “chỉ có giá trị khi có nhãn Đã xác minh từ ERP” — mâu thuẫn quy tắc “giờ đồng bộ không chứng minh là hiện tại”| Sửa chữ trong design system (chưa sửa, ngoài phạm vi canvas)  
Thấp| Nội dung hội thoại AI là kịch bản cố định: sau khi tìm SKU, AI vẫn hiển thị hội thoại về VIN| Chấp nhận cho prototype hoặc gắn kịch bản theo truy vấn  
Thấp| Ô tìm nhanh trên header và chuông thông báo chưa có hành vi| Thiết kế cùng SH-02 / Q9  
Thấp| Trong chế độ Play của canvas, phím Esc vừa đóng drawer vừa thoát Play (hành vi của khung xem)| Không sửa được trong artboard  
—| Hai sửa nhỏ đăng sau lượt kiểm tra cuối chưa được xem lại: AI hiện “đang tải” khi thử lại ERP; thẻ công cụ dùng trạng thái lỗi cho “dữ liệu cũ / chưa xác minh”| Xem lại ở lượt review sau  
—| Chưa bấm thử: Khung ứng dụng (SH-01), lỗi tải danh mục, drawer và trang ở 1024 px, tooltip của nút bị khóa| Xem lại ở lượt review sau  
  
E · Phụ thuộc nghiệp vụ chưa giải quyết Mã| Nội dung| Ảnh hưởng  
---|---|---  
B6| Vai trò và quyền chính thức| Chặn luồng C, D  
R4| VAT| Chặn tính tổng báo giá  
E1 · E2| Ngưỡng hiệu lực dữ liệu ERP; có được hiển thị bản lưu không| Ảnh hưởng A5  
R1 · R7| Hàng hết / sắp hết khi báo giá; nguồn mã tương đương| Luồng C  
B1 · B2 · Q1| Trạng thái báo giá, giá & chiết khấu, phiên bản| Chặn luồng C  
B4 · B5| Thẩm quyền đơn nháp; tạo trên ERP| Chặn luồng D  
Q5| Công cụ AI được phép gọi| Cần cho luồng B  
Q7 · Q8| Xác thực, hết phiên; Tổng quan, Hồ sơ| Luồng E  
  
F · Ảnh chụp bằng chứng (gửi kèm trong cuộc trò chuyện) Ảnh| Artboard  
---|---  
Kết quả VIN mẫu, 1440 px| A · Prototype  
Tìm BP-9999X| A4  
Drawer chi tiết BP-1042| A2  
Trang đầy đủ| A2  
Quay lại giữ lựa chọn| A · Prototype  
Bản lưu chưa xác nhận lại| A5  
Không có quyền giá & tồn| A6  
Thêm vào báo giá — chờ luồng C| A8  
1280 px| R  
1024 px, AI nổi| R  
  
## G · Mức sẵn sàng

  * Workflow A: sẵn sàng cho lượt review UX tiếp theo. Chưa được phê duyệt; còn 3 lỗi mức trung bình ở mục D.
  * Có thể bắt đầu luồng B (Trợ lý AI) khi có câu trả lời Q5. Luồng C và D bị chặn bởi B1, B2, Q1, B4, B5, B6.
  * Không có kiểm tra nào về phân quyền máy chủ, tích hợp ERP hay giao dịch thật. Mọi dữ liệu là dữ liệu mô phỏng.
  * Chưa chuyển sang Workflow B hoặc C — chờ người duyệt quyết định.
