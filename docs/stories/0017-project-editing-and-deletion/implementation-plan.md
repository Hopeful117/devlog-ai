# Implementation Plan — Story 0017

## Status

Ready for human approval.

## Overview

Implement project editing and permanent deletion through the existing Spring Boot project API and
Angular project cockpit.

Editing will preserve the current `PUT /api/v1/projects/{slug}` contract and its partial-update
compatibility. A provided name will be normalized, validated as non-blank, checked for uniqueness,
and persisted without changing the project's UUID or slug. The Angular detail page will expose a
typed reactive edit form and render the persisted response immediately after success.

Deletion will add `DELETE /api/v1/projects/{slug}`. The service will resolve one project, delete it
inside a transaction, flush the operation, and rely on an exhaustive Flyway migration to cascade
through DevLog-owned persistence. The `Project` entity will not gain broad child collections or JPA
cascades. External repositories and filesystem workspaces remain outside deletion scope.

The project cockpit will place deletion in a separate danger zone. The user must deliberately enter
the exact current project name before the destructive request becomes available. Successful
deletion navigates to `/projects`; failure preserves the detail page and its data.

## Planned Changes

### 1. Make the update request contract explicit and compatible

Keep `UpdateProjectRequest` nullable-field semantics so existing description-only clients remain
valid. Add validation that applies only when `name` is present:

* normalize the provided name with `trim()` in the application service;
* reject an empty normalized name as a validation failure;
* enforce the existing 100-character name and 5,000-character description limits;
* leave an omitted name unchanged;
* define description clearing explicitly: a present empty string is persisted as an empty
  description, while an omitted description remains unchanged.

Use a focused request-level constraint or service validation following existing exception-handler
conventions. Do not change the endpoint to PATCH or require all fields, because that would be a
public compatibility break unrelated to the user workflow.

Before applying the mapper, compare the normalized requested name with the current name. When it is
different, query for another project using that name while excluding the current project ID. Return
the standard `409 RESOURCE_CONFLICT` contract for a duplicate. Keep the database uniqueness
constraint authoritative for races: persist with `saveAndFlush`, translate a confirmed name
collision to the same conflict response, and rethrow unrelated integrity failures for sanitized
global handling.

Do not regenerate or modify the slug. Do not expose UUID, slug, timestamps, or status in the update
request.

### 2. Extend the project repository only with bounded identity queries

Add repository methods needed for deterministic uniqueness checks, using exact Spring Data method
derivation or an explicit bounded query:

* find/exists by name while excluding one project UUID;
* retain existing slug lookup and slug existence behavior.

Do not add bulk child-delete queries or repository methods that accept arbitrary tables or paths.
Project deletion remains a single resolved-entity operation.

### 3. Add the transactional project deletion service

Extend `ProjectService` with `delete(String slug)` and implement it in `ProjectServiceImpl`:

1. enter a transaction;
2. resolve the exact project by slug;
3. throw the existing `EntityNotFoundException` when absent;
4. delete the resolved project entity;
5. flush before returning so constraint or persistence failures are observed inside the service
   boundary;
6. commit only when the complete database cascade succeeds.

No external cleanup callback, workspace deletion, remote-provider request, AI task, or asynchronous
event is executed. A failure rolls back the project row and every cascaded change.

### 4. Add the DELETE API route

Add `DELETE /api/v1/projects/{slug}` to `ProjectController`. Delegate to the service and return
`204 No Content` with an empty body.

Reuse the existing error contract:

* unknown slug → `404 ENTITY_NOT_FOUND`;
* duplicate update name → `409 RESOURCE_CONFLICT`;
* invalid update request → `400 VALIDATION_FAILED`;
* unexpected persistence/integrity defect → sanitized `500 INTERNAL_ERROR` with correlation ID and
  server-side logging.

Do not add a request-body confirmation token to the backend. The API expresses deterministic
deletion; the explicit human confirmation belongs to the frontend interaction boundary.

### 5. Normalize project ownership with one Flyway migration

Create `V30__cascade_project_owned_data_on_delete.sql`. Before writing it, re-enumerate all current
foreign keys from the authoritative V1–V29 migrations and the PostgreSQL schema so no dependency
added outside the initial analysis is missed.

Alter non-cascading direct project foreign keys to `ON DELETE CASCADE`, preserving their current
columns, nullability, indexes, and names:

* `knowledge_events.project_id`;
* `decisions.project_id`;
* `artifacts.project_id`;
* `documentations.project_id`;
* `validatable_proposals.project_id`;
* `sources.project_id`;
* `project_profile_snapshots.project_id`.

Retain already-cascading direct relationships for milestones, analyses, insights, generated
deliverables, and project commits.

