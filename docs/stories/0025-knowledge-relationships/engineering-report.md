# Engineering Report — Story 0025 Knowledge Relationships

## Status

✅ Completed

## Objective

Introduce a `KnowledgeRelation` entity to create directed, typed relationships between knowledge
entities, enabling traceable causal and associative links across the knowledge model.

## Acceptance Criteria

| AC | Description | Status |
|----|-------------|--------|
| AC-1 | `KnowledgeRelationType` enum | ✅ |
| AC-2 | `EntityType` enum | ✅ |
| AC-3 | `KnowledgeRelation` JPA entity | ✅ |
| AC-4 | Flyway migration V35 | ✅ |
| AC-5 | `KnowledgeRelationRepository` | ✅ |
| AC-6 | `KnowledgeRelationService` with CRUD + query operations | ✅ |
| AC-7 | `KnowledgeRelationController` REST API | ✅ |
| AC-8 | Request/Response DTOs with validation | ✅ |
| AC-9 | MapStruct mapper | ✅ |
| AC-10 | Unit tests (service + controller) | ✅ |
| AC-11 | SonarQube Quality Gate passes | ✅ |

## Implementation

### What was built

- `EntityType` enum — CHALLENGE, DECISION, ENGINEERING_EVENT, INSIGHT
- `KnowledgeRelationType` enum — RESOLVES, CAUSED_BY, RELATES_TO, DERIVED_FROM, ADDRESSES, INFORMED_BY
- `KnowledgeRelation` entity — polymorphic FK pattern with source/target type+id
- `KnowledgeRelationRepository` — queries for project, source, and target
- `KnowledgeRelationService` / `KnowledgeRelationServiceImpl` — full CRUD with self-ref validation
- `KnowledgeRelationController` — REST API with 6 endpoints
- Flyway `V35` — table, CHECK constraints, composite indexes

### What was modified

- `ProjectDeletionPostgresIntegrationTest` — migration count 34 → 35

### Verification

- **505 tests** passing (0 failures, 0 errors)
- **SonarQube Quality Gate**: PASSED
- **0 new violations**

### Decisions

- **Polymorphic FK over dedicated join tables**: Single `knowledge_relations` table avoids
  schema explosion. Trade-off: no JPA `@ManyToOne` to concrete types, but appropriate for V1.
- **DB + application self-reference prevention**: CHECK constraint in migration plus
  validation in service layer.
- **Immutable timestamps only**: No `updatedAt` — relations are created once, not edited.
  If editing is needed later, it can be added.

## Residual Risks

- **Low**: Individual entity deletion (Challenge, Decision, etc.) does not cascade-delete
  related KnowledgeRelations — application-level cleanup needed in future
- **Info**: Relation types are an enum — adding new types requires migration

## Next Steps

- Story 0026: Wire existing entities via relationships (demo/test data)
- Future: Cascade cleanup when entities are deleted
