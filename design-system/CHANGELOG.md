# Changelog — AutoParts Agent Design System

## 2026-11-17 · Nhãn nguồn theo từng phần (phản hồi nghiệp vụ Batch B)
- **ChatMessage:** thêm `label`. Mặc định giữ “Gợi ý, cần kiểm tra” (Workflow A không đổi); truyền `false` để bỏ nhãn chung và gắn nhãn theo từng phần/thẻ.
- **SourceTag:** thêm nguồn `lookup` (“Dữ liệu tra cứu”) và `verified` (“Đã xác minh” — chỉ dùng khi đạt quy tắc đối chiếu đã được duyệt). Nhãn “AI nhận diện” dùng `source="ai"` + `label`.
- **ERPResult:** thêm trạng thái `lookup` (Dữ liệu tra cứu, màu thông tin) và thuộc tính `label`. Dữ liệu từ ERP không còn mặc định được gọi là “Đã xác minh”.
- **AIComposer (thay đổi hiển thị):** câu lưu ý mặc định đổi thành “AI có thể sai. Giá và tồn kho chỉ lấy từ thẻ dữ liệu, kèm thời điểm tra cứu do hệ thống ghi.”; thêm `disclaimer` để thay hoặc ẩn.
- **ToolCall:** thêm trạng thái `pending` (Chưa gửi) và `unknown` (Chưa rõ kết quả — dùng khi người dùng dừng mà máy chủ chưa xác nhận hủy).

## 2026-11-16 · Màn hình đầu tiên: Tra cứu phụ tùng + Trợ lý AI
- **Mới `screens/product-lookup/`:** màn hình 1440px dùng hoàn toàn component của hệ thống. Có 5 biến thể chọn trong bảng Tweaks: Bình thường, Đang tải, Không có kết quả, Lỗi ERP, Không có quyền. Dữ liệu sản phẩm là hư cấu.
- **ToolCall (sửa bố cục):** tên công cụ và thời gian chạy chuyển xuống dòng thứ hai; trạng thái luôn nằm cùng hàng với nhãn và không xuống dòng. Lý do: trong khung AI 400px, bố cục một hàng cũ bị vỡ.

## 2026-11-16 · Hướng dẫn viết giao diện & quy chuẩn icon

### Viết giao diện
- **Mới `guidelines/writing.md`:** nguyên tắc (tiếng Việt, ngắn gọn, hướng hành động, không marketing), bảng phân biệt 7 loại thông tin, vòng đời một thao tác (không báo thành công trước khi backend xác nhận; quá thời gian ≠ thất bại), và ví dụ nên/không nên cho nhãn nút, tiêu đề cột, toast, trạng thái trống, lỗi, Trợ lý AI.
- **Quy tắc bảo mật nội dung:** thông báo lỗi không lộ stack trace, mã HTTP, endpoint, SQL, token, tên vai trò nội bộ, dữ liệu ngoài quyền; chỉ hiện mã tham chiếu.
- **README:** bổ sung các quy tắc trên vào CONTENT FUNDAMENTALS.
- **6 thẻ mới (nhóm Writing):** Button labels, Table headings, System notifications, Empty states, Error messages, Information types. Tất cả dùng component thật của hệ thống.

### Icon (thay đổi hiển thị)
- **`Icon` mặc định:** size 16 → **20**, stroke 1.75 → **2**.
- **Token mới:** `--icon-size-md` (20), `--icon-size-sm` (16), `--icon-size-inline` (14), `--icon-stroke` (2), `--icon-gap` (8), `--icon-gap-compact` (6).
- **Chuẩn hóa kích thước về thang 20 / 16 / 14** (trước đây có 11, 12, 13, 15, 18, 22):
  - **20:** Sidebar, icon đầu dòng của Alert / Toast / Dialog, EmptyState, IconButton md/lg, Button lg.
  - **16:** Button sm/md, IconButton sm, Tabs, Tag, Breadcrumb, icon cuối trong Input / Select / SearchField, AppHeader search, AIComposer, AIPanel, avatar trong ChatMessage.
  - **14:** Badge, SourceTag, ERPResult, ToolCall, ActionConfirmation, MetricCard delta, icon sắp xếp trong DataTable, dòng thông báo dưới Field.
- **README ICONOGRAPHY:** quy định kích thước, nét, căn chỉnh, khoảng cách, khả năng truy cập.
- **Thẻ mới:** "Icon sizes & alignment" (Brand).

### Cách kiểm tra
1. Tab Design System → nhóm **Writing**: 6 thẻ ví dụ hiển thị đủ, cột "Không nên" gạch ngang đỏ.
2. Brand → **Icon sizes & alignment**: ba hàng icon 20 / 16 / 14, nét đều 2px.
3. Components → Core, Navigation, Feedback: icon trong nút, sidebar, toast, alert lớn hơn và nét đậm hơn so với trước; không có icon bị lệch dòng.
4. `ui_kits/app/index.html` vẫn chạy (các icon không truyền `size` giờ hiển thị 20px).

## 2026-11-16 · Kết hợp 3 nguồn tham khảo Refero (ClickUp, Dub, Seline)
Mọi thay đổi chỉ bổ sung hoặc tinh chỉnh. Không token hay component nào bị xóa. Font, tiếng Việt, Lucide, các quy tắc truy cập, nguồn dữ liệu, phân quyền và trạng thái lỗi giữ nguyên.

