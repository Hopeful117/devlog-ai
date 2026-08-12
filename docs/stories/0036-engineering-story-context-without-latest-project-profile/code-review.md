# Story 0036 — Engineering Story Context Without Latest Project Profile — Code Review

## Findings

No findings.

## Review Scope

Reviewed:

* the approved Story, Repository Analysis, and Implementation Plan;
* `backend/src/main/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderImpl.java`;
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/ProjectContextProviderTest.java`;
* `backend/src/test/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceTest.java`;
* targeted backend validation output.

## Story Compliance

The implementation satisfies the approved Story intent:

* Engineering Story Context now has a graceful path for existing projects
  without a latest profile snapshot;
* the fix remains scoped to context assembly;
* missing project behavior remains distinct and still propagates as `404`;
* no automatic profile generation or other write-side side effect was added.

## Plan Compliance

The implementation follows the approved plan closely:

* the fix is localized to `ProjectContextProviderImpl`;
* `ProjectProfileServiceImpl` and `ProjectProfileController` remain unchanged;
* a regression test now reproduces the real throwing behavior from the profile
  service;
* service-level compatibility with `latestProjectProfile == null` is now
  covered.

No plan deviation was identified.

## Correctness Review

### Narrow exception handling

The implementation catches only the exact missing-project-profile case by
matching the expected `EntityNotFoundException` message for:

* entity = `Project profile`
* identifier = current `projectId`

This is narrow enough to preserve:

* true missing-project failures;
* unrelated not-found conditions;
* current strict latest-profile endpoint semantics.

### Domain-model consistency

The fix aligns runtime behavior with the existing domain model:

* `ProjectContextSnapshot` already allows `latestProjectProfile` to be `null`;
* provider tests already expected graceful missing-profile behavior.

This is a repair of an inconsistency, not a change in architectural direction.

### Risk control

The main risk was over-catching not-found exceptions.

The implementation avoids that by:

* checking project existence separately first;
* only suppressing the exact missing-project-profile exception;
* rethrowing everything else.

That is appropriate for this bugfix scope.

## Validation Review

Executed evidence is sufficient for this Story scope:

* `./mvnw -Dtest=ProjectContextProviderTest,EngineeringStoryContextControllerWebMvcTest,EngineeringStoryContextServiceTest test`
  passed with `23` tests and no failures;
* `git diff --check` passed.

The Implementation Report correctly notes that live HTTP after code change was
not revalidated against a restarted local backend instance during this step.

That is an acceptable limitation because the targeted backend tests directly
cover the fixed code path.

## Residual Risks

Residual risk remains acceptable:

* the narrow message-based exception discrimination depends on the current
  `EntityNotFoundException` message shape;
* if the shared exception class later gains typed fields or different message
  formatting, this provider-level handling may need a small follow-up cleanup.

This is a maintainability observation, not a correctness defect in the current
implementation.

## Conclusion

The implementation is sound, well-scoped, and resolves the identified backend
contract defect without broadening the latest-profile service contract or
changing unrelated DevLog behavior.
