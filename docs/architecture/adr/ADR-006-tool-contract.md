# ADR-006 Tool contract & per-call authorization
- **Status:** PROPOSED (sửa sau review 2026-11-13) · **Open:** D-10 (giới hạn), A-14
- **Context:** SOURCE FACT CLAUDE.md, rule 10/30, skill `agent-tool-contract`. security-auditor: lý do từ chối chi tiết trong `requestQuote` tạo credit/stock oracle; tool `validateVin` buộc VIN đầy đủ ra provider.
- **Decision:** Registry tĩnh 3 tool (`searchVerifiedProduct`, `requestQuote`, `requestHandoff`); VIN validate server-side, không phải tool. JSON schema strict, regex cho mã; không tham số danh tính; authz + ownership + quota theo conversation và principal mỗi lần; `requestQuote` chỉ trả `ISSUED`/`NOT_QUOTED`; output DTO typed qua EgressGuard. Chi tiết `docs/contracts/TOOL_CONTRACTS.md`.
- **Consequences:** model không thể giải thích lý do không báo giá; nhân viên xử lý qua handoff (cần business chấp nhận — A-14).
- **Evidence required:** T-TOOL-01..13, SEC-03. NOT RUN.
