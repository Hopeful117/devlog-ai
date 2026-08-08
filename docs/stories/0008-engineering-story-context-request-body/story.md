# Story 0008 — Engineering Story Context Request Body

## Story ID
0008

## Title
Accept complete Engineering Stories through a body-based context request

## Status
Draft

## Priority
High

## Date
2026-08-08

---

## User Story

As an Engineering Story Context API consumer such as Kiko,
I want to submit a complete Engineering Story in the HTTP request body,
So that DevLog can build story-specific repository context without the request being rejected because the Story exceeds HTTP request-target or header limits.

---

## Context

The first real use of DevLog inside Kiko's `engineering-story` workflow exposed a transport limitation in the existing API contract:

```text
GET /api/projects/{projectId}/engineering-story-context?description={storyDescription}
```

The Engineering-Skills adapter correctly transmitted the complete Story as the `description` query parameter. A realistic Story was approximately 11.5 KiB before URL encoding. Tomcat rejected the resulting request before it reached Spring MVC with:

```text
IllegalArgumentException: Request header is too large
```

The same project mapping and context engine succeeded with a short description, returning 58 evidence items, 58 selection decisions, a context digest and no warnings. The failure is therefore isolated to the GET query-string transport, not project resolution, Repository Context construction, ranking or evidence quality.

Increasing the global HTTP header limit would treat the symptom and broaden server exposure. Truncating the Story would discard scope, constraints or acceptance criteria and weaken story-specific ranking. The API needs a body-based request contract suitable for complete Engineering Stories.

The existing GET endpoint may already have consumers and must remain available during this increment. Engineering-Skills owns its adapter and will adopt the new contract in a separate repository-owned change.

---

## Objective

Add an additive body-based Engineering Story Context API operation that accepts the complete Story description as JSON, delegates to the existing context-building service, and returns the existing `EngineeringStoryContext` response unchanged.

The change must remove request-target size as the practical limit for normal Engineering Stories without redesigning Repository Context or changing Kiko's orchestration responsibilities.

---

## Acceptance Criteria

### AC-1: Body-based context request

DevLog must expose a `POST` operation on the existing resource path:

```text
POST /api/projects/{projectId}/engineering-story-context
```

The request must use JSON and carry the Story description in the request body through a small explicit request model.

### AC-2: Complete realistic Stories are accepted

The POST operation must accept a realistic complete Engineering Story whose description is at least as large as the approximately 11.5 KiB Story that failed through the GET query parameter.

The request must reach the existing context-building service and must not depend on increasing Tomcat's request-header limit.

### AC-3: Existing context behavior is reused

The POST operation must call the same Engineering Story Context service behavior as the existing GET operation and return the existing `EngineeringStoryContext` response contract.

It must preserve:

* the description supplied to intent and user-guidance construction;
* the `engineering-story-v1` Context Profile;
* ranked evidence and provenance;
* selection decisions;
* context digest and warnings;
* existing project-not-found and context-building error semantics.

No parallel context-building path may be introduced.

### AC-4: GET compatibility is preserved

The existing GET operation must remain available and behaviorally compatible. It remains suitable for existing consumers and short descriptions. This Story must not silently redirect, remove or change its successful response contract.

### AC-5: Request validation is explicit

The request model and controller boundary must define the intended behavior for:

* a missing request body;
* malformed JSON;
* a missing description field;
* a JSON `null` description;
* a blank description;
* an unsupported content type.

Repository Analysis and Implementation Planning must select semantics consistent with the existing service behavior and standardized DevLog API error contract. Validation must not introduce an arbitrary size limit that rejects normal complete Stories without a demonstrated operational reason.

### AC-6: Standard API errors are preserved

Failures that reach Spring MVC must use the common error response introduced by Story 0007, including the appropriate HTTP status, stable error code and correlation identifier.

The implementation must not create controller-specific error bodies.

### AC-7: Controller tests cover the real HTTP contract

Spring MVC tests must cover at least:

* successful POST with a normal Story description;
* successful POST with a description of at least 11.5 KiB;
* exact description transmission to the service;
* response serialization compatibility with the existing GET operation;
* continued GET compatibility;
* the validation and content-type cases established under AC-5;
* project/service failures through the shared error boundary where directly relevant.

### AC-8: Live validation demonstrates the fix

With DevLog running locally, validation must submit a complete realistic Story through POST and confirm that:

* the request is not rejected as an oversized request target/header;
* Repository Context is returned;
* evidence and selection decisions are present;
* the digest is present;
* no unexpected warning or server error is produced.

