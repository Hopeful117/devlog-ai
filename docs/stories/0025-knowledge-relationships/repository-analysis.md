# Repository Analysis — Story 0025 Knowledge Relationships

## Story Summary

Introduce a `KnowledgeRelation` entity to create directed relationships between knowledge
entities (Challenge, Decision, EngineeringEvent, Insight), enabling traceable causal and
associative links across the knowledge model.

## Current State

### Knowledge Entities

| Entity | Package | Table | Relationships |
|--------|---------|-------|---------------|
| `Challenge` | `challenge/` | `challenges` | Project only |
| `Decision` | `decision/` | `decisions` | Project only |
| `EngineeringEvent` | `engineeringevent/` | `engineering_events` | Project, Analysis, Proposal, Validation, Source |
| `Insight` | `insight/` | `insights` | Project, Analysis, Proposal, Validation |

### Gap

All knowledge entities are isolated — they share a `project_id` but have no direct links
between them. The knowledge model doc states:

> "Structured Knowledge provides a human-readable representation of the project's evolution."

But without relationships, the system cannot answer:
- "Which Event resolved which Challenge?"
- "Which Decision caused which Event?"
- "Which Insights relate to which Decision?"

### Design Options

**Option A: Polymorphic FK (source_type + source_id + target_type + target_id)**
- Flexible — any entity can relate to any other entity
- No schema changes when adding new entity types
- Requires manual ID resolution (no JPA `@ManyToOne`)
-查询 needs dynamic type resolution

**Option B: Dedicated join tables per relationship pair**
- Type-safe JPA relationships
- Cleaner queries
- Schema grows exponentially with entity types

**Option C: Single relation table with typed enum + generic UUID refs**
- Middle ground — typed relationships with flexible entity references
- Enum governs allowed relationship types
- UUID references with discriminator column

### Recommendation: Option A (Polymorphic FK)

For V1, a single `knowledge_relations` table with:
- `source_entity_type` (CHALLENGE, DECISION, ENGINEERING_EVENT, INSIGHT)
- `source_entity_id` (UUID)
- `target_entity_type` (same enum)
- `target_entity_id` (UUID)
- `relation_type` (enum: RESOLVES, CAUSED_BY, RELATES_TO, DERIVED_FROM, etc.)
- `project_id` (for scoping and cascade)
- `description` (optional human-readable explanation)

This follows the pattern established by JPA polymorphic associations in other projects and
avoids schema explosion.

## Affected Modules

- New package: `knowledge/relation/` (entity, repository, service, controller, dto, mapper)
- New test package: `knowledge/relation/`
- New migration: `V35__create_knowledge_relations_table.sql`

## Risks

- **Medium**: Polymorphic FK means no JPA `@ManyToOne` to concrete types — manual resolution needed
- **Low**: No changes to existing entities
- **Info**: Relation types are an enum — extensible but requires migration for new types

## Constraints

- Must not modify existing entity tables
- Must be project-scoped (all relations belong to one project)
- Must support cascade delete when a project is deleted
- Must prevent self-relation (source ≠ target)
