# Story 0059 — Context Maintenance Test Harness Across Surfaces — Repository Analysis

## Workflow Status

* Story `0059` exists locally as the final maintenance Story in the current
  sequence.
* No implementation artifact exists yet beyond `story.md`.
* Stories `0052` through `0058` are now integrated on `main`, which means the
  maintenance capability is functionally present but its regression harness is
  still uneven across layers.

## Story Intent

Story `0059` is not another maintenance feature slice.

It is a test-harness hardening Story for the capability introduced by:

* `0052` — maintenance findings model;
* `0054` — stale understanding and projection refresh gaps;
* `0055` — duplicate-debt findings;
* `0056` — human remediation workflow;
* `0057` — internal human-context maintenance;
* `0058` — bounded automatic reconciliation.

The core problem is now architectural coverage rather than missing behavior.

The repository already has good unit and slice coverage for individual
maintenance concerns, but it still lacks a dedicated cross-surface harness that
proves the composed system behaves correctly when several maintenance surfaces
participate in one evaluation cycle.

## Relevant Repository Evidence

### 1. Maintenance evaluation is cross-surface in production, but mostly unit-tested in isolation

`MaintenanceEvaluationServiceImpl` is now the orchestration seam for:

* stale understanding findings from project freshness;
* missing projection refresh findings from project freshness summary gaps;
* stale internal human-context findings from active note recency;
* duplicate-debt findings from trusted-knowledge duplicate audit;
* automatic resolution of deterministic findings whose condition has cleared.

This is already a cross-surface system:

* `PROJECT_UNDERSTANDING`
* `INTERNAL_HUMAN_CONTEXT`
* trusted-knowledge duplicate debt as project-understanding maintenance

But current coverage is centered on:

* `MaintenanceEvaluationServiceTest`
* `MaintenanceFindingServiceTest`
* `MaintenanceFindingControllerWebMvcTest`

These are valuable, but they are still mock-heavy seam tests.

They prove decision logic.

They do not fully prove that a multi-surface evaluation behaves correctly
against real persistence state and append-only finding history together.

### 2. The only current maintenance integration test is persistence-oriented, not evaluation-oriented

`MaintenanceFindingPostgresIntegrationTest` currently verifies:

* finding persistence;
* basic lifecycle updates;
* acknowledgement audit persistence.

It does **not** verify:

* evaluation-driven finding creation across multiple surfaces;
* no-finding paths;
* duplicate suppression against persisted findings;
* automatic resolution against persisted findings;
* trust-boundary behavior when duplicate debt and deterministic findings are
  present together.

So the repository has an integration seam for the maintenance domain, but it is
too narrow for `0059`.

### 3. The repository already contains the right pattern for focused integration harnesses

Several Stories already established the preferred style:

* `CommitChangedFilesEagerFetchIntegrationTest`
* `ProjectContextEagerFetchIntegrationTest`
* `ProjectDeletionPostgresIntegrationTest`
* Story `0043` refresh-harness strengthening

The common shape is:

* `@SpringBootTest`
* Testcontainers Postgres
* real repositories/services where the seam matters
* deterministic fixture insertion with `JdbcTemplate`
* invariants asserted directly instead of fragile snapshots

This is the right model for `0059`.

The Story should not introduce:

* browser-heavy E2E;
* provider-live AI tests;
* orchestration through broad fake repositories;
* brittle snapshot dumps of whole maintenance payloads.

### 4. The best multi-surface seam is `MaintenanceEvaluationService` with real persistence and mocked upstream signal providers

The cross-surface orchestration happens in `MaintenanceEvaluationServiceImpl`,
but some of its inputs come from services that are already independently tested
and would add unnecessary noise if fully recreated:

* `ProjectFreshnessService`
* `TrustedKnowledgeDuplicateAuditService`

By contrast, the state that most needs real verification is local and
persistent:

* projects;
* human context inputs;
* maintenance findings;
* maintenance finding actions;
* finding status transitions across repeated evaluations.

That leads to the strongest seam for `0059`:

* a Spring Boot / Postgres integration test around the real
  `MaintenanceEvaluationService`;
* real maintenance repositories and services;
* persisted human-context inputs and findings;
* deterministic mocked freshness and duplicate-audit outputs at the boundary.

This would keep the tests:

* deterministic;
* fast enough for routine runs;
* broad enough to validate real multi-surface maintenance behavior.

### 5. The most valuable invariant set is already visible from the current Stories

The repository now implies several stable maintenance invariants.

These should become explicit in `0059`.

#### Finding-generation invariants

* stale source freshness creates `STALE_PROJECT_UNDERSTANDING`;
* unchecked freshness sources create `MISSING_PROJECTION_REFRESH`;
* stale active human context creates `STALE_HUMAN_CONTEXT_INPUT`;
* duplicate audit clusters create the correct duplicate-debt finding family;
* when no condition applies, no finding is created.

#### Cross-surface coexistence invariants

* one evaluation may create findings for multiple surfaces in the same run;
* deterministic families and duplicate-debt families may coexist without
  collapsing into one generic signal;
* the created findings remain individually typed and reviewable.

