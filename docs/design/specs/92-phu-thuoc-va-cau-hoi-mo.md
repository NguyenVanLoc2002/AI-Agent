<!-- Trích xuất tự động từ bảng canvas `Dependencies.dc.html`. Bản gốc: docs/design/prototypes/Dependencies.dc.html -->

# Phụ thuộc về quyền và câu hỏi nghiệp vụ mở  
  
Cập nhật theo phản hồi review ngày 16/11/2026. Giai đoạn hiện tại là UX prototype: không có quy tắc nghiệp vụ, hợp đồng API hay cơ chế bảo mật nào được phê duyệt. Giới hạn trên giao diện không thay thế kiểm tra quyền ở máy chủ.

ĐỀ XUẤTDùng trong prototype, chưa phê duyệt CHƯA XÁC ĐỊNHChưa có thông tin — giao diện coi như không có quyền BỊ CHẶNChặn thiết kế luồng sau

Persona mô phỏng (chỉ phục vụ kiểm tra giao diện) Persona| Nhãn trong giao diện| Quyền mặc định| Ghi chú  
---|---|---|---  
Sales Employee| Nhân viên kinh doanh (mô phỏng)| Không có| Persona chỉ là nhãn. Mọi quyền được bật/tắt riêng bằng Tweaks (Đề xuất: có / Không / Chưa xác định). Không persona nào được mặc định có quyền xem giá, sửa giá, xem liên chi nhánh, quản lý báo giá hay tạo đơn nháp.  
Restricted Employee| Nhân viên hạn chế (mô phỏng)| Không có  
Sales Supervisor| Giám sát kinh doanh (mô phỏng)| Không có  
System Administrator| Quản trị hệ thống (mô phỏng)| Không có  
  
Capability dùng trong Workflow A Capability| Kiểm soát| Khi không có / chưa xác định| Bề mặt bị ảnh hưởng| Trạng thái  
---|---|---|---|---  
parts.search| Tra cứu danh mục, thông số, xe tương thích| Luôn bật trong prototype| Kết quả, chi tiết| ĐỀ XUẤT  
erp.price.view| Xem giá bán| Ô giá khóa “Hạn chế” / “Chưa xác định quyền”; giá không được tải về giao diện; AI trả công cụ “không có quyền”; không chọn dòng| Bảng kết quả, drawer, trang, AI, mã tương đương| CHƯA XÁC ĐỊNH  
erp.stock.view| Xem tồn kho| Ô tồn khóa; khối tồn kho ẩn; AI không nhận số tồn| Bảng kết quả, drawer, trang, AI| CHƯA XÁC ĐỊNH  
branch.scope| Phạm vi chi nhánh được xem (đối tượng)| Chỉ chi nhánh trong phạm vi được tải; phạm vi chưa xác định → ẩn toàn bộ tồn kho. Chi nhánh ngoài phạm vi không xuất hiện kể cả dạng khóa| Khối tồn kho, AI| CHƯA XÁC ĐỊNH  
quote.line.add| Thêm sản phẩm vào báo giá| Nút khóa trung tính. Khi có quyền: nút vẫn chỉ là điểm nối tới luồng C, không lưu gì| Thanh công cụ khi chọn dòng| BỊ CHẶN · luồng C  
ai.use| Mở Trợ lý AI| Chưa mô phỏng tắt; AI luôn chỉ dùng quyền của người dùng| AI panel| CHƯA XÁC ĐỊNH  
export| Xuất danh sách| Không có nút xuất trong Workflow A| —| NGOÀI PHẠM VI  
  