Reconcile indirect constraints required for coherent ownership:

* proposals owned through analyses must not block analysis/project deletion;
* validations owned by proposals must cascade with proposals;
* optional insight provenance links to proposals and validations should use `ON DELETE SET NULL`
  rather than delete trusted insights when provenance is removed independently;
* generated-deliverable/insight join rows must disappear when either owned side is deleted;
* existing analysis diagnostics, facts, observations, AI tasks, source commit history, commit
  parents, and changed files retain or receive cascading ownership as required by the final graph;
* optional analysis references that intentionally survive subordinate deletion retain
  `ON DELETE SET NULL` where root project deletion is still guaranteed by their direct project FK.

Use explicit `DROP CONSTRAINT` / `ADD CONSTRAINT` statements with repository-established names.
Do not disable constraints, issue data cleanup deletes, or recreate tables. The migration must be
safe for populated databases and idempotence is governed by Flyway versioning, not conditional SQL.

### 6. Add real PostgreSQL cascade verification

Add maintained Testcontainers PostgreSQL test dependencies scoped to tests. Use the Spring Boot
Testcontainers integration or a minimal dynamic datasource configuration compatible with Spring
Boot 4.1 so Flyway runs V1 through V30 against a real disposable PostgreSQL instance.

Create a focused integration test that inserts two projects and representative rows for every
direct project ownership branch plus all important indirect chains. Delete only the first project
through the real service/API boundary and verify:

* the first project and every owned descendant are absent;
* no orphan join/provenance/history/diagnostic row remains;
* every row belonging to the second project remains;
* Flyway reached V30 on a fresh schema;
* an injected or reproducible restrictive-integrity failure rolls back the root deletion where a
  safe test mechanism exists.

If CI cannot provide Docker, the test may use Testcontainers' standard skip/failure convention only
if the repository quality policy explicitly permits it. Local completion still requires the test to
run successfully against Docker/PostgreSQL; H2 or mocks cannot substitute for cascade validation.

### 7. Extend backend unit and Web MVC coverage

Update `ProjectServiceTest` for:

* successful normalized name/description update;
* description-only update compatibility;
* explicit empty-description clearing;
* stable UUID and slug;
* blank provided name rejection;
* duplicate name conflict excluding the current project;
* unknown-project update;
* successful transactional deletion delegation and flush;
* unknown-project deletion;
* unrelated integrity-failure propagation.

Update `ProjectControllerWebMvcTest` for:

* `DELETE /api/v1/projects/{slug}` returning 204 and calling the service;
* standard 404 deletion response;
* blank/oversized update validation;
* duplicate update conflict serialization;
* unchanged create/read/archive routes.

Update shared error tests only when a new targeted exception or mapping is actually required. Prefer
the existing `ConflictException` and `RESOURCE_CONFLICT` contract unless implementation evidence
shows a project-specific code is necessary.

### 8. Add typed Angular edit and delete operations

Extend `project.models.ts` with an `UpdateProjectRequest` containing only `name` and `description`
as optional fields under the compatible API contract.

Extend `ProjectService` with:

* `updateProject(identifier, request): Observable<ProjectDetail>` using encoded slug and PUT;
* `deleteProject(identifier): Observable<void>` using encoded slug and DELETE.

Extend `project.service.spec.ts` to verify method, encoded URL, request body, typed returned project,
and empty 204 deletion response. Existing list/detail/create tests remain unchanged.

### 9. Add a reactive edit interaction to the project cockpit

Add a project-actions area in `ProjectDetailPage` with an `Edit project` control. When activated:

* populate a typed reactive form from the currently loaded project;
* validate required trimmed name, maximum lengths, and submission state;
* submit the normalized name and description through `updateProject`;
* ignore duplicate submissions through `exhaustMap` or the established equivalent;
* replace/refetch the displayed project from the successful persisted response while keeping its
  slug route stable;
* close/reset the form only after success;
* keep entered values and show `toRequestError(error, 'project')` on failure;
* expose pending and error feedback through status/alert semantics.

Preserve the current route-driven observable composition and the test prohibiting imperative
subscriptions. Introduce a small refresh/project-state subject only if necessary, keeping event
streams declarative and shared to avoid duplicate HTTP loading.

Do not redesign the entire cockpit or move editing to a new route unless implementation evidence
shows the in-page form cannot preserve the current component architecture.

### 10. Add an accessible destructive confirmation interaction

Add a visually separate `Danger zone` below the ordinary project content. Selecting `Delete
project` reveals an in-page confirmation region rather than immediately calling the API.

The region must:

