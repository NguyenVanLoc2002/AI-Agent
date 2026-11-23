<!-- Trích xuất tự động từ bảng canvas `Inventory.dc.html`. Bản gốc: docs/design/prototypes/Inventory.dc.html -->

# Danh mục màn hình

## Ánh xạ UC (bổ sung 17/11/2026, đối chiếu docs/requirements/USE_CASES.md)
Thiết kế là DESIGN PROPOSAL. Cột “Xung đột” trỏ tới docs/DECISIONS_REQUIRED.md §UX; màn hình có xung đột chưa giải quyết thì chưa được code.

| Nhóm màn hình | UC / BR | Xung đột |
|---|---|---|
| SH-01 khung, SH-06 đăng nhập, SH-07 hết phiên (E5, E7) | Hạ tầng (ADR-010, A-06), không có UC riêng | UX-12 |
| A1–A7, PL-01..PL-05 tra cứu phụ tùng | UC-02 (phía nhân viên: chưa có UC riêng); BR-04..BR-07 | UX-13, UX-15 |
| A8, PL-06 thêm vào báo giá | UC-03 | UX-04 |
| AI-01, B1–B9 Trợ lý AI cho nhân viên | Chưa có UC (TOOL_CONTRACTS: nhân viên không dùng agent tool) | UX-01, UX-13, UX-16, UX-17 |
| B9 chuyển nhân viên | UC-05 | UX-17 |
| C1–C4 danh sách, chi tiết, phiên bản báo giá | UC-03; BR-14..BR-17 | UX-06, UX-07, UX-09, UX-14 |
| C5 lập báo giá cho khách, tra cứu khách | Chưa có UC | UX-02, UX-04 |
| C6 Trợ lý lập báo giá | UC-03 (AGENT đề xuất) | UX-01, UX-07 |
| C7 khách xác nhận, kiểm tra lại trước tạo đơn | UC-04; BR-14..BR-16 | UX-05, UX-06 |
| D1–D3 đơn hàng nháp, tạo đơn | UC-04 | UX-03, UX-08, UX-18 |
| D4 trạng thái duyệt từ ERP, thông báo | UC-04 (ghi nhận thủ công, A-15) | UX-10 |
| D5 nhờ Trợ lý duyệt/tạo đơn (kịch bản từ chối) | UC-04; BR-20, BR-21 | — |
| D2-Acting trưởng bộ phận xử lý thay | Chưa có UC (LEAD ngoài PoC) | UX-11 |
| E1 tổng quan, E3 hồ sơ, E4 yêu cầu cấp quyền | Chưa có UC | UX-11, UX-13 |
| E2 hoạt động / nhật ký | UC-07 một phần (SECREV chỉ xem trace đã redact) | UX-11 |
| F1 CH-01..03 chat khách, chờ nhân viên, trạng thái báo giá (`specs/50`, đã duyệt 23/11/2026) | UC-01, UC-03/04 phía khách, UC-05 | UX-05, UX-08, UX-09, UX-13, UX-14, UX-16 |
| F2 HO-01 hộp chờ, F3 HO-02 hội thoại đang xử lý (`specs/50`, đã duyệt) | UC-05, UC-06 | UX-13, UX-17 |
| F4 SI-01 Security Inspector (`specs/50`, đã duyệt) | UC-07 | UX-11, UX-13 |
| F5 OP-01 công tắc AI (`specs/50`, đã duyệt) | UC-08 | UX-13 |
  
  
Cập nhật sau review có điều kiện ngày 16/11/2026 , Batch B, C, D và E ngày 17/11/2026. “Đã rà soát” nghĩa là đã chạy thử thật trên canvas trong trình duyệt; không mục nào được phê duyệt chính thức.

Đã thiết kếCó trên canvas Đã rà soátĐã chạy thử thật trong trình duyệt Đã phê duyệtĐược người duyệt chấp thuận chính thức Bị chặnChờ quyết định nghiệp vụ

Workflow A và khung ứng dụng · không mục nào ở trạng thái “Đã phê duyệt” Mã| Màn hình / trạng thái| Thành phần design system| Artboard| Tình trạng| Bằng chứng  
---|---|---|---|---|---  
SH-01| Khung ứng dụng| Sidebar, AppHeader, AIPanel, ToastViewport| SH-01 · Khung| Đã thiết kế| Chỉ xem trên canvas; không bấm thử trong lần review này  
A1 · PL-01| Tra cứu — kết quả (SKU, OE, VIN)| SearchField, DataTable, TableToolbar, Pagination, SourceTag| A · Prototype| Đã thiết kế · Đã rà soát| Đã chạy: VIN mẫu → 8 dòng; BP-1042 → 1 dòng; 43512-0D150 → 1 dòng  
A3 · PL-02| VIN mô phỏng: không hợp lệ / không hỗ trợ / không có ánh xạ / có ánh xạ| SearchField error, EmptyState, Badge| A3 · ba artboard| Đã thiết kế · Đã rà soát| Đã chạy cả 4 trạng thái  
A4| Không tìm thấy (BP-9999X)| EmptyState| A4| Đã thiết kế · Đã rà soát| Đã chạy  
A · nhãn| Đổi sang 3 nhãn: AI nhận diện · Dữ liệu tra cứu · Đã xác minh; tên công cụ theo tài liệu 1.0| SourceTag lookup, ERPResult lookup, ChatMessage label| A · Prototype, A2 · chi tiết| Đã thiết kế · Đã rà soát| 17/11 theo quyết định của bạn. Đã chạy: bảng Trợ lý, cột Nguồn, drawer chi tiết. Lỗi danh mục chạy lại vẫn đúng  
A7| Đang tải · Lỗi tải danh mục| DataTable loading, EmptyState| A7 · hai artboard| Đã thiết kế · Đã rà soát| 17/11: sửa lỗi lặp “Mã tham chiếu”, đã chạy lại — mã hiện một lần, Thử lại vẫn tải kết quả  
A2 · PL-03| Chi tiết — drawer| Drawer, ERPResult, DataTable, DescriptionList| A2 · drawer| Đã thiết kế · Đã rà soát| Mở bằng chuột và phím Enter; Esc đóng drawer (khung xem cũng thoát Play)  
A2 · PL-03| Chi tiết — trang đầy đủ| PageHeader, ERPResult, Card| A2 · trang| Đã thiết kế · Đã rà soát| Đã chạy; quay lại giữ truy vấn, kết quả, lựa chọn  
PL-04| Giá & tồn trong phạm vi chi nhánh| ERPResult, DataTable| A2 · nội dung| Đã thiết kế · Đã rà soát| Chỉ hiện Long Biên; không có chi nhánh ngoài phạm vi  
PL-05| Mã tương đương (gợi ý AI)| Card nested, SourceTag, Button| A2 · nội dung| Đã thiết kế · Đã rà soát| “Kiểm tra ERP” → giá + nhãn ERP mô phỏng  
A5| ERP: bản lưu, dữ liệu cũ, chưa xác minh, quá thời gian, không khả dụng| Alert, SourceTag, ERPResult| A5 · năm artboard| Đã thiết kế · Đã rà soát| Đã xem cả 5; Thử lại từ bản lưu → xác nhận mới + toast  
A6| Không có quyền · chưa xác định quyền/phạm vi · không có quyền báo giá| Alert neutral, Button restricted, ERPResult restricted| A6 · ba artboard| Đã thiết kế · Đã rà soát| Đã xem; drawer khi không có quyền không lộ giá/tồn  
A8 · PL-06| Thêm vào báo giá — điểm nối luồng C| Button, Toast| A8| Bị chặn| Toast “chưa khả dụng, không lưu gì” đã chạy. Thiết kế thật bị chặn bởi B1, B2, R1  
AI-01| Trợ lý AI theo ngữ cảnh trong Workflow A| AIPanel, ChatMessage, ToolCall, ERPResult| Mọi artboard A| Đã thiết kế · Đã rà soát| Mở/đóng đã chạy; nội dung đổi theo trạng thái ERP và quyền  
R| Bố cục 1440 · 1280 · 1024| —| R · hai artboard + Prototype| Đã thiết kế · Đã rà soát| Đã xem ở chế độ Fit; còn cột “Nguồn” phải cuộn ngang khi mở AI (xem báo cáo)  
  
