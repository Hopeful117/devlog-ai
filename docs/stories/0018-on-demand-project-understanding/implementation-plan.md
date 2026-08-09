# Implementation Plan — Story 0018

## Status

Ready for human approval.

## Overview

Implement one Core-owned, on-demand Project Understanding operation over the existing Analysis
pipeline. The operation will accept a project, one explicitly selected active Git Source, an optional
revision, and optional User Guidance. The Core—not Angular—will resolve `describe-project-v1`, use
the internal `ARCHITECTURE_REVIEW`/`INSIGHT_GENERATION` mapping, prepare Git history, claim or reuse
an equivalent execution, and start the existing workflow.

Every product execution will persist immutable Source provenance and a normalized execution key.
PostgreSQL will enforce at most one equivalent `PENDING` or `IN_PROGRESS` execution with a partial
unique index. Terminal Analyses will never block a later refresh.

The Project Cockpit will expose the action before and after first success. The initial label will be
`Understand project`; after an earlier canonical execution it will become `Refresh understanding`.
Both states use the same API and application service. Generic Analysis creation remains unchanged.

## Planned Changes

### 1. Define the product API contract

Add a dedicated Project Understanding feature package in the Core with:

* `POST /api/v1/projects/{projectId}/understanding-executions`;
* request fields: `sourceId`, optional `targetRevision`, optional existing `UserGuidance` shape;
* response fields: Analysis identity and status, selected Source identity, requested revision,
  canonical Intent id/version, and an outcome distinguishing `CREATED` from `REUSED`.

Validate UUID/path ownership, 255-character revision limits, and existing User Guidance limits with
the repository's Bean Validation conventions. The client must not provide Analysis type, Intent,
prompt, context profiles, execution key, or lifecycle status.

Return the existing Analysis on an equivalent in-flight request, with a normal success response and
`REUSED` outcome. Use standard 404/400/409 error envelopes for missing entities, invalid input, or
unsupported state. Do not expose database constraint details.

### 2. Persist immutable Source scope and execution identity

Create `V31__add_project_understanding_execution_scope.sql` and extend `Analysis` with nullable,
write-once fields used only by the product flow:

* `selected_source_id` — nullable Source FK for current relational lookup;
* `selected_source_snapshot` — JSONB snapshot containing Source UUID, name, type, repository URL,
  default branch, and provider at claim time;
* `understanding_execution_key` — nullable lowercase SHA-256 digest of normalized semantic inputs.

Use `ON DELETE SET NULL` for the selected Source FK so future standalone Source deletion cannot erase
historical Analyses. The immutable JSON snapshot preserves provenance after the FK is cleared.
Project deletion continues through the existing Analysis cascade from Story 0017.

Add a partial unique index on `understanding_execution_key` for rows whose status is `PENDING` or
`IN_PROGRESS`. Generic Analyses keep all three columns null and are unaffected. Terminal status
transition releases the key for a later intentional refresh without rewriting history.

Extend Analysis mapping/response models only with nullable source-scope fields needed by consumers.
Do not add a new Analysis type or alter existing request compatibility.

### 3. Normalize an equivalent execution deterministically

Add a small Core component that computes the execution key from a versioned canonical structure:

* project UUID;
* Source UUID;
* normalized requested revision, using an explicit default-revision marker when omitted;
* resolved canonical Intent id and version;
* normalized User Guidance with stable field order, trimmed nullable strings, and priority order
  preserved according to current guidance semantics;
* execution-key schema version.

Serialize through the configured deterministic JSON mapper and hash UTF-8 bytes with SHA-256. Keep
the component unit-testable with golden vectors. Do not treat the resolved commit as part of
equivalence: two concurrent default-revision requests represent the same in-flight user request,
while a later request is allowed after the first becomes terminal.

### 4. Add the short transactional claim/reuse boundary

Create a transactional claim service that:

1. re-resolves project, selected active Source ownership, and canonical Intent;
2. computes the normalized execution key;
3. queries an equivalent active Analysis;
4. returns it as `REUSED` when present;
5. otherwise creates a `PENDING` Analysis with internal type `ARCHITECTURE_REVIEW`, canonical Intent,
   normalized revision/guidance, Source snapshot, and execution key;
6. flushes before returning so the partial unique index closes concurrent races;
7. on a unique-key race, clears the failed persistence context as required and reloads the winning
   active Analysis rather than returning 500.

