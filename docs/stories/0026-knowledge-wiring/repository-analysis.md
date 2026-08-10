# Repository Analysis — Story 0026

## Story

Story 0026 — Knowledge Wiring: Add entity-centric convenience methods to `KnowledgeRelationService` for cross-entity navigation.

## Current State

### KnowledgeRelationService (existing)

| Method | Description |
|--------|-------------|
| `create(CreateKnowledgeRelationRequest)` | Creates a relation with self-ref validation |
| `getById(UUID)` | Get single relation by ID |
| `getByProject(UUID)` | Get all relations for a project |
| `getBySource(EntityType, UUID)` | Get relations where entity is source |
| `getByTarget(EntityType, UUID)` | Get relations where entity is target |
| `delete(UUID)` | Delete a relation |

### KnowledgeRelationRepository (existing)

3 query methods:
- `findByProjectIdOrderByCreatedAtDesc`
- `findBySourceEntityTypeAndSourceEntityId`
- `findByTargetEntityTypeAndTargetEntityId`

### KnowledgeRelationController (existing)

6 endpoints:
- `POST /api/v1/knowledge-relations`
- `GET /api/v1/knowledge-relations/{id}`
- `GET /api/v1/knowledge-relations/project/{projectId}`
- `GET /api/v1/knowledge-relations/source/{entityType}/{entityId}`
- `GET /api/v1/knowledge-relations/target/{entityType}/{entityId}`
- `DELETE /api/v1/knowledge-relations/{id}`

### EntityType (4 values)

- `CHALLENGE` → Challenge entity
- `DECISION` → Decision entity
- `ENGINEERING_EVENT` → EngineeringEvent entity
- `INSIGHT` → Insight entity

### KnowledgeRelationType (6 values)

- `RESOLVES`, `CAUSED_BY`, `RELATES_TO`, `DERIVED_FROM`, `ADDRESSES`, `INFORMED_BY`

## Problem

The current API requires callers to:
1. Know the `EntityType` enum values
2. Use generic `getBySource(EntityType, UUID)` instead of domain-specific methods
3. Construct `CreateKnowledgeRelationRequest` manually with all fields

This creates friction for:
- UI features wanting "related items" on entity pages
- API consumers unfamiliar with the knowledge model
- Future controller additions needing entity-specific endpoints

## Recommendation

### Option A: Service-Layer Convenience Methods (Recommended)

Add 4 convenience methods to `KnowledgeRelationService`:
- `getByChallenge(UUID)` → delegates to `getBySource(EntityType.CHALLENGE, id)`
- `getByDecision(UUID)` → delegates to `getBySource(EntityType.DECISION, id)`
- `getByEngineeringEvent(UUID)` → delegates to `getBySource(EntityType.ENGINEERING_EVENT, id)`
- `getByInsight(UUID)` → delegates to `getBySource(EntityType.INSIGHT, id)`

**Pros**: Minimal change, DRY (delegates to existing methods), no schema changes
**Cons**: Only adds service-layer convenience, no new API endpoints

### Option B: Controller Endpoints + Service Methods

Same as Option A, plus new controller endpoints:
- `GET /api/v1/knowledge-relations/challenge/{challengeId}`
- `GET /api/v1/knowledge-relations/decision/{decisionId}`
- etc.

**Pros**: Full API surface for entity-centric queries
**Cons**: More files changed, controller bloat for simple delegation

### Option C: Validation Enhancement

Add relation-type validation to `create()` method (e.g., forbid certain source/target combinations per relation type).

**Pros**: Stronger data integrity
**Cons**: May be premature without clear business rules

## Scope Recommendation

**Option A** — Service-layer convenience methods only. Controller additions can follow in Story 0027 if needed.

## Risks

- **Low**: New methods are pure delegation — no new logic to test beyond delegation verification
- **Low**: No schema changes required
- **None**: Existing tests unaffected

## Files Affected

| File | Change |
|------|--------|
| `KnowledgeRelationService.java` | +4 method signatures |
| `KnowledgeRelationServiceImpl.java` | +4 method implementations |
| `KnowledgeRelationServiceTest.java` | +4 tests |

## Migration

None. Uses existing `knowledge_relations` table (V35).
