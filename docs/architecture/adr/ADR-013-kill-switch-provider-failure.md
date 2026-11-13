# ADR-013 Kill switch & provider failure
- **Status:** PROPOSED (sửa sau review 2026-11-13)
- **Context:** SOURCE FACT SEC-06 (≤1 phút, chuyển nhân viên, không gọi provider), SEC-07 (key revoked, fallback an toàn), FR-012.
- **Decision:** `feature_switch` trong DB + cache TTL ≤30s; kiểm tra trước **mỗi** provider call và hủy stream đang chạy; OFF → handoff `AI_DISABLED`; không đọc được trạng thái switch (DB lỗi) → coi như OFF (fail-closed). 401/403 → breaker mở, handoff `PROVIDER_AUTH`; không transition quote/order sau thời điểm lỗi. Key chỉ ở gateway, nạp từ secret store/env, không trong repo. Thu hồi key thật trong secret store không được kiểm thử trong PoC.
- **Evidence required:** T-SEC06 (đếm request tại proxy, gồm biến thể DB lỗi), T-SEC07. NOT RUN.
