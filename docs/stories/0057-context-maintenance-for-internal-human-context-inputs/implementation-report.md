# Story 0057 — Context Maintenance For Internal Human Context Inputs — Implementation Report

## Outcome

Implemented the first bounded maintenance slice for internal human context
inputs.

The delivered change adds:

* a dedicated maintenance surface for internal human context;
* a first explicit issue type for stale active human-context inputs;
* deterministic backend evaluation based on active note recency within the same
  note type;
* reuse of the existing human-reviewed maintenance workflow for this new
  finding family.

## Key Changes

### Backend maintenance domain

Extended:

* `MaintenanceContextSurface` with `INTERNAL_HUMAN_CONTEXT`
* `MaintenanceFindingIssueType` with `STALE_HUMAN_CONTEXT_INPUT`

Extended `MaintenanceEvaluationServiceImpl` to inspect active human context
inputs and create a maintenance finding when:

* at least two active inputs exist for the same note type;
* an older active note lags a newer active note of the same type by at least
  14 days;
* the older note itself is at least 30 days old.

This keeps the first slice conservative and deterministic.

### Workflow support

Extended `MaintenanceFindingServiceImpl` so the existing review workflow from
Story `0056` also supports:

* `STALE_HUMAN_CONTEXT_INPUT`

Supported actions remain:

* acknowledge
* dismiss with rationale
* resolve with rationale

Important boundary preserved:

* resolving a maintenance finding does not archive or rewrite the note itself;
* note lifecycle state remains in the `projectcontextinput` domain.

### Frontend

Extended maintenance cockpit models and UI behavior to:

* recognize the `INTERNAL_HUMAN_CONTEXT` surface;
* recognize the `STALE_HUMAN_CONTEXT_INPUT` issue type;
* label the surface as `Human context`;
* expose the same bounded remediation workflow for this finding family.

## Documentation Update

Documentation update: Required.

Updated:

* `docs/knowledge-model.md`
* `docs/ui-ux.md`

Reason:

* internal human context now participates in context maintenance;
* the repository now documents that maintenance review for this surface remains
  distinct from direct note mutation.

## Validation

Executed backend validation:

```text
cd backend && ./mvnw -Dtest=MaintenanceEvaluationServiceTest,MaintenanceFindingServiceTest,MaintenanceFindingControllerWebMvcTest,ProjectHumanContextInputServiceTest test
```

Result:

* build success;
* 28 tests run;
* 0 failures;
* 0 errors.

Executed frontend targeted tests:

```text
cd frontend && npm test -- --watch=false --include src/app/features/context-maintenance/maintenance-finding.service.spec.ts --include src/app/features/context-maintenance/project-maintenance-section.spec.ts
```

Result:

* 2 test files passed;
* 7 tests passed;
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

## Scope Notes

This Story deliberately does not:

* change `ProjectHumanContextInputStatus`
* auto-archive notes from maintenance
* add semantic AI judgment for note supersession
* add note editing/version history redesign
* promote human context into trusted knowledge

## Vault Outcome

Vault consulted during Repository Analysis: No.

Vault outcome: no vault action.

Rationale:

* the Story remains a repository-local extension of the existing maintenance
  capability and internal human-context domain;
* no new transverse cross-project practice or reusable architectural pattern
  emerged beyond the already established ADR-052 / ADR-053 boundaries.
