<!-- Trích xuất tự động từ bảng canvas `BatchCSpec.dc.html`. Bản gốc: docs/design/prototypes/BatchCSpec.dc.html -->

# Batch C · Đặc tả màn hình Báo giá  
  
Cập nhật 17/11/2026 theo quyết định B1, B2, Q1–Q6, VR, R4. “Đã rà soát” nghĩa là đã chạy thử thật trên canvas trong Chrome; không mục nào được phê duyệt.

Đã thiết kếCó trên canvas Đã rà soátĐã chạy thử thật trong trình duyệt Đã phê duyệtĐược người duyệt chấp thuận chính thức Bị chặnChờ quyết định nghiệp vụ

## Phạm vi và căn cứ · cập nhật theo phản hồi nghiệp vụ 17/11 (lần 2)

  * **Có căn cứ:** B1 (luồng trạng thái của tài liệu tham chiếu, không có bước duyệt báo giá, duyệt đơn tại ERP), Q3 (khách từ ERP, AI không tạo khách), Q6 (Zalo OA là kênh gửi), VR (ba nhãn; checklist xác minh còn mở), B2 (AI không chiết khấu; chiết khấu thủ công còn mở).
  * **PROPOSED:** Q1 (khóa phiên bản đã phát hành), Q2 (hiệu lực 3 ngày mô phỏng, cấu hình backend), Q4 (hết hàng / cần kiểm tra không phát hành báo giá cam kết).
  * **BLOCKED:** R4 — không tính VAT, không hiển thị tổng thanh toán.
  * Mock: MockErpConnector, Quote Service, Zalo OA. Không phải bằng chứng Quote Service, phân quyền, API ERP đích, gửi Zalo OA hay chống tạo trùng đã hoạt động.
  * Chưa thiết kế: tạo đơn nháp và chi tiết xử lý đơn (Batch D), mẫu nội dung gửi khách, luồng phía khách trên Zalo OA.

## C1 · QT-01 · Danh sách báo giá

Artboard: C · Prototype, C1 · bốn artboard Đã thiết kế · Đã rà soát

Điểm vào| Mục “Báo giá” trên sidebar; nút “Danh sách”; breadcrumb.  
---|---  
Thao tác người dùng| Lọc theo trạng thái báo giá: Tất cả · Nháp · Đã gửi · Khách xác nhận · Hết hạn · Hủy; trưởng bộ phận đổi “Của tôi / Cả bộ phận”; bấm dòng; “Soạn báo giá”.  
Dữ liệu hiển thị| Mã, khách (mã ERP), trạng thái báo giá, phiên bản hiện hành (và “đang soạn vN”), hiệu lực đến, tiền hàng, **Xử lý đơn (ERP)** tách riêng: Đã tạo đơn nháp · Nhân viên duyệt · Từ chối. “Bị thay thế” là trạng thái phiên bản, xem ở C4.  
Chuyển màn hình| Dòng → C2. “Soạn báo giá” → C5.  
Phụ thuộc quyền| Nhân viên: báo giá của mình. Trưởng bộ phận: thêm báo giá của bộ phận (D34). Không có quyền / quản trị → EmptyState, không tải dữ liệu, ẩn Trợ lý.  
Đang tải & lỗi| Danh sách rỗng → EmptyState + Soạn báo giá.  
Khôi phục| “Về Tra cứu phụ tùng”.  
Thành phần dùng lại| PageHeader, Tabs, DataTable, Badge, Tag, EmptyState  
  
## C2 · QT-02 · Chi tiết báo giá · nháp và đã phát hành

Artboard: C2 · mười artboard Đã thiết kế · Đã rà soát

Điểm vào| Bấm dòng ở C1; chip mã báo giá từ Trợ lý; sau khi lưu nháp (C5).  
---|---  
Thao tác người dùng| Nháp: sửa số lượng, bỏ dòng, “Tính giá (Quote Service)”, “Phát hành vN & gửi qua Zalo OA”, “Bỏ thay đổi”. Đã gửi/Hết hạn: “Tạo phiên bản mới”, “Hủy báo giá” (có hộp xác nhận). Q4: “Chuyển nhân viên kinh doanh”.  
Dữ liệu hiển thị| Khách (ERP · Mock, mã do backend xác định). Phát hành → hết hạn (3 ngày mô phỏng, cấu hình backend, Q2). Chính sách giá theo ERP, không có trường chiết khấu (B2). Dòng: trạng thái mã (VR), tồn Còn hàng / Hết hàng / Cần kiểm tra, số lượng, đơn giá Quote Service. Tổng: tiền hàng; VAT và tổng thanh toán BLOCKED (R4). Khối “Mô phỏng phía khách (Zalo OA)” để xem phản hồi khi khách xác nhận.  
Chuyển màn hình| Tab Phiên bản → C4. Phát hành → C3. Khách xác nhận → C7.  
Phụ thuộc quyền| Nháp chỉ người lập (có quyền) sửa. Không quyền xem giá → “Hạn chế”. Báo giá người khác: máy chủ từ chối, trừ trưởng bộ phận (chỉ xem). Hủy: quyền chưa xác định.  
Đang tải & lỗi| Hết hàng / cần kiểm tra: không phát hành báo giá cam kết cung ứng (Q4). ERP không khả dụng: không tính giá, không phát hành.  
Khôi phục| Bỏ thay đổi; tạo phiên bản mới khi hết hạn; chuyển nhân viên.  
Thành phần dùng lại| Card, DescriptionList, DataTable, Input, IconButton, Badge, SourceTag, Alert, ActionConfirmation, Button  
  
