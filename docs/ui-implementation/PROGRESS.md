# Tiến độ triển khai Frontend

> Cập nhật: 2026-11-26 · Kế hoạch: `IMPLEMENTATION_PLAN.md` · Chỉ đánh dấu `[x]` khi lệnh kiểm tra đã chạy và exit code đã xem; ghi bằng chứng ở cột cuối hoặc trong `docs/SESSION_HANDOFF.md`.

Ký hiệu: `[ ]` chưa làm · `[~]` đang làm · `[x]` xong (VERIFIED) · `[!]` BLOCKED · `[-]` ngoài phạm vi

## Tổng quan
| Phase | Trạng thái | Bắt đầu | Xong | Bằng chứng |
|---|---|---|---|---|
| P0 Chốt điều kiện | XONG | 2026-11-26 | 2026-11-26 | A-80, `DECISIONS.md` |
| P1 Design System | Đạt quality gate, chờ duyệt + commit | 2026-11-26 | — | `evidence/P1-quality-gate.md` |
| P2 App Layout | Chưa bắt đầu | — | — | — |
| P3 Batch F vào khung | Chưa bắt đầu | — | — | — |
| P4 Đơn hàng NV (EP-10e) | GATED (thiết kế) | — | — | — |
| P5 Batch A–E | BLOCKED (UX-xx, contract) | — | — | — |
| P6 Chất lượng | Liên tục | — | — | — |
| W-BIZ Xung đột A–E | Chờ quyết định chủ dự án | 2026-11-26 | — | `CONFLICT_RESOLUTION_MATRIX.md` |

## Đã có trước kế hoạch (không làm lại)
- [x] F5 OP-01 công tắc AI — EP-10a, A-67
- [x] F1 CH-01..04 chat khách — EP-10b, A-68
- [x] F2 HO-01, F3 HO-02 — EP-10c, A-69
- [x] F4 SI-01 — EP-10d, A-70
- [x] E2E backend thật, CSP, phục vụ bản build — A-71..A-75

## P0 · Chốt điều kiện
- [x] P0-T01 Chủ dự án chọn QĐ 1–8 (1A 2B 3A 4A 5A 6A 7A 8A, 2026-11-26)
- [x] P0-T02 A-80 trong `DECISIONS_REQUIRED.md` + `DECISIONS.md`
- [x] P0-T03 Dòng FE-UI-P1..P6, FE-UI-WBIZ trong backlog §2a
- [x] P0-T04 Worktree `AI-Agent-fe`, nhánh `feat/FE-UI-P1-design-system` từ `origin/main` `c4edc3b`
- [ ] P0-T05 Brief thiết kế batch G (D06) — chưa làm, cần trước P4

## P1 · Design System
- [x] P1-T01 tokens.css/bundle.css giống bản gốc; `ds:verify` 57 → 73 file
- [x] P1-T02 Export Field, Breadcrumb
- [x] P1-T03 Chép Toast (+ ToastViewport)
- [x] P1-T04 Chép PageHeader, Tabs, Tag, Card, Tooltip (sửa D11), DataTable (sửa D14), Checkbox (D10)
- [x] P1-T05 `sources.json` + `ds:verify` bắt file chép không khai báo
- [x] P1-T06 `breakpoints.ts`
- [x] P1-T07 `foundation.css` + `config/foundationCss.test.ts`
- [-] P1-T08 Định dạng tiền/thời gian — ngoài phạm vi P1 được duyệt, chuyển P3
- [-] P1-T09 Trang tham chiếu — không làm

## P2 · App Layout & hạ tầng
- [ ] P2-T01 Router
- [ ] P2-T02 Bảng route theo vai trò
- [ ] P2-T03 Guard hiển thị → SH-03
- [ ] P2-T04 StaffShell SH-01 (1024/1280/1440)
- [ ] P2-T05 CustomerShell (390)
- [ ] P2-T06 PageState (7 trạng thái)
- [ ] P2-T07 ToastViewport SH-08
- [ ] P2-T08 ErrorBoundary → SH-04
- [ ] P2-T09 Hết phiên E5 (401)
- [ ] P2-T10 Lớp mock dev/test + test build
- [ ] P2-T11 Cầu nối tạm F1–F5
- [ ] P2-T12 README

## P3 · Batch F vào khung
- [ ] P3-T01 E7 Đăng nhập
- [ ] P3-T02 F5 Công tắc AI
- [ ] P3-T03 F2 Hộp chờ
- [ ] P3-T04 F3 Hội thoại
- [ ] P3-T05 F4 Inspector
- [ ] P3-T06 F1 Chat khách
- [ ] P3-T07 Gỡ cầu nối tạm
- [ ] P3-T08 Bảng lệch thiết kế

## P4 · Đơn hàng NV (GATED)
- [!] P4-T00 Thiết kế batch G được duyệt
- [ ] P4-T01 Bảng trường API
- [ ] P4-T02 `api/orders.ts` + decoder
- [ ] P4-T03 Nhãn tiếng Việt
- [ ] P4-T04 Page G1 chờ ghi quyết định
- [ ] P4-T05 Page G2 đối soát
- [ ] P4-T06 Mock theo contract
- [ ] P4-T07 E2E
- [ ] P4-T08 TRACEABILITY, backlog, README

## W-BIZ · Xung đột A–E (song song)
- [ ] W-BIZ-T01 Nhóm 0: UX-16, UX-09, UX-07, UX-18, UX-05
- [ ] W-BIZ-T02 UX-15 + UC tra cứu phía NV
- [ ] W-BIZ-T03 Contract nháp nhóm A
- [ ] FE-UI-Q01 Vòng focus design system (đổi token trên Claude Design?)

## P5 · Batch A–E (BLOCKED)
- [!] P5-A Tra cứu phụ tùng — UX-13, UX-15, UX-01
- [!] P5-B Trợ lý AI nhân viên — UX-01, UX-16, UX-17
- [!] P5-C Báo giá nhân viên — UX-04, UX-07, UX-09, UX-14
- [!] P5-D Đơn nháp nhân viên — UX-03, UX-08, UX-10, UX-18
- [!] P5-E Tổng quan, Hoạt động, Hồ sơ — UX-11, UX-12
- [-] C5, D2-Acting, E7-LoginLocked, E5-Warning, R10, Audit Test Dashboard

## P6 · Cổng chất lượng mỗi phase
| Kiểm tra | P1 | P2 | P3 | P4 |
|---|---|---|---|---|
| typecheck / unit / build / ds:verify | [x] 261 test | [ ] | [ ] | [ ] |
| axe mọi trạng thái | [x] jsdom, không contrast | [ ] | [ ] | [ ] |
| bàn phím | [x] component | [ ] | [ ] | [ ] |
| responsive | NOT RUN (jsdom; P2-T14) | [ ] | [ ] | [ ] |
| khớp thiết kế | — | [ ] | [ ] | [ ] |
| E2E backend thật | NOT RUN (D13: thiếu `APAS_DEMO_PASSWORD`) | [ ] | [ ] | [ ] |
| review advisory | [x] sec 0H/0M/4L, UX 2H/7M/5L | [ ] | [ ] | [ ] |
