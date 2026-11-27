# Brief cho Claude Design · P0-T05 · Batch G · Đơn nháp của nhân viên (EP-10e)

> 2026-11-27 · Căn cứ: QĐ 6 = A (`../DECISIONS.md` FE-UI-D06, A-80): vẽ bổ sung một batch G nhỏ trên Claude Design theo contract thật, như batch F, rồi chủ dự án duyệt trước khi code (P4-T00). Brief này là **yêu cầu thiết kế**, không phải thiết kế đã duyệt. Là DESIGN PROPOSAL: không thay ADR, `docs/contracts/` hay `docs/security/RBAC_MATRIX.md`.

## Vì sao cần batch G
Batch D (D1–D4, `docs/design/specs/30-batch-d-don-hang-nhap.md`) lệch ba quyết định đã có, nên không dùng lại được:
- **UX-03**: SALES không tạo đơn nháp. Đơn chỉ sinh ra khi khách xác nhận báo giá (ADR-007, A-03).
- **UX-08**: kết quả ERP chưa rõ thì đối soát tay, **không** có "tạo lại cùng khóa" hay nút thử lại (ADR-009, D-03).
- **UX-10**: hệ thống không đọc trạng thái duyệt, lý do hay thông báo từ ERP, không có liên kết "Mở trên ERP". SALES tự ghi lại quyết định đã làm trong ERP (D-01, A-15).

## Người dùng và quyền
- Chỉ **SALES của team phục vụ khách** (RBAC_MATRIX EP-08a/EP-08b, A-43). Vai trò khác vào màn hình → trạng thái 403 của F6 (xám, khóa, không gọi API).
- Đơn của team khác và đơn không tồn tại trả **cùng một** 404, nên giao diện không được phân biệt hai trường hợp.
- Mỗi lần đọc danh sách được ghi audit (`STAFF_LIST_READ`, A-52). Danh sách chỉ đọc khi vào màn hình, sau một thao tác ghi, và khi bấm "Tải lại". **Không** tự làm mới theo giờ (như F2, A-69).

## Màn hình cần vẽ

### G1 · Đơn chờ ghi quyết định duyệt
Đọc: `GET /api/internal/orders/awaiting-decision` → danh sách `AwaitingView`. Tối đa 50 dòng, cũ nhất trước (theo thời điểm ERP tạo đơn nháp). Gồm đơn ở trạng thái `CREATED` và `RECONCILED_CREATED` (đơn đã đối soát ở G2 là "có trong ERP").

| Trường | Kiểu | Ý nghĩa | Gợi ý hiển thị |
|---|---|---|---|
| `requestId` | số | Khóa kỹ thuật của yêu cầu đơn nháp, dùng trong đường dẫn ghi | Không hiện (câu hỏi Q1) |
| `quoteRef` | chuỗi | Mã báo giá | Mã BG |
| `quoteVersion` | số nguyên | Phiên bản báo giá đã xác nhận | "phiên bản N" cạnh mã BG |
| `erpOrderRef` | chuỗi, luôn có | Số đơn nháp do ERP cấp; SALES dùng để tìm đơn trong ERP | Số đơn ERP, chữ đơn cách (mono) |
| `since` | chuỗi ISO-8601 UTC hoặc `null` | Lúc ERP tạo đơn nháp | Giờ hiển thị như F2; `null` → "—" |

Ghi: `POST /api/internal/orders/{requestId}/staff-decision` với `{"decision": "APPROVED" | "REJECTED"}`. **Không** có ô ghi chú hay lý do (A-43).
Trả về: `DecidedView{requestId, status}`, `status` là `STAFF_APPROVED` hoặc `STAFF_REJECTED`.
- Quyết định là **cuối cùng**: không có hoàn tác hay sửa. Vì vậy cần hộp xác nhận (Dialog, động từ lặp lại ở tiêu đề và nút, tone warning như "Hoàn tất và mở lại AI" ở F3).
- Ghi lại cùng quyết định lần nữa → 200 với trạng thái hiện tại (an toàn khi bấm đúp). Quyết định ngược lại trên đơn đã có quyết định → 409.
- Sau khi ghi thành công, đơn rời danh sách. Quyết định chỉ đổi trạng thái báo giá; **không** gửi tin cho khách.
- Chữ cần thể hiện đúng bản chất: SALES **ghi lại** việc đã duyệt hoặc từ chối trong ERP, không duyệt trong APAS. Gợi ý nút: "Ghi đã duyệt" / "Ghi đã từ chối" (câu hỏi Q4).

### G2 · Đơn chưa rõ kết quả ERP (đối soát)
Đọc: `GET /api/internal/orders/unknown` → danh sách `UnknownView`. Tối đa 50 dòng, cũ nhất trước. Chỉ đơn ở `OUTCOME_UNKNOWN`.

