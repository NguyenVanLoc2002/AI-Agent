---
paths:
  - "backend/**"
---
# Backend rules
- Keep domain services independent from LLM SDKs, transport and actual Arito API.
- Use BigDecimal + explicit currency/rounding for monetary calculations.
- Controller -> application service -> port -> adapter; avoid business logic in controllers.
- Request DTO validation, pagination, non-leaking errors and explicit states are mandatory.
- Database changes via Flyway; avoid silent auto-update and unbounded queries.
- Mock Arito and real connector implement the same domain-focused interface.
- Integration tests should include concurrency and failure recovery.
