# Story 0076 — Pre-Promotion Duplicate Check

## Status

Draft

## Priority

High

## Objective

Add a duplicate check at insight creation time to prevent new duplicates
from entering the knowledge base, shifting quality from reactive (maintenance
evaluation) to proactive (creation gate).

## Motivation

Today, duplicate detection only runs during maintenance evaluation — after
the insight already exists. By the time the duplicate is detected, the user
has already seen and potentially relied on the duplicate insight. The
maintenance finding requires manual resolution.

Checking for duplicates at creation time:
* Prevents duplicate debt from accumulating
* Reduces maintenance finding noise
* Gives the user immediate feedback ("this insight already exists")
* Catches duplicates at the moment they are most actionable

## Scope

### In Scope

1. Add similarity check in `InsightPromotionService` before saving
2. Use `InsightSimilarityService` (from Story 0075) for similarity computation
3. Three outcomes: create, create + warn, block
4. Configurable thresholds and behavior per similarity level
5. Return similarity metadata in promotion response

### Out of Scope

* UI changes for duplicate warnings (future story)
* Bulk deduplication of existing duplicates (Story 0074 handles this)
* Cross-project duplicate detection
* Real-time similarity index updates (batch is sufficient)

## Constraints

* Must not slow down insight creation for non-duplicate cases (< 500ms)
* Must not block creation when similarity is below threshold
* Must integrate cleanly with existing proposal → validation → promotion flow

## Acceptance Criteria

* AC-1: New insight with similarity > 0.85 to existing insight is blocked
  with error message
* AC-2: New insight with similarity 0.65-0.85 is created with a
  `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW` finding
* AC-3: New insight with similarity < 0.65 is created normally
* AC-4: Thresholds are configurable via application properties
* AC-5: Promotion response includes similarity metadata
* AC-6: All existing tests pass

## Dependencies

* Story 0074: Fix overlap resolution (must be merged first)
* Story 0075: TF-IDF similarity service (must be merged first)
* `InsightPromotionService` — add pre-promotion check
* `InsightSimilarityService` — similarity computation
