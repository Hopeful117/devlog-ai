# Story 0066 — Maintenance Workflow Activation

## Status

Draft

## Priority

High

## Objective

Add the missing UI trigger for context maintenance evaluation, completing the
workflow defined in ADR-053/054 so users can trigger evaluations and act on
findings directly from the project cockpit.

## Motivation

The context maintenance agents (ADR-053/054) work correctly but were never
triggered from the UI. This created a broken workflow where:

* The backend `POST /evaluations` endpoint exists and works
* The frontend displays findings correctly
* But nothing triggers the evaluation from the UI
* Users have no way to see or act on maintenance findings

This Story closes the workflow gap by adding the missing UI trigger.

## Scope

### In Scope

1. Add `evaluate()` method to `MaintenanceFindingService`
2. Add `MaintenanceEvaluationResponse` model to frontend types
3. Add evaluation state management to `ProjectMaintenanceSection` component
4. Add "Run evaluation" button with loading/error/timestamp feedback
5. Add toolbar styles for the maintenance section

### Out Of Scope

* Auto-trigger evaluation after analysis
* Evaluation history (only last timestamp shown)
* Evaluation count badge
* Separate maintenance page (remains in cockpit)

## Constraints

* Must use existing backend `POST /evaluations` endpoint
* Must follow existing Angular patterns in the codebase
* Must not break existing maintenance section functionality

## Acceptance Criteria

* AC-1: A "Run evaluation" button is visible in the maintenance section of the cockpit
* AC-2: Clicking the button triggers `POST /api/v1/projects/{id}/maintenance-findings/evaluations`
* AC-3: A loading state is shown while evaluation runs
* AC-4: After evaluation completes, findings are refreshed and displayed
* AC-5: The last evaluation timestamp is shown in the maintenance section
* AC-6: Error states are handled gracefully

## Dependencies

* ADR-053 — Internal Context Maintenance Capability
* ADR-054 — Context Maintenance Agent
* Story 0060-0065: Context Maintenance infrastructure (completed)

## Artifacts

* `repository-analysis.md`
* `implementation-plan.md`
* `implementation-report.md`
* `code-review.md`
* `engineering-report.md`
