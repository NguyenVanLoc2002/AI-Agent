# WORKFLOW A — CONDITIONAL UX REVIEW & REQUIRED CORRECTIONS

Tôi đã xem báo cáo hoàn thành Workflow A của dự án AutoParts Agent — Employee Workspace.

Tôi đồng ý tiếp tục hoàn thiện Workflow A theo các điều kiện dưới đây.

Đây là chấp thuận cho giai đoạn UX Prototype, KHÔNG phải phê duyệt cuối cùng đối với business rules, API contracts hoặc security implementation.

## 1. Trả lời các vấn đề nghiệp vụ

### B6 — Roles and Permissions

Chưa phê duyệt danh sách role chính thức.

Trong giai đoạn prototype, sử dụng các persona mô phỏng để kiểm tra UI:

- Sales Employee
- Restricted Employee
- Sales Supervisor
- System Administrator

Các persona này chỉ phục vụ UX simulation.

Không mặc định quyền xem giá, chỉnh sửa giá, truy cập liên chi nhánh, quản lý báo giá hoặc tạo đơn nháp cho bất kỳ persona nào.

Thiết kế UI theo permission/capability và object-level access. Những quyền chưa được xác nhận phải đánh dấu PROPOSED hoặc UNKNOWN.

### R4 — VAT

Trạng thái: UNKNOWN.

Không tự giả định giá đã bao gồm VAT hoặc chưa bao gồm VAT.

Hiển thị trạng thái VAT chưa xác nhận khi cần.

Không tự áp tỷ lệ thuế hoặc tạo quy tắc tính tổng báo giá chính thức.

### B3 — VIN Decoder

Trong demo, sử dụng deterministic Mock VIN Mapping từ fictional fixtures.

Không kết nối dịch vụ VIN decoder thật.

Không để LLM tự suy đoán vehicle compatibility.

Bổ sung trạng thái:
- VIN không hợp lệ
- VIN không được hỗ trợ
- Không tìm thấy dữ liệu ánh xạ
- Có dữ liệu mẫu đã ánh xạ

Đánh dấu rõ nguồn dữ liệu mô phỏng.

### R5 — Inventory Visibility

Mặc định chỉ cho xem dữ liệu tồn kho thuộc phạm vi chi nhánh được cấp quyền.

Không tự cấp quyền xem tồn kho toàn hệ thống.

Không hiển thị hoặc truyền dữ liệu của chi nhánh ngoài phạm vi truy cập đến các component và AI Panel không được phép.

Nếu chưa có dữ liệu quyền/chi nhánh, sử dụng trạng thái hạn chế hoặc chưa xác định phù hợp.

## 2. Correct ERP Data Provenance

Review Product Lookup, Product Detail, SourceTag, and AI Panel.

Distinguish:
- Fresh ERP-confirmed data
- Previously synchronized or cached data
- Stale data
- Unverified data
- ERP unavailable

A synchronization timestamp alone must not be treated as proof that a value is current.

Do not display stale or mock values as live ERP-confirmed data.

Use clear Vietnamese labels.

## 3. Review Permission UX

Ensure unauthorized data is not visible through:
- Product search results
- Product detail drawer
- Full product detail page
- AI Assistant
- Tool result cards
- Export actions
- Notifications or previews

The prototype should demonstrate UI restrictions, but must not claim these replace backend authorization.

## 4. Complete Workflow A Interactions

Verify the following flows:

1. Search by SKU, OE, and mock VIN.
2. Search an unknown SKU: BP-9999X.
3. Select a product result.
4. Open the product detail drawer.
5. Navigate to the full product detail page.
6. Navigate back while preserving search context.
7. Change mock ERP connection states.
8. Change simulated permission states.
9. Open and close the AI Assistant.
10. Display loading, empty, timeout, unavailable, and unauthorized states.
11. Check keyboard and focus behavior.
12. Check responsive layouts at 1440px, 1280px, and 1024px.

Do not claim a test passed unless the interaction was actually verified.

## 5. Quotation Placeholder

The "Thêm vào báo giá" action must remain clearly identified as a future workflow interaction until Workflow C is designed.

Do not simulate completed quotation persistence.

Do not invent quotation approval rules, pricing rules, or discount permissions.

## 6. Update Documentation

Update the existing project-level design documentation:

- Screen Inventory
- Screen State Matrix
- Interaction Specifications
- Permission Dependencies
- Design Decision Log
- Open Business Questions
- Workflow A Review Report

Do not create redundant documentation if equivalent files already exist.

Clearly distinguish:
- Designed
- Reviewed
- Approved
- Blocked

## 7. Required Final Report

Report:

A. What was corrected

B. Which actual screens/components were modified

C. Which prototype interactions were verified

D. Any remaining visual or functional errors

E. Unresolved business dependencies

F. Screenshots or review evidence where available

G. Workflow A readiness for the next design batch

Do not claim that backend authorization, ERP integration, or real transactions have been tested.

Do not modify repository code outside the design artifact.

Do not proceed automatically to Workflow B or C.

Stop and present the completed Workflow A for my review.

Respond in Vietnamese.