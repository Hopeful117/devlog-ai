# Story 0063 — Integrate Agent Assessments Into Maintenance Lifecycle — Implementation Plan

## Approach

Extend the existing API response and UI to include assessments as additional
context without altering the remediation workflow.

## Steps

### 1. Backend — Extend API Response

**File**: `backend/.../dto/response/MaintenanceFindingResponse.java`

Add `List<MaintenanceAssessmentResponse> assessments` field to the record.
Add null-safe constructor normalization (`assessments = assessments == null ? List.of() : List.copyOf(assessments)`).

### 2. Backend — Add Repository Query

**File**: `backend/.../repository/MaintenanceAssessmentRepository.java`

Add `findByFindingIdInOrderByCreatedAtDescIdDesc(Collection<UUID> findingIds)` to batch-load assessments.

### 3. Backend — Enrich Findings in Service

**File**: `backend/.../service/MaintenanceFindingServiceImpl.java`

Update `getByProject()` to:
1. Fetch all findings for the project
2. Batch-load assessments via `findByFindingIdIn()`
3. Group assessments by finding ID
4. Construct enriched `MaintenanceFindingResponse` with assessments

### 4. Frontend — Add Assessment Model

**File**: `frontend/src/app/features/context-maintenance/maintenance-finding.models.ts`

Add `MaintenanceAssessment` interface matching `MaintenanceAssessmentResponse`.
Add `assessments: MaintenanceAssessment[]` to `MaintenanceFinding`.

### 5. Frontend — Display Assessments

**File**: `frontend/src/app/features/context-maintenance/project-maintenance-section.html`

Add assessment display block under each finding:
- Classification badge
- Confidence badge
- Recommended action badge
- Rationale text
- Supporting signals

### 6. Frontend — Component Helpers

**File**: `frontend/src/app/features/context-maintenance/project-maintenance-section.ts`

Add helper methods for formatting:
- `classificationLabel(classification)`
- `confidenceLabel(confidence)`
- `assessmentActionLabel(action)`

### 7. Frontend — Styles

**File**: `frontend/src/app/features/context-maintenance/project-maintenance-section.scss`

Add `.maintenance-assessments`, `.maintenance-assessment`, and child classes
with distinct visual treatment (purple accent border to distinguish from findings).

### 8. Tests

Update all test files that construct `MaintenanceFindingResponse` to include
the assessments parameter.

## Verification

```bash
cd backend && ./mvnw test -Dtest="MaintenanceFindingServiceTest,MaintenanceFindingControllerWebMvcTest,MaintenanceAssessmentServiceTest,MaintenanceAssessmentControllerWebMvcTest,MaintenanceEvaluationServiceTest"
cd frontend && npx ng test --watch=false
```
