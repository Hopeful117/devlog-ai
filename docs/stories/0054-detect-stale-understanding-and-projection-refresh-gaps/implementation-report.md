# Story 0054 — Detect Stale Understanding And Projection Refresh Gaps — Implementation Report

## Outcome

Implemented the first deterministic context-maintenance evaluation flow for
freshness-related issues.

The delivered slice adds:

* explicit evaluation through
  `POST /api/v1/projects/{projectId}/maintenance-findings/evaluations`;
* stale-understanding findings derived from persisted `projectfreshness`
  results when a checked source is `STALE`;
* missing-projection findings when active sources exist without any persisted
  freshness check;
* duplicate-open-finding suppression for repeated evaluations.

## Key Changes

### Backend evaluation flow

Added:

* `MaintenanceEvaluationService`
* `MaintenanceEvaluationServiceImpl`
* `MaintenanceEvaluationResponse`

The evaluation reuses `ProjectFreshnessService.summary(projectId)` rather than
reimplementing freshness classification inside `contextmaintenance`.

### API

Extended `MaintenanceFindingController` with:

* `POST /api/v1/projects/{projectId}/maintenance-findings/evaluations`

The response returns:

* `version`
* `projectId`
* `createdCount`
* `skippedCount`
* `createdFindings`

### Tests

Added or updated:

* `MaintenanceEvaluationServiceTest`
* `MaintenanceFindingControllerWebMvcTest`

Covered behaviors:

* stale-understanding finding creation;
* missing-projection finding creation;
* no-finding path for `CURRENT` freshness;
* duplicate-open-finding suppression;
* evaluation endpoint contract.

## Documentation Update

Documentation update: Required.

Updated:

* `README.md`
* `docs/knowledge-model.md`

Reason:

* the repository now exposes a new maintenance-evaluation endpoint;
* the first deterministic staleness policy and its limits needed canonical
  documentation.

## Validation

Executed:

```text
cd backend && ./mvnw -Dtest=MaintenanceFindingControllerWebMvcTest,MaintenanceFindingServiceTest,MaintenanceEvaluationServiceTest test
```

Result:

* build success;
* 13 tests run;
* 0 failures;
* 0 errors.

## Scope Notes

The implementation deliberately treats the first projection-refresh signal as a
gap on the **freshness projection surface** itself.

It does not claim:

* a timeline refresh scheduler exists;
* a universal project-health score exists;
* trusted knowledge can be mutated automatically.

## Vault Outcome

Vault consulted during Repository Analysis: No.

Vault outcome: no vault action.

Rationale:

* the Story was fully constrained by repository-local architecture and existing
  freshness/maintenance code;
* the implemented behavior does not introduce a cross-project pattern requiring
  immediate vault curation.
