# ADR-015 Phơi bày endpoint demo / inspector / ops
- **Status:** ACCEPTED (2026-11-19, project owner, kế hoạch EP-04). Profile `local` gồm `demo` (`spring.profiles.group`).
- **Context:** BRD §12 (SOURCE FACT): endpoint admin/security phải RBAC + redaction, không public debug payload. architecture-reviewer: `/api/demo/**` cần gate theo profile.
- **Decision:** `/api/demo/**` chỉ đăng ký ở profile `local`/`demo`; `/api/security/**` chỉ SECREV; `/api/ops/**` chỉ OPERATOR; actuator chỉ `health`.
- **Evidence required:** test profile (VERIFIED 2026-11-19: `ApplicationBootstrapIT.noDemoEndpointsWithoutTheDemoProfile`), T-UC07-RBAC (NOT RUN, EP-09).
- **EP-08a (2026-11-19, owner duyệt):** `/api/internal/**` (inbox nhân viên, đối soát đơn) chỉ vai trò SALES ở mức URL trong `SecurityConfiguration`, và chỉ đăng ký ở profile demo vì người dùng duy nhất là người dùng demo. Bằng chứng: `StaffInboxIT` (ma trận vai trò × endpoint). Biến thể đường dẫn dựa vào mặc định của Spring Security, chưa có test riêng (A-42(o)).
