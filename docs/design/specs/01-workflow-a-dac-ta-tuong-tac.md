<!-- Trích xuất tự động từ bảng canvas `InteractionSpec.dc.html`. Bản gốc: docs/design/prototypes/InteractionSpec.dc.html -->

# Đặc tả tương tác · Workflow A

Các chuyển tiếp trong prototype “A · Tra cứu → Chi tiết”. Trạng thái kiểm tra thực tế của từng chuyển tiếp nằm trong Báo cáo review.

Đặc tả tương tác · Workflow A Mã| Chuyển tiếp| Kích hoạt| Hành vi hệ thống| Focus / trợ năng  
---|---|---|---|---  
T01| Tìm theo SKU / mã OE / tên| Nhấn Enter hoặc “Tìm”| Khóa nút tìm, skeleton 0,9 giây; kết quả lọc đúng theo mã đã nhập (VD 43512-0D150 → 1 dòng); không khớp → A4. Lựa chọn cũ bị xóa.| Focus ở lại ô tìm  
T02| Tìm theo số VIN mô phỏng| Enter ở chế độ VIN| Kiểm tra định dạng trước: sai → lỗi dưới ô, không gửi yêu cầu. Mã nhà sản xuất không có trong dữ liệu mẫu → “VIN không được hỗ trợ”. Hợp lệ nhưng không có ánh xạ → “Không tìm thấy dữ liệu ánh xạ”. Có ánh xạ → dòng xe + nhãn “Ánh xạ VIN mô phỏng”.| Lỗi gắn với ô qua SearchField  
T03| Thử nhanh| Bấm mã mẫu dưới ô tìm| Điền mã và chế độ tương ứng rồi chạy T01/T02. Chỉ có trong bản mô phỏng.| Nút thật, Tab tới được  
T04| Kết quả → drawer chi tiết| Bấm hoặc Enter trên dòng| Drawer 480 px (420 px dưới 1280) mở trên vùng nội dung; Trợ lý AI vẫn mở và đổi ngữ cảnh sang sản phẩm.| Focus chuyển vào drawer  
T05| Đóng drawer| Esc, nút đóng, bấm lớp phủ| Giữ nguyên truy vấn, kết quả và lựa chọn.| Chưa trả focus về dòng đã mở (đã ghi là lỗi còn lại)  
T06| Drawer → trang đầy đủ| “Mở trang đầy đủ”| Trang hai cột (một cột dưới 1180 px); breadcrumb “Tra cứu phụ tùng / SKU”.| —  
T07| Trang → kết quả| “Quay lại kết quả “…””, breadcrumb| Quay về kết quả cũ, không tìm lại; truy vấn và lựa chọn được giữ.| —  
T08| Chọn dòng| Ô chọn trong bảng| Chỉ khi có quyền xem giá và ERP đã xác nhận mới. Lựa chọn chỉ lưu tạm trên màn hình.| Ô chọn có nhãn  
T09| Thêm vào lựa chọn| Nút trong drawer / trang| Bật/tắt cùng tập lựa chọn với bảng; không gửi gì lên máy chủ.| Nhãn nút đổi, không chỉ đổi màu  
T10| Kiểm tra mã tương đương| “Kiểm tra ERP”| Đang kiểm tra… → giá + nhãn ERP mô phỏng + trạng thái tồn + “Xem”. Khóa khi không có quyền hoặc ERP chưa xác nhận mới.| —  
T11| Thêm vào báo giá| Nút trên thanh công cụ khi đã chọn dòng| Không có quyền / chưa xác định: nút khóa trung tính. Có quyền (đề xuất): toast thông tin “chưa khả dụng, thuộc Workflow C, không có dữ liệu nào được lưu”. Không mô phỏng việc lưu báo giá.| —  
T12| Thử lại ERP| “Thử lại” trong Alert hoặc khối giá| Ô và khối chuyển sang đang kiểm tra; bản lưu / dữ liệu cũ / chưa xác minh / quá thời gian → xác nhận mới + toast. Không khả dụng → vẫn không khả dụng, ghi số lần thử.| Nút aria-busy  
T13| Bật / tắt Trợ lý AI| Nút ✦ trên header, nút đóng của panel| Từ 1280 px: panel 400 px đẩy nội dung. Dưới 1280 px: sidebar tự thu gọn, panel nổi đè lên nội dung.| Nút có trạng thái bật  
T14| Đổi kịch bản| Tweaks của artboard| Đặt lại prototype theo màn hình bắt đầu, trạng thái ERP, persona và từng quyền.| —  
T15| Module khác trên sidebar| Bấm mục ngoài Tra cứu| Toast “Ngoài phạm vi Workflow A”.| —  
  
## Ghi chú

  * Tài liệu này thay thế “Interaction Spec” của Workflow A trong design system cho dự án Employee Workspace; bản cũ trong design system được giữ nguyên, không sửa.
  * Chuyển động dùng token của design system (120 / 180 / 240 ms), tắt khi người dùng bật giảm chuyển động.
  * Mọi giới hạn quyền ở đây chỉ là mô phỏng giao diện; máy chủ vẫn phải kiểm tra quyền cho từng yêu cầu.
