# Story 0071 — Stale Understanding Chain Refresh

## Status

Draft

## Priority

High

## Objective

Add a chained refresh action for `STALE_PROJECT_UNDERSTANDING` maintenance
findings that triggers a freshness check followed by a project understanding
re-analysis.

## Motivation

When project understanding is flagged as stale, users currently have to:

1. Navigate to the Overview page
2. Trigger a freshness check
3. Wait for the check to complete
4. Manually trigger a project understanding re-analysis
5. Wait for the analysis to complete
6. Return to maintenance to mark as resolved

This creates significant friction and requires understanding of the two-step
process. The chained action automates this workflow.

## Scope

### In Scope

1. Create `MaintenanceRemediationService.refreshProjectUnderstanding()` method
2. Add `POST /actions/refresh-understanding` endpoint
3. Chain freshness check → understanding re-analysis
4. Update finding status to RESOLVED after successful refresh

### Out Of Scope

* Partial refresh (freshness only or understanding only)
* Auto-trigger after evaluation
* Progress indicator for chained operations
* Rollback on failure

## Constraints

* Must use existing `ProjectFreshnessService.check()` for freshness
* Must use existing `ProjectUnderstandingService.execute()` for re-analysis
* Must handle failures in either step gracefully
* Comment required for refresh action

## Acceptance Criteria

* AC-1: User can trigger refresh action from maintenance UI
* AC-2: Refresh action triggers freshness check for all sources
* AC-3: Refresh action triggers understanding re-analysis after freshness check
* AC-4: Finding transitions to RESOLVED after successful refresh
* AC-5: Error handling for freshness check failures
* AC-6: Error handling for understanding re-analysis failures
* AC-7: Understanding not triggered if freshness check fails

## Dependencies

* Story 0060-0065: Context Maintenance infrastructure
* `ProjectFreshnessService` — existing freshness check per source
* `ProjectUnderstandingService` — existing understanding re-analysis
