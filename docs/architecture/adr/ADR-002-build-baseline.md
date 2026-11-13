# ADR-002 Build tool & baseline
- **Status:** PROPOSED · **Open:** A-04
- **Context:** SOURCE FACT (CLAUDE.md): Java 21, Spring Boot, Postgres/Flyway, JUnit 5, Testcontainers là preferred; "detect actual build before changing" — repo chưa có build.
- **Decision (đề xuất):** Gradle Kotlin DSL, một project `backend/` (module nghiệp vụ là package để Modulith kiểm); Spring Boot 3.x bản GA ổn định tại thời điểm EP-00 (pin phiên bản chính xác); task `integrationTest` tách cho Testcontainers; `frontend/` sau (EP-10).
- **Alternatives:** Maven (tương đương chức năng) — chờ chọn.
- **Consequences:** cần Docker local cho Testcontainers (`.claude/settings.json` đang `ask` cho docker compose).
- **Evidence required:** build sạch, `./gradlew test` exit 0 (EP-00). NOT RUN.
- **Update 2026-11-13 (POC-001):** Theo yêu cầu POC-001 của owner, build tool là **Maven** (Maven Wrapper 3.9.11), không dùng Gradle như đề xuất ban đầu. Spring Boot **4.1.2**, bản GA mới nhất trên Maven Central ngày 2026-11-13; dòng 3.x không được dùng. Đi kèm Spring Modulith 2.1.2, ArchUnit 1.5.1 và Testcontainers do Boot quản lý phiên bản. Một project `backend/`. Test tích hợp tách bằng failsafe (`*IT`). ADR vẫn ở trạng thái PROPOSED cho tới khi owner ký.
- **Evidence (POC-001):** `./mvnw -B clean verify` exit 0, 77 unit + 6 IT (2026-11-13, Java 21.0.12, Docker 28.3.0). VERIFIED cho phạm vi skeleton.
