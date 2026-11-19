# ADR-008 Opaque quote handle & server-side rendering
- **Status:** ACCEPTED (2026-11-18, project owner)
- **Context:** SOURCE FACT BRD §05: LLM chỉ nhận token opaque bind conversation/customer/quoteId/version/TTL; render server-side. security-auditor: token tự mô tả có thể bị replay; khuyến nghị random + lưu server.
- **Decision:** handle = 128-bit ngẫu nhiên, lưu SHA-256 trong `quote_handle` cùng binding; resolve chỉ khi khớp conversation + customer + version + chưa hết hạn + chưa revoke. Quote card render từ DB, không qua model, không vào history. confirmationToken và handle có rate limit chống brute force; lỗi trả 404 đồng nhất. (Bucket tồn chuyển sang ADR-005.) **Owner 2026-11-18:** báo giá hiệu lực 24 giờ (cấu hình được), handle hết hạn cùng báo giá; khách chưa liên kết → không báo giá tự động (D-04). Báo giá không khẳng định phụ tùng lắp vừa xe (A-31); quote card ghi rõ.
- **Evidence required:** T-UC03-TOKEN-REPLAY, T-UC03-RENDER, T-AUTH-02 (rate limit ở HTTP: EP-04).
