<!-- Trích xuất tự động từ bảng canvas `BatchESpec.dc.html`. Bản gốc: docs/design/prototypes/BatchESpec.dc.html -->

# Batch E · Đặc tả màn hình hỗ trợ  
  
Cập nhật 17/11/2026 theo quyết định Q7, Q8, B6, PF, R10. “Đã rà soát” nghĩa là đã chạy thử thật trên canvas trong Chrome; không mục nào được phê duyệt.

Đã thiết kếCó trên canvas Đã rà soátĐã chạy thử thật trong trình duyệt Đã phê duyệtĐược người duyệt chấp thuận chính thức Bị chặnChờ quyết định nghiệp vụ

## Phạm vi và căn cứ

  * Batch E = màn hình hỗ trợ: DB-01, AU-01, PF-01, SH-03, SH-07, SH-04 và đăng nhập. Áp dụng quyết định 17/11: Q7 (tài khoản nội bộ, 8 giờ, 30 phút — đề xuất cấu hình), Q8 (chấp thuận có điều kiện theo vai trò), B6 (phân quyền nhật ký 4 vai trò), PF (chỉ sửa thông tin không ảnh hưởng phân quyền), R10 (quản lý trực tiếp → IT; quyền đặc biệt cần Giám đốc). Tất cả vẫn là đề xuất, chưa có trong tài liệu gốc.
  * Có căn cứ từ các quyết định trước: audit cho mọi thao tác (D54), quản trị không xem nội dung kinh doanh (AI1), timeout và đối soát theo khóa (D57), thao tác của Trợ lý ghi người xác nhận (D42).
  * Không thêm năng lực mới cho Trợ lý: bảng Trợ lý không xuất hiện ở Batch E.
  * Dữ liệu mô phỏng. Không phải bằng chứng xác thực, phân quyền hay lưu audit đã hoạt động.

## E1 · DB-01 · Tổng quan — việc cần làm

Artboard: E · Prototype, E1 · ba artboard, RE-1024 Đã thiết kế · Đã rà soát

Điểm vào| Mục “Tổng quan” trên sidebar (trang mặc định vẫn là Tra cứu phụ tùng — chưa đổi).  
---|---  
Thao tác người dùng| Bấm từng ô để mở đúng danh sách; “Xem tất cả” hoạt động.  
Dữ liệu hiển thị| Theo vai trò (Q8 chấp thuận có điều kiện). **Nhân viên:** báo giá đang xử lý, chờ khách xác nhận, hết hạn, đơn nháp, khách được phân công, hoạt động gần đây. **Trưởng bộ phận:** số liệu tổng hợp bộ phận, việc cần xử lý thay / phân công, bảng đếm việc theo nhân viên (không phải KPI). **Quản trị:** tình trạng dịch vụ, lỗi 24 giờ, đăng nhập thất bại, từ chối theo quyền ở AI Gateway; không có giá, doanh thu, dữ liệu khách. Không KPI nâng cao, dự báo, phân tích lịch sử mua. Danh sách ô cần đối chiếu widget thực tế.  
Chuyển màn hình| Ô → Báo giá / Đơn hàng nháp (prototype khác); “Xem tất cả” → E2.  
Phụ thuộc quyền| Theo vai trò như trên. Kiểm tra bảo mật: Tổng quan không áp dụng, dẫn sang Hoạt động. Quản trị: “Yêu cầu quyền đặc biệt” (cần Giám đốc).  
Đang tải & lỗi| Lỗi tải → E6.  
Khôi phục| Thử lại.  
Thành phần dùng lại| PageHeader, Badge, Button, Card, Alert  
  
## E2 · AU-01 · Hoạt động (nhật ký audit)

Artboard: E2 · sáu artboard Đã thiết kế · Đã rà soát

Điểm vào| Mục “Hoạt động” trên sidebar; “Xem tất cả” từ E1.  
---|---  
Thao tác người dùng| Lọc theo loại (Báo giá, Đơn nháp, Trợ lý AI, Đăng nhập & quyền); trưởng bộ phận đổi “Của tôi / Cả bộ phận”; bấm dòng để xem chi tiết.  
Dữ liệu hiển thị| Thời điểm, người thao tác + vai trò + “xử lý thay” (D54), hành động, đối tượng, kết quả (thành công / bị từ chối / chờ đối soát), tham chiếu hoặc khóa chống trùng. Thao tác của Trợ lý ghi người xác nhận. Chỉ xem, không sửa, không xóa.  
Chuyển màn hình| Chi tiết mở ngay dưới bảng.  
Phụ thuộc quyền| B6 (đề xuất đã chọn): nhân viên — thao tác của mình; trưởng bộ phận — nghiệp vụ của bộ phận; quản trị — **nhật ký kỹ thuật riêng** (đăng nhập, lỗi hệ thống, metadata AI Gateway đã loại dữ liệu nhạy cảm), không thấy nghiệp vụ, hội thoại, giá; kiểm tra bảo mật — audit theo phạm vi và thời hạn được Giám đốc phê duyệt, mỗi lần xem được ghi audit; nhân viên hạn chế: không có quyền.  
Đang tải & lỗi| Rỗng theo bộ lọc: EmptyState.  
Khôi phục| —  
Thành phần dùng lại| Tabs, Tag, DataTable, Badge, DescriptionList, Alert, EmptyState  
  
