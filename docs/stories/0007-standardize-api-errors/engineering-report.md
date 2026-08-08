# Engineering Report

## Story

Story 0007 — Standardize API Error Semantics.

The Story required DevLog to return explicit, stable and traceable HTTP errors so API consumers such as Kiko can distinguish invalid requests, unknown routes, unsupported methods, missing entities, known conflicts and unexpected backend failures without parsing human-readable messages.

## Objective

Correct framework and business failures that incorrectly reached the generic `500 Internal Server Error` path, add stable machine-readable codes and correlation identifiers to public error bodies, and preserve safe server-side diagnostics and existing successful API contracts.

## Repository Analysis Summary

Repository Analysis established that the Java Core backend already had the correct ownership boundaries: thin controllers, HTTP-agnostic services, a shared `GlobalExceptionHandler`, a common `ApiErrorResponse`, and a highest-precedence `CorrelationIdFilter`.

The observed defects came from incomplete exception classification. Spring MVC missing-route and unsupported-method exceptions and `ProjectSlugAlreadyExistsException` fell through to the generic `500` handler. The common response lacked a stable code and body correlation identifier, while the Angular consumer tolerated additive fields.

The analysis constrained the change to explicit, evidence-based mappings. It excluded blanket runtime-exception classification, persistence changes, frontend redesign, Repository Context changes and a second competing error format.

## Implementation Plan Summary

The human-approved plan extended the existing shared boundary rather than replacing it. It defined a semantic error-code vocabulary, added `code` and `correlationId` to common errors, exposed the filter's effective identifier through a request attribute, mapped established framework and duplicate-slug failures explicitly, and retained the specialized AI task conflict fields.

Validation was planned at handler, filter, dispatcher/advice, full backend, frontend compatibility and live HTTP levels. Successful responses, database schema, Engineering-Skills and DevLog context selection remained out of scope.

## Implementation Summary

The backend now returns:

* unknown routes as `404/ROUTE_NOT_FOUND`;
* unsupported methods as `405/METHOD_NOT_ALLOWED`, preserving `Allow`;
* invalid parameters, malformed bodies and validation failures as explicit `400` codes;
* missing entities as `404/ENTITY_NOT_FOUND`;
* duplicate project slugs as `409/PROJECT_SLUG_ALREADY_EXISTS`;
* unexpected failures as safe `500/INTERNAL_ERROR` responses.

Every common error includes the effective identifier selected by `CorrelationIdFilter`, matching MDC and `X-Correlation-ID`. The specialized AI task conflict response retains its domain code and current status while adding the same traceability field.

The focused MVC validation used existing standalone MockMvc infrastructure because the Spring Boot MockMvc auto-configuration artifact is not present. Both the standalone and running-application missing-route exception variants are supported and were verified. A rejected duplicate-slug POST was used for live conflict validation and did not persist data.

## Modified Files

* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/AiTaskConflictResponse.java` — added correlation traceability to specialized conflicts.
* `backend/src/main/java/com/hopeful117/devlogai/shared/exception/handler/GlobalExceptionHandler.java` — centralized error construction, assigned semantic codes and added explicit route, method and duplicate-slug mappings.
* `backend/src/main/java/com/hopeful117/devlogai/shared/logging/CorrelationIdFilter.java` — exposes the effective identifier as a request attribute.
* `backend/src/main/java/com/hopeful117/devlogai/shared/response/ApiErrorResponse.java` — additively exposes code and correlation identifier.
* `backend/src/test/java/com/hopeful117/devlogai/project/controller/ProjectControllerWebMvcTest.java` — verifies duplicate-slug HTTP behavior.
* `backend/src/test/java/com/hopeful117/devlogai/shared/controller/ControllerWebMvcTestSupport.java` — installs the correlation filter in shared MVC tests.
* `backend/src/test/java/com/hopeful117/devlogai/shared/exception/handler/GlobalExceptionHandlerTest.java` — verifies semantic mappings and traceability.
* `backend/src/test/java/com/hopeful117/devlogai/shared/logging/CorrelationIdFilterTest.java` — verifies accepted and generated identifier propagation.

## Created Files

* `backend/src/main/java/com/hopeful117/devlogai/shared/response/ApiErrorCode.java` — stable public error-code vocabulary.
* `backend/src/test/java/com/hopeful117/devlogai/shared/exception/handler/ApiErrorHandlingWebMvcTest.java` — focused dispatcher, advice and filter contract tests.

## Architecture Impact

No system-level architecture, persistence, dependency direction or AI responsibility changed.

The implementation introduces one small shared enum and one explicit request attribute contract between the existing correlation filter and HTTP error handler. Controller/service separation, safe diagnostic boundaries, successful response compatibility and deterministic Core ownership are preserved. The JSON extension is additive, so the current Angular consumer required no production change.

## Validation

* Focused backend suite: 24 tests passed with no failures or errors.
* Complete backend verification: 368 tests passed; package creation and configured JaCoCo checks passed.
* Frontend suite: 73 tests across 21 files passed unchanged.
* Frontend production build succeeded.
* `git diff --check` succeeded.
* Docker backend image rebuilt and started with healthy PostgreSQL and AI Engine dependencies.
* Live HTTP probes confirmed expected `404`, `405`, `400`, `404` and `409` contracts, stable codes, safe messages and matching header/body correlation identifiers.

SonarQube was not executed because no available local SonarQube service or required project configuration was identified.

## Review Outcome

The Code Review verified all eleven acceptance criteria, plan compliance, architecture boundaries, test quality and live behavior.

No Blocker, Major, Minor or Observation finding remained. No residual risk was identified.

Technical recommendation: Ready for human approval.

Human Code Review approval: granted.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Remaining Work

None.

## Lessons Learned

Framework-level HTTP behavior must be tested through the dispatcher and confirmed against the running Spring Boot application because standalone MockMvc and the production resource handler can raise different exception types for the same unmapped route.

Correlation identifiers should be transferred explicitly as request context rather than regenerated or inferred from logging state. Stable semantic error codes should describe consumer-visible failure meaning rather than Java implementation types.

## Final Status

Completed
