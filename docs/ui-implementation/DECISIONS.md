# Quyết định triển khai Frontend (FE-UI)

> Sổ quyết định chi tiết cho `docs/ui-implementation/`. Mục tổng trong sổ đăng ký chung: `docs/DECISIONS_REQUIRED.md` **A-80**. Không quyết định nào ở đây thay đổi ADR; khi phát hiện xung đột với ADR, contract hoặc RBAC_MATRIX thì báo chủ dự án trước, không ghi đè.

## Bằng chứng phê duyệt
| Lượt | Ngày | Lời chủ dự án (nguyên văn rút gọn) | Phạm vi |
|---|---|---|---|
| 1 | 2026-11-26 | "Tôi phê duyệt các lựa chọn sau: **1A 2B 3A 4A 5A 6A 7A 8A**" kèm phần "Decision clarification" D01–D08 | FE-UI-D01..D08 |
| 2 | 2026-11-26 | "theo Phương án A đề xuất hết cho tôi" (trả lời 4 câu hỏi preflight) | FE-UI-D09, D10, D12, D13 |
| 3 | 2026-11-26 | "theo đề xuất của bạn" (sau quality gate P1) | FE-UI-Q01 = A, commit P1, W-BIZ nhóm 0 (A-81) |
| 4 | 2026-11-26 | "UX-09 chọn (a), bắt đầu P2 đi, tôi đã merge phần vừa rồi" (PR #65 merged) | UX-09 = (a); bắt đầu P2 (A-84) |

## Quyết định
| ID | Quyết định | Trạng thái | Hệ quả / ràng buộc |
|---|---|---|---|
| FE-UI-D01 | Hash route (`/#/…`) | APPROVED 2026-11-26 (1A) | Không đổi `FrontendPaths`/`SecurityConfiguration` (A-74). Deep link qua hash |
| FE-UI-D02 | `react-router-dom` với `HashRouter`, không tự viết router | APPROVED 2026-11-26 (2B) | Cài ở **P2**, không phải P1. Preflight 2026-11-26: React 18.3.1 + peer `react >=18`; đề xuất ghim **7.18.5** (phát hành 2026-10-27, > 2 tuần), phụ thuộc kéo theo `react-router` 7.18.5, `cookie`, `set-cookie-parser`; `HashRouter` không sinh inline script (CSP A-73 giữ nguyên). Khi cài: `npm audit`, cập nhật lockfile, báo blocker trước khi đổi |
| FE-UI-D03 | CSS + design token cho responsive | APPROVED 2026-11-26 (3A) | `src/app/foundation.css`, chỉ `var(--…)`; test chặn giá trị cứng |
| FE-UI-D04 | Không đưa ID hội thoại/đơn hàng vào URL trong phạm vi hiện tại | APPROVED 2026-11-26 (4A) | Kiểm tra quyền theo đối tượng ở backend vẫn bắt buộc |
| FE-UI-D05 | Mock chỉ trong `src/mocks/`, chỉ dev/test, chỉ theo API contract có thật | APPROVED 2026-11-26 (5A) | Phải có test chứng minh mock không nằm trong bản build production (P2-T10) |
| FE-UI-D06 | Thiết kế batch G cho EP-10e trên Claude Design theo contract thật | APPROVED 2026-11-26 (6A) | Chủ dự án duyệt thiết kế trước khi code màn hình |
| FE-UI-D07 | Batch A–E BLOCKED theo từng nhóm nghiệp vụ tới khi UX-xx, UC, quyền và contract được duyệt | APPROVED 2026-11-26 (7A) | Mở khóa từng nhóm độc lập: `CONFLICT_RESOLUTION_MATRIX.md` |
| FE-UI-D08 | Breakpoint lấy từ thiết kế (390 · 1024 · 1280 · 1440) | APPROVED 2026-11-26 (8A) | Giao diện nhân viên dưới 1024 px vẫn là PROPOSED, không coi là đã duyệt |
| FE-UI-D09 | Cách ly Git: worktree `D:/Project_Private/AI-Agent-fe`, nhánh `feat/FE-UI-P1-design-system` từ `origin/main` `c4edc3b` | APPROVED 2026-11-26 (1A) | Không đụng thư mục chính (phiên backend); bỏ upstream để nhánh không trỏ vào `main` |
| FE-UI-D10 | Chép `Checkbox` như dependency bắt buộc của `DataTable`, có review + hash, không export | APPROVED 2026-11-26 (2A) | Export khi một page cần dùng riêng |
| FE-UI-D11 | `Tooltip` là bản sao **đã sửa** (giao diện giữ nguyên): mô tả gắn vào control được focus (4.1.2), Esc đóng ở mọi nơi, di chuột vào được tooltip — trễ đóng 120 ms, bật pointer events (1.4.13) | DECIDED trong phạm vi P1 (yêu cầu WCAG 2.2 AA của lượt 1); chủ dự án có thể bác | `sources.json` `modified` + `copySha256`; test đỏ với bản gốc (2 test) |
| FE-UI-D14 | `DataTable` là bản sao **đã sửa** một dòng (giao diện giữ nguyên): Enter/Space của dòng chỉ tác dụng khi chính dòng được focus, không khi nhấn trên checkbox/nút bên trong (2.1.1, 4.1.2) | DECIDED trong phạm vi P1 sau review UX H2; chủ dự án có thể bác | `sources.json` `modified` + `copySha256`; test đỏ khi bỏ dòng chặn |
| FE-UI-D15 | Chữ cho trang "không tìm thấy" và lỗi hiển thị (SH-04): "Không tìm thấy trang" / "Đường dẫn không đúng hoặc trang không còn. Quay về trang chính để tiếp tục."; "Không hiển thị được trang này" / "Không có thay đổi nào được thực hiện. Tải lại trang; nếu vẫn lỗi, liên hệ bộ phận IT." | PROPOSED (không artboard nào ghi chữ này; theo writing.md và E6) | Trang "không có quyền" dùng nguyên chữ F6 (403) đã duyệt |
| FE-UI-D16 | Toast chỉ nhận mục trong `TOAST_TEXT` (chữ artboard) hoặc lỗi → `failureText` + mã `REQ-`; không tự đóng; tối đa 3 | DECIDED trong P2 | F6: 409 là toast cảnh báo. Tự đóng sẽ thêm khi có toast thành công đầu tiên (kèm dừng khi hover/focus) |
| FE-UI-D17 | Focus: sau khi đổi route → `h1` của trang (hoặc `main`); lúc khung vừa hiện, chỉ khi focus đang ở `body` (sau đăng nhập nút đã mất) | DECIDED trong P2 (WCAG 2.4.3) | Skip link chuyển focus bằng JS vì `#id` sẽ bị hash router hiểu là đổi trang |
| FE-UI-D18 | Khung theo artboard F2–F5: nhóm sidebar = nhóm breadcrumb (Kinh doanh / Vận hành / Kiểm tra bảo mật); icon OPS `sliders-horizontal` (F5, trước là `shield-alert`); header chỉ hiện nhãn vai trò (API phiên không có tên người); mục EP-10e của F2 chưa hiện tới P4 | DECIDED trong P2 | Lệch có lý do, ghi trong bằng chứng P2 |
| FE-UI-D19 | Mock bật bằng Vite mode `mock` (`npm run dev:mock`, cổng 5174), không bằng biến môi trường (cmd của Windows không nhận `VAR=1`; `.env.*` bị gitignore); người dùng mock `ops`/`sales`/`secrev`/`cust`, mật khẩu `mock`; `npm run build` chạy `verify-no-mocks` | DECIDED trong P2 (thực thi D05) | Mock chỉ trả các endpoint có thật, mỗi câu trả lời đi qua decoder của app trong test |
| FE-UI-D20 | `react-router-dom` **7.18.5** ghim chính xác (kéo `react-router` 7.18.5, `cookie` 1.1.1, `set-cookie-parser` 2.7.3; MIT; integrity; > 2 tuần) | DONE trong P2 (thực thi D02) | `npm audit` 0; bundle JS 276 → 320 kB (gzip 81 → 96 kB) |
| FE-UI-D12 | Không tạo `CURRENT_STATE`; hiện trạng ở `docs/SESSION_HANDOFF.md` + `PROGRESS.md` | APPROVED 2026-11-26 (3A) | |
| FE-UI-D13 | E2E hồi quy ở quality gate chỉ khi có `APAS_DEMO_PASSWORD` trong môi trường và được dựng PostgreSQL (`apas-e2e`) | APPROVED 2026-11-26 (4A) | P1: biến chưa có → NOT RUN, ghi lý do |

## Câu hỏi mở cho chủ dự án
| ID | Câu hỏi | Bằng chứng | Đề xuất | Tạm thời |
|---|---|---|---|---|
| FE-UI-Q01 | Vòng focus của design system (`--focus-ring: 0 0 0 3px rgba(37,99,235,.22)`, `tokens.css`) có độ tương phản khoảng 1,3:1 trên nền trắng, dưới mức 3:1 thường áp cho chỉ báo focus (WCAG 1.4.11 / 2.4.13). Mọi component (Button, Tabs, Tag, Checkbox, Input…) dùng nó kèm `outline: none` | review `ux-workflow-reviewer` P1 (H1); chưa đo bằng công cụ trên trình duyệt | ⭐ A: đưa sang Claude Design đổi token (ví dụ viền `--border-focus` đặc), rồi chép lại; B: giữ token, chấp nhận rủi ro a11y | **APPROVED A 2026-11-26 (A-81)**: brief `design-briefs/FE-UI-Q01-focus-ring.md` gửi Claude Design; frontend chờ bản xuất mới. Tới lúc đó giữ vá tạm trong `foundation.css` |

## Quy tắc dùng component rút ra khi review P1 (ràng buộc cho P2+)
- **Toast** chỉ hiển thị chuỗi được truyền vào; P2 `useToast` nhận thông điệp cố định từ `app/texts.ts` + `REQ-…`, không bao giờ nhận `error.message`, thân phản hồi hay dữ liệu nghiệp vụ. Toast lỗi không tự đóng.
- **Tag**: không dùng `onClick` và `onRemove` cùng lúc (control lồng nhau).
- **Card**: tiêu đề luôn là `h3`; đặt trong vùng có `h2` hoặc dùng `PageHeader` (`h1`) + tiêu đề vùng.
- **DataTable**: không có trạng thái lỗi — page hiện lỗi bên ngoài bảng; luôn truyền `emptyState` theo writing rule ("Chưa có …"); sắp xếp nội bộ chỉ áp lên các dòng đã có, không ngụ ý sắp xếp/lọc/phân trang phía server; muốn sắp xếp theo server thì phải có contract và truyền `onSortChange`.
- **Tabs**: không tạo `tabpanel`; page tự render `role="tabpanel"` có nhãn; `value` phải trùng một tab bật, nếu không danh sách không có phần tử nào nhận Tab.
- **Tooltip**: chỉ cho thông tin phụ; control phải có tên riêng (`aria-label`) không phụ thuộc tooltip; con phải focus được (nút `disabled` không phát sự kiện) và chuyển `aria-describedby` xuống phần tử thật. Việc trình đọc màn hình đọc mô tả thêm vào lúc focus chưa kiểm bằng NVDA (NOT RUN).
- **DataTable (bổ sung)**: vùng cuộn ngang phải tới được bằng bàn phím — page đặt bảng trong vùng `role="region"` có `aria-label` và `tabIndex={0}` khi có thể tràn; lúc tải, page hiện "Đang tải…" trong `role="status"` bên ngoài bảng (bảng chỉ có `aria-busy`); dòng mở được thì ô đầu chứa nút/liên kết thật có tên; `activeRowKey` chỉ đổi nền — page thêm chỉ dấu bằng chữ/icon; không dùng `selectable` khi chưa có thao tác hàng loạt được duyệt (`aria-selected` trên `tr` của bảng thường không hợp lệ ARIA, nhãn "Chọn dòng" trùng nhau, "Chọn tất cả" chỉ gồm dòng đã tải); cột số/tiền phải có `sortValue` (so sánh mặc định `>`/`<` không theo tiếng Việt, sai với chuỗi số).
- **Toast (bổ sung)**: `ToastViewport` của P2 là vùng live tồn tại sẵn (không chèn vùng đã có chữ); nếu có tự đóng thì dừng khi hover/focus (2.2.1).
- **Tag (bổ sung)**: `children` là chuỗi, hoặc truyền `removeLabel` đầy đủ.
- **PageHeader**: một `h1` mỗi trang; shell P2 chuyển focus tới đó khi đổi route.
- **Skip link** (`.ap-skip-link`): `main` cần `id` và `tabIndex={-1}`.
