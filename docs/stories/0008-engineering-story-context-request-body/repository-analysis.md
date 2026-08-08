# Repository Analysis

## Story Understanding

Story 0008 requests an additive HTTP transport for Engineering Story Context:

```text
POST /api/projects/{projectId}/engineering-story-context
Content-Type: application/json

{"description":"<complete Engineering Story>"}
```

The engineering problem is not Repository Context quality. The existing GET operation places the complete Story in a query parameter; a real 11.5 KiB Story becomes an oversized request target and is rejected by Tomcat before Spring MVC can invoke the controller. A short description succeeds through the same project mapping and context pipeline.

The required behavior is therefore to accept the Story through a JSON body, pass the exact description to the existing application service, and return the existing `EngineeringStoryContext`. The current GET operation must remain compatible.

The Story explicitly excludes changes to Repository Context collection/ranking, Tomcat header limits, Story truncation or summarization, persistence, project resolution, local ports, and the external Engineering-Skills adapter. The adapter must adopt POST in a separate Engineering-Skills-owned change before Kiko's full end-to-end workflow is unblocked.

Repository Analysis was intentionally performed from the current repository without DevLog context, as explicitly authorized after the oversized-GET failure was isolated.

## Repository Summary

The affected runtime is the Java 21 / Spring Boot Core backend. The relevant API is a thin Spring MVC controller in the `projectcontext` package. It delegates to an application service which builds the project snapshot and asks `RepositoryContextAdapter` for the deterministic, ranked Repository Context.

The transport boundary and context-building pipeline are already separated. This makes the change additive: a second controller method can adapt JSON input to the existing `buildWithRepositoryContext(UUID, String)` contract without changing the service, context engine, collectors, persistence, or response model.

DevLog's shared HTTP error boundary is `GlobalExceptionHandler`. Story 0007 established `ApiErrorResponse`, stable `ApiErrorCode` values, and correlation IDs for malformed JSON, Bean Validation, unsupported methods, missing routes, missing entities, conflicts, and unexpected failures.

## Affected Modules

### Backend — `com.hopeful117.devlogai.projectcontext`

This package owns Engineering Story Context assembly and exposure.

Relevant components:

* `EngineeringStoryContextController` currently exposes only GET and passes the optional query parameter directly to the service.
* `EngineeringStoryContextService` already exposes `buildWithRepositoryContext(UUID, String)`.
* `EngineeringStoryContextServiceImpl` builds the project snapshot and Repository Context and returns the response.
* `EngineeringStoryContext` is the existing immutable response record.
* `RepositoryContextAdapter` converts the supplied description into the intent objective and optional user guidance.

The controller and a new transport request type are directly affected. The service and response components are dependencies whose behavior should remain unchanged.

### Backend shared HTTP boundary — `com.hopeful117.devlogai.shared`

`GlobalExceptionHandler`, `ApiErrorResponse`, `ApiErrorCode`, and `CorrelationIdFilter` own standardized HTTP failures and traceability.

Malformed or missing JSON is already handled as `MALFORMED_REQUEST`. Bean Validation is already handled as `VALIDATION_FAILED`. However, `HttpMediaTypeNotSupportedException` has no explicit handler and would currently fall through the generic exception handler, risking an incorrect `500 INTERNAL_ERROR` for unsupported POST content types. This directly affects AC-5 and AC-6 and must be addressed or explicitly proven to retain correct semantics through framework handling.

### Backend tests

The `projectcontext` package currently has service and adapter unit tests but no dedicated MVC test for `EngineeringStoryContextController`.

`ControllerWebMvcTestSupport` supplies a reusable standalone MockMvc configuration with the real `GlobalExceptionHandler` and `CorrelationIdFilter`. `ApiErrorHandlingWebMvcTest` demonstrates the expected end-to-end MVC style and shared error assertions.

### Documentation

`README.md` describes the Core API and Swagger/OpenAPI discovery but does not currently document this specialized endpoint. Current Story/API documentation must expose the new POST contract without rewriting historical reports.

## Existing Implementation

### Existing behavior

`EngineeringStoryContextController` exposes:

```text
GET /api/projects/{projectId}/engineering-story-context
```

Its optional `description` query parameter is passed unchanged to:

```text
EngineeringStoryContextService.buildWithRepositoryContext(projectId, description)
```

The service first calls `ProjectContextProvider.build(projectId)`, then `RepositoryContextAdapter.buildRepositoryContext(projectId, storyDescription)`, and returns `EngineeringStoryContext` containing the project snapshot, generation time, project ID and Repository Context.

`RepositoryContextAdapter` already defines null/blank semantics. A null or blank description produces the fallback objective `Engineering Story preparation` and no `UserGuidance`; a non-blank description becomes both the intent objective and guidance focus. Preserving these semantics means the POST request DTO does not need to reject null or blank descriptions merely because transport changed.

