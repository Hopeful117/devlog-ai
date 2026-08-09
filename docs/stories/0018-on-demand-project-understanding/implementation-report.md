# Implementation Report — Story 0018

## Status

Implementation and documentation reconciliation completed.

## Summary

Story 0018 adds one always-available, user-triggered Project Understanding capability to the
Project Cockpit. A user selects an active Git Source, optionally supplies a revision, and launches
the canonical `describe-project-v1` workflow without assembling an internal Analysis manually.
The same API and application service handle first-time initialization and later refreshes.

The Core validates and synchronizes the selected Source, imports history without a duplicate Git
synchronization, claims or reuses an equivalent in-flight Analysis, and starts the existing
workflow. Each product Analysis retains immutable Source provenance and a deterministic execution
key. PostgreSQL prevents equivalent `PENDING` or `IN_PROGRESS` duplicates while allowing a new
execution after a terminal state.

## Backend Implementation

* Added `POST /api/v1/projects/{projectId}/understanding-executions` with typed Source, revision,
  guidance, Analysis, and `CREATED | REUSED` contracts.
* Added deterministic SHA-256 execution-key generation over versioned normalized inputs.
* Added preparation, short transactional claim/reuse, and orchestration services with distinct
  pre-claim and post-claim failure boundaries.
* Reused the registered `describe-project-v1` Intent and normal Analysis workflow.
* Scoped product collection to the selected Source while retaining all-active-source behavior for
  generic Analyses.
* Reused an already synchronized workspace for history import; the transactional history service
  re-resolves the Source before persistence.
* Preserved the proposal-review boundary: proposals remain `PROPOSED` until explicit validation.

## Persistence Implementation

Flyway migration V31 adds nullable, product-specific fields to `analyses`:

* `selected_source_id`, with `ON DELETE SET NULL`;
* immutable `selected_source_snapshot` JSONB;
* `understanding_execution_key`.

A partial unique index applies only to non-null execution keys in `PENDING` or `IN_PROGRESS` state.
Generic Analyses remain unaffected. Real PostgreSQL tests verify the migration, concurrent
uniqueness, terminal-key release, Source deletion with retained snapshot, and project-deletion
compatibility.

## Frontend Implementation

* Added a typed `ProjectUnderstandingService` and request/response models.
* Added a focused Project Cockpit section without replacing generic Analysis controls.
* Shows `Understand project` initially and `Refresh understanding` after a prior canonical run.
* Keeps the action available after success or failure.
* Shows explicit no-Source guidance, auto-selects one compatible Source, and requires deliberate
  selection when several are available.
* Supports an optional revision, prevents duplicate local submission with `exhaustMap`, announces
  status/errors accessibly, and navigates for both created and reused executions.

## Validation

### Backend

`./mvnw -q verify` passed with 437 tests, zero failures, zero errors, and zero skipped tests. The
JaCoCo bundle rule passed with 85.81% instruction, 65.1% branch, and 82.5% line coverage.

Focused tests cover execution-key normalization, first creation, active-execution reuse, failure
handling, controller contracts, Source ownership/scope, and V31 PostgreSQL behavior. The real
concurrent insert test proves the partial unique index admits only one equivalent active execution.

### Frontend

`npm test -- --watch=false` passed with 85 tests across 23 files. `npm run build` and Prettier checks
for changed frontend files passed. Component tests cover initial/refresh labels, Source selection,
revision normalization, unavailable/error/status states, duplicate-click suppression, retry, and
created/reused navigation.

### SonarQube

Authenticated `clean verify sonar:sonar` with Quality Gate wait passed:

* Quality Gate `OK`;
* new-code coverage 87.1%;
* new duplicated lines 0.0%;
* new violations 0;
* overall coverage 87.5%;
* bugs, vulnerabilities, and security hotspots: 0.

### Docker and live behavior

The Docker backend and frontend images rebuilt and started successfully. Validation used the
existing non-destructive DevLog project `f3d56247-aada-4a76-982b-e6802c0b309c` and Source
`7819103b-37e7-4e15-95ec-fff9a12d21e4`.

The first request created Analysis `bd71ca14-88fa-4028-b3f9-91365d931b44`; an equivalent concurrent
request returned that same Analysis with `REUSED`. The execution completed and produced COMPLETE
Project Profile `298421b0-0a2f-4182-a85d-7567804cbbdf` at resolved commit
`b2f2c8881bf8b7b89331c5161ca4f8cad16cd3f4`, with seven characteristics. Six generated proposals
remained `PROPOSED`; none was automatically validated. The Engineering Story Context endpoint then
returned repository context successfully, confirming the missing-profile bootstrap gap was closed.

The frontend project route served successfully. Interaction behavior was validated through Angular
component tests and the running UI; no browser-automation connector was available for an additional
end-to-end accessibility pass.

`git diff --check` passed.

## Deviations and Decisions

* Both `CREATED` and `REUSED` return HTTP 200. The approved plan required a normal success response
  and an explicit outcome, but did not require HTTP 201.
* The backend accepts optional User Guidance. The V1 Cockpit deliberately exposes only Source and
  revision to keep the primary interaction compact; guidance remains available to API clients.
* Live validation used the user-provided project rather than creating or deleting disposable
  project data. It did not alter Source configuration or validate proposals.
* The plan's browser-validation cases are covered by component/template tests plus a served Docker
  UI, not by an automated browser session.

No deviation changes the synchronous single-Source boundary, canonical Intent, Analysis lifecycle,
or mandatory human validation.

## Documentation Reconciliation

Documentation update: Required and completed.

* `README.md` documents the product action, API, duplicate behavior, failure boundary, and human
  validation.
* `docs/ui-ux.md` documents the always-available first-run/refresh interaction.
* `docs/architecture.md` now distinguishes explicit initialization/refresh from future passive
  monitoring.
* `docs/roadmap.md` marks repository bootstrap/on-demand understanding implemented while retaining
  monitoring and evolution work.

No ADR was required because the implementation preserves existing ownership and trust boundaries.

## Residual Limitations

The HTTP operation remains synchronous with Git preparation and workflow submission, as approved;
durable background AgentJob orchestration remains out of scope. V1 targets one selected Source per
execution. The database unique index is the concurrency authority, with application-level winner
reload after a genuine key race. Passive monitoring, scheduled refresh, and semantic comparison
between executions remain future capabilities.

## Recommendation

Ready for Code Review.
