# Story 0061 — Duplicate Ambiguity Resolution Agent — Implementation Plan

## Overview

Implement the first AI-assisted reasoning domain for the Context Maintenance
Agent by evaluating ambiguous duplicate and overlap findings to distinguish
genuine duplication from legitimate enrichment.

## Implementation Strategy

### Phase 1: Core Agent Service

Create `DuplicateAmbiguityResolutionAgent` component:

1. **Input**: Finding issue type + `InsightDuplicateClusterResponse`
2. **Processing**: Rule-based evaluation of cluster metadata
3. **Output**: `Optional<AgentAssessmentResult>` with classification, confidence, rationale, recommended action

### Phase 2: Integration

Integrate agent into `MaintenanceEvaluationServiceImpl.evaluate()`:

1. After each duplicate finding is created, call the agent
2. If agent produces a non-low-confidence assessment, persist it via `MaintenanceAssessmentService`
3. Wrap agent call in try-catch to prevent agent failures from blocking evaluation

### Phase 3: Tests

1. Unit tests for `DuplicateAmbiguityResolutionAgent` (10 tests)
2. Verify existing `MaintenanceEvaluationServiceTest` still passes

## Agent Evaluation Logic

### Semantic Duplicate Clusters

| Condition | Classification | Confidence | Action |
|-----------|---------------|------------|--------|
| Same family (sourceType) | LIKELY_DUPLICATE | HIGH | RESOLVE |
| Different families | UNCERTAIN | MEDIUM | ESCALATE |

### Richer Successor Clusters

| Condition | Classification | Confidence | Action |
|-----------|---------------|------------|--------|
| Richness delta >= 2 or provenance advantage | LIKELY_ENRICHMENT | HIGH | RESOLVE |
| Marginal difference | UNCERTAIN | MEDIUM | ESCALATE |

### Review Required Clusters

| Condition | Classification | Confidence | Action |
|-----------|---------------|------------|--------|
| Always | UNCERTAIN | MEDIUM | ESCALATE |

### Low-Confidence Suppression

Assessments with `LOW` or `VERY_LOW` confidence are suppressed (not persisted).

## Files to Create

| File | Purpose |
|------|---------|
| `backend/src/main/java/.../agent/DuplicateAmbiguityResolutionAgent.java` | Core agent service |
| `backend/src/test/java/.../agent/DuplicateAmbiguityResolutionAgentTest.java` | Unit tests |

## Files to Modify

| File | Change |
|------|--------|
| `backend/src/main/java/.../service/MaintenanceEvaluationServiceImpl.java` | Add agent integration |

## Validation

```bash
cd backend && ./mvnw test -Dtest="DuplicateAmbiguityResolutionAgentTest,MaintenanceEvaluationServiceTest" test
```

Expected: All tests pass (22 tests).
