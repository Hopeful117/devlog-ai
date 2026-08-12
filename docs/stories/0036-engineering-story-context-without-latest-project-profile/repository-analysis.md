# Story 0036 — Engineering Story Context Without Latest Project Profile — Repository Analysis

## Purpose

Read-only analysis of the `engineering-story-context` 404 bug observed for the
`Engineering-Skills` project.

The goal is to scope the fix precisely before implementation.

## Story Understanding

The bug is not:

* a missing endpoint;
* a wrong HTTP method;
* a missing project registration in DevLog;
* an Engineering-Skills-side transport bug.

The bug is:

* the Engineering Story Context runtime currently treats a missing latest
  project profile as a fatal not-found condition, even when the project itself
  exists and the rest of project/repository context could still be built.

Requested outcome:

* Engineering Story Context should degrade gracefully when latest profile is
  absent.

Explicit non-goals:

* auto-generating a profile on the read path;
* changing the whole error model;
* redesigning Repository Context selection.

## Relevant Components

### `EngineeringStoryContextController`

File:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextController.java`

Findings:

* the endpoint exists and supports both:
  * `GET /api/projects/{projectId}/engineering-story-context`
  * `POST /api/projects/{projectId}/engineering-story-context`
* the controller delegates both paths to:
  * `buildAgentWithRepositoryContext(...)` by default
  * `buildWithRepositoryContext(...)` for `?detail=full`

Conclusion:

* route absence is not the cause.

### `EngineeringStoryContextServiceImpl`

File:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceImpl.java`

Findings:

* both context-building paths begin with:
  * `ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);`
* any failure in `ProjectContextProviderImpl` prevents both the compact and
  full response modes from being built.

Conclusion:

* the root cause is upstream of projection and repository-context adaptation.

### `ProjectContextProviderImpl`

File:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java`

Findings:

* the provider first confirms the project exists via `projectRepository.findById(projectId)`
* it then unconditionally calls:
  * `projectProfileService.getLatestByProject(projectId)`
* no local fallback is implemented when no profile exists.

Conclusion:

* this unconditional call is the most direct runtime cause of the bug.

### `ProjectProfileServiceImpl`

File:

* `backend/src/main/java/com/hopeful117/devlogai/profile/service/ProjectProfileServiceImpl.java`

Findings:

* `getLatestByProject(projectId)` returns:
  * first latest profile snapshot if one exists;
  * otherwise throws:
    * `EntityNotFoundException("Project profile", projectId)`

Conclusion:

* the current behavior is correct for a dedicated latest-profile lookup API,
  but it is too strict when reused inside Engineering Story Context assembly.

### `ProjectContextSnapshot`

File:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextSnapshot.java`

Findings:

* `latestProjectProfile` is modeled as:
  * `ProjectProfileResponse latestProjectProfile`
* it is not validated as non-null in the record constructor.

Conclusion:

* the domain snapshot already allows a missing profile semantically.

This strongly suggests the current runtime failure is accidental coupling, not
an intended invariant of the snapshot model.

## Existing Tests and Behavioral Evidence

### Live HTTP evidence

Observed against the running local instance:

* `GET /api/v1/projects/engineering-skills` → `200`
* `GET /api/v1/projects/93441821-2a71-4a1d-93cd-f38369030205/latest-profile`
  → `404`
* `GET /api/projects/93441821-2a71-4a1d-93cd-f38369030205/engineering-story-context?description=test`
  → `404 ENTITY_NOT_FOUND`
* error message:
  * `Project profile not found with identifier: 93441821-2a71-4a1d-93cd-f38369030205`

Interpretation:

* project exists;
* latest profile does not;
* Engineering Story Context currently fails because latest profile does not.

### `ProjectContextProviderTest`

File:

* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderTest.java`

Key findings:

* several tests explicitly stub:
  * `when(projectProfileService.getLatestByProject(projectId)).thenReturn(null);`
* and then assert:
  * `snapshot.latestProjectProfile()` is `null`
  * provider returns a valid snapshot with empty or populated lists

Examples include tests named:

* `shouldReturnEmptyListsWhenNoData`
* `shouldHandleMissingProfileGracefully`
* `shouldApplyPaginationLimits`

Interpretation:

* the intended provider contract already treats missing latest profile as a
  valid non-fatal state.

This is the strongest evidence that runtime and test expectations are currently
misaligned.

### `EngineeringStoryContextControllerWebMvcTest`

File:

* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`

