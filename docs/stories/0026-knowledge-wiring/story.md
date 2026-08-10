# Story 0026 — Knowledge Wiring

## Story ID

0026

## Title

Wire Knowledge Model entities for cross-entity navigation and contextual discovery

## Status

Completed

## Priority

Medium

## Date

2026-08-10

---

## User Story

As Kiko exploring Engineering Story context,
I want to discover relationships between Challenges, Decisions, Engineering Events, and Insights,
So that I can navigate the knowledge graph and verify source-code traceability.

---

## Context

The Knowledge Model Phase 3 has delivered:
- **Story 0024**: Challenge entity (CRUD API, Flyway V34)
- **Story 0025**: KnowledgeRelation polymorphic FK entity (CRUD API, Flyway V35)

The `KnowledgeRelation` table supports 4 entity types:
- `CHALLENGE` → Challenge entity
- `DECISION` → Decision entity  
- `ENGINEERING_EVENT` → EngineeringEvent entity
- `INSIGHT` → Insight entity

And 6 relation types:
- `RESOLVES`
- `CAUSED_BY`
- `RELATES_TO`
- `DERIVED_FROM`
- `ADDRESSES`
- `INFORMED_BY`

However, there is no wiring layer to:
1. Create relations with validation and convenience methods
2. Query relations in meaningful ways (entity-centric navigation)
3. Express domain-specific workflows (e.g., "What decisions led to this engineering event?")

---

## Problem Statement

The current `KnowledgeRelationService` only provides generic CRUD operations. Users must:
- Manually construct `CreateKnowledgeRelationRequest` with UUID IDs
- Call low-level queries like `getBySource()` with `EntityType` enum
- Translate between domain concepts and raw relation types

This creates friction for:
- UI features wanting to show "related items" on entity pages
- Engineering Story workflows needing to establish causality
- API consumers unfamiliar with the knowledge model

---

## Scope

### In Scope
1. Add relation-creation convenience methods to `KnowledgeRelationService`
2. Add entity-centric query methods (e.g., "get Challenges related to Decision X")
3. Add validation for allowed source/target combinations per relation type
4. Create unit tests for new service methods

### Out of Scope
1. Controller/endpoint additions (Story 0027)
2. Bulk import or migration of historical relations
3. GraphQL or advanced querying (future enhancement)

---

## Impact

- **Files Changed**: 3-5 Java files (service, request DTO, tests)
- **Migration**: None (uses existing V35 table)
- **Tests**: 5-8 new tests

---

## Acceptance Criteria

1. Given a Challenge, when I ask for related Decisions, then I receive all Decisions connected via any relation type
2. Given a Decision, when I create a `RESOLVES` relation to a Challenge, then the relation is saved with correct metadata
3. Given a relation type that forbids bidirectional connections, when I attempt to create a self-relating entity, then a validation error is thrown
4. All existing tests continue to pass
5. New tests achieve 100% coverage of added service methods