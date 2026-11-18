# Quyết định nghiệp vụ Batch E · Màn hình hỗ trợ (17/11/2026)

Ghi lại từ hội thoại thiết kế. Đây là định hướng cho prototype, chưa phải phê duyệt cuối cùng về bảo mật.

## Q7 · Đăng nhập và phiên làm việc
- Chỉ tài khoản nội bộ do backend quản lý, phân quyền theo RBAC. Không tự đăng ký.
- Phiên tối đa 8 giờ; hết hạn sau 30 phút không hoạt động.
- Khi phiên hết hạn: không tự tiếp tục thao tác ghi đang dở. Kiểm tra lại quyền cả khi công cụ AI đang chạy. Thao tác tạo đơn đang dở phải đối soát.
- SSO: cần xem xét thêm (chưa chốt). Quên mật khẩu: chưa thiết kế.

## Q8 · Tổng quan (dashboard) theo vai trò — chấp thuận có điều kiện
- **Nhân viên:** báo giá đang xử lý, chờ khách xác nhận, hết hạn; đơn nháp; khách được phân công.
- **Trưởng bộ phận:** số liệu tổng hợp và việc của bộ phận.
- **Quản trị:** tình trạng hệ thống, lỗi, cảnh báo. Không xem giá, doanh thu hay dữ liệu khách.
- Không có KPI nâng cao, không dự báo. Cần đối chiếu widget với dữ liệu thực tế.

## B6 · Ai xem được nhật ký hoạt động
4 vai trò: Nhân viên, Trưởng bộ phận, Quản trị hệ thống, Người được ủy quyền kiểm tra bảo mật.
- Quản trị không xem nội dung hội thoại và giá.
- Mở quyền đặc biệt cho quản trị cần Giám đốc phê duyệt và có audit.

## PF · Hồ sơ cá nhân
- Người dùng chỉ được sửa: tùy chọn giao diện, thông báo, đổi mật khẩu.
- Không được tự sửa: vai trò, quyền, bộ phận, chi nhánh, phạm vi khách hàng, mã nhân viên, định danh, trạng thái tài khoản.

## R10 · Yêu cầu cấp quyền
- Luồng: quản lý trực tiếp → IT. Nút "Yêu cầu cấp quyền" có lý do và phạm vi.
- Không tự cấp quyền. Backend chỉ cập nhật quyền sau khi được phê duyệt; có audit.
- Quyền đặc biệt cho quản trị cần Giám đốc duyệt.
- Nếu chưa có hệ thống tiếp nhận yêu cầu thì nút chỉ là điểm nối (mô phỏng), **không được hiển thị là đã cấp quyền**.

Chi tiết màn hình: `../specs/40-batch-e-man-hinh-ho-tro.md`.