| Trường | Kiểu | Ý nghĩa | Gợi ý hiển thị |
|---|---|---|---|
| `requestId` | số | Khóa kỹ thuật, dùng trong đường dẫn ghi | Không hiện (Q1) |
| `quoteRef` | chuỗi | Mã báo giá | Mã BG |
| `quoteVersion` | số nguyên | Phiên bản báo giá | "phiên bản N" |
| `externalRef` | chuỗi | Mã tham chiếu đã gửi sang ERP; SALES tra đơn trong ERP bằng mã này | Mã tra cứu ERP, mono, có nút sao chép |
| `since` | chuỗi ISO-8601 UTC hoặc `null` | Lúc yêu cầu kết thúc mà chưa rõ kết quả | Giờ hiển thị; `null` → "—" |
| `reportedErpOrderRef` | chuỗi hoặc `null` | Số đơn ERP báo về **sau khi** đã quá hạn chờ | Khi có: dòng phụ "ERP đã báo số đơn …" (Q3) |

Ghi: `POST /api/internal/orders/{requestId}/reconcile` với
- `{"outcome": "CREATED", "erpOrderRef": "<số đơn ERP>"}`: đơn có trong ERP. `erpOrderRef` **bắt buộc**, 1–64 ký tự, chỉ gồm chữ Latin không dấu, số và `: _ . -`.
- `{"outcome": "ABSENT"}`: ERP không có đơn này. **Không** gửi `erpOrderRef`.

Trả về: `ReconciledView{requestId, status}`, `status` là `RECONCILED_CREATED` hoặc `RECONCILED_ABSENT`.
- Sai quy tắc trên (thiếu số đơn khi CREATED, có số đơn khi ABSENT, sai ký tự) → 400. Cần trạng thái lỗi ở ô nhập, kiểm tra ngay trên giao diện trước khi gửi.
- Nếu có `reportedErpOrderRef`, backend chỉ nhận `CREATED` với **đúng** số đó; mọi lựa chọn khác → 409.
- Đơn đối soát `CREATED` chuyển sang G1 để chờ ghi quyết định duyệt. Thiết kế nên cho thấy điều này sau khi ghi (ví dụ toast kèm liên kết sang G1), nhưng không tự mở G1.
- Ghi lại đúng kết quả cũ → 200. Không bao giờ gửi lại sang ERP từ màn hình này: **không** có nút "tạo lại", "thử lại" hay "gửi lại ERP" (UX-08).

## Không được vẽ (API không có hoặc bị cấm)
- Giá, tổng tiền, VAT, số lượng, dòng hàng (danh sách không trả giá).
- Tên, số điện thoại, mã khách, tên team, công nợ hay lý do công nợ (NOT_QUOTED vẫn OPEN).
- Trạng thái duyệt, lý do hay thông báo đọc từ ERP; liên kết "Mở trên ERP" (UX-10).
- Nút tạo đơn, tạo lại, thử lại, gửi lại ERP (UX-03, UX-08).
- Ô ghi chú, lý do tự do, người được giao, lịch sử thao tác, bộ lọc, tìm kiếm, phân trang, sắp xếp theo cột, xuất file (API trả tối đa 50 dòng, không có phân trang).
- Mã HTTP, tên endpoint, tên vai trò nội bộ, tên trạng thái kỹ thuật (`OUTCOME_UNKNOWN`, `RECONCILED_CREATED` …) trên giao diện.

Nếu thiết kế cần thêm trường, ghi rõ là **đề xuất backend** (issue riêng trước P4), không vẽ như đã có.

## Trạng thái của mỗi màn hình (theo F6 và `../phases/P4-staff-orders.md`)
| Trạng thái | Yêu cầu |
|---|---|
| Đang tải | Skeleton bảng |
| Rỗng | EmptyState: "Chưa có đơn chờ ghi quyết định" (G1), "Chưa có đơn cần đối soát" (G2), kèm "Tải lại" |
| Có dữ liệu | Bảng; đủ 50 dòng → dòng chú thích "Hiển thị 50 đơn cũ nhất" |
| Lỗi đọc | Alert + mã tham chiếu `REQ-…` (từ `traceId` của `application/problem+json`, writing guide §8) + "Tải lại" |
| Quá thời gian | Không ghi gì; "Tải lại" |
| 403 | F6: xám, khóa, không đỏ |
| Đang ghi | Nút trong Dialog khóa, chữ "Đang ghi…"; không đóng Dialog bằng Esc khi đang gửi |
| Ghi thành công | Toast "Đã ghi …" **chỉ sau 2xx**; dòng biến mất; focus về dòng kế tiếp hoặc tiêu đề trang |
| 404 | "Đơn này không còn trong danh sách của bạn" (đã xử lý hoặc không thuộc team); tải lại danh sách khi người dùng bấm |
| 409 | "Đơn đã có người ghi hoặc trạng thái đã đổi"; không tự gửi lại; "Tải lại để kiểm tra" |
| Ghi chưa rõ kết quả (mất mạng, quá hạn, 5xx) | "Chưa rõ đã ghi được chưa"; **không** tự gửi lại; "Tải lại để kiểm tra" |
| Validation | G1: chưa chọn quyết định (nếu thiết kế dùng radio). G2: chưa chọn kết quả, thiếu hoặc sai số đơn ERP |

