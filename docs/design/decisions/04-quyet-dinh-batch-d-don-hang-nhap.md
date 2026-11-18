# Quyết định nghiệp vụ Batch D · Đơn hàng nháp (17/11/2026)

Ghi lại từ hội thoại thiết kế. Đây là định hướng cho prototype, chưa phải phê duyệt cuối cùng về API hay bảo mật.

## Lựa chọn ban đầu
- **B4 · Ai tạo đơn nháp:** nhân viên bấm tạo, sau khi hệ thống kiểm tra lại giá, tồn kho và công nợ. AI không tự tạo đơn.
- **B5 · Duyệt đơn:** duyệt hoặc từ chối trong ERP. Giao diện chỉ hiện trạng thái đồng bộ và nút mở ERP.

## 4 quyết định bổ sung
1. **Người lập báo giá vắng mặt:** trưởng bộ phận hoặc nhân viên được phân công lại có đủ quyền tiếp tục xử lý. Mọi thao tác phải có audit (ai làm, thay ai, lúc nào).
2. **Khách đã xác nhận nhưng báo giá hết hạn:** không tạo đơn tự động. Backend kiểm tra lại giá, tồn kho, công nợ; lập báo giá hoặc phiên bản mới; khách phải xác nhận lại.
3. **ERP duyệt / từ chối đơn:** đề xuất thông báo cho nhân viên phụ trách và cập nhật trạng thái; khi từ chối thì hiện lý do lấy từ ERP. Cơ chế thông báo (kênh, tần suất) **chưa quy định**.
4. **Timeout khi tạo đơn:** 30 giây cho demo, cần xác nhận lại theo API ERP đích thật. Hết thời gian thì chuyển trạng thái **"Chờ đối soát"**, tra cứu theo idempotency key trước khi cho thử lại. **Tuyệt đối không tạo đơn trùng.**

## Vẫn PROPOSED / BLOCKED
- PROPOSED: khóa chống trùng = mã báo giá + phiên bản; thiếu hàng hoặc công nợ không đạt thì không tạo đơn, chuyển nhân viên có thẩm quyền.
- BLOCKED: VAT và tổng thanh toán (R4).
- Chi tiết màn hình: `../specs/30-batch-d-don-hang-nhap.md`.
