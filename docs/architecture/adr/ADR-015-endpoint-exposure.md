# ADR-015 Phơi bày endpoint demo / inspector / ops
- **Status:** PROPOSED
- **Context:** BRD §12 (SOURCE FACT): endpoint admin/security phải RBAC + redaction, không public debug payload. architecture-reviewer: `/api/demo/**` cần gate theo profile.
- **Decision:** `/api/demo/**` chỉ đăng ký ở profile `local`/`demo`; `/api/security/**` chỉ SECREV; `/api/ops/**` chỉ OPERATOR; actuator chỉ `health`.
- **Evidence required:** test profile (endpoint không tồn tại ở profile khác), T-UC07-RBAC. NOT RUN.
