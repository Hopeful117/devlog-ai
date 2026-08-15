# Story 0075 — TF-IDF Duplicate Detection — Engineering Report

## Status

Reported

## Story

| Field | Value |
|-------|-------|
| Number | 0075 |
| Title | TF-IDF Duplicate Detection |
| Status | Done |
| Acceptance Criteria | 5/5 satisfied |

## Scope Delivered

### Implemented

* `InsightSimilarityService.computeTfIdfVector()` — lightweight TF-IDF vectorizer for insight text (title + content + rationale)
  * Tokenization: lowercase, strip punctuation, filter stop words, stem
  * TF: term frequency / total terms in document
  * IDF: log(total documents / documents containing term)
  * Vector: sparse map of term → tf-idf weight
* `InsightSimilarityService.cosineSimilarity()` — cosine similarity between two vectors
  * dot product / (||a|| * ||b||)
  * Edge case: empty vectors return 0.0
* Configuration via `InsightSimilarityProperties`:
  * `exactThreshold` (default: 1.0)
  * `duplicateThreshold` (default: 0.85) — high confidence duplicate
  * `overlapThreshold` (default: 0.45) — overlap review
* Replaced title-token BFS clustering in `TrustedKnowledgeDuplicateAuditService.audit()` with TF-IDF pairwise similarity and union-find clustering with configurable threshold
* Existing cluster classification preserved: LIKELY_RICHER_SUCCESSOR, LIKELY_SEMANTIC_DUPLICATE, REVIEW_REQUIRED
* Similarity score added to cluster metadata for UI display
* All 5 acceptance criteria satisfied

### Deferred

* Retroactive re-evaluation of existing duplicate findings using new TF-IDF scores
* Embedding-based semantic similarity (future story, out of scope per story 0075)
* Real-time similarity indexing on insight creation (mapped to Story 0076)

## Design Outcome

### Boundary Retained

The fix is surgically scoped to two existing services: `InsightSimilarityService` and `TrustedKnowledgeDuplicateAuditService`. No new classes, no new endpoints, no schema changes. The TF-IDF algorithm is a new implementation within the existing service, and the clustering extension is a natural evolution of the existing pass-2 mechanism.

### Why This Matters

The current duplicate detection misses insights with different titles but near-identical content bodies, and insights about the same concept using different vocabulary. TF-IDF + cosine similarity provides a more robust similarity measure while remaining deterministic (no LLM dependency) and preserving all existing finding types and API contracts.

### Implementation Summary

| File | Change |
|------|--------|
| `InsightSimilarityService.java` | Added `computeTfIdfVector()` and updated `computeSimilarity()` to accept corpus |
| `InsightSimilarityProperties.java` | No changes — existing thresholds reused |
| `TrustedKnowledgeDuplicateAuditService.java` | Pass 2 now uses TF-IDF pairwise similarity instead of title-token BFS |
| `InsightSimilarityServiceTest.java` | 9 new/updated tests for vectorization, cosine similarity, threshold behavior |
| `TrustedKnowledgeDuplicateAuditServiceTest.java` | 5 tests updated to verify TF-IDF cluster detection |
| `KnowledgeDeduplicationServiceTest.java` | Verified post-creation resolution works with new audit flow |

## Current Dataset Outcome

Before this story, duplicate detection relied on exact fingerprint (identical normalized content) and title-token overlap (Jaccard ≥ 0.4). After this story, insights with different titles but near-identical content are detected as duplicates, while preserving exact duplicate detection and all existing finding types.

## Quality Gates

* backend tests: PASS (754 total, including 14 new/updated)
* backend verify: PASS with JaCoCo checks
* No frontend changes required
* All acceptance criteria satisfied

## Documentation Outcome

This story folder is the canonical documentation. Story 0076 documentation is also included in the same branch (pre-promotion similarity awareness).

## Limitations

1. TF-IDF is term-based, not embedding-based. Insights with different vocabulary but similar meaning may score below threshold.
2. The performance constraint (< 5s for 200 insights) was validated on the development workload; production clusters may vary.
3. Existing duplicate findings before this story are not retroactively re-evaluated.

## Next Architectural Questions

1. Should there be a scheduled job to re-evaluate existing findings with the new TF-IDF scores?
2. Should the UI display the similarity score alongside the duplicate cluster category?
3. Should Story 0076 (pre-promotion similarity awareness) integrate with this TF-IDF framework or remain independent?