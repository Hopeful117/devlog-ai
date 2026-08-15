# Story 0070 — Missing Projection Refresh Batch Freshness — Implementation Plan

## Approach

Add a batch freshness check action to `MaintenanceRemediationService` that
triggers freshness verification for all sources and updates the projection.

## Steps

### 1. Extend MaintenanceRemediationService Interface

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationService.java`

Add method:

```java
MaintenanceFindingResponse refreshMissingProjection(UUID projectId, UUID findingId, UUID actedBy, String comment);
```

### 2. Implement refreshMissingProjection()

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationServiceImpl.java`

- Inject `ProjectFreshnessService`, `SourceService`
- Get all active sources for project
- Run `ProjectFreshnessService.check()` for each source
- Update finding status to RESOLVED
- Record action in finding history

### 3. Add Refresh Endpoint

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/controller/MaintenanceFindingController.java`

```java
@PostMapping("/{findingId}/actions/refresh-missing-projection")
public ResponseEntity<MaintenanceFindingResponse> refreshMissingProjection(
        @PathVariable UUID projectId,
        @PathVariable UUID findingId,
        @Valid @RequestBody MaintenanceFindingActionRequest request
) {
    return ResponseEntity.ok(remediationService.refreshMissingProjection(projectId, findingId, request.actedBy(), request.comment()));
}
```

### 4. Add refreshMissingProjection() to Frontend Service

**File**: `frontend/src/app/features/context-maintenance/maintenance-finding.service.ts`

```typescript
refreshMissingProjection(projectId: string, findingId: string, request: MaintenanceFindingActionRequest): Observable<MaintenanceFindingResponse> {
  return this.http.post<MaintenanceFindingResponse>(
    `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/refresh-missing-projection`,
    request,
  );
}
```

### 5. Add Backend Tests

**File**: `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationServiceImplTest.java`

Test cases:
- refreshMissingProjection triggers batch freshness check
- refreshMissingProjection updates finding status to RESOLVED
- refreshMissingProjection handles source errors gracefully

## Verification

```bash
# Backend tests
cd backend && ./mvnw test -Dtest="MaintenanceRemediationServiceImplTest"

# Frontend lint
cd frontend && npm run lint && npm run format:check
```
