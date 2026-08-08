# Implementation Plan

## Overview

Implement Story 0008 as an additive Spring MVC transport change. A new JSON request record will carry the complete Engineering Story description to a POST operation on the existing Engineering Story Context resource. The controller will pass that description unchanged to the existing `EngineeringStoryContextService.buildWithRepositoryContext(UUID, String)` method and return the existing response type.

The existing GET operation will remain unchanged. The shared HTTP error boundary will gain explicit unsupported-media-type classification so the new JSON contract preserves Story 0007's stable, traceable error semantics. Focused MockMvc coverage will reproduce the large-payload case through the real MVC binding path, followed by backend regression and live Docker validation.

## Planned Changes

### 1. Introduce the Engineering Story Context request model

Add a small immutable request record in `com.hopeful117.devlogai.projectcontext` containing one nullable `String description` field.

Do not add `@NotBlank` or a restrictive `@Size`: the existing adapter deliberately treats null and blank descriptions as generic Engineering Story preparation, while complete Stories must not be rejected by an arbitrary application limit.

The request model exists only to define the JSON transport contract; it must not contain context-building behavior.

### 2. Add the body-based POST operation

Extend `EngineeringStoryContextController` with a POST mapping on:

```text
/api/projects/{projectId}/engineering-story-context
```

Declare JSON consumption, bind the explicit request body, extract `description`, delegate to `buildWithRepositoryContext(projectId, description)`, and return the existing `EngineeringStoryContext` response.

Keep the current GET method and its optional query parameter unchanged. Both methods must remain thin transport adapters to the same service operation; no Repository Context orchestration may move into the controller.

With the request body required by Spring MVC:

* an absent body or malformed JSON is a malformed request;
* `{}`, `{"description":null}`, and blank descriptions are accepted and retain the current service fallback semantics;
* a non-JSON content type is unsupported media type.

### 3. Complete unsupported-media-type error classification

Extend the shared API error-code enumeration with a stable `UNSUPPORTED_MEDIA_TYPE` value.

Add an explicit `HttpMediaTypeNotSupportedException` handler to `GlobalExceptionHandler` returning HTTP 415 through the common `ApiErrorResponse` builder. Preserve the correlation identifier and use a neutral public message. If Spring supplies acceptable media types, retain protocol-standard response headers through the exception headers as the existing 405 handler does.

Do not alter unrelated error mappings or introduce controller-specific error responses.

### 4. Add controller-level MVC regression coverage

Create a focused `EngineeringStoryContextControllerWebMvcTest` using `ControllerWebMvcTestSupport`, a mocked `EngineeringStoryContextService`, and the real shared advice/filter path.

Cover:

* normal POST JSON binding and exact description delegation;
* a deterministic description of at least 11.5 KiB sent through MockMvc;
* successful response serialization using the existing response model;
* unchanged GET binding and delegation;
* missing body and malformed JSON as 400 `MALFORMED_REQUEST`;
* `{}`, JSON null, and blank descriptions reaching the service as their exact values;
* unsupported content type as 415 `UNSUPPORTED_MEDIA_TYPE` with a correlation identifier;
* invalid project UUID through the existing 400 `INVALID_PARAMETER` mapping;
* service-level missing-project propagation through the existing 404 `ENTITY_NOT_FOUND` mapping where useful to prove the resource uses the shared boundary.

The large-payload assertion must verify the service receives the complete string, not only that the response status is successful.

### 5. Extend shared error-boundary tests

Update `ApiErrorHandlingWebMvcTest` or the most focused shared MVC test to assert the new unsupported-media-type mapping independently of the Engineering Story controller. This keeps the stable error code and correlation contract owned by the shared boundary.

Avoid duplicating every shared error assertion in the controller test.

### 6. Document the additive API contract

Update current DevLog documentation to show:

* POST as the preferred operation for complete Engineering Stories;
* the JSON request shape;
* GET remaining compatible for short descriptions and existing consumers;
* the response remaining `EngineeringStoryContext`.

Do not rewrite historical Story reports. Explicitly note in the implementation handoff that Engineering-Skills must switch its adapter separately before the Kiko workflow can use the new operation.

### 7. Validate the provider contract

Run focused backend tests first, then the complete backend suite and existing quality checks. Rebuild/restart the local backend when required and submit the complete Story 0008 file through POST as JSON.

Inspect compact response indicators—evidence count, selection-decision count, digest, warnings—and confirm backend logs contain no oversized-header or unexpected server error. Also issue a short GET request to confirm backward compatibility.

Do not modify Engineering-Skills, OpenClaw configuration, or ports during this validation.