Batch B · Không gian làm việc AI · cập nhật theo phản hồi nghiệp vụ 17/11/2026 · không mục nào ở trạng thái “Đã phê duyệt” Mã| Màn hình / trạng thái| Thành phần design system| Artboard| Tình trạng| Bằng chứng  
---|---|---|---|---|---  
B1 · AI-01| Không gian hội thoại · lịch sử của bạn · lịch sử bộ phận (trưởng bộ phận) · không có quyền xem phiên người khác · quản trị không xem nội dung kinh doanh| ChatMessage, AIComposer, Alert, EmptyState, Tag| B · Prototype, B1 · sáu artboard| Đã thiết kế · Đã rà soát| Đã chạy: câu hỏi VIN, phiên cũ, tra cứu lại; trưởng bộ phận lọc theo nhân viên và mở phiên của Lê Thu Hà; nhân viên bị từ chối khi mở phiên người khác; quản trị thấy ghi chú khóa  
B2 · AI-01| Trợ lý theo ngữ cảnh| AIPanel, PartDetail| B2, B2 · 1024| Đã thiết kế · Đã rà soát| Đã chạy: so sánh dạng xếp dọc trong bảng; 1024 px breadcrumb rút gọn, Mở Không gian. Esc chưa xác minh (Esc thoát Play)  
B3 · AI-03| Dữ liệu tra cứu danh mục · tải thêm · so sánh| Bảng theo token, SourceTag lookup/ai| B3 · ba artboard| Đã thiết kế · Đã rà soát| Đã chạy: nhãn “Dữ liệu tra cứu”, cột “Chưa xác minh”, Tải thêm 5 mã → 8/8 + ghi chú AI chỉ dựa trên 3 mã đầu  
B4 · AI-03| Tiến độ · dừng (chưa rõ kết quả)| ToolCall pending/unknown| B4 · hai artboard, B7 · chậm| Đã thiết kế · Đã rà soát| Đã chạy: bấm Dừng khi đang chạy → “Chưa rõ kết quả / Chưa gửi” → Chạy lại hoàn tất  
B5 · AI-03| Dữ liệu tra cứu ERP mô phỏng| ERPResult lookup| B3 · VIN, B5 · ba artboard| Đã thiết kế · Đã rà soát| Đã chạy: thẻ “Dữ liệu tra cứu · ERP mô phỏng”, “Tra cứu lúc … (hệ thống ghi)”, AI chỉ nói “còn hàng”. Bản lưu, dữ liệu cũ, mã thay thế chưa xem lại sau sửa  
B6 · AI-03| Từ chối theo quyền · ngoài phạm vi (AI5) · sửa giá| ToolCall, ERPResult restricted| B6 · bốn artboard| Đã thiết kế · Đã rà soát| Đã chạy: hỏi Cầu Giấy → máy chủ từ chối + câu trả lời AI5; sửa giá → không giao cho Trợ lý. Không quyền giá & tồn chưa xem lại  
B7 · AI-03| Lỗi danh mục · ERP · dịch vụ AI| ERPResult error, EmptyState, ChatMessage error| B7 · sáu artboard| Đã thiết kế · Đã rà soát| Đã chạy: lỗi danh mục → Thử lại; ERP quá thời gian → Thử lại → dữ liệu tra cứu mới, giữ lịch sử; AI quá thời gian → Tạo lại câu trả lời  
B8 · AI-02| Chọn sản phẩm trực tiếp · điểm nối lập báo giá| Button restricted, Tag, ToolCall pending| B8 · năm artboard| Đã thiết kế · Đã rà soát| Đã chạy: Thêm vào lựa chọn, điểm nối lap_bao_gia; không có quyền xem giá → nút khóa “Hạn chế”  
B9 · AI-02| Chuyển nhân viên| ToolCall, Tag, Button| B9 · bốn artboard| Đã thiết kế · Đã rà soát| Đã chạy: Chuyển → HT-0042; chưa rõ → Kiểm tra trạng thái; thất bại → Thử lại → HT-0042  
RB| Bố cục 1440 · 1280 · 1024| —| B · 1280, B2 · 1024| Đã thiết kế · Đã rà soát| 1440 và 1024 đã chạy sau sửa; 1280 đã chạy ở vòng trước  
C · điểm nối| Lập báo giá · xác nhận báo giá · tạo đơn nháp · nhận diện ảnh| —| B8 · điểm nối| Đã thiết kế| Lập báo giá đã thiết kế ở Batch C (theo đề xuất, chờ B1, B2, Q1). Tạo đơn nháp → Batch D; nhận diện ảnh vẫn là điểm nối  
  
