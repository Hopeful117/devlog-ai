# Story 0036 — Engineering Story Context Without Latest Project Profile — Implementation Report

## Overview

Implemented a narrow bugfix so Engineering Story Context no longer fails with
`404 ENTITY_NOT_FOUND` when the project exists but no latest project profile
snapshot exists yet.

The fix is intentionally scoped to the context-assembly path.

It preserves the current strict behavior of the dedicated
`GET /api/v1/projects/{projectId}/latest-profile` endpoint while making
Engineering Story Context degrade gracefully to:

* `latestProjectProfile = null`

for the specific case:

* project exists;
* latest project profile is missing.

## Modified Files

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java`
  Added a narrow `latestProfileOrNull(projectId)` helper that catches only the
  specific missing-project-profile exception and translates it to `null`.

* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderTest.java`
  Added regression coverage for the real runtime case where
  `getLatestByProject(projectId)` throws
  `EntityNotFoundException("Project profile", projectId)`.

* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceTest.java`
  Added service-level coverage proving context construction still succeeds when
  `ProjectContextSnapshot.latestProjectProfile()` is `null`.

## Implemented Changes

### 1. `ProjectContextProviderImpl` now degrades gracefully for missing latest profile

Before the fix:

* `build(projectId)` confirmed the project exists;
* then called `projectProfileService.getLatestByProject(projectId)` directly;
* if no profile existed, `EntityNotFoundException("Project profile", projectId)`
  aborted the entire context build.

After the fix:

* project existence is still checked first and remains authoritative;
* the provider now treats only the exact missing-project-profile case as
  non-fatal;
* all other exceptions still propagate unchanged.

This keeps the change small and avoids masking unrelated failures.

### 2. Provider tests now reproduce the real throwing behavior

Existing provider tests already modeled missing latest profile as graceful, but
they did so by mocking:

* `when(projectProfileService.getLatestByProject(projectId)).thenReturn(null)`

That did not match the real production implementation.

The new regression test now uses:

* `thenThrow(new EntityNotFoundException("Project profile", projectId))`

and verifies that:

* `provider.build(projectId)` still succeeds;
* `snapshot.latestProjectProfile()` is `null`.

This closes the runtime-vs-test gap identified in the Repository Analysis.

### 3. Service tests now lock the nullable-profile context contract

Added a service-level test proving:

* `EngineeringStoryContextServiceImpl.buildWithRepositoryContext(...)`
  succeeds when `ProjectContextProvider` returns a snapshot whose
  `latestProjectProfile` is `null`.

This ensures the downstream context-building path remains compatible with the
provider’s graceful semantics.

## Validation

### Automated

Command:

```text
./mvnw -Dtest=ProjectContextProviderTest,EngineeringStoryContextControllerWebMvcTest,EngineeringStoryContextServiceTest test
```

Result:

* `23` tests run
* `0` failures
* `0` errors

Covered behaviors:

* context endpoint GET/POST transport behavior;
* shared error handling for malformed requests and missing projects;
* provider-level graceful handling of missing latest profile;
* service-level compatibility with `latestProjectProfile == null`.

### Repository hygiene

Command:

```text
git diff --check
```

Result:

* passed.

### Live HTTP

Observed before implementation:

* existing project `engineering-skills` returned `200`;
* `latest-profile` returned `404`;
* `engineering-story-context` also returned `404 ENTITY_NOT_FOUND` on
  `Project profile`.

Post-change live HTTP was **not** revalidated against the running local
instance because the currently running backend process was not rebuilt/restarted
as part of this implementation step.

Therefore:

* live behavior after restart remains expected but unverified in this
  Implementation Report;
* automated targeted backend tests are the authoritative validation executed in
  this step.

## Documentation Reconciliation

Documentation update: Not required.

Reason:

* the bugfix changes runtime behavior for an existing endpoint contract but does
  not introduce a new API surface, workflow stage, or user-facing repository
  documentation requirement.

## Deviations

No deviation from the approved plan.

The implementation stayed deliberately narrow:

* no profile auto-generation;
* no service-contract broadening in `ProjectProfileServiceImpl`;
* no migration;
* no frontend change;
* no Engineering-Skills adapter change.
