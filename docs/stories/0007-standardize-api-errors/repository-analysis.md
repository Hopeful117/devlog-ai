# Repository Analysis

## Story Understanding

Story 0007 requests a correction and extension of DevLog's HTTP error boundary.

The immediate defect is incorrect classification of framework-level request failures. An unmapped route currently becomes `500 Internal Server Error` instead of `404 Not Found`, and an unsupported HTTP method becomes `500` instead of `405 Method Not Allowed`. The Story also requires known conflicts, especially duplicate project slugs, to retain their business semantics rather than falling through to the unexpected-error handler.

The engineering objective is to make error responses deterministic for API consumers such as Kiko. The existing common response should gain a stable machine-readable code and the effective correlation identifier while keeping human-readable messages and internal diagnostics separate.

The Story includes explicit mappings for established HTTP and business failures, extension of the common error representation, correlation consistency, compatibility of the specialized AI task conflict response, and end-to-end Spring MVC tests.

It excludes a migration to RFC 9457 `ProblemDetail`, a redesign of every exception hierarchy, blanket conversion of `IllegalArgumentException` to a client error, successful-response changes, DevLog context-engine changes, Engineering-Skills changes, persistence work, and a general observability redesign.

## Repository Summary

The affected implementation is contained primarily in the Java Core backend.

Controllers are thin HTTP adapters governed by `docs/controller.md`. They validate inputs, return `ResponseEntity`, delegate to services and allow exceptions to propagate to the shared `GlobalExceptionHandler`. Services remain HTTP-agnostic and express failure through exceptions.

`GlobalExceptionHandler` is a `@RestControllerAdvice` that translates selected application and Spring MVC exceptions into `ApiErrorResponse`. It currently handles AI task conflicts, unsupported analysis types, typed-parameter mismatch, malformed JSON, invalid AI results, missing entities, generic conflicts and Bean Validation failures. Its final `Exception` handler logs the exception with its stack trace and returns a neutral `500` response.

`CorrelationIdFilter` runs at the highest filter precedence. It accepts a safe `X-Correlation-ID` or generates a UUID, stores the effective value in MDC during request processing and adds it to every response header. The production logging policy requires the effective identifier on every response and classifies expected 4xx failures as warnings and unexpected 5xx failures as errors.

The Angular frontend centralizes HTTP failure conversion in `frontend/src/app/core/http/request-error.ts`. It currently reads only the optional backend `message` and branches primarily on HTTP status. Adding JSON fields is compatible with this consumer, but changing existing status or message semantics could affect its behavior. It also currently derives some specialized error kinds by searching message text, showing why stable codes are useful, although migrating frontend classification to codes is not required to correct the backend defect unless planning includes the smallest compatible adaptation.

No database entity, repository, migration or AI Engine contract is involved.

## Affected Modules

### Shared HTTP exception boundary

Relevant components:

* `backend/src/main/java/com/hopeful117/devlogai/shared/exception/handler/GlobalExceptionHandler.java`;
* `backend/src/main/java/com/hopeful117/devlogai/shared/response/ApiErrorResponse.java`.

This module owns translation from Java and Spring MVC exceptions to public HTTP status, safe message and response body. It is the direct source of the observed misclassification because all unhandled framework exceptions reach the generic `Exception` handler.

### Correlation logging boundary

Relevant component:

* `backend/src/main/java/com/hopeful117/devlogai/shared/logging/CorrelationIdFilter.java`.

The filter owns correlation identifier validation, generation, MDC lifecycle and response headers. The error handler needs access to this exact effective value. The current filter exposes it through MDC while the downstream chain executes but does not store it as an explicit request attribute.

### Project conflict behavior

Relevant components:

* `backend/src/main/java/com/hopeful117/devlogai/project/exception/ProjectSlugAlreadyExistsException.java`;
* `backend/src/main/java/com/hopeful117/devlogai/project/service/ProjectServiceImpl.java`;
* `backend/src/main/java/com/hopeful117/devlogai/project/controller/ProjectController.java`.

