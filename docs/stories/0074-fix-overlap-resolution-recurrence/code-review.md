# Code Review — Story 0074

## Verdict

The implementation is clean and surgically targeted. Three independent bugs
are fixed with minimal code changes. The new `KnowledgeRelation` creation
on supersede is non-fatal (catch + log), which is the right design choice
for a traceability feature that should never block the deduplication flow.
All changes are backward-compatible.

## Review Findings

### Functional and Architectural Review

**Audit input filtering (correct)**

Replacing `findByProjectIdOrderByCreatedAtDescIdDesc` with
`findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, List.of(InsightStatus.ACTIVE))`
is the minimal correct fix. SUPERSEDED and ARCHIVED insights are now
excluded from duplicate clustering, which prevents the recurrence loop.
The query uses Spring Data derived method naming — no JPQL to maintain.

**Finding deduplication guard (correct)**

Changing from `OPEN || ACKNOWLEDGED` to `!= DISMISSED` is the right
semantic. A RESOLVED finding means the condition was already addressed;
re-creating an identical finding would be a regression. DISMISSED findings
are explicitly false positives and should not block new findings. The
filter is now: OPEN, ACKNOWLEDGED, RESOLVED — all "active" states.

**KnowledgeRelation creation on supersede (well-designed)**

The `try/catch` around `knowledgeRelationService.create()` is appropriate.
Relation creation is traceability, not correctness — failing to log the
relationship should not prevent the insight from being superseded. The
`log.warn` level is correct for a non-fatal degradation. The builder
pattern matches the existing `CreateKnowledgeRelationRequest` convention.

**Status-filtered repository query (clean)**

`findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` takes a
`Collection<InsightStatus>` parameter, making it reusable for any
status combination. The `List.of(InsightStatus.ACTIVE)` call site is
clear about intent.

### Test Review

* `TrustedKnowledgeDuplicateAuditServiceTest`: 5 tests updated to use
  new status-filtered query — all pass
* `MaintenanceEvaluationServiceTest`: 3 new tests added:
  * `shouldSkipDuplicateFindingWhenEquivalentResolvedFindingExists`
  * `shouldSkipOverlapReviewWhenEquivalentResolvedFindingExists`
  * `shouldSkipEquivalentOpenDuplicateDebtFinding` (updated)
* `InsightServiceTest`: 2 new tests:
  * `shouldSupersedeInsightAndCreateResolvesRelation`
  * `shouldSupersedeInsightEvenIfRelationCreationFails`
* **744 backend tests pass**, 0 failures

### Data and Compatibility Review

No database migrations required. No schema changes. The
`KnowledgeRelation` entity and `RESOLVES` relation type already existed
from Story 0025. No new API contracts. No frontend changes.

## Residual Risks

* **Low — No frontend feedback for relation creation failure**: If the
  RESOLVES relation fails, the user sees no indication. This is acceptable
  because the relation is audit-only and the supersede itself succeeds.
* **Low — No retroactive relation creation**: Existing superseded insights
  from before this fix will not have RESOLVES relations. A one-time
  migration script could backfill, but this is out of scope.

## Repository Hygiene

* No secrets or credentials in diff
* No hardcoded paths or environment-specific values
* All new code follows existing patterns and conventions
* Test coverage maintained at high level
