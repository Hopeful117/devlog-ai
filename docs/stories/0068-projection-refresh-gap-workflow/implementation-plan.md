# Story 0068 — Projection Refresh Gap Workflow — Implementation Plan

## Approach

Enable workflow support for `PROJECTION_REFRESH_GAP` and create a batch
freshness check action that refreshes all unchecked sources.

## Steps

### 1. Update Backend supportsWorkflow()

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingServiceImpl.java`

Add `PROJECTION_REFRESH_GAP` to manual actions case:

```java
case STALE_HUMAN_CONTEXT_INPUT,
        STALE_PROJECT_UNDERSTANDING,
        MISSING_PROJECTION_REFRESH,
        PROJECTION_REFRESH_GAP,
        TRUSTED_KNOWLEDGE_EXACT_DUPLICATE,
        TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE,
        TRUSTED_KNOWLEDGE_OVERLAP_REVIEW -> true;
```

### 2. Create MaintenanceRemediationService Interface

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationService.java`

```java
public interface MaintenanceRemediationService {
    MaintenanceFindingResponse refreshProjection(UUID projectId, UUID findingId, UUID actedBy, String comment);
}
```

### 3. Implement MaintenanceRemediationServiceImpl

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationServiceImpl.java`

- Inject `ProjectFreshnessService`, `MaintenanceFindingService`
- Implement `refreshProjection()`:
  1. Get all active sources for project
  2. Run `ProjectFreshnessService.check()` for each source
  3. Update finding status to RESOLVED
  4. Record action in finding history

### 4. Add Refresh Endpoint

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/controller/MaintenanceFindingController.java`

```java
@PostMapping("/{findingId}/actions/refresh-projection")
public ResponseEntity<MaintenanceFindingResponse> refreshProjection(
        @PathVariable UUID projectId,
        @PathVariable UUID findingId,
        @Valid @RequestBody MaintenanceFindingActionRequest request
) {
    return ResponseEntity.ok(remediationService.refreshProjection(projectId, findingId, request.actedBy(), request.comment()));
}
```

### 5. Update Frontend supportsWorkflow()

**File**: `frontend/src/app/features/context-maintenance/project-maintenance-section.ts`

Add `PROJECTION_REFRESH_GAP` to `supportsWorkflow()`:

```typescript
finding.issueType === 'PROJECTION_REFRESH_GAP' ||
```

### 6. Add refreshProjection() to Frontend Service

**File**: `frontend/src/app/features/context-maintenance/maintenance-finding.service.ts`

```typescript
refreshProjection(projectId: string, findingId: string, request: MaintenanceFindingActionRequest): Observable<MaintenanceFindingResponse> {
  return this.http.post<MaintenanceFindingResponse>(
    `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/refresh-projection`,
    request,
  );
}
```

### 7. Add Backend Tests

**File**: `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationServiceImplTest.java`

Test cases:
- refreshProjection triggers batch freshness check
- refreshProjection updates finding status to RESOLVED
- refreshProjection records action in history
- refreshProjection handles source errors gracefully

## Verification

```bash
# Backend tests
cd backend && ./mvnw test -Dtest="MaintenanceRemediationServiceImplTest"

# Frontend lint
cd frontend && npm run lint && npm run format:check
```
