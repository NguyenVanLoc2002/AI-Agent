<!-- Trích xuất tự động từ bảng canvas `BatchDSpec.dc.html`. Bản gốc: docs/design/prototypes/BatchDSpec.dc.html -->

# Batch D · Đặc tả màn hình Đơn hàng nháp  
  
Cập nhật 17/11/2026 theo 4 quyết định của bạn. “Đã rà soát” nghĩa là đã chạy thử thật trên canvas trong Chrome; không mục nào được phê duyệt.

Đã thiết kếCó trên canvas Đã rà soátĐã chạy thử thật trong trình duyệt Đã phê duyệtĐược người duyệt chấp thuận chính thức Bị chặnChờ quyết định nghiệp vụ

## Phạm vi và căn cứ

  * **Lựa chọn của bạn 17/11 (PROPOSED):** B4 — nhân viên bấm tạo đơn nháp sau khi kiểm tra lại; B5 — duyệt / từ chối đơn trong ERP, giao diện chỉ hiện trạng thái đồng bộ và nút mở ERP.
  * **Có căn cứ từ phản hồi:** tạo đơn cần khách xác nhận theo mã và phiên bản; luôn kiểm tra lại giá, tồn kho, công nợ trước khi tạo; giá đổi → phiên bản báo giá mới; duyệt đơn cuối cùng thuộc nhân viên có quyền trong ERP, không thuộc AI.
  * **Quyết định của bạn 17/11 (lần 2):** người lập vắng → trưởng bộ phận hoặc nhân viên được phân công lại có đủ quyền tiếp tục, có audit; khách xác nhận nhưng hết hạn → không tạo đơn tự động, backend kiểm tra lại và lập phiên bản mới; ERP duyệt/từ chối → thông báo nhân viên phụ trách (cơ chế chưa quy định); timeout 30 giây cho demo → Chờ đối soát, tra cứu theo idempotency key trước khi retry.
  * **PROPOSED:** khóa chống trùng = mã báo giá + phiên bản; báo giá hết hạn không tạo đơn (Q2); thiếu hàng không tạo đơn (Q4); công nợ không đạt → chuyển nhân viên. **BLOCKED:** VAT và tổng thanh toán (R4).
  * Mock: MockErpConnector, Quote Service. Không phải bằng chứng API ERP đích, phân quyền, chống tạo đơn trùng hay đồng bộ trạng thái đã hoạt động.
  * Ngoài phạm vi: sửa đơn, giao hàng, hóa đơn, hủy đơn trên ERP, thông báo cho khách sau khi duyệt/từ chối.

## D1 · DO-01 · Danh sách: chờ tạo đơn và đơn nháp

Artboard: D · Prototype, D1 · năm artboard Đã thiết kế · Đã rà soát

Điểm vào| Mục “Đơn hàng nháp” trên sidebar; nút “Danh sách”.  
---|---  
Thao tác người dùng| Tab Chờ tạo đơn · Chờ duyệt · Nhân viên duyệt · Từ chối; trưởng bộ phận đổi “Của tôi / Cả bộ phận”; bấm dòng.  
Dữ liệu hiển thị| Chờ tạo đơn: báo giá khách đã xác nhận theo mã và phiên bản, chưa có đơn — thời điểm xác nhận, hiệu lực (hết hạn được đánh dấu), tiền hàng, người lập. Đơn nháp: mã DN, báo giá nguồn, trạng thái xử lý từ ERP, tiền hàng, tạo lúc, đồng bộ lúc.  
Chuyển màn hình| Báo giá → D2. Đơn → D4.  
Phụ thuộc quyền| Nhân viên: của mình. Trưởng bộ phận: cả bộ phận, chỉ xem báo giá người khác. Quyền chưa xác định / quản trị: EmptyState, không tải dữ liệu.  
Đang tải & lỗi| Rỗng: EmptyState theo tab.  
Khôi phục| —  
Thành phần dùng lại| PageHeader, Tabs, Tag, DataTable, Badge, EmptyState  
  
## D2 · DO-02 · Xem lại và kiểm tra trước khi tạo đơn

Artboard: D2 · mười một artboard Đã thiết kế · Đã rà soát

