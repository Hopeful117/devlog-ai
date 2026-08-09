# Implementation Report — Story 0017

## Status

Implementation and documentation reconciliation completed.

## Summary

Story 0017 completes user-facing project CRUD by exposing the existing update capability in the
Angular project cockpit and adding permanent project deletion across the Angular, Spring Boot, and
PostgreSQL boundaries.

Project updates now normalize provided names, reject blank names, detect duplicate names with the
standard conflict contract, preserve description-only compatibility, and keep UUID/slug identity
stable. Permanent deletion resolves one project, executes transactionally, flushes before returning,
and delegates owned-data cleanup to Flyway-managed PostgreSQL cascades.

The UI provides an in-page edit form and a separate danger zone. Deletion requires entering the
exact current project name, prevents duplicate submissions, navigates only after a successful 204,
and preserves the page on failure.

## Backend Implementation

* Added `DELETE /api/v1/projects/{slug}` returning `204 No Content`.
* Added transactional `ProjectService.delete` with exact slug resolution, entity deletion, and
  explicit flush.
* Preserved the nullable partial-update behavior of `PUT /api/v1/projects/{slug}`.
* Added optional-name non-blank validation and service-level trimming.
* Added name uniqueness lookup excluding the current project.
* Added deterministic `409 RESOURCE_CONFLICT` handling for pre-checked and database-race duplicate
  names while preserving unrelated integrity failures.
* Kept slug, UUID, status, and timestamps outside the update request.

## Persistence Implementation

Flyway migration V30 makes direct DevLog-owned project relationships consistently cascade for:

* knowledge events;
* decisions;
* artifacts;
* documentations;
* proposals;
* sources;
* project profile snapshots.

It retains already-cascading project relationships and reconciles indirect ownership:

* proposals cascade from analyses;
* validations cascade from proposals;
* optional Insight proposal/validation provenance becomes `ON DELETE SET NULL`;
* deliverable/Insight join rows cascade when an Insight is deleted.

The migration changes constraints only. It does not delete existing data, recreate tables, disable
referential integrity, or touch external repositories/workspaces.

## Frontend Implementation

* Added typed `UpdateProjectRequest`, `updateProject`, and `deleteProject` contracts.
* Added a pre-populated reactive edit form in the project cockpit.
* Preserved the declarative observable architecture and avoided imperative subscriptions in the
  production component.
* Applied persisted update responses immediately while keeping the slug route stable.
* Added pending/error states and duplicate-submission prevention with `exhaustMap`.
* Added a separate danger zone with permanent-deletion scope and external-repository exclusion.
* Required exact project-name confirmation and provided cancellation.
* Navigated to `/projects` only after successful deletion.

## Files Modified

### Backend

* `backend/pom.xml`
* `ProjectController.java`
* `UpdateProjectRequest.java`
* `ProjectRepository.java`
* `ProjectService.java`
* `ProjectServiceImpl.java`
* `ProjectControllerWebMvcTest.java`
* `ProjectServiceTest.java`

### Frontend

* `project.models.ts`
* `project.service.ts`
* `project.service.spec.ts`
* `project-detail-page.ts`
* `project-detail-page.html`
* `project-detail-page.scss`
* `project-detail-page.spec.ts`

### Documentation

* `README.md`
* `docs/ui-ux.md`
* Story 0017 lifecycle artifacts

## Files Created

* `backend/src/main/resources/db/migration/V30__cascade_project_owned_data_on_delete.sql`
* `backend/src/test/java/com/hopeful117/devlogai/project/ProjectDeletionPostgresIntegrationTest.java`

## Existing-Solution Outcome

The implementation reuses Spring MVC, Spring Data JPA, Bean Validation, Flyway, PostgreSQL foreign
keys, Angular reactive forms, RxJS, and the existing request-error contract. No custom CRUD or
modal framework was introduced.

Testcontainers 2.0.5, managed through Spring Boot 4.1's dependency catalog, provides isolated real
PostgreSQL migration validation. It is test-scoped and adds no production runtime dependency.

## Validation

### Backend focused tests

Project service and Web MVC tests passed, covering:

* normalized and description-only updates;
* duplicate names and unrelated integrity failures;
* blank update validation;
* stable identity behavior;
* delete success, flush, 204, and unknown-project 404.

### PostgreSQL and Flyway

`ProjectDeletionPostgresIntegrationTest` passed against `postgres:17-alpine` through Testcontainers
2.0.5.

Evidence:

* fresh schema migrated successfully from V1 through V30;
* all direct project foreign keys report `CASCADE`;
* validation/provenance/join constraints report their approved cascade or set-null rules;
* a project with Source → Commit → Changed File data was deleted completely;
* a second project and its full representative chain remained intact.

The existing local PostgreSQL database also migrated from V29 to V30 successfully during the full
Spring context test.

### Complete backend validation

Command: `./mvnw -q verify`

Result:

* 428 tests;
* 0 failures;
* 0 errors;
* 0 skipped;
* JaCoCo rule passed;
* 23,791 of 27,355 lines covered;
* line coverage 86.97%.

### Frontend validation

Commands:

* `npm test -- --watch=false`
* `npm run build`
* `npx prettier --check` on all changed project frontend files

Result:

* 21 test files passed;
* 79 tests passed;
* production build passed;
* Prettier check passed.

Tests cover typed HTTP contracts, encoded identifiers, edit initialization/normalization, exact-name
confirmation, success navigation, and failure without navigation. Template and stylesheet review
verified semantic labels, alert/status feedback, keyboard-native controls, visible textual warning,
and responsive danger-zone layout.

### SonarQube

Authenticated analysis used the ignored repository-root `.env` without exposing the token.

The first analysis correctly failed on one new Minor code smell (`java:S8924`) in a test. The static
import was corrected and the complete affected validation was repeated.

Final result:

* Quality Gate `OK`;
* new-code coverage 87.0%;
* new duplicated lines 0.0%;
* new violations 0;
* unresolved new-code issues 0;
* overall Sonar coverage 87.5%;
* bugs 0;
* vulnerabilities 0;
* security hotspots 0.

### Docker and live API

Backend and frontend images built successfully and the services were recreated from the new images.
Both `http://localhost:18080/api/v1/projects` and `http://localhost:18083/projects` returned 200.

A disposable live project was:

1. created through POST;
2. renamed and given a new description through PUT;
3. verified to retain its original slug;
4. deleted through DELETE with 204;
5. verified absent through GET with 404.

Disposable validation data was removed by the tested delete operation.

## Documentation Reconciliation

Documentation update: Required and completed.

* `README.md` now describes edit/archive/delete project management, stable slug behavior, REST
  contracts, standard errors, transactional database deletion, and the external repository/workspace
  exclusion.
* `docs/ui-ux.md` now defines the ordinary edit interaction and separate exact-name danger-zone
  confirmation.
* `docs/roadmap.md` was inspected and did not require an update because it tracks repository-memory
  product phases rather than project-management CRUD status.
* No ADR was required because the implementation preserves established Java Core, Angular UI,
  Flyway, and PostgreSQL ownership boundaries.

## Plan Deviations

* The Testcontainers 2.0 artifact is named `testcontainers-postgresql`; the local Spring Boot 4.1
  dependency catalog established version 2.0.5. The initial unversioned legacy artifact declaration
  was corrected before validation.
* Automated Angular component/template coverage plus served Docker UI validation replaced manual
  browser automation because no browser-control connector was available in the execution session.
  No acceptance behavior was left untested at the component or HTTP-contract level.

## Residual Risks

* The PostgreSQL integration test requires a Docker-capable test environment.
* Exact-name confirmation is a frontend safety control; direct API consumers remain responsible for
  their own destructive-action confirmation policy.
* Future migrations adding project-owned tables must preserve the V30 cascade convention and extend
  the metadata integration test.

## Final Implementation Outcome

Implementation complete and ready for independent Code Review.
