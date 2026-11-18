<!-- Trích xuất tự động từ bảng canvas `DecisionLog.dc.html`. Bản gốc: docs/design/prototypes/DecisionLog.dc.html -->

# Nhật ký quyết định thiết kế  

> Mã `Dxx` dưới đây là mã nhật ký thiết kế. Khi dẫn chiếu bên ngoài `docs/design/` viết `UX-Dxx` để không nhầm với `D-xx` trong `docs/DECISIONS_REQUIRED.md`. “Áp dụng” không có nghĩa là đã được duyệt; các điểm lệch với ADR/contract/RBAC: `docs/DECISIONS_REQUIRED.md` §UX.
  
Những thay đổi thiết kế của Workflow A sau review có điều kiện. “Áp dụng” nghĩa là đã có trong prototype, không có nghĩa là đã phê duyệt.

Nhật ký quyết định thiết kế · 16–17/11/2026 Mã| Quyết định| Nguồn| Tình trạng  
---|---|---|---  
D01| Dùng 4 persona mô phỏng, quyền bật/tắt riêng theo capability; không persona nào có quyền mặc định| Review B6| Áp dụng  
D02| Quyền “chưa xác định” được xử lý như không có quyền, với nhãn riêng “Chưa xác định quyền”| Review B6| Áp dụng  
D03| Hiển thị băng “Chế độ mô phỏng UX” trên mọi màn hình Workflow A, ghi rõ quyền là đề xuất và không thay thế kiểm tra ở máy chủ| Review mục 3| Áp dụng  
D04| Sáu trạng thái dữ liệu ERP: xác nhận mới, bản lưu, dữ liệu cũ, chưa xác minh, quá thời gian, không khả dụng| Review mục 2| Áp dụng  
D05| Chỉ “xác nhận mới” được dùng để chọn sản phẩm; bản lưu hiển thị mờ có nhãn; các trạng thái còn lại ẩn số| Review mục 2| Đề xuất · chờ E2  
D06| Mọi nhãn ERP ghi “ERP mô phỏng”; không trình bày dữ liệu mẫu như ERP thật| Review mục 2| Áp dụng  
D07| Giải mã VIN bằng bảng ánh xạ mẫu cố định; bốn trạng thái: không hợp lệ, không được hỗ trợ, không có ánh xạ, có ánh xạ| Review B3| Áp dụng  
D08| Trợ lý AI không suy đoán xe tương thích; khi không có ánh xạ chỉ báo không xác định được| Review B3| Áp dụng  
D09| Bảng tồn kho chỉ chứa chi nhánh trong phạm vi được cấp; bỏ hàng TP.HCM / Đà Nẵng dạng khóa của bản trước| Review R5| Áp dụng  
D10| Bỏ thẻ công cụ “tồn kho TP.HCM — không có quyền” khỏi Trợ lý AI vì để lộ chi nhánh ngoài phạm vi| Review R5, mục 3| Áp dụng  
D11| Ghi “VAT: chưa xác định” cạnh giá; không tính thuế hay tổng| Review R4| Áp dụng  
D12| “Thêm vào báo giá” chỉ là điểm nối tới luồng C; không mô phỏng lưu báo giá| Review mục 5| Áp dụng  
D13| Kết quả tìm kiếm lọc đúng theo mã nhập (bản trước luôn trả cả 8 dòng)| Kiểm tra nội bộ| Áp dụng  
D14| Dưới 1280 px: sidebar tự thu gọn, Trợ lý AI nổi đè nội dung; drawer 420 px; trang chi tiết một cột dưới 1180 px| Review mục 4.12| Đề xuất  
D15| Thêm nút “Thử nhanh” với mã mẫu để người duyệt kiểm tra nhanh các trạng thái tìm kiếm| Hỗ trợ review| Chỉ trong prototype  
D16| Batch B được thiết kế dù Q5 chưa trả lời: chỉ dùng 5 công cụ chỉ đọc đã có trong Workflow A| Yêu cầu Batch B 17/11| Thay bằng D25  
D17| Không gian hội thoại mở từ nút “Mở rộng” của bảng Trợ lý; không thêm mục sidebar (giữ sơ đồ ứng dụng)| Sơ đồ ứng dụng| Áp dụng  
D18| Bảng Trợ lý và không gian hội thoại dùng chung một phiên| Thiết kế| Áp dụng  
D19| Tách hai loại từ chối: thiếu quyền → công cụ bị máy chủ từ chối; ngoài khả năng Trợ lý → không gọi công cụ nào| Yêu cầu “tôn trọng ranh giới quyền ở máy chủ”| Áp dụng  
D20| Mẫu xác nhận chỉ dùng cho “thêm vào lựa chọn tạm”| Yêu cầu “không thêm năng lực AI”| Thay bằng D28  
D21| Khi AI quá thời gian, vẫn hiện kết quả công cụ (có nhãn nguồn) nhưng không hiện diễn giải của AI| Thiết kế| Áp dụng  
D22| Thử lại ERP giữ lịch sử lần thử thay vì ghi đè trạng thái cũ| Phát hiện khi chạy thử| Áp dụng  
D23| “Dừng” chỉ ngừng nhận kết quả; công cụ đang chạy ghi “Chưa rõ kết quả”, không hiển thị “đã hủy” khi chưa có xác nhận máy chủ| Phản hồi AI2| Áp dụng  
D24| Thời điểm tra cứu trên thẻ dữ liệu ghi là “do hệ thống ghi”, không dùng giờ tạo tin nhắn AI| Phản hồi mục 7| Áp dụng  
D25| Phân biệt công cụ Agent (tra_xe_theo_vin, tim_phu_tung, lap_bao_gia, chuyen_nhan_vien) với tra cứu nội bộ do backend gọi (erp.gia_ban, erp.ton_kho); mỗi dòng công cụ ghi “AI nhận: …”| Phản hồi Q5| Áp dụng  
D26| AI không nhận giá, số lượng tồn, tên kho; tồn chỉ ở dạng con_hang / het_hang / can_kiem_tra. Giá/tồn do hệ thống hiển thị trên thẻ có phân quyền| Phản hồi Q5 · 1.3| Áp dụng  
D27| Ba nhãn nguồn theo từng phần: AI nhận diện · Dữ liệu tra cứu · Đã xác minh. Dữ liệu ERP không tự thành “Đã xác minh”| Phản hồi mục 5, 7| Áp dụng  
D28| Thêm vào lựa chọn tạm là thao tác chọn trực tiếp, không hộp xác nhận; Trợ lý không tự thêm| Phản hồi AI3| Áp dụng  
D29| Chuyển nhân viên thiết kế đầy đủ; lập báo giá và nhận diện ảnh chỉ là điểm nối, thiết kế ở Batch C| Phản hồi Q5 + lựa chọn của bạn 17/11| Áp dụng  
D30| Khi tim_phu_tung lỗi, không gọi tra cứu ERP (chưa có mã hàng)| Phát hiện khi chạy thử| Áp dụng  
D31| Lịch sử hội thoại chỉ hiện phiên của chính người dùng; phiên cũ chỉ xem, giá/tồn là dữ liệu tại thời điểm tra cứu| Phản hồi AI1| Áp dụng · tạm  
D32| Mọi ngưỡng quá thời gian ghi “chưa chốt · giả lập”; không dùng số giây làm quy tắc| Phản hồi AI4| Áp dụng  
D33| Workflow A đổi sang 3 nhãn nguồn; dữ liệu ERP thành “Dữ liệu tra cứu”; trạng thái ERP “chưa xác minh” đổi thành “chưa được xác nhận” để không trùng nhãn “Đã xác minh”| Quyết định của bạn 17/11| Áp dụng  
D34| Trưởng bộ phận xem lịch sử của bộ phận, lọc theo nhân viên, chỉ xem; giá/tồn hiện theo quyền của người xem| Quyết định của bạn 17/11 · AI1| Áp dụng · tạm  
D35| Nội dung gửi khi chuyển nhân viên giữ nguyên: câu hỏi gần nhất, mã đã tra, lý do| Quyết định của bạn 17/11| Áp dụng  
D36| Câu trả lời khi hỏi dữ liệu ngoài phạm vi giữ nguyên văn theo phản hồi nghiệp vụ| Quyết định của bạn 17/11| Áp dụng  
D37| Batch C là luồng Báo giá; quy tắc chưa chốt thiết kế theo đề xuất, ghi mã quyết định trên giao diện| Lựa chọn của bạn 17/11| Áp dụng  
D38| Giá theo chính sách ERP qua backend; Quote Service tính; nhân viên không nhập giá tay; không có trường chiết khấu; AI không chiết khấu, yêu cầu giảm giá chuyển nhân viên| Phản hồi 17/11 · B2| Áp dụng · chiết khấu thủ công còn mở  
D39| Bản nháp sửa trực tiếp; phát hành khóa nội dung; thay đổi sau phát hành tạo phiên bản mới; phiên bản cũ “Bị thay thế”, không dùng tạo đơn| Phản hồi 17/11 · Q1| PROPOSED  
D40| Trạng thái báo giá: Nháp, Đã gửi, Khách xác nhận, Hết hạn, Hủy; “Bị thay thế” ở mức phiên bản; xử lý đơn (Đã tạo đơn nháp, Nhân viên duyệt, Từ chối) tách cột riêng, lấy từ ERP; không có bước duyệt báo giá| Phản hồi 17/11 · B1| Áp dụng · có căn cứ  
D41| Tính giá / gửi quá thời gian → “Chưa rõ”, kiểm tra trạng thái trước khi gửi lại| Theo mẫu B9| Áp dụng  
D42| lap_bao_gia: xác nhận trước khi gửi; AI chỉ nhận {BAO_GIA_1}; dòng chưa “Đã xác minh” không tự động đưa vào; kết quả là Nháp, Trợ lý không phát hành| Phản hồi Q5, VR| Áp dụng  
D43| Phát hành & gửi qua Zalo OA là một bước; chỉ ghi “Đã gửi” khi dịch vụ gửi xác nhận; không phản hồi → “Đã phát hành · chưa xác nhận gửi”| Phản hồi 17/11 · Q6| Áp dụng · có căn cứ  
D44| Hiệu lực 3 ngày từ lúc phát hành, cấu hình ở backend; hết hạn không nhận xác nhận, không tạo đơn| Phản hồi 17/11 · Q2| PROPOSED  
D45| Khách chọn từ ERP (MockErpConnector) bằng số điện thoại / mã; nhiều hoặc không thấy → chuyển nhân viên; không tạo khách mới trong giao diện| Phản hồi 17/11 · Q3| Áp dụng · giá khách mới còn mở  
D46| Dòng hết hàng / cần kiểm tra: không phát hành báo giá cam kết cung ứng, chuyển nhân viên kinh doanh| Phản hồi 17/11 · Q4| PROPOSED  
D47| Không tính VAT, không hiển thị tổng thanh toán; chỉ hiển thị tiền hàng theo đơn giá ERP| Phản hồi 17/11 · R4| BLOCKED  
D48| Trước tạo đơn luôn kiểm tra lại giá, tồn, công nợ; giá đổi → backend tạo phiên bản mới thay vì sửa phiên bản khách đã xác nhận| Phản hồi 17/11 · Q1, Q2| PROPOSED  
D49| Nhân viên (người lập báo giá) bấm tạo đơn nháp trên ERP sau khi kiểm tra lại đạt| Lựa chọn của bạn 17/11 · B4| PROPOSED  
D50| Duyệt / từ chối đơn trong ERP; giao diện chỉ hiện trạng thái đồng bộ, nút “Mở trên ERP”| Lựa chọn của bạn 17/11 · B5| PROPOSED  
D51| Khóa chống trùng = mã báo giá + phiên bản; quá thời gian → kiểm tra theo khóa, không tạo lại tự động| Phản hồi 17/11 (chống tạo đơn trùng)| PROPOSED  
D52| Công nợ chỉ hiện đạt / không đạt, không hiện số dư, không gửi Trợ lý; không đạt → chuyển nhân viên| Phản hồi Q5, Q2| PROPOSED  
D53| Trợ lý không có công cụ trong Đơn hàng nháp| Phản hồi Q5 (không thêm năng lực AI)| Áp dụng  
D54| Người lập vắng mặt: trưởng bộ phận hoặc nhân viên được phân công lại, có đủ quyền, tiếp tục xử lý và tạo đơn; mọi thao tác ghi audit| Quyết định của bạn 17/11| Áp dụng  
D55| Khách xác nhận nhưng báo giá hết hạn: không tạo đơn tự động; backend kiểm tra lại giá, tồn kho, công nợ, lập báo giá / phiên bản mới, khách xác nhận lại| Quyết định của bạn 17/11| Áp dụng  
D56| ERP duyệt / từ chối: thông báo nhân viên phụ trách, cập nhật trạng thái, từ chối hiện lý do ERP; cơ chế thông báo chưa quy định| Quyết định của bạn 17/11| PROPOSED  
D57| Timeout tạo đơn 30 giây cho demo; quá hạn → “Chờ đối soát”, tra cứu theo idempotency key trước khi retry, không tạo đơn trùng| Quyết định của bạn 17/11 · cần xác nhận theo API ERP đích| PROPOSED  
D58| Tổng quan theo vai trò: nhân viên — việc của mình; trưởng bộ phận — tổng hợp và việc của bộ phận; quản trị — tình trạng hệ thống, không dữ liệu kinh doanh; không KPI nâng cao, dự báo| Quyết định 17/11 · Q8| Chấp thuận có điều kiện · cần đối chiếu widget  
D59| Nhật ký: nhân viên — của mình; trưởng bộ phận — bộ phận; quản trị — nhật ký kỹ thuật, metadata AI Gateway đã lọc; kiểm tra bảo mật — theo phạm vi và thời hạn phê duyệt; quyền đặc biệt cho quản trị cần Giám đốc, ghi audit| Quyết định 17/11 · B6| Áp dụng · đề xuất  
D60| Tài khoản nội bộ, backend xác thực, RBAC, không tự đăng ký; tối đa 8 giờ, 30 phút không hoạt động; hết phiên không tự tiếp tục thao tác ghi; công cụ đang chạy kiểm tra quyền lại; tạo đơn đang xử lý phải đối soát| Quyết định 17/11 · Q7| Đề xuất cấu hình · SSO chờ xác minh  
D61| Hồ sơ: chỉ sửa tùy chọn giao diện, thông báo, mật khẩu; khóa vai trò, quyền, bộ phận, chi nhánh, phạm vi khách hàng, mã nhân viên, định danh, trạng thái| Quyết định 17/11 · PF| Áp dụng · đề xuất  
D62| Yêu cầu cấp quyền qua quản lý trực tiếp → IT; không tự cấp; chỉ cập nhật sau phê duyệt hợp lệ; chưa có hệ thống tiếp nhận → điểm nối, không hiện đã cấp| Quyết định 17/11 · R10| Áp dụng · điểm nối