Điểm vào| Bấm báo giá ở tab “Chờ tạo đơn”.  
---|---  
Thao tác người dùng| “Kiểm tra giá, tồn kho, công nợ”; “Kiểm tra lại”; “Mở báo giá vN+1” (giá đổi); “Chuyển nhân viên có thẩm quyền” (thiếu hàng, công nợ).  
Dữ liệu hiển thị| Bốn kết quả do backend trả: Hiệu lực, Giá, Tồn kho, Công nợ — đạt / không đạt kèm lý do. Công nợ chỉ hiện đạt / không đạt, không hiện số dư, không gửi cho Trợ lý. Dòng hàng của phiên bản khách xác nhận (đã khóa), tiền hàng; VAT BLOCKED (R4).  
Chuyển màn hình| Đạt → D3. Giá đổi → báo giá phiên bản mới (Batch C).  
Phụ thuộc quyền| Người phụ trách; nhân viên được phân công lại; hoặc trưởng bộ phận có đủ quyền khi người lập vắng mặt (quyết định 17/11). Mọi thao tác ghi audit: người thao tác, vai trò, thời điểm, khóa. Thẻ xác nhận ghi “Người thao tác” khi xử lý thay.  
Đang tải & lỗi| Đang kiểm tra; ERP không khả dụng → không kiểm tra, không tạo. Khách xác nhận nhưng hết hạn → không tạo đơn tự động; nút “Kiểm tra lại & lập phiên bản mới” để backend kiểm tra giá, tồn kho, công nợ và lập phiên bản mới, khách xác nhận lại (quyết định 17/11).  
Khôi phục| Kiểm tra lại; chuyển nhân viên.  
Thành phần dùng lại| Card, Badge, Button, Alert, DataTable, DescriptionList, SourceTag  
  
## D3 · DO-05 · Xác nhận tạo đơn nháp trên ERP

Artboard: D3 · bảy artboard Đã thiết kế · Đã rà soát

Điểm vào| “Tạo đơn nháp trên ERP” khi kiểm tra đạt.  
---|---  
Thao tác người dùng| “Tạo đơn nháp” / “Không tạo” trên thẻ xác nhận; “Kiểm tra trạng thái”; “Thử lại”.  
Dữ liệu hiển thị| Thẻ xác nhận: báo giá + phiên bản + thời điểm khách xác nhận, khách, số dòng, tiền hàng, **khóa chống trùng** (mã báo giá-phiên bản). Ghi rõ đơn chờ duyệt trong ERP, không duyệt ở đây.  
Chuyển màn hình| Thành công → D4 (đơn mới, toast).  
Phụ thuộc quyền| Không có quyền tạo đơn: nút khóa; máy chủ từ chối nếu gọi.  
Đang tải & lỗi| Đang tạo (tối đa 30 giây — đề xuất cho demo, cần xác nhận theo API ERP đích). Quá thời gian → **Chờ đối soát** : tra cứu theo khóa chống trùng trước khi tạo lại; không thấy → tạo lại cùng khóa; tuyệt đối không tạo đơn trùng. Lỗi ERP-502 → Thử lại cùng khóa. Tạo lần hai → backend trả đơn đã có. Danh sách đánh dấu báo giá đang “Chờ đối soát”.  
Khôi phục| Kiểm tra trạng thái tìm thấy đơn → mở đơn, không tạo thêm.  
Thành phần dùng lại| ActionConfirmation (note riêng), Button loading, Alert, Toast  
  
## D4 · DO-06 · Đơn nháp và kết quả xử lý từ ERP

Artboard: D4 · bảy artboard Đã thiết kế · Đã rà soát

Điểm vào| Sau khi tạo; bấm đơn ở D1.  
---|---  
Thao tác người dùng| “Làm mới trạng thái”; “Mở trên ERP” (điểm nối); “Chuyển nhân viên có thẩm quyền” khi bị từ chối.  
Dữ liệu hiển thị| Trạng thái xử lý đơn từ ERP: Đã tạo đơn nháp · chờ duyệt / Nhân viên duyệt / Từ chối, người và thời điểm, lý do từ ERP, đồng bộ lần cuối. Nguồn: báo giá + phiên bản, khóa chống trùng. Dòng hàng, tiền hàng. Hoạt động.  
Chuyển màn hình| Danh sách theo trạng thái. Thông báo → mở đơn.  
Phụ thuộc quyền| Duyệt / từ chối chỉ trong ERP (B5 · PROPOSED); giao diện không duyệt, không sửa đơn. Không quyền xem giá → “Hạn chế”.  
Đang tải & lỗi| Không đồng bộ được → hiện trạng thái lần cuối + Thử lại. Khi ERP duyệt / từ chối: thông báo cho nhân viên phụ trách (chuông trên header + khối Thông báo + toast), cập nhật trạng thái, từ chối thì hiện lý do ERP cung cấp. Cơ chế thông báo: PROPOSED, chưa quy định trong tài liệu gốc.  
Khôi phục| Làm mới; mở trên ERP.  
Thành phần dùng lại| Card, DescriptionList, Badge, Button, Alert, DataTable  
  
