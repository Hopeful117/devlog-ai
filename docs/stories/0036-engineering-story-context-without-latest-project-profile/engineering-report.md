# Engineering Report

## Story

Story 0036 — Gracefully Build Engineering Story Context Without a Latest Project Profile.

## Objective

Restore the `engineering-story-context` read path for valid projects that do
not yet have a persisted latest project profile, without weakening the
dedicated latest-profile endpoint semantics or introducing any write-side
fallback.

## Repository Analysis Summary

The approved analysis established that the observed `404 ENTITY_NOT_FOUND` was
not caused by missing routing or an unknown project.

The endpoint already existed and the target project also existed.

The failure came from `ProjectContextProviderImpl` reusing
`projectProfileService.getLatestByProject(projectId)` as if a latest profile
were mandatory for context assembly. That assumption conflicted with both the
nullable `latestProjectProfile` field already present in
`ProjectContextSnapshot` and the intent already expressed in
`ProjectContextProviderTest`.

The analysis therefore scoped the fix to the composition layer, not to the
profile-service contract.

## Implementation Plan Summary

The approved plan kept the bugfix deliberately narrow:

* degrade gracefully only when the project exists and the missing entity is the
  latest `Project profile`;
* preserve the strict `404` semantics of
  `GET /api/v1/projects/{projectId}/latest-profile`;
* add regression coverage for the real throwing behavior seen in production;
* verify that downstream Engineering Story Context construction still succeeds
  when `latestProjectProfile` is `null`.

No migration, frontend change, profile auto-generation, or Engineering-Skills
adapter change was approved.

## Implementation Summary

The implementation was completed exactly in that scope.

`ProjectContextProviderImpl` now routes latest-profile lookup through a
dedicated helper that catches only the exact
`EntityNotFoundException("Project profile", projectId)` case and translates it
to `null`.

Project existence remains checked separately and still fails normally when the
project itself does not exist.

Regression coverage was added at two levels:

* provider-level coverage now reproduces the real production behavior where the
  profile service throws instead of returning `null`;
* service-level coverage now proves Engineering Story Context can still be
  built when `latestProjectProfile` is absent.

## Modified Files

* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java`
  Narrow missing-profile fallback in the context-assembly path.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderTest.java`
  Regression test for thrown `EntityNotFoundException("Project profile", projectId)`.
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceTest.java`
  Service-level compatibility test for `latestProjectProfile == null`.

## Created Files

* `docs/stories/0036-engineering-story-context-without-latest-project-profile/story.md`
* `docs/stories/0036-engineering-story-context-without-latest-project-profile/repository-analysis.md`
* `docs/stories/0036-engineering-story-context-without-latest-project-profile/implementation-plan.md`
* `docs/stories/0036-engineering-story-context-without-latest-project-profile/implementation-report.md`
* `docs/stories/0036-engineering-story-context-without-latest-project-profile/code-review.md`
* `docs/stories/0036-engineering-story-context-without-latest-project-profile/engineering-report.md`

## Architecture Impact

No architectural boundary changed.

The fix preserves the existing ownership model:

* `ProjectRepository` remains the authority for project existence;
* `ProjectProfileServiceImpl` remains strict for dedicated latest-profile
  retrieval;
* Engineering Story Context remains a best-available read model that may carry
  an absent latest profile when the rest of the context is still valid.

No ADR update was required.

## Validation

Executed validation:

* `./mvnw -Dtest=ProjectContextProviderTest,EngineeringStoryContextControllerWebMvcTest,EngineeringStoryContextServiceTest test`
  passed with `23` tests, `0` failures, `0` errors.
* `git diff --check` passed.

Live HTTP behavior after the code change was not revalidated against a rebuilt
or restarted local backend during this Story.

That limitation is acceptable for this Story because the automated backend
tests directly exercise the fixed code path and preserve the missing-project
error contract.

## Documentation Reconciliation

Documentation update: Not required.

The fix changes the robustness of an existing backend read path without adding
new endpoints, configuration, operator procedures, or user-facing setup steps.

## Review Outcome

Code Review found no findings.

The implementation was judged compliant with the approved Story and plan,
correctly scoped, and appropriately validated for a backend bugfix of this
size.

Human Code Review approval: granted.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Remaining Work

No required work remains for Story 0036 itself.

Separate follow-up remains appropriate on the Engineering-Skills side to
correct the branch/workflow issue that caused this Story's final artifacts to
be stranded outside the expected branch flow.

## Lessons Learned

* Reusing a strict lookup service inside a best-effort read model can create
  accidental coupling if optional state is treated as mandatory.
* Tests that model graceful behavior with `null` stubs should also cover the
  real production exception path when the underlying service does not actually
  return `null`.
* Final workflow artifacts need to be anchored to the branch that contains the
  implementation; otherwise the audit trail becomes correct in content but
  misplaced in history.

## Final Status

Completed
