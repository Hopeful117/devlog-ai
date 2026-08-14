# Story 0066 — Maintenance Workflow Activation — Engineering Report

## Architecture Impact

This Story closes the workflow gap identified in ADR-053/054 by connecting the
frontend UI to the existing backend evaluation endpoint.

The maintenance workflow is now complete:

```
User clicks "Run evaluation"
    ↓
POST /api/v1/projects/{id}/maintenance-findings/evaluations
    ↓
MaintenanceEvaluationService.evaluate()
    ↓
Deterministic checks + Agent evaluation
    ↓
Findings returned to frontend
    ↓
User reviews and acts on findings
```

## What Was Missing

The backend infrastructure was complete but the UI trigger was never implemented.
This created a situation where:

* The maintenance agents worked correctly
* Findings were created when evaluation was triggered
* But nothing triggered the evaluation from the UI
* Users had no way to see maintenance findings

## What Was Added

| Component | Change |
|-----------|--------|
| `MaintenanceFindingService` | +`evaluate()` method |
| `MaintenanceFinding` models | +`MaintenanceEvaluationResponse` interface |
| `ProjectMaintenanceSection` | +evaluation state + `evaluate()` method |
| Template | +toolbar with button, timestamp, error display |
| Styles | +toolbar layout |

## Known Limitations

1. **Manual trigger only**: Evaluation must be triggered manually by clicking "Run evaluation"
2. **No auto-evaluation**: Evaluation is not triggered automatically after analysis
3. **No evaluation history**: Only the last evaluation timestamp is shown

## Recommendations

Future improvements could include:

* Auto-trigger evaluation after project analysis
* Show evaluation history (last N evaluations)
* Add evaluation count badge on the button
