# ADR-010 Identity / session cho demo & RBAC
- **Status:** PROPOSED · **Open:** A-06, D-08
- **Context:** SOURCE FACT: danh tính từ server; rule 50: không bearer token trong localStorage nếu chưa duyệt.
- **Decision:** PoC dùng Spring Security form login với user seed synthetic; session cookie HttpOnly + Secure + SameSite=Lax; CSRF token; regenerate session khi login; 4 vai trò (`docs/security/RBAC_MATRIX.md`). Không SSO; khách chưa liên kết ngoài PoC.
- **Evidence required:** T-AUTH-01, T-RBAC-MATRIX. NOT RUN.
