# ADR-016 Egress capture & network route
- **Status:** PROPOSED
- **Context:** SOURCE FACT SECURITY_TEST_MATRIX: "independent inspect of actual outbound bytes at proxy". security-auditor: capture trước nén/retry bỏ sót rò rỉ.
- **Decision:** Capture kép: (a) interceptor HTTP client của adapter (sau nén/retry); (b) WireMock sau proxy local là route duy nhất trong test; đối chiếu số request. Production: proxy/firewall ngoài ứng dụng (ngoài phạm vi repo).
- **Consequences:** PoC chỉ chứng minh trong môi trường local; không thay thế kiểm tra mạng production.
- **Evidence required:** T-SEC01-CAPTURE, T-SEC06. NOT RUN.