Story 0007's `GlobalExceptionHandler` already translates `HttpMessageNotReadableException` to HTTP 400 / `MALFORMED_REQUEST` and `MethodArgumentNotValidException` to HTTP 400 / `VALIDATION_FAILED`, with the correlation identifier supplied by `CorrelationIdFilter`.

### Missing behavior

There is no POST mapping or JSON request model for Engineering Story Context. There is also no controller-level MVC coverage proving request binding, exact large-description transmission, response serialization, GET compatibility, or error semantics for this resource.

Unsupported content type is not explicitly classified by the shared handler. If the new POST endpoint relies on `consumes = application/json`, this missing classification becomes observable and must not surface as a generic 500.

### Behavior that must remain unchanged

* GET path, optional query parameter, and successful response.
* `EngineeringStoryContextService` and response contracts.
* Null/blank fallback semantics already implemented by `RepositoryContextAdapter`.
* Repository Context profile, collectors, evidence ranking, selection, budgets, provenance, decisions, digest, and warnings.
* Project-not-found propagation through the shared error boundary.
* Correlation-ID behavior and safe unexpected-error responses.
* Persistence and schema.

## Relevant Documentation

* `README.md` — Core API/runtime and OpenAPI documentation entry points.
* `docs/architecture.md` — Core Service ownership and deterministic project-context boundaries.
* `docs/decisions/ADR-037.md` — Repository-First Context Extraction.
* `docs/decisions/ADR-038.md` — deterministic, modular Repository Context Engine.
* `docs/decisions/ADR-039.md` — deterministic Context Intelligence and profiles.
* `docs/decisions/ADR-040.md` — separation of Repository Evidence and Trusted Knowledge.
* Story 0003 artifacts — existing Engineering Story Context integration and backward-compatible GET description contract.
* Story 0007 artifacts — standardized API error semantics and correlation contract.
* `engineering-story` Repository Analysis workflow prompt — analysis boundaries and Human Approval Gate 1.

## Constraints

* POST must be additive on the existing resource path; GET compatibility is mandatory.
* The JSON boundary must use a small explicit request model rather than unstructured maps or controller-specific parsing.
* The exact non-blank description must reach the existing service without truncation, summarization, or normalization that changes Story meaning.
* Null and blank values should retain the adapter's established fallback behavior. A physically absent body and malformed JSON remain malformed HTTP requests.
* Unsupported content type must receive deliberate client-error semantics through the common error representation, not a controller-specific body or generic 500.
* The controller remains transport-only and must not duplicate context assembly.
* The existing response type is reused; no POST-specific response is introduced.
* The test must exercise the real Spring MVC request path. Direct controller invocation cannot reproduce the transport regression.
* The regression payload must be at least 11.5 KiB and transmitted as JSON body content.
* Tomcat header limits must not be increased and the payload must not be moved to another header.
* No DevLog port change belongs to this Story; port work is now Story 0009.
* No Engineering-Skills/OpenClaw file may be modified by this DevLog-owned Story.
* The repository remains authoritative; DevLog evidence remains context rather than trusted interpretation under ADR-040.
* Human Approval Gate 1 must be satisfied before Implementation Planning begins.

## Risks

### Unsupported media type may violate Story 0007 semantics

The new POST operation creates a real `Content-Type` boundary. Without explicit classification, `HttpMediaTypeNotSupportedException` can be caught by the generic handler and reported as 500. That would make a normal client mistake indistinguishable from a server failure.

### Accidental validation incompatibility

Adding `@NotBlank` to the request field would make POST semantics stricter than the existing GET/service contract. The adapter deliberately supports absent or blank descriptions through a generic Story-preparation objective.

### GET/POST drift

Separate controller methods could diverge if either performs orchestration. Both must remain thin adapters to the same service method.

### Inadequate regression test

Mocking the controller directly, testing only a short body, or changing the global header limit would not demonstrate that the original failure is fixed.

### Partial cross-repository resolution

Completing this DevLog Story exposes the correct provider contract but does not switch Kiko's adapter from GET to POST. The external consumer change remains required and must not be mistaken for part of DevLog completion.

## Open Questions

None.

The existing adapter establishes intentional null/blank fallback semantics, while Spring MVC and the standardized error contract establish suitable behavior for absent/malformed bodies. Unsupported media type is a bounded, directly related error-classification gap rather than an unresolved product decision.

## Recommendation

Ready for planning

## Implementation Readiness

The current repository contains every required contract and abstraction:

* an existing POST-capable Spring MVC stack and request DTO conventions;
* the reusable `buildWithRepositoryContext(UUID, String)` service method;
* the unchanged `EngineeringStoryContext` response;
* shared standardized error and correlation infrastructure;
* reusable MockMvc test support.

No missing data, persistence change, new context capability, ADR, or technical prerequisite blocks implementation planning. The only directly related shared-boundary gap is deliberate unsupported-media-type classification, which is small and compatible with Story 0007.

## Approval Required

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
