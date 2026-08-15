# Story 0072 — Trusted Knowledge Deduplication Service — Repository Analysis

## Current State

### Backend

`Insight` entity (`backend/src/main/java/com/hopeful117/devlogai/insight/entity/Insight.java`):
- No status field (all insights are implicitly ACTIVE)
- No archive or supersede operations

`InsightService`:
- `getByProject(projectId)` — returns all insights
- No archive or supersede methods

`TrustedKnowledgeDuplicateAuditService`:
- `audit(projectId)` — detects duplicate clusters
- Returns recommendations: `KEEP_NEWEST_AS_CANONICAL`, `KEEP_RICHEST_AS_CANONICAL`, `REVIEW_MANUALLY`

`KnowledgeRelationService`:
- `delete(id)` — deletes knowledge relations
- No transfer or merge operations

### Frontend

`MaintenanceFindingService`:
- No deduplication action methods

`ProjectMaintenanceSection`:
- `supportsWorkflow()` returns true for `TRUSTED_KNOWLEDGE_*` types
- But no remediation action exists

## Gap Analysis

| Component | Current | Required |
|-----------|---------|----------|
| Insight status field | ❌ Not exists | Add ACTIVE/ARCHIVED/SUPERSEDED |
| Insight archive/supersede | ❌ Not exists | Create new methods |
| Knowledge deduplication service | ❌ Not exists | Create new service |
| Merge endpoint | ❌ Not exists | Create new endpoint |
| Relation transfer | ❌ Not exists | Create new operation |
| Tests | ❌ No coverage | Add tests |

## Verification

Backend was verified via `InsightServiceTest` and
`TrustedKnowledgeDuplicateAuditServiceTest`.
