# Code Review Report — Story 0017

## Review Status

Ready for human approval.

## Scope Reviewed

The review inspected:

* Story 0017, the approved Repository Analysis, and the approved Implementation Plan;
* the complete implementation diff;
* V1–V30 ownership constraints and the real PostgreSQL integration test;
* backend update/delete service and HTTP contracts;
* Angular service, reactive component, template, styles, and tests;
* README and canonical UI documentation;
* backend/frontend/Docker/API/SonarQube validation evidence.

## Findings

No Blocker, Major, Minor, or Observation finding remains.

The initial SonarQube Minor finding requiring a static Mockito import was corrected before this
review. Final SonarQube analysis reports no unresolved new-code issue.

## Story Compliance

### Editing

The project detail page exposes a pre-populated accessible edit form for name and description. The
frontend normalizes values, prevents duplicate submission, preserves values on failure, and displays
the persisted response on success.

The backend preserves description-only compatibility, rejects a provided blank name, bounds both
fields, detects duplicate names, and protects database-race conflicts. UUID, slug, status, and
timestamps remain outside the request contract. The update path never regenerates the slug.

Result: Compliant with AC-1 through AC-4.

### Permanent deletion

The backend exposes `DELETE /api/v1/projects/{slug}`, returns 204 only after transactional delete and
flush, and returns the standard 404 for an unknown project. The operation resolves one exact entity
and does not invoke external repository or filesystem deletion.

V30 makes direct project ownership consistent and reconciles indirect constraints. The real
PostgreSQL test verifies every expected delete rule, actual deep-chain deletion, migrated version,
and isolation of another project.

Result: Compliant with AC-5 through AC-7.

### Destructive UI and navigation

Deletion is visually and semantically separated from editing. The UI explains permanence and scope,
requires the exact current project name, supports cancellation, disables duplicate submission, and
navigates only after success. Request failure leaves the project visible and emits an alert.

Controls use native semantic form/button/label elements, the warning is textual rather than
color-only, and the responsive layout preserves the interaction on narrow screens.

Result: Compliant with AC-8 through AC-10.

### Tests, compatibility, documentation, and quality

Backend unit/WebMvc coverage, a real PostgreSQL/Flyway integration test, Angular HTTP/component
coverage, full suites, production build, formatting, Docker/API validation, JaCoCo, and authenticated
SonarQube all pass. Existing project creation/read/archive and unrelated project workflows remain
covered by the complete suites.

README and UI/UX documentation accurately describe stable-slug editing, destructive confirmation,
database scope, external-resource exclusion, and standard errors. The roadmap correctly remains
unchanged.

Result: Compliant with AC-11 through AC-15.

## Architecture Review

* The Java Core remains authoritative for validation and transaction outcome.
* PostgreSQL/Flyway remains authoritative for referential integrity.
* The `Project` entity does not acquire broad child collections or competing JPA cascade behavior.
* Angular remains responsible for explicit human confirmation and does not infer success.
* External repository/workspace lifecycle remains separate from project persistence.
* Existing standard API errors are reused without introducing an unnecessary exception hierarchy.
* Testcontainers is isolated to test scope and is an appropriate maintained solution for real
  migration verification.

No ADR is required because no established architecture decision changed.

## Security and Data-Loss Review

The destructive target is a single server-resolved project slug. SQL deletion is performed by JPA
against the resolved entity; the migration contains fixed constraints and no dynamic SQL. The UI
requires deliberate exact-name confirmation. External filesystem and remote-provider deletion are
explicitly absent.

The permanent-delete API has no authentication boundary because the repository currently has no
authentication/authorization layer. Story 0017 does not weaken an existing control; adding identity
and authorization remains a separate platform capability.

## Validation Review

* Backend: 428 tests, 0 failures/errors/skips.
* JaCoCo: 86.97% line coverage; bundle rule passed.
* PostgreSQL: fresh V1–V30 migration and cascade/isolation test passed on PostgreSQL 17.
* Frontend: 79 tests across 21 files; production build and formatting passed.
* SonarQube: Quality Gate `OK`, new coverage 87.0%, new duplication 0.0%, new violations 0.
* Docker: backend/frontend images built and recreated successfully.
* Live API: create → stable-slug update → delete 204 → subsequent 404 passed.

The evidence is proportionate to the irreversible behavior and covers both fresh and migrated local
database paths.

## Residual Risks

* CI environments running the integration suite require Docker/Testcontainers access.
* Direct API clients must provide their own confirmation UX.
* Future project-owned tables require explicit inclusion in the cascade convention and metadata
  test.

These risks are bounded, documented, and do not represent unfinished Story 0017 scope.

## Recommendation

Technical recommendation: Approve.

Human Code Review approval is still required. No Engineering Report, commit, merge, or finalization
may occur before that approval.
