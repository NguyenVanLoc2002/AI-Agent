# ADR-011 Conversation history: policy-safe summary
- **Status:** PROPOSED (sửa sau review 2026-11-13)
- **Context:** SOURCE FACT rule 30 §2. security-auditor: giá đã render quay lại qua replay/summary là đường rò Critical; tin nhắn nhân viên (có thể chứa giá) có thể vào history sau khi bot được mở khóa; gọi LLM để tóm tắt cũng là egress.
- **Decision:** history gửi model = summary deterministic dựng từ structured state (sản phẩm đã xác minh, handle, câu hỏi mở, N lượt **khách** đã sanitize gần nhất). Loại trừ: assistant text nguyên văn, quote card, mọi tin direction STAFF. Không dùng LLM tóm tắt trong PoC.
- **Evidence required:** T-SEC01-HISTORY, T-SEC01-STAFF. NOT RUN.
