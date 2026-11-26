# P3 · Đưa F1–F5 và đăng nhập vào khung mới

> DESIGN PROPOSAL · nhánh dự kiến `feat/FE-UI-P3-batch-f-shell` · issue FE-UI-P3.

## Scope
Chuyển các page đã làm (EP-10a..d) sang router, khung và `PageState` của P2; đối chiếu lại từng page với artboard batch F và E7 để sửa chỗ lệch thiết kế (layout, khoảng cách, chữ, icon). **Không đổi hành vi nghiệp vụ hay luồng gọi API** (các quy tắc đọc "khi vào / sau thao tác / khi yêu cầu" vì audit, A-52/A-69/A-70 giữ nguyên).

## Page và task
| ID | Page | Artboard | Task | Trạng thái cần đủ |
|---|---|---|---|---|
| P3-T01 | E7 Đăng nhập | `E7-Login`, `E7-LoginError` | Đối chiếu layout/chữ; thông báo sai thông tin chung chung (A-71) | đang gửi, sai thông tin, lỗi mạng, hết phiên (thông báo) |
| P3-T02 | F5 Công tắc AI `#/cong-tac-ai` | `F5-KillSwitch`, `F6-StaffStates` | Dùng PageHeader + PageState; giữ Dialog danger | đang tải, không đọc được (coi là tắt), đang đổi, lỗi đổi, 403 |
| P3-T03 | F2 Hộp chờ `#/hop-cho` | `F2-Inbox`, `F6` | PageState; toast "đã có người nhận" (409) qua ToastViewport; DataTable nếu artboard dùng bảng | đang tải, rỗng, 409, quá thời gian, 403 |
| P3-T04 | F3 Hội thoại (mở từ F2, QĐ 4) | `F3-Conversation`, `F6` | Breadcrumb "Hộp chờ › Hội thoại"; PageState 404/409/503 | đang tải, 404 mất quyền, 409, 503 không hiện báo giá, gửi chưa rõ |
| P3-T05 | F4 Inspector `#/su-kien-bao-mat` | `F4-Inspector`, `F6` | Field cho bộ lọc, validation tại trường (≤ 7 ngày); ToolCall nếu artboard dùng (chép ở P3, review) | đang tải, rỗng, lọc sai, trace không tìm thấy, `truncated`, 403 |
| P3-T06 | F1 Chat khách `#/chat` | `F1-Chat`, `F1-Handoff`, `F1-QuoteStates`, `F1-SendStates` | CustomerShell; kiểm 390 px, bàn phím ảo không che ô nhập | CH-01..04 đủ như `specs/50` |
| P3-T07 | Gỡ cầu nối tạm P2-T11 | — | Xóa `SCREENS_BY_ROLE` cũ trong `App.tsx` | — |
| P3-T08 | Ghi chỗ lệch thiết kế còn lại | — | Bảng "lệch có lý do" trong PR (vd. trường API không có) | — |

## Dependencies
P2 hoàn tất. Không cần backend mới.

## Acceptance Criteria
- Mỗi page khớp artboard tương ứng về bố cục, chữ, màu token, icon (so ảnh chụp ở 1440/1280/1024 và 390 cho F1); chỗ lệch có lý do ghi trong PR.
- Đủ trạng thái theo cột "Trạng thái cần đủ", mỗi trạng thái có component test.
- Số lần đọc API audit không tăng: test fake backend đếm lời gọi trước/sau giống nhau.
- Không đổi nhãn `QuoteStatus`/`HandoffReason` (UX-06, UX-17 đã duyệt).
- 224 unit test cũ (có chỉnh selector nếu cần) + test mới xanh; 29 E2E xanh.

## Testing
```sh
cd frontend
npm run typecheck && npx vitest run && npm run build && npm run ds:verify
APAS_E2E_REQUIRE_TLS=1 npm run e2e   # theo frontend/README.md: jar -Pwith-frontend, DB mới, Fake provider
```
Thêm: axe trên mỗi trạng thái; test bàn phím F3 (gửi tin, Hoàn tất có xác nhận), F5 (Dialog giữ focus).

## Threat cases
Giá và tin nhắn không còn trên màn hình sau đăng xuất/hết phiên; F3 sau resolve → 404 "Không tìm thấy"; Inspector không hiện trường ngoài allowlist (decoder fail closed giữ nguyên).

## Rollback
Revert commit; page cũ trở lại; không đổi backend.
