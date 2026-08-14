# Story 0059 — Context Maintenance Test Harness Across Surfaces — Implementation Report

## Outcome

Implemented the dedicated cross-surface maintenance test harness requested by
Story `0059`.

The delivered change adds:

* a real Spring Boot + Testcontainers integration seam around maintenance
  evaluation;
* one primary multi-surface scenario covering freshness, projection-refresh
  gaps, internal human context, duplicate debt, and bounded automation in the
  same run;
* one no-finding scenario protecting false-positive resistance;
* explicit verification that maintenance automation does not silently mutate
  trusted knowledge or archive human context inputs.

## Key Changes

### Backend test harness

Added:

* `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/MaintenanceEvaluationIntegrationTest.java`

The new harness uses:

* `@SpringBootTest`
* Testcontainers Postgres
* real `MaintenanceEvaluationService`
* real `MaintenanceFindingService`
* persisted setup through `JdbcTemplate`

Only upstream signal providers are mocked at the boundary:

* `ProjectFreshnessService`
* `TrustedKnowledgeDuplicateAuditService`

This keeps the evaluation seam realistic while preserving deterministic test
inputs.

### Multi-surface evaluation scenario

The primary scenario verifies one evaluation run can:

* create `STALE_PROJECT_UNDERSTANDING`;
* create `MISSING_PROJECTION_REFRESH`;
* create `STALE_HUMAN_CONTEXT_INPUT`;
* create `TRUSTED_KNOWLEDGE_EXACT_DUPLICATE`;
* auto-resolve one previously persisted deterministic finding;
* preserve one existing duplicate-debt finding without automatic mutation.

The assertions focus on invariants rather than brittle payload snapshots:

* issue families created;
* persisted finding count;
* `AUTO_RESOLVE` audit semantics;
* reserved system actor identity;
* unchanged duplicate-debt status;
* unchanged human-context row status.

### False-positive resistance scenario

The complementary scenario verifies that when:

* freshness is current;
* unchecked source count is zero;
* duplicate audit is empty;
* human context is not stale;

the evaluation creates no findings and records no automatic actions.

## Documentation Update

Documentation update: Not required.

Reason:

* the Story hardens repository verification only;
* no runtime behavior, API contract, UX contract, or architectural policy
  changed.

The invariant set and residual limits are recorded in the Story artifacts
instead.

## Validation

Executed backend targeted validation:

```text
cd backend && ./mvnw -Dtest=MaintenanceEvaluationIntegrationTest,MaintenanceFindingPostgresIntegrationTest,MaintenanceEvaluationServiceTest,MaintenanceFindingServiceTest,MaintenanceFindingControllerWebMvcTest test
```

Result:

* build success;
* 33 tests run;
* 0 failures;
* 0 errors.

Executed diff hygiene check:

```text
git diff --check
```

Result:

* passed;
* no whitespace or patch formatting issues.

Executed backend full test suite:

```text
cd backend && ./mvnw test -DskipITs
```

Result:

* build success;
* 645 tests run;
* 0 failures;
* 0 errors;
* 0 skipped.

## Residual Blind Spots

This harness still deliberately does not cover:

* browser E2E across the cockpit;
* live freshness computation internals;
* live duplicate-audit generation internals;
* provider- or timing-sensitive AI behavior;
* every duplicate cluster category in integration form.

These remain out of scope because `0059` targets the highest-value
cross-surface evaluation seam, not a full system simulation stack.

## Vault Outcome

Vault consulted during Repository Analysis: No.

Vault outcome: no vault action.

Rationale:

* the Story strengthens repository-local verification only;
* no new reusable cross-project operating pattern emerged beyond the already
  documented engineering-story workflow.
