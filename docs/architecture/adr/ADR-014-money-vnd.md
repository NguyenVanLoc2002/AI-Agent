# ADR-014 Money / VND
- **Status:** PROPOSED (sửa sau review 2026-11-13) · **Open:** D-04, D-13 (scale, làm tròn, VAT, đơn vị)
- **Context:** SOURCE FACT BRD §05, rule 20: BigDecimal, quy tắc tiền tệ cấu hình; BRD §15: thuế/VAT/làm tròn chưa xác nhận. architecture-reviewer: VND thường không dùng đơn vị lẻ, nên scale 2 không được giả định.
- **Decision (tạm):** `Money(BigDecimal, Currency)`; kiểu cột NUMERIC; **scale và RoundingMode lấy từ cấu hình**, chưa chốt (D-13); so sánh bằng `compareTo`; PoC chưa tính VAT và hiển thị rõ "chưa gồm VAT".
- **Evidence required:** T-UC03-MONEY (tham số hóa theo cấu hình scale/rounding). NOT RUN.
