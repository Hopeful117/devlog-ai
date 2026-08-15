# Story 0071 — Stale Understanding Chain Refresh — Implementation Plan

## Approach

Add a chained refresh action to `MaintenanceRemediationService` that triggers
a freshness check followed by a project understanding re-analysis.

## Steps

### 1. Extend MaintenanceRemediationService Interface

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationService.java`

Add method:

```java
MaintenanceFindingResponse refreshProjectUnderstanding(UUID projectId, UUID findingId, UUID actedBy, String comment);
```

### 2. Implement refreshProjectUnderstanding()

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationServiceImpl.java`

- Inject `ProjectFreshnessService`, `ProjectUnderstandingService`, `SourceService`
- Step 1: Get all active sources for project
- Step 2: Run `ProjectFreshnessService.check()` for each source
- Step 3: If any freshness check fails, throw exception and abort
- Step 4: Run `ProjectUnderstandingService.execute()` for re-analysis
- Step 5: Update finding status to RESOLVED
- Step 6: Record action in finding history

### 3. Add Refresh Endpoint

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/controller/MaintenanceFindingController.java`

```java
@PostMapping("/{findingId}/actions/refresh-understanding")
public ResponseEntity<MaintenanceFindingResponse> refreshProjectUnderstanding(
        @PathVariable UUID projectId,
        @PathVariable UUID findingId,
        @Valid @RequestBody MaintenanceFindingActionRequest request
) {
    return ResponseEntity.ok(remediationService.refreshProjectUnderstanding(projectId, findingId, request.actedBy(), request.comment()));
}
```

### 4. Add refreshProjectUnderstanding() to Frontend Service

**File**: `frontend/src/app/features/context-maintenance/maintenance-finding.service.ts`

```typescript
refreshProjectUnderstanding(projectId: string, findingId: string, request: MaintenanceFindingActionRequest): Observable<MaintenanceFindingResponse> {
  return this.http.post<MaintenanceFindingResponse>(
    `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/refresh-understanding`,
    request,
  );
}
```

### 5. Add Backend Tests

**File**: `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationServiceImplTest.java`

Test cases:
- refreshProjectUnderstanding triggers freshness check then understanding
- refreshProjectUnderstanding updates finding status to RESOLVED
- refreshProjectUnderstanding aborts if freshness check fails
- refreshProjectUnderstanding handles understanding re-analysis failure

## Verification

```bash
# Backend tests
cd backend && ./mvnw test -Dtest="MaintenanceRemediationServiceImplTest"

# Frontend lint
cd frontend && npm run lint && npm run format:check
```
