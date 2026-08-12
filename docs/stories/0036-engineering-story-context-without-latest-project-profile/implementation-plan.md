# Story 0036 — Engineering Story Context Without Latest Project Profile — Implementation Plan

## Overview

Fix the Engineering Story Context read path so an existing project remains
readable even when no latest project profile snapshot exists yet.

The implementation should be narrow, read-only, and scoped to Engineering Story
Context assembly.

The preferred approach is to preserve the current strict semantics of the
dedicated latest-profile lookup while making `ProjectContextProviderImpl`
degrade gracefully for the specific case:

* project exists;
* latest project profile does not exist.

This aligns runtime behavior with the current `ProjectContextProviderTest`
contract without changing broader profile-service semantics unnecessarily.

## Planned Changes

### 1. Handle missing latest profile explicitly in `ProjectContextProviderImpl`

Update:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java`

Implementation intent:

* keep the existing `projectRepository.findById(projectId)` check as the
  authoritative project-existence gate;
* keep calling `projectProfileService.getLatestByProject(projectId)`;
* catch only the specific `EntityNotFoundException` meaning:
  * entity = `Project profile`
  * identifier = current `projectId`
* translate that case into:
  * `latestProfile = null`
* rethrow every other exception unchanged.

This keeps the fix narrow and avoids silently swallowing unrelated not-found
conditions.

### 2. Preserve strict semantics of the dedicated latest-profile endpoint

Do **not** change:

* `ProjectProfileServiceImpl.getLatestByProject(projectId)`
* `ProjectProfileController`

Why this is preferable for the first bugfix:

* `GET /api/v1/projects/{projectId}/latest-profile` is a direct lookup endpoint;
* returning `404` there is still semantically coherent when no profile exists;
* the observed bug is specifically that Engineering Story Context should not
  depend on that strict lookup behavior.

This avoids broadening a public service contract and limits the change to the
composition layer where the bug actually manifests.

### 3. Add provider-level regression coverage for the real runtime case

Update:

* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderTest.java`

The current tests already stub `getLatestByProject(projectId)` as nullable, but
they do not model the real throwing behavior of `ProjectProfileServiceImpl`.

Add or adjust tests so they prove:

* when `projectProfileService.getLatestByProject(projectId)` throws
  `EntityNotFoundException("Project profile", projectId)`,
  `provider.build(projectId)` still succeeds;
* `snapshot.latestProjectProfile()` is `null`;
* project snapshot and other repository data still flow through normally.

This turns the current implied semantics into an explicit regression test for
the real bug.

### 4. Add endpoint-level regression coverage for Engineering Story Context

Update:

* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`
  or the closest controller/service-level test layer that can express the final
  contract cleanly.

Add regression coverage proving:

* Engineering Story Context still returns `404 ENTITY_NOT_FOUND` for a truly
  missing project;
* Engineering Story Context does **not** return `404` merely because latest
  profile is missing for an existing project;
* both POST body-based and GET compatibility paths preserve this behavior when
  relevant.

If WebMvc-level mocking is too shallow to exercise the provider semantics
directly, add a service-level regression test instead and keep controller tests
focused on transport.

### 5. Add service-level coverage only if the provider change needs it

Potential update:

* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceTest.java`

This is optional unless implementation introduces meaningful service-layer
logic.

If the provider fix alone is sufficient, avoid unnecessary service changes.

If added, coverage should prove:

* `buildWithRepositoryContext(...)` and
* `buildAgentWithRepositoryContext(...)`

can both succeed when `ProjectContextProvider` returns a snapshot with
`latestProjectProfile == null`.

### 6. Keep Repository Context and projection behavior unchanged

Do not change:

* `RepositoryContextAdapter`
* `AgentContextProjectionService`
* ranking/allocation behavior
* compact/full projection limits

The bug is not about repository context quality or projection size.

It is about preventing premature failure before those stages run.

### 7. Validate with targeted backend tests and live HTTP checks

Validation should prove the exact regression is fixed without broad behavioral
drift.

Expected validation:

* targeted project-context/profile test classes;
* relevant WebMvc tests for Engineering Story Context;
* live manual HTTP checks against the local backend when feasible:
  * existing project + no latest profile:
    * Engineering Story Context returns `200`
  * existing project + no latest profile:
    * latest-profile endpoint may still return `404`
  * missing project:
    * Engineering Story Context still returns `404`

## Files to Modify

Expected primary modifications:

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java`
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderTest.java`

Likely additional test updates:

* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextControllerWebMvcTest.java`
* optionally `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceTest.java`

## Files Not Expected to Change

The following should remain unchanged unless implementation reveals an
unexpected coupling:

* `ProjectProfileServiceImpl`
* `ProjectProfileController`
* `RepositoryContextAdapter`
* projection DTOs and projection services
* database migrations
* frontend code
* Engineering-Skills adapter code

## Sequencing

1. Update `ProjectContextProviderImpl` to degrade gracefully only for the
   missing-project-profile case.
2. Add provider-level regression coverage for the real throwing behavior.
3. Add endpoint/service regression coverage for Engineering Story Context.
4. Run targeted backend tests.
5. Run live HTTP verification against the local backend when feasible.
6. Summarize the exact fixed contract in the Implementation Report.

## Validation

Targeted automated validation should include at minimum:

* `ProjectContextProviderTest`
* `EngineeringStoryContextControllerWebMvcTest`
* optionally `EngineeringStoryContextServiceTest` if touched

Manual/live validation should include:

* `GET /api/v1/projects/{projectId}/latest-profile`
  for the Engineering-Skills project still returning `404` if no profile exists;
* `GET /api/projects/{projectId}/engineering-story-context?description=test`
  returning `200` for that same project;
* `POST /api/projects/{projectId}/engineering-story-context`
  with JSON body returning `200` for that same project;
* missing project still returning `404 ENTITY_NOT_FOUND`.

No frontend, migration, or repository-wide validation is required unless the
implementation unexpectedly broadens scope.

## Risks and Controls

### Risk: Catching too much

Control:

* catch only the `Project profile` missing case after project existence is
  already confirmed.

### Risk: Changing latest-profile semantics accidentally

Control:

* leave `ProjectProfileServiceImpl` and `ProjectProfileController` unchanged.

### Risk: Tests still miss the real runtime path

Control:

* add a regression test where `getLatestByProject(projectId)` throws the same
  `EntityNotFoundException` as production, not merely `null`.

### Risk: Hidden coupling in projection layers

Control:

* validate both compact/default and GET/POST compatibility behavior through the
  context endpoint tests and live checks.

## Completion Criteria

The Story is complete when:

* Engineering Story Context returns `200` for an existing project without a
  latest project profile;
* the returned snapshot allows `latestProjectProfile == null`;
* missing project still returns `404 ENTITY_NOT_FOUND`;
* strict latest-profile endpoint behavior remains unchanged;
* regression tests cover the real missing-profile runtime case;
* relevant backend validation succeeds.
