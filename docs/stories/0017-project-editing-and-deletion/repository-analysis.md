# Repository Analysis — Story 0017

## Status

Ready for human approval.

## Executive Summary

Story 0017 is feasible within the existing Spring Boot and Angular project boundaries, but the two
requested capabilities are at different maturity levels:

* **Editing already exists in the backend** through `PUT /api/v1/projects/{slug}` and
  `ProjectService.update`. The Angular application has no typed update request and no edit UI.
* **Permanent deletion does not exist.** Adding only `repository.delete(project)` would currently
  fail for populated projects because project ownership is represented by many foreign keys with
  inconsistent `ON DELETE` behavior.

The recommended direction is to preserve the existing stable slug update contract, add explicit
backend validation for provided update values and duplicate names, expose typed edit behavior in
the project cockpit, and implement deletion as one project-row delete inside a service transaction
after a Flyway migration makes project-owned database relationships consistently cascade.

The migration must be designed from the full relationship graph, including indirect tables. JPA
entity cascades should not become the primary deletion mechanism because `Project` intentionally
does not model every owned collection and database referential integrity is already authoritative.

## DevLog Context Outcome

`DEVLOG_CONTEXT_ERROR: DevLog request failed: fetch failed. Repository Analysis continues without
DevLog.`

The configured local DevLog endpoint was unavailable. Analysis continued through targeted direct
repository inspection. The repository is authoritative.

## Current Repository State

* Canonical repository: `/home/ludo/Bureau/workspace/devlog-ai`
* Branch: `main`, tracking `origin/main`
* Initial working tree: clean
* Story 0017 adds only its lifecycle directory at this gate
* Previous Story 0016 is completed and has no remaining work

## Existing Project Backend

### HTTP and service contracts

`ProjectController` currently exposes:

* `POST /api/v1/projects`;
* `GET /api/v1/projects`;
* `GET /api/v1/projects/{slug}`;
* `PUT /api/v1/projects/{slug}`;
* `PATCH /api/v1/projects/{slug}/archive`.

`ProjectServiceImpl.update` resolves by slug, applies `ProjectMapper.updateProject`, saves, and
returns `ProjectResponse`. `ProjectMapper` ignores null request properties, so the endpoint behaves
like a partial update despite using PUT.

There is no delete method in `ProjectService`, no delete controller route, and no project deletion
test.

### Update contract gaps

`UpdateProjectRequest` applies only `@Size(max = 100)` to name and `@Size(max = 5000)` to
description. Consequently:

* null fields are ignored;
* a blank name is currently accepted by Bean Validation;
* the service does not explicitly check duplicate names;
* `projects.name` is unique in PostgreSQL, so a collision would currently surface as a generic
  unhandled persistence failure and `500 INTERNAL_ERROR`;
* the slug is not remapped and therefore remains stable, which matches the Story;
* current tests verify only successful mapper delegation and unknown-project behavior.

The update contract should remain compatible with description-only requests already supported by
the API. A provided name must be trimmed/non-blank and unique, while an omitted name may remain a
no-op. Planning should select a validation mechanism that expresses this without turning the
existing partial PUT into an accidental full replacement.

## Existing Project Frontend

`ProjectService` exposes only list, detail, and create calls. `project.models.ts` has no typed update
request. `ProjectsPage` already demonstrates the repository's reactive-form and RxJS conventions:

* typed reactive forms;
* trim-before-submit;
* `exhaustMap` for duplicate-submission prevention;
* state unions for pending/error handling;
* centralized `toRequestError` mapping.

`ProjectDetailPage` is read-only. Its view model is route-driven and combines the project with
sources, analyses, deliverables, and insights. It uses a declarative observable pipeline and has an
explicit test forbidding imperative subscriptions.

The smallest coherent UI change belongs in the project detail cockpit. It should add local edit and
delete interaction state while preserving the declarative loading boundary. After update, the
returned project must replace or trigger reload of the displayed project. After delete, navigation
to `/projects` should occur only after the HTTP request completes successfully.

