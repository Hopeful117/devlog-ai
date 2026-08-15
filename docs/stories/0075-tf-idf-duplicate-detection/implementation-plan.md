# Story 0075 — TF-IDF Duplicate Detection — Implementation Plan

## Phase 1: TF-IDFVectorizer

### 1.1 New Service
* Create `InsightSimilarityService` in `com.hopeful117.devlogai.insight.service`
* Implement lightweight TF-IDF vectorizer:
  * Tokenization: lowercase, strip punctuation, filter stop words, stem
  * TF: term frequency / total terms in document
  * IDF: log(total documents / documents containing term)
  * Vector: sparse map of term → tf-idf weight
* Implement `cosineSimilarity(Vector a, Vector b)`:
  * dot product / (||a|| * ||b||)
* No external dependencies — pure Java implementation

### 1.2 Configuration
* Add `devlog.insight.similarity` properties:
  * `exactThreshold` (default: 1.0)
  * `duplicateThreshold` (default: 0.85) — high confidence duplicate
  * `overlapThreshold` (default: 0.65) — overlap review
* Inject via `@ConfigurationProperties`

## Phase 2: Replace Clustering in Audit

### 2.1 Audit Service Update
* In `TrustedKnowledgeDuplicateAuditService`:
  * Keep Pass 1 (exact fingerprint) unchanged
  * Replace Pass 2 (title token BFS) with TF-IDF similarity:
    * For each non-exact-duplicate insight, compute TF-IDF vector
    * Pairwise cosine similarity matrix
    * Union-Find clustering with configurable threshold
    * Classify clusters same as before (LIKELY_RICHER_SUCCESSOR, etc.)

### 2.2 Cluster Classification
* Existing classification logic preserved:
  * Richness score delta for LIKELY_RICHER_SUCCESSOR
  * Same family key for LIKELY_SEMANTIC_DUPLICATE
  * Fallback to REVIEW_REQUIRED
* Add similarity score to cluster metadata for UI display

## Phase 3: Tests

### 3.1 Unit Tests
* TF-IDF vectorizer: tokenization, TF, IDF, cosine similarity
* Cosine similarity: identical docs (1.0), completely different (0.0),
  partially similar (0.5-0.8)
* Clustering: two similar insights form cluster, dissimilar don't
* Threshold config: different thresholds produce different clusters

### 3.2 Integration Tests
* Audit service with TF-IDF: detects duplicates missed by token overlap
* Audit service: exact duplicate detection still works
* Audit service: performance with 200 insights

## Validation

* `./mvnw test` — all backend tests pass
* Manual: create two insights with different titles but similar content,
  verify they are detected as duplicates
