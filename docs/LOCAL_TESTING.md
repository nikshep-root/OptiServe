# Local backend integration tests

The backend verification suite uses PostgreSQL and Flyway. On Windows, run it against an isolated PostgreSQL 16 container instead of pointing it at a development database.

## Prerequisites

- Docker Desktop installed and running
- Java 21 available to the Maven Wrapper
- PowerShell 5.1 or later

From `optiserve-backend`, run:

```powershell
.\scripts\verify-with-test-postgres.ps1
```

The script checks Docker and the selected host port, then starts a uniquely named PostgreSQL 16 container with a dedicated `optiserve_test` database and randomly generated credentials. The default host port is `5433`; select another free port with `-Port 5434`. It waits for PostgreSQL, sets the datasource and JWT environment variables plus `JAVA_TOOL_OPTIONS=-Duser.timezone=Asia/Kolkata`, and runs `mvnw.cmd -B clean verify`. It restores the caller's environment variables and removes only the container ID created by that run, including when setup or Maven fails. It does not connect to or alter another PostgreSQL container or database.

GitHub Actions uses its own PostgreSQL 16 service container on port `5432` and runs the Maven wrapper in the CI job. The local script provides the same database major version while keeping its disposable database separate from a developer's existing database.
