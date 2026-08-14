# Story 0059 — Context Maintenance Test Harness Across Surfaces — Implementation Plan

## Status

Planned

## Planning Goal

Add the smallest high-value cross-surface maintenance harness that would catch
real regression classes before they escape into running DevLog instances.

The goal is not to add broad E2E.

The goal is to harden the evaluation seam where multiple maintenance surfaces,
persisted findings, and automation boundaries interact.

## Key Decision

Implement `0059` primarily as a **Spring Boot / Testcontainers integration
harness** around the real `MaintenanceEvaluationService`, with only external
signal providers mocked at the boundary.

This is the most valuable seam because it exercises:

* real persistence;
* real finding lifecycle updates;
* real repeated evaluation behavior;
* cross-surface coexistence;
* automation boundaries.

## Why This Approach

The repository already has:

* strong unit tests for `MaintenanceEvaluationServiceImpl`;
* service tests for maintenance remediation rules;
* WebMvc tests for cockpit-facing endpoints;
* one persistence-oriented integration test for maintenance findings.

The missing value is not more isolated unit coverage.

The missing value is one dedicated harness that proves the composed maintenance
system behaves correctly when:

* freshness signals,
* duplicate-debt signals,
* human-context state,
* persisted findings,
* repeated evaluation,
* automatic reconciliation

meet in one run.

## In-Scope Implementation Steps

### Step 1 — Formalize the maintenance invariant set

Record the invariant set explicitly in the Story artifacts and mirror it in the
test structure.

Primary invariant groups:

* finding-generation invariants;
* multi-surface coexistence invariants;
* false-positive resistance invariants;
* persistence/de-duplication invariants;
* automation and trust-boundary invariants.

Concrete invariants to protect:

* stale understanding creates `STALE_PROJECT_UNDERSTANDING`;
* unchecked freshness sources create `MISSING_PROJECTION_REFRESH`;
* stale active human context creates `STALE_HUMAN_CONTEXT_INPUT`;
* duplicate audit clusters create their correct duplicate-debt finding family;
* no eligible condition means no new finding;
* equivalent `OPEN` or `ACKNOWLEDGED` findings are skipped, not duplicated;
* deterministic cleared findings may be `AUTO_RESOLVE`d;
* duplicate-debt findings must not be automatically resolved;
* maintenance evaluation must not silently mutate trusted knowledge or archive
  human-context inputs.

### Step 2 — Add a dedicated cross-surface integration test class

Create a new Spring Boot / Testcontainers integration test dedicated to
maintenance evaluation across surfaces.

Preferred target:

* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/MaintenanceEvaluationIntegrationTest.java`

Implementation style:

* `@SpringBootTest`
* Testcontainers Postgres
* real `MaintenanceEvaluationService`
* real `MaintenanceFindingRepository` / `MaintenanceFindingService`
* persisted fixture setup through `JdbcTemplate`

Use direct SQL helpers following existing repository conventions instead of
introducing a generalized fixture framework.

### Step 3 — Mock only upstream maintenance signal providers

Inside the integration harness, replace only:

* `ProjectFreshnessService`
* `TrustedKnowledgeDuplicateAuditService`

These two services are the bounded signal providers feeding maintenance
evaluation. Their internals are already tested elsewhere and should not be
rebuilt here.

All maintenance-domain persistence and lifecycle behavior should remain real.

Preferred tool:

* Spring test mocks (`@MockitoBean` or the repository’s accepted equivalent)

The goal is to feed deterministic cross-surface signal combinations into the
real maintenance pipeline.

### Step 4 — Cover one primary multi-surface scenario end-to-end at the evaluation seam

Add one high-value scenario that persists a project and exercises more than one
maintenance surface in a single evaluation.

Recommended scenario contents:

* one stale freshness source;
* one unchecked source count to trigger missing projection refresh;
* two active human-context notes of the same type where one qualifies as stale;
* one duplicate audit cluster producing duplicate-debt;
* one persisted deterministic finding that should now auto-resolve;
* one persisted duplicate-debt finding that must remain untouched by
  automation.

Assert:

* created findings include the expected issue families;
* the created findings remain individually typed and surfaced;
* the deterministic persisted finding transitions to `RESOLVED` with appended
  `AUTO_RESOLVE` action history;
* the duplicate-debt finding remains unchanged;
* the human-context input row status remains unchanged.

This single scenario satisfies most of `AC-2`, `AC-3`, and `AC-4`.

### Step 5 — Add one no-finding / false-positive resistance scenario

Add a complementary scenario proving the harness also protects against noisy
maintenance output.

Recommended setup:

* current freshness only;
* `uncheckedSourceCount = 0`;
* empty duplicate audit;
* either zero or one active human-context input, or two notes that do not meet
  the stale thresholds;
* no eligible persisted deterministic findings to auto-resolve.

Assert:

* `createdCount == 0`;
* no `AUTO_RESOLVE` action is recorded;
* maintenance findings remain absent or unchanged;
* human-context note status remains `ACTIVE`.

This anchors `AC-3` around non-regression behavior rather than only positive
paths.

### Step 6 — Keep assertions invariant-based, not snapshot-based

Avoid asserting full generated details blobs except where the exact text is
itself the contract.

Prefer assertions on:

* issue type;
* context surface;
* status;
* action history type;
* comment presence or reserved system actor identity;
* persisted row counts;
* unchanged human-context status;
* unchanged duplicate-debt status.

This keeps the harness stable as explanatory strings evolve.

### Step 7 — Document residual blind spots honestly

Record the deliberate limits of the harness in the implementation artifacts.

Expected residual blind spots:

* no browser E2E across the cockpit;
* no live duplicate-audit generation pipeline;
* no live freshness computation pipeline;
* no AI/provider-timing-sensitive scenarios;
* no coverage of every duplicate cluster category in integration form if the
  primary scenario already covers the critical trust boundary.

This is required by `AC-5` and keeps the Story honest.

## Files Likely To Change

Expected:

* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/MaintenanceEvaluationIntegrationTest.java`

