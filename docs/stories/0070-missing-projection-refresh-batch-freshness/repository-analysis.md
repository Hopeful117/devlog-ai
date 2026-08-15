# Story 0070 — Missing Projection Refresh Batch Freshness — Repository Analysis

## Current State

### Backend

`MaintenanceFindingServiceImpl.supportsWorkflow()` (line 184) defines:

```java
// AUTO_RESOLVE: STALE_PROJECT_UNDERSTANDING, MISSING_PROJECTION_REFRESH, STALE_HUMAN_CONTEXT_INPUT
// Manual actions: STALE_HUMAN_CONTEXT_INPUT, TRUSTED_KNOWLEDGE_*
// MISSING_PROJECTION_REFRESH: Only AUTO_RESOLVE, not manual actions
```

`MISSING_PROJECTION_REFRESH` only supports AUTO_RESOLVE, not manual Dismiss/Resolve.

### Existing Freshness Infrastructure

`ProjectFreshnessService` provides:
- `check(projectId, sourceId)` — checks freshness for a single source
- `summary(projectId)` — returns freshness summary across all active sources

`ProjectFreshnessController` provides:
- `POST /api/v1/projects/{projectId}/freshness-checks` — HTTP endpoint for freshness check

## Gap Analysis

| Component | Current | Required |
|-----------|---------|----------|
| Backend workflow support | ✅ AUTO_RESOLVE only | Add manual actions |
| Batch freshness action | ❌ Not exists | Create new action |
| Tests | ❌ No coverage | Add tests |

## Verification

Backend was verified via `MaintenanceFindingServiceImplTest` and
`ProjectFreshnessServiceTest`.
