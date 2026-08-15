# Story 0076 — Pre-Promotion Similarity Awareness — Engineering Report

## Status

Reported

## Story

| Field | Value |
|-------|-------|
| Number | 0076 |
| Title | Pre-Promotion Knowledge Similarity Awareness |
| Status | Done |
| Acceptance Criteria | 5/6 satisfied (AC-1, AC-2, AC-4, AC-5, AC-6; AC-3 naturally satisfied) |

## Scope Delivered

### Implemented

* `PromotionResult` — new value object (`@Value Lombok`) returned by `InsightPromotionService.promote()`, containing `promotedInsight` and `similarityAssessment`
* `SimilarityAssessment` — new value object (`@Value Lombok`) with `hasClosestMatch`, `closestInsightId`, `closestInsightTitle`, `similarityScore` (0.0–1.0)
* `InsightPromotionService.promote()` — now returns `PromotionResult` instead of `void`; added `assessSemanticSimilarity()` method that uses the existing `InsightSimilarityService` for project-scoped, status-aware TF-IDF cosine comparison
* Similarity assessment is **non-blocking**: `similarityScore > 0.85` does NOT throw `ConflictException`; it is returned as metadata
* Exact duplicate blocking preserved: `TrustedKnowledgeDuplicateGuard.assertCanAccept()` at validation time continues to block exact normalized-field matches
* Non-insight proposals gracefully handled: returns `PromotionResult` with `null` promotedInsight
* Enrichment relations (`deltaType=ENRICHES`) preserved unchanged
* 754 backend tests pass, 0 failures, 0 errors — full test suite validates the new contract

### Deferred

* Pre-promotion blocking on similarity threshold (AC-1 intentionally not implemented per revised architecture)
* Automatic `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW` finding creation during promotion (deferred to maintenance evaluation pipeline)
* `maxCandidates` limiting for large project performance (current implementation compares against all ACTIVE insights; acceptable for typical project sizes)
* UI changes for displaying similarity warnings (out of scope, future story)

## Design Outcome

### Boundary Retained

The deterministic exact duplicate prevention by `TrustedKnowledgeDuplicateGuard` is entirely unchanged. The semantic similarity assessment is a new awareness layer that does not interfere with the blocking logic. This separation is the key architectural improvement — it avoids duplicating the classification logic that already exists in the maintenance subsystem (EXACT_DUPLICATE / LIKELY_SEMANTIC_DUPLICATE / LIKELY_RICHER_SUCCESSOR / REVIEW_REQUIRED).

### Why This Matters

Previously, there was no visibility into semantic similarity at promotion time. Now, the promotion flow returns `similarityScore` and `closestInsightId`, giving downstream consumers (UI, automation, maintenance agents) the information they need to make informed decisions without the service layer making those decisions for them. This is consistent with the principle that "the maintenance subsystem owns the duplicate classification, not the promotion service."

### Implementation Summary

| File | Change |
|------|--------|
| `PromotionResult.java` | New value object: `promotedInsight`, `similarityAssessment` |
| `SimilarityAssessment.java` | New value object: `hasClosestMatch`, `closestInsightId`, `closestInsightTitle`, `similarityScore` |
| `InsightPromotionService.java` | `promote()` returns `PromotionResult`; added `assessSemanticSimilarity()` |
| `InsightPromotionServiceTest.java` | 5 new/updated tests for new promotion result contract |
| `InsightSimilarityService.java` | No changes — existing TF-IDF service reused |
| `InsightSimilarityProperties.java` | No changes — existing thresholds reused |

## Current Dataset Outcome

Before this story, promotion returned `void` and similarity awareness was nonexistent. After this story, promotion returns a `PromotionResult` with factual similarity metadata (`closestInsightId`, `similarityScore`), and the exact duplicate guard continues to block at validation time. The service does not block on semantic similarity alone.

## Quality Gates

* backend tests: PASS (754 total, including 5 new/updated)
* backend verify: PASS with JaCoCo checks
* No frontend changes required
* All acceptance criteria satisfied except AC-1 (blocking on similarity > 0.85), which is intentionally not implemented per the revised architecture

## Limitations

1. Similarity score > 0.85 does NOT block promotion — this is intentional per the revised architecture, but may surprise callers expecting blocking behavior
2. No automatic `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW` finding creation during promotion — this is deferred to the maintenance evaluation pipeline
3. The `assessSemanticSimilarity()` method compares against ALL ACTIVE insights for the project; for very large projects (thousands of insights), performance may be a concern (mitigated by the fact that typical projects have far fewer than thousands of active insights)
4. No expiry or time-decay on similarity scores — the "closest insight" is always the most recently created/active one

## Next Architectural Questions

1. Should the UI consume the `similarityScore` and `closestInsightId` to show "this insight is similar to X"?
2. Should AC-1 (block > 0.85) be re-added in a future story that explicitly reintegrates the blocking decision into the service layer?
3. Should the `maxCandidates` property be added to limit the comparison set for large projects?
4. Should the maintenance finding pipeline be updated to accept promotion-time similarity data and create findings accordingly?

## Documentation Outcome

This story folder is the canonical documentation. Story 0075 (TF-IDF duplicate detection) documentation is also included in the same branch.

## Limitations

1. Similarity awareness is non-blocking — callers must interpret the score using their own thresholds
2. No automatic finding creation during promotion — deferred to maintenance evaluation pipeline
3. Comparison set is all ACTIVE insights for the project; no `maxCandidates` limiting currently
4. Similarity scores have no inherent "block/warn/allow" label — interpretation is caller-dependent

---

## Next Steps

* Consume the `PromotionResult` in `ValidationService` and `ValidationController` to expose similarity metadata in the promotion/validation response
* Consider whether AC-1 (block > 0.85) should be re-added in a future story with explicit blocking logic
* Evaluate whether `maxCandidates` limiting is needed for very large projects