## Files to Modify

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextController.java` — add the JSON POST operation while preserving GET.
* `backend/src/main/java/com/hopeful117/devlogai/shared/response/ApiErrorCode.java` — add the unsupported-media-type code.
* `backend/src/main/java/com/hopeful117/devlogai/shared/exception/handler/GlobalExceptionHandler.java` — translate unsupported content types to the common 415 response.
* `backend/src/test/java/com/hopeful117/devlogai/shared/exception/handler/ApiErrorHandlingWebMvcTest.java` — verify shared 415 code and correlation behavior.
* `README.md` or the current API documentation identified during implementation — document POST and retained GET compatibility.

## Files to Create

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextRequest.java` — immutable JSON request contract containing the Story description.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java` — real MVC coverage for POST, large bodies, compatibility, binding, and error cases.

No migration or persistence file will be created.

## Dependencies

Internal dependencies:

* `EngineeringStoryContextController` depends on the existing `EngineeringStoryContextService`.
* POST and GET both depend on `buildWithRepositoryContext(UUID, String)`.
* MVC failures depend on `GlobalExceptionHandler`, `ApiErrorCode`, `ApiErrorResponse`, and `CorrelationIdFilter`.
* Controller tests depend on the existing `ControllerWebMvcTestSupport` test abstraction.

Ordering dependencies:

1. Define the request and error-code contracts.
2. Add controller and shared-handler behavior.
3. Add focused MVC tests.
4. Update current documentation.
5. Run automated and live validation.

No new library, framework, service, persistence dependency, or external API is required. The current Spring MVC/Jackson/testing stack is sufficient.

The Engineering-Skills adapter change is an external follow-up, not an implementation dependency for exposing and validating the DevLog provider contract.

## Test Plan

### New controller MVC tests

Create `EngineeringStoryContextControllerWebMvcTest` covering:

| Test behavior | Acceptance criteria |
| --- | --- |
| POST binds JSON and delegates the exact description | AC-1, AC-3 |
| POST accepts and transmits at least 11.5 KiB | AC-2, AC-7 |
| POST returns the existing response representation | AC-3, AC-7 |
| GET remains available with its current parameter semantics | AC-4, AC-7 |
| Missing/malformed body returns common 400 error | AC-5, AC-6, AC-7 |
| Missing/null/blank field retains service fallback inputs | AC-5, AC-7 |
| Non-JSON content returns common traceable 415 | AC-5, AC-6, AC-7 |
| Invalid UUID and missing project use shared errors | AC-6, AC-7 |

### Updated shared MVC tests

Add an unsupported-media-type assertion for HTTP 415, stable `UNSUPPORTED_MEDIA_TYPE`, response path, and correlation-ID consistency.

### Existing regression tests

Run the existing project-context service and adapter tests to confirm the context pipeline and null/blank handling remain unchanged.

Suggested targeted command:

```text
cd backend
./mvnw test -Dtest="EngineeringStoryContextControllerWebMvcTest,EngineeringStoryContextServiceTest,RepositoryContextAdapterTest,ApiErrorHandlingWebMvcTest,GlobalExceptionHandlerTest"
```

Then run:

```text
cd backend
./mvnw test
```

Run the repository's configured JaCoCo/quality verification if it is not already bound to `test` or `verify`. Frontend validation is not required because no frontend contract or code changes.

### Live validation

Against the rebuilt local backend:

* serialize the complete Story file as `{"description": ...}`;
* POST it to the new endpoint for the configured DevLog project;
* expect HTTP 200 and an `EngineeringStoryContext` containing non-empty Repository Context evidence and decisions plus a digest;
* inspect warnings and backend logs;
* call the existing GET endpoint with a short description and expect HTTP 200;
* send a non-JSON POST and expect the standardized 415 response.

Expected success means all targeted and repository-wide backend tests pass, live POST handles the complete Story without request-header rejection, GET remains compatible, and no unexpected scope changes are present.

## Risks

### POST becomes stricter than GET

Bean Validation could accidentally reject null or blank descriptions that currently trigger a supported fallback. The plan avoids field constraints and tests each value explicitly.

### Unsupported media type remains a 500

The generic exception handler currently risks misclassification. The plan adds a dedicated 415 mapping and shared MVC coverage.

### Large-body test gives false confidence

A direct Java method call would bypass HTTP parsing. The plan sends the generated payload through MockMvc and verifies the complete string at the mocked service boundary, then repeats the scenario against the live server.

### GET and POST behavior diverge

Both operations delegate to the same existing service method and return the same response type. Tests cover each transport without duplicating application logic.

### Provider completion is mistaken for end-to-end completion

The external adapter still uses GET. The Implementation and Engineering Reports must identify the separate Engineering-Skills adoption as a required follow-up rather than modifying that repository in this Story.

## Validation Checklist

* [ ] `EngineeringStoryContextRequest` is immutable and contains the nullable description only.
* [ ] POST exists on the existing Engineering Story Context path and consumes JSON.
* [ ] POST passes the exact description to `buildWithRepositoryContext`.
* [ ] GET remains unchanged and compatible.
* [ ] Null and blank descriptions preserve existing fallback semantics.
* [ ] Missing/malformed bodies return the common 400 error.
* [ ] Unsupported content types return common HTTP 415 / `UNSUPPORTED_MEDIA_TYPE` with correlation ID.
* [ ] No controller-specific error representation is introduced.
* [ ] A body of at least 11.5 KiB succeeds through MockMvc and reaches the service intact.
* [ ] Existing `EngineeringStoryContext` serialization is reused.
* [ ] Targeted backend tests pass.
* [ ] Complete backend tests and configured coverage/quality checks pass.
* [ ] Live POST with the complete Story returns evidence, decisions, digest, and no unexpected server error.
* [ ] Live short GET remains successful.
* [ ] Current API documentation describes POST and retained GET behavior.
* [ ] No header-limit increase, truncation, context-engine change, persistence change, port change, or Engineering-Skills modification is present.
* [ ] Implementation Report explicitly records the external adapter follow-up.

## Recommendation

Ready for implementation

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