No reusable modal/dialog abstraction was found in the inspected project feature. Planning should
prefer a small accessible in-page confirmation region or native dialog implemented in repository
style rather than introduce a general UI framework for one destructive action.

## Persistence Ownership Analysis

The `Project` entity contains no JPA child collections and no entity cascade configuration. This is
consistent with bounded aggregate loading, but means deletion semantics must be expressed in the
database or in an explicit deletion coordinator.

### Direct project foreign keys

The schema contains direct project ownership from:

* `knowledge_events` — no delete action;
* `decisions` — no delete action;
* `artifacts` — no delete action;
* `documentations` — no delete action;
* `milestones` — `ON DELETE CASCADE`;
* `analyses` — `ON DELETE CASCADE`;
* `insights` — `ON DELETE CASCADE`;
* `validatable_proposals` — no delete action;
* `sources` — no delete action;
* `project_profile_snapshots` — no delete action;
* `generated_deliverables` — `ON DELETE CASCADE`;
* `project_commits` — `ON DELETE CASCADE`.

A populated project with any non-cascading direct relationship cannot currently be deleted by
deleting its project row.

### Indirect ownership and cross-links

The migration graph also includes:

* analysis-owned facts, observations, AI tasks, execution diagnostics, and collection warnings;
* proposals linked to analyses;
* validations linked to proposals;
* insights linked to analyses and optionally to proposals;
* profile snapshots linked to analyses;
* generated deliverables linked optionally to analyses;
* deliverable-insight join rows;
* project commits linked to sources, plus parent and changed-file rows.

Several of these edges already cascade, while others use restrictive or set-null behavior. The
final Flyway migration must inspect and alter constraints by their exact existing names. It must
ensure that deleting one project can traverse the complete owned graph without ordering failures,
while preserving intentional behavior when a subordinate entity is deleted independently.

### Recommended deletion authority

Use PostgreSQL foreign keys as the primary ownership enforcement and make every direct
project-owned relationship cascade from `projects`. Reconcile indirect restrictive edges only where
they can block that root deletion or leave orphans. Keep optional provenance relationships as
`SET NULL` when independent deletion semantics require the surviving record, otherwise use cascade
where the child is exclusively owned.

Then implement a small transactional service operation:

1. resolve the project by slug or return the standard 404;
2. delete that exact resolved project;
3. allow the database to enforce the reviewed cascade graph;
4. return only after successful transaction completion.

This is simpler and less error-prone than encoding a long ordered sequence of repository deletes in
Java. It also covers future access paths that delete projects outside this controller, provided new
project-owned tables follow the same schema convention.

## API Error and Concurrency Boundaries

The standard `GlobalExceptionHandler` already maps `EntityNotFoundException` to 404, validation
errors to 400, project slug conflicts to 409, generic conflicts to 409, and unexpected errors to a
sanitized 500. The new route can reuse these contracts.

Relevant edge behavior:

* concurrent deletion after lookup may produce a no-op or persistence race unless transaction and
  repository behavior are tested;
* editing after deletion must return 404 when lookup observes no project;
* duplicate name update needs an intentional conflict rather than a database exception translated
  as 500;
* integrity failures must not expose constraint names or database details;
* the frontend should remain on the detail page on every failed update/delete request.

No authentication or authorization layer was found in the affected path. Adding one would be a
materially separate capability and remains out of scope.

## Architecture and Documentation Constraints

Relevant established decisions include:

* ADR-003 and ADR-005: the Java Core owns project state and authoritative business rules; Angular
  owns user interaction;
* ADR-021: sources are technical inputs attached to a project, while external repository lifecycle
  remains separate from project identity;
* ADR-043: trusted project knowledge must not be autonomously deleted by agents; Story 0017 is an
  explicit human UI action, not an agent autonomy expansion.

