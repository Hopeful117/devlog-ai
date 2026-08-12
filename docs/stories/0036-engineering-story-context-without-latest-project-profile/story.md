# Story 0036 — Gracefully Build Engineering Story Context Without a Latest Project Profile

## Status

Draft

## Priority

High

## Objective

Fix the `engineering-story-context` read API so it can still build usable
project and repository context when the target project exists but has no
persisted latest project profile yet.

Today, the endpoint:

* `POST /api/projects/{projectId}/engineering-story-context`
* `GET /api/projects/{projectId}/engineering-story-context`

returns:

* `404 ENTITY_NOT_FOUND`

for the `Engineering-Skills` project even though the project itself exists in
DevLog.

The failure is not caused by a missing route or unknown project.

It is caused by the context-building path requiring a latest
`ProjectProfileSnapshot` and propagating `EntityNotFoundException("Project profile", projectId)`
instead of degrading gracefully.

## Motivation

The Engineering Story Context endpoint is intended to help coding agents and
humans start work from the best available repository knowledge.

Blocking the endpoint until a project profile exists weakens that purpose:

* newly registered projects cannot benefit from Repository Context selection;
* DevLog context integration becomes brittle for valid projects that have not
  yet produced a profile snapshot;
* Engineering-Skills correctly falls back, but DevLog loses a high-value
  read-side integration for an avoidable reason;
* the runtime behavior contradicts the current `ProjectContextProviderImpl`
  tests, which already model missing profile as a valid state.

The bug is therefore a read-path robustness defect, not a repository-mapping
or HTTP-routing issue.

## Scope

### In Scope

1. Make Engineering Story Context build successfully when:
   * the project exists;
   * the latest project profile does not exist.

2. Preserve the existing response contract:
   * project context is still returned;
   * repository context still builds;
   * `latestProjectProfile` may be `null` / absent in the serialized snapshot
     as already allowed by the domain model.

3. Align implementation and tests around the same missing-profile semantics.

4. Add regression coverage proving that:
   * missing project still returns `404 ENTITY_NOT_FOUND`;
   * missing profile for an existing project does **not** return 404 for the
     Engineering Story Context endpoint.

### Out of Scope

* Generating a project profile automatically as part of context retrieval.
* Changing the dedicated `GET /api/v1/projects/{projectId}/latest-profile`
  endpoint semantics unless implementation analysis proves it is required.
* Changing Engineering-Skills adapter behavior.
* Altering Repository Context ranking, projection limits, or evidence
  selection.
* Broad API error-contract redesign; that belongs to earlier error-handling
  stories.

## Constraints

* Existing projects that genuinely do not exist must still return
  `404 ENTITY_NOT_FOUND`.
* Missing latest project profile must be treated as an allowable incomplete
  project-context state for Engineering Story Context.
* The fix must remain read-only. No background write, profile generation, or
  persistence side effect should be introduced.
* The compact agent projection and the full detail mode must remain compatible
  with the updated semantics.

## Impact

Likely affected backend components:

* `ProjectContextProviderImpl`
* `EngineeringStoryContextServiceImpl`
* project-context tests
* possibly targeted WebMvc tests for context endpoint behavior

Potentially relevant but not expected to change:

* `ProjectProfileServiceImpl`
* `ProjectProfileController`
* Repository Context adapter and projection services

## Acceptance Criteria

* AC-1: `POST /api/projects/{projectId}/engineering-story-context` returns `200`
  for an existing project that has no latest project profile.
* AC-2: `GET /api/projects/{projectId}/engineering-story-context?description=...`
  also returns `200` for that same condition.
* AC-3: The returned context still contains project snapshot data and usable
  repository context.
* AC-4: The missing latest profile is represented as an allowed null/absent
  state rather than a hard error.
* AC-5: Requesting Engineering Story Context for a truly missing project still
  returns `404 ENTITY_NOT_FOUND`.
* AC-6: Existing behavior for projects that do have a latest profile remains
  unchanged.
* AC-7: Automated tests cover both:
  * existing project + missing profile;
  * missing project.
* AC-8: Relevant repository validation succeeds.

## Technical Context

Verified live behavior:

* `GET /api/v1/projects/engineering-skills` → `200` (project exists)
* `GET /api/v1/projects/93441821-2a71-4a1d-93cd-f38369030205/latest-profile`
  → `404`
* `GET /api/projects/93441821-2a71-4a1d-93cd-f38369030205/engineering-story-context?description=test`
  → `404 ENTITY_NOT_FOUND` with message:
  * `Project profile not found with identifier: 93441821-2a71-4a1d-93cd-f38369030205`

Relevant implementation evidence:

* `ProjectContextProviderImpl.build(projectId)` currently calls
  `projectProfileService.getLatestByProject(projectId)` unconditionally.
* `ProjectProfileServiceImpl.getLatestByProject(projectId)` throws
  `EntityNotFoundException("Project profile", projectId)` when no snapshot
  exists.
* `ProjectContextSnapshot` already models `latestProjectProfile` as a nullable
  field.
* `ProjectContextProviderTest` already contains multiple tests asserting that
  missing latest profile is handled gracefully by returning `null`.

## Dependencies

* Existing Engineering Story Context endpoint and projection pipeline
* Project profile snapshot subsystem
* Shared API error handling

## Risks

1. **Masking real project-not-found cases**
   - Mitigation: degrade only when the project exists and the missing entity is
     specifically the latest profile.

2. **Accidental semantic change to `latest-profile` endpoint**
   - Mitigation: keep this Story scoped to Engineering Story Context unless a
     deeper requirement emerges during approved analysis.

3. **Breaking compact/full response parity**
   - Mitigation: add tests at the context endpoint/service level for the
     missing-profile scenario.

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