`ProjectServiceImpl.create` deliberately throws `ProjectSlugAlreadyExistsException` when the generated slug exists. The exception extends `RuntimeException` directly and has no handler, so it currently reaches the generic `500` path instead of the required `409 Conflict` response.

### Specialized AI task conflict response

Relevant components:

* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/AiTaskConflictResponse.java`;
* the `AiTaskResultConflictException` branch in `GlobalExceptionHandler`.

This response already contains a stable `code` plus domain-specific `currentStatus`, but it lacks the correlation identifier that the common contract will require. Its additional domain field is legitimate and should remain compatible rather than forcing all errors into an identical DTO.

### Backend tests

Relevant components:

* `backend/src/test/java/com/hopeful117/devlogai/shared/exception/handler/GlobalExceptionHandlerTest.java`;
* `backend/src/test/java/com/hopeful117/devlogai/shared/logging/CorrelationIdFilterTest.java`;
* `backend/src/test/java/com/hopeful117/devlogai/shared/controller/ControllerWebMvcTestSupport.java`;
* `backend/src/test/java/com/hopeful117/devlogai/project/controller/ProjectControllerWebMvcTest.java`;
* `backend/src/test/java/com/hopeful117/devlogai/project/service/ProjectServiceTest.java`;
* other controller tests constructing or asserting the current error DTO.

The handler unit test invokes advice methods directly. Controller tests use standalone MockMvc with the advice, which exercises controller exception translation but does not automatically reproduce the complete registered-filter runtime. A focused MVC or application-level test is required to validate the effective correlation header/body relationship and framework exceptions through their real resolver path.

### Angular request error adapter

Relevant component:

* `frontend/src/app/core/http/request-error.ts` and its tests.

The consumer reads `message` and status only. Additive fields do not require an immediate behavior change. Planning should decide whether merely documenting compatibility is sufficient or whether using stable codes for existing message-derived classifications is justified within the Story's minimal scope.

## Existing Implementation

### Existing correct behavior

The live backend returned the following correct responses:

* invalid `projectId` UUID on the Engineering Story Context endpoint → `400 Bad Request` with `Invalid value for parameter 'projectId'.`;
* valid but unknown project UUID → `404 Not Found` with an explicit project-not-found message.

The handler also explicitly implements:

* malformed JSON → `400`;
* Bean Validation failure → `400`;
* `ConflictException` → `409`;
* unsupported analysis type → `422`;
* unexpected exception → safe `500` response with detailed server-side logging.

`ApiErrorResponse` currently contains `timestamp`, `status`, `error`, `message` and `path`. `AiTaskConflictResponse` contains those conceptual fields plus `code` and `currentStatus`.

`CorrelationIdFilter` validates caller-provided identifiers against the documented character and length policy, otherwise generates a UUID. It writes the effective identifier to MDC and the `X-Correlation-ID` response header, then clears MDC after request processing.

### Missing or incorrect behavior

Live read-only requests demonstrated:

```text
GET /api/projects
→ 500 Internal Server Error

GET /api/v1/not-a-route
→ 500 Internal Server Error

