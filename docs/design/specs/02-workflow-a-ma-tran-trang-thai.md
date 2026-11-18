<!-- Trích xuất tự động từ bảng canvas `StateMatrix.dc.html`. Bản gốc: docs/design/prototypes/StateMatrix.dc.html -->

# Ma trận trạng thái màn hình · Workflow A

Mỗi ô mô tả cách vùng giao diện thể hiện một trạng thái. Hàng cuối chỉ artboard tương ứng trên trang Workflow A.

Ma trận trạng thái Workflow A · tất cả đã thiết kế, trạng thái kiểm tra xem Báo cáo review Vùng| Đang tải| Không có kết quả / trống| Lỗi| ERP: xác nhận mới| ERP: bản lưu| ERP: dữ liệu cũ| ERP: chưa xác minh| ERP: quá thời gian| ERP: không khả dụng| Không có quyền| Chưa xác định quyền / phạm vi  
---|---|---|---|---|---|---|---|---|---|---|---  
Ô tìm kiếm (SKU / OE / VIN)| Nút tìm khóa, spinner; VIN: “Đang tra bảng ánh xạ VIN mẫu…”| Gợi ý “Nhập mã để tra cứu”| VIN không hợp lệ: lỗi ngay dưới ô (17 ký tự, không I/O/Q)| —| —| —| —| —| —| —| —  
Kết quả VIN| —| VIN không được hỗ trợ · Không có dữ liệu ánh xạ (EmptyState, gợi ý tìm theo OE)| —| Dòng xe + nhãn “Ánh xạ VIN mô phỏng · dữ liệu mẫu”| như xác nhận mới| như xác nhận mới| như xác nhận mới| như xác nhận mới| như xác nhận mới| —| —  
Bảng kết quả| Skeleton các dòng| “Không tìm thấy …” + Tìm theo mã OE / Hỏi Trợ lý AI| Lỗi danh mục + Thử lại, mã tham chiếu| Giá, tồn + nhãn “ERP mô phỏng · xác nhận 09:42”; cho chọn dòng| Số mờ + nhãn “Bản lưu · chưa xác nhận lại 08:15”; Alert; không cho chọn| Giá/tồn ẩn “—”, nhãn “Dữ liệu cũ · đã ẩn”; Alert| Ẩn “—”, nhãn “Chưa xác minh”; Alert| Ẩn “—”, nhãn “Quá thời gian”; Alert + Thử lại| Ẩn “—”, nhãn “ERP không khả dụng”; Alert + Thử lại| Ô khóa “Hạn chế”; Alert trung tính; không chọn| Ô khóa “Chưa xác định quyền”; Alert trung tính  
Khối giá & tồn (drawer / trang)| ERPResult đang tải khi thử lại| —| —| Giá lớn + “VAT: chưa xác định”; bảng tồn chỉ chi nhánh trong phạm vi| ERPResult “bản lưu”, số mờ, cảnh báo, nút Thử lại| Ẩn số, giải thích ngưỡng chưa xác nhận| Ẩn số| Ẩn số + Thử lại| Ẩn số + Thử lại| Giá và/hoặc tồn khóa riêng; cả hai → ERPResult restricted| Tồn kho: “chưa xác định phạm vi chi nhánh”, không tải chi nhánh nào  
Mã tương đương (gợi ý AI)| “Đang kiểm tra…”| Không có mục khi sản phẩm không có mã tương đương| “Không có trong danh mục ERP mẫu”| Nút Kiểm tra ERP → giá + nhãn ERP mô phỏng| Nút Kiểm tra ERP khóa| khóa| khóa| khóa| khóa| Nút khóa “Cần quyền xem giá bán”| Nút khóa “Chưa xác định quyền xem giá bán”  
Trợ lý AI| Công cụ đang chạy| Công cụ thành công + “không tìm thấy”, không suy đoán xe| Công cụ lỗi| Kết quả công cụ + ERPResult (chỉ chi nhánh trong phạm vi)| Công cụ quá thời gian, chỉ nói có bản lưu, không dùng số liệu| Công cụ không khả dụng, không đưa số| như dữ liệu cũ| Công cụ quá thời gian| Công cụ không khả dụng| Công cụ “không có quyền”, không trả dữ liệu| Công cụ “không có quyền”, không trả dữ liệu  
Thêm vào báo giá| —| —| —| Chỉ là điểm nối luồng C: toast thông tin “chưa khả dụng, không có dữ liệu nào được lưu”| Không chọn được dòng| Không chọn được| Không chọn được| Không chọn được| Không chọn được| Nút khóa trung tính| Nút khóa “quyền chưa được xác định”  
Artboard| A7 · Đang tải| A4 · BP-9999X · A3 · VIN| A7 · Lỗi danh mục · A3 · VIN không hợp lệ| Prototype · A2| A5 · Bản lưu| A5 · Dữ liệu cũ| A5 · Chưa xác minh| A5 · Quá thời gian| A5 · Không khả dụng| A6 · Không có quyền · A6 · Báo giá| A6 · Chưa xác định  
  
