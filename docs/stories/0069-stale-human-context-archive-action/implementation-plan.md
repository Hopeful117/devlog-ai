# Story 0069 — Stale Human Context Archive Action — Implementation Plan

## Approach

Add an archive action to `MaintenanceRemediationService` that extracts the
input ID from the finding details and calls the existing archive service.

## Steps

### 1. Extend MaintenanceRemediationService Interface

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationService.java`

Add method:

```java
MaintenanceFindingResponse archiveStaleHumanContext(UUID projectId, UUID findingId, UUID actedBy, String comment);
```

### 2. Implement archiveStaleHumanContext()

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationServiceImpl.java`

- Inject `ProjectHumanContextInputService`
- Parse `finding.getDetails()` to extract input ID (UUID pattern)
- Call `projectHumanContextInputService.archive(projectId, inputId)`
- Update finding status to RESOLVED
- Record action in finding history

### 3. Add Archive Endpoint

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/controller/MaintenanceFindingController.java`

```java
@PostMapping("/{findingId}/actions/archive-context-input")
public ResponseEntity<MaintenanceFindingResponse> archiveStaleHumanContext(
        @PathVariable UUID projectId,
        @PathVariable UUID findingId,
        @Valid @RequestBody MaintenanceFindingActionRequest request
) {
    return ResponseEntity.ok(remediationService.archiveStaleHumanContext(projectId, findingId, request.actedBy(), request.comment()));
}
```

### 4. Add archiveStaleHumanContext() to Frontend Service

**File**: `frontend/src/app/features/context-maintenance/maintenance-finding.service.ts`

```typescript
archiveStaleHumanContext(projectId: string, findingId: string, request: MaintenanceFindingActionRequest): Observable<MaintenanceFindingResponse> {
  return this.http.post<MaintenanceFindingResponse>(
    `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/archive-context-input`,
    request,
  );
}
```

### 5. Add Backend Tests

**File**: `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceRemediationServiceImplTest.java`

Test cases:
- archiveStaleHumanContext extracts input ID from details
- archiveStaleHumanContext calls archive service
- archiveStaleHumanContext updates finding status to RESOLVED
- archiveStaleHumanContext handles invalid input ID
- archiveStaleHumanContext handles archive service failure

## Verification

```bash
# Backend tests
cd backend && ./mvnw test -Dtest="MaintenanceRemediationServiceImplTest"

# Frontend lint
cd frontend && npm run lint && npm run format:check
```