DELETE /api/v1/projects
→ 500 Internal Server Error
```

Backend logs identified the missing-route exception as Spring MVC `NoResourceFoundException`. Neither it nor `HttpRequestMethodNotSupportedException` has a dedicated handler, so both reach `handleUnexpected`.

`ProjectSlugAlreadyExistsException` is tested at service level but is not translated at the HTTP boundary. `BusinessException` is currently unused. `AIEngineCommunicationException`, Git exceptions and collection exceptions exist, but repository inspection does not establish that all cross a synchronous controller boundary or share one public status. They should not be mapped speculatively in this Story.

No stable code exists in `ApiErrorResponse`, and neither common nor specialized error bodies contain the effective request correlation identifier.

### Behavior that must remain unchanged

* Unexpected failures must remain `500`, use a neutral public message and retain detailed server-side logging.
* Existing entity-not-found, validation, malformed-request and known-conflict statuses must remain semantically correct.
* Controllers must remain free of exception-catching and ad hoc response construction.
* Services must remain HTTP-agnostic.
* The response header must continue to expose the effective correlation identifier.
* The frontend must continue to receive the existing `message` and status fields.
* Successful API responses and the Engineering Story Context contract must not change.

## Relevant Documentation

* `README.md` — Java Core API ownership, runtime versions and correlation logging capability.
* `docs/controller.md` — controller, service and global exception-handler responsibility boundaries.
* `docs/logging-policy.md` — correlation identifier validation, propagation, response-header and log-level requirements.
* `docs/decisions/ADR-025.md` — stable machine-readable warning codes and separation of diagnostics from internal logs; relevant precedent for stable codes and non-exposure of stack traces.
* Story 0007 definition.
* Engineering Story Repository Analysis workflow instructions.

No accepted ADR specifically fixes the public API error representation. The Story extends an existing shared HTTP contract without changing the system's architecture, so no new ADR is required.

## Constraints

* Use one shared public error boundary; do not introduce a second competing error format.
* Preserve the controller/service separation defined by `docs/controller.md`.
* Use explicit exception classification. Do not map all `RuntimeException` or `IllegalArgumentException` instances to client errors.
* Preserve safe public messages and keep stack traces, exception types and sensitive details in server logs only.
* The correlation identifier in the body must equal the effective value in the response header and MDC; generating another identifier in the handler would violate traceability.
* Preserve existing `ApiErrorResponse` fields for frontend compatibility.
* Preserve the domain-specific `currentStatus` information of AI task conflicts.
* Unknown routes must be distinguished from unknown domain entities even if both use HTTP 404.
* Expected 4xx failures should flow through the existing logging policy as warnings, not be mislabeled as server errors.
* The implementation must work with Java 21, Spring Boot 4.1 and the current Spring MVC exception model.
* Tests must exercise the dispatcher/advice path and, for correlation consistency, the filter path.
* No persistence or database migration is justified.

## Risks

### Correlation source coupling

Reading MDC directly in the handler would work within the current synchronous servlet request, but it couples the public response contract to logging context. Storing the effective identifier as a request attribute would make ownership more explicit but changes the filter/handler contract. Planning must choose the smallest reliable approach and test it through the full request chain.

### Incorrect broad exception mapping

Several `IllegalArgumentException` instances represent internal invariant violations, security checks or programmer errors. A broad `400` mapping would hide genuine backend defects and weaken operational signals.

### Framework exception coverage

Testing only direct advice methods can miss Spring resolver behavior. Missing-resource and unsupported-method tests need a configuration that actually causes Spring MVC to raise the same exceptions seen in the running application.

### Consumer behavior

Adding JSON fields is compatible with the current Angular structural cast, but changing existing message or status semantics could alter UI classification. The current frontend also derives three error kinds from message substrings; this remains fragile even after codes are introduced unless a later or tightly scoped adaptation uses them.

### Error-code stability

Once exposed, codes become public API vocabulary. Codes should describe stable failure semantics rather than Java exception names so implementation refactoring does not break consumers.

## Open Questions

None.

The representation of stable codes and the exact mechanism for exposing the effective correlation identifier are bounded implementation decisions that can be resolved during Implementation Planning without changing Story scope.

## Recommendation

Ready for planning

The defect is reproduced, ownership is clear, the existing shared error infrastructure is reusable, and no architectural or data prerequisite is missing. The Story can be implemented without changing DevLog's successful API contracts, database or Engineering-Skills integration.

## Implementation Readiness

The repository contains all required contracts and test infrastructure:

* a centralized `GlobalExceptionHandler`;
* an extensible `ApiErrorResponse`;
* a correlation filter and documented correlation policy;
* typed exceptions for known entity and conflict cases;
* standalone MockMvc support and application test infrastructure;
* a tolerant frontend error adapter.

No missing ownership, data, dependency, migration or ADR blocks planning. Implementation Planning must define the stable code vocabulary, the exact shared response-construction mechanism, the filter-to-handler correlation contract and the minimum end-to-end MVC test configuration.

## Approval Required

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