Ma trận trạng thái Batch B · cập nhật 17/11 theo phản hồi nghiệp vụ Vùng| Đang chạy| Trống / ngoài kịch bản| Lỗi| Tra cứu được| ERP: bản lưu| Một phần quyền| Không có / chưa xác định quyền  
---|---|---|---|---|---|---|---  
Hội thoại (B1/B2)| Bước n/N, “Đang tổng hợp…”, Dừng → “Chưa rõ kết quả”| Lời chào 3 nhóm; câu ngoài kịch bản → không gọi công cụ + Chuyển nhân viên| AI quá thời gian: lỗi ở câu trả lời, dữ liệu tra cứu giữ nhãn; AI không khả dụng: EmptyState| —| —| —| Sửa giá/chiết khấu: không giao cho Trợ lý  
Công cụ (ToolCall)| Đang chạy · Chưa gửi| —| Thất bại · Quá thời gian · ERP không khả dụng · Chưa rõ kết quả; lịch sử lần thử| Hoàn tất + “AI nhận: …”| Quá thời gian + “chỉ có bản lưu · AI nhận can_kiem_tra”| —| Không có quyền: máy chủ từ chối, không trả dữ liệu  
Dữ liệu tra cứu danh mục| —| —| Lỗi danh mục: không đưa mã OE; ERP “Chưa gửi”| Nhãn Dữ liệu tra cứu; mã “Chưa xác minh”; Tải thêm| —| —| —  
Thẻ dữ liệu ERP mô phỏng| Thẻ “Đang đọc…”| —| Thẻ đỏ + mã tham chiếu + Thử lại| Dữ liệu tra cứu + thời điểm do hệ thống ghi; giá/tồn chỉ hiện cho người có quyền, không gửi AI| Thẻ Bản lưu, không trích số| Chỉ trường được phép; trường khác “Hạn chế”| Thẻ “Không có quyền xem dữ liệu này”; ngoài phạm vi → Chuyển nhân viên  
Chọn sản phẩm (B8)| —| —| Chưa tra cứu được giá → nút tắt + Thử lại| Thêm / Đã chọn · Bỏ chọn — không có hộp xác nhận| Nút tắt| —| Nút khóa “Cần quyền xem giá bán”  
Chuyển nhân viên (B9)| Đang gửi| —| Thất bại → Thử lại; Chưa rõ → Kiểm tra trạng thái, không gửi lại tự động| Đã tạo HT-0042 · trạng thái do hệ thống trả về| —| —| Quyền chưa xác định  
  
Ma trận trạng thái Batch C · Báo giá · cập nhật 17/11 (lần 2) Vùng| Đang chạy| Trống / chưa có| Lỗi| Thành công| Chưa rõ| Bị chặn theo quy tắc| Không có quyền  
---|---|---|---|---|---|---|---  
Danh sách (C1)| —| EmptyState| —| 6 trạng thái báo giá + cột Xử lý đơn (ERP)| —| —| EmptyState, không tải  
Tính giá nháp| “Quote Service đang tính…”| “Chưa tính”| QS-502 + Thử lại| Giá cập nhật trên nháp| Kiểm tra trạng thái| ERP không khả dụng| Không quyền xem giá: nút khóa  
Phát hành & gửi| “Đang gửi qua Zalo OA…”| —| ZOA-503, chưa phát hành| Đã gửi + mã ZOA, hết hạn +3 ngày| Đã phát hành · chưa xác nhận gửi → Kiểm tra| Chưa tính giá; hàng hết/cần kiểm tra (Q4); xung đột nháp| —  
Phiên bản| —| —| —| Hiện hành / Bị thay thế / Khách xác nhận| —| Đã phát hành: không sửa (Q1)| —  
Khách xác nhận (C7)| Kiểm tra lại giá, tồn, công nợ| —| Phiên bản bị thay thế / hết hạn: từ chối| Khách xác nhận → sẵn sàng tạo đơn (Batch D)| —| Giá đổi → phiên bản mới| —  
Khách hàng (C5)| —| Không thấy: không tạo khách, chuyển nhân viên| —| Một kết quả: chọn| Nhiều kết quả: cảnh báo + chuyển nhân viên| —| —  
Trợ lý (C6)| ToolCall đang chạy| —| Backend từ chối (VR) · lỗi| Nháp đã tính giá, mã thay {BAO_GIA_1}| Kiểm tra trạng thái| Giảm giá: từ chối + chuyển nhân viên| Máy chủ từ chối  
Tổng tiền| —| —| —| Tiền hàng theo đơn giá ERP| —| VAT, tổng thanh toán: BLOCKED (R4)| “Hạn chế”  
  
