# ADR-010 Identity / session cho demo & RBAC
- **Status:** ACCEPTED (2026-11-18, project owner; A-06 APPROVED, UX-12 một phần) · **Open:** D-08, phần còn lại của UX-12
- **Context:** SOURCE FACT: danh tính từ server; rule 50: không bearer token trong localStorage nếu chưa duyệt.
- **Decision:** PoC dùng Spring Security form login với user seed synthetic; session cookie HttpOnly + Secure + SameSite=Lax; CSRF token; regenerate session khi login; 4 vai trò (`docs/security/RBAC_MATRIX.md`). Không SSO; khách chưa liên kết ngoài PoC.
- **Quy tắc phiên (owner 2026-11-18, lấy từ UX-12):** hết hạn sau 30 phút không hoạt động và tối đa 8 giờ kể từ khi đăng nhập; hết phiên → 401 với mã đã sanitize, client quay lại trang đăng nhập. **Chưa làm (issue sau):** khóa tài khoản khi sai mật khẩu nhiều lần, cảnh báo 5 phút trước khi hết phiên, kết thúc các phiên khác khi đổi mật khẩu. Vai trò Kho: D-08 OPEN.
- **Liên quan EP-04:** A-19 (identity bọc kết quả liên kết opaque thành `AuthenticatedCustomerRef`), A-21/A-29 (`conversation` gọi sanitizer và nhận VIN lúc nhận tin) — APPROVED 2026-11-18, xem `docs/DECISIONS_REQUIRED.md`.
- **Evidence required:** T-AUTH-01 (session fixation, CSRF, cookie flags), T-AUTH-IDLE / T-AUTH-MAX (hết phiên sau 30 phút không hoạt động / 8 giờ), T-RBAC-MATRIX. NOT RUN.
