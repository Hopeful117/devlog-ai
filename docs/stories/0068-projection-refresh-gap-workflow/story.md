# Story 0068 — Projection Refresh Gap Workflow

## Status

Draft

## Priority

High

## Objective

Enable workflow actions for `PROJECTION_REFRESH_GAP` maintenance findings and
add a batch freshness check action that triggers projection refresh for all
unchecked sources.

## Motivation

The `PROJECTION_REFRESH_GAP` finding type currently has **zero workflow support**
— it can be created but never acted upon. This creates a dead end where:

* Users see the finding but cannot acknowledge, dismiss, or resolve it
* No remediation action exists to refresh the projection
* The finding persists indefinitely with no resolution path

This Story closes the gap by adding workflow support and a batch freshness check.

## Scope

### In Scope

1. Add `PROJECTION_REFRESH_GAP` to backend `supportsWorkflow()` for Acknowledge/Dismiss/Resolve
2. Add `PROJECTION_REFRESH_GAP` to frontend `supportsWorkflow()` check
3. Create `MaintenanceRemediationService` with `refreshProjection()` method
4. Add `POST /actions/refresh-projection` endpoint
5. Batch freshness check for all unchecked sources via `ProjectFreshnessService`

### Out Of Scope

* Knowledge deduplication actions (Story 0072)
* Understanding refresh chain (Story 0071)
* Human context archive (Story 0069)
* Auto-trigger after evaluation

## Constraints

* Must use existing `ProjectFreshnessService.check()` for each source
* Must not block UI during batch freshness check
* Comment required for Dismiss/Resolve actions

## Acceptance Criteria

* AC-1: User can acknowledge a `PROJECTION_REFRESH_GAP` finding
* AC-2: User can dismiss a `PROJECTION_REFRESH_GAP` finding with comment
* AC-3: User can resolve a `PROJECTION_REFRESH_GAP` finding with comment
* AC-4: "Refresh projection" button triggers batch freshness check
* AC-5: Batch check processes all unchecked sources
* AC-6: Finding transitions to RESOLVED after successful refresh
* AC-7: Error states are handled gracefully

## Dependencies

* Story 0060-0065: Context Maintenance infrastructure
* Story 0066: Maintenance Workflow Activation
* `ProjectFreshnessService` — existing freshness check per source
* `ProjectFreshnessController` — existing `POST /freshness-checks` endpoint
