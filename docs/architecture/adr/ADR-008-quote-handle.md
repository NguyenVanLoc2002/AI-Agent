# ADR-008 Opaque quote handle & server-side rendering
- **Status:** PROPOSED
- **Context:** SOURCE FACT BRD §05: LLM chỉ nhận token opaque bind conversation/customer/quoteId/version/TTL; render server-side. security-auditor: token tự mô tả có thể bị replay; khuyến nghị random + lưu server.
- **Decision:** handle = 128-bit ngẫu nhiên, lưu SHA-256 trong `quote_handle` cùng binding; resolve chỉ khi khớp conversation + customer + version + chưa hết hạn + chưa revoke. Quote card render từ DB, không qua model, không vào history. confirmationToken và handle có rate limit chống brute force; lỗi trả 404 đồng nhất. (Bucket tồn chuyển sang ADR-005.)
- **Evidence required:** T-UC03-TOKEN-REPLAY, T-UC03-RENDER, T-AUTH-02. NOT RUN.
