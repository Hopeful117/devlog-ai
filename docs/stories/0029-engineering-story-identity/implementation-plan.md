# Implementation Plan — Story 0029: Engineering Story Identity and Git Evolution Tracking

## Overview

This plan details the implementation of the `EngineeringStory` entity and supporting infrastructure to establish the first missing edge in the feedback loop: deterministic traceability from Engineering Stories to their Git evolution.

---

## Part 1: New Files to Create

### 1.1 Entity & Enum

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/entity/EngineeringStory.java`

Lombok JPA entity with:
- `@Entity`, `@Table(name = "engineering_stories")`
- `@EntityListeners(AuditingEntityListener.class)`
- UUID primary key, project FK with CASCADE
- Fields: id, project, storyNumber, title, status, storyPath, baseCommit, targetCommit, createdAt, completedAt

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/entity/StoryStatus.java`

Enum with values: `REGISTERED`, `IN_PROGRESS`, `COMPLETED`

---

### 1.2 Repository

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/repository/EngineeringStoryRepository.java`

Spring Data JPA repository extending `JpaRepository<EngineeringStory, UUID>`:
- `List<EngineeringStory> findByProject_Id(UUID projectId)`
- `Optional<EngineeringStory> findByProject_IdAndStoryNumber(UUID projectId, Integer storyNumber)`
- `boolean existsByProject_IdAndStatus(UUID projectId, StoryStatus status)`

---

### 1.3 Service Layer

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/service/EngineeringStoryService.java`

Interface with operations:
- `EngineeringStoryResponse register(CreateEngineeringStoryRequest request)`
- `EngineeringStoryResponse startImplementation(UUID storyId, StartStoryRequest request)`
- `EngineeringStoryResponse complete(CompleteStoryRequest request)`
- `EngineeringStoryResponse getById(UUID storyId)`
- `List<EngineeringStoryResponse> getByProject(UUID projectId)`

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/service/EngineeringStoryServiceImpl.java`

Implementation with:
- `@RequiredArgsConstructor` for dependency injection
- Project existence validation
- Status transition validation (REGISTERED → IN_PROGRESS → COMPLETED only)
- Immutable timestamp handling

---

### 1.4 DTOs

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/dto/request/CreateEngineeringStoryRequest.java`

Lombok `@Data` record with:
- `@NotNull` UUID projectId
- `@NotBlank` String title
- `@NotBlank` Integer storyNumber
- `@NotBlank` String storyPath

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/dto/request/StartStoryRequest.java`

Lombok `@Data` record with:
- `@NotBlank` String baseCommit

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/dto/request/CompleteStoryRequest.java`

Lombok `@Data` record with:
- `@NotBlank` String targetCommit

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/dto/response/EngineeringStoryResponse.java`

Java record with all fields:
- UUID id, UUID projectId, Integer storyNumber, String title, String status, String storyPath, String baseCommit, String targetCommit, Instant createdAt, Instant completedAt

---

### 1.5 Controller

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/controller/EngineeringStoryController.java`

`@RestController` with:
- `POST /api/v1/projects/{projectId}/stories` → `201 Created` with Location header
- `POST /api/v1/projects/{projectId}/stories/{storyId}/start`
- `POST /api/v1/projects/{projectId}/stories/{storyId}/complete`
- `GET /api/v1/projects/{projectId}/stories/{storyId}` → `200 OK`
- `GET /api/v1/projects/{projectId}/stories` → `200 OK` (list)

---

### 1.6 Mapper

**File:** `backend/src/main/java/com/hopeful117/devlogai/story/mapper/EngineeringStoryMapper.java`

MapStruct with `componentModel = "spring"`:
- `toResponse(EngineeringStory)` → `EngineeringStoryResponse`
- `toEntity(CreateEngineeringStoryRequest)` → `EngineeringStory`
- Field naming conventions for camelCase ↔ snake_case

---

### 1.7 Migration

**File:** `backend/src/main/resources/db/migration/V36__create_engineering_stories_table.sql`

SQL migration creating `engineering_stories` table with:
- UUID PK
- project_id FK with `ON DELETE CASCADE`
- UNIQUE constraint on (project_id, story_number)
- CHECK constraint on status
- Indexes on project_id

---

### 1.8 Tests

**File:** `backend/src/test/java/com/hopeful117/devlogai/story/service/EngineeringStoryServiceTest.java`

Mockito unit tests covering:
- Register success
- Register with invalid projectId → `EntityNotFoundException`
- Start implementation
- Complete story
- Get by ID
- Get by project

**File:** `backend/src/test/java/com/hopeful117/devlogai/story/controller/EngineeringStoryControllerWebMvcTest.java`

