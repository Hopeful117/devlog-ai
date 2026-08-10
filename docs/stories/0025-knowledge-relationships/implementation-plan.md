# Implementation Plan — Story 0025 Knowledge Relationships

## Overview

Implement a `KnowledgeRelation` entity using polymorphic FK pattern to connect knowledge
entities. New package `knowledge/relation/` with full CRUD, Flyway migration, and tests.

## Steps

### Step 1: EntityType Enum

Create `knowledge/relation/entity/EntityType.java` with values: `CHALLENGE`, `DECISION`,
`ENGINEERING_EVENT`, `INSIGHT`.

### Step 2: KnowledgeRelationType Enum

Create `knowledge/relation/entity/KnowledgeRelationType.java` with values: `RESOLVES`,
`CAUSED_BY`, `RELATES_TO`, `DERIVED_FROM`, `ADDRESSES`, `INFORMED_BY`.

### Step 3: KnowledgeRelation Entity

Create `knowledge/relation/entity/KnowledgeRelation.java`:
- `UUID id` (generated)
- `Project project` (ManyToOne LAZY, not null)
- `EntityType sourceEntityType` (Enumerated STRING, not null)
- `UUID sourceEntityId` (not null)
- `EntityType targetEntityType` (Enumerated STRING, not null)
- `UUID targetEntityId` (not null)
- `KnowledgeRelationType relationType` (Enumerated STRING, not null)
- `String description` (TEXT, nullable)
- `Instant createdAt` (CreatedDate)

### Step 4: Flyway Migration V35

Create `V35__create_knowledge_relations_table.sql`:
- `knowledge_relations` table with all columns
- FK to `projects(id)` with CASCADE delete
- CHECK constraints on entity types and relation type
- Composite index on (source_entity_type, source_entity_id)
- Composite index on (target_entity_type, target_entity_id)
- Index on project_id

### Step 5: KnowledgeRelationRepository

Create `knowledge/relation/repository/KnowledgeRelationRepository.java`:
- Extends `JpaRepository<KnowledgeRelation, UUID>`
- `findByProjectIdOrderByCreatedAtDesc(UUID projectId)`
- `findBySourceEntityTypeAndSourceEntityId(EntityType type, UUID id)`
- `findByTargetEntityTypeAndTargetEntityId(EntityType type, UUID id)`

### Step 6: DTOs

Create `knowledge/relation/dto/request/CreateKnowledgeRelationRequest.java`:
- `UUID projectId` (@NotNull)
- `EntityType sourceEntityType` (@NotNull)
- `UUID sourceEntityId` (@NotNull)
- `EntityType targetEntityType` (@NotNull)
- `UUID targetEntityId` (@NotNull)
- `KnowledgeRelationType relationType` (@NotNull)
- `String description` (nullable)

Create `knowledge/relation/dto/response/KnowledgeRelationResponse.java`:
- Java record with all fields including `id`, `projectId`, timestamps

### Step 7: KnowledgeRelationMapper

Create `knowledge/relation/mapper/KnowledgeRelationMapper.java`:
- MapStruct `@Mapper(componentModel = "spring")`
- `toResponse(KnowledgeRelation)` → `KnowledgeRelationResponse`
- `toEntity(CreateKnowledgeRelationRequest)` → `KnowledgeRelation`

### Step 8: KnowledgeRelationService + KnowledgeRelationServiceImpl

Create `knowledge/relation/service/KnowledgeRelationService.java` (interface):
- `create(CreateKnowledgeRelationRequest)` → `KnowledgeRelationResponse`
- `getById(UUID)` → `KnowledgeRelationResponse`
- `getByProject(UUID)` → `List<KnowledgeRelationResponse>`
- `getBySource(EntityType, UUID)` → `List<KnowledgeRelationResponse>`
- `getByTarget(EntityType, UUID)` → `List<KnowledgeRelationResponse>`
- `delete(UUID)` → void

Create `knowledge/relation/service/KnowledgeRelationServiceImpl.java`:
- `create`: validate project exists, validate source ≠ target, map, save, return
- `getById`: find by id or throw EntityNotFoundException
- `getByProject`: query by project id
- `getBySource`: query by source entity type + id
- `getByTarget`: query by target entity type + id
- `delete`: find by id, delete, or throw EntityNotFoundException

### Step 9: KnowledgeRelationController

Create `knowledge/relation/controller/KnowledgeRelationController.java`:
- `@RestController` at `/api/v1/knowledge-relations`
- `POST /` → create (returns 201 + Location header)
- `GET /{id}` → getById
- `GET /project/{projectId}` → getByProject
- `GET /source/{entityType}/{entityId}` → getBySource
- `GET /target/{entityType}/{entityId}` → getByTarget
- `DELETE /{id}` → delete (returns 204)

### Step 10: Tests

Create `knowledge/relation/service/KnowledgeRelationServiceTest.java`:
- `shouldCreateRelationSuccessfully`
- `shouldThrowExceptionWhenProjectDoesNotExist`
- `shouldThrowExceptionWhenSourceEqualsTarget`
- `shouldReturnRelationsForProject`
- `shouldFindRelationByIdSuccessfully`
- `shouldThrowExceptionWhenRelationDoesNotExist`
- `shouldReturnRelationsBySource`
- `shouldReturnRelationsByTarget`
- `shouldDeleteRelationSuccessfully`
- `shouldThrowExceptionWhenDeletingNonExistentRelation`

Create `knowledge/relation/controller/KnowledgeRelationControllerWebMvcTest.java`:
- `shouldExposeAllKnowledgeRelationRoutes` — POST, GET by id, GET by project,
  GET by source, GET by target, DELETE

### Step 11: Verification

- Run `mvn clean verify` — all tests must pass
- Update `ProjectDeletionPostgresIntegrationTest` migration count 34 → 35
- Run SonarQube Quality Gate — 0 new violations

## File Manifest

New files (13):
```
backend/src/main/java/com/hopeful117/devlogai/knowledge/relation/
  entity/EntityType.java
  entity/KnowledgeRelationType.java
  entity/KnowledgeRelation.java
  repository/KnowledgeRelationRepository.java
  dto/request/CreateKnowledgeRelationRequest.java
  dto/response/KnowledgeRelationResponse.java
  mapper/KnowledgeRelationMapper.java
  service/KnowledgeRelationService.java
  service/KnowledgeRelationServiceImpl.java
  controller/KnowledgeRelationController.java

backend/src/main/resources/db/migration/V35__create_knowledge_relations_table.sql

backend/src/test/java/com/hopeful117/devlogai/knowledge/relation/
  service/KnowledgeRelationServiceTest.java
  controller/KnowledgeRelationControllerWebMvcTest.java
```

Modified files (1):
```
backend/src/test/java/.../project/ProjectDeletionPostgresIntegrationTest.java
```