Keep the transaction limited to database work. Do not hold a project/database lock during clone,
fetch, history parsing, deterministic collection, or AI submission. The unique partial index is the
cross-client and cross-instance authority; a preliminary lookup is only an optimization.

Add bounded repository queries by execution key and active statuses. Do not use a broad
project-status query followed by in-memory equivalence.

### 5. Prepare Source and history before claiming an Analysis

Add a preparation service that executes before the claim transaction:

1. resolve the project and `findByIdAndProject_IdAndActiveTrue` Source;
2. require `GIT_REPOSITORY`;
3. resolve `describe-project-v1` and verify its normal task mapping;
4. normalize/validate the requested revision;
5. synchronize through `WorkspaceManager` to validate and resolve the exact commit;
6. import Git history idempotently for that resolved revision.

Refactor `ProjectHistoryService` with an internal method accepting an already synchronized workspace,
or an equivalent narrow seam, so preparation does not fetch/reset the same repository twice merely
to import history. Retain the current public history-import endpoint and behavior.

If any preparation step fails, return the standard actionable error and create no Analysis. Local
workspace cache changes and already imported commits are acceptable idempotent preparation effects;
no Profile, proposal, validation, or Trusted Knowledge is created.

Re-resolve ownership inside the claim transaction to close the activation/deletion race between
preparation and persistence. If Source state changed, create no Analysis.

### 6. Scope collection to the selected Source without breaking generic Analyses

Update `KnowledgeCollectionServiceImpl` source resolution:

* when `selectedSourceId` is present, load exactly that Source for the Analysis project and require
  it to remain compatible with the immutable snapshot;
* when absent, retain the current all-active-Sources project query for generic Analyses;
* continue recording requested revision on Analysis and exact resolved revision in diagnostics;
* never silently fall back from a missing selected Source to another active Source.

The product execution must produce one diagnostics revision entry. Existing generic multi-source
tests and behavior remain intact.

Where practical, reuse the prepared workspace revision during collection through the existing
workspace cache; correctness must not depend on cache presence. A later fetch must still checkout
the Analysis' requested revision deterministically.

### 7. Orchestrate create/reuse and workflow start in one application call

The Project Understanding application service will:

1. run precondition and history preparation;
2. claim or reuse the execution;
3. immediately return a reused Analysis without starting it again;
4. start a newly claimed Analysis through `AnalysisWorkflowService`;
5. return its identity/outcome for navigation.

If a failure occurs after claim but before the workflow moves the Analysis to `IN_PROGRESS`, add a
bounded transition that marks that claimed `PENDING` understanding Analysis `FAILED`. If failure
occurs after start, retain the existing workflow failure handling. No product request may leave an
unrecoverable stray `PENDING` row because internal orchestration stopped between claim and start.

Keep execution synchronous with the existing HTTP/workflow model for this Story. Do not introduce
AgentJob, an outbox, a broker, scheduler, webhook, or passive monitor.

### 8. Preserve the proposal and validation boundary

Reuse current knowledge selection, AI submission, output validation, proposal persistence, and
Analysis detail behavior. Do not call proposal-validation or Trusted Knowledge promotion services
from Project Understanding.

Add a focused regression test proving a successful execution creates/reaches the normal proposal
review boundary without automatically validating or promoting its proposals. Earlier Analyses,
Profiles, proposals, validations, and Trusted Insights remain untouched by refresh failures.

### 9. Add the typed Angular service

Create product-facing models for request, response, Source snapshot summary, and `CREATED | REUSED`
outcome. Add a `ProjectUnderstandingService` with one POST call to the new endpoint.

The service accepts project UUID plus the typed request. It does not call generic Analysis creation,
workflow start, history import, Intent catalog, or Source synchronization endpoints. Add an HTTP
service spec verifying encoded URL, method, exact body, and both outcomes.

### 10. Add an always-available Cockpit interaction

Add a focused `ProjectUnderstandingSection` to the Project Cockpit rather than repurposing the
advanced `ProjectAnalysesSection`.

Inputs should be the current project, loaded Sources, and loaded Analyses. The component will:

* detect prior canonical understanding by `describe-project` plus `v1`, not by any Analysis count;
* display `Understand project` before a prior canonical execution and `Refresh understanding`
  afterward;
* remain visible after completion or failure;
* show an unavailable state and repository-connection guidance when no compatible active Source
  exists;