## Kích thước và bố cục
- Khung nhân viên SH-01 như batch F, artboard 1440 và 1280 (sidebar đầy) và 1024 (sidebar thu gọn) theo QĐ 8 (FE-UI-D08). Dưới 1024 px chưa vẽ.
- Chỉ dùng component đã có trong design system (DataTable, Tag, Button, Dialog, Radio, Input, Alert, EmptyState, Skeleton, Toast, PageHeader, Tabs nếu chọn Q2b). Component mới phải nêu rõ.
- Chữ theo `design-system/guidelines/writing.md`: tiếng Việt, câu ngắn, không "đã xác minh" khi không có mốc thời gian, không đổ lỗi cho người dùng.

## Câu hỏi cho chủ dự án — **đã trả lời 2026-11-27: Q1A Q2A Q3A Q4A** ("theo đề xuất", `../DECISIONS.md` FE-UI-D30..D33)
Thiết kế theo các phương án A dưới đây.

1. **Q1 · Có hiện `requestId` không?** ⭐ A: không. Mỗi dòng nhận diện bằng mã BG + phiên bản + số đơn ERP (G1) hoặc mã tra cứu ERP (G2); một phiên bản báo giá chỉ xác nhận được một lần (SEC-04, A-65). B: hiện như mã yêu cầu cho hỗ trợ.
2. **Q2 · G1 và G2 ở đâu?** ⭐ A: hai mục sidebar riêng trong nhóm "Kinh doanh" (ví dụ "Đơn chờ ghi duyệt", "Đơn cần đối soát"); mỗi trang chỉ đọc một danh sách khi vào. B: một trang "Đơn nháp" với Tabs, chỉ đọc danh sách của tab đang mở.
3. **Q3 · Khi ERP đã báo số đơn (`reportedErpOrderRef`)?** ⭐ A: điền sẵn và khóa số đơn đó, chọn sẵn "Có trong ERP", tắt "Không có trong ERP" kèm câu giải thích. B: để trống như bình thường, backend từ chối nếu khác (409).
4. **Q4 · Chữ trên nút G1?** ⭐ A: "Ghi đã duyệt" / "Ghi đã từ chối", tiêu đề Dialog "Ghi đơn … đã được duyệt trên ERP?". B: "Đã duyệt" / "Đã từ chối".

## Kết quả cần giao
- Canvas Claude Design "AutoParts – Batch G" (private), dùng AutoParts Agent Design System bản đang dùng cho batch F (`1795426540-5cc3`) hoặc bản mới hơn nếu đã có FE-UI-Q01 (ghi rõ bản).
- Artboard: `G1-Awaiting`, `G1-Confirm` (Dialog cả hai quyết định), `G2-Unknown`, `G2-Reconcile` (Dialog, có và không có `reportedErpOrderRef`, lỗi ô nhập), `G-States` (bảng trạng thái ở trên), mỗi cái ở 1440; thêm 1024 cho G1 và G2.
- Bảng nhãn tiếng Việt cho `Decision`, `Finding` và `status` của `DecidedView`/`ReconciledView`.
- Sau khi duyệt: `docs/design/specs/60-batch-g-don-hang-nhan-vien.md` và bản sao `docs/design/prototypes/G*.dc.html` (P4-T00).

## Sau khi bản thiết kế được duyệt (việc của frontend, P4)
P4-T01 bảng trường từ backend (đã đối chiếu trong brief này, kiểm lại khi code), P4-T02 decoder fail closed, P4-T03..T08 như `../phases/P4-staff-orders.md`.

## Nguồn đã đối chiếu (2026-11-27, `main` `c5dfaad`)
- `backend/src/main/java/com/secureerp/agent/order/adapter/web/StaffDecisionController.java` (`AwaitingView`, `DecisionRequest`, `DecidedView`)
- `backend/src/main/java/com/secureerp/agent/order/adapter/web/OrderReconciliationController.java` (`UnknownView`, `ReconcileRequest`, `ReconciledView`)
- `backend/src/main/java/com/secureerp/agent/order/StaffDecisions.java`, `OrderReconciliation.java`, `DraftOrderState.java`
- `backend/src/main/java/com/secureerp/agent/order/internal/DefaultStaffDecisions.java`, `DefaultDraftOrders.java` (`reconcile`, `unknown`), `DraftOrderStore.java` (`awaitingDecision`, `unknown`: tối đa 50, cũ nhất trước)
- `backend/src/main/java/com/secureerp/agent/order/spi/DraftOrderOutcome.java` (`ERP_ORDER_REF`)
- `docs/security/RBAC_MATRIX.md` (EP-08a, EP-08b, AUDIT-READS); `docs/DECISIONS_REQUIRED.md` UX-03, UX-08, UX-10, A-43, A-69

## Ngoài phạm vi
Batch A–E (P5), thay đổi backend, tạo đơn, mọi luồng phía khách.
