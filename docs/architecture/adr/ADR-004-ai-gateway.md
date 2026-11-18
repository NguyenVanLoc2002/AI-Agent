# ADR-004 AI Gateway & Fake provider cho PoC
- **Status:** ACCEPTED (2026-11-18, project owner) · A-05 APPROVED (chỉ Fake/WireMock provider) · **Open:** D-07: provider thật vẫn bị cấm
- **Context:** SOURCE FACT rule 30: typed ModelRequest, pipeline sanitize, capture tại adapter, `store=false` ≠ ZDR. D-07 (region/ZDR/DPA/legal) chưa giải quyết.
- **Decision:** Một `ModelProviderPort`; chỉ `gateway.adapter` gọi ra ngoài. PoC chỉ dùng Fake provider deterministic (WireMock sau proxy local); **không** gọi provider thật tới khi D-07 có quyết định bằng văn bản. Pipeline theo `docs/contracts/AI_GATEWAY.md` §2.
- **Alternatives:** provider thật với dữ liệu synthetic ngay — loại vì D-07 và vì hành vi model không phải mục tiêu của SEC-01.
- **Consequences:** chất lượng hội thoại chưa được đánh giá trong PoC; cần issue riêng sau D-07.
- **Evidence required:** T-SEC01-*. NOT RUN.