Ma trận trạng thái Batch D · Đơn hàng nháp · 17/11 Vùng| Đang chạy| Trống| Lỗi| Thành công| Chưa rõ| Bị chặn theo quy tắc| Không có quyền  
---|---|---|---|---|---|---|---  
Danh sách (D1)| —| EmptyState theo tab| —| Chờ tạo đơn + đơn theo trạng thái ERP| —| —| EmptyState, không tải  
Kiểm tra lại (D2)| “Đang kiểm tra…”| “Chưa kiểm tra”| ERP không khả dụng| 4 mục đạt| —| Hết hạn → backend lập phiên bản mới · giá đổi · thiếu hàng · công nợ không đạt| Chỉ xem; trưởng bộ phận / người được phân công: xử lý thay + audit  
Tạo đơn (D3)| “Đang tạo trên ERP…”| —| ERP-502 + Thử lại cùng khóa| DN mới → D4| Chờ đối soát (quá 30 giây) → tra cứu theo khóa; không thấy → tạo lại cùng khóa| Đã có đơn cho khóa này| Nút khóa / máy chủ từ chối  
Đơn (D4)| “Đang đồng bộ…”| —| Không đồng bộ: trạng thái lần cuối| Chờ duyệt / Nhân viên duyệt / Từ chối (ERP) + thông báo cho nhân viên phụ trách| —| Không duyệt, không sửa ở đây| Giá “Hạn chế”  
Trợ lý (D5)| —| —| —| —| —| Không tạo, không duyệt, không đọc trạng thái| —  
  
Ma trận trạng thái Batch E · Màn hình hỗ trợ · 17/11 Vùng| Đang tải| Trống| Lỗi| Bình thường| Phiên| Một phần quyền| Không có quyền  
---|---|---|---|---|---|---|---  
Tổng quan (E1)| —| Ô có số 0| E6 + Thử lại| 6 ô việc cần làm| Sắp hết / hết hạn| Quản trị: chỉ kết nối| —  
Hoạt động (E2)| —| EmptyState theo bộ lọc| —| Bảng + chi tiết| —| Quản trị: nhật ký kỹ thuật · kiểm tra bảo mật: theo phạm vi| E4 + Yêu cầu cấp quyền (điểm nối)  
Hồ sơ (E3)| —| —| —| Thông tin + quyền| Đăng xuất| Quyền “Không / Chưa xác định”| —  
Phiên (E5) · Đăng nhập (E7)| “Đang đăng nhập…” / “Đang xác thực…”| —| AUTH-401 · sai thông tin · tạm khóa AUTH-423| Về đúng trang| 30 phút không hoạt động · 8 giờ tối đa · thao tác đang chờ → đối soát| —| —  
  
## Quy tắc chung về nguồn dữ liệu

  * Giờ đồng bộ một mình không chứng minh dữ liệu là hiện tại. Chỉ trạng thái “xác nhận mới” được dùng để chọn sản phẩm.
  * Bản lưu được hiển thị mờ, có nhãn và cảnh báo; dữ liệu cũ, chưa xác minh, quá thời gian và không khả dụng đều ẩn số.
  * Mọi dữ liệu trong bản mẫu là dữ liệu mô phỏng và được ghi là “ERP mô phỏng”, không bao giờ trình bày như ERP thật.
  * Dữ liệu ngoài quyền hoặc ngoài phạm vi chi nhánh không được truyền vào bảng, drawer, trang, Trợ lý AI, thẻ kết quả công cụ hay thông báo.
