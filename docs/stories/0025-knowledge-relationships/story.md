# Story 0025 — Knowledge Relationships

## Status

✅ Completed

## Objective

Introduce a `KnowledgeRelation` entity to create directed, typed relationships between knowledge
entities, enabling traceable causal and associative links across the knowledge model.

## Motivation

Phase 3 of the DevLog roadmap requires a connected knowledge model. Currently, Challenge,
Decision, EngineeringEvent, and Insight exist as isolated entities sharing only a project
reference. Without relationships, the system cannot answer questions like "which Event resolved
which Challenge?" or "which Decision caused which Event?".

## Acceptance Criteria

- AC-1: `KnowledgeRelationType` enum with values: `RESOLVES`, `CAUSED_BY`, `RELATES_TO`,
  `DERIVED_FROM`, `ADDRESSES`, `INFORMED_BY`.
- AC-2: `EntityType` enum with values: `CHALLENGE`, `DECISION`, `ENGINEERING_EVENT`, `INSIGHT`.
- AC-3: `KnowledgeRelation` JPA entity with fields: `id`, `project`, `sourceEntityType`,
  `sourceEntityId`, `targetEntityType`, `targetEntityId`, `relationType`, `description`,
  `createdAt`.
- AC-4: Flyway migration V35 creating the `knowledge_relations` table with project FK,
  check constraints, and indexes.
- AC-5: `KnowledgeRelationRepository` Spring Data JPA repository.
- AC-6: `KnowledgeRelationService` / `KnowledgeRelationServiceImpl` with `create`, `getById`,
  `getByProject`, `getBySource`, `getByTarget`, and `delete` operations.
- AC-7: `KnowledgeRelationController` REST API at `/api/v1/knowledge-relations`.
- AC-8: Request/Response DTOs with Jakarta validation.
- AC-9: MapStruct mapper for entity ↔ DTO conversion.
- AC-10: Unit tests for service and controller.
- AC-11: SonarQube Quality Gate passes with 0 new violations.
