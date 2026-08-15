# Story 0071 — Stale Understanding Chain Refresh — Repository Analysis

## Current State

### Backend

`MaintenanceFindingServiceImpl.supportsWorkflow()` (line 184) defines:

```java
// AUTO_RESOLVE: STALE_PROJECT_UNDERSTANDING, MISSING_PROJECTION_REFRESH, STALE_HUMAN_CONTEXT_INPUT
// Manual actions: STALE_HUMAN_CONTEXT_INPUT, TRUSTED_KNOWLEDGE_*
// STALE_PROJECT_UNDERSTANDING: Only AUTO_RESOLVE, not manual actions
```

`STALE_PROJECT_UNDERSTANDING` only supports AUTO_RESOLVE, not manual Dismiss/Resolve.

### Existing Infrastructure

`ProjectFreshnessService` provides:
- `check(projectId, sourceId)` — checks freshness for a single source
- `summary(projectId)` — returns freshness summary across all active sources

`ProjectUnderstandingService` provides:
- `execute(projectId, request)` — full analysis workflow

`ProjectFreshnessController` provides:
- `POST /api/v1/projects/{projectId}/freshness-checks` — HTTP endpoint for freshness check

`ProjectUnderstandingController` provides:
- `POST /api/v1/projects/{projectId}/understanding-executions` — HTTP endpoint for understanding re-analysis

## Gap Analysis

| Component | Current | Required |
|-----------|---------|----------|
| Backend workflow support | ✅ AUTO_RESOLVE only | Add manual actions |
| Chained refresh action | ❌ Not exists | Create new action |
| Freshness → Understanding chain | ❌ Not exists | Create new workflow |
| Tests | ❌ No coverage | Add tests |

## Verification

Backend was verified via `MaintenanceFindingServiceImplTest` and
`ProjectUnderstandingServiceTest`.
