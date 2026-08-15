# Story 0072 — Trusted Knowledge Deduplication Service — Implementation Plan

## Approach

Create the missing resolution infrastructure for duplicate knowledge findings
by adding status to Insight entity, creating deduplication service, and
implementing merge operations.

## Steps

### 1. Add Status Field to Insight Entity

**File**: `backend/src/main/java/com/hopeful117/devlogai/insight/entity/Insight.java`

Add enum and field:

```java
public enum InsightStatus {
    ACTIVE, ARCHIVED, SUPERSEDED
}

@Enumerated(EnumType.STRING)
@Column(nullable = false)
private InsightStatus status = InsightStatus.ACTIVE;
```

### 2. Add Archive/Supersede Methods to InsightService

**File**: `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightService.java`

Add methods:

```java
public Insight archiveInsight(UUID insightId) {
    Insight insight = repository.findById(insightId)
        .orElseThrow(() -> new EntityNotFoundException("Insight", insightId));
    insight.setStatus(InsightStatus.ARCHIVED);
    return repository.save(insight);
}

public Insight supersedeInsight(UUID insightId, UUID canonicalInsightId) {
    Insight insight = repository.findById(insightId)
        .orElseThrow(() -> new EntityNotFoundException("Insight", insightId));
    Insight canonical = repository.findById(canonicalInsightId)
        .orElseThrow(() -> new EntityNotFoundException("Insight", canonicalInsightId));
    
    // Transfer knowledge relations
    transferKnowledgeRelations(insight, canonical);
    
    insight.setStatus(InsightStatus.SUPERSEDED);
    return repository.save(insight);
}
```

### 3. Update InsightRepository Queries

**File**: `backend/src/main/java/com/hopeful117/devlogai/insight/repository/InsightRepository.java`

Update queries to exclude ARCHIVED/SUPERSEDED by default:

```java
List<Insight> findByProjectIdAndStatus(UUID projectId, InsightStatus status);
```

### 4. Create KnowledgeDeduplicationService Interface

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/KnowledgeDeduplicationService.java`

```java
public interface KnowledgeDeduplicationService {
    MaintenanceFindingResponse mergeExactDuplicate(UUID projectId, UUID findingId, UUID actedBy, String comment);
    MaintenanceFindingResponse resolveSemanticDuplicate(UUID projectId, UUID findingId, UUID actedBy, String comment);
}
```

### 5. Implement KnowledgeDeduplicationServiceImpl

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/KnowledgeDeduplicationServiceImpl.java`

- Inject `InsightService`, `MaintenanceFindingService`
- Implement `mergeExactDuplicate()`:
  1. Parse finding details to get cluster data
  2. Identify canonical insight (newest)
  3. Archive all other insights in cluster
  4. Transfer knowledge relations to canonical
  5. Update finding status to RESOLVED

- Implement `resolveSemanticDuplicate()`:
  1. Parse finding details to get cluster data
  2. Apply recommended action from assessment
  3. Archive or supersede as appropriate
  4. Transfer knowledge relations
  5. Update finding status to RESOLVED

### 6. Add Deduplication Endpoints

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/controller/MaintenanceFindingController.java`

```java
@PostMapping("/{findingId}/actions/merge-duplicate")
public ResponseEntity<MaintenanceFindingResponse> mergeDuplicate(
        @PathVariable UUID projectId,
        @PathVariable UUID findingId,
        @Valid @RequestBody MaintenanceFindingActionRequest request
) {
    return ResponseEntity.ok(deduplicationService.mergeExactDuplicate(projectId, findingId, request.actedBy(), request.comment()));
}

@PostMapping("/{findingId}/actions/resolve-semantic-duplicate")
public ResponseEntity<MaintenanceFindingResponse> resolveSemanticDuplicate(
        @PathVariable UUID projectId,
        @PathVariable findingId,
        @Valid @RequestBody MaintenanceFindingActionRequest request
) {
    return ResponseEntity.ok(deduplicationService.resolveSemanticDuplicate(projectId, findingId, request.actedBy(), request.comment()));
}
```

### 7. Add Frontend Service Methods

**File**: `frontend/src/app/features/context-maintenance/maintenance-finding.service.ts`

```typescript
mergeDuplicate(projectId: string, findingId: string, request: MaintenanceFindingActionRequest): Observable<MaintenanceFindingResponse> {
  return this.http.post<MaintenanceFindingResponse>(
    `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/merge-duplicate`,
    request,
  );
}

resolveSemanticDuplicate(projectId: string, findingId: string, request: MaintenanceFindingActionRequest): Observable<MaintenanceFindingResponse> {
  return this.http.post<MaintenanceFindingResponse>(
    `${this.projectsUrl}/${encodeURIComponent(projectId)}/maintenance-findings/${encodeURIComponent(findingId)}/actions/resolve-semantic-duplicate`,
    request,
  );
}
```

### 8. Add Backend Tests

**File**: `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/KnowledgeDeduplicationServiceImplTest.java`

Test cases:
- mergeExactDuplicate keeps newest insight as canonical
- mergeExactDuplicate archives other insights
- mergeExactDuplicate transfers knowledge relations
- mergeExactDuplicate updates finding status to RESOLVED
- resolveSemanticDuplicate applies recommended action
- resolveSemanticDuplicate handles REVIEW_MANUALLY case
- resolveSemanticDuplicate handles merge failures

## Verification

```bash
# Backend tests
cd backend && ./mvnw test -Dtest="KnowledgeDeduplicationServiceImplTest"

# Frontend lint
cd frontend && npm run lint && npm run format:check
```
