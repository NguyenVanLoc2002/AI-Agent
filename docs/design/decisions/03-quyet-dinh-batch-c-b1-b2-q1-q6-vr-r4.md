B1 — Bộ trạng thái báo giá và bước duyệt

Áp dụng luồng nghiệp vụ theo tài liệu gốc:

Nháp → Đã gửi → Khách xác nhận → Đã tạo đơn nháp → Nhân viên duyệt / Từ chối.

Các trạng thái ngoại lệ: Hết hạn, Bị thay bởi phiên bản mới, Hủy.

Khi thiết kế giao diện, cần phân biệt:

Trạng thái báo giá: Nháp, Đã gửi, Khách xác nhận, Hết hạn, Bị thay thế, Hủy.

Trạng thái xử lý đơn: Đã tạo đơn nháp, Nhân viên duyệt, Từ chối.

Về bước duyệt:

Không bổ sung bước bắt buộc nhân viên duyệt từng báo giá tự động, vì tài liệu gốc không quy định bước này.

Báo giá đủ điều kiện có thể được backend tạo và gửi theo mẫu đã duyệt. Việc duyệt đơn hàng cuối cùng thuộc nhân viên có quyền trong ERP, không thuộc AI.

Trường hợp cần thay đổi giá hoặc xử lý ngoại lệ phải chuyển nhân viên có thẩm quyền.

B2 — Chiết khấu

Không cho phép AI tự tính, áp dụng hoặc thay đổi chiết khấu.

Giá báo cho khách phải lấy theo chính sách giá của ERP thông qua backend.

Khi người dùng yêu cầu giảm giá:

AI không được tự đồng ý.

Không được sửa đơn giá bằng prompt hoặc công cụ AI.

Chuyển yêu cầu đến nhân viên kinh doanh có quyền xử lý.

Nếu có điều chỉnh giá, phải áp dụng theo thẩm quyền và quy trình ERP.

Chưa bổ sung trường nhập chiết khấu có thể chỉnh sửa trực tiếp trong panel Trợ lý.

Còn chờ chủ nghiệp vụ: Có cho phép chiết khấu thủ công trên giao diện báo giá hay không, mức tối đa và cấp phê duyệt tương ứng.

Q1 — Phiên bản báo giá đã tạo có được sửa không?

Đề xuất: Không sửa trực tiếp phiên bản đã phát hành.

Quy tắc thiết kế:

Bản nháp chưa phát hành có thể được chỉnh sửa bởi người có quyền.

Sau khi phát hành phiên bản v1, nội dung kinh doanh của phiên bản đó trở thành immutable.

Khi cần điều chỉnh mã hàng, số lượng, đơn giá hoặc thông tin ảnh hưởng tổng tiền, phải tạo phiên bản mới v2.

Phiên bản cũ được giữ để tra cứu và audit.

Phiên bản bị thay thế không được sử dụng để tạo đơn nháp mới.

Nếu giá thay đổi khi kiểm tra lại trước tạo đơn, backend phải tạo báo giá phiên bản mới thay vì sửa giá trên phiên bản khách đã xác nhận.

Đây là quy tắc đề xuất nhằm hiện thực hóa versioning; tài liệu gốc chưa quy định rõ thời điểm khóa dữ liệu.

Q2 — Thời hạn hiệu lực báo giá

Tài liệu gốc dùng 3 ngày làm ví dụ, chưa phải chính sách được phê duyệt.

Đề xuất áp dụng cho dữ liệu mô phỏng: 3 ngày kể từ thời điểm phát hành báo giá.

Yêu cầu UI:

Hiển thị ngày giờ phát hành và hết hạn.

Có trạng thái Hết hạn.

Không cho phép xác nhận báo giá đã hết hạn để tạo đơn.

Trước khi tạo đơn luôn kiểm tra lại giá, tồn kho và điều kiện công nợ.

Nếu giá thay đổi, tạo phiên bản mới.

Thời hạn phải được cấu hình ở backend, không hard-code trong frontend.

