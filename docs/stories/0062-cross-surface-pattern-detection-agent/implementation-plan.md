# Story 0062 — Cross-Surface Pattern Detection Agent — Implementation Plan

## Overview

Implement cross-surface pattern detection for the Context Maintenance Agent
so correlated staleness and degradation signals across multiple context
surfaces can be identified and surfaced as a coherent maintenance assessment.

## Implementation Strategy

### Phase 1: Core Agent Service

Create `CrossSurfacePatternDetectionAgent` component:

1. **Input**: List of all active `MaintenanceFinding` entities
2. **Processing**: Rule-based evaluation of cross-surface patterns
3. **Output**: `Optional<AgentAssessmentResult>` with classification, confidence, rationale, recommended action, contributing finding IDs

### Phase 2: Integration

Integrate agent into `MaintenanceEvaluationServiceImpl.evaluate()`:

1. Called after all deterministic findings are created
2. Uses combined list of current findings and newly created findings
3. Assessment is linked to the first contributing finding
4. Wrapped in try-catch to prevent agent failures from blocking evaluation

### Phase 3: Tests

1. Unit tests for `CrossSurfacePatternDetectionAgent` (10 tests)
2. Verify existing `MaintenanceEvaluationServiceTest` still passes

## Pattern Detection Logic

### Correlated Staleness

| Surfaces | Confidence | Action |
|----------|------------|--------|
| 3+ surfaces | HIGH | ESCALATE |
| 2 surfaces | MEDIUM | ESCALATE |
| 1 surface | (suppressed) | — |

### Correlated Duplicate Debt

| Findings | Confidence | Action |
|----------|------------|--------|
| 3+ findings | HIGH | ESCALATE |
| 2 findings | MEDIUM | ESCALATE |
| 1 finding | (suppressed) | — |

### Priority

Staleness patterns take precedence over duplicate debt patterns.

## Files to Create

| File | Purpose |
|------|---------|
| `backend/src/main/java/.../agent/CrossSurfacePatternDetectionAgent.java` | Core agent service |
| `backend/src/test/java/.../agent/CrossSurfacePatternDetectionAgentTest.java` | Unit tests |

## Files to Modify

| File | Change |
|------|--------|
| `backend/src/main/java/.../service/MaintenanceEvaluationServiceImpl.java` | Add agent integration |

## Validation

```bash
cd backend && ./mvnw test -Dtest="CrossSurfacePatternDetectionAgentTest,MaintenanceEvaluationServiceTest" test
```

Expected: All tests pass (22 tests).