WebMvc tests extending `ControllerWebMvcTestSupport` covering:
- `POST /api/v1/projects/{projectId}/stories` → `201 Created` + Location header
- `POST /.../start` → `200 OK`
- `POST /.../complete` → `200 OK`
- `GET /.../stories/{id}` → `200 OK`
- `GET /.../stories` → `200 OK` with array

---

## Part 2: Files to Modify

### 2.1 Entity Snapshots

**File:** `ProjectContextSnapshot.java`

Changes:
- Add `List<EngineeringStorySnapshot> engineeringStories` to the record fields
- Add `EngineeringStorySnapshot` nested record

```java
public record ProjectContextSnapshot(
    // ... existing fields ...
    List<EngineeringStorySnapshot> engineeringStories
) {
    public record EngineeringStorySnapshot(
        UUID id,
        UUID projectId,
        Integer storyNumber,
        String title,
        String status,
        String storyPath,
        String baseCommit,
        String targetCommit,
        Instant createdAt,
        Instant completedAt
    ) {}
}
```

---

### 2.2 Context Provider

**File:** `ProjectContextProviderImpl.java`

Changes:
- Add constant: `private static final int MAX_ENGINEERING_STORIES = 20;`
- Inject `EngineeringStoryRepository` via constructor
- Add method: `private List<EngineeringStorySnapshot> toEngineeringStorySnapshots(UUID projectId)`
- Call in `build()` method to populate the new field

---

### 2.3 Adapter & Analysis Context

**File:** `RepositoryContextAdapter.java`

Changes:
- Ensure `ProjectContextSnapshot.engineeringStories` is passed through to `AnalysisContext`

**File:** `AnalysisContext.java`

Changes:
- Add `List<EngineeringStorySnapshot> engineeringStories` field
- Update all constructors to include the new field with default `List.of()`

**File:** `AnalysisContextServiceImpl.java`

Changes:
- Pass `projectContext.engineeringStories()` to `AnalysisContext` constructor

---

## Part 3: Build & Test Requirements

### Compile

```bash
./gradlew compileJava compileTestJava --no-daemon
```

### Run Tests

```bash
./gradlew test --tests "*story*" --no-daemon
```

### Expected Results

- 0 compilation errors
- All Story tests pass
- SonarQube Quality Gate passes (0 new violations)

---

## Part 4: Package Structure

New package: `com.hopeful117.devlogai.story`

Structure:
```
story/
├── entity/
│   ├── EngineeringStory.java
│   └── StoryStatus.java
├── repository/
│   └── EngineeringStoryRepository.java
├── service/
│   ├── EngineeringStoryService.java
│   └── EngineeringStoryServiceImpl.java
├── dto/
│   ├── request/
│   │   ├── CreateEngineeringStoryRequest.java
│   │   ├── StartStoryRequest.java
│   │   └── CompleteStoryRequest.java
│   └── response/
│       └── EngineeringStoryResponse.java
├── controller/
│   └── EngineeringStoryController.java
└── mapper/
    └── EngineeringStoryMapper.java
```

---

## Part 5: Git Commits Strategy

Following established workflow:
- **Primary commit:** All implementation changes
- **Code Review fixes:** Additional commits if needed (rare in current workflow)
- **Commit message:** `feat(story): add engineering story identity and git evolution tracking`

---

## Part 6: Documentation Reconciliation

After implementation and Code Review:
- Verify all new code is self-documenting
- Story.md artifacts are complete
- No changes to README/architecture docs required

---

## Part 7: Expected Test Coverage

| Metric | Target |
|---|---|
| Line coverage (new code) | ≥ 85% |
| Instruction coverage | ≥ 90% |
| Branch coverage | ≥ 75% |
| Complexity | ≤ 10 per method |
| Cyclomatic complexity average | ≤ 5 |

---

## Acceptance Criteria Verification

| AC # | Status |
|---|---|
| AC-1: EngineeringStory entity | ✅ Plan defines complete entity structure |
| AC-2: StoryStatus enum | ✅ Included in plan |
| AC-3: V36 migration | ✅ SQL structure defined |
| AC-4: Repository | ✅ Spring Data JPA pattern |
| AC-5: Service | ✅ CRUD methods defined |
| AC-6: Controller | ✅ REST endpoints defined |
| AC-7: DTOs | ✅ All 4 DTOs planned |
| AC-8: Mapper | ✅ MapStruct pattern |
| AC-9: Unit tests | ✅ Service + Controller tests planned |
| AC-10: Snapshot enrichment | ✅ Integration planned |
| AC-11: SonarQube | ✅ Build target defined |

---

## Dependencies

- **No new dependencies** — uses existing:
  - Lombok
  - Spring Data JPA
  - MapStruct
  - Jakarta Validation
  - PostgreSQL/Flyway

- **No changes to existing entities** — backward compatible

---

**End of Implementation Plan**