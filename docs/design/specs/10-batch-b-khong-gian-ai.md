<!-- Trích xuất tự động từ bảng canvas `BatchBSpec.dc.html`. Bản gốc: docs/design/prototypes/BatchBSpec.dc.html -->

# Batch B · Đặc tả màn hình Không gian làm việc AI

Cập nhật 17/11/2026 theo phản hồi nghiệp vụ. “Đã rà soát” nghĩa là đã chạy thử thật trên canvas trong Chrome; không mục nào được phê duyệt.

Đã thiết kếCó trên canvas Đã rà soátĐã chạy thử thật trong trình duyệt Đã phê duyệtĐược người duyệt chấp thuận chính thức Bị chặnChờ quyết định nghiệp vụ

## Phạm vi và giả định · cập nhật theo phản hồi nghiệp vụ 17/11

  * Câu trả lời của Trợ lý là **kịch bản cố định** , không dùng mô hình AI thật.
  * **Công cụ Agent** (theo tài liệu giải pháp 1.0, đề xuất): tra_xe_theo_vin, tim_phu_tung, lap_bao_gia (điểm nối), chuyen_nhan_vien. **Tra cứu nội bộ do backend gọi** : erp.gia_ban, erp.ton_kho.
  * **AI nhận:** thông số, mã hàng/OE, trạng thái tồn (con_hang · het_hang · can_kiem_tra), tham chiếu báo giá. **AI không nhận:** giá, số lượng tồn, tên kho, công nợ, thông tin khách hàng. Giá/tồn do hệ thống hiển thị trực tiếp cho người có quyền.
  * **Ba nhãn nguồn** gắn theo từng phần: AI nhận diện · Dữ liệu tra cứu · Đã xác minh. Chưa phần nào mang “Đã xác minh” vì quy tắc đối chiếu chưa được duyệt.
  * Toàn bộ dữ liệu ERP là **mô phỏng** và được ghi rõ. Chưa kiểm tra API ERP đích, phân quyền máy chủ hay dịch vụ AI thật.
  * Lập báo giá, xác nhận báo giá, tạo đơn nháp và nhận diện ảnh vẫn thuộc MVP; trong Batch B chỉ là điểm nối, thiết kế ở Batch C.

## B1 · AI-01 · Không gian hội thoại Trợ lý AI

Artboard: B · Prototype, B1 · sáu artboard (gồm lịch sử bộ phận, không có quyền, quản trị) Đã thiết kế · Đã rà soát

Điểm vào| Nút “Mở rộng” trên bảng Trợ lý theo ngữ cảnh (B2). Không có mục sidebar riêng — giữ quyết định sơ đồ.  
---|---  
Thao tác người dùng| 4 câu hỏi mẫu; gõ câu hỏi + Enter; chip gợi ý; “Phiên mới”; mở phiên cũ (chỉ xem); “Tra cứu lại với dữ liệu hiện tại”; bật/tắt “Nguồn & công cụ” dưới 1360 px.  
Dữ liệu hiển thị| Lời chào nêu 3 nhóm: làm được trong bản mô phỏng · thuộc MVP chưa thiết kế ở đây · không giao cho Trợ lý. Cột trái: trạng thái dịch vụ AI, phiên hiện tại, lịch sử của chính người dùng. Cột phải: công cụ đã gọi (Agent / Nội bộ), ranh giới dữ liệu với AI, nhãn nguồn, quyền, lựa chọn tạm, yêu cầu hỗ trợ.  
Chuyển màn hình| “Xem chi tiết” trên thẻ dữ liệu → B2 (cùng phiên, giữ vị trí cuộn khi quay lại). B2 “Mở rộng” → B1 với nút “Quay lại BP-1042”.  
Phụ thuộc quyền| Mở Trợ lý: ai.use (chưa xác định). Lịch sử (đề xuất theo AI1): nhân viên — phiên của mình; trưởng bộ phận — thêm “Lịch sử bộ phận”, lọc theo nhân viên, chỉ xem; quản trị — không xem nội dung kinh doanh (mở quyền tạm thời chưa thiết kế). Mở phiên người khác khi không có quyền → máy chủ từ chối, không tải nội dung.  
Đang tải & lỗi| Đang chạy (B4) · dịch vụ chậm / quá thời gian / không khả dụng (B7). Câu ngoài kịch bản → không gọi công cụ, đề xuất chuyển nhân viên.  
Khôi phục| “Phiên mới”, “Kiểm tra lại” khi AI không khả dụng, “Chuyển nhân viên”.  
Thành phần dùng lại| Sidebar, AppHeader, ChatMessage (label=false), AIComposer (disclaimer), Alert, SourceTag, Tag, EmptyState, ToastViewport  
  
