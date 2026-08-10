# Implementation Plan — Story 0024 Challenge Entity

## Overview

Implement a `Challenge` entity following the exact patterns of the existing `Decision` entity.
New package `challenge/` with full CRUD, Flyway migration, and tests.

## Steps

### Step 1: ChallengeStatus Enum

Create `challenge/entity/ChallengeStatus.java` with values: `OPEN`, `RESOLVED`, `ACCEPTED`, `MITIGATED`.

### Step 2: Challenge Entity

Create `challenge/entity/Challenge.java`:
- `UUID id` (generated)
- `Project project` (ManyToOne LAZY, not null)
- `String title` (not null)
- `String description` (TEXT, nullable)
- `String impact` (TEXT, nullable)
- `ChallengeStatus status` (Enumerated STRING, not null, default OPEN)
- `String resolution` (TEXT, nullable)
- `Instant createdAt` (CreatedDate)
- `Instant updatedAt` (LastModifiedDate)

### Step 3: Flyway Migration V34

Create `V34__create_challenges_table.sql`:
- `challenges` table with all columns
- FK to `projects(id)` with CASCADE delete
- CHECK constraint on `status` column
- Index on `project_id`

### Step 4: ChallengeRepository

Create `challenge/repository/ChallengeRepository.java`:
- Extends `JpaRepository<Challenge, UUID>`
- `findByProjectIdOrderByCreatedAtDesc(UUID projectId)`
- `findByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId, Pageable pageable)`

### Step 5: DTOs

Create `challenge/dto/request/CreateChallengeRequest.java`:
- `UUID projectId` (@NotNull)
- `String title` (@NotBlank)
- `String description` (nullable)
- `String impact` (nullable)
- `ChallengeStatus status` (nullable, defaults to OPEN)
- `String resolution` (nullable)

Create `challenge/dto/request/UpdateChallengeRequest.java`:
- `String title` (nullable)
- `String description` (nullable)
- `String impact` (nullable)
- `ChallengeStatus status` (nullable)
- `String resolution` (nullable)

Create `challenge/dto/response/ChallengeResponse.java`:
- Java record with all fields including `id`, `projectId`, timestamps

### Step 6: ChallengeMapper

Create `challenge/mapper/ChallengeMapper.java`:
- MapStruct `@Mapper(componentModel = "spring")`
- `toResponse(Challenge)` → `ChallengeResponse`
- `toEntity(CreateChallengeRequest)` → `Challenge`

### Step 7: ChallengeService + ChallengeServiceImpl

Create `challenge/service/ChallengeService.java` (interface):
- `create(CreateChallengeRequest)` → `ChallengeResponse`
- `getById(UUID)` → `ChallengeResponse`
- `getByProject(UUID)` → `List<ChallengeResponse>`
- `update(UUID, UpdateChallengeRequest)` → `ChallengeResponse`

Create `challenge/service/ChallengeServiceImpl.java`:
- Follow `DecisionServiceImpl` patterns exactly
- `create`: validate project exists, map, save, return
- `getById`: find by id or throw `EntityNotFoundException`
- `getByProject`: query by project id
- `update`: find by id, apply non-null fields from request, save, return

### Step 8: ChallengeController

Create `challenge/controller/ChallengeController.java`:
- `@RestController` at `/api/v1/challenges`
- `POST /` → create (returns 201 + Location header)
- `GET /{id}` → getById
- `GET /project/{projectId}` → getByProject
- `PUT /{id}` → update

### Step 9: Unit Tests

Create `challenge/service/ChallengeServiceTest.java`:
- `shouldCreateChallengeSuccessfully`
- `shouldThrowExceptionWhenProjectDoesNotExist`
- `shouldReturnChallengesForProject`
- `shouldFindChallengeByIdSuccessfully`
- `shouldThrowExceptionWhenChallengeDoesNotExist`
- `shouldUpdateChallengeSuccessfully`
- `shouldThrowExceptionWhenUpdatingNonExistentChallenge`

Create `challenge/controller/ChallengeControllerWebMvcTest.java`:
- `shouldExposeAllChallengeRoutes` — POST, GET by id, GET by project, PUT

### Step 10: Verification

- Run `mvn clean verify` — all tests must pass
- Run SonarQube Quality Gate — 0 new violations

## File Manifest

New files (13):
```
backend/src/main/java/com/hopeful117/devlogai/challenge/
  entity/ChallengeStatus.java
  entity/Challenge.java
  repository/ChallengeRepository.java
  dto/request/CreateChallengeRequest.java
  dto/request/UpdateChallengeRequest.java
  dto/response/ChallengeResponse.java
  mapper/ChallengeMapper.java
  service/ChallengeService.java
  service/ChallengeServiceImpl.java
  controller/ChallengeController.java

backend/src/main/resources/db/migration/V34__create_challenges_table.sql

backend/src/test/java/com/hopeful117/devlogai/challenge/
  service/ChallengeServiceTest.java
  controller/ChallengeControllerWebMvcTest.java
```

Modified files: None