Batch C · Báo giá · cập nhật 17/11 theo quyết định B1, B2, Q1–Q6, VR, R4 · không mục nào “Đã phê duyệt” Mã| Màn hình / trạng thái| Thành phần design system| Artboard| Tình trạng| Bằng chứng  
---|---|---|---|---|---  
C1 · QT-01| Danh sách · trạng thái báo giá (B1) · cột Xử lý đơn (ERP)| DataTable, Tabs, Badge| C1-List| Đã thiết kế · Đã rà soát| Đã chạy vòng 2: 6 tab trạng thái, cột Xử lý đơn tách riêng  
C1| Cả bộ phận · rỗng · không có quyền| Tag, EmptyState| C1-ListTeam, C1-Empty, C1-NoAccess| Đã thiết kế| Vòng 2 chỉ render tự động (NoAccess đã chạy ở vòng 1)  
C2 · QT-02| Nháp đã tính giá → phát hành & gửi qua Zalo OA| Card, DataTable, Button| C2-Ready| Đã thiết kế · Đã rà soát| Đã chạy: BG-0417 phát hành v1, toast “Dịch vụ gửi đã xác nhận”, trạng thái Đã gửi, hết hạn +3 ngày  
C2| Đã gửi → tạo phiên bản mới → sửa → tính giá → phát hành (v1 bị thay thế)| Button, Input| C2-Quote, C2-NewVer, C4-Versions| Đã thiết kế · Đã rà soát| Đã chạy: v2 tạo, FO-2210 10→12, tính giá, phát hành; tab Phiên bản: v1 “Bị thay thế”  
C2| Nháp có dòng hết hàng / cần kiểm tra (Q4)| Alert, Badge| C2-Draft| Đã thiết kế · Đã rà soát| Đã chạy: nút phát hành tắt, Alert + Chuyển nhân viên kinh doanh  
C2| Có thay đổi chưa tính giá · không quyền xem giá · hết hạn · của người khác · hủy| Alert, EmptyState| C2-Dirty, C2-NoPrice, C2-Expired, C2-OtherDenied, C2-Cancelled| Đã thiết kế| Chỉ render tự động  
C3| Gửi Zalo OA chưa có xác nhận → kiểm tra trạng thái gửi| Alert, Badge| C3-SendUnknown| Đã thiết kế · Đã rà soát| Đã chạy: “Đã phát hành · chưa xác nhận gửi” → Kiểm tra → Đã gửi, không gửi lại  
C3| Đang gửi · gửi lỗi · đang tính · chưa rõ tính giá · lỗi Quote Service · ERP không khả dụng · xung đột nháp · giá khác phiên bản trước| Alert, Button loading| C3-Sending, C3-SendError, C3-Recalc, C3-Unknown, C3-Error, C3-ErpDown, C3-Conflict, C3-PriceChange| Đã thiết kế| Vòng 2 chỉ render tự động; luồng tính giá thành công đã chạy trong C2  
C4 · QT-04| Lịch sử phiên bản · so sánh · hoạt động| DataTable, Tabs| C4-Versions, C4-Activity| Đã thiết kế · Đã rà soát| Đã chạy: trạng thái từng phiên bản, mã ZOA riêng mỗi lần gửi  
C5 · QT-02| Tra cứu khách ERP: một / nhiều / không thấy| Input, Alert, Button| C5-New, C5-NewMulti, C5-NewNone, C5-NewInvalid| Đã thiết kế · Đã rà soát| Đã chạy: nhiều khớp → cảnh báo + chuyển nhân viên; không thấy → không tạo khách; KH-00321 → chọn được  
C6 · AI-02| Yêu cầu giảm giá → Trợ lý từ chối, chuyển nhân viên (B2)| ChatMessage, ToolCall| C6-AiDiscount| Đã thiết kế · Đã rà soát| Đã chạy: chuyen_nhan_vien → HT-0058  
C6| lap_bao_gia · tóm tắt khác biệt| ActionConfirmation, ToolCall| C6-AiLap, C6-AiLapDone, C6-AiLapRefused, C6-AiDiff| Đã thiết kế · Đã rà soát| Đã chạy ở vòng 1; vòng 2 chỉ đổi chữ (kết quả là Nháp, Trợ lý không phát hành) — render tự động  
C6| Không quyền lập qua Trợ lý · chưa rõ đã tạo| ToolCall| C6-AiLapUnauth, C6-AiLapUnknown| Đã thiết kế| Chỉ render tự động  
C7| Khách xác nhận phiên bản bị thay thế → từ chối; xác nhận phiên bản hiện hành → Khách xác nhận| Alert| C7-RejectSuperseded, C7-ConfirmedNew| Đã thiết kế · Đã rà soát| Đã chạy trên BG-0417: v1 bị từ chối, v2 được nhận  
C7| Kiểm tra lại giá, tồn, công nợ trước tạo đơn: không đổi / giá đổi → v3| Button, Alert| C7-ConfirmedNew, C7-PrecheckChange| Đã thiết kế · Đã rà soát| Đã chạy: không đổi → toast; preset giá đổi → v2 bị thay thế, v3 nháp  
C7| Xác nhận báo giá hết hạn → từ chối · đơn nháp chờ duyệt / đã duyệt / từ chối (ERP)| Alert, Badge| C7-RejectExpired, C2-Confirmed| Đã thiết kế| Chỉ render tự động  
RC| Bố cục 1024| —| RC-1024| Đã thiết kế| Đã xem ở vòng 1; chưa xem lại sau thay đổi  
  