## E3 · PF-01 · Hồ sơ cá nhân

Artboard: E3 · bốn artboard Đã thiết kế · Đã rà soát

Điểm vào| Biểu tượng người dùng trên header.  
---|---  
Thao tác người dùng| Sửa mật độ hiển thị, bật/tắt thông báo, “Lưu tùy chọn”; “Đổi mật khẩu” (mật khẩu hiện tại + mới); “Đăng xuất”; “Yêu cầu cấp quyền”.  
Dữ liệu hiển thị| PF (đề xuất đã chọn): khóa — mã nhân viên, tài khoản, vai trò, bộ phận, chi nhánh / phạm vi khách hàng, trạng thái tài khoản. Phiên: tài khoản nội bộ, tối đa 8 giờ, 30 phút không hoạt động (Q7). Quyền do máy chủ trả về.  
Chuyển màn hình| —  
Phụ thuộc quyền| Theo persona.  
Đang tải & lỗi| Mật khẩu hiện tại sai / mật khẩu mới chưa đạt → lỗi tại trường.  
Khôi phục| Đổi mật khẩu ghi audit; các phiên khác đăng xuất (đề xuất).  
Thành phần dùng lại| Card, DescriptionList, Badge, Button  
  
## E4 · SH-03 · R10 · Không có quyền · Yêu cầu cấp quyền

Artboard: E4 · ba artboard, E2 · không có quyền Đã thiết kế · Đã rà soát

Điểm vào| Mở trang không có quyền (ví dụ Quản trị người dùng, Hoạt động với nhân viên hạn chế).  
---|---  
Thao tác người dùng| “Yêu cầu cấp quyền” → biểu mẫu (quyền đề nghị — chỉ quyền chưa có, phạm vi, lý do bắt buộc); “Về Tổng quan”.  
Dữ liệu hiển thị| R10 (đề xuất đã chọn): gửi qua quản lý trực tiếp → IT / quản trị theo phê duyệt; quyền đặc biệt với dữ liệu kinh doanh cần Giám đốc. Chưa có hệ thống tiếp nhận nên sau khi gửi hiện **“Yêu cầu chưa được gửi đi — điểm nối mô phỏng, quyền không thay đổi”** ; không bao giờ hiện đã cấp quyền.  
Chuyển màn hình| Về E1.  
Phụ thuộc quyền| Người dùng không tự cấp quyền.  
Đang tải & lỗi| Thiếu quyền đề nghị / lý do → lỗi.  
Khôi phục| —  
Thành phần dùng lại| EmptyState unauthorized, Dialog, Select, Input, Textarea, Alert  
  
## E5 · SH-07 · Phiên đăng nhập hết hạn

Artboard: E5 · năm artboard Đã thiết kế · Đã rà soát

Điểm vào| Phiên sắp hết hoặc đã hết khi đang ở bất kỳ trang nào.  
---|---  
Thao tác người dùng| “Tiếp tục làm việc” (gia hạn); “Đăng nhập lại”; “Đăng xuất”.  
Dữ liệu hiển thị| Q7: tối đa 8 giờ; hết sau 30 phút không hoạt động (cấu hình đề xuất). Sắp hết: băng cảnh báo 5 phút trước, “Tiếp tục làm việc” chỉ đặt lại thời gian không hoạt động. Hết hạn: hộp thoại chặn thao tác; thay đổi chưa lưu giữ trên thiết bị (đề xuất). Có thao tác đang chờ (ví dụ tạo đơn): không gửi lại tự động; sau khi đăng nhập, đối soát theo khóa chống trùng (D57).  
Chuyển màn hình| Đăng nhập lại → về đúng trang đang làm.  
Phụ thuộc quyền| —  
Đang tải & lỗi| Đăng nhập lại thất bại → lỗi AUTH-401, thử lại.  
Khôi phục| Đăng nhập lại; đối soát thao tác đang chờ.  
Thành phần dùng lại| Dialog (DS), Alert, Button  
  
