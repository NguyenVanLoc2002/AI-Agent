# Hướng dẫn viết giao diện — AutoParts Agent Sandbox

Mọi nội dung giao diện viết bằng **tiếng Việt**, ngắn gọn, chuyên nghiệp và hướng vào hành động. Mục tiêu: người dùng biết ngay **điều gì đã xảy ra, dữ liệu đến từ đâu, và cần làm gì tiếp theo**.

## 1. Nguyên tắc

1. **Tiếng Việt, kiểu đặt dấu mới:** Hủy, Xóa, Khóa, Hóa đơn, Ủy quyền. Chỉ giữ nguyên tiếng Anh cho mã kỹ thuật: SKU, OE, VIN, ERP, VAT.
2. **Ngắn và cụ thể.** Bỏ trạng từ, tính từ trang trí. Một câu = một ý. Nhãn nút 1–3 từ; thông báo tối đa 2 câu.
3. **Hướng hành động.** Nút bắt đầu bằng động từ. Lỗi luôn kèm bước tiếp theo.
4. **Không ngôn ngữ marketing.** Không "tuyệt vời", "siêu nhanh", "thông minh vượt trội", "chỉ với một cú nhấp", không dấu chấm than, không emoji.
5. **Không báo thành công trước khi backend xác nhận.** Trong lúc chờ dùng "Đang …"; chỉ khi có phản hồi thành công mới dùng "Đã …".
6. **Không lộ chi tiết kỹ thuật hay thông tin nhạy cảm.** Không stack trace, mã lỗi HTTP, tên bảng/endpoint, SQL, token, ID nội bộ, dữ liệu của người khác. Chỉ hiển thị **mã tham chiếu** (`REQ-7F2A`) để bộ phận hỗ trợ tra cứu.
7. **Viết hoa đầu câu.** Không viết hoa từng chữ; chữ in hoa chỉ dùng cho tiêu đề cột và nhãn nhỏ phía trên.
8. **Xưng hô:** hệ thống gọi người dùng là "bạn". Trợ lý AI xưng "tôi" và luôn nói rõ việc đã làm và chưa làm.

## 2. Phân biệt loại thông tin

| Loại | Nhãn / mẫu câu | Thể hiện |
|---|---|---|
| **Dữ liệu đã xác minh** | "Đã xác minh từ ERP · 09:42" | Teal, `shield-check`, luôn kèm thời điểm |
| **Gợi ý AI** | "Gợi ý AI — cần kiểm tra" | Violet, `sparkles`; không bao giờ tự lưu |
| **Dữ liệu lưu tạm** | "Bản lưu lúc 09:42 — chưa xác minh lại" | Amber, `clock` |
| **Cảnh báo** | "{Vấn đề}. {Ảnh hưởng}." | Amber, `triangle-alert` |
| **Lỗi** | "{Điều đã xảy ra}. {Hệ quả}. {Bước tiếp theo}." | Đỏ, `circle-x` |
| **Đang chờ xác nhận** | "Chờ bạn xác nhận" / "Chờ trưởng nhóm duyệt" | Amber, `circle-pause` |
| **Không có quyền** | "Bạn không có quyền {hành động}." | Xám, `lock` — không dùng đỏ |

Không gộp các loại vào một câu: "Giá 1.250.000 ₫ (gợi ý)" là **sai**; giá chỉ hiển thị kèm nguồn của nó.

## 3. Vòng đời một thao tác

```
Bấm "Gửi duyệt"  →  nút: "Đang gửi…" (khóa, chống gửi trùng)
                 →  backend xác nhận OK   →  toast: "Đã gửi duyệt báo giá BG-2026-0412"
                 →  backend lỗi           →  toast: "Chưa gửi được báo giá. Chưa có thay đổi nào được ghi. Vui lòng thử lại."
                 →  quá thời gian         →  "Chưa nhận được phản hồi từ hệ thống. Kiểm tra trạng thái báo giá trước khi gửi lại."
```

- Quá thời gian **không** có nghĩa là thất bại. Không khẳng định "Thất bại"; hướng người dùng kiểm tra trạng thái để tránh tạo trùng.
- Không dùng "Đã lưu" cho thao tác lưu cục bộ chưa gửi về máy chủ; dùng "Đã lưu nháp trên máy này".

## 4. Nhãn nút

