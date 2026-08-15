# Story 0067 — Maintenance Stale Projection Workflow — Repository Analysis

## Current State

### Backend

`MaintenanceFindingServiceImpl.supportsWorkflow()` (line 184) defines which
finding types support which actions:

```java
// AUTO_RESOLVE: STALE_PROJECT_UNDERSTANDING, MISSING_PROJECTION_REFRESH, STALE_HUMAN_CONTEXT_INPUT
// Manual actions (ACKNOWLEDGE, DISMISS, RESOLVE): STALE_HUMAN_CONTEXT_INPUT, TRUSTED_KNOWLEDGE_*
```

`STALE_PROJECT_UNDERSTANDING` and `MISSING_PROJECTION_REFRESH` only support
AUTO_RESOLVE, not manual Dismiss.

### Frontend

`ProjectMaintenanceSection.supportsWorkflow()` (line 102) checks:

```typescript
finding.issueType === 'TRUSTED_KNOWLEDGE_EXACT_DUPLICATE' ||
finding.issueType === 'TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE' ||
finding.issueType === 'TRUSTED_KNOWLEDGE_OVERLAP_REVIEW' ||
finding.issueType === 'STALE_HUMAN_CONTEXT_INPUT'
```

`STALE_PROJECT_UNDERSTANDING` and `MISSING_PROJECTION_REFRESH` are not included.

## Gap Analysis

| Component | Current | Required |
|-----------|---------|----------|
| Backend AUTO_RESOLVE | ✅ Works | No change |
| Backend manual Dismiss | ❌ Not supported | Add support |
| Frontend buttons | ❌ Not shown | Show Dismiss button |
| Tests | ❌ No coverage | Add tests |

## Verification

Backend validation was verified via `MaintenanceFindingServiceImplTest` and
`MaintenanceEvaluationServiceTest`.
