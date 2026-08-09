# Engineering Report — Story 0017

## Story

Story 0017 — Project Editing and Deletion completes the project CRUD workflow by allowing users to
edit project-maintained fields and permanently delete projects through the Angular application.

## Objective

Give users direct control over project names and descriptions while preserving stable identity, and
provide an explicit irreversible deletion workflow that removes DevLog-owned project data safely
without touching external repositories or filesystem workspaces.

## Repository Analysis Summary

Repository Analysis established that editing already existed in the backend but was absent from the
frontend. Permanent deletion was absent from both layers and could not safely rely on the original
database schema because direct and indirect project foreign keys used inconsistent delete rules.

The approved direction preserved `PUT /api/v1/projects/{slug}`, UUID and slug stability, kept status
under the existing lifecycle endpoint, assigned referential integrity to PostgreSQL/Flyway, and
required exact-name confirmation in the human-facing UI.

## Implementation Plan Summary

The approved plan introduced:

* compatible optional-field update validation and deterministic name conflicts;
* a transactional `DELETE /api/v1/projects/{slug}` contract;
* Flyway V30 for complete project-ownership cascades;
* real PostgreSQL verification through Testcontainers;
* typed Angular update/delete service methods;
* reactive edit and destructive-confirmation interactions;
* canonical documentation reconciliation and full quality validation.

## Implementation Summary

The backend now normalizes provided project names, rejects blank values, preserves description-only
updates, detects duplicate names, and distinguishes unrelated integrity failures. Updates never
regenerate the slug or expose internal identity/lifecycle fields.

Permanent deletion resolves one project by slug, deletes and flushes it inside a transaction, and
returns 204 only after successful persistence. V30 converts every non-cascading direct
project-ownership constraint and reconciles proposal, validation, Insight provenance, and
deliverable join behavior.

The Angular project cockpit now contains a pre-populated edit form and a separate danger zone.
Deletion requires the exact current project name, prevents duplicate submissions, reports failure
without leaving the page, and navigates to the project list only after success.

## Architecture Impact

The implementation preserves the established architecture:

* Java Core owns validation and transaction outcome;
* PostgreSQL and Flyway own referential integrity;
* Angular owns explicit human interaction and confirmation;
* external repository/workspace lifecycle remains separate from project persistence;
* standard API error contracts remain authoritative.

No ADR was required. Testcontainers 2.0.5 was added only in test scope to verify real PostgreSQL
behavior.

## Documentation Reconciliation

Documentation update: Completed.

* `README.md` documents stable-slug editing, archive/delete endpoints, errors, cascade scope, and
  external-resource exclusion.
* `docs/ui-ux.md` documents the ordinary edit action and separate exact-name danger zone.
* `docs/roadmap.md` required no update because it does not track project CRUD status.

## Validation

Final validation evidence:

* backend `./mvnw -q verify`: 428 tests, 0 failures, 0 errors, 0 skipped;
* JaCoCo: 23,791/27,355 lines, 86.97%, rule passed;
* PostgreSQL 17/Testcontainers: fresh V1–V30 migration, constraint rules, deep deletion, and
  cross-project isolation passed;
* frontend: 79 tests across 21 files passed;
* Angular production build passed;
* Prettier check passed;
* SonarQube Quality Gate `OK`;
* new-code coverage 87.0%;
* new duplicated lines 0.0%;
* new violations and unresolved new-code issues 0;
* backend and frontend Docker images built and services restarted successfully;
* live create → update → stable slug → delete 204 → GET 404 validation passed.

The initial SonarQube run identified one Minor static-import smell in a new test. It was corrected,
and the affected validation plus final Quality Gate were rerun successfully.

## Code Review Outcome

The independent Code Review found no remaining Blocker, Major, Minor, or Observation finding and
recommended approval.

Human Code Review approval: granted on 2026-08-09.

## Workflow Approvals

* Repository Analysis: Human approved
* Implementation Plan: Human approved
* Code Review: Human approved

## Residual Risks

* PostgreSQL integration tests require Docker/Testcontainers access in CI.
* Direct API consumers must provide their own destructive-action confirmation.
* Future project-owned tables must extend the V30 cascade convention and metadata test.

These are bounded operational constraints rather than unfinished Story scope.

## Remaining Work

None for Story 0017.

Authentication, authorization, recycle-bin behavior, bulk CRUD, and external repository deletion
remain separate future capabilities.

## Final Status

Completed

No commit, push, or merge was performed automatically.
