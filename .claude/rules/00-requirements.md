# Requirements, change control and evidence
- Every implementation references a use-case ID, acceptance criteria and source (`docs/TRACEABILITY.md`). If source does not specify behavior, label DESIGN PROPOSAL / OPEN DECISION; do not present as original requirement.
- For new flows document actor, authentication, preconditions, happy path, alternative paths, authorization, side effects, data flows, audit events and rollback.
- Record scope expansion and API dependencies in decision log. Never silently weaken requirements to make tests pass.
- Definition of ready: actor + authorized resource, data classification, technical contract, failure behavior, acceptance examples and test fixtures.
- Definition of done: deterministic tests, threat cases, code review, no exposed sensitive data, docs updated and exact evidence from executed commands.