#### Persistence and de-duplication invariants

* existing `OPEN` or `ACKNOWLEDGED` equivalent findings are skipped instead of
  duplicated;
* `DISMISSED` or `RESOLVED` findings are not silently replaced as if they were
  still active equivalents.

#### Automation boundary invariants

* deterministic cleared findings may be `AUTO_RESOLVE`d;
* duplicate-debt findings must not be auto-resolved;
* automatic resolution must append explicit audit history;
* automatic resolution must not mutate trusted knowledge or archive human
  context notes.

#### False-positive resistance invariants

* a single active human-context note must not emit a stale human-context
  finding;
* current freshness with no unchecked sources must not emit freshness findings;
* empty duplicate audit must not emit duplicate-debt findings.

### 6. The first multi-surface scenario should be intentionally narrow but real

The strongest first scenario is not “every surface at once.”

It is:

* stale project understanding;
* missing projection refresh;
* stale internal human context;
* duplicate debt;
* one pre-existing deterministic finding eligible for automatic resolution;
* one duplicate-debt finding that must remain untouched by automation.

That single scenario would verify:

* more than one maintenance surface in the same evaluation;
* finding generation and skip behavior;
* automation allowed on deterministic families only;
* no silent mutation of semantically sensitive duplicate debt.

This gives much higher regression value than isolated one-family integration
tests.

## Architectural Implications

### A. `0059` should define invariants explicitly in the artifact, not only in test names

Acceptance criterion `AC-1` asks for an explicit invariant set.

So the Story should not stop at adding tests.

The invariant set should be recorded in:

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`

This mirrors the benefit Story `0043` provided for refresh-path safety:
test-hardening becomes an explicit architectural asset rather than silent
background code.

### B. The harness should be integration-heavy at one seam and unit-light elsewhere

The unit tests already exist and are strong.

The missing value is at the seam where:

* maintenance evaluation;
* persisted finding state;
* persisted human-context state;
* repeated evaluations;
* automatic reconciliation

all meet.

So `0059` should prefer:

* one or two high-value integration tests

over:

* many additional mocked service tests that duplicate `0054` to `0058`.

### C. The Story should avoid inventing a maintenance mega-fixture framework

The repository does not currently rely on abstract fixture builders for these
integration tests.

Existing tests use direct inserts and focused helpers.

That is the right trade-off here:

* easier to read;
* lower indirection;
* better evidence for operational invariants.

A lightweight local helper set inside the new integration test class is enough.

### D. The frontend likely does not need new primary coverage in this Story

Current frontend maintenance coverage already proves:

* rendering of findings;
* bounded review controls;
* `AUTO_RESOLVE` audit visibility.

Story `0059` is primarily about the cross-surface maintenance harness and trust
boundaries.

The highest-value gap is backend integration, not additional cockpit snapshot
tests.

Frontend changes may be unnecessary unless implementation introduces a new
observable contract.

## Recommended Implementation Direction

Recommended first slice for `0059`:

1. add a new Spring Boot / Testcontainers integration test dedicated to
   maintenance evaluation across surfaces;
2. persist a project plus human-context inputs and existing findings with
   `JdbcTemplate`;
3. mock only `ProjectFreshnessService` and
   `TrustedKnowledgeDuplicateAuditService`;
4. invoke the real `MaintenanceEvaluationService.evaluate(projectId)`;
5. assert explicit invariants on:
   * created findings by issue type and surface;
   * skipped equivalents;
   * `AUTO_RESOLVE` audit history for deterministic findings;
   * non-automation of duplicate debt;
   * no silent mutation of human-context input state.

Add at least one complementary no-finding scenario proving:

* current freshness,
* no duplicate audit clusters,
* insufficient stale-human-context conditions

results in:

* no created findings;
* no auto-resolutions;
* unchanged project memory state.

## Risks

### Risk 1 — Over-mocking turns the Story into another unit-test increment

If every collaborator is mocked, the Story will not materially improve the
cross-surface harness.

Mitigation:

* keep real persistence and real maintenance services;
* mock only external signal providers whose internals are not the target here.

### Risk 2 — Over-ambitious full-stack orchestration will make tests brittle

Trying to generate real freshness, real duplicate audits, and real UI flow in
one Story would create a noisy and expensive harness.

Mitigation:

* test at the maintenance evaluation seam;
* assert invariants, not end-to-end incidental details.

### Risk 3 — Assertions could drift toward full payload snapshots

Snapshot-style expectations would be brittle as summaries/details evolve.

Mitigation:

* assert issue families, statuses, audit actions, counts, and state boundaries;
* avoid asserting every detail string unless that detail is the invariant.

## Conclusion

Story `0059` should be implemented as a **maintenance evaluation integration
test harness** centered on explicit invariants.

The repository already has:

* strong unit logic coverage;
* a persistence-only maintenance integration test;
* established patterns for seam-focused integration hardening.

What is missing is a dedicated regression harness that proves the composed
maintenance system can:

* generate findings across surfaces;
* resist false positives;
* preserve trust boundaries;
* reconcile deterministic findings safely over repeated evaluations.

That is the highest-value, lowest-risk interpretation of the Story.
