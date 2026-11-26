# P0 · Chốt điều kiện sẵn sàng

> **XONG 2026-11-26** — quyết định ghi ở `../DECISIONS.md` và `docs/DECISIONS_REQUIRED.md` A-80. Bảng dưới giữ để truy vết.

## Scope
- Chủ dự án chọn 8 quyết định ở `../IMPLEMENTATION_PLAN.md` §4.
- Ghi quyết định vào `docs/DECISIONS_REQUIRED.md` (mục A-xx mới) và dòng backlog ở `docs/backlog/IMPLEMENTATION_PLAN.md` §2a (FE-UI-P1 … FE-UI-P6).
- Xác định nhánh và issue cho từng phase.

Ngoài scope: giải quyết UX-xx của batch A–E (thuộc P5), thay đổi backend.

## Quyết định — hệ quả chi tiết
| # | Đề xuất ⭐ | Hệ quả nếu chọn ⭐ | Hệ quả nếu chọn B |
|---|---|---|---|
| 1 | Hash route | Không đổi `FrontendPaths`/`SecurityConfiguration`; deep link `/#/hop-cho` hoạt động; `Referrer-Policy: no-referrer` vẫn giữ hash khỏi máy khác | Issue backend riêng: thêm SPA fallback GET cho danh sách route, IT `FrontendServingIT` mở rộng, review security |
| 2 | Router tự viết | ~80 dòng + test; không có loader/nested route | Thêm `react-router-dom` (ghim chính xác), cập nhật lockfile, `npm audit` |
| 3 | CSS theo token | Media query thật cho 1024/1280; `style-src 'self'` vẫn đạt vì file CSS cùng origin | Hook `matchMedia` ở mỗi page, khó test |
| 4 | Không ID trong URL | Tải lại F3 → về F2 (như hiện tại); không lộ mã trong lịch sử | ID trong URL; backend vẫn 404 người khác; cần test đổi ID tay |
| 5 | Mock trong `src/mocks/`, chỉ endpoint có contract | Test build chặn mock trong `dist`; dev mode `VITE_APAS_MOCK=1` để xem trạng thái hiếm | MSW cần service worker → đổi CSP `worker-src` (A-74) |
| 6 | Vẽ batch G trên Claude Design | P4 chờ thiết kế (ước 1–2 ngày) + duyệt | Dựng từ D1/D4, tự quyết phần cắt bỏ |
| 7 | A–E giữ BLOCKED | P5 không có task chạy được tới khi có quyết định | Mã UI dựa trên API tưởng tượng, phải làm lại khi có contract |
| 8 | Breakpoint từ thiết kế | <1024 px cho nhân viên là đề xuất chưa vẽ | Chờ artboard mới |

## Tasks
| ID | Task | Phụ thuộc | Đầu ra |
|---|---|---|---|
| P0-T01 | Chủ dự án chọn QĐ 1–8 | — | Trả lời dạng "1A 2A …" |
| P0-T02 | Ghi A-xx trong `DECISIONS_REQUIRED.md` | T01 | Mục quyết định có ngày, người duyệt |
| P0-T03 | Thêm dòng FE-UI-P1..P6 vào backlog §2a | T01 | Bảng backlog |
| P0-T04 | Tạo nhánh `feat/FE-UI-P1-design-system` từ `main` khi bắt đầu P1 | T02 | Nhánh local |
| P0-T05 | (Nếu QĐ 6 = A) yêu cầu thiết kế batch G trên Claude Design | T01 | Brief cho Claude Design, liệt kê đúng trường API |

## Dependencies
Không có (điểm bắt đầu).

## Acceptance Criteria
- Có 8 quyết định được ghi với nguồn (lời chủ dự án, ngày).
- Mỗi phase P1–P6 có một dòng backlog với trạng thái PROPOSED/READY/BLOCKED khớp `../PROGRESS.md`.
- Không file mã nào thay đổi.

## Testing
Không có test mã. Kiểm tra: `git diff --stat` chỉ gồm `docs/`.
