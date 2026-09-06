# Repository Guidelines

## Platform and Architecture

OPTISERVE is a Java 21, Spring Boot, Maven, PostgreSQL, and Flyway application. The backend is a modular monolith; do not split its core functionality into microservices. Organize code by business module and preserve this dependency flow:

`Controller → Application Service → Domain/Business Logic → Repository`

Use DTOs at REST boundaries. Controllers must not expose JPA entities directly. The future ML integration is a separate FastAPI service. Its service-duration prediction is non-authoritative; the backend scheduler calculates authoritative waiting-time estimates.

## Project Layout and Commands

The application is in `optiserve-backend/`. Java sources use `src/main/java/com/minor_project/optiserve_backend/`; tests mirror them in `src/test/java/`. Place configuration in `src/main/resources/` and Flyway migrations in `src/main/resources/db/migration/` (for example, `V1__create_resources.sql`).

Run from `optiserve-backend/`:

- `mvnw.cmd test` — compile and run tests.
- `mvnw.cmd clean package` — run tests and build the JAR.
- `mvnw.cmd spring-boot:run` — start locally.

## Engineering Rules

Use Bean Validation for API inputs and a global exception handler for consistent error responses. Define explicit transaction boundaries for state-changing workflows. Use four-space Java indentation, `PascalCase` types, `camelCase` members, and role-specific names such as `QueueController` and `QueueApplicationService`.

Write unit tests for domain logic and integration tests for REST, persistence, security, and migrations. Important business rules require tests; no feature may be implemented without tests. Do not add a dependency unless its purpose is justified and no existing dependency covers it.

## Configuration and Review

Keep PostgreSQL credentials and secrets outside version control. Pull requests must describe the change, list validation performed, and explicitly call out API, Flyway migration, configuration, or scheduling-rule effects. Use focused imperative commits, such as `Add queue aging policy`.
