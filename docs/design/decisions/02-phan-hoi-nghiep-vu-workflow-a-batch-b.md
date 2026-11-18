# PHẢN HỒI NGHIỆP VỤ — WORKFLOW A & BATCH B

**Dự án:** AutoParts Agent Sandbox (APAS) – Mock ERP  
**Nguồn:** tài liệu yêu cầu tham chiếu riêng (không có trong repo; bản tóm tắt: BRD@af2d09f)  
**Mục tiêu:** Hoàn thiện thiết kế Trợ lý AI theo đúng nghiệp vụ MVP và ranh giới bảo mật.

## 1. Q5 — Trợ lý AI được phép sử dụng những công cụ nào?

### 1.1. Phạm vi theo tài liệu nghiệp vụ gốc

Không chấp thuận việc mặc định toàn bộ Trợ lý AI chỉ có 5 công cụ Read-only, vì điều đó chưa phản ánh đầy đủ mục tiêu MVP.

Theo tài liệu tham chiếu, Agent trên kênh Zalo có 4 công cụ được đề xuất:

| Công cụ | Nghiệp vụ | Ranh giới thực thi |
|---|---|---|
| `tra_xe_theo_vin` | Tra cứu xe theo VIN | VIN/catal​og, không truy xuất thông tin kinh doanh |
| `tim_phu_tung` | Tìm phụ tùng, đối chiếu OE và mã hàng | Backend kiểm tra nguồn, mã và quyền |
| `lap_bao_gia` | Yêu cầu lập báo giá | Quote Service tính giá và quản lý phiên bản, không phải LLM |
| `chuyen_nhan_vien` | Chuyển hội thoại đến nhân viên | Backend điều phối và ghi trạng thái |

Giao diện nhân viên được phép bổ sung công cụ tra cứu nội bộ, nhưng phải chịu RBAC và object-level authorization.

### 1.2. Đối với 5 công cụ hiện có trong Workflow A

Có thể giữ 5 chức năng này làm nền tảng tra cứu trong giao diện nhân viên, với các điều kiện:

- Tra ánh xạ VIN mẫu: chỉ là dữ liệu demo; kết quả thật phải đến từ nguồn VIN/OE được cấp phép.
- Tìm phụ tùng và đọc thông số: hiển thị nguồn catalog/ERP và trạng thái xác minh.
- Tra giá bán ERP: backend thực hiện, giá chỉ hiển thị tại thành phần UI được phân quyền; không đưa giá vào context hoặc tool response gửi cho LLM.
- Tra tồn kho ERP: backend được đọc tồn kho theo quyền; LLM chỉ nhận `con_hang`, `het_hang` hoặc `can_kiem_tra`, không nhận số lượng và tên kho.

Ngoài ra, giao diện phải phản ánh các nghiệp vụ nhận diện ảnh phụ tùng, lập báo giá và chuyển nhân viên khi nằm trong phạm vi thiết kế MVP.

**Quyết định:** 5 chức năng hiện tại có thể giữ cho phần tra cứu của Batch B, nhưng không được sử dụng chúng để kết luận rằng toàn bộ MVP là Read-only Agent.

### 1.3. AI có được đọc giá và tồn kho thay người dùng không?

**Backend được phép thực hiện truy vấn được ủy quyền. LLM không được nhận dữ liệu kinh doanh nhạy cảm.**

Luồng đúng:

1. Người dùng gửi yêu cầu.
2. Backend xác định danh tính, quyền và ngữ cảnh khách hàng.
3. Agent đề nghị gọi công cụ có trong allowlist.
4. Tool Manager kiểm tra quyền và tham số.
5. Backend gọi ERP Connector để truy vấn.
6. AI chỉ nhận dữ liệu kỹ thuật, trạng thái tồn kho được phép và tham chiếu báo giá.
7. Backend hiển thị dữ liệu ERP cho người có quyền hoặc chèn báo giá vào thông điệp gửi khách.

Ví dụ: AI chỉ thấy `{BAO_GIA_1}`, không thấy đơn giá thực tế. Backend thay tham chiếu đó bằng báo giá đã tính.

Không được gửi cho LLM giá vốn, giá bán theo khách, công nợ, danh sách khách hàng hoặc lịch sử mua hàng.

### 1.4. Các thao tác ghi được phép

Cần phân biệt rõ:

