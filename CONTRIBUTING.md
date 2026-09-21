# Contributing to OptiServe

Thanks for contributing to OptiServe! 

OptiServe is an intelligent service-center operations platform that manages vehicle service requests, workflows, queues, resources, assignments, and service execution.

---

## Getting Started

Before making changes:
1. **Fork** the repository if you are not already a collaborator.
2. **Clone** the repository locally.
3. **Create a new branch** from the latest `main`.
4. **Make your changes**.
5. **Add or update tests** where required.
6. **Verify** that all existing tests pass.
7. **Open a Pull Request (PR)** against `main`.

---

## Branch Naming

Please use a descriptive branch name.
Examples:
* `feature/frontend-dashboard`
* `feature/service-request-ui`
* `feature/queue-management`
* `fix/wait-time-calculation`
* `docs/contributing-guide`

---

## Development Guidelines

### Backend
The backend leverages the following stack:
* Java 21
* Spring Boot
* Maven
* PostgreSQL
* Flyway
* JPA
* Spring Security

Follow the established project architecture:
```text
Controller 
    ↓ 
Application Service 
    ↓ 
Domain / Business Logic 
    ↓ 
Repository
```
* **Boundary Rules:** Use DTOs (Data Transfer Objects) at REST API boundaries. Avoid exposing JPA entities directly through public APIs.

### Frontend
* **API Consumption:** The frontend must consume the existing backend APIs instead of duplicating business logic locally.
* **Business Rules:** Rules governing scheduling, queue priority, resource compatibility, and waiting-time calculations must remain authoritative in the backend.

### Database
* **Migrations:** All database schema changes must be managed using **Flyway migrations**. 
* **Restrictions:** Do not modify the database schema manually or commit schema modifications without a corresponding migration script.

### Testing
* **Coverage:** New features must include appropriate automated tests.
* **Validation:** Run the existing test suite locally to verify everything passes before opening a Pull Request.

---

## Pull Requests

Keep Pull Requests focused on a single feature or specific bug fix. A high-quality Pull Request should detail:
* **What** was changed
* **Why** it was changed
* **How** it was tested
* **Implementation details** (any critical architectural decisions)

*Note: Avoid mixing unrelated changes inside the same Pull Request.*

### Commits
Use clear, active, and descriptive commit messages. Examples:
* `Add queue management API`
* `Add dynamic wait time calculation`
* `Add frontend dashboard`
* `Fix resource assignment concurrency`
* `Update service request validation`

### Code Review
* All Pull Requests require at least one approval before merging.
* Address all review comments proactively and keep discussions constructive and focused on the code being evaluated.

### Questions
If you are unsure about an implementation pattern, always discuss it with the team first before introducing any major architectural changes.
At last add - 


## Reporting Issues

When reporting an issue, include the steps to reproduce the problem, expected behaviour and actual behaviour whenever possible.