## E7 · SH-06 · Đăng nhập

Artboard: E7 · ba artboard Đã thiết kế · Đã rà soát

Điểm vào| Mở ứng dụng khi chưa đăng nhập; sau “Đăng xuất”.  
---|---  
Thao tác người dùng| Nhập tài khoản, mật khẩu; Enter hoặc “Đăng nhập”.  
Dữ liệu hiển thị| Tài khoản nhân viên nội bộ, xác thực qua backend, RBAC. Không có nút đăng ký. Quên mật khẩu: liên hệ quản lý / IT. SSO: chờ xác minh hệ thống danh tính.  
Chuyển màn hình| Thành công → Tổng quan.  
Phụ thuộc quyền| —  
Đang tải & lỗi| Sai thông tin: một thông báo chung, không nói trường nào sai. Tạm khóa: AUTH-423, thử lại sau 15 phút (đề xuất).  
Khôi phục| Thử lại; liên hệ IT.  
Thành phần dùng lại| Input, Button, Alert  
  
## E6 · SH-04 · Lỗi hệ thống / không tải được

Artboard: E6 Đã thiết kế · Đã rà soát

Điểm vào| Khi một trang không tải được.  
---|---  
Thao tác người dùng| “Thử lại”.  
Dữ liệu hiển thị| Thông báo không có thay đổi nào được thực hiện, mã tham chiếu.  
Chuyển màn hình| —  
Phụ thuộc quyền| —  
Đang tải & lỗi| —  
Khôi phục| Thử lại; gửi mã cho hỗ trợ.  
Thành phần dùng lại| EmptyState error  
  
Rà soát nhất quán #| Phát hiện| Mức| Xử lý  
---|---|---|---  
V1| Khung, sidebar, header, băng mô phỏng, toast, nhãn PROPOSED giống Batch C, D.| —| Đạt  
V2| Bốn biểu tượng không có trong design system (user-round, log-in, message-square, x-circle) dùng ở Batch C, D, E — hiển thị trống.| Trung bình| Đã sửa cả ba batch bằng biểu tượng có sẵn  
V3| Tổng quan báo 1 “Chờ đối soát” nhưng Hoạt động ghi đã đối soát xong.| Thấp| Đã sửa dữ liệu mẫu  
V4| Quản trị vẫn thấy cột Tham chiếu (chứa mã báo giá).| Trung bình| Đã sửa: ẩn  
V5| Chuyển trang giữ vị trí cuộn cũ (Hồ sơ mở ở giữa trang).| Thấp| Đã sửa: cuộn về đầu  
V6| Ghi chú trước nói DS chưa có Modal — sai: DS có Dialog. Hộp thoại hết phiên và yêu cầu quyền nay dùng Dialog.| —| Đã sửa  
V7| Biểu mẫu yêu cầu quyền giữ cảnh báo lỗi sau khi đã nhập; liệt kê cả quyền người dùng đã có.| Thấp| Đã sửa (chưa chạy lại trong Chrome)  
  
Trạng thái còn thiếu #| Trạng thái| Tình trạng  
---|---|---  
EM1| Đặt lại mật khẩu, SSO| Chưa thiết kế · chờ xác minh hệ thống danh tính  
EM2| Quản trị người dùng, phân quyền, phân công lại báo giá| Chưa thiết kế · B6  
EM3| Xuất nhật ký, lọc theo thời gian, tìm theo mã| Chưa thiết kế  
EM4| Phê duyệt quyền đặc biệt phía Giám đốc; hệ thống tiếp nhận yêu cầu quyền| Chưa có · điểm nối  
EM5| Danh sách yêu cầu chuyển nhân viên phía người nhận| Chưa thiết kế · HO  
EM6| Thông báo ngoài ứng dụng (Zalo, email)| Chưa quy định  
EM7| Mất mạng (offline)| Chưa thiết kế · DS có EmptyState offline  
  
Kiểm thử cần có khi làm backend #| Nội dung  
---|---  
T1| Audit không sửa, không xóa được qua giao diện hay API  
T2| Quản trị chỉ nhận siêu dữ liệu audit  
T3| Hết phiên giữa thao tác ghi: không gửi lại tự động; đối soát theo khóa sau khi đăng nhập  
T4| Lần truy cập bị từ chối được ghi audit  
T5| Hết phiên sau 30 phút không hoạt động và 8 giờ tối đa theo cấu hình backend  
T6| Quản trị không nhận được nội dung kinh doanh trong nhật ký; metadata AI Gateway đã loại dữ liệu nhạy cảm  
T7| Quyền chỉ thay đổi sau phê duyệt hợp lệ; thay đổi quyền ghi audit
