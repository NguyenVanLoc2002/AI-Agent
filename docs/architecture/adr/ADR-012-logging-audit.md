# ADR-012 Logging, audit & observability
- **Status:** PROPOSED (sửa sau review 2026-11-13) · **Open:** D-09 (retention)
- **Context:** SOURCE FACT rule 10, BRD §10 NFR-OBS. security-auditor: HMAC theo từng giá trị cho phép so sánh giá trị giữa các khách và brute-force số entropy thấp.
- **Decision:** log JSON có cấu trúc chỉ gồm event ID, traceId pseudonymous, policy code, field names, status; cấm body và exception cause thô (error mapper trả code). Audit (chủ sở hữu `egress_record`) lưu field names, decision, policy code, honeytoken ID, length — **không** hash/HMAC theo giá trị. APM tắt trong PoC. Retention synthetic ngắn.
- **Evidence required:** T-SEC05, T-SEC05-INSPECTOR. NOT RUN.
