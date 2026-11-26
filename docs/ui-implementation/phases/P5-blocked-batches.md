# P5 · Batch A–E — BLOCKED

> DESIGN PROPOSAL. Thiết kế batch A–E (16–17/11/2026) chưa được phê duyệt, không có endpoint, và mâu thuẫn ADR/contract/RBAC ở nhiều điểm. Theo `CLAUDE.md` §Precedence 3–5 và `docs/DECISIONS_REQUIRED.md` §UX, các màn hình này **không được code** cho tới khi điều kiện mở khóa dưới đây đạt. Không dựng trên mock tự đặt (QĐ 7).

## Điều kiện mở khóa chung (mỗi nhóm)
1. Chủ dự án quyết định các UX-xx của nhóm (bảng dưới).
2. Có use case (hoặc bổ sung vào `docs/requirements/USE_CASES.md`) với actor, quyền, luồng lỗi.
3. Có contract nháp trong `docs/contracts/` (DTO allowlist, mã lỗi, phân loại dữ liệu) và cập nhật RBAC_MATRIX.
4. Spec/prototype của nhóm được rà lại theo quyết định và đánh dấu "Đã phê duyệt" trong `specs/90`.
5. Khi 1–4 đạt: tạo phase con (P5-A, P5-C…), UI dựng mock-first trên contract nháp (QĐ 5), backend làm song song; nối API thật khi có.

## Nhóm và điều kiện riêng
| Phase con | Nhóm | Màn hình | Phải quyết | Component cần chép | Phụ thuộc nhóm khác |
|---|---|---|---|---|---|
| P5-A | Tra cứu phụ tùng | A1–A7, PL-01..05, SH-02, SH-05 | UX-13 (UC phía NV), UX-15 (giá/tồn cho NV), UX-01 (VIN qua `vinRef`); câu hỏi E1, E2, R7, R9 | SearchField, DataTable, TableToolbar, Pagination, Drawer, SourceTag, ERPResult | — |
| P5-B | Trợ lý AI cho nhân viên | B1–B9, AI panel trong SH-01 | UX-01 (NV không dùng agent tool), UX-16, UX-17; AI2, AI4 | AIPanel, ToolCall, ActionConfirmation | P5-A (ngữ cảnh) |
| P5-C | Báo giá phía nhân viên | C1–C4, C6 | UX-04, UX-07, UX-09 (VAT), UX-14 (kênh gửi); Q1, Q2, Q4 | Tabs, DataTable, ActionConfirmation | P5-A (thêm vào báo giá) |
| P5-D | Đơn nháp phía nhân viên | D1–D5 (phần không thuộc P4) | UX-03, UX-08, UX-10, UX-18 | ActionConfirmation | P5-C, P4 |
| P5-E | Hỗ trợ | E1 Tổng quan, E2 Hoạt động, E3 Hồ sơ | UX-11, UX-12 (phần OPEN), Q8; nguồn số liệu | MetricCard, ChartCard, AvatarGroup, Tabs | — |
| — | Ngoài PoC theo safe default | C5 tra cứu khách, D2-Acting, E7-LoginLocked, E5-Warning, R10 gửi yêu cầu quyền, Audit Test Dashboard (SEC-DASH) | UX-02, UX-11, UX-12, A-45 | — | — |

## Tasks (chỉ tài liệu cho tới khi mở khóa)
| ID | Task | Phụ thuộc |
|---|---|---|
| P5-T01 | Lập danh sách câu hỏi cho từng nhóm, dạng quyết định đánh số có ⭐ | P0 |
| P5-T02 | Với nhóm được chọn mở trước: soạn UC + contract nháp (issue riêng, có review architecture + security) | T01, quyết định |
| P5-T03 | Viết phase con chi tiết (Scope/Tasks/AC/Testing) theo mẫu P3/P4 | T02 |

## Acceptance Criteria (để chuyển một nhóm từ BLOCKED sang READY)
- Mọi UX-xx của nhóm có trạng thái APPROVED trong `DECISIONS_REQUIRED.md`.
- Contract nháp được duyệt; RBAC_MATRIX có dòng cho mỗi hành động.
- `SCREEN_INVENTORY.md` và `PROGRESS.md` cập nhật trạng thái.

## Testing
Không có cho tới khi mở khóa. Khi mở: như P3/P4 + test contract với mock, sau đó E2E với backend thật.