Câu hỏi nghiệp vụ — trạng thái sau review Mã| Câu hỏi| Quyết định hiện tại| Trạng thái  
---|---|---|---  
B6| Vai trò và quyền chính thức| Phân quyền nhật ký đã chọn (D59). Vai trò, quyền chi tiết khác vẫn theo persona mô phỏng.| MỘT PHẦN  
R4| Giá đã gồm VAT hay chưa| Không giả định; không tính VAT, không hiển thị tổng thanh toán (D47). Chờ chủ nghiệp vụ và đơn vị phụ trách ERP.| BLOCKED  
B3| Nguồn giải mã VIN| Bảng ánh xạ VIN mẫu cố định (fixtures). Không dùng dịch vụ thật, không để AI suy đoán xe tương thích.| ĐÃ TRẢ LỜI cho demo  
R5| Phạm vi xem tồn kho| Chỉ chi nhánh trong phạm vi được cấp; không tự cấp toàn hệ thống; không truyền dữ liệu ngoài phạm vi tới component hay AI.| ĐÃ TRẢ LỜI cho demo  
E1| Ngưỡng hiệu lực của dữ liệu ERP (khi nào bản lưu trở thành dữ liệu cũ)| Chưa có. Prototype giả lập 60 phút và ghi rõ là giả lập.| MỞ · mới  
E2| Có được hiển thị bản lưu (cached) cho người dùng không, và dùng vào việc gì| Prototype hiển thị mờ kèm nhãn “Bản lưu · chưa xác nhận lại”, không cho chọn hay báo giá.| MỞ · mới  
R1| Hàng sắp hết / hết hàng có được thêm vào báo giá| Chưa thiết kế (thuộc luồng C).| MỞ · luồng C  
R7| Nguồn mã tương đương| Gắn nhãn “Gợi ý AI · chưa xác minh”; phải “Kiểm tra ERP” mới thấy giá.| MỞ  
R9| Ngưỡng quá thời gian ERP, chính sách thử lại| Giả lập 10 giây, thử lại thủ công.| MỞ  
R10| Kênh xin thêm quyền| Quản lý trực tiếp → IT / quản trị (D62). Chưa có hệ thống tiếp nhận: điểm nối.| ĐÃ QUYẾT · ĐIỂM NỐI  
B1| Trạng thái báo giá và bước duyệt| Luồng của tài liệu tham chiếu; không duyệt báo giá; duyệt đơn tại ERP (D40).| CÓ CĂN CỨ  
B2| Chiết khấu| AI không chiết khấu; giá theo ERP (D38). Chiết khấu thủ công, mức tối đa, cấp phê duyệt: chờ chủ nghiệp vụ.| CÓ CĂN CỨ · còn mở  
Q1| Phiên bản đã phát hành có sửa được không| Khóa sau phát hành, thay đổi bằng phiên bản mới (D39, D48).| PROPOSED  
Q2| Thời hạn hiệu lực báo giá| Mô phỏng 3 ngày từ lúc phát hành, cấu hình backend (D44). Thời hạn chính thức, cách tính ngày, ngày nghỉ: chờ chủ nghiệp vụ.| PROPOSED  
Q3| Nguồn khách hàng, tạo khách mới| ERP là nguồn có thẩm quyền; AI không tạo khách (D45). Giá khách mới, quy trình tạo/liên kết: chờ chủ nghiệp vụ.| CÓ CĂN CỨ · còn mở  
Q4| Báo giá mã hết hàng| Không phát hành báo giá cam kết cung ứng, chuyển nhân viên (D46). Hàng đặt trước/đang nhập/giao thiếu: chờ chủ nghiệp vụ.| PROPOSED  
Q6| Kênh gửi báo giá| Zalo OA; chỉ ghi đã gửi khi dịch vụ xác nhận (D43). Demo: điểm nối mô phỏng.| CÓ CĂN CỨ  
B4 · B5| Thẩm quyền đơn nháp; “Tạo trên ERP”| Batch D theo lựa chọn 17/11: nhân viên tạo, duyệt trong ERP (D49, D50). Người lập vắng: trưởng bộ phận / phân công lại + audit (D54). Timeout 30 giây demo, cần xác nhận theo API ERP đích (D57). Cơ chế thông báo chưa quy định (D56).| PROPOSED  
Q5| Danh sách công cụ AI được phép, và Trợ lý có được đọc giá/tồn thay người dùng không| Theo tài liệu giải pháp 1.0: 4 công cụ Agent + tra cứu nội bộ do backend gọi. Backend được truy vấn có ủy quyền; LLM không nhận giá, số lượng tồn, dữ liệu khách. Lập báo giá, tạo đơn nháp thuộc MVP; sửa giá, chiết khấu, duyệt đơn không giao cho AI.| ĐÃ TRẢ LỜI cho thiết kế · tài liệu là bản đề xuất, chưa phải API đã có  
Q7 · Q8| Xác thực và hết phiên; Tổng quan| Q7: tài khoản nội bộ, 8 giờ / 30 phút (D60), SSO chờ xác minh. Q8: chấp thuận có điều kiện theo vai trò (D58), cần đối chiếu widget thực tế.| ĐÃ QUYẾT · ĐỀ XUẤT  
AI1| Có lưu lịch sử hội thoại không, bao lâu, ai xem được| Có lưu. Hội thoại khách hàng 12 tháng (đề xuất, chờ chủ nghiệp vụ duyệt). Thời hạn lưu hội thoại nội bộ và audit log chưa xác định. Xem theo vai trò: nhân viên — được phân công; trưởng bộ phận — bộ phận; quản trị — không xem nội dung kinh doanh mặc định.| ĐÃ TRẢ LỜI một phần · thời hạn nội bộ MỞ  
AI2| “Dừng” có hủy truy vấn ở máy chủ không| Chưa có hợp đồng hủy. Giao diện chỉ ngừng nhận kết quả, không báo “đã hủy”; không hủy/tạo lại báo giá, đơn dựa trên trạng thái streaming.| MỞ · cần API contract  
AI3| Những thao tác nào cần xác nhận; đề xuất có hết hạn không| Tra cứu: không cần. Lựa chọn tạm: thao tác chọn trực tiếp. Tạo đơn nháp: khách xác nhận theo mã + phiên bản báo giá. Báo giá có hạn (ví dụ 3 ngày, chờ chốt); gợi ý AI và lựa chọn tạm chưa có TTL.| ĐÃ TRẢ LỜI cho Batch B · thời hạn báo giá MỞ  
AI4| Ngưỡng quá thời gian và chính sách thử lại của dịch vụ AI| Chưa có ngưỡng; chốt sau khi có hợp đồng API và đo thực tế. Timeout sau lệnh tạo đơn phải đối soát, không tự thử lại.| MỞ  
AI5| Trợ lý trả lời thế nào khi được hỏi về dữ liệu ngoài phạm vi| Dùng câu trả lời đề xuất trong phản hồi; backend từ chối trước khi dữ liệu tới LLM/UI. Mô hình chi nhánh/kho chưa chốt.| ĐỀ XUẤT · mô hình quyền MỞ  
VR| Quy tắc gắn nhãn “Đã xác minh”| Ba nhãn theo mục 5; chỉ mã Đã xác minh được lập báo giá tự động; không tự đặt ngưỡng confidence. Checklist đối chiếu: chờ chủ nghiệp vụ.| CÓ CĂN CỨ · còn mở  
HO| Người nhận và quyền của “Chuyển nhân viên” trong giao diện nhân viên| Prototype ghi “do hệ thống điều phối — quy tắc chưa xác định”.| MỞ · mới  
  
