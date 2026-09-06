# OPTISERVE Domain Rules

## Core Model

- A **ServiceRequest** represents a customer request for a service.
- A **ServiceType** defines the type of service and its characteristics.
- A **Resource** represents a service counter/resource and has compatible service types.
- An **Assignment** maps a request to a resource and represents actual service execution.
- A **QueueEntry** represents a waiting request.
- A request has an explicit lifecycle state.

## Priority and Critical Requests

Priority classes are `CRITICAL`, `URGENT`, `APPOINTMENT`, and `NORMAL`.

A `CRITICAL` request receives the next available compatible resource and never interrupts an in-progress service. If every compatible resource is busy, it waits for the earliest compatible resource to become available. Multiple critical requests remain subject to compatible-resource availability.

## Fairness and Compatibility

Waiting-time aging prevents starvation: long-waiting `NORMAL` and low-priority requests gradually gain effective priority. Aging does not demote `CRITICAL` requests.

A request may only be assigned to a compatible resource. If none is available, it remains waiting. A resource becoming available triggers queue recalculation.

## Dynamic Waiting Time

Waiting time is an estimate, not a guarantee, and is recalculated after relevant state changes: new or critical request arrival, cancellation, no-show, appointment arrival, resource availability or failure, service start/completion, and service-duration updates.

For one compatible resource, the estimate is approximately the current assignment's remaining time plus predicted or known durations of eligible preceding requests. For multiple resources, it depends on the earliest compatible-resource availability and requests assigned first.

## ML and Service Duration

ML predicts expected **service duration**, not authoritative waiting time. The scheduler uses the prediction as an input. If ML is unavailable, use a deterministic fallback duration. Every prediction includes its model version and timestamp.

Actual service may finish earlier or later than predicted. Duration changes must update downstream waiting estimates.

## Edge Cases

Support critical arrivals when all resources are busy or a compatible resource is free; multiple critical requests; long-waiting normal requests; resource failure and availability; longer or shorter services; cancellation; no-show; late appointments; specific capability requirements; no available compatible resource; and concurrent attempts to claim the same request or resource.

## State Safety

- A request cannot be assigned twice.
- A resource cannot serve two active requests simultaneously.
- Invalid state transitions must be rejected.
- Critical requests do not cancel or terminate active assignments.

Do not introduce additional business rules unless they are documented here.