## C3 · QT-02 · QT-09 · Tính giá · phát hành & gửi · lỗi

Artboard: C3 · chín artboard Đã thiết kế · Đã rà soát

Điểm vào| “Tính giá” và “Phát hành & gửi” trên C2.  
---|---  
Thao tác người dùng| Chờ; “Kiểm tra trạng thái” (tính giá chưa rõ); “Kiểm tra trạng thái gửi”; “Thử lại”; “Tải bản nháp mới nhất” (xung đột).  
Dữ liệu hiển thị| Tính giá cập nhật bản nháp, không tạo phiên bản mới (Q1: nháp sửa trực tiếp). Phát hành khóa nội dung, đặt thời hạn, thay thế phiên bản trước. **Chỉ ghi “Đã gửi” khi dịch vụ gửi xác nhận** (mã ZOA-…); không phản hồi → “Đã phát hành · chưa xác nhận gửi”; lỗi → chưa phát hành. Giá khác phiên bản trước → Alert. Xung đột: bản nháp bị sửa ở phiên khác, không phát hành bản cũ.  
Chuyển màn hình| Thành công → ở lại C2 với trạng thái mới, toast.  
Phụ thuộc quyền| Như C2.  
Đang tải & lỗi| Ngưỡng quá thời gian chưa chốt (giả lập).  
Khôi phục| Kiểm tra trạng thái trước khi gửi lại; không gửi lại tự động.  
Thành phần dùng lại| Button loading, Alert, Toast, Badge  
  
## C4 · QT-04 · Lịch sử phiên bản · hoạt động

Artboard: C4 · hai artboard Đã thiết kế · Đã rà soát

Điểm vào| Tab “Phiên bản”, “Hoạt động” trên C2.  
---|---  
Thao tác người dùng| Chọn phiên bản để so sánh; Trợ lý tóm tắt khác biệt.  
Dữ liệu hiển thị| Mỗi phiên bản: Nháp / Đã phát hành · hiện hành / Bị thay thế / Khách xác nhận; thời điểm tạo, người tạo; phát hành → hết hạn và mã gửi Zalo OA; lý do; tiền hàng. So sánh số lượng và đơn giá. Hoạt động: dòng thời gian gồm Quote Service, backend, Zalo OA, khách, ERP.  
Chuyển màn hình| Quay lại Chi tiết.  
Phụ thuộc quyền| Không quyền xem giá → cột giá “Hạn chế”.  
Đang tải & lỗi| —  
Khôi phục| —  
Thành phần dùng lại| DataTable, Tabs pill, bảng theo token  
  
## C5 · QT-02 · Soạn báo giá · khách hàng từ ERP

Artboard: C5 · bốn artboard Đã thiết kế · Đã rà soát

Điểm vào| “Soạn báo giá” ở C1; dòng điền sẵn từ lựa chọn tạm.  
---|---  
Thao tác người dùng| Nhập số điện thoại hoặc mã khách → “Tra cứu ERP”; chọn khách; sửa/xóa/thêm dòng; “Lưu nháp”; “Chuyển nhân viên xác minh”.  
Dữ liệu hiển thị| Một kết quả → chọn. Nhiều kết quả → cảnh báo, chỉ chọn khi chắc chắn, hoặc chuyển nhân viên. Không thấy → không tạo khách mới ở đây, chuyển nhân viên (Q3). Đơn giá: Quote Service tính sau khi lưu.  
Chuyển màn hình| Lưu → C2 (Nháp, chưa tính giá). Hủy → C1.  
Phụ thuộc quyền| Lập báo giá: quyền đề xuất.  
Đang tải & lỗi| Thiếu khách → Alert + lỗi tại trường.  
Khôi phục| Đổi khách; chuyển nhân viên.  
Thành phần dùng lại| Input, Button, Alert, DataTable, IconButton, Toast  
  
## C6 · AI-02 · Trợ lý trong báo giá

Artboard: C6 · bảy artboard Đã thiết kế · Đã rà soát

