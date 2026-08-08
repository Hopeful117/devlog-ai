# Engineering Report

## Story

Story 0008 — Engineering Story Context Request Body.

The Story added a body-based HTTP operation allowing DevLog consumers to submit complete Engineering Stories without encoding them into a GET query string. The existing GET operation remains available for compatibility.

## Objective

The real Kiko integration exposed that a complete Engineering Story produced an HTTP request target larger than Tomcat's accepted header/request-line size. Tomcat rejected the request before Spring MVC could invoke DevLog.

The objective was to remove that transport limitation without increasing global server limits, truncating the Story, redesigning Repository Context, or changing Engineering-Skills from the DevLog repository.

## Repository Analysis Summary

The analysis established that Repository Context construction already worked correctly with short descriptions. The defect was isolated to transport.

The affected boundary was the Java Core `projectcontext` HTTP controller. Its existing service already accepted `UUID projectId` and `String storyDescription`, built the same project snapshot and Repository Context, and returned `EngineeringStoryContext`.

The analysis also identified that unsupported content types were not explicitly handled by the shared error boundary and could become generic server errors. Existing null and blank description fallback semantics had to remain intact.

Relevant architectural constraints included thin controllers, reuse of the deterministic context pipeline, backward-compatible GET behavior, shared API errors and correlation IDs, and preservation of ADR-037 through ADR-040 boundaries.

## Implementation Plan Summary

The human-approved plan selected an additive POST operation on the existing resource path using a small immutable JSON request record. Both GET and POST would delegate to the same application service and return the existing response model.

The plan also included explicit HTTP 415 classification, focused MockMvc coverage with a payload above 11.5 KiB, full backend and coverage validation, OpenAPI verification, and a real Docker-backed request using a complete Story.

Excluded work remained excluded: server header-limit changes, truncation, Repository Context changes, persistence, ports, and Engineering-Skills modifications.

## Implementation Summary

DevLog now exposes:

```text
POST /api/projects/{projectId}/engineering-story-context
Content-Type: application/json

{"description":"<complete Engineering Story>"}
```

`EngineeringStoryContextRequest` carries the nullable description. The controller delegates its exact value to `buildWithRepositoryContext`. The GET endpoint remains compatible and uses the same delegation path.

The shared error contract now maps unsupported content types to HTTP 415 / `UNSUPPORTED_MEDIA_TYPE` with path and correlation identifier. Missing or malformed bodies use the existing `MALFORMED_REQUEST` behavior. Missing, null, and blank description fields retain the existing service fallback semantics.

Six controller MVC tests and one shared error-boundary test were added. Current README documentation describes the POST request and retained GET operation.

No implementation deviation from the approved plan occurred.

## Modified Files

* `README.md` — documented the POST request-body contract and retained GET compatibility.
* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextController.java` — added POST and shared GET/POST delegation.
* `backend/src/main/java/com/hopeful117/devlogai/shared/response/ApiErrorCode.java` — added `UNSUPPORTED_MEDIA_TYPE`.
* `backend/src/main/java/com/hopeful117/devlogai/shared/exception/handler/GlobalExceptionHandler.java` — added standardized HTTP 415 handling.
* `backend/src/test/java/com/hopeful117/devlogai/shared/exception/handler/ApiErrorHandlingWebMvcTest.java` — added shared 415/correlation coverage.

Workflow artifacts were created under `docs/stories/0008-engineering-story-context-request-body/`. The previously drafted port Story was renumbered to `docs/stories/0009-dedicated-local-ports/` before Story 0008 began.

## Created Files

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextRequest.java` — immutable JSON request contract.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java` — HTTP contract and regression coverage.

## Architecture Impact

There is no meaningful context-engine architectural change.

The change is an additive HTTP transport capability. It introduces one request record and one stable error code while preserving:

* the existing service and response contracts;
* deterministic Repository Context construction;
* Context Profiles, ranking, selection, provenance and budgets;
* evidence/trusted-knowledge separation;
* GET compatibility;
* persistence and dependency direction.

No new external dependency, database migration, authentication change, or ADR was required.

## Validation

Focused validation:

```text
./mvnw test -Dtest="EngineeringStoryContextControllerWebMvcTest,EngineeringStoryContextServiceTest,RepositoryContextAdapterTest,ApiErrorHandlingWebMvcTest,GlobalExceptionHandlerTest"
```

Result: 33 tests passed.

Autonomous backend validation while PostgreSQL was initially unavailable:

```text
./mvnw test -Dtest='!DevlogAiBackendApplicationTests'
```

Result: 374 tests passed. The initial complete-suite attempt had only the known infrastructure error caused by unavailable `localhost:5432`.

Quality validation:

```text
./mvnw verify -Dtest='!DevlogAiBackendApplicationTests'
```

Result: 374 tests passed and the configured JaCoCo line-coverage threshold of 80% was met.

After `docker compose up -d --build`, the complete backend suite passed:

```text
./mvnw test
```

Result: 375 tests passed, 0 failures, 0 errors.

Live validation submitted the complete Story 0008 document through POST. Result:

* HTTP 200;
* 58 evidence items;
* 58 selection decisions;
* context digest `58c045a609ac0023e8d935800decda80efc0f79117aa49aa8300ebd854d9a631`;
* expected bounded-context warning `EVIDENCE_SUMMARY_TRUNCATED`;
* no oversized-header or unexpected server error.

A live short GET returned HTTP 200. A live `text/plain` POST returned HTTP 415 with `UNSUPPORTED_MEDIA_TYPE` and a correlation identifier. OpenAPI exposes both GET and POST. `git diff --check` passed.

SonarQube was not required or executed for this bounded backend transport change; the repository's configured Maven coverage gate was executed successfully.

## Review Outcome

Technical recommendation: Ready for human approval.

The Code Review verified all ten acceptance criteria, found no Blocker, Major, Minor, or Observation finding, and confirmed architecture, compatibility, error semantics, tests, OpenAPI, and live behavior.

The only residual integration dependency is external: Engineering-Skills still uses GET and must adopt POST in its own repository before Kiko can submit complete Stories through the normal workflow.

Human Code Review approval: granted.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Remaining Work

DevLog Story 0008 has no remaining provider-side work.

A separate Engineering-Skills-owned change must switch `devlog-context.mjs` from the GET query parameter to the new POST JSON body. This is a non-blocking follow-up to the completed DevLog Story, but it is required before resuming a DevLog-first Engineering Story with a complete Story description.

The dedicated local-port work remains separately scoped as DevLog Story 0009.

## Lessons Learned

* A context engine can be functionally mature while its transport contract still blocks real workflow use; realistic end-to-end payloads are necessary validation.
* Query input larger than practical request-target limits belongs in request content. POST is the smallest interoperable choice for the current Spring MVC stack.
* The standardized error boundary made the new unsupported-content-type case easy to expose consistently and traceably.
* Provider and consumer ownership should remain separate: DevLog exposes context, while Engineering-Skills owns how Kiko invokes it and falls back.
* Compact inspection of evidence counts, decisions, digest and warnings is sufficient for live integration validation without flooding Kiko's context.

## Final Status

Completed with Follow-up