## B2 · AI-01 · Trợ lý theo ngữ cảnh

Artboard: B2, B2 · 1024 Đã thiết kế · Đã rà soát

Điểm vào| Biểu tượng Trợ lý trên header khi xem chi tiết phụ tùng; “Xem chi tiết” từ B1.  
---|---  
Thao tác người dùng| Đóng/mở bảng (nút X, biểu tượng header, Esc); chip gợi ý theo SKU; gõ câu hỏi; “Mở rộng”.  
Dữ liệu hiển thị| Ngữ cảnh “Đang xem: Má phanh trước · BP-1042”; cùng nội dung với B1; bảng so sánh hiển thị dạng xếp dọc cho vừa bảng hẹp. Nội dung chính là khối chi tiết của Workflow A.  
Chuyển màn hình| “Mở rộng” → B1. Dưới 1180 px bảng là lớp phủ, mặc định đóng; breadcrumb rút gọn.  
Phụ thuộc quyền| Như B1. Khối chi tiết giữ quy tắc quyền của Workflow A.  
Đang tải & lỗi| Trạng thái dịch vụ ở đầu bảng.  
Khôi phục| Như B1.  
Thành phần dùng lại| AIPanel, IconButton, PageHeader, PartDetail (Workflow A), ChatMessage, AIComposer  
  
## B3 · AI-03 · Kết quả công cụ có cấu trúc

Artboard: B3 · VIN, B3 · Tải thêm, B3 · So sánh Đã thiết kế · Đã rà soát

Điểm vào| Sau khi công cụ Agent tra_xe_theo_vin / tim_phu_tung trả kết quả.  
---|---  
Thao tác người dùng| “Tải thêm 5 mã”; “So sánh thông số”; “Tra giá và tồn BP-2210A”.  
Dữ liệu hiển thị| Bảng danh mục gắn “Dữ liệu tra cứu · danh mục mẫu”, cột “Trạng thái mã: Chưa xác minh” (quy tắc đối chiếu chưa duyệt). Nhận định của AI tách riêng, gắn “AI nhận diện”, ghi rõ chỉ dựa trên phần đã tải.  
Chuyển màn hình| Nút trong khối AI chạy kịch bản tiếp theo trong cùng phiên.  
Phụ thuộc quyền| Công cụ Agent qua Tool Manager kiểm tra quyền và tham số (đề xuất).  
Đang tải & lỗi| Lỗi danh mục: xem B7.  
Khôi phục| Tải thêm không làm AI tự tổng hợp lại phần mới.  
Thành phần dùng lại| Bảng theo token, Badge, SourceTag (lookup, ai), DescriptionList  
  
## B4 · AI-03 · Tiến độ chạy công cụ

Artboard: B4 · đang chạy, B4 · đã dừng, B7 · chậm Đã thiết kế · Đã rà soát

Điểm vào| Ngay sau khi gửi câu hỏi có gọi công cụ.  
---|---  
Thao tác người dùng| “Dừng”; sau khi dừng: “Chạy lại truy vấn đọc”.  
Dữ liệu hiển thị| Bước n/N, thanh tiến độ; mỗi công cụ ghi loại (Agent / Nội bộ), trạng thái và “AI nhận: …”. Bấm vào dòng để xem ranh giới thực thi.  
Chuyển màn hình| Hết bước → “Đang tổng hợp câu trả lời…” → kết quả hoặc lỗi.  
Phụ thuộc quyền| Không.  
Đang tải & lỗi| Dừng: công cụ đang chạy → “Chưa rõ kết quả · máy chủ chưa xác nhận hủy”; công cụ sau → “Chưa gửi”. Không hiển thị “đã hủy” (AI2).  
Khôi phục| Chỉ cho chạy lại truy vấn đọc.  
Thành phần dùng lại| ToolCall (pending, unknown mới), Spinner, Button  
  
## B5 · AI-03 · Dữ liệu tra cứu từ ERP mô phỏng

Artboard: B3 · VIN, B5 · ba artboard Đã thiết kế · Đã rà soát

