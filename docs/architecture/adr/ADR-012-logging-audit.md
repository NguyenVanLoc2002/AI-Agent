# ADR-012 Logging, audit & observability
- **Status:** ACCEPTED (2026-11-18, project owner) · **Open:** D-09 (retention)
- **Triển khai:** SEC-01a (2026-11-18) tạo bảng `egress_record` (Flyway V2, cột `attempt` ở V3), `audit.EgressDecisionEvent` và port đồng bộ `audit.EgressAuditLog` (một dòng PASS cho mỗi lần gửi, kể cả retry); phần còn lại (audit_event, inspector, log pipeline SEC-05) thuộc EP-09.
- **Context:** SOURCE FACT rule 10, BRD §10 NFR-OBS. security-auditor: HMAC theo từng giá trị cho phép so sánh giá trị giữa các khách và brute-force số entropy thấp.
- **Decision:** log JSON có cấu trúc chỉ gồm event ID, traceId pseudonymous, policy code, field names, status; cấm body và exception cause thô (error mapper trả code). Audit (chủ sở hữu `egress_record`) lưu field names, decision, policy code, honeytoken ID, length — **không** hash/HMAC theo giá trị. APM tắt trong PoC. Retention synthetic ngắn.
- **Evidence required:** T-SEC05, T-SEC05-INSPECTOR. NOT RUN.
