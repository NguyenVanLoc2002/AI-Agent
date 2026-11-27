# P3 · Quality gate — bằng chứng

> 2026-11-26 · issue FE-UI-P3 · quyết định FE-UI-D22–D29 (`../DECISIONS.md`), mục chung A-87 (số sẽ kiểm lại trước khi mở PR) · worktree `D:/Project_Private/AI-Agent-fe`, nhánh `feat/FE-UI-P3-batch-f-shell` từ `main` `eea4140` (đã có FE-REACT19, PR #70).
> Môi trường: Windows 11, Node 22.20.0, npm 10.9.3, React 19.3.1, react-router-dom 7.18.5, Vitest 5.1.1, Playwright 1.65.1 (Chromium 1255).

## 1. Phạm vi đã làm
| Task | Màn hình | Đã làm |
|---|---|---|
| P3-T01 | E7 Đăng nhập | Thẻ 400 px theo artboard; tên thương hiệu làm tiêu đề thẻ, h1 "Đăng nhập" ẩn (cho trình đọc màn hình và E2E bảo mật framing); nhãn "Tài khoản"; lỗi trống "Bắt buộc"; sai thông tin một câu chung "Tài khoản hoặc mật khẩu không đúng" (A-71); "Đang xác thực…"; dòng "Không tự đăng ký… liên hệ quản lý trực tiếp hoặc bộ phận IT" |
| P3-T02 | F5 Công tắc AI | `PageHeader`; thẻ "Trạng thái hiện tại" (badge theo lý do: vận hành tắt = warning, nhà cung cấp từ chối khóa = error, không đọc được = restricted); nhãn công tắc và mô tả của artboard; "Thay đổi gần nhất"; khối lý do (nền nested); Alert "Thời gian áp dụng"; lỗi 4xx theo F6 "Không đổi được trạng thái AI"; 403 → `PageState` |
| P3-T03 | F2 Hộp chờ | `PageHeader` + "Tải lại"; Alert khóa "Danh sách không chứa thông tin khách hàng"; `DataTable` 5 cột, việc tiếp theo thành dòng phụ dưới lý do; nút theo artboard (Mở / Nhận xử lý / Đã có người nhận bị khóa); dòng ghi chú cuối; `PageState` đang tải/rỗng/lỗi/quá thời gian/403; **409 → toast F6** |
| P3-T04 | F3 Hội thoại | Breadcrumb "Kinh doanh › Hộp chờ nhân viên › …mã" (`useExtraCrumb`); badge "Bạn đang xử lý" · "Lý do: …" · "Trợ lý AI đang tạm dừng"; hai cột (tin nhắn 480+, cột phải 320+); `ChatMessage`; ô "Trả lời khách hàng" + nút "Gửi"; thẻ báo giá theo artboard + badge "Chỉ xem, không sửa giá tại đây"; hai Alert (nhật ký kiểm toán, khi bấm Hoàn tất); 503 báo giá theo F6; **404/403 → trạng thái "Không tìm thấy cuộc trò chuyện" tại chỗ (F6)**; 409 → toast |
| P3-T05 | F4 Inspector | `PageHeader` + 2 badge meta; khung lọc 16/20; "Áp dụng" icon `list-filter`; bảng tiêu đề in hoa, "Số lần" căn phải, chân bảng + "Tải thêm" trong cùng thẻ; trace: badge kết luận cạnh tiêu đề, hộp nested có tiêu đề overline; "Không tìm thấy trace" và "Chỉ hiện 200 dòng đầu" theo F6; `PageState` cho đang tải/rỗng/lỗi/403 |
| P3-T06 | F1 Chat khách | Header khách theo F1 (thương hiệu, "Tư vấn phụ tùng · Thử nghiệm", badge trạng thái do page cấp, đăng xuất dạng icon); ô soạn dính đáy (`.ap-customer__composer`), placeholder và dòng ghi chú theo trạng thái; thẻ báo giá theo F1 (mỗi dòng một hộp nested, "Tạm tính", nút icon `square-check`); 4 trạng thái F1-QuoteStates; chữ F1-SendStates (409, 429, quá thời gian) |
| P3-T07 | Cầu nối tạm P2-T11 | Đã không còn từ P2 (`SCREENS_BY_ROLE` không còn trong mã; kiểm bằng tìm kiếm). Không có việc |
| P3-T08 | Bảng lệch thiết kế | Mục 5 |
| P2 dư | Focus sau đăng xuất / hết phiên / "Thử lại" (UX M2) | Thông báo trên trang đăng nhập (hoặc h1) và thông báo lỗi kiểm tra phiên nhận focus khi focus đang ở `body` |
| P2 dư | Khung đã đăng nhập dưới CSP | 4 test bundle mới (OPS, SALES, SECREV, CUST) chạy bản build dưới CSP bắt buộc, API trả bằng `page.route` |
| P1-T08 | Rà `money.ts` và định dạng thời gian | Mục 6 |

Không đổi backend, API, hợp đồng, phụ thuộc, CSP, `sources.json` hay bản sao design system.

## 2. Gate cuối (2026-11-26T11:14Z, code cuối, sau khi sửa theo review)
| Lệnh | Kết quả |
|---|---|
| `npm run typecheck` | **exit 0** |
| `npx vitest run` | **exit 0** · 19 file · **312** test passed (298 trước P3 + 14 mới); chạy 2 lần liên tiếp, cả 2 đều 312 |
| `npm run build` (typecheck + vite build + verify-no-mocks) | **exit 0** · JS 413,07 kB (gzip 122,81; trước P3 399,72 / 119,09) · CSS 16,48 kB · "production bundle has no mock code" |
| `npm run ds:verify` | exit 0 · 73 file (không bản sao nào đổi) |
| `npm audit` | 0 vulnerabilities |
| `npx tsc -p tsconfig.e2e.json --noEmit` | exit 0 |
| `npm run e2e:shell` (Playwright, không backend) | **exit 0** · **13 passed** (9 cũ + 4 mới: màn hình đã đăng nhập của OPS, SALES, SECREV, CUST từ bản build dưới CSP bắt buộc, 0 vi phạm CSP, mọi request cùng origin) |
| Ảnh chụp đối chiếu | bản build + dữ liệu tổng hợp: E7 1440/390, F5 1440/1024, F2 1440/1280/1024, F3 1440, F4 1440 (+ trace), F1 390/1280. Sửa sau khi xem: viền focus mặc định trên h1 (D29), cột "Số lần" F4 bị khuất ở 1440 (padding 12, pseudonym xuống dòng), bảng trace cạnh nhau quá hẹp (chồng dọc). Ảnh không commit |
| `git diff --check` | exit 0 |
| Lint | NOT RUN — repo không cấu hình lint |
| `npm run e2e` (backend thật) | **NOT RUN** — không có `APAS_DEMO_PASSWORD` (D13) |

## 3. Test mới và test đã chỉnh
| File | Nội dung |
|---|---|
| `src/app/App.test.tsx` (+3) | focus vào thông báo sau đăng xuất, sau hết phiên; focus về thông báo khi "Thử lại" lại lỗi. Đột biến: bỏ hai lệnh `focus()` → 3/3 đỏ, đã khôi phục |
| `src/app/shell.test.tsx` (+2) | breadcrumb F3 ba cấp khi mở hội thoại, về hai cấp khi quay lại, focus h1 F3; badge trạng thái trong header khách |
| `src/pages/staff/StaffWorkspace.test.tsx` (+2, sửa) | 409 + dòng nay của người khác → toast "Yêu cầu đã có người nhận"; 409 + dòng vẫn chờ → toast "Cuộc trò chuyện đang bận"; F3 404 → trạng thái "không tìm thấy" có mã `REQ-`, không đọc báo giá, không đọc lại danh sách tới khi bấm "Về hộp chờ", focus h1 hộp chờ khi quay lại; 404 khi gửi → tin nhắn và giá rời màn hình, focus "Về hộp chờ"; 403 khi đọc báo giá → không còn tin nhắn |
| `src/pages/KillSwitchPage.test.tsx` (+3, sửa) | chữ F5 (thời gian áp dụng, thay đổi gần nhất, khối lý do); 429 giữ lời khuyên; trạng thái không đọc được → "Tải lại" đọc lại |
| `e2e-shell/bundle.spec.ts` (+4) | màn hình đã đăng nhập của 4 vai trò từ bản build dưới CSP bắt buộc; mọi request cùng origin; fixture bắt vi phạm CSP |
| Selector | đổi theo chữ artboard ở `CustomerChatPage.test.tsx`, `SecurityInspectorPage.test.tsx`, `e2e/session.spec.ts`, `e2e/support.ts` (nhãn "Tài khoản", câu sai thông tin), `e2e-shell/shell.spec.ts`. Không test nào bị bỏ |

## 4. Số lần đọc API có kiểm toán
Không đổi. Các test đếm lời gọi đã có vẫn xanh, không sửa số: hộp chờ chỉ đọc khi vào, sau nhận/hoàn tất và khi bấm "Tải lại" (không theo giờ); F3 đọc báo giá một lần mỗi lần mở, tin nhắn khi vào, sau gửi và khi bấm "Tải tin mới"; Inspector đọc khi vào, "Áp dụng", "Tải thêm", trace chỉ khi yêu cầu. Thay đổi duy nhất về luồng: khi F3 mất quyền (404/403), danh sách hộp chờ **không** còn được đọc lại tự động; nó được đọc khi người dùng bấm "Về hộp chờ" (một lần, như trước). Test: `StaffWorkspace.test.tsx` "shows "not found" when the access ended…" đếm `GET /api/internal/handoffs` = 1 trước khi bấm, 2 sau khi bấm.

## 5. Lệch thiết kế có lý do (P3-T08)
| Màn hình | Artboard | Bản cài đặt | Lý do |
|---|---|---|---|
| E7 | Tiêu đề "AutoParts Agent", không có h1 | Giữ h1 "Đăng nhập" ẩn | Tiêu đề trang cho trình đọc màn hình; E2E framing/headers/session (baseline bảo mật) tìm heading này |
| E7 | "Đăng nhập bằng tài khoản nhân viên nội bộ"; placeholder `ten@apas.example` | "Đăng nhập bằng tài khoản demo · dữ liệu mô phỏng"; không placeholder | Bản demo đăng nhập cả khách (F1); tài khoản demo không phải email |
| E7 | "Sai nhiều lần sẽ tạm khóa"; artboard E7-LoginLocked (AUTH-423); SSO | Không có | Chưa có khóa tài khoản ở backend (A-39(b)); SSO chờ xác minh. Không hứa điều hệ thống chưa làm |
| E7 | Padding 28, bo góc 16 | `--space-card-lg` 24, `--radius-xl` 12 | Không có token 28/16; quy tắc D03 không dùng giá trị cứng |
| F5 | Hai thẻ cạnh nhau: trạng thái + mẫu hộp thoại; thẻ "Các trạng thái tắt" | Một thẻ trạng thái; hộp thoại là `Dialog` thật; chỉ khối lý do của trạng thái hiện tại | Thẻ thứ hai và thẻ cuối là mẫu minh họa của artboard |
| F5 | "Lý do tắt gần nhất" khi AI đang bật | Không có | API `/api/ops/kill-switch` chỉ có lý do khi đang tắt |
| F2 | Bảng tự vẽ, tiêu đề in hoa | `DataTable` của design system (tiêu đề không in hoa) | Dùng component chung (P1); không sửa bản sao |
| F2 | Sidebar có "Đơn chưa rõ kết quả", "Đơn chờ quyết định" + số đếm | Chưa có | EP-10e, P4 (thiết kế batch G) |
| F3 | Không có nút đọc lại tin | "Tải tin mới" trong thẻ tin nhắn | Tin chỉ đọc khi yêu cầu (A-69, 2A) |
| F3 | Dòng báo giá chỉ có thành tiền | Thêm "Đơn giá" dưới SKU | Nhân viên cần đơn giá để trả lời khách; trước P3 đã hiện |
| F3 | Mất quyền: không vẽ hành vi | Trạng thái F6 "Không tìm thấy cuộc trò chuyện" tại chỗ + "Về hộp chờ" | Chữ F6 có "Quay lại hộp chờ"; nội dung rời màn hình ngay |
| F4 | Bộ lọc "Từ/Đến" dạng chữ dd/mm/yyyy | Chọn khoảng (24 giờ / 7 ngày / tùy chọn) + `datetime-local` | Đã duyệt ở EP-10d (A-70), giữ |
| F4 | Trace: `DescriptionList` + `ToolCall`, hai hộp cạnh nhau | Bảng trong hộp nested, hộp chồng dọc | Một trace có nhiều lần kiểm tra/gọi công cụ, mỗi lần có thời điểm và mã; `ToolCall` vẽ `<button>` cả khi không có gì để mở (a11y) nên không chép |
| F4 | Ô bảng padding 16 | 12 | Bảng 7–9 cột mã; với 16 cột "Số lần" bị khuất ở 1440 |
| F4 | Không có | Nút "Đến tra cứu trace" | Lối tắt bàn phím đã có từ EP-10d (review UX) |
| F1 | Header không có tài khoản | Nút đăng xuất dạng icon (nhãn "Đăng xuất") | Khách phải đăng xuất được; icon giữ header một dòng ở 390 |
| F1 | Header 56 | 56 tối thiểu; dòng "Tư vấn phụ tùng · Thử nghiệm" xuống dòng ở 390 khi có badge + nút | Chấp nhận (không cắt chữ) |
| F1 | Không có badge cho lúc nhân viên đã nhận | "Nhân viên đang hỗ trợ" (info) | Trạng thái `STAFF` có thật; artboard chỉ vẽ dải "Nhân viên tư vấn đã tham gia" |
| F1 | Tin nhân viên dùng `ChatMessage` assistant (icon AI) | Giữ icon người | Tin của người không mang dấu AI (minh bạch với khách) |
| F1 | Dòng có tên sản phẩm "Má phanh trước" | Chỉ SKU | `QuoteView` không có tên sản phẩm |
| F1 | Tin hệ thống dạng dải giữa (`role="system"`) | Như tin trợ lý AI | API không phân biệt câu cố định của backend với câu trả lời của AI |

## 6. Rà `money.ts` và thời gian (P1-T08)
- `formatMoney(amount, currency)`: chỉ nhận chuỗi thập phân đã qua decoder (`AMOUNT` = `^\d{1,15}(\.\d{1,4})?$`, không âm); nhóm nghìn bằng chuỗi, không qua số thực; VND `₫`, tiền khác giữ mã và phần lẻ. Test `chatDecoders.test.ts` (4 giá trị + số 15 chữ số). Không sửa.
- Ghi nhận, không sửa: số có số 0 đứng đầu ("0012") sẽ hiện "0.012"; backend (`BigDecimal.toPlainString`) không sinh dạng này. VND có phần lẻ (decoder cho phép) hiện nguyên, không làm tròn — đúng hơn là che đi.
- Thời gian: `formatDateTime` (Intl `vi-VN`, `h23`) và `formatSeconds` đều theo múi giờ máy người xem; Inspector ghi rõ múi giờ (UTC+hh:mm) ở dòng tóm tắt và ô "Đến". Hạn báo giá F3 so với đồng hồ máy (đã có từ EP-10c, security L-2).

## 7. Review advisory (2026-11-26, chỉ đọc, không chạy gì)
| Review | Kết quả | Đã xử lý |
|---|---|---|
| security-auditor | 0H / 1M / 3L | **M-1** 409 khi hoàn tất cũng có nghĩa "không còn do bạn giữ": bỏ toast "đang bận", trả lại câu thận trọng như trước P3 (test). **L-1** 409 khi nhận mà dòng đã mất hoặc nay là của mình: không toast trái với danh sách; dòng mất → "Yêu cầu không còn trong hộp chờ." (2 test). **L-2** breadcrumb bỏ mã khi mất quyền. **L-3** stub `/api/**` trong `bundle.spec.ts` chấp nhận, không tính là bằng chứng SEC (đã ghi ở đầu khối test) |
| ux-workflow-reviewer | 0H / 3M / 10L | **M1** F3: tin lỗi lần đầu thì báo giá kẹt ở skeleton → báo giá đọc một lần sau lần đọc tin đầu tiên thành công, kể cả từ "Thử lại"/"Tải tin mới" (test; đột biến đỏ). **M2** F5: đọc lại xóa công tắc, focus rơi về `body`; còn phát hiện thêm khi viết test: công tắc còn khóa lúc hộp thoại đóng nên không nhận lại focus → giữ nội dung khi đọc lại, focus về công tắc khi mở khóa, "Tải lại" focus tiêu đề thẻ (test; đột biến đỏ). **M3** F2: 409/404 khi nhận làm mất focus → focus về dòng hoặc h1 (test). **L7** khung khóa và dòng ghi chú chỉ hiện cùng danh sách; **L8** bỏ nhãn vai trò ẩn lẻ trong header khách; **L9** `100dvh` + `scroll-padding-bottom` cho ô soạn dính đáy; **L10** spinner F5 không đọc hai lần; **L11** bỏ dấu chấm cuối tiêu đề; **L12** ô đăng nhập `readOnly` khi đang gửi (không mất focus). Không sửa: L4 (chữ 404/403 chung của F6 vẫn đúng ý), L5 (thông báo "Có 1 tin mới" sau khi gửi, có từ EP-10c), L6 (trạng thái mất quyền không có h1; focus đã vào nút), L13 (`aria-label` của main, vô hại) |

## 8. Rủi ro còn lại
- E2E backend thật (`npm run e2e`) **NOT RUN**: không có `APAS_DEMO_PASSWORD` (D13). Selector E2E đã đổi theo chữ mới nhưng chưa chạy với jar.
- Ảnh chụp đối chiếu artboard chỉ lưu ngoài repo (thư mục tạm của phiên), dữ liệu tổng hợp; không commit.
- Giao diện nhân viên dưới 1024 px vẫn PROPOSED (D08).