### AC-9: API documentation is updated

Current API or operational documentation that presents the Engineering Story Context endpoint must document the POST request body and continued GET compatibility. Historical Story reports must not be rewritten.

### AC-10: No unrelated behavior changes

The Story must not alter Repository Context collectors, profiles, ranking, selection or budgets; project resolution; persistence; Human Approval semantics; the error-code architecture; local service ports; or external Engineering-Skills files.

---

## Scope

### In Scope

* Add a JSON request model for an Engineering Story description.
* Add a POST mapping on the existing Engineering Story Context resource path.
* Reuse the existing Engineering Story Context service contract.
* Preserve the GET operation for backward compatibility.
* Define request-boundary validation consistent with current DevLog semantics.
* Add focused MVC tests, including a realistic large Story payload.
* Update current DevLog API documentation where the endpoint is described.
* Perform live POST validation against the local DevLog runtime.

### Out of Scope

* Modifying Engineering-Skills or its `devlog-context.mjs` adapter.
* Removing or deprecating the existing GET endpoint.
* Increasing Tomcat request-header limits.
* Truncating, summarizing or rewriting the submitted Story.
* Adding multipart upload, streaming, compression or asynchronous jobs.
* Adding collectors, file-content analysis, symbols, dependencies or embeddings.
* Automatic repository/project resolution.
* Changing local Docker host ports; that work belongs to Story 0009.
* Database migrations or new persisted entities.

---

## Impacted Components

Repository Analysis must confirm the exact set. Expected components include:

* `EngineeringStoryContextController` — additive POST mapping;
* a small request DTO in the project-context HTTP boundary;
* existing Engineering Story Context controller tests;
* shared error-contract tests only where request validation exposes an uncovered path;
* current API/README documentation that names the endpoint.

The Repository Context Engine, collectors, service implementation and persistence layer are expected to remain unchanged unless analysis demonstrates that a minimal compatibility adjustment is necessary.

---

## Architectural Ownership and Boundaries

* DevLog owns the HTTP contract, deterministic context construction, evidence, ranking and provenance.
* The controller owns transport adaptation only and must remain thin.
* `EngineeringStoryContextService` remains the application entry point for context construction.
* The common Spring HTTP error boundary owns error serialization.
* Engineering-Skills owns when and how the endpoint is invoked and must adopt POST separately.
* Kiko owns reasoning and targeted repository verification.
* The repository remains the final implementation source of truth.

This is an additive transport correction, not a new context capability. No new ADR is expected unless Repository Analysis discovers a broader API-versioning decision.

---

## Risks

### Divergent GET and POST behavior

Duplicating orchestration in the controller could make the two operations drift. Both must delegate to the same service contract.

### Ambiguous empty-description semantics

The current GET parameter is optional and the service already handles null or blank descriptions. Adding Bean Validation without inspecting that behavior could introduce an accidental incompatibility.

### False large-payload validation

A controller unit test that calls the method directly would not prove the HTTP transport works. At least one test must pass the large JSON body through Spring MVC, and live validation must use the real server.

### External consumer remains on GET

The DevLog provider fix alone does not change Engineering-Skills. Completion must explicitly report that the consumer-side adapter change remains required before Kiko can send the full Story.

### Accidental scope expansion

The transport defect may invite context summarization, API redesign or server-limit tuning. Those are not required to solve the demonstrated failure.

---

## Validation Strategy

Use focused Spring MVC tests to establish request binding, validation, service delegation, error semantics and response compatibility. Include a generated deterministic description of at least 11.5 KiB so the regression remains reproducible without depending on a particular Story file.

Then run the relevant backend test suite and a live local request containing a complete realistic Story. Inspect only compact response indicators such as evidence count, selection-decision count, digest and warnings.

---

## Definition of Done

* [ ] All acceptance criteria are satisfied.
* [ ] A complete realistic Engineering Story succeeds through POST.
* [ ] The existing GET endpoint remains compatible.
* [ ] The existing context-building service and response model are reused.
* [ ] Large-payload MVC regression coverage passes.
* [ ] Shared error semantics remain consistent with Story 0007.
* [ ] Live validation confirms evidence, decisions and digest are returned.
* [ ] Current DevLog API documentation is aligned.
* [ ] No server header-limit increase or Story truncation is introduced.
* [ ] No Engineering-Skills or port configuration is modified.
* [ ] Code Review is complete.
* [ ] Engineering Report is produced after all Human Approval Gates.
