# Controller Guidelines

## Purpose

Controllers are responsible for the HTTP layer only.

Their role is to expose the application's capabilities through a REST API while delegating all business logic to the service layer.

A controller should never contain business rules.

---

## Responsibilities

Controllers are responsible for:

- Mapping HTTP requests to service calls.
- Validating incoming request payloads (`@Valid`).
- Returning the appropriate HTTP status codes.
- Building `ResponseEntity` responses.
- Setting HTTP headers when necessary (e.g. `Location`, caching, ETags).
- Delegating all business operations to the corresponding service.

Controllers must **not**:

- Access repositories directly.
- Contain business logic.
- Perform persistence operations.
- Catch business exceptions (handled globally by `GlobalExceptionHandler`).

---

## Response Policy

All controller methods **must** return a `ResponseEntity`.

Examples:

| Operation | Response |
|----------|----------|
| Create | `ResponseEntity.created(...).body(...)` |
| Read | `ResponseEntity.ok(...)` |
| Update | `ResponseEntity.ok(...)` |
| Archive | `ResponseEntity.noContent().build()` |

Returning DTOs directly is discouraged.

Using `ResponseEntity` ensures that HTTP concerns remain inside the controller and allows future additions such as custom headers, caching strategies, and API versioning without changing the service layer.

---

## Service Boundary

Services are HTTP-agnostic.

Services:

- return DTOs or domain objects;
- throw business exceptions;
- never know about `ResponseEntity`;
- never manipulate HTTP status codes.

This separation keeps the business layer reusable for REST APIs, CLI commands, scheduled jobs, or future integrations.

---

## Validation

Request validation belongs to the controller layer.

Example:

```java
@PostMapping
public ResponseEntity<ProjectResponse> create(
        @Valid @RequestBody CreateProjectRequest request) {

    return ResponseEntity
            .created(...)
            .body(projectService.create(request));
}
```

---

## Exception Handling

Controllers do not catch business exceptions.

Exceptions propagate naturally to the global exception handling mechanism.

Example:

```text
Controller
    │
    ▼
Service
    │
    ▼
EntityNotFoundException
    │
    ▼
GlobalExceptionHandler
    │
    ▼
HTTP 404 Response
```

---

## Design Principle

Controllers manage HTTP.

Services manage business rules.

Repositories manage persistence.

Each layer has a single responsibility.

## Resource Filtering

When retrieving resources filtered by another domain entity, prefer explicit resource-based paths.

Example:

GET /api/v1/knowledge-events/project/{projectId}

instead of:

GET /api/v1/projects/{projectId}/knowledge-events

Reason:

- improves readability;
- keeps the resource being queried visible;
- avoids deeply nested routes;
- simplifies future evolution of domains.