Điểm vào| Khi backend trả erp.gia_ban / erp.ton_kho.  
---|---  
Thao tác người dùng| “Xem chi tiết”; “Thêm vào lựa chọn”; “Thử lại”.  
Dữ liệu hiển thị| Thẻ ERPResult trạng thái mới “Dữ liệu tra cứu · ERP mô phỏng” (không còn tự gọi là “Đã xác minh”), thời điểm “Tra cứu lúc … (hệ thống ghi)”, giá kèm “VAT: chưa xác định”, tồn trong phạm vi. Ghi chú: hiển thị bởi hệ thống, không gửi cho AI. AI chỉ nói trạng thái “còn hàng / hết hàng / cần kiểm tra”.  
Chuyển màn hình| “Xem chi tiết” → B2.  
Phụ thuộc quyền| erp.price.view, erp.stock.view, phạm vi — trường bị từ chối ghi “Hạn chế” / “Chưa xác định quyền”.  
Đang tải & lỗi| Bản lưu: thẻ “Bản lưu”, không trích số. Dữ liệu cũ / chưa xác minh: thẻ lỗi, không số.  
Khôi phục| “Thử lại” giữ lịch sử lần thử.  
Thành phần dùng lại| ERPResult (state lookup mới), DescriptionList, Button, Toast  
  
## B6 · AI-03 · Công cụ bị từ chối · ngoài phạm vi

Artboard: B6 · bốn artboard Đã thiết kế · Đã rà soát

Điểm vào| Máy chủ từ chối theo quyền; hỏi dữ liệu ngoài phạm vi; yêu cầu thao tác không giao cho Trợ lý.  
---|---  
Thao tác người dùng| “Chuyển nhân viên”.  
Dữ liệu hiển thị| Thiếu quyền: ToolCall “Không có quyền” + “Máy chủ từ chối (mô phỏng)… không có dữ liệu nào được trả về”. Ngoài phạm vi (AI5): câu trả lời theo phương án đề xuất trong phản hồi nghiệp vụ, kèm ghi chú mô hình chi nhánh/kho chưa chốt. Sửa giá/chiết khấu/duyệt đơn: từ chối, không gọi công cụ.  
Chuyển màn hình| “Chuyển nhân viên” → B9.  
Phụ thuộc quyền| Backend từ chối trước khi dữ liệu tới LLM hoặc UI.  
Đang tải & lỗi| —  
Khôi phục| Chuyển nhân viên.  
Thành phần dùng lại| ToolCall, ERPResult restricted, ChatMessage  
  
## B7 · AI-03 · Quá thời gian và lỗi

Artboard: B7 · sáu artboard Đã thiết kế · Đã rà soát

Điểm vào| Lỗi danh mục; ERP quá thời gian / không khả dụng; dịch vụ AI chậm, không trả lời, không khả dụng.  
---|---  
Thao tác người dùng| “Thử lại” (khi an toàn: chỉ truy vấn đọc); “Tạo lại câu trả lời”; “Kiểm tra lại”; “Chuyển nhân viên”.  
Dữ liệu hiển thị| Lỗi danh mục: không đưa ra mã OE; công cụ ERP sau đó “Chưa gửi” vì chưa có mã. ERP lỗi: thẻ đỏ, mã tham chiếu. AI quá thời gian: dữ liệu tra cứu vẫn hiện kèm nhãn nguồn. Mọi ngưỡng ghi “chưa chốt · giả lập” (AI4).  
Chuyển màn hình| “Mở Tra cứu phụ tùng” → Workflow A (toast trong bản mô phỏng).  
Phụ thuộc quyền| —  
Đang tải & lỗi| Không ghi số giây làm quy tắc.  
Khôi phục| Thử lại giữ lịch sử; ERP xuống vẫn lỗi, toast ghi số lần.  
Thành phần dùng lại| ERPResult error, ToolCall, ChatMessage error, EmptyState, AIPanel status, Toast  
  
## B8 · AI-02 · Chọn sản phẩm · điểm nối báo giá

Artboard: B8 · năm artboard Đã thiết kế · Đã rà soát

Điểm vào| Câu hỏi thêm vào lựa chọn; nút trên thẻ dữ liệu; yêu cầu lập báo giá.  
---|---  
Thao tác người dùng| “Thêm vào lựa chọn” / “Đã chọn · Bỏ chọn” từng dòng; bỏ chọn ở cột phải.  
Dữ liệu hiển thị| Lựa chọn tạm là thao tác chọn trực tiếp, không có hộp xác nhận (AI3). Trợ lý không tự thêm. Lập báo giá: ToolCall lap_bao_gia “Chưa gửi” + tóm tắt quy tắc theo tài liệu giải pháp 1.0, đánh dấu “sẽ thiết kế ở Batch C”.  
Chuyển màn hình| Lựa chọn hiện ở cột phải; báo giá → Batch C.  
Phụ thuộc quyền| Cần quyền xem giá và giá tra cứu mới. Không quyền → nút khóa; chưa tra cứu được → nút tắt + Thử lại.  
Đang tải & lỗi| —  
Khôi phục| Thử lại tra giá.  
Thành phần dùng lại| Button (restricted), Tag, ToolCall pending, SourceTag lookup, Toast  
  
