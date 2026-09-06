# OPTISERVE Backend Implementation Plan

## Phase 0 — Foundation and Local Runtime

Confirm the PostgreSQL connection approach and supply environment-based datasource configuration. Establish Flyway baseline migrations and test database support. Define the common API error shape, global exception handling, validation conventions, transaction boundaries, and security baseline before feature endpoints are added.

## Phase 1 — Core Domain and Persistence

Model service requests, service types, resources, compatibility, assignments, queue entries, and service lifecycle states. Add versioned Flyway migrations, JPA repositories, and DTO mappings. Preserve a clear module boundary between domain rules, application services, and REST adapters.

## Phase 2 — Service and Resource APIs

Implement validated REST APIs for managing service types, compatible resources, and service requests. Do not expose entities. Cover authorization decisions, invalid state transitions, and persistence behavior with unit and integration tests.

## Phase 3 — Queue Scheduler

Implement a transactional scheduler that recalculates on enqueue, cancellation, completion, resource availability, priority changes, and compatibility changes. Rank requests by priority plus aging, limit assignment to compatible available resources, and use predicted duration as a scheduling input. Critical requests take the next compatible resource without interrupting active work. Calculate waiting time dynamically from current assignments and queue state.

## Phase 4 — Operations APIs and Observability

Add queue, assignment, and operational-status endpoints with explicit response DTOs. Publish or record important queue events, add audit-friendly state transitions, and expose safe operational metrics. Add integration coverage for scheduler recalculation and concurrency-sensitive flows.

## Phase 5 — ML Integration and Hardening

Integrate the separate FastAPI prediction service behind a resilient client with timeouts, fallbacks, and contract tests. Treat predictions as non-authoritative estimates. Complete performance, security, migration, and end-to-end tests; document deployment configuration and operational runbooks.
