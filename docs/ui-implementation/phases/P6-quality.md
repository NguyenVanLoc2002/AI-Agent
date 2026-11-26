# P6 · Chất lượng xuyên suốt và nghiệm thu

> DESIGN PROPOSAL. Chạy ở cuối mỗi phase (cổng ra) và một lần tổng kiểm sau P3/P4.

## Scope
Truy cập, responsive, bảo mật phía trình duyệt, khớp thiết kế, bằng chứng test. Không thêm tính năng.

## Tasks
| ID | Task | Khi nào | Chi tiết |
|---|---|---|---|
| P6-T01 | Cổng mỗi phase: typecheck, unit, build, ds:verify, `git diff --check` | cuối P1–P5 | Exit code ghi vào PR |
| P6-T02 | Axe trên mọi trạng thái của mọi page | cuối P2–P4 | 0 vi phạm mức serious/critical |
| P6-T03 | Bàn phím: thứ tự tab, focus sau chuyển trang/đóng Dialog, Esc, skip link | cuối P2–P4 | Test Testing Library + kiểm tay ghi lại |
| P6-T04 | Ma trận responsive: 390 (khách; rộng hơn: nội dung giữ cột giữa như hiện tại, chưa có artboard), 1024 / 1280 / 1440 (nhân viên); không cuộn ngang trang | cuối P2–P4 | Playwright viewport + ảnh chụp tạm (không commit ảnh có dữ liệu thật — chỉ dữ liệu tổng hợp) |
| P6-T05 | Khớp thiết kế: so từng page với artboard, ghi chỗ lệch có lý do | cuối P3, P4 | Bảng trong PR; `ux-workflow-reviewer` |
| P6-T06 | Bảo mật trình duyệt: 0 vi phạm CSP trong E2E, không gì trong `localStorage`, `sessionStorage` chỉ mục đã duyệt (A-68), không giá/token trong DOM sau đăng xuất | mỗi phase có E2E | `security-auditor` |
| P6-T07 | Chữ tiếng Việt: kiểu đặt dấu mới, động từ trên nút, không "OK", không lộ mã HTTP/endpoint | mỗi phase | Test quét chuỗi trong `texts.ts`/page |
| P6-T08 | E2E đầy đủ với backend thật (`APAS_E2E_REQUIRE_TLS=1 npm run e2e`) | cuối P2, P3, P4 | Theo `frontend/README.md` |
| P6-T09 | Cập nhật `PROGRESS.md`, `SESSION_HANDOFF.md`, TRACEABILITY | cuối mỗi phase | VERIFIED / NOT RUN / BLOCKED / PROPOSED tách riêng |
| P6-T10 | Review advisory: `ux-workflow-reviewer`, `security-auditor`, `qa-gatekeeper` | trước PR mỗi phase | Ghi số H/M/L và cách xử lý vào A-xx |

## Dependencies
Phase đang kết thúc.

## Acceptance Criteria (definition of done cho mỗi phase frontend)
- Tất cả lệnh ở T01 exit 0, đã ghi lệnh và thời điểm.
- Mọi trạng thái của page trong phase có component test + axe.
- E2E xanh khi phase đổi DOM hoặc luồng (hoặc NOT RUN có lý do).
- Không có màu/cỡ/khoảng cách hard-code; không icon ngoài Lucide 0.460.0; font duy nhất Be Vietnam Pro.
- Không dữ liệu nhạy cảm trong log, ảnh chụp, snapshot test.

## Testing
Chính là các task trên.
