# Code Review — Story 0075

## Verdict

The implementation is clean and extends the existing `InsightSimilarityService` without duplicating its classification logic. The TF-IDF vectorizer is a pure Java implementation with no external dependencies, and the configurable thresholds in `InsightSimilarityProperties` are reused rather than redefined. All changes are backward-compatible with existing API contracts and finding types.

## Review Findings

### Functional and Architectural Review

#### TF-IDF Vectorizer (correct)

The `InsightSimilarityService.computeTfIdfVector()` and `cosineSimilarity()` methods provide a deterministic, self-contained similarity calculation. Tokenization, stop-word filtering, stemming, and IDF weighting are all implemented purely in Java with no LLM dependency. The unit tests cover the full spectrum: identical docs (1.0), completely different (0.0), and partial similarity (0.5-0.8). The existing `duplicateThreshold=0.85` and `overlapThreshold=0.45` properties are reused, maintaining consistency with the maintenance subsystem.

#### Audit Service Update (correct)

In `TrustedKnowledgeDuplicateAuditService`, replacing the title-token BFS with TF-IDF pairwise similarity preserves the existing cluster classification logic (EXACT_DUPLICATE / LIKELY_RICHER_SUCCESSOR / LIKELY_SEMANTIC_DUPLICATE / REVIEW_REQUIRED). The union-find clustering with configurable threshold is a clean extension of the existing pass-2 mechanism. The backward compatibility guarantee is maintained: exact fingerprint pass-1 remains unchanged.

#### Configuration Reuse (correct)

No new Spring properties were introduced. The existing `InsightSimilarityProperties` with `exactThreshold=1.0`, `duplicateThreshold=0.85`, `overlapThreshold=0.45` are reused, which is the correct design — adding arbitrary thresholds would fragment the maintenance domain model.

#### Performance (correct)

The repository-index approach (project-scoped, status-aware, bounded comparison) is appropriate for the stated constraint of 200+ insights in < 5s. No vector database or external index was introduced, keeping the deployment footprint minimal.

### Test Review

* `InsightSimilarityServiceTest`: 9 tests cover vectorization, cosine similarity edge cases, and threshold behavior — all pass
* `TrustedKnowledgeDuplicateAuditServiceTest`: 5 tests updated to verify TF-IDF cluster detection alongside exact fingerprint — all pass
* `KnowledgeDeduplicationServiceTest`: 15 tests verify that post-creation resolution still works with the new similarity-aware audit — all pass
* **754 backend tests pass**, 0 failures, 0 errors — the full test suite validates the integration

### Data and Compatibility Review

No database migrations required. No schema changes. The `InsightSimilarityService` and `InsightSimilarityProperties` already existed from story 0074. No new API contracts. No frontend changes.

### Residual Risks

* **Medium** — TF-IDF similarity is term-based, not embedding-based. Insights with different vocabulary but similar meaning may score below the threshold and not be detected as duplicates. This is acceptable because the exact fingerprint pass-1 covers the deterministic case, and the semantic gap is intentionally left for manual review.
* **Low** — The performance constraint (< 5s for 200 insights) was measured on a single development machine. Production workloads with heavy duplicate clusters may vary.

### Repository Hygiene

* No secrets or credentials in any diff
* No hardcoded paths or environment-specific values
* All new code follows existing patterns (the `InsightSimilarityService` was already part of the codebase from story 0074)
* Test coverage is maintained at the high level established by the existing suite

## Verdict

Accept. The story adds robust TF-IDF duplicate detection as an enhancement over simple token overlap, without disrupting the existing deterministic duplicate prevention flow. The 754/754 test pass rate confirms no regression.