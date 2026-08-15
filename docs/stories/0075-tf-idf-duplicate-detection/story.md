# Story 0075 — TF-IDF Duplicate Detection

## Status

Draft

## Priority

High

## Objective

Improve knowledge duplicate detection from simple title token overlap to
TF-IDF cosine similarity on title + content, catching duplicates that the
current approach misses.

## Motivation

The current duplicate detection has two passes:
1. **Exact fingerprint** — catches identical normalized content (works well)
2. **Topic clustering** — uses only title token overlap (Jaccard ≥ 0.4)

The topic clustering misses:
* Two insights with different titles but near-identical content bodies
* Insights about the same concept using different vocabulary
* Subtle semantic overlap that token overlap cannot capture

TF-IDF + cosine similarity on the full text (title + content + rationale)
provides a more robust similarity measure while remaining deterministic
(no LLM dependency).

## Scope

### In Scope

1. Implement TF-IDF vectorizer for insight text (title + content + rationale)
2. Replace title-token clustering with cosine similarity
3. Add configurable similarity thresholds per finding type
4. Maintain backward compatibility with existing finding types
5. Add repository index for efficient similarity search

### Out of Scope

* Embedding-based semantic similarity (future story)
* LLM-assisted duplicate evaluation (existing agent covers this)
* Cross-project duplicate detection
* Real-time similarity indexing on insight creation (Story 0076)

## Constraints

* Must remain deterministic (no external API calls for similarity)
* Must handle projects with 100+ insights efficiently
* Must not change existing API contracts or finding types

## Acceptance Criteria

* AC-1: TF-IDF similarity detects insights with different titles but
  near-identical content
* AC-2: Similarity thresholds are configurable per finding type
* AC-3: Existing exact duplicate detection is preserved
* AC-4: Performance is acceptable for projects with 200+ insights (< 5s)
* AC-5: All existing tests pass

## Dependencies

* Story 0074: Fix overlap resolution (must be merged first)
* `Insight` entity — needs text fields for vectorization
* Apache Commons Math or custom TF-IDF implementation (no new dependency
  preferred)
