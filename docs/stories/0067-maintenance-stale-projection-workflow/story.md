# Story 0067 — Maintenance Stale Projection Workflow

## Status

Draft

## Priority

High

## Objective

Enable user workflow actions (Dismiss) for `STALE_PROJECT_UNDERSTANDING` and
`MISSING_PROJECTION_REFRESH` maintenance findings, so users can dismiss false
positives instead of being forced to wait for auto-resolution.

## Motivation

The current implementation only supports Acknowledge/Dismiss/Resolve for
`TRUSTED_KNOWLEDGE_*` and `STALE_HUMAN_CONTEXT_INPUT` findings.

For `STALE_PROJECT_UNDERSTANDING` and `MISSING_PROJECTION_REFRESH`, users can
see the problem but have no way to act on it. This creates frustration when:

* A source is flagged as stale but the user judges it doesn't need refresh
* A projection is flagged as missing but the user considers it unnecessary
* The only resolution is waiting for auto-resolve when the condition clears

## Scope

### In Scope

1. Update backend `supportsWorkflow()` to allow Dismiss for `STALE_PROJECT_UNDERSTANDING` and `MISSING_PROJECTION_REFRESH`
2. Update frontend `supportsWorkflow()` to match
3. Add tests for the new workflow support

### Out Of Scope

* Acknowledge action for these types (not needed — user either dismisses or waits)
* Resolve action for these types (resolution is automatic via auto-resolve)
* New finding types
* Workflow changes for existing TRUSTED_KNOWLEDGE_* types

## Constraints

* Dismiss requires a mandatory comment (existing validation)
* Auto-resolve continues to work for cleared conditions
* Backend validation must prevent invalid state transitions

## Acceptance Criteria

* AC-1: User can dismiss a `STALE_PROJECT_UNDERSTANDING` finding with a comment
* AC-2: User can dismiss a `MISSING_PROJECTION_REFRESH` finding with a comment
* AC-3: Dismiss transitions finding to DISMISSED status
* AC-4: Comment is mandatory for dismiss action
* AC-5: Auto-resolve still works for cleared conditions
* AC-6: All existing tests pass

## Dependencies

* Story 0060-0065: Context Maintenance infrastructure
* Story 0066: Maintenance Workflow Activation