Possible:

* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/MaintenanceFindingPostgresIntegrationTest.java`
  if a small helper extraction or naming cleanup improves readability

Documentation artifacts:

* `docs/stories/0059-context-maintenance-test-harness-across-surfaces/repository-analysis.md`
* `docs/stories/0059-context-maintenance-test-harness-across-surfaces/implementation-plan.md`
* `docs/stories/0059-context-maintenance-test-harness-across-surfaces/implementation-report.md`
* `docs/stories/0059-context-maintenance-test-harness-across-surfaces/code-review.md`
* `docs/stories/0059-context-maintenance-test-harness-across-surfaces/engineering-report.md`

Canonical documentation:

* likely no canonical docs change required unless the implementation exposes a
  new explicit testing contract worth documenting

## Validation Plan

At minimum:

* targeted backend execution for the new maintenance integration harness
* targeted maintenance backend suite
* full backend `./mvnw test -DskipITs`
* frontend validation only if implementation unexpectedly changes frontend code
* `git diff --check`

Recommended commands:

```text
cd backend && ./mvnw -Dtest=MaintenanceEvaluationIntegrationTest,MaintenanceFindingPostgresIntegrationTest,MaintenanceEvaluationServiceTest,MaintenanceFindingServiceTest,MaintenanceFindingControllerWebMvcTest test
cd backend && ./mvnw test -DskipITs
git diff --check
```

## Risks

### Risk 1 — The new harness duplicates unit-test logic instead of adding real signal

Mitigation:

* assert persisted outcomes and repeated-evaluation behavior;
* keep real repositories/services in the integration seam.

### Risk 2 — The scenario becomes overstuffed and hard to maintain

Mitigation:

* keep one primary multi-surface scenario and one complementary no-finding
  scenario;
* split only if readability materially suffers.

### Risk 3 — Overly exact text assertions will make the harness brittle

Mitigation:

* assert families, statuses, action types, counts, and trust boundaries first;
* assert detail strings only when needed to distinguish identities.

### Risk 4 — Spring test mocking choices may create awkward context setup

Mitigation:

* follow the repository’s existing `@SpringBootTest` + Testcontainers style;
* if mock-bean registration proves noisy, introduce the smallest local test
  configuration necessary rather than redesigning the harness.

## Planned Outcome

After `0059`:

* context maintenance will have an explicit invariant set under automated
  verification;
* the repository will include at least one real multi-surface maintenance
  scenario;
* false-positive resistance will be protected by automated no-finding coverage;
* automation boundaries from `0058` will be guarded at the persisted-system
  seam instead of only in mocked service tests.