Còn chờ chủ nghiệp vụ: Thời hạn hiệu lực chính thức, quy tắc tính ngày và xử lý ngày nghỉ.

Q3 — Nguồn dữ liệu khách hàng và tạo khách mới

ERP là nguồn dữ liệu khách hàng có thẩm quyền trong quy trình báo giá.

Theo tài liệu tham chiếu:

Tài khoản Zalo đã liên kết với khách trong ERP: sử dụng mã khách được backend xác định.

Chưa liên kết: yêu cầu số điện thoại và thực hiện tra cứu ERP.

Tìm thấy nhiều khách hoặc không tìm thấy: chuyển nhân viên xác minh.

AI không được tự tạo khách mới.

Khách mới áp dụng bảng giá khách mới theo chính sách của chủ nghiệp vụ.

Không cho phép LLM tự chọn mã khách, thay đổi mã khách hoặc yêu cầu giá của một khách hàng khác.

Đối với demo, sử dụng MockErpConnector và dữ liệu khách hàng giả lập có phân quyền rõ ràng.

Còn chờ chủ nghiệp vụ: Chính sách giá khách mới và quy trình nhân viên tạo/liên kết khách hàng.

Q4 — Có được báo giá mã hết hàng không?

Tài liệu gốc yêu cầu kiểm tra tồn kho nhưng chưa quy định rõ chính sách bán hàng hết tồn.

Đề xuất an toàn cho demo:

Cho phép tra cứu và hiển thị sản phẩm hết hàng.

Cho phép người có quyền xem thông tin giá từ ERP.

Với sản phẩm het_hang hoặc can_kiem_tra, không tự động phát hành báo giá cam kết cung ứng và không tự tạo đơn nháp.

Chuyển nhân viên kinh doanh xử lý yêu cầu báo giá hàng chưa có sẵn.

UI phải phân biệt rõ:

Còn hàng / Hết hàng / Cần kiểm tra

Không được tự chuyển trạng thái hết hàng thành còn hàng hoặc tự dự đoán ngày nhập kho.

Còn chờ chủ nghiệp vụ: Có hỗ trợ báo giá hàng đặt trước, hàng đang nhập hoặc giao thiếu hay không. Nếu có, phải bổ sung chính sách riêng trước khi triển khai.

Q6 — Kênh gửi báo giá cho khách

Kênh chính trong phạm vi MVP: Zalo Official Account (Zalo OA).

Luồng nghiệp vụ:

Quote Service tạo báo giá từ dữ liệu ERP.

Backend dựng bảng báo giá theo mẫu đã duyệt.

AI chỉ sử dụng tham chiếu báo giá, không nhận đơn giá thực tế.

Backend gửi nội dung báo giá đến khách qua Zalo OA.

Ghi nhận trạng thái gửi và tham chiếu giao dịch.

Giao diện nhân viên nội bộ hỗ trợ xem, xử lý và theo dõi báo giá theo quyền.

Không tự động gửi qua Zalo cá nhân, email hoặc SMS khi chưa có yêu cầu và tích hợp được phê duyệt.

Trong thiết kế demo, nút Gửi báo giá chỉ là điểm nối mô phỏng. Không ghi là đã gửi thực tế khi chưa có xác nhận từ dịch vụ gửi.

VR — Quy tắc gắn nhãn “Đã xác minh”

Áp dụng đúng ba nhãn trong mục 5 tài liệu:

Nhãn

Điều kiện

AI nhận diện

AI đưa ra nhận định từ văn bản hoặc hình ảnh; chưa đủ căn cứ xác minh

Dữ liệu tra cứu

Dữ liệu lấy từ nguồn catalog VIN/OE hoặc ERP, có nguồn truy xuất

Đã xác minh

Đã đáp ứng quy tắc đối chiếu được chủ nghiệp vụ phê duyệt

Quy tắc bắt buộc:

Không gắn nhãn Đã xác minh chỉ vì AI nhận diện có vẻ chính xác.

Mã OE phải lấy từ catalog được cấp phép hoặc dữ liệu khách cung cấp đã đối chiếu với danh mục ERP.