### Token
- **Thêm `--shadow-card`** (`0 4px 16px rgba(16,24,40,.05)`): bóng nhẹ cho thẻ KPI và thẻ biểu đồ. *Nguồn:* Seline.
- **Thêm `--shadow-ring`** (`0 0 0 4px rgba(16,24,40,.06)`): cho popover và khung preview nổi. *Nguồn:* Dub.
- **Thêm `--bg-nested`** (#FAFAFB): nền thẻ lồng bên trong, không viền. *Nguồn:* Dub.
- **Thêm `--space-card`** (20) **và `--space-card-lg`** (24): đệm thẻ thường và thẻ dashboard. *Nguồn:* Seline.
- **Thêm `--chart-*`** (primary, primary-soft, neutral, neutral-strong, grid, axis): biểu đồ dùng một màu nhấn và các sắc xám. *Tự đề xuất*, vì nguồn không có đặc tả biểu đồ.

### Component đã thay đổi
- **Badge:**
  - Thêm prop `shape`. `pill` có chấm màu = trạng thái bản ghi; `rounded` 6px = phân loại hoặc nguồn dữ liệu.
  - Thêm tông `restricted`.
  - Mặc định vẫn là `rounded` → không ảnh hưởng chỗ đang dùng.
  - *Nguồn:* ClickUp, Dub.
- **Card:** thêm prop `variant`.
  - `default`: giữ nguyên như cũ.
  - `elevated`: `--shadow-card`, đệm 24.
  - `nested`: nền `--bg-nested`, không viền.
  - *Nguồn:* Dub, Seline.
- **MetricCard:** đệm 20 → 24, bóng `shadow-xs` → `--shadow-card`. *Nguồn:* Seline.
- **DataTable:** tiêu đề cột đổi từ nền xám sang nền trắng, chỉ giữ viền dưới. Thứ tự focus, sắp xếp, chọn dòng và bàn phím không đổi. *Nguồn:* Dub.
- **Sidebar:** mục đang chọn đổi từ "thẻ trắng nổi" sang nền xanh nhạt `--bg-selected`. Vòng focus vẫn giữ. *Nguồn:* Dub.
- **Tabs:** thêm `variant="pill"` (tab đang chọn nền `--gray-900`, tương phản 16:1) để đổi kỳ báo cáo hoặc chế độ xem biểu đồ. Điều hướng ←/→/Home/End giữ nguyên. *Nguồn:* Seline.

### Component mới (`components/dashboard/`)
- **ChartCard:** gồm tiêu đề, con số chính, tab đổi kỳ, vùng biểu đồ, chú thích, nguồn dữ liệu (`SourceTag`).
  - Trạng thái có sẵn: ready / loading / empty / error / restricted.
  - Bắt buộc có `summary` (tóm tắt bằng chữ cho trình đọc màn hình).
- **AvatarGroup:** cụm chữ cái viết tắt xếp chồng cho người phụ trách hoặc người duyệt.
  - Hiển thị "+N" khi vượt số lượng; tên đầy đủ đọc được qua `aria-label`.
  - Chỉ dùng màu xanh và xám, không lấy màu trạng thái hay màu nguồn dữ liệu.
  - Avatar sau nằm trên avatar trước, ô "+N" luôn ở trên cùng. Kích thước 28px (cỡ nhỏ) / 36px (cỡ thường), độ chồng 1px / 4px. Độ chồng cộng viền trắng 2px luôn nhỏ hơn khoảng trống hai bên chữ viết tắt.

### Sửa lỗi
- **AvatarGroup:** chữ viết tắt bị che ("QB" chỉ còn thấy "B") và dấu "+" của ô "+N" bị khuất. Nguyên nhân: avatar đầu nằm trên cùng và độ chồng 8px. Lần 1: đảo thứ tự xếp chồng. Lần 2: tăng kích thước vòng tròn (24→28, 32→36) và giảm độ chồng, vì hai chữ viết tắt 12–13px rộng gần hết vòng 24px.
  - *Nguồn:* ClickUp.

### Tài liệu và thẻ
- **README:** thêm 3 link tham khảo kèm phạm vi đã áp dụng và đã loại bỏ; quy tắc "viền trước, tối đa 2 tầng nổi"; "một nút chính cho mỗi khu vực"; quy ước hình dạng badge; bố cục dashboard; quy tắc màu biểu đồ.
- **Thẻ mới:** "Chart colors" (Colors), "Dashboard layout" (Spacing), "Dashboard & workflow" (Components).
- **Thẻ cập nhật:** Elevation (thêm shadow-card, shadow-ring, bg-nested), Data display (badge dạng pill), Navigation (tab dạng pill).

### Cách kiểm tra
1. Mở **tab Design System**:
   - Colors → "Chart colors".
   - Spacing → "Elevation" và "Dashboard layout".
   - Components → "Dashboard & workflow": bấm các nút trạng thái (Có dữ liệu / Đang tải / Trống / Lỗi ERP / Không có quyền) và tab "7 ngày / 30 ngày".
2. **Bàn phím:**
   - Trong "Dashboard & workflow", dùng Tab đến nhóm tab dạng pill, rồi dùng ←/→ để đổi kỳ.
   - Trong "Navigation", mục sidebar đang chọn phải có nền xanh nhạt, và vẫn hiện vòng focus khi Tab tới.
3. **Bảng:** trong "Data display", tiêu đề cột nền trắng, có viền dưới, nút sắp xếp vẫn dùng được bằng Enter.
4. **Không bị hỏng:** UI kit `ui_kits/app/index.html` vẫn chạy. Các Badge cũ vẫn bo 6px vì mặc định là `rounded`.
