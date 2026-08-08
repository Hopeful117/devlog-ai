# Implementation Plan

## Overview

Extend DevLog's existing shared HTTP error boundary rather than introducing a new response standard.

The implementation will add a stable error-code vocabulary, carry the effective correlation identifier from `CorrelationIdFilter` into error response bodies, explicitly classify missing routes, unsupported methods and duplicate project slugs, and preserve the current neutral treatment of unexpected failures. The specialized AI task conflict response will keep its domain-specific state while joining the same traceability contract.

Tests will move beyond direct handler invocation by exercising the filter, Spring dispatcher and controller advice together. The Angular consumer will remain unchanged unless implementation validation reveals that the additive JSON fields break an actual contract.

## Planned Changes

1. **Define the stable public error-code vocabulary.**

   Add a small shared enum or equivalent immutable type owned by the HTTP response layer. It will describe public failure semantics rather than Java exception class names. The initial vocabulary will cover unknown routes, unsupported methods, invalid parameters, malformed requests, validation failures, missing entities, generic conflicts, duplicate project slugs, unsupported analysis types, invalid AI task results and unexpected internal failures.

   Existing domain-specific AI task conflict codes will remain valid. The shared vocabulary must not force their replacement with a less precise generic code.

2. **Extend the common error response additively.**

   Add `code` and `correlationId` to `ApiErrorResponse` while preserving `timestamp`, `status`, `error`, `message` and `path`. Keep JSON field names stable and avoid changing successful response contracts.

   Centralize construction of common error responses inside `GlobalExceptionHandler` so status, reason phrase, code, message, path and correlation identifier cannot drift between handlers.

3. **Expose the effective request correlation identifier explicitly.**

   Extend `CorrelationIdFilter` to store the exact validated or generated identifier as a request attribute before invoking the filter chain, while retaining the existing MDC entry and `X-Correlation-ID` response header.

   `GlobalExceptionHandler` will read that request attribute when building an error response. It must not generate another identifier. Tests and standalone MVC support must run the filter or provide the attribute explicitly so missing test setup does not weaken the production contract.

4. **Classify framework-level HTTP failures.**

   Add explicit mappings in `GlobalExceptionHandler` for:

   * Spring MVC missing-resource or unmapped-route failure → `404 Not Found` with a route-specific stable code;
   * unsupported HTTP method → `405 Method Not Allowed` with a method-specific stable code.

   Preserve the `Allow` header behavior supplied by Spring where available. These expected 4xx responses must no longer reach the generic unexpected-exception logger.

5. **Classify the established duplicate-project conflict.**

   Map `ProjectSlugAlreadyExistsException` to `409 Conflict` with its own stable code and existing safe business message.

   Do not add speculative mappings for unused `BusinessException`, infrastructure/Git failures, collection failures or all `IllegalArgumentException` instances. Unclassified failures continue through the safe generic `500` path until their HTTP semantics are established by a concrete use case.

6. **Update all existing error mappings to the common contract.**

   Assign deterministic codes to existing validation, malformed JSON, typed-parameter mismatch, entity-not-found, conflict, unsupported-analysis and invalid-AI-result mappings.

   Keep current HTTP statuses and safe message behavior. Continue logging unexpected exceptions once with their stack trace, while returning only the neutral message, internal-error code and correlation identifier publicly.

7. **Align the specialized AI task conflict response.**

   Add `correlationId` to `AiTaskConflictResponse` and populate it from the same request attribute. Preserve its current `code`, `currentStatus`, message, path and `409` status.

   Do not remove the specialized response or collapse its domain-specific fields into `ApiErrorResponse`.

8. **Strengthen MVC and compatibility tests.**

   Update handler and filter unit tests for codes and correlation attributes. Ensure shared standalone MockMvc support includes the real `CorrelationIdFilter` where appropriate.

   Add focused MVC coverage that triggers missing-route and unsupported-method exceptions through Spring, checks duplicate-slug translation through `ProjectController`, verifies existing validation/entity behavior, exercises a controlled unexpected exception and asserts that the response header and body contain the same correlation identifier.

   Keep the frontend adapter unchanged because it structurally reads only `message` and status and tolerates additive fields. Run its existing tests to confirm compatibility. Migrating its message-substring classification to stable codes is a separate improvement unless a minimal adaptation becomes necessary for Story correctness.