## B9 · AI-02 · Chuyển nhân viên

Artboard: B9 · bốn artboard Đã thiết kế · Đã rà soát

Điểm vào| Nút “Chuyển nhân viên” ở câu ngoài phạm vi, lỗi, sửa giá, câu ngoài kịch bản; hoặc gõ yêu cầu.  
---|---  
Thao tác người dùng| Chọn lý do (4 lý do theo tài liệu); “Chuyển” / “Không chuyển”; “Thử lại”; “Kiểm tra trạng thái”.  
Dữ liệu hiển thị| Thẻ ghi rõ yêu cầu gồm gì và không gồm giá, tồn, thông tin khách; người nhận do hệ thống điều phối (quy tắc chưa xác định). Trạng thái: chờ bạn bấm → đang gửi → đã tạo HT-0042 (trạng thái do hệ thống trả về) / thất bại / chưa rõ.  
Chuyển màn hình| Yêu cầu đã tạo hiện ở cột phải.  
Phụ thuộc quyền| chuyen_nhan_vien là công cụ Agent theo tài liệu; quyền chi tiết chưa xác định.  
Đang tải & lỗi| Quá thời gian → “Chưa rõ”, không gửi lại tự động; phải kiểm tra trạng thái trước.  
Khôi phục| Thất bại → Thử lại; chưa rõ → Kiểm tra trạng thái (tìm thấy yêu cầu cũ, không tạo thêm).  
Thành phần dùng lại| ToolCall, Tag (chọn lý do), Button, Toast  
  
Thay đổi design system (17/11 · không đổi hiển thị Workflow A, trừ câu lưu ý dưới ô nhập) Thành phần| Thay đổi  
---|---  
ChatMessage| Thêm label; mặc định giữ “Gợi ý, cần kiểm tra”; Batch B dùng label=false và gắn nhãn theo từng phần.  
SourceTag| Thêm nguồn lookup (Dữ liệu tra cứu) và verified (Đã xác minh).  
ERPResult| Thêm trạng thái lookup và label.  
AIComposer| Câu lưu ý mới về thời điểm tra cứu do hệ thống ghi; thêm disclaimer. Workflow A cũng hiện câu mới.  
ToolCall| Thêm trạng thái pending (Chưa gửi) và unknown (Chưa rõ kết quả).  
  
Rà soát nhất quán #| Phát hiện| Mức| Xử lý  
---|---|---|---  
V1| Khung, sidebar, header, băng mô phỏng, toast giống Workflow A; Be Vietnam Pro; token màu.| —| Đạt  
V2| Workflow A đã đổi sang 3 nhãn (theo quyết định của bạn). Tin nhắn AI chỉ có công cụ trong A vẫn gắn nhãn chung “AI nhận diện” ở tiêu đề.| Thấp| Đã sửa phần chính; nhãn tiêu đề còn lại chấp nhận được  
V3| Esc đóng bảng Trợ lý đã cài, nhưng trong chế độ Play phím Esc cũng thoát Play nên chưa xác minh được.| Thấp| Cần kiểm tra ngoài khung xem  
V4| Băng cảnh báo phiên cũ bị ép hẹp chữ.| —| Đã sửa: nút chuyển xuống dưới  
V5| Khi lỗi danh mục, các bước ERP hiện “Chưa gửi” — đã sửa sau lần chạy đầu (trước đó vẫn tra giá cho BP-1042).| —| Đã sửa, đã chạy lại  
  
Trạng thái còn thiếu · theo hướng xử lý trong phản hồi #| Trạng thái| Tình trạng  
---|---|---  
M1| Lỗi công cụ danh mục| Đã thiết kế (B7)  
M2| Phiên đăng nhập hết hạn khi công cụ đang chạy| Đã có hướng · Q7 (decisions/05, PROPOSED · UX-12)  
M3| Lịch sử phiên| Đã thiết kế: của mình, của bộ phận (trưởng bộ phận), không có quyền, quản trị bị khóa  
M4| Đề xuất hết hạn| Không đặt TTL; chờ chốt thời hạn báo giá (Batch C)  
M5| Kết quả dài / nhiều trang| Đã thiết kế “Tải thêm” (B3)  
M6| Chi nhánh ngoài phạm vi| Đã thiết kế theo AI5 đề xuất (B6); mô hình chi nhánh/kho chưa chốt  
M7| Nhận diện ảnh phụ tùng| Điểm nối · Batch C
