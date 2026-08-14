# Story 0058 — Narrow Safe Automatic Maintenance Actions — Implementation Report

## Outcome

Implemented the first narrow automatic maintenance action slice.

The delivered change adds:

* explicit `AUTO_RESOLVE` audit semantics for system-owned maintenance actions;
* deterministic auto-resolution during maintenance evaluation for cleared
  low-risk findings;
* bounded eligibility limited to freshness- and stale-human-context families;
* preserved human governance for dismissed, resolved, and duplicate-debt
  findings.

## Key Changes

### Backend maintenance workflow

Extended `MaintenanceFindingActionType` with:

* `AUTO_RESOLVE`

Extended `MaintenanceFindingService` and `MaintenanceFindingServiceImpl` with a
dedicated automatic resolution path that:

* records `AUTO_RESOLVE`;
* uses the reserved system actor UUID
  `00000000-0000-0000-0000-000000000002`;
* requires an explanatory comment;
* resolves findings without reusing human `RESOLVE` semantics implicitly.

The workflow guardrails now allow automatic resolution only for:

* `STALE_PROJECT_UNDERSTANDING`
* `MISSING_PROJECTION_REFRESH`
* `STALE_HUMAN_CONTEXT_INPUT`

Duplicate-debt families remain human-reviewed only.

### Deterministic reconciliation in evaluation

Extended `MaintenanceEvaluationServiceImpl` so one evaluation run now:

* creates missing deterministic findings;
* suppresses duplicate creation for both `OPEN` and `ACKNOWLEDGED` equivalents;
* auto-resolves eligible `OPEN` or `ACKNOWLEDGED` findings when their exact
  deterministic condition is no longer emitted by evaluation.

The matching logic uses a normalized deterministic key based on:

* context surface;
* issue type;
* summary;
* details.

This keeps automatic reconciliation specific to the exact bounded rule anchor
instead of broadly closing all findings of a family.

### Frontend

Extended maintenance models and specs to recognize:

* `AUTO_RESOLVE`

The maintenance UI remains observational:

* no new automation controls were added;
* automatic actions appear through ordinary finding audit history.

## Documentation Update

Documentation update: Required.

Updated:

* `docs/knowledge-model.md`
* `docs/ui-ux.md`

Reason:

* the repository now documents that bounded automatic maintenance reconciliation
  exists;
* the eligible families and explicit audit semantics are now part of the
  canonical behavior contract.

## Validation

Executed backend targeted validation:

```text
cd backend && ./mvnw -Dtest=MaintenanceEvaluationServiceTest,MaintenanceFindingServiceTest,MaintenanceFindingControllerWebMvcTest test
```

Result:

* build success;
* 29 tests run;
* 0 failures;
* 0 errors.

Executed frontend targeted validation:

```text
cd frontend && npm test -- --watch=false --include src/app/features/context-maintenance/maintenance-finding.service.spec.ts --include src/app/features/context-maintenance/project-maintenance-section.spec.ts
```

Result:

* 2 test files passed;
* 8 tests passed;
* 0 failures.

Executed frontend quality gates:

```text
cd frontend && npm run lint
cd frontend && npm run format:check
cd frontend && npm run build
```

Result:

* lint passed;
* format check passed;
* production build passed.

Executed backend full test suite:

```text
cd backend && ./mvnw test -DskipITs
```

Result:

* build failed outside Story `0058` scope;
* failing test:
  `SelectedJavaSymbolEnricherTest.appliesAggregateSymbolAndTokenLimits`
* observed assertion:
  expected `SYMBOL_COUNT_LIMIT` but was `null`.

## Scope Notes

This Story deliberately does not:

* auto-dismiss or auto-resolve duplicate-debt findings;
* archive or rewrite internal human-context inputs;
* merge, delete, or semantically mutate trusted knowledge;
* add background schedulers or automation policy UI.

## Vault Outcome

Vault consulted during Repository Analysis: No.

Vault outcome: no vault action.

Rationale:

* the Story extends an already established repository-local maintenance
  workflow;
* no new cross-project reusable operating model emerged beyond the explicit
  ADR-053 automation boundary already captured in repository docs.