**Lập báo giá:** Nằm trong MVP. AI có thể yêu cầu công cụ `lap_bao_gia`, nhưng backend quyết định có đủ điều kiện hay không. Chỉ mã hàng **Đã xác minh** mới được đưa vào báo giá tự động.

**Tạo đơn nháp:** Nằm trong MVP, nhưng chỉ backend được gọi ERP sau khi khách xác nhận đúng báo giá, đúng phiên bản và qua bước kiểm tra lại.

**Duyệt đơn, sửa giá hoặc chiết khấu:** Không cấp cho AI. Do nhân viên có quyền thực hiện theo quy trình ERP.

**Chuyển nhân viên:** Cho phép Agent yêu cầu khi không xác định được mã hàng, yêu cầu vượt quyền, dữ liệu không đáng tin cậy hoặc gặp trường hợp cần xử lý thủ công.

---

## 2. AI1 — Có lưu lịch sử hội thoại không, lưu bao lâu, ai được xem?

**Có lưu lịch sử hội thoại.**

Theo tài liệu gốc:

- Backend AI Agent lưu hội thoại và trạng thái tác vụ.
- Nội dung tin nhắn khách hàng lưu trong CSDL AI Agent, thời gian **12 tháng (đề xuất)**.
- Ảnh giấy đăng kiểm được đề xuất lưu nội bộ 30 ngày.
- Nhật ký AI Gateway phải hạn chế dữ liệu nhạy cảm; không ghi giá hoặc số điện thoại.
- Thời hạn lưu lịch sử hội thoại nội bộ của nhân viên và thời hạn audit log chưa được xác định riêng.

**Quyền xem lịch sử:**

| Vai trò | Phạm vi theo tài liệu |
|---|---|
| Nhân viên kinh doanh | Hội thoại khách hàng được phân công |
| Trưởng bộ phận | Hội thoại của bộ phận |
| Quản trị hệ thống | Không được xem nội dung kinh doanh theo mặc định |
| AI Agent | Chỉ phiên đang xử lý, theo phạm vi cần thiết |

Việc mở quyền truy cập dữ liệu kinh doanh tạm thời cho quản trị hệ thống cần Giám đốc duyệt và phải có audit log.

**Yêu cầu thiết kế:** Có thể thiết kế lịch sử hội thoại theo các quyền trên; không mặc định mọi nhân viên được xem toàn bộ lịch sử. Giá và tồn kho đã lưu trong báo giá cũ phải hiển thị như dữ liệu tại thời điểm lập, không phải giá/tồn hiện tại.

Thời hạn 12 tháng vẫn cần chủ nghiệp vụ phê duyệt thành chính sách lưu trữ chính thức.

---

## 3. AI2 — Nút “Dừng” có hủy truy vấn ở máy chủ không?

**Tài liệu gốc chưa xác định hợp đồng cancellation cho từng yêu cầu Agent. Không được khẳng định nút Dừng đã hủy truy vấn máy chủ.**

Tài liệu chỉ xác định cơ chế ngắt AI khẩn cấp ở cấp hệ thống: khi kích hoạt, trong vòng 1 phút mọi tin mới phải được chuyển sang nhân viên (SEC-06). Cơ chế này khác nút Dừng một phản hồi.

Đề xuất hoàn thiện thiết kế như sau:

- Nút Dừng ngừng tiếp nhận nội dung streaming mới trên UI.
- Nếu backend hỗ trợ hủy tác vụ, frontend gửi cancellation request và hiển thị trạng thái trả về thực tế.
- Truy vấn đã gửi sang ERP có thể đã hoàn tất; Dừng không đồng nghĩa rollback.
- Không được hủy hoặc tạo lại báo giá, đơn nháp chỉ dựa trên trạng thái streaming.
- Nếu trạng thái giao dịch chưa xác định, phải tra cứu/đối soát trước khi cho phép thao tác tiếp.

**Trạng thái AI2:** Chưa chốt cơ chế hủy phía server; cần API contract. UI không được thể hiện một thao tác đã hủy thành công khi chưa có xác nhận.

---

## 4. AI3 — Thao tác nào cần xác nhận? Đề xuất có hết hạn không?

### Xác nhận nghiệp vụ

**Tra cứu VIN, OE, phụ tùng:** Không cần người dùng xác nhận từng truy vấn hợp lệ.

**Nhận diện từ ảnh:** Kết quả là gợi ý AI, không được tự coi là mã hàng đã xác minh để báo giá.

