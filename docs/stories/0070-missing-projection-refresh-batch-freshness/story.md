# Story 0070 — Missing Projection Refresh Batch Freshness

## Status

Draft

## Priority

High

## Objective

Add a batch freshness check action for `MISSING_PROJECTION_REFRESH` maintenance
findings that triggers freshness verification for all sources and updates the
projection.

## Motivation

When a projection refresh is flagged as missing, users currently have to:

1. Navigate to the Overview page
2. Manually trigger freshness checks for each source
3. Wait for all checks to complete
4. Return to maintenance to mark as resolved

This creates friction and requires understanding of the freshness check workflow.
The batch action automates this process.

## Scope

### In Scope

1. Create `MaintenanceRemediationService.refreshMissingProjection()` method
2. Add `POST /actions/refresh-missing-projection` endpoint
3. Batch freshness check for all active sources
4. Update finding status to RESOLVED after successful refresh

### Out Of Scope

* Projection rebuild (handled by existing infrastructure)
* Source-specific refresh (covered by Story 0068)
* Auto-trigger after evaluation
* Progress indicator for batch check

## Constraints

* Must use existing `ProjectFreshnessService.check()` for each source
* Must not block UI during batch check
* Comment required for refresh action

## Acceptance Criteria

* AC-1: User can trigger refresh action from maintenance UI
* AC-2: Refresh action triggers batch freshness check for all sources
* AC-3: Finding transitions to RESOLVED after successful refresh
* AC-4: Error handling for individual source failures
* AC-5: All sources checked even if some fail

## Dependencies

* Story 0060-0065: Context Maintenance infrastructure
* Story 0068: Projection Refresh Gap Workflow (reuses batch freshness pattern)
* `ProjectFreshnessService` — existing freshness check per source
