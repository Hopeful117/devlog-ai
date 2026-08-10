# Repository Analysis — Story 0027

## Story

Story 0027 — Decision CRUD Completion: Add update and delete operations to the Engineering Decision API.

## Current State

### Decision Entity (existing)

| Field | Type | Constraints |
|-------|------|-------------|
| `id` | UUID | PK, auto-generated |
| `project` | Project | ManyToOne, not null |
| `title` | String | not null |
| `context` | String | max 5000 |
| `choice` | String | max 5000 |
| `rationale` | String | max 5000 |
| `consequences` | String | max 5000, nullable |
| `createdAt` | Instant | auto-generated |
| `updatedAt` | Instant | auto-updated |

### DecisionController (existing)

| Method | Endpoint | Status |
|--------|----------|--------|
| POST | `/api/v1/decisions` | ✅ |
| GET | `/api/v1/decisions/{id}` | ✅ |
| GET | `/api/v1/decisions/project/{projectId}` | ✅ |
| PUT | `/api/v1/decisions/{id}` | ❌ Missing |
| DELETE | `/api/v1/decisions/{id}` | ❌ Missing |

### DecisionService (existing)

| Method | Status |
|--------|--------|
| `create(CreateDecisionRequest)` | ✅ |
| `getById(UUID)` | ✅ |
| `getByProject(UUID)` | ✅ |
| `update(UUID, UpdateDecisionRequest)` | ❌ Missing |
| `delete(UUID)` | ❌ Missing |

### DecisionServiceTest (existing)

5 tests covering create, getById, getByProject, and error cases.

## Recommendation

Follow the same pattern as `ChallengeService` (Story 0024) which already has update/delete.

### Files to Create/Modify

| File | Action |
|------|--------|
| `UpdateDecisionRequest.java` | Create (new DTO) |
| `DecisionService.java` | Add `update()` and `delete()` signatures |
| `DecisionServiceImpl.java` | Add `update()` and `delete()` implementations |
| `DecisionController.java` | Add PUT and DELETE endpoints |
| `DecisionServiceTest.java` | Add tests for update and delete |

## Risks

- **Low**: Standard CRUD pattern, well-established in the codebase
- **Low**: No schema changes required
- **None**: Existing tests unaffected

## Migration

None. Uses existing `decisions` table.
