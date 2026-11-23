# Batch F · Đặc tả màn hình còn thiếu (UX-13)

Cập nhật 23/11/2026. Thiết kế trên canvas Claude Design "AutoParts – Màn hình UX-13" (private, chủ dự án), dùng AutoParts Agent Design System (bản `1795426540-5cc3`). Bản sao trong repo: `docs/design/prototypes/F*.dc.html`.

**Trạng thái:** có trên canvas; chưa chạy thử trong trình duyệt; chưa phê duyệt. Đã qua một vòng review `ux-workflow-reviewer` (advisory, chỉ đọc mã). Là DESIGN PROPOSAL: không thay ADR, `docs/contracts/` hay `docs/security/RBAC_MATRIX.md`.

## Phạm vi và căn cứ

- Bốn nhóm màn hình UX-13 còn thiếu:
  - Chat khách (UC-01, UC-03/04 phía khách, UC-05 phía khách);
  - Hộp chờ và hội thoại của nhân viên (UC-05, UC-06);
  - Security Inspector (UC-07);
  - Công tắc AI (UC-08).
- Mỗi màn hình chỉ hiển thị trường mà API hiện có trả về:
  - `TurnView` / `QuoteCardView` và `GET …/messages` (conversation);
  - `HandoffView` (handoff);
  - `Trace` / `EventView` (securityinspector);
  - `{aiEnabled, reason, since}` (operations).
- Quyền theo RBAC_MATRIX:
  - CUST chỉ thấy hội thoại và báo giá của mình;
  - SALES chỉ thấy nhóm mình, chỉ đọc nội dung khi đang giữ handoff;
  - SECREV chỉ dùng Inspector;
  - OPS chỉ dùng công tắc.
- Không có vai trò quản trị đặc biệt (UX-11).
- Kênh khách là trình mô phỏng chat cục bộ, không phải Zalo (D-06, UX-14).
- Dữ liệu mô phỏng. Không phải bằng chứng phân quyền, audit hay egress đã hoạt động.

## F1 · CH-01 · Chat khách hàng và thẻ báo giá

Artboard: `F1-Chat.dc.html` (390×844) · Đã thiết kế

| | |
|---|---|
| Điểm vào | Khách đăng nhập kênh mô phỏng; tạo hoặc mở lại cuộc trò chuyện (`POST /api/demo/conversations`). |
| Thao tác người dùng | Gõ tin (≤ 2.000 ký tự) rồi gửi; mỗi lần gửi kèm `clientEventId` để không xử lý trùng (AC-01.2). Bấm "Xác nhận báo giá" trên thẻ. |
| Dữ liệu hiển thị | Tin của khách và trợ lý AI. Thẻ báo giá do backend dựng (AC-03.2): mã BG, phiên bản, hạn hiệu lực, dòng (SKU, số lượng, đơn giá, thành tiền), Tạm tính, ghi chú. Không hiện tổng thanh toán hay VAT (UX-09). |
| Chuyển màn hình | Bị chuyển nhân viên (`handedOff=true`) → F1-Handoff. Xác nhận → F1-QuoteStates (đang kiểm tra / thành công / lỗi). |
| Phụ thuộc quyền | Chỉ hội thoại của khách; báo giá của khách khác → "Không tìm thấy" (UX-16). Xác nhận gắn `quoteRef` + `version` + `confirmationToken`; chữ "ok" trong chat không tạo đơn (AC-04.5, UX-05). |
| Đang tải & lỗi | Đang gửi: nút khóa, "Đang gửi…". Lượt đang chạy (409) hoặc quá giới hạn (429): báo "Vui lòng chờ" và không mất nội dung gõ. Lỗi dịch vụ: thông điệp an toàn và chuyển nhân viên (AC-01.3). |
| Khôi phục | Thử gửi lại với cùng `clientEventId`. |
| Thành phần dùng lại | ChatMessage, AIComposer, Badge, Button |

## F1 · CH-02 · Chờ nhân viên tư vấn

Artboard: `F1-Handoff.dc.html` (390×844) · Đã thiết kế

