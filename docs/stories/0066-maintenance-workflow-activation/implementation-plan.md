# Story 0066 — Maintenance Workflow Activation — Implementation Plan

## Approach

Add the missing UI trigger for maintenance evaluation by extending the existing
frontend service and component with evaluation capability.

## Steps

### 1. Add Evaluation Response Model

**File**: `frontend/src/app/features/context-maintenance/maintenance-finding.models.ts`

Add `MaintenanceEvaluationResponse` interface matching backend response:

```typescript
export interface MaintenanceEvaluationResponse {
  readonly version: string;
  readonly projectId: string;
  readonly createdCount: number;
  readonly skippedCount: number;
  readonly createdFindings: readonly MaintenanceFinding[];
}
```

### 2. Add evaluate() Method to Service

**File**: `frontend/src/app/features/context-maintenance/maintenance-finding.service.ts`

Add method calling `POST /evaluations`:

```typescript
evaluate(projectId: string): Observable<MaintenanceEvaluationResponse> {
  return this.http.post<MaintenanceEvaluationResponse>(
    `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/evaluations`,
    {},
  );
}
```

### 3. Add Evaluation State to Component

**File**: `frontend/src/app/features/context-maintenance/project-maintenance-section.ts`

Add state properties:

* `evaluating: boolean` — tracks loading state
* `evaluationError: string` — stores error message
* `lastEvaluationTime: string | null` — stores timestamp of last evaluation

Add `evaluate()` method with loading/error handling and findings refresh.

### 4. Add Evaluation Button to Template

**File**: `frontend/src/app/features/context-maintenance/project-maintenance-section.html`

Add toolbar section at top of card:

* "Run evaluation" button (disabled during evaluation)
* Loading text ("Evaluating…")
* Last evaluation timestamp display
* Error message display

### 5. Add Toolbar Styles

**File**: `frontend/src/app/features/context-maintenance/project-maintenance-section.scss`

Add styles for `.maintenance-toolbar` layout.

## Verification

```bash
# Verify frontend builds
cd frontend && npm run build

# Verify backend evaluation works
curl -X POST http://localhost:18080/api/v1/projects/{projectId}/maintenance-findings/evaluations
```
