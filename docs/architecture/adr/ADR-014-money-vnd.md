# ADR-014 Money / VND
- **Status:** ACCEPTED (2026-11-18, project owner; D-13 một phần) · **Open:** D-04 (giá cho khách mới chưa liên kết), VAT, đơn vị tính (D-13 phần còn lại)
- **Context:** SOURCE FACT BRD §05, rule 20: BigDecimal, quy tắc tiền tệ cấu hình; BRD §15: thuế/VAT/làm tròn chưa xác nhận. architecture-reviewer: VND thường không dùng đơn vị lẻ, nên scale 2 không được giả định.
- **Decision:** `Money(BigDecimal, Currency)`; kiểu cột NUMERIC; so sánh bằng `compareTo`; `toString()` không in số tiền. **Owner 2026-11-18:** VND, scale 0, `RoundingMode.HALF_UP`, đặt trong cấu hình (`apas.quote.money.*`), không viết cứng. Thành tiền mỗi dòng = đơn giá × số lượng, làm tròn theo dòng; tổng = tổng các dòng đã làm tròn. PoC chưa tính VAT và quote card ghi rõ "Giá chưa gồm VAT".
- **Evidence required:** T-UC03-MONEY (tham số hóa theo cấu hình scale/rounding).
