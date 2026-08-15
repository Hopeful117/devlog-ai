# Story 0069 — Stale Human Context Archive Action — Repository Analysis

## Current State

### Backend

`ProjectHumanContextInputService` provides:
- `archive(projectId, inputId)` — sets input status to ARCHIVED

`ProjectHumanContextInputController` provides:
- `PATCH /api/v1/projects/{projectId}/context-inputs/{inputId}/archive` — HTTP endpoint

### Finding Structure

`STALE_HUMAN_CONTEXT_INPUT` findings contain:
- `details` field: Contains information about the stale input (e.g., "Input 'Medium-term objective' may be stale")
- `contextSurface`: `INTERNAL_HUMAN_CONTEXT`
- `suggestedAction`: `REVIEW`

### Current Workflow

Users can currently:
- Acknowledge the finding
- Dismiss the finding
- Resolve the finding (manual resolution)

But no action triggers the actual archival of the stale input.

## Gap Analysis

| Component | Current | Required |
|-----------|---------|----------|
| Archive service | ✅ Exists | No change |
| Archive endpoint | ✅ Exists | No change |
| Maintenance archive action | ❌ Not exists | Create new action |
| Input ID extraction | ❌ Not exists | Parse from finding details |
| Tests | ❌ No coverage | Add tests |

## Verification

Backend was verified via `ProjectHumanContextInputServiceTest` and
`MaintenanceFindingServiceTest`.