Phải có căn cứ đối chiếu giữa xe/VIN, mã OE, mã hàng và điều kiện phù hợp sản phẩm.

Chỉ mã hàng Đã xác minh mới được sử dụng để lập báo giá tự động.

Khi kết quả mâu thuẫn, thiếu dữ liệu hoặc có nhiều lựa chọn chưa phân giải, yêu cầu bổ sung thông tin hoặc chuyển nhân viên.

Dữ liệu mô phỏng phải được đánh dấu rõ là Mock, không tạo ấn tượng đã kiểm chứng trên ERP thật.

Không tự đặt ngưỡng confidence cho AI. Tài liệu yêu cầu đánh giá trên dữ liệu gán nhãn và phê duyệt ngưỡng sau khi đo.

Còn chờ chủ nghiệp vụ: Phê duyệt checklist đối chiếu kỹ thuật và quy tắc đủ điều kiện xác minh cho từng nhóm phụ tùng.

R4 — Giá báo đã gồm VAT chưa?

Chưa có quyết định nghiệp vụ trong tài liệu gốc.

Không được mặc định giá trả về từ ERP đã bao gồm hoặc chưa bao gồm VAT.

Đề xuất cách thiết kế:

Đơn giá phải phản ánh đúng giá nhận từ ERP.

Backend phải biết loại giá và quy tắc thuế áp dụng trước khi tính tổng.

Nếu có đủ thông tin, báo giá hiển thị rõ tiền hàng, tiền VAT và tổng thanh toán.

Nếu chưa xác định được cách xử lý VAT, không được tính hoặc hiển thị tổng thanh toán như một giá trị đã xác minh.

Mock ERP có thể dùng dữ liệu VAT giả lập được cấu hình tường minh, kèm nhãn môi trường demo.

Còn chờ chủ nghiệp vụ và đơn vị phụ trách ERP xác nhận:

Giá bán API ERP đích trả về đã gồm VAT hay chưa?

VAT được xác định theo mặt hàng, theo giao dịch hay theo cấu hình khác?

Backend phải lấy giá trị VAT từ ERP hay áp dụng quy tắc được chủ nghiệp vụ cung cấp?

Không triển khai công thức VAT theo giả định chưa có căn cứ.

Trạng thái phê duyệt và phạm vi thực hiện

ID

Kết luận

Trạng thái

B1

Dùng workflow trạng thái của tài liệu gốc; duyệt đơn tại ERP

Có căn cứ thiết kế

B2

AI không được tự chiết khấu

Có căn cứ; chính sách nhân viên còn mở

Q1

Khóa phiên bản phát hành, thay đổi bằng phiên bản mới

PROPOSED

Q2

Mô phỏng hiệu lực 3 ngày

PROPOSED

Q3

Khách hàng lấy từ ERP; AI không tạo khách mới

Có căn cứ; giá khách mới còn mở

Q4

Hết hàng không tự báo giá cam kết cung ứng

PROPOSED

Q6

Zalo OA là kênh gửi chính

Có căn cứ thiết kế

VR

Ba nhãn nguồn và xác minh theo quy tắc chủ nghiệp vụ

Có căn cứ; checklist xác minh còn mở

R4

Không giả định VAT đã bao gồm

BLOCKED — chờ quy tắc giá/thuế

Yêu cầu thực hiện: Tiếp tục hoàn thiện thiết kế và trạng thái UI dựa trên các quy tắc có căn cứ. Những chính sách chưa chốt phải được ghi PROPOSED hoặc BLOCKED, không đánh dấu Đã phê duyệt.

Không được coi demo sử dụng Mock ERP là bằng chứng Quote Service, authorization, ERP API, gửi Zalo OA hoặc idempotency đã hoạt động.

Khi triển khai backend, cần có positive/negative tests cho versioning, báo giá hết hạn, không truy cập giá của khách khác, không tự chiết khấu, giá thay đổi trước tạo đơn và chống tạo đơn trùng.