Findings:

* coverage exists for:
  * POST body transport
  * GET compatibility
  * malformed body / media type errors
  * missing project returning 404
* there is no explicit regression test for:
  * existing project + missing latest profile

Conclusion:

* the missing-profile bug is not currently locked down at endpoint level.

### `EngineeringStoryContextServiceTest`

File:

* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceTest.java`

Findings:

* tests validate normal build paths and propagation of missing project
  exceptions
* no test covers:
  * provider returning a snapshot with `latestProjectProfile == null`
    followed by successful context construction

Conclusion:

* service-level regression coverage for this scenario is also missing.

## Architecture and Ownership

Relevant boundaries:

* project existence is authoritative in `ProjectRepository`
* latest profile is a derived, optional projection of prior analysis work
* Engineering Story Context is a read model intended to assemble the best
  available current context for an existing project

Architectural implication:

* an existing project should not become unreadable merely because one derived
  projection is absent, unless that projection is explicitly required by the
  endpoint contract

Current evidence suggests it is **not** explicitly required:

* domain snapshot allows null profile
* provider tests already expect graceful handling
* Story/README intent for Engineering Story Context is to improve repository
  context retrieval, not to gate it on prior profile generation

## Root Cause Hypothesis

Primary root cause:

* `ProjectContextProviderImpl` unconditionally delegates latest profile lookup
  to `ProjectProfileService.getLatestByProject(projectId)`, which is strict and
  throws when no snapshot exists.

Why the bug escaped:

* provider unit tests mocked `getLatestByProject(projectId)` as nullable and
  therefore never exercised the real throwing behavior of
  `ProjectProfileServiceImpl`
* endpoint/service tests covered missing project but not existing project plus
  missing profile

This created a mismatch:

* tests encoded graceful semantics;
* runtime encoded strict semantics.

## Fix Shape Guidance

The smallest credible fix should:

* preserve `404 ENTITY_NOT_FOUND` when the project itself is missing;
* preserve strict semantics of the dedicated `latest-profile` endpoint unless a
  later approved decision expands scope;
* make Engineering Story Context treat missing latest profile as:
  * `latestProjectProfile = null`
  * not a fatal error

Likely implementation directions:

1. Catch and translate only the specific missing-project-profile case inside
   `ProjectContextProviderImpl`, leaving other `EntityNotFoundException`s
   untouched.
2. Or introduce a nullable/latest-profile lookup method in the profile service
   for context-assembly use.

The second option is cleaner but changes a public service contract.

The first option is narrower and more likely appropriate for a bugfix story.

Implementation planning should compare both explicitly.

## Risks

### Over-broad exception swallowing

If the fix catches every `EntityNotFoundException`, real missing-project or
other not-found bugs could be hidden.

Control:

* narrow handling to the specific `Project profile` missing case for an already
  confirmed existing project.

### Behavioral drift in latest-profile endpoint

If implementation weakens `ProjectProfileServiceImpl.getLatestByProject`, the
dedicated latest-profile endpoint might change semantics unintentionally.

Control:

* keep the strict endpoint behavior unless explicitly approved otherwise.

### Incomplete regression coverage

If only provider tests are changed, the HTTP regression may still slip later.

Control:

* add endpoint-level or service-level coverage for the real scenario.

## Validation Considerations

Validation should include at minimum:

* provider-level regression proving null latest profile still builds snapshot;
* Engineering Story Context WebMvc regression proving `200` for existing
  project + missing latest profile;
* preservation of `404` for truly missing project;
* targeted repository tests for affected project-context/profile classes.

## Conclusion

The bug is a real backend contract defect in `devlog-ai`.

The strongest evidence is the contradiction between:

* live runtime behavior:
  * Engineering Story Context returns `404 ENTITY_NOT_FOUND` because latest
    project profile is missing
* existing provider tests:
  * missing latest profile is already treated as a valid, graceful state

This is an appropriate, well-bounded bugfix story.
