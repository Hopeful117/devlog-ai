# Story 0064 — Confidence Thresholds And Assessment Filtering — Implementation Plan

## Approach

Add a configurable confidence threshold that filters low-value assessments
before they reach the persistence layer.

## Steps

### 1. Backend — Configuration Class

**File**: `backend/.../config/MaintenanceAgentProperties.java`

Create `@ConfigurationProperties` class with:
- `assessmentMinimumConfidence` field (default: MEDIUM)
- `isAboveThreshold(confidence)` method for threshold checks

### 2. Backend — Application Properties

**File**: `backend/src/main/resources/application.properties`

Add:
```properties
devlog.context-maintenance.agent.assessment-minimum-confidence=${CONTEXT_MAINTENANCE_AGENT_MIN_CONFIDENCE:MEDIUM}
```

### 3. Backend — Filter Assessments in Service

**File**: `backend/.../service/MaintenanceEvaluationServiceImpl.java`

- Inject `MaintenanceAgentProperties`
- In `evaluateDuplicateFinding()`: check threshold before calling `assessmentService.create()`
- In `evaluateCrossSurfacePatterns()`: check threshold before calling `assessmentService.create()`
- Log suppressed assessments at INFO level for debugging

### 4. Backend — Tests

**File**: `backend/.../config/MaintenanceAgentPropertiesTest.java`

- Test threshold logic for all confidence levels
- Test null confidence handling

**File**: `backend/.../service/MaintenanceEvaluationServiceTest.java`

- Update constructor to include `MaintenanceAgentProperties` mock
- Add test: suppress low-confidence assessments
- Add test: persist high-confidence assessments

## Verification

```bash
cd backend && ./mvnw test -Dtest="MaintenanceAgentPropertiesTest,MaintenanceEvaluationServiceTest,MaintenanceFindingServiceTest,MaintenanceFindingControllerWebMvcTest,MaintenanceAssessmentServiceTest,MaintenanceAssessmentControllerWebMvcTest"
```
