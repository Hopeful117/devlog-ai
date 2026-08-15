# Story 0074 — Fix Overlap Resolution Recurrence — Engineering Report

## Status

Reported

## Story

| Field | Value |
|-------|-------|
| Number | 0074 |
| Title | Fix Overlap Resolution Recurrence |
| Status | Done |
| Acceptance Criteria | 5/5 satisfied |

## Scope Delivered

### Implemented

* `InsightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` —
  status-filtered query to exclude SUPERSEDED/ARCHIVED insights from audit
* `TrustedKnowledgeDuplicateAuditService.audit()` — now uses ACTIVE-only
  insight list for duplicate clustering
* `MaintenanceEvaluationServiceImpl.hasEquivalentActiveFinding()` — treats
  RESOLVED findings as non-recurring equivalents (changed from `OPEN ||
  ACKNOWLEDGED` to `!= DISMISSED`)
* `InsightServiceImpl.supersedeInsight()` — creates `KnowledgeRelation(RESOLVES)`
  between superseded and canonical insight with graceful failure handling
* 6 new unit tests across 3 test classes

### Deferred

* Retroactive RESOLVES relation backfill for existing superseded insights
* Content merging from superseded to canonical insight
* Frontend indication of relation creation failure

## Design Outcome

### Boundary Retained

The fix is surgically scoped to three existing services. No new classes,
no new endpoints, no schema changes. The `KnowledgeRelationService`
dependency added to `InsightServiceImpl` is a natural extension — the
service already existed and was already used by the deduplication flow.

### Why This Matters

The overlap resolution loop was a critical UX bug: users could not
permanently resolve duplicate findings. This fix makes the maintenance
dashboard trustworthy — resolved findings stay resolved.

## Implementation Summary

### Added

| File | Change |
|------|--------|
| `InsightRepository.java` | Added `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` |
| `TrustedKnowledgeDuplicateAuditService.java` | Uses ACTIVE-only insight query |
| `MaintenanceEvaluationServiceImpl.java` | `hasEquivalentActiveFinding` includes RESOLVED |
| `InsightServiceImpl.java` | `supersedeInsight` creates RESOLVES relation |
| `MaintenanceEvaluationServiceTest.java` | 3 new tests for RESOLVED finding detection |
| `InsightServiceTest.java` | 2 new tests for supersede relation creation |
| `TrustedKnowledgeDuplicateAuditServiceTest.java` | 5 tests updated for new query method |

## Current Dataset Outcome

Before this story, resolving an overlap finding caused it to reappear on
the next evaluation cycle. After this story, resolved findings stay
resolved and superseded insights are excluded from duplicate audit.

## Quality Gates

* backend tests: PASS (744 total, 6 new)
* backend verify: PASS
* frontend lint: PASS
* No frontend changes required

## Documentation Outcome

This story folder is the canonical documentation. Stories 0075 and 0076
documentation also included (planning only).

## Limitations

1. Existing superseded insights before this fix have no RESOLVES relations
2. Relation creation failure is silent (log only)
3. RESOLVED findings prevent re-creation even if the underlying issue recurs

## Next Architectural Questions

1. Should there be a retroactive migration to backfill RESOLVES relations
   for previously superseded insights?
2. Should the frontend show a warning when relation creation fails?
3. Should RESOLVED findings have an expiry after which they no longer
   block re-creation?
