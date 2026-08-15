# Story 0074 — Fix Overlap Resolution Recurrence — Implementation Plan

## Phase 1: Fix Audit Input Filtering

### 1.1 Repository
* Add `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(UUID projectId, Collection<InsightStatus> statuses)` to `InsightRepository`
* Import `InsightStatus` in repository interface

### 1.2 Audit Service
* In `TrustedKnowledgeDuplicateAuditService.audit()`:
  * Replace `insightRepository.findByProjectIdOrderByCreatedAtDescIdDesc(projectId)` with the new status-filtered query
  * Pass `List.of(InsightStatus.ACTIVE)` as the status filter
  * SUPERSEDED and ARCHIVED insights are now excluded from clustering

## Phase 2: Fix Finding Deduplication Guard

### 2.1 Evaluation Service
* In `MaintenanceEvaluationServiceImpl.hasEquivalentActiveFinding()`:
  * Remove the `OPEN`/`ACKNOWLEDGED` filter
  * Instead, check all non-DISMISSED findings (OPEN, ACKNOWLEDGED, RESOLVED)
  * Rationale: a RESOLVED finding with identical details means the condition
    was already addressed; re-creation would be a regression

## Phase 3: Create KnowledgeRelation on Supersede

### 3.1 Insight Service
* Add `KnowledgeRelationService` dependency to `InsightServiceImpl`
* In `supersedeInsight(UUID insightId, UUID canonicalInsightId)`:
  * After setting status to SUPERSEDED, create a `KnowledgeRelation`:
    * `sourceEntityType = INSIGHT`
    * `sourceEntityId = insightId` (superseded)
    * `targetEntityType = INSIGHT`
    * `targetEntityId = canonicalInsightId`
    * `relationType = RESOLVES`
  * Catch and log relation creation failures (non-fatal)

## Phase 4: Tests

### 4.1 Repository
* Test `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` returns only ACTIVE insights
* Test that SUPERSEDED insights are excluded

### 4.2 Audit Service
* Test that audit input excludes SUPERSEDED/ARCHIVED insights
* Test that exact duplicate cluster does not reform after superseding

### 4.3 Evaluation Service
* Test that `hasEquivalentActiveFinding` detects RESOLVED findings
* Test that resolved findings prevent re-creation of identical findings

### 4.4 Insight Service
* Test that `supersedeInsight` creates a RESOLVES KnowledgeRelation
* Test that relation creation failure does not break supersede

## Validation

* `./mvnw test` — all backend tests pass
* `npm run lint && npm run format:check && npx ng test` — frontend clean
* Manual: resolve overlap → re-run evaluation → overlap does not reappear