* state that deletion is permanent and removes DevLog-owned project data;
* name the exact project;
* require the user to enter the exact current project name;
* keep the confirmation button disabled until the value matches exactly;
* offer a non-destructive cancel action;
* prevent repeated submission while pending;
* announce request errors without closing the region;
* use semantic labels, keyboard-operable controls, visible focus, and text in addition to danger
  color.

On successful `204`, navigate to `/projects`. Do not mutate the list optimistically and do not
navigate on error. If an edit changes the project name, immediately update the required confirmation
value to the persisted current name.

### 11. Extend Angular component coverage

Refactor the `ProjectDetailPage` test fixture only enough to supply `updateProject`, `deleteProject`,
and a test router. Add tests for:

* edit form activation and pre-population;
* trim, required, and maximum-length validation;
* disabled/pending duplicate submission;
* successful persisted update rendering with stable route;
* update failure with retained form values;
* delete region opening and cancellation;
* exact-name confirmation requirement;
* successful deletion navigation;
* deletion failure without navigation or false success;
* accessible labels, alert/status output, and destructive explanatory text;
* continued declarative implementation without direct `.subscribe()`.

Use controlled subjects where necessary to prove pending-state behavior rather than relying on
timing.

### 12. Reconcile canonical documentation

Update `README.md` where project management and REST endpoints are described:

* editable fields and stable slug behavior;
* PUT update and DELETE response contracts;
* permanent deletion and database-owned cascade boundary;
* explicit statement that external repositories/workspaces are not deleted.

Update `docs/ui-ux.md` only in the project cockpit/action sections affected by the implemented edit
and danger-zone interaction. Update `docs/roadmap.md` only if its current factual capability list
needs reconciliation after completion. Do not create a new ADR unless implementation materially
changes the approved PostgreSQL ownership strategy or project aggregate boundary.

Record the documentation outcome explicitly in the Implementation Report before Code Review.

### 13. Validate the complete behavior

Run validation in this order:

1. focused backend project service/controller tests;
2. PostgreSQL Testcontainers migration/cascade integration test;
3. complete backend `./mvnw -q verify` and JaCoCo check;
4. focused Angular project service/detail tests;
5. complete frontend `npm test -- --watch=false` using the supported Angular 22 runner options;
6. frontend production `npm run build`;
7. formatting check with the repository's Prettier configuration or `npx prettier --check` on
   changed frontend files;
8. rebuild/start the Docker stack against a migrated PostgreSQL database;
9. API validation for update, duplicate-name conflict, deletion of a populated disposable project,
   404 after deletion, and isolation of a second project;
10. browser validation of edit success/error, typed confirmation, cancellation, delete success,
    navigation, and responsive/accessibility basics;
11. authenticated SonarQube analysis with Quality Gate wait.

The Implementation Report must record commands, results, test counts, coverage, Quality Gate,
new-code findings, migration version, and any residual limitation. A failed cascade check, build,
test suite, or Quality Gate prevents successful implementation completion.

## Expected Files to Modify

### Backend

* `backend/pom.xml` — PostgreSQL Testcontainers test dependencies.
* `backend/src/main/java/com/hopeful117/devlogai/project/controller/ProjectController.java` — DELETE
  route.
* `backend/src/main/java/com/hopeful117/devlogai/project/dto/request/UpdateProjectRequest.java` —
  provided-name validation contract.
* `backend/src/main/java/com/hopeful117/devlogai/project/repository/ProjectRepository.java` — bounded
  name uniqueness lookup.
* `backend/src/main/java/com/hopeful117/devlogai/project/service/ProjectService.java` — delete
  operation.
* `backend/src/main/java/com/hopeful117/devlogai/project/service/ProjectServiceImpl.java` — update
  normalization/conflict handling and transactional delete.
* `backend/src/test/java/com/hopeful117/devlogai/project/service/ProjectServiceTest.java` — update and
  delete service coverage.
* `backend/src/test/java/com/hopeful117/devlogai/project/controller/ProjectControllerWebMvcTest.java`
  — update/delete API coverage.

Additional shared exception tests may change only if the existing standard conflict contract cannot
represent a duplicate project name truthfully.

### Frontend

* `frontend/src/app/features/projects/project.models.ts` — typed update request.
* `frontend/src/app/features/projects/project.service.ts` — update/delete HTTP operations.
* `frontend/src/app/features/projects/project.service.spec.ts` — HTTP contract coverage.
* `frontend/src/app/features/projects/project-detail-page.ts` — edit/delete reactive state.
* `frontend/src/app/features/projects/project-detail-page.html` — project actions, edit form, danger
  zone, and confirmation.
* `frontend/src/app/features/projects/project-detail-page.scss` — accessible action/form/danger
  styling.