## D5 · AI-02 · Trợ lý trong Đơn hàng nháp

Artboard: D5 · hai artboard Đã thiết kế · Đã rà soát

Điểm vào| Bảng Trợ lý trên D2, D4.  
---|---  
Thao tác người dùng| Câu gợi ý hoặc tự gõ.  
Dữ liệu hiển thị| Trợ lý không có công cụ ở đây: không tạo đơn, không duyệt, không đọc trạng thái hay công nợ. Trả lời chỉ ra bước trên màn hình; ghi “Không có công cụ nào được gọi”.  
Chuyển màn hình| —  
Phụ thuộc quyền| —  
Đang tải & lỗi| —  
Khôi phục| —  
Thành phần dùng lại| AIPanel, ChatMessage, AIComposer, SourceTag  
  
Rà soát nhất quán #| Phát hiện| Mức| Xử lý  
---|---|---|---  
V1| Khung, sidebar, header, băng mô phỏng, toast, nhãn PROPOSED / BLOCKED giống Batch C.| —| Đạt  
V2| Thẻ xác nhận tạo đơn dùng câu mặc định “AI chỉ đề xuất…” dù không phải đề xuất của AI. Lỗi tương tự ở hộp “Hủy báo giá” Batch C.| Trung bình| Đã sửa cả hai bằng prop note  
V3| Badge kết quả kiểm tra bị kéo giãn hết chiều ngang.| Thấp| Đã sửa  
V4| Khi “Chưa rõ đã tạo”, nút “Kiểm tra lại” vẫn bấm được.| Thấp| Đã sửa: tắt cho tới khi kiểm tra trạng thái  
V5| Mở Trợ lý ở 1440 px: thẻ “Trạng thái trên ERP” chật, badge chạm cột bên cạnh.| Thấp| Chưa sửa  
V6| Thẻ xác nhận: giá trị “Người thao tác” dài đè lên nhãn.| Thấp| Đã sửa: rút gọn “Bạn · thay Trần Quốc Bảo”  
  
Trạng thái còn thiếu #| Trạng thái| Tình trạng  
---|---|---  
DM1| Khách xác nhận nhưng hết hạn| Đã thiết kế theo quyết định 17/11  
DM2| Hủy đơn nháp chưa duyệt, sửa đơn| Ngoài phạm vi · theo ERP  
DM3| Thông báo khi ERP duyệt / từ chối| Đã thiết kế trong ứng dụng; kênh (Zalo, email…) chưa quy định  
DM4| Ngưỡng quá thời gian khi tạo đơn| 30 giây cho demo · cần xác nhận theo API ERP đích  
DM5| Phiên đăng nhập hết hạn khi đang tạo đơn| Đã có hướng · Q7 (decisions/05, PROPOSED · UX-12)  
DM6| Người lập vắng mặt| Đã thiết kế: trưởng bộ phận / phân công lại + audit. Màn hình phân công lại chưa thiết kế  
DM7| Trạng thái “Chờ đối soát” kéo dài (đối soát cũng lỗi)| Chưa thiết kế  
  
Kiểm thử cần có khi làm backend #| Nội dung  
---|---  
T1| Chống tạo đơn trùng: cùng khóa báo giá-phiên bản gửi hai lần chỉ ra một đơn; timeout → tra cứu trước khi retry  
T7| Audit ghi đúng người thao tác khi trưởng bộ phận / người được phân công xử lý thay  
T2| Không tạo đơn từ phiên bản bị thay thế hoặc báo giá hết hạn  
T3| Giá thay đổi trước khi tạo → phiên bản mới, không tạo đơn  
T4| Không trả số dư công nợ cho giao diện hay Trợ lý; chỉ đạt / không đạt  
T5| Người không có quyền tạo đơn bị máy chủ từ chối  
T6| Trạng thái hiển thị đúng nguồn ERP và thời điểm đồng bộ