9. **Validate the running contract.**

   After automated tests pass, rebuild or restart the backend and repeat read-only HTTP probes for an unknown route, unsupported method, invalid UUID and unknown project. Confirm correct statuses, stable codes, safe messages, paths and correlation consistency.

## Files to Modify

* `backend/src/main/java/com/hopeful117/devlogai/shared/response/ApiErrorResponse.java` — add stable code and correlation identifier fields.
* `backend/src/main/java/com/hopeful117/devlogai/shared/exception/handler/GlobalExceptionHandler.java` — centralize response construction, assign codes and add explicit `404`, `405` and duplicate-slug mappings.
* `backend/src/main/java/com/hopeful117/devlogai/shared/logging/CorrelationIdFilter.java` — expose the effective identifier as a request attribute while preserving MDC and response-header behavior.
* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/AiTaskConflictResponse.java` — add the effective correlation identifier without removing domain fields.
* `backend/src/test/java/com/hopeful117/devlogai/shared/exception/handler/GlobalExceptionHandlerTest.java` — update direct handler expectations for codes and correlation identifiers and add new explicit mapping coverage where useful.
* `backend/src/test/java/com/hopeful117/devlogai/shared/logging/CorrelationIdFilterTest.java` — verify request attribute, MDC and header all expose the same effective identifier.
* `backend/src/test/java/com/hopeful117/devlogai/shared/controller/ControllerWebMvcTestSupport.java` — include correlation-filter behavior in controller-level MVC tests if this remains the smallest reliable shared setup.
* `backend/src/test/java/com/hopeful117/devlogai/project/controller/ProjectControllerWebMvcTest.java` — verify duplicate-slug conflict translation and correlation metadata.
* AI task result controller tests and any other tests that construct or assert `AiTaskConflictResponse` or `ApiErrorResponse` — adapt to the additive contract.

The Angular `request-error.ts` should not be modified unless validation proves an actual compatibility need.

## Files to Create

* `backend/src/main/java/com/hopeful117/devlogai/shared/response/ApiErrorCode.java` — stable machine-readable public error-code vocabulary, if an enum is confirmed as the smallest implementation.
* A focused test class under `backend/src/test/java/com/hopeful117/devlogai/shared/exception/handler/` or `shared/controller/` — end-to-end MVC coverage for framework error resolution and correlation consistency, if existing controller tests cannot express all required paths cleanly.

No production controller, persistence entity, migration or frontend file should be created.

## Dependencies

The implementation reuses:

* Spring Boot 4.1 / Spring MVC exception types and MockMvc;
* the existing `GlobalExceptionHandler`;
* the existing `CorrelationIdFilter` and MDC logging policy;
* the existing project conflict exception;
* JUnit 5, Mockito and Spring test support.

No new external dependency is required.

Ordering dependencies:

1. define the error-code and correlation contracts;
2. update common and specialized response DTOs;
3. update the filter-to-handler handoff;
4. implement explicit exception mappings;
5. adapt tests and consumers;
6. run automated and live validation.

No database, AI Engine, Engineering-Skills or infrastructure prerequisite exists.

## Test Plan

### Shared handler unit tests

Update `GlobalExceptionHandlerTest` to verify for every supported family:

* expected HTTP status;
* stable code;
* safe message;
* request path;
* correlation identifier.

Add direct coverage for missing route, unsupported method and duplicate project slug only as supplementary unit coverage; MVC tests remain authoritative for framework exception resolution.

### Correlation filter tests

Verify:

* a valid caller identifier appears identically in MDC during the chain, request attribute, response header and error body;
* an invalid or missing identifier is replaced once by a UUID reused across those locations;
* MDC remains cleared after the request.

### Spring MVC tests

Exercise the dispatcher and advice with the correlation filter enabled:

* `GET` unknown API route → `404`, route code, safe message, path and matching correlation ID;
* unsupported method on an existing route → `405`, method code and matching correlation ID;
* invalid UUID path parameter → `400` and invalid-parameter code;
* service-thrown `EntityNotFoundException` → `404` and entity code;
* malformed JSON or Bean Validation error → `400` and appropriate code;
* service-thrown `ProjectSlugAlreadyExistsException` → `409` and duplicate-slug code;
* controlled unexpected exception → safe `500`, internal-error code and no leaked internal message;
* specialized AI task conflict → `409`, existing domain code/current status and matching correlation ID.

### Frontend compatibility

Run existing tests for `request-error.ts` and affected feature tests. Success means additive backend fields require no frontend change and existing message/status handling remains functional.

### Validation commands

Run targeted backend tests first, followed by the repository's normal backend validation:

```text
cd backend
./mvnw -Dtest=GlobalExceptionHandlerTest,CorrelationIdFilterTest,ProjectControllerWebMvcTest,<new-mvc-test> test
./mvnw test
```

If the known PostgreSQL-dependent application-context test cannot run in the local environment, rerun the established suite excluding only that documented infrastructure-dependent test and report the limitation explicitly. Since Docker PostgreSQL is currently available, prefer running the complete suite against the configured database.

Run relevant frontend tests without changing production frontend behavior:

```text
cd frontend
npm test -- --watch=false
npm run build
```

Run whitespace and coverage/quality checks already configured by the repository. SonarQube is required only if it is configured and available for this module; absence must be reported rather than replaced with an invented result.

Finally rebuild the backend container and execute read-only HTTP probes. Expected results are `404`, `405`, `400` and `404` respectively for unknown route, unsupported method, invalid UUID and unknown project, with common error codes and matching header/body correlation IDs.

## Risks

### Missing correlation attribute outside the production filter chain

Direct handler tests or unusual servlet dispatches may omit the request attribute. Mitigation: define one public filter attribute constant, install the filter in shared MVC test setup and make tests fail visibly if the established request contract is absent rather than generating a second identifier.

### Public code vocabulary becomes accidental implementation detail

Using exception class names as codes would couple clients to Java internals. Mitigation: choose semantic codes such as route-not-found, method-not-allowed and internal-error, serialize them deterministically and assert them in contract tests.

### Existing 404 frontend messaging changes

Correcting unknown routes from `500` to `404` changes frontend classification from generic to not-found. This is intended HTTP behavior. Mitigation: preserve a clear backend message and run frontend error-adapter tests.

### Standalone MockMvc differs from the running application

Standalone setup may not reproduce all resource-handler behavior. Mitigation: use the smallest application-context or explicitly configured MockMvc test that reproduces the live `NoResourceFoundException`, and retain live read-only probes as final evidence.

### Over-expansion into all exception types

The repository contains infrastructure and invariant exceptions with unresolved public semantics. Mitigation: restrict new mappings to framework failures and the established duplicate-slug conflict named by the Story; leave all other unknown failures on the safe `500` path.

No blocking risk or human clarification is required before implementation.

## Validation Checklist

* [ ] A stable semantic API error-code type exists and is serialized deterministically.
* [ ] `ApiErrorResponse` preserves existing fields and adds `code` and `correlationId`.
* [ ] `AiTaskConflictResponse` preserves `code` and `currentStatus` and adds `correlationId`.
* [ ] `CorrelationIdFilter` exposes one effective identifier through MDC, request attribute and response header.
* [ ] Error bodies use that same identifier and never generate another one.
* [ ] Unknown API routes return `404`, not `500`.
* [ ] Unsupported methods return `405`, not `500`.
* [ ] Duplicate project slugs return `409`, not `500`.
* [ ] Existing invalid parameter, malformed JSON, validation, entity-not-found, conflict and unsupported-analysis statuses remain correct.
* [ ] Unexpected exceptions remain `500` with a neutral public message and server-side stack trace only.
* [ ] No blanket `IllegalArgumentException` or infrastructure-exception mapping is introduced.
* [ ] Specialized AI task conflict fields remain compatible.
* [ ] MVC tests traverse filter, dispatcher and advice for framework errors.
* [ ] Header/body correlation consistency is tested for accepted and generated identifiers.
* [ ] Existing backend tests pass, with any infrastructure-only limitation reported explicitly.
* [ ] Existing frontend error handling tests and build pass without unnecessary production changes.
* [ ] Live read-only HTTP probes return the expected statuses and stable contract.
* [ ] No successful API response, database schema, DevLog context engine or Engineering-Skills file changes.
* [ ] No RFC 9457 migration, broad exception-hierarchy redesign or unrelated refactor is included.

## Recommendation

Ready for implementation

The implementation strategy is bounded, reuses the existing shared error and correlation infrastructure, defines explicit compatibility constraints and identifies authoritative MVC and live validation. No architectural ambiguity or missing prerequisite blocks implementation.

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
