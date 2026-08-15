# Story 0068 — Projection Refresh Gap Workflow — Repository Analysis

## Current State

### Backend

`MaintenanceFindingServiceImpl.supportsWorkflow()` (line 184) defines:

```java
// AUTO_RESOLVE: STALE_PROJECT_UNDERSTANDING, MISSING_PROJECTION_REFRESH, STALE_HUMAN_CONTEXT_INPUT
// Manual actions: STALE_HUMAN_CONTEXT_INPUT, TRUSTED_KNOWLEDGE_*
// PROJECTION_REFRESH_GAP: NOT INCLUDED
```

`PROJECTION_REFRESH_GAP` is completely excluded from workflow support.

### Existing Freshness Infrastructure

`ProjectFreshnessService` provides:
- `check(projectId, sourceId)` — checks freshness for a single source
- `summary(projectId)` — returns freshness summary across all active sources

`ProjectFreshnessController` provides:
- `POST /api/v1/projects/{projectId}/freshness-checks` — HTTP endpoint for freshness check

### Frontend

`ProjectMaintenanceSection.supportsWorkflow()` (line 102) checks:

```typescript
finding.issueType === 'TRUSTED_KNOWLEDGE_EXACT_DUPLICATE' ||
finding.issueType === 'TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE' ||
finding.issueType === 'TRUSTED_KNOWLEDGE_OVERLAP_REVIEW' ||
finding.issueType === 'STALE_HUMAN_CONTEXT_INPUT' ||
finding.issueType === 'STALE_PROJECT_UNDERSTANDING' ||
finding.issueType === 'MISSING_PROJECTION_REFRESH'
```

`PROJECTION_REFRESH_GAP` is not included.

## Gap Analysis

| Component | Current | Required |
|-----------|---------|----------|
| Backend workflow support | ❌ Not included | Add Acknowledge/Dismiss/Resolve |
| Frontend buttons | ❌ Not shown | Show action buttons |
| Batch freshness action | ❌ Not exists | Create new endpoint |
| Tests | ❌ No coverage | Add tests |

## Verification

Backend was verified via `MaintenanceFindingServiceImplTest` and
`MaintenanceEvaluationServiceTest`.
