# Story 0069 — Stale Human Context Archive Action

## Status

Draft

## Priority

High

## Objective

Add an archive action for `STALE_HUMAN_CONTEXT_INPUT` maintenance findings
that allows users to archive the stale human context input directly from the
maintenance UI.

## Motivation

When a human context input is flagged as stale, users currently have to:

1. Navigate to the Settings page
2. Find the specific input
3. Manually archive it
4. Return to maintenance to mark as resolved

This creates friction and requires the user to know which input to archive.
The finding's `details` field contains the input ID, enabling automated archival.

## Scope

### In Scope

1. Create `MaintenanceRemediationService.archiveStaleHumanContext()` method
2. Add `POST /actions/archive-context-input` endpoint
3. Parse finding details to extract input ID
4. Call existing `ProjectHumanContextInputService.archive()`
5. Update finding status to RESOLVED

### Out Of Scope

* Creating replacement human context input
* Bulk archival of multiple stale inputs
* Undo archival action
* Auto-archive after evaluation

## Constraints

* Must use existing `ProjectHumanContextInputService.archive()`
* Finding details must contain valid input ID
* Comment required for archive action

## Acceptance Criteria

* AC-1: User can trigger archive action from maintenance UI
* AC-2: Archive action extracts input ID from finding details
* AC-3: Archive action calls `ProjectHumanContextInputService.archive()`
* AC-4: Finding transitions to RESOLVED after successful archival
* AC-5: Error handling for invalid input ID
* AC-6: Error handling for archive service failures

## Dependencies

* Story 0060-0065: Context Maintenance infrastructure
* `ProjectHumanContextInputService.archive()` — existing archive method
* `ProjectHumanContextInputController` — existing `PATCH /archive` endpoint