| Nên | Không nên | Lý do |
|---|---|---|
| Tạo báo giá | Tạo mới | Thiếu đối tượng |
| Gửi duyệt | OK / Đồng ý | Không nói hành động |
| Xác nhận tạo đơn | Có | Hành động nhạy cảm phải lặp lại động từ |
| Thêm vào báo giá | Thêm ngay! | Marketing, dấu chấm than |
| Kiểm tra tồn kho | Click để xem | "Click" + mơ hồ |
| Thử lại | Reload | Tiếng Anh |
| Hủy | Thoát / Bỏ qua | "Hủy" là chuẩn duy nhất |
| Xóa dòng | Xóa | Thiếu đối tượng khi có nhiều thứ có thể xóa |
| Xuất Excel | Download | Tiếng Anh |
| Đang gửi… | Đang xử lý… | Nói rõ việc đang làm |

## 5. Tiêu đề cột bảng

Danh từ ngắn, không động từ, có đơn vị khi cần:

`Mã OE` · `SKU` · `Sản phẩm` · `Hãng` · `Chi nhánh` · `Tồn khả dụng` · `Đã giữ` · `Đang về` · `Đơn giá (₫)` · `CK (%)` · `Thành tiền (₫)` · `Trạng thái` · `Người phụ trách` · `Cập nhật lúc` · `Nguồn dữ liệu`

Tránh: "Số lượng sản phẩm còn lại trong kho" (dài), "Qty" (tiếng Anh), "Giá" (mơ hồ — giá bán hay giá vốn?).

## 6. Thông báo hệ thống (toast)

| Loại | Tiêu đề | Mô tả |
|---|---|---|
| Đang chờ | Đang tạo đơn hàng nháp… | Vui lòng không đóng trang. |
| Thành công (sau xác nhận) | Đã tạo đơn hàng nháp | DH-NHAP-0187 · Mã ERP SO-26-11-00931 |
| Cảnh báo | ERP phản hồi chậm | Giá hiển thị theo bản lưu lúc 09:42. |
| Lỗi | Chưa gửi được báo giá | Chưa có thay đổi nào được ghi. Vui lòng thử lại. |
| Không có quyền | Bạn không có quyền tạo đơn | Liên hệ quản lý chi nhánh để được cấp quyền. |

## 7. Trạng thái trống

Công thức: **{Chưa có / Không tìm thấy} + {đối tượng}** · **{cách tạo hoặc cách sửa}** · **{1 nút hành động}**

- Chưa có báo giá nào — "Tạo báo giá đầu tiên từ kết quả tra cứu." · [Tạo báo giá]
- Không tìm thấy kết quả cho "04465-0D1" — "Kiểm tra lại mã OE hoặc bỏ bớt bộ lọc." · [Xóa bộ lọc]
- Chưa có đơn hàng nháp — "Đơn nháp được tạo từ báo giá đã duyệt."
- Không có dữ liệu trong kỳ này — "Chọn khoảng thời gian khác."

## 8. Thông báo lỗi

| Nên | Không nên |
|---|---|
| Không kết nối được ERP. Giá và tồn kho tạm thời không khả dụng. Mã tham chiếu: REQ-7F2A | `ERR 502: upstream erp-gw.internal:8443 timeout` |
| Chưa lưu được báo giá. Vui lòng thử lại sau ít phút. | `SQLException: duplicate key "quotes_pkey"` |
| Bạn không có quyền xem giá chi nhánh này. | `403 Forbidden — role SALES_HN lacks PRICE_READ:HCM` |
| Số VIN phải gồm đúng 17 ký tự. | Dữ liệu không hợp lệ. |
| Phiên làm việc đã hết hạn. Vui lòng đăng nhập lại. | `JWT expired at 2026-11-16T09:42:00Z` |
| Không tìm thấy khách hàng với thông tin đã nhập. | Khách hàng KH-00412 thuộc chi nhánh HCM-Q7 (lộ dữ liệu ngoài quyền) |

Quy tắc: lỗi xác thực (form) nói **chính xác cần sửa gì**; lỗi hệ thống nói **hệ quả + bước tiếp theo + mã tham chiếu**; lỗi quyền nói **không có quyền gì + liên hệ ai**, không tiết lộ dữ liệu tồn tại hay không.

## 9. Trợ lý AI

- Mở đầu bằng kết quả, không vòng vo: "Chi nhánh Long Biên còn 24 bộ."
- Nêu nguồn: số liệu ERP luôn nằm trong khối "Đã xác minh từ ERP".
- Đề xuất thao tác bằng "Tôi đã chuẩn bị … — vui lòng xác nhận", không bao giờ "Tôi đã thêm/đã tạo/đã duyệt" khi chưa có xác nhận của người dùng và backend.
- Khi không biết hoặc không đọc được dữ liệu: "Tôi chưa thể xác minh … lúc này. Tôi sẽ không đưa ra số liệu ước đoán."