Deletion must remove only DevLog persistence. It must not delete cloned workspaces, local source
directories, or remote repositories. Repository Analysis found no architectural need for a new ADR
if planning follows the existing ownership boundaries. A new ADR becomes necessary only if planning
changes aggregate ownership or adopts soft deletion/event retention instead of the Story's approved
hard-delete semantics.

README/API documentation and any canonical UI behavior documentation describing project management
will require reconciliation. The roadmap should change only if it currently tracks CRUD as a
planned capability.

## Affected Areas

### Backend

* `project/controller/ProjectController.java`
* `project/service/ProjectService.java`
* `project/service/ProjectServiceImpl.java`
* `project/dto/request/UpdateProjectRequest.java`
* project conflict/error handling as required
* a new Flyway migration after V29
* service, controller, integration, and migration-oriented tests

### Frontend

* `features/projects/project.models.ts`
* `features/projects/project.service.ts`
* `features/projects/project-detail-page.ts`
* `features/projects/project-detail-page.html`
* `features/projects/project-detail-page.scss`
* project service and detail component tests

### Documentation

* `README.md` and relevant canonical API/UI documentation
* `docs/roadmap.md` only when factually affected
* Story lifecycle artifacts

## Testing Strategy Required by Planning

The implementation plan must include:

* focused service and WebMvc tests for update/delete contracts;
* a PostgreSQL-backed integration test or equivalent real-schema validation proving cascades across
  every direct ownership branch and representative indirect chains;
* another-project isolation assertions;
* migrated-schema validation, not only entity mocks;
* Angular HTTP service tests for encoded update/delete URLs and bodies;
* Angular component tests for edit initialization, validation, success/error state, confirmation,
  cancellation, duplicate-submit prevention, and post-delete navigation;
* full backend verify, full frontend test/build/lint or repository-equivalent checks;
* authenticated SonarQube Quality Gate and local Docker/API/UI validation when configured.

Mock-only service tests are insufficient for the cascade requirement.

## Risks and Mitigations

1. **Incomplete cascade graph causes runtime deletion failure.** Inventory every direct and indirect
   constraint, apply one named Flyway migration, and verify against PostgreSQL with populated data.
2. **Over-broad cascade deletes another project's records.** Delete only one resolved project and
   assert a second complete project remains unchanged.
3. **JPA and database cascade conflict.** Keep `Project` free of broad child collections and make
   the database the primary deletion authority.
4. **Duplicate updated name becomes a 500.** Add deterministic uniqueness checking plus database
   race fallback compatible with the standard conflict contract.
5. **PUT validation breaks description-only clients.** Preserve nullable omitted fields while
   rejecting a provided blank name.
6. **UI accidentally submits destructive action.** Separate delete controls, require explicit
   project-specific confirmation, and disable repeat submission.
7. **External repositories are mistaken for owned data.** Delete only persistence records; never
   invoke filesystem or remote-provider deletion.
8. **Reactive project detail state becomes imperative and fragile.** Preserve observable state
   composition and extend existing state/event patterns with focused tests.

## Open Questions Resolved for Planning

* **Does editing change the slug?** No. Slug and UUID remain stable.
* **Does editing include status?** No. Existing lifecycle endpoints retain status ownership.
* **Is deletion reversible?** No. Archive is the reversible alternative.
* **Does deletion remove external repositories or files?** No.
* **Where is confirmation enforced?** In the human-facing frontend; backend remains a deterministic
  API and does not infer UI confirmation state.
* **What owns cascade behavior?** PostgreSQL/Flyway, with a small transactional Java entry point.

## Recommendation

Approve this Repository Analysis and proceed to Implementation Planning.

Planning should preserve the existing update route and stable identity, add only the missing typed
frontend operations and interaction states, and use one comprehensive Flyway ownership migration
rather than a Java-level manual deletion chain.

No implementation file should be modified before explicit human approval of this analysis and the
subsequent Implementation Plan.
