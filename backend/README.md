# secure-erp-agent backend (POC-001 bootstrap)

Security-first PoC backend: Java 21, Spring Boot 4.1.2, Maven, PostgreSQL 17, Flyway, JUnit 5,
Testcontainers, Spring Modulith 2.1.2, ArchUnit 1.5.1.
**Skeleton only:** no business logic, no Arito connection, no LLM provider, synthetic data only.
Design: `../docs/architecture/` (ADRs are still PROPOSED).

## Layout
- `com.secureerp.agent` (root, the "bootstrap" role) → `SecureErpAgentApplication`
- One package per module: `identity, conversation, agent, gateway, policy, tools, catalog, quote,
  order, handoff, audit, securityinspector, platform, erp`. Allowed dependencies are declared in each
  `package-info.java` and verified by `ModuleStructureTest` (Spring Modulith).
- ARCH-xx rules: `src/test/java/com/secureerp/agent/ArchitectureRules.java`. Deliberate-violation
  fixtures: `src/test/java/com/secureerp/archfixture`.
- `platform`: `ErrorCode`, `ApplicationException`, `internal/GlobalExceptionHandler` (RFC 9457,
  no messages/values/stack traces), `internal/CorrelationIdFilter` (server-generated `X-Request-Id`; client value ignored), UTC `Clock`.
- `db/migration/V1__baseline.sql`: empty baseline, no tables until a schema is approved.

## Profiles
| Profile | Use | Database |
|---|---|---|
| (none) | any non-local environment | `APP_DB_URL`, `APP_DB_USERNAME`, `APP_DB_PASSWORD` required. Without them, startup fails |
| `local` | developer machine, bound to 127.0.0.1, plain-text logs, health details shown | compose Postgres. Defaults: `jdbc:postgresql://127.0.0.1:5432/secure_erp_agent` and user `secure_erp_agent`. You must provide `APP_DB_PASSWORD` |
| tests | `*IT` classes | Testcontainers `postgres:17.12-alpine` |

Actuator exposes only `/actuator/health` (+ `/liveness`, `/readiness`).

## Build and test
```bash
./mvnw -B test          # unit + architecture tests (no Docker)
./mvnw -B clean verify  # + Testcontainers integration tests (Docker must be running)
```

## Run locally
Git Bash:
```bash
cd backend
export APP_DB_PASSWORD='<choose a local throwaway password>'
docker compose up -d --wait
# if 5432 is taken: export APP_DB_PORT=55432 APP_DB_URL=jdbc:postgresql://127.0.0.1:55432/secure_erp_agent
./mvnw -B -DskipTests package
java -jar target/secure-erp-agent-0.1.0-SNAPSHOT.jar --spring.profiles.active=local
curl http://127.0.0.1:8080/actuator/health
docker compose down        # add -v to delete the local synthetic data volume
```
PowerShell: set variables with `$env:APP_DB_PASSWORD = '...'`, and use `.\mvnw.cmd` instead of `./mvnw`.

## Not here yet
Auth/session (ADR-010), demo endpoints (ADR-015), business tables, gateway/egress guard (SEC-01a),
Mock Arito. ARCH-05/06/08 are added with SEC-01a, when their marker types exist.

## Rules for the next issues
- When adding `api`/`spi` sub-packages, annotate their `package-info.java` with `@NamedInterface`
  and reference them as `"quote::spi"` etc. in `allowedDependencies`. A plain `"quote"` only exposes
  the module's base package.
- Adding Spring Security (ADR-010) requires a test showing that `AccessDeniedException` gives a 403,
  not a 500. The handler re-throws these exceptions, matched by class name.
- `audit` depends only on `platform`, so that every other module, `identity` included, can publish audit events
  without a cycle. The actor is passed to `audit` as a pseudonymous string.