**Thêm vào lựa chọn tạm:** Có thể dùng một thao tác chọn rõ ràng trên UI. Đây không phải hành động tạo báo giá hoặc ghi ERP; tài liệu gốc không quy định phải có hộp thoại xác nhận riêng.

**Lập báo giá:** Backend chỉ thực hiện khi đủ dữ liệu, mã hàng đã xác minh và có chính sách giá hợp lệ. AI không tự tính hoặc sửa giá.

**Tạo đơn nháp:** Bắt buộc có xác nhận rõ ràng từ khách bằng thao tác theo mã và phiên bản báo giá, ví dụ:

`Xác nhận BG-2026-001234 v1`

Câu nói tự do như “ok để anh xem” không được coi là xác nhận tạo đơn.

Trước khi tạo đơn, backend phải:

1. Kiểm tra báo giá còn hiệu lực và đúng phiên bản.
2. Tra lại giá, tồn kho và cờ điều kiện công nợ.
3. Nếu giá thay đổi, lập phiên bản báo giá mới, không tạo đơn.
4. Sử dụng idempotency key theo mã báo giá và phiên bản.
5. Nếu timeout, kiểm tra đơn đã tạo trước khi retry.

**Duyệt đơn:** Do nhân viên thực hiện trong ERP, không giao cho AI.

### Thời hạn hiệu lực

Phải phân biệt:

- **Báo giá:** Có thời hạn. Tài liệu đưa ra ví dụ 3 ngày, nhưng thời hạn thực tế phải do chủ nghiệp vụ chốt.
- **Gợi ý nhận diện AI:** Chưa có thời hạn cố định trong tài liệu. Không tự đặt 5 phút làm quy tắc nghiệp vụ.
- **Lựa chọn tạm trên UI:** Chưa được quy định là giao dịch có thời hạn.

Thiết kế có thể bổ sung trạng thái đề xuất không còn phù hợp khi VIN, sản phẩm, ngữ cảnh hoặc quyền thay đổi; tuy nhiên đây là đề xuất UI, không phải chính sách đã được duyệt.

---

## 5. AI4 — Ngưỡng timeout của dịch vụ AI là bao nhiêu?

**Tài liệu gốc không quy định một ngưỡng timeout AI bằng giây cụ thể.**

Do đó chưa phê duyệt các giá trị 10, 15 hoặc 60 giây.

Cần tách các trường hợp:

- AI model phản hồi chậm hoặc không phản hồi.
- Catalog VIN/OE không phản hồi.
- ERP Connector timeout khi đọc.
- ERP timeout sau khi đã nhận lệnh tạo đơn nháp.

Trường hợp cuối phải xử lý bằng đối soát và idempotency, không tự retry tạo đơn mới.

Trong thiết kế Batch B, cần có trạng thái đang xử lý, quá thời gian, thử lại khi an toàn và chuyển nhân viên khi cần. Không đưa kết quả chưa xác minh vào báo giá tự động.

Ngưỡng timeout chính thức phải được chốt sau khi có hợp đồng API và đo thực tế trên hệ thống demo. SEC-06 yêu cầu ngắt AI khẩn cấp trong 1 phút, **không phải** timeout mặc định cho mỗi cuộc hội thoại.

---

## 6. AI5 — Trợ lý trả lời thế nào về chi nhánh ngoài phạm vi?

**Tài liệu gốc chưa định nghĩa mô hình phân quyền theo chi nhánh.**

Tài liệu có quy định quyền theo khách hàng được phân công, bộ phận và chức năng; đồng thời có nghiệp vụ tồn kho theo kho. Không được tự xem “chi nhánh” và “kho” là cùng một đối tượng phân quyền.

Đối với yêu cầu nằm ngoài phạm vi đã được backend cấp quyền, đề xuất phản hồi:

> Tôi không thể tra cứu dữ liệu này trong phạm vi quyền truy cập hiện tại. Vui lòng liên hệ nhân viên hoặc người quản lý có thẩm quyền để được hỗ trợ.

Backend phải từ chối truy vấn trước khi dữ liệu không được phép tới LLM hoặc UI.

Không hiển thị giá, số lượng tồn kho, công nợ hay thông tin khách hàng ngoài quyền.

Cần chủ nghiệp vụ xác nhận thêm mô hình chi nhánh/kho, phạm vi quyền của từng vai trò và quy trình chuyển yêu cầu giữa các đơn vị. Cho đến lúc đó, AI5 là phương án thiết kế bảo mật đề xuất, chưa phải quy tắc phân quyền chi nhánh đã duyệt.

---

## 7. Các vấn đề còn lại trong thiết kế

### Design system

**ChatMessage:** Phải sử dụng đúng ba nhãn nguồn/trạng thái theo mục 5 tài liệu:

- `AI nhận diện`: Nội dung do AI nhận diện hoặc suy luận.
- `Dữ liệu tra cứu`: Kết quả từ catalog VIN/OE hoặc ERP.
- `Đã xác minh`: Kết quả đã đạt quy tắc đối chiếu được chủ nghiệp vụ duyệt.

Không tự động gắn “Đã xác minh” chỉ vì dữ liệu đến từ ERP. Một tin nhắn có thể chứa nhiều loại nội dung, cần gắn nhãn theo từng phần hoặc từng thẻ.

Dữ liệu Mock ERP phải có dấu hiệu nhận biết môi trường giả lập để tránh hiểu nhầm với dữ liệu ERP thật.

**AIComposer:** Sửa cảnh báo cố định không còn phù hợp với thời điểm xác minh. Thời điểm tra cứu phải lấy từ metadata backend; không sử dụng thời gian tạo tin nhắn AI để khẳng định giá/tồn vừa được kiểm tra.

### Workflow A

Đồng ý sửa lỗi lặp “Mã tham chiếu” ở màn hình lỗi tải danh mục. Đây là lỗi hiển thị, không làm thay đổi nghiệp vụ đã review.

Chỉ cần kiểm tra lại phần bị ảnh hưởng và regression test liên quan; không tự ý sửa các quyết định đã được duyệt của Workflow A.

### Lỗi UI nhỏ

Đồng ý đưa vào vòng chỉnh sửa:

- Breadcrumb bị cắt tại 1024 px.
- Mất vị trí cuộn khi quay lại hội thoại.
- Bảng so sánh quá rộng trong panel Trợ lý.
- Panel Trợ lý chưa hỗ trợ đóng bằng Esc.

### Trạng thái cần bổ sung

| Trạng thái | Hướng xử lý |
|---|---|
| Lỗi công cụ danh mục | Báo lỗi rõ ràng, không bịa mã OE, có thể chuyển nhân viên |
| Phiên đăng nhập hết hạn khi tool chạy | Giữ BLOCKED theo Q7; backend phải kiểm tra lại quyền |
| Lịch sử phiên | Theo AI1 và RBAC |
| Đề xuất hết hạn | Phân biệt thời hạn báo giá với gợi ý AI; chưa tự đặt TTL |
| Kết quả dài/nhiều trang | Phân trang hoặc tải thêm; không tự tổng hợp phần dữ liệu chưa truy xuất |
| Chi nhánh ngoài phạm vi | Theo AI5, không tiết lộ dữ liệu và chưa giả định cấu trúc phân quyền chi nhánh |

---

## 8. Điều kiện để tiếp tục Batch B

Có thể tiếp tục thiết kế theo phạm vi tra cứu đã nêu, nhưng **chưa phê duyệt Batch B** cho tới khi:

1. Phân biệt rõ công cụ Agent được gọi, công cụ backend nội bộ và dữ liệu được phép gửi tới LLM.
2. Không đưa giá bán theo khách, số lượng tồn theo kho hoặc dữ liệu nhạy cảm vào phản hồi LLM.
3. Thiết kế không loại bỏ nghiệp vụ lập báo giá, xác nhận báo giá, tạo đơn nháp và chuyển nhân viên khỏi phạm vi MVP tổng thể.
4. Áp dụng nhất quán ba nhãn `AI nhận diện`, `Dữ liệu tra cứu`, `Đã xác minh`.
5. Hoàn thiện error states, RBAC states và các lỗi design system.
6. Các chính sách chưa được xác nhận như Q7, timeout AI, TTL gợi ý, phạm vi chi nhánh phải giữ trạng thái chờ quyết định.

**Lưu ý:** Tài liệu tham chiếu là bản đề xuất, không phải bằng chứng ERP đã có API hoặc các cơ chế bảo mật đã được triển khai. Phần UI có thể tiếp tục thiết kế dựa trên hợp đồng dự kiến, nhưng chỉ được phê duyệt triển khai thực tế khi các dependency và kiểm thử tương ứng đã được xác nhận.