* select the only compatible active Source by default;
* require a visible deliberate selection when several are available;
* show Source name/default branch and an optional revision input;
* optionally expose existing User Guidance in user-oriented language;
* prevent duplicate local submission with the established RxJS `exhaustMap` pattern;
* announce preparation/running/reuse/error states through `role=status` and `role=alert`;
* navigate to `/analyses/{id}` for both created and reused responses.

Do not hide or remove generic Analysis controls. Avoid internal terms such as Intent, context profile,
AI Task, collector, or PromptRequest in the primary interaction.

### 11. Refresh Cockpit state coherently

The current project detail view loads Sources and Analyses once through `forkJoin`. Integrate the new
section without adding imperative subscriptions or duplicating the entire Cockpit request graph.
Navigation normally leaves for Analysis detail after launch/reuse, so no optimistic local Analysis
insertion is required.

When the user later returns, the existing route load obtains the latest execution. If implementation
adds an in-place retry/result state, use a bounded refresh trigger following current observable
patterns. Preserve Story 0017 edit/delete state and its tests.

### 12. Add backend coverage

Add focused tests for:

* first execution and later refresh;
* canonical internal type, `describe-project-v1`, and immutable Source snapshot;
* default and explicit revision normalization/resolution;
* missing project, missing/inactive/unsupported Source, and Source/project mismatch;
* invalid revision and missing canonical mapping producing no Analysis;
* history import before context selection and idempotent refresh import;
* selected-source-only collection plus unchanged generic multi-source collection;
* equivalent `PENDING` and `IN_PROGRESS` reuse;
* different Source, revision, Intent-version fixture, or guidance producing distinct keys;
* terminal `COMPLETED`/`FAILED` execution allowing a new claim;
* failure before claim creating no Analysis;
* failure after claim marking the execution failed;
* failure after start retaining existing workflow diagnostics semantics;
* no automatic proposal validation/promotion.

Add a PostgreSQL/Testcontainers concurrency integration test that sends simultaneous equivalent
claims and proves exactly one active Analysis exists and all callers receive its UUID. Also verify
the V31 partial index, terminal-key release, Source FK/snapshot behavior, and Story 0017 project
deletion cascade compatibility. Mock-only concurrency tests are insufficient.

Update Analysis mapper/service/controller/workflow tests only where nullable product fields or the
new bounded transition affect their contracts. Generic endpoints must retain their existing tests.

### 13. Add frontend coverage

Test the Angular section for:

* initial and refresh labels based only on canonical prior executions;
* action availability after earlier success and failure;
* no active compatible Source guidance;
* one-Source default selection and many-Source deliberate selection;
* default and explicit revision request bodies;
* guidance normalization/validation where exposed;
* pending disabled state and ignored duplicate clicks;
* `CREATED` and `REUSED` navigation;
* backend validation/preparation failure with retained form state and retry;
* accessible labels, keyboard-operable controls, status, and alert output;
* regression coverage for project CRUD and generic Analysis controls;
* continued prohibition of direct component `.subscribe()` where repository tests enforce it.

Use controlled Subjects to prove pending and repeated-click behavior deterministically.

### 14. Reconcile canonical documentation

Update only documentation affected by implemented behavior:

* `README.md` — product action and API contract;
* `docs/ui-ux.md` — always-available first-run/refresh Cockpit interaction and outcome language;
* `docs/architecture.md` — explicit on-demand initialization/refresh, distinguishing it from future
  passive monitoring and removing wording that implies mandatory automatic first connection;
* `docs/roadmap.md` — mark repository bootstrap analysis complete while retaining future monitoring
  and evolution work;
* API examples for Source/revision selection, reuse outcome, failure boundary, and mandatory human
  validation.

Do not rewrite unrelated vision documents or create a new ADR unless implementation departs from the
approved single-source synchronous boundary, adds durable background orchestration, or changes trust
promotion semantics. Record the documentation outcome in the Implementation Report.

### 15. Validate the complete behavior

Run validation in this order:

1. focused execution-key, preparation, claim, collection, history, workflow, and controller tests;
2. PostgreSQL/Testcontainers V31 and concurrency integration tests;
3. complete backend `./mvnw -q verify` with JaCoCo checks;
4. focused Angular service/component tests;
5. complete frontend tests using the repository-supported non-watch command;
6. frontend production build;
7. Prettier/format checks for changed frontend and documentation files;
8. rebuild and start the Docker stack on the dedicated local ports;
9. live API validation against a disposable project for first execution, equivalent reuse, terminal
   refresh, invalid Source/revision, Profile creation, and Engineering Story Context availability;
10. browser validation of first/refresh labels, Source selection, pending/error/reuse navigation,
    proposal review boundary, responsive layout, keyboard use, and status announcements;
11. authenticated SonarQube analysis with Quality Gate wait.

The live project `f3d56247-aada-4a76-982b-e6802c0b309c` may be used for non-destructive bootstrap
validation after confirming its Source configuration. Do not delete or overwrite its user data.

The Implementation Report must record commands, results, test counts, coverage, migration version,
Quality Gate, new-code findings, live Profile/context outcome, and residual limitations. Any failed
test, build, migration/concurrency assertion, or Quality Gate prevents successful completion.

## Expected Files to Modify

### Backend

* Analysis entity, mapper, response, repository, service, and focused tests.
* Knowledge collection Source resolution and tests.
* Project history service/internal composition seam and tests.
* Standard exception handling only if a focused preparation error is not representable today.
* `backend/pom.xml` only if Story 0017 did not already add the required PostgreSQL/Testcontainers
  test dependencies.

### Frontend

* `features/projects/project-detail-page.ts` and template/style integration.
* Analysis models only if nullable Source provenance is shown by the existing detail page.
* Existing project-detail tests for integration/regression coverage.

### Documentation

* `README.md`.
* `docs/ui-ux.md`.
* `docs/architecture.md`.
* `docs/roadmap.md`.

## Expected Files to Create

* `backend/src/main/resources/db/migration/V31__add_project_understanding_execution_scope.sql`.
* A backend Project Understanding controller, request/response DTOs, application/preparation/claim
  services, execution-key component, and focused tests under repository package conventions.
* A PostgreSQL concurrency/schema integration test under the established integration-test location.
* Angular Project Understanding models, service/spec, component template/style/spec.
* Story 0018 implementation, review, and engineering reports at their permitted workflow stages.

Exact filenames may be adjusted to repository naming conventions during implementation, but the
approved responsibilities and boundaries must not change without returning to Gate 2.

## Dependencies and Existing-Solution Preflight

The repository already provides Spring transactions, Spring Data JPA, PostgreSQL partial indexes,
Flyway, deterministic Jackson serialization, Java SHA-256 support, Bean Validation, Angular reactive
forms, RxJS, and the complete Analysis workflow. These maintained existing mechanisms are sufficient.

Use the Testcontainers PostgreSQL support already introduced by Story 0017 for real concurrency and
schema validation. No new production dependency, paid service, custom job framework, or frontend
state library is planned.

## Acceptance-Criteria Traceability

* AC-1 and AC-8 → steps 9–11 and 13.
* AC-2 and AC-3 → steps 1, 2, 5, 6, 10, 12, and 13.
* AC-4 and AC-5 → steps 5–8.
* AC-6 → steps 1–3, 6–7, 10, and 12.
* AC-7 → steps 2–4, 7, 12, and 13.
* AC-9 → step 8 and its regression coverage.
* AC-10 → steps 5, 7, 8, and 12.
* AC-11 → steps 9–11 and 13.
* AC-12 → step 12.
* AC-13 → step 13.
* AC-14 → steps 2, 6–8, 11–13.
* AC-15 → steps 14–15.

## Definition of Done

Story 0018 implementation is complete only when:

* the Core exposes one typed Project Understanding contract;
* first execution and refresh use the same operation;
* one selected active Git Source and its immutable snapshot are traceable;
* `describe-project-v1` is Core-owned and history is available to its context selection;
* equivalent active requests converge on one Analysis under real PostgreSQL concurrency;
* terminal executions permit later refresh;
* generic Analysis behavior remains compatible;
* the Cockpit action is always available when preconditions permit and communicates outcomes rather
  than internals;
* failures respect the pre-claim/post-claim traceability boundary;
* proposals remain subject to explicit human validation;
* focused and complete backend/frontend validation passes;
* V31 and Story 0017 deletion compatibility are proven against PostgreSQL;
* canonical documentation is reconciled;
* authenticated SonarQube Quality Gate passes with no new unresolved Story-attributable issue;
* the Implementation Report records complete evidence for independent Code Review.

## Recommendation

Approve this Implementation Plan and proceed to implementation.

Implementation must not begin until explicit human approval of this current plan.