Điểm vào| Bảng Trợ lý trên C1/C2/C5; câu gợi ý.  
---|---  
Thao tác người dùng| “Gửi yêu cầu” / “Không gửi” (lap_bao_gia); “Kiểm tra trạng thái”; chip mã báo giá; “Chuyển nhân viên có thẩm quyền”.  
Dữ liệu hiển thị| lap_bao_gia: thẻ xác nhận ghi dòng nào không được đưa vào; AI chỉ nhận {BAO_GIA_1}; kết quả là bản Nháp đã tính giá — Trợ lý không phát hành, không gửi. Tóm tắt khác biệt: chỉ mã và số lượng. **Yêu cầu giảm giá** : từ chối, không sửa giá, chuyển nhân viên (B2).  
Chuyển màn hình| Chip mã → C2 của báo giá vừa tạo.  
Phụ thuộc quyền| Không quyền lập báo giá → máy chủ từ chối. Checklist VR chưa duyệt → backend từ chối.  
Đang tải & lỗi| Chưa rõ → không gửi lại tự động.  
Khôi phục| Kiểm tra trạng thái; soạn thủ công.  
Thành phần dùng lại| AIPanel, ChatMessage, ActionConfirmation, ToolCall, SourceTag, AIComposer  
  
## C7 · QT-02 · Khách xác nhận · kiểm tra trước tạo đơn

Artboard: C7 · bốn artboard, C2 · Khách xác nhận Đã thiết kế · Đã rà soát

Điểm vào| Khách gửi “Xác nhận BG-… vN” qua Zalo OA (mô phỏng bằng nút trên C2).  
---|---  
Thao tác người dùng| “Kiểm tra lại giá, tồn kho, công nợ”; điểm nối “Tạo đơn nháp — Batch D”.  
Dữ liệu hiển thị| Xác nhận hợp lệ → trạng thái Khách xác nhận. Xác nhận phiên bản bị thay thế hoặc báo giá hết hạn → hệ thống từ chối, ghi vào Hoạt động. Kiểm tra lại: không đổi → sẵn sàng tạo đơn; giá đổi → backend tạo phiên bản mới, phiên bản khách đã xác nhận bị thay thế, cần phát hành và khách xác nhận lại (Q1). Sau khi tạo đơn: trạng thái xử lý đơn lấy từ ERP; duyệt/từ chối do nhân viên có quyền trong ERP.  
Chuyển màn hình| Tạo đơn → Batch D (chưa thiết kế).  
Phụ thuộc quyền| Kiểm tra lại: người lập (đề xuất).  
Đang tải & lỗi| ERP không khả dụng: không kiểm tra được.  
Khôi phục| —  
Thành phần dùng lại| Button, ToolCall pending, Alert, Badge  
  
Rà soát nhất quán · vòng 2 #| Phát hiện| Mức| Xử lý  
---|---|---|---  
V1| Khung, sidebar, header, băng mô phỏng, toast, ba nhãn nguồn giống Workflow A và Batch B.| —| Đạt  
V2| Sau khi khách xác nhận hợp lệ, cảnh báo “từ chối xác nhận v1” cũ vẫn hiện.| Trung bình| Đã sửa, đã chạy lại  
V3| Hai lần gửi cùng ra một mã ZOA.| Thấp| Đã sửa  
V4| Đánh dấu PROPOSED / BLOCKED / CÓ CĂN CỨ hiện ngay cạnh quy tắc trên giao diện.| —| Mới  
V5| Mở Trợ lý ở 1440 px: bảng dòng cuộn ngang; Alert nhiều khách khớp: nút hành động nằm giữa khối.| Thấp| Chưa sửa  
  
Trạng thái còn thiếu #| Trạng thái| Tình trạng  
---|---|---  
CM1| Tạo đơn nháp, chi tiết đơn, chống tạo đơn trùng| Batch D  
CM2| Chiết khấu thủ công: mức tối đa, cấp phê duyệt| Chờ chủ nghiệp vụ (B2)  
CM3| Tạo / liên kết khách mới, bảng giá khách mới| Chờ chủ nghiệp vụ (Q3)  
CM4| Hàng đặt trước, đang nhập, giao thiếu| Chờ chủ nghiệp vụ (Q4)  
CM5| VAT và tổng thanh toán| BLOCKED (R4)  
CM6| Quyền hủy báo giá, thông báo cho khách khi hủy| Chưa xác định  
CM7| Thời hạn chính thức, ngày nghỉ| Chờ chủ nghiệp vụ (Q2)  
CM8| Phiên đăng nhập hết hạn khi đang tính giá / gửi| Đã có hướng · Q7 (decisions/05, PROPOSED · UX-12)  
  
Kiểm thử cần có khi làm backend (theo phản hồi) #| Nội dung  
---|---  
T1| Versioning: phiên bản đã phát hành không sửa; thay đổi tạo phiên bản mới; phiên bản bị thay thế không tạo đơn  
T2| Báo giá hết hạn: không nhận xác nhận, không tạo đơn  
T3| Không truy cập giá của khách khác  
T4| Không tự chiết khấu (UI, API, công cụ AI)  
T5| Giá thay đổi trước tạo đơn → phiên bản mới  
T6| Chống tạo đơn trùng (idempotency)
