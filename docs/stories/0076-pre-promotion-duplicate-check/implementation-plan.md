# Story 0076 — Pre-Promotion Duplicate Check — Implementation Plan

## Phase 1: Similarity Service Integration

### 1.1 Dependency
* Inject `InsightSimilarityService` into `InsightPromotionService`

### 1.2 Configuration
* Add `devlog.insight.promotion.similarity` properties:
  * `blockThreshold` (default: 0.85) — block creation
  * `warnThreshold` (default: 0.65) — create + finding
  * `maxCandidates` (default: 50) — limit similarity search scope

## Phase 2: Pre-Promotion Check

### 2.1 Promotion Flow Update
* In `InsightPromotionService.promote()`:
  * Before `insightRepository.save()`:
    1. Load existing ACTIVE insights for the project (limit to N most recent)
    2. Compute TF-IDF vectors for candidate insights
    3. Compute cosine similarity between new insight and each candidate
    4. Find max similarity score and matching insight
  * If maxSimilarity > blockThreshold:
    * Throw `ConflictException` with message:
      "Insight is too similar to existing insight '{title}' (similarity: {score})"
    * Do NOT create the insight
  * If maxSimilarity > warnThreshold:
    * Create the insight normally
    * Also create a `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW` maintenance finding
      referencing both insights
  * If maxSimilarity <= warnThreshold:
    * Create the insight normally (no action)

### 2.2 Response Enrichment
* Add similarity metadata to `ProjectUnderstandingResponse`:
  * `similarInsightId` (if similarity > warnThreshold)
  * `similarityScore`
  * `similarityAction` (CREATED / CREATED_WITH_WARNING / BLOCKED)

## Phase 3: Tests

### 3.1 Unit Tests
* Block: new insight with similarity 0.9 → ConflictException
* Warn: new insight with similarity 0.75 → created + finding
* Allow: new insight with similarity 0.5 → created normally
* Edge: no existing insights → always allow
* Edge: project with 200+ insights → performance acceptable

### 3.2 Integration Tests
* Full promotion flow with duplicate check
* Finding created for warned insights
* Block message includes similar insight title and score

## Validation

* `./mvnw test` — all backend tests pass
* Manual: promote a proposal that duplicates an existing insight → blocked