| | |
|---|---|
| Điểm vào | Lượt trả về `handedOff=true`, hoặc khách mở lại cuộc trò chuyện đang chờ. |
| Thao tác người dùng | Vẫn nhắn được; tin được lưu cho nhân viên. |
| Dữ liệu hiển thị | Banner "Đang chờ nhân viên tư vấn". Các câu cố định của backend (`ChatTexts`) hiện như tin hệ thống. Tin nhân viên có nhãn "Nhân viên tư vấn". **Không bao giờ hiện lý do chuyển** (A-14). |
| Chuyển màn hình | Nhân viên hoàn tất → trở lại CH-01, trợ lý AI trả lời tiếp. |
| Phụ thuộc quyền | Chỉ khách sở hữu hội thoại. |
| Đang tải & lỗi | Như CH-01. |
| Khôi phục | Như CH-01. |
| Thành phần dùng lại | Alert, ChatMessage, AIComposer, Badge |

## F1 · CH-03 · Trạng thái báo giá phía khách

Artboard: `F1-QuoteStates.dc.html` (390×1180) · Đã thiết kế

| | |
|---|---|
| Điểm vào | Phản hồi của `POST /api/demo/quotes/{quoteRef}/confirm`. |
| Thao tác người dùng | Không có thao tác ghi mới trong các trạng thái này. |
| Dữ liệu hiển thị | Bốn trạng thái, chỉ dùng các trường `ConfirmationView` (`quoteRef`, `version`, `status`, `handedOff`) và `QuoteView`: **hết hiệu lực** (410 `QUOTE_EXPIRED`: nút khóa, nhắn để lấy báo giá mới); **đã thay đổi** (409 `QUOTE_STALE_VERSION` / `QUOTE_REPRICED`: tải `GET /api/demo/quotes/{quoteRef}` để có phiên bản mới và `confirmationToken` mới, nút "Xác nhận phiên bản N"); **đang gửi, chưa rõ kết quả** (202 `ORDER_SUBMITTING` / `ORDER_OUTCOME_UNKNOWN`: mã tham chiếu = mã BG + phiên bản, không có nút gửi lại, UX-08/UX-18); **chuyển nhân viên khi xác nhận** (`handedOff=true`, ví dụ `CONFIRMATION_BLOCKED` hoặc `ORDER_FAILED`: chỉ báo nhân viên sẽ hỗ trợ). Không nêu lý do (A-14). |
| Chuyển màn hình | Về CH-01. |
| Phụ thuộc quyền | Như CH-01. |
| Đang tải & lỗi | "Chưa nhận được kết quả" là trạng thái chờ, không phải lỗi. |
| Khôi phục | Nhân viên đối soát thủ công (UC-04, A-41). |
| Thành phần dùng lại | Alert, Badge, Button |

## F2 · HO-01 · Hộp chờ nhân viên

Artboard: `F2-Inbox.dc.html` (1440×900, trang co giãn) · Đã thiết kế

| | |
|---|---|
| Điểm vào | Sidebar "Hộp chờ nhân viên" (SALES). |
| Thao tác người dùng | "Nhận xử lý" (claim); "Mở" khi đang giữ; "Tải lại". |
| Dữ liệu hiển thị | Theo `HandoffView`: mã cuộc trò chuyện rút gọn, lý do chuyển (nhãn tiếng Việt, UX-17), lần mở tiếp theo nếu có (`followUpReasonCode`), trạng thái (Chờ nhận / Bạn đang xử lý / Nhân viên khác đang xử lý), thời điểm mở, cũ nhất ở trên. **Không có tên khách hay nội dung tin nhắn.** |
| Chuyển màn hình | Mở → F3. |
| Phụ thuộc quyền | Chỉ nhóm của nhân viên; dòng do người khác giữ bị khóa (409 khi cố nhận). Mỗi lần đọc danh sách được audit (`STAFF_LIST_READ`). |
| Đang tải & lỗi | Rỗng: EmptyState "Chưa có yêu cầu nào". Bị người khác nhận trước: Toast và tải lại. |
| Khôi phục | Tải lại. |
| Thành phần dùng lại | Sidebar, AppHeader, Alert, Badge, Button, EmptyState |

## F3 · HO-02 · Hội thoại đang xử lý

Artboard: `F3-Conversation.dc.html` (1440×900, trang co giãn) · Đã thiết kế

| | |
|---|---|
| Điểm vào | "Mở" từ F2, chỉ khi đang giữ handoff. |
| Thao tác người dùng | Gửi tin cho khách (≤ 2.000 ký tự); "Hoàn tất và mở lại AI" (resolve); về hộp chờ. |
| Dữ liệu hiển thị | Tin theo `direction` của API (`CUSTOMER` → "Khách hàng", `BOT` → "Trợ lý AI", `STAFF` → "Nhân viên tư vấn"; API không cho biết nhân viên nào viết); tin bị chặn hiện "[tin nhắn đã bị chặn]". Báo giá của hội thoại (`StaffQuoteView`), chỉ xem, không có token xác nhận; nhãn trạng thái theo bảng ánh xạ bên dưới. |
| Chuyển màn hình | Hoàn tất → F2. Nếu có việc tiếp theo, hệ thống mở yêu cầu mới và AI vẫn tạm dừng. |
| Phụ thuộc quyền | Chỉ người giữ, chỉ khi HUMAN_ACTIVE; mất quyền ngay khi RESOLVED (A-15). Mỗi lần đọc tin nhắn và báo giá được audit. |
| Đang tải & lỗi | Mất quyền giữa chừng → "Không tìm thấy" (404). Khóa đang bận → 409 "Vui lòng thử lại". Không ghi được audit báo giá → 503, không hiện báo giá. |
| Khôi phục | Về F2. |
| Thành phần dùng lại | Sidebar, AppHeader, ChatMessage, Textarea, Badge, Alert, Button |

## F4 · SI-01 · Security Inspector

Artboard: `F4-Inspector.dc.html` (1440×980, trang co giãn) · Đã thiết kế

| | |
|---|---|
| Điểm vào | Sidebar "Sự kiện bảo mật" (SECREV). |
| Thao tác người dùng | Lọc theo loại sự kiện, vai trò và khoảng thời gian (≤ 7 ngày); "Tải thêm" (cursor); bấm mã trace để xem chi tiết. |
| Dữ liệu hiển thị | Sự kiện (`EventView`): thời điểm, hành động, tài nguyên, kết quả, vai trò + bí danh nhân viên, mã trace, số lần gộp. Trace: kiểm tra egress (quyết định, lần gửi, mã chính sách, mã honeytoken, đường dẫn trường bị chặn, tên trường, số byte) và gọi công cụ (tên, quyết định, mã lý do). **Không có nội dung tin nhắn, giá hay dữ liệu cá nhân**; mọi giá trị trên artboard là mã mẫu. |
| Chuyển màn hình | Trong trang. |
| Phụ thuộc quyền | Chỉ SECREV; vai trò khác 403. Mỗi lần xem được audit (`INSPECTOR_READ`). |
| Đang tải & lỗi | Trace không có bản ghi → "Không tìm thấy trace". Bộ lọc sai → lỗi kiểm tra tại trường. |
| Khôi phục | Sửa bộ lọc. |
| Thành phần dùng lại | Sidebar, AppHeader, Select, Input, Button, Badge, DescriptionList, ToolCall |

## F5 · OP-01 · Công tắc trợ lý AI

Artboard: `F5-KillSwitch.dc.html` (1440×900, trang co giãn) · Đã thiết kế

| | |
|---|---|
| Điểm vào | Sidebar "Công tắc AI" (OPS). |
| Thao tác người dùng | Gạt công tắc; tắt cần xác nhận ("Tắt AI", tone danger); bật không cần. |
| Dữ liệu hiển thị | Bật/tắt, lý do (người vận hành / nhà cung cấp AI từ chối khóa / không đọc được trạng thái), thời điểm. |
| Chuyển màn hình | Trong trang. |
| Phụ thuộc quyền | Chỉ OPS; không hiển thị dữ liệu kinh doanh. Thay đổi được audit (`KILL_SWITCH_CHANGED`). |
| Đang tải & lỗi | Không đọc được trạng thái → coi là tắt, hiện rõ. Mốc "tối đa 30 giây" cho máy chủ khác lấy từ thiết kế cache của `KillSwitch` (A-44, ACC-01 SEC-06: chứng minh bằng đồng hồ test, chưa đo thời gian thật). Hộp thoại trên artboard là mẫu tĩnh; sản phẩm dùng `Dialog` tone danger (role alertdialog, giữ focus). |
| Khôi phục | Tải lại; bật lại bằng công tắc (cũng đóng cầu dao xác thực nhà cung cấp). |
| Thành phần dùng lại | Sidebar, AppHeader, Switch, Badge, DescriptionList, Alert, Button, Icon |

## Trạng thái quote: ánh xạ nhãn (đề xuất, UX-06)

| `status` | Khách (F1) | Nhân viên (F3) |
|---|---|---|
| ISSUED | Chờ xác nhận | Đã gửi khách |
| ORDER_SUBMITTING, ORDER_OUTCOME_UNKNOWN | Đang kiểm tra | Chưa rõ kết quả |
| CONFIRMATION_BLOCKED, ORDER_FAILED | Chờ nhân viên | Cần nhân viên xử lý |
| DRAFT_ORDER_CREATED | Đã gửi yêu cầu đặt hàng | Đã tạo đơn nháp |
| STAFF_APPROVED | Đã được duyệt | Đã duyệt |
| REJECTED | Không được duyệt | Bị từ chối |
| EXPIRED | Hết hiệu lực | Hết hiệu lực |
| SUPERSEDED | Đã có phiên bản mới | Đã có phiên bản mới |
| CANCELLED | Đã hủy | Đã hủy |
| DRAFT, CONFIRMED | không hiện cho khách (trạng thái nội bộ, chuyển tiếp ngay) | Đang xử lý |

## Trạng thái mô tả nhưng chưa vẽ

Các trạng thái sau có trong đặc tả nhưng chưa có artboard riêng; vẽ trước khi code EP-10:
- đang tải (Skeleton) cho F2–F5;
- danh sách rỗng F2;
- 403 / 404 cho F2–F5;
- quá thời gian khi gửi tin (F1, F3);
- 409 khi nhận xử lý hoặc khi khóa đang bận (F2, F3);
- "Không tìm thấy trace" và cờ `truncated` (F4);
- lỗi khi gạt công tắc (F5).

## Khoảng trống so với backend (chưa giải quyết)

1. **Thời gian tin nhắn:** `GET …/messages` không trả thời điểm, nên giờ trên artboard F1/F3 chỉ để minh họa. Muốn hiện giờ phải mở rộng contract.
2. **Nguồn gốc giá:** `QuoteCardView` không có nguồn hay thời điểm đồng bộ, nên thẻ ghi "Giá do hệ thống lập" thay vì "Đã xác minh từ ERP" (writing rule: không ghi "đã xác minh" khi không có mốc thời gian).
3. **Trạng thái hội thoại phía khách:** API khách không trả `ACTIVE_BOT` / `HANDOFF_PENDING` / `HUMAN_ACTIVE`; giao diện chỉ suy ra từ `handedOff` của lượt gần nhất. Khi mở lại một hội thoại đang chờ, banner CH-02 chưa có nguồn dữ liệu.
4. **Nhãn lý do chuyển nhân viên:** bảng ánh xạ tiếng Việt dưới đây là đề xuất (UX-17), cần duyệt. Bảng phủ toàn bộ `HandoffReason` (16 giá trị trong `HandoffView.reasonCode`); model chỉ được yêu cầu 3 trong số đó qua `requestHandoff`.
5. **Mã tham chiếu cho xác nhận:** `ConfirmationView` không có mã yêu cầu hay thời điểm gửi; giao diện dùng mã BG + phiên bản.

| Mã | Nhãn đề xuất |
|---|---|
| DLP_INBOUND | Tin nhắn chứa dữ liệu nhạy cảm |
| DLP_EGRESS | Chặn dữ liệu gửi ra |
| PROMPT_INJECTION | Nghi chèn lệnh |
| TOOL_REJECTED_REPEATED | Yêu cầu công cụ bị từ chối |
| PROVIDER_ERROR | Dịch vụ AI gián đoạn |
| PROVIDER_AUTH | Nhà cung cấp AI từ chối khóa |
| CATALOG_UNAVAILABLE | Danh mục gián đoạn |
| QUOTE_NOT_ISSUED | Không lập được báo giá |
| CUSTOMER_REQUEST | Khách yêu cầu nhân viên |
| UNSURE | Trợ lý AI không chắc chắn |
| OUT_OF_SCOPE | Ngoài phạm vi hỗ trợ |
| OUTPUT_REJECTED | Câu trả lời bị chặn |
| AGENT_LIMIT | Vượt giới hạn lượt xử lý |
| ERP_UNKNOWN | Chưa rõ kết quả ERP |
| ORDER_NOT_CREATED | Chưa tạo được đơn |
| AI_DISABLED | Trợ lý AI đang tắt |
