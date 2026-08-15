# Story 0067 — Maintenance Stale Projection Workflow — Implementation Plan

## Approach

Extend the workflow support to include `STALE_PROJECT_UNDERSTANDING` and
`MISSING_PROJECTION_REFRESH` for the Dismiss action only.

## Steps

### 1. Update Backend supportsWorkflow()

**File**: `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingServiceImpl.java`

Change the manual actions case in `supportsWorkflow()` to include:

```java
case STALE_HUMAN_CONTEXT_INPUT,
        STALE_PROJECT_UNDERSTANDING,
        MISSING_PROJECTION_REFRESH,
        TRUSTED_KNOWLEDGE_EXACT_DUPLICATE,
        TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE,
        TRUSTED_KNOWLEDGE_OVERLAP_REVIEW -> true;
```

### 2. Update Frontend supportsWorkflow()

**File**: `frontend/src/app/features/context-maintenance/project-maintenance-section.ts`

Add the two new types to `supportsWorkflow()`:

```typescript
finding.issueType === 'STALE_PROJECT_UNDERSTANDING' ||
finding.issueType === 'MISSING_PROJECTION_REFRESH' ||
```

### 3. Add Backend Tests

**File**: `backend/src/test/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceFindingServiceImplTest.java`

Add test cases:
- Dismiss STALE_PROJECT_UNDERSTANDING with comment → DISMISSED
- Dismiss MISSING_PROJECTION_REFRESH with comment → DISMISSED
- Dismiss without comment → error

### 4. Add Frontend Tests

**File**: `frontend/src/app/features/context-maintenance/project-maintenance-section.spec.ts`

Add test cases:
- STALE_PROJECT_UNDERSTANDING shows Dismiss button
- MISSING_PROJECTION_REFRESH shows Dismiss button

## Verification

```bash
# Backend tests
cd backend && ./mvnw test -Dtest="MaintenanceFindingServiceImplTest"

# Frontend lint
cd frontend && npm run lint && npm run format:check
```
