# Implementation Report — Story 0024 Challenge Entity

## Status

✅ Complete

## Summary

Implemented the `Challenge` entity following the established `Decision` pattern. New package
`challenge/` with full CRUD, Flyway migration, and tests.

## Implementation

### Step 1: ChallengeStatus Enum
Created `ChallengeStatus.java` with values: `OPEN`, `RESOLVED`, `ACCEPTED`, `MITIGATED`.

### Step 2: Challenge Entity
Created `Challenge.java` — JPA entity with `id`, `project` (ManyToOne LAZY), `title`,
`description`, `impact`, `status` (default OPEN), `resolution`, `createdAt`, `updatedAt`.

### Step 3: Flyway Migration V34
Created `V34__create_challenges_table.sql` with `challenges` table, FK to `projects(id)`
with CASCADE delete, CHECK constraint on status, and index on `project_id`.

### Step 4: ChallengeRepository
Created `ChallengeRepository.java` extending `JpaRepository<Challenge, UUID>` with
`findByProjectIdOrderByCreatedAtDesc` and paginated variant.

### Step 5: DTOs
Created `CreateChallengeRequest` (projectId + title required), `UpdateChallengeRequest`
(all fields nullable), and `ChallengeResponse` (Java record with all fields).

### Step 6: ChallengeMapper
Created MapStruct `ChallengeMapper` with `toResponse` and `toEntity` mappings.

### Step 7: ChallengeService + ChallengeServiceImpl
Created interface and implementation with `create`, `getById`, `getByProject`, and `update`
operations. Update applies only non-null fields from the request.

### Step 8: ChallengeController
Created REST controller at `/api/v1/challenges` with POST (201 + Location), GET by id,
GET by project, and PUT endpoints.

### Step 9: Tests
- `ChallengeServiceTest` — 7 tests: create, project not found, list by project, get by id,
  challenge not found, update, update not found
- `ChallengeControllerWebMvcTest` — 1 test: all routes (POST, GET, GET by project, PUT)

### Step 10: Verification
- `mvn clean verify` — 494 tests, 0 failures
- SonarQube Quality Gate PASSED — 0 new violations

## Files Modified

- `ProjectDeletionPostgresIntegrationTest.java` — migration count 33 → 34

## Files Created (13)

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

## Test Evidence

- 494 tests passing (0 failures, 0 errors)
- SonarQube Quality Gate: PASSED
- 0 new violations