* `frontend/src/app/features/projects/project-detail-page.spec.ts` — complete interaction coverage.

### Documentation

* `README.md` — project CRUD/API and deletion boundary.
* `docs/ui-ux.md` — project cockpit actions when canonical behavior is affected.
* `docs/roadmap.md` — only if factual status reconciliation is required.

## Expected Files to Create

* `backend/src/main/resources/db/migration/V30__cascade_project_owned_data_on_delete.sql` — complete
  ownership constraint migration.
* A PostgreSQL/Testcontainers project deletion integration test under
  `backend/src/test/java/com/hopeful117/devlogai/project/` following final package conventions.
* A focused optional-name validation annotation/validator and tests only if standard Bean Validation
  cannot express nullable-but-non-blank-when-present behavior cleanly.
* Story 0017 implementation, review, and final reporting artifacts at their permitted workflow
  stages.

## Dependencies and Existing-Solution Preflight

The CRUD endpoints, Spring Data repository, Bean Validation, Angular reactive forms, RxJS state
patterns, centralized request-error mapping, Flyway, and PostgreSQL already solve the application
capabilities required by this Story. No custom framework or paid service is justified.

For real migration/cascade tests, use the maintained Testcontainers PostgreSQL module and Spring
Boot Testcontainers integration rather than building a custom Docker orchestration harness. These
are test-scoped dependencies and do not alter the production runtime.

No new production dependency is planned.

## Acceptance-Criteria Traceability

* AC-1, AC-2, AC-3 → steps 1, 2, 7, 9, and 11.
* AC-4 → step 8.
* AC-5 → steps 3, 4, and 7.
* AC-6, AC-7 → steps 3, 5, and 6.
* AC-8, AC-9, AC-10 → steps 9, 10, and 11.
* AC-11 → steps 6 and 7.
* AC-12 → steps 8 and 11.
* AC-13 → steps 7, 11, and 13.
* AC-14 → step 12.
* AC-15 → step 13.

## Implementation Order

1. Reconfirm repository/branch/working-tree state and full FK graph.
2. Add failing backend update/delete contract tests.
3. Add V30 and the failing PostgreSQL cascade integration test fixture.
4. Implement backend validation, conflict handling, transactional deletion, and controller route.
5. Run focused backend and real PostgreSQL tests.
6. Add failing Angular HTTP and component tests.
7. Implement typed frontend service operations and cockpit edit/delete interactions.
8. Run focused frontend tests and production build.
9. Reconcile README/UI documentation and determine roadmap impact.
10. Run complete backend/frontend/Docker/API/browser/Sonar validation.
11. Produce the Implementation Report with explicit documentation outcome.
12. Perform independent Code Review and stop at Human Approval Gate 3.

## Stop Conditions During Implementation

Implementation must stop and return for human guidance if:

* the authoritative schema contains project-related data whose ownership cannot be determined;
* deletion would require removing external repositories, workspaces, or non-DevLog data;
* a migration cannot preserve existing data safely;
* an existing authentication/authorization contract conflicts with the planned route;
* the stable-slug update decision conflicts with an undiscovered public contract;
* PostgreSQL cascade behavior cannot be validated against the real migrated schema;
* repository state becomes unsafe or materially diverges from the approved plan;
* required tests, build, Docker validation, or Sonar Quality Gate fail without an in-scope fix.

## Completion Checklist

- [ ] Provided update names are trimmed, non-blank, bounded, and unique.
- [ ] Description-only update compatibility is preserved.
- [ ] UUID, slug, timestamps, and status cannot be edited through the general update request.
- [ ] Successful edit returns and displays persisted state without a full browser reload.
- [ ] DELETE resolves one project and returns 204 only after successful transactional deletion.
- [ ] V30 cascades every DevLog-owned project relationship without deleting another project.
- [ ] Optional provenance behavior remains truthful when subordinate records are deleted.
- [ ] External repositories and filesystem workspaces are untouched.
- [ ] Exact-name destructive confirmation is accessible and prevents accidental/duplicate requests.
- [ ] Delete failure preserves the detail page and reports an actionable error.
- [ ] Backend unit, WebMvc, PostgreSQL integration, and complete verify suites pass.
- [ ] Angular service/component, complete test, formatting, and production build checks pass.
- [ ] Docker/API/browser validation passes on the migrated schema.
- [ ] JaCoCo and authenticated SonarQube Quality Gate pass with no new unresolved issue.
- [ ] Canonical documentation is reconciled and recorded in the Implementation Report.
- [ ] Independent Code Review is completed before final approval.

## Recommendation

Approve this Implementation Plan and proceed to implementation.

No implementation or migration file may be modified until the human explicitly approves this plan.