Phụ thuộc vào design system Thành phần| Vấn đề| Cách xử lý tạm trong prototype  
---|---|---  
SourceTag| Chưa có nguồn “dữ liệu cũ”, “chưa xác minh”, “danh mục”, “mô phỏng”.| Dùng source erp/cache với nhãn tùy chỉnh; các trạng thái còn lại là chữ + biểu tượng  
ERPResult| Chưa có trạng thái riêng cho quá thời gian, dữ liệu cũ, chưa xác minh.| state error + tiêu đề phụ mô tả trạng thái  
Drawer| Chế độ modal che cả Trợ lý AI; chế độ inline không tự giữ focus.| Drawer inline trong lớp phủ vùng nội dung; tự chuyển focus vào khi mở, Esc để đóng  
DataTable| Không khóa chọn từng dòng.| Cả bảng chỉ chọn được khi giá đã được ERP xác nhận mới  
AppHeader| Chưa có menu tài khoản.| Hiển thị tên + persona mô phỏng  
ChatMessage| Tiêu đề tin nhắn luôn ghi “Gợi ý, cần kiểm tra”.| ĐÃ SỬA 17/11: thêm prop label; Batch B gắn nhãn theo từng phần. Workflow A chưa đổi  
ToolCall| Chưa có trạng thái “chờ”, “chưa rõ”, “bản lưu”.| ĐÃ SỬA 17/11: thêm pending, unknown. Bản lưu vẫn dùng “Quá thời gian” + lời nhắn  
ActionConfirmation| Chưa có trạng thái “hết hạn”.| Chưa thiết kế (M4)  
EmptyState| Thuộc tính code tự thêm “Mã tham chiếu:”.| ĐÃ SỬA trong Workflow A và Batch B (truyền mã trần)  
Bảng trong hội thoại| DS chưa có bảng gọn cho kết quả công cụ.| Bảng HTML theo token  
ERPResult · AIComposer · SourceTag| Dữ liệu ERP tự gọi là “Đã xác minh”; câu lưu ý cũ; thiếu nhãn Dữ liệu tra cứu / Đã xác minh.| ĐÃ SỬA 17/11: state lookup + label; disclaimer mới; nguồn lookup, verified
