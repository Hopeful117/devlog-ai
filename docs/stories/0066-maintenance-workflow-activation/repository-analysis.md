# Story 0066 — Maintenance Workflow Activation — Repository Analysis

## Current State

The context maintenance system has complete backend infrastructure:

* `POST /api/v1/projects/{id}/maintenance-findings/evaluations` — triggers full evaluation cycle
* `GET /api/v1/projects/{id}/maintenance-findings` — returns findings with assessments
* `POST .../acknowledgements`, `.../dismissals`, `.../resolutions` — workflow actions
* `MaintenanceEvaluationService` — orchestrates deterministic checks + agent evaluation
* `DuplicateAmbiguityResolutionAgent` — evaluates duplicate clusters
* `CrossSurfacePatternDetectionAgent` — detects cross-surface patterns

The frontend has:

* `MaintenanceFindingService` — HTTP client with GET and action methods
* `ProjectMaintenanceSection` — component rendering findings with workflow buttons
* `MaintenanceFinding` model — complete type definitions

## Gap Analysis

| Component | Status | Gap |
|-----------|--------|-----|
| Backend `POST /evaluations` | ✅ Working | None |
| Backend `GET /findings` | ✅ Working | None |
| Frontend service `evaluate()` | ❌ Missing | No method to call evaluation endpoint |
| Frontend UI trigger | ❌ Missing | No button to trigger evaluation |
| Frontend feedback | ❌ Missing | No loading/error/timestamp display |

## Verification

Backend evaluation was verified manually:

```bash
curl -X POST http://localhost:18080/api/v1/projects/f3d56247-aada-4a76-982b-e6802c0b309c/maintenance-findings/evaluations
```

Result: 7 findings created (1 stale source, 6 duplicate knowledge clusters).