Batch D · Đơn hàng nháp · 17/11 · B4, B5 theo lựa chọn của bạn (PROPOSED) · cập nhật 4 quyết định 17/11 · không mục nào “Đã phê duyệt” Mã| Màn hình / trạng thái| Thành phần design system| Artboard| Tình trạng| Bằng chứng  
---|---|---|---|---|---  
D1 · DO-01| Chờ tạo đơn · đơn chờ duyệt / duyệt / từ chối| DataTable, Tabs, Badge| D1-Todo, D1-Orders| Đã thiết kế · Đã rà soát| Đã chạy: 4 báo giá chờ tạo (1 hết hạn), mở xem lại từ danh sách  
D1| Cả bộ phận · rỗng · quyền chưa xác định| Tag, EmptyState| D1-Team, D1-Empty, D1-NoAccess| Đã thiết kế| Chỉ render tự động  
D2 · DO-02| Kiểm tra giá, tồn kho, công nợ → đạt| Card, Badge, Button| D2-Review, D2-Checking, D2-Ok| Đã thiết kế · Đã rà soát| Đã chạy: BG-0418 v2 kiểm tra → 4 mục đạt  
D2| Công nợ không đạt → chuyển nhân viên| Badge, Button| D2-Credit| Đã thiết kế · Đã rà soát| Đã chạy: BG-0420, không hiện số dư công nợ, nút tạo đơn tắt  
D2| Giá đổi · thiếu hàng · hết hạn · không có quyền tạo đơn| Alert, Badge, Button restricted| D2-PriceChange, D2-Stock, D2-Expired, D2-NoPerm| Đã thiết kế| Chỉ render tự động  
D3 · DO-05| Xác nhận tạo đơn nháp trên ERP → DN-2026-0241| ActionConfirmation, Toast| D3-Confirm, D3-Creating, D4-Created| Đã thiết kế · Đã rà soát| Đã chạy: thẻ xác nhận có khóa chống trùng; tạo xong mở đơn, toast  
D3| Quá 30 giây → Chờ đối soát → tra cứu theo khóa: thấy đơn / không thấy → tạo lại cùng khóa| Alert, Button| D3-Unknown, D3-NotFound| Đã thiết kế · Đã rà soát| Đã chạy cả hai nhánh; không tạo đơn trùng  
D3| ERP lỗi · đơn đã tồn tại · ERP không khả dụng| Alert| D3-Error, D3-Duplicate, D3-ErpDown| Đã thiết kế| Chỉ render tự động  
D4 · DO-06| Đơn chờ duyệt · bị từ chối (ERP)| Card, DescriptionList, Alert| D4-Pending, D4-Rejected| Đã thiết kế · Đã rà soát| Đã chạy: từ chối hiện người, thời điểm, lý do từ ERP + chuyển nhân viên  
D4| Đã duyệt · không đồng bộ được · không quyền xem giá| Alert, Badge| D4-Approved, D4-SyncDown, D4-NoPrice| Đã thiết kế| Chỉ render tự động; “Làm mới trạng thái” chưa bấm trong Chrome  
D5 · AI-02| Nhờ Trợ lý duyệt đơn → không giao cho Trợ lý| ChatMessage| D5-AiApprove| Đã thiết kế · Đã rà soát| Đã chạy  
D5| Nhờ Trợ lý tạo đơn| ChatMessage| D5-AiCreate| Đã thiết kế| Chỉ render tự động  
RD| Bố cục 1024| —| RD-1024| Đã thiết kế · Đã rà soát| Đã xem: hai thẻ xếp dọc hợp lý, sidebar thu gọn  
D2| Trưởng bộ phận xử lý thay người lập vắng mặt · audit| Alert, ActionConfirmation| D2-Acting| Đã thiết kế · Đã rà soát| Đã chạy: tạo DN-2026-0241, Hoạt động ghi người thao tác + dòng Audit  
D2| Nhân viên được phân công lại| Alert| D2-Reassigned| Đã thiết kế| Chỉ render tự động  
D2| Khách xác nhận nhưng hết hạn → backend kiểm tra lại & lập v2, chờ khách xác nhận lại| Alert, Button| D2-Expired, D2-ExpiredRenewed| Đã thiết kế · Đã rà soát| Đã chạy: không có nút tạo đơn; lập BG-2026-0410 v2  
D4| Thông báo ERP duyệt / từ chối (chuông, khối Thông báo, toast)| IconButton, Card| D4-Notify| Đã thiết kế · Đã rà soát| Đã chạy: chuông có số 1, khối Thông báo, mở đơn. Nhánh từ chối qua “Làm mới” chỉ render tự động  
  
Batch E · Màn hình hỗ trợ · cập nhật 17/11 theo Q7, Q8, B6, PF, R10 (đề xuất đã chọn) · không mục nào “Đã phê duyệt” Mã| Màn hình / trạng thái| Thành phần design system| Artboard| Tình trạng| Bằng chứng  
---|---|---|---|---|---  
E1 · DB-01| Tổng quan nhân viên (Q8 có điều kiện)| Badge, Button, Card| E1-Dashboard| Đã thiết kế · Đã rà soát| Đã chạy vòng 2: 5 ô theo Q8, hoạt động gần đây  
E1| Tổng quan trưởng bộ phận: tổng hợp + bảng theo nhân viên| Card, bảng theo token| E1-DashTeam| Đã thiết kế · Đã rà soát| Đã chạy  
E1| Tổng quan quản trị: tình trạng hệ thống · 1024| Card, Badge| E1-DashAdmin, RE-1024| Đã thiết kế| Chỉ render tự động  
E2 · AU-01| Hoạt động nhân viên · chi tiết| Tabs, DataTable, DescriptionList| E2-Audit, E2-AuditDetail| Đã thiết kế · Đã rà soát| Đã chạy vòng 1; vòng 2 đổi phạm vi theo B6, chưa chạy lại  
E2| Nhật ký kỹ thuật quản trị (B6)| Tabs, DataTable, Alert| E2-AuditAdmin| Đã thiết kế · Đã rà soát| Đã chạy: đăng nhập, lỗi hệ thống, AI Gateway; không có nghiệp vụ  
E2| Kiểm tra bảo mật theo phạm vi được phê duyệt| Alert, DataTable| E2-AuditAuditor| Đã thiết kế · Đã rà soát| Đã chạy: băng phạm vi 15/11–17/11, hết hạn 20/11  
E2| Cả bộ phận · không có quyền| Tag, EmptyState| E2-AuditTeam, E2-AuditDenied| Đã thiết kế| Chỉ render tự động  
E3 · PF-01| Hồ sơ: trường khóa, tùy chọn, thông báo, phiên (PF, Q7)| Select, Switch, DescriptionList| E3-Profile| Đã thiết kế · Đã rà soát| Đã chạy vòng 2  
E3| Đổi mật khẩu · hồ sơ trưởng bộ phận · nhân viên hạn chế| Input, Alert| E3-Password, E3-ProfileSupervisor, E3-ProfileRestricted| Đã thiết kế| Chỉ render tự động  
E4 · SH-03 · R10| Yêu cầu cấp quyền → “chưa gửi đi — điểm nối mô phỏng”| Dialog, Select, Textarea| E4-Request, E4-RequestSent| Đã thiết kế · Đã rà soát| Đã chạy: lỗi khi để trống, gửi → không hiện đã cấp quyền  
E4| Không có quyền truy cập| EmptyState| E4-Denied| Đã thiết kế · Đã rà soát| Đã chạy vòng 1  
E5 · SH-07| Sắp hết phiên · hết phiên khi đang tạo đơn| Dialog, Alert| E5-Warning, E5-Inflight| Đã thiết kế · Đã rà soát| Đã chạy vòng 2: Dialog DS, nội dung Q7; bỏ nút đóng (X) vì phiên phải đăng nhập lại  
E5| Hết 30 phút không hoạt động · hết 8 giờ · đăng nhập lại thất bại| Dialog| E5-Expired, E5-ExpiredMax, E5-ReloginFail| Đã thiết kế| Chỉ render tự động  
E7 · SH-06| Đăng nhập: sai thông tin → thành công| Input, Button, Alert| E7-Login, E7-LoginError| Đã thiết kế · Đã rà soát| Đã chạy: Đăng xuất → đăng nhập sai → đúng → Tổng quan  
E7| Tài khoản tạm khóa| Alert| E7-LoginLocked| Đã thiết kế| Chỉ render tự động  
E6 · SH-04| Lỗi hệ thống| EmptyState error| E6-Error| Đã thiết kế| Chỉ render tự động  
  
Các luồng sau · chưa thiết kế, chờ quyết định nghiệp vụ Mã| Màn hình| Luồng| Chặn bởi| Tình trạng  
---|---|---|---|---
