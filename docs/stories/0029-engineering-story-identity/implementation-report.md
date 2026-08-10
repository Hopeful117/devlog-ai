# Implementation Report — Story 0029

## Story

Story 0029 — Engineering Story Identity and Git Evolution Tracking: introduce the `EngineeringStory` entity and supporting infrastructure to establish deterministic traceability from Engineering Stories to their Git evolution (the first missing edge in the feedback loop).

## What Was Implemented

### Step 1 — Entity & Enum

Created `EngineeringStory` JPA entity and `StoryStatus` enum:
- `EngineeringStory`: UUID PK, project FK (CASCADE), storyNumber, title, status, storyPath, baseCommit, targetCommit, createdAt, completedAt
- `StoryStatus`: `REGISTERED`, `IN_PROGRESS`, `COMPLETED`

### Step 2 — Migration

Created **V36** `engineering_stories` table with:
- UNIQUE constraint on `(project_id, story_number)`
- CHECK constraint on `status`
- FK to `projects` with `ON DELETE CASCADE`
- Index on `project_id`

### Step 3 — Repository

Created `EngineeringStoryRepository` (Spring Data JPA):
- `findByProject_Id`
- `findByProject_IdOrderByCreatedAtDesc`
- `findByProject_IdAndStoryNumber`
- `existsByProject_Id`
- `findByProject_IdAndStatusOrderByStoryNumber`

### Step 4 — Service Layer

Created `EngineeringStoryService` interface and `EngineeringStoryServiceImpl`:
- `register`: validates project existence, maps request, defaults status to `REGISTERED`, saves
- `startImplementation`: requires current status `REGISTERED`, sets `baseCommit`, transitions to `IN_PROGRESS`
- `complete`: requires current status `IN_PROGRESS`, sets `targetCommit` and `completedAt`, transitions to `COMPLETED`
- `getById` / `getByProject`
- Enforces linear status transitions (`REGISTERED → IN_PROGRESS → COMPLETED`)

### Step 5 — Controller

Created `EngineeringStoryController` at `/api/v1/projects/{projectId}/stories`:
- `POST` register → `201 Created` + Location header
- `POST /{storyId}/start`
- `POST /{storyId}/complete`
- `GET /{storyId}`
- `GET` (list)

### Step 6 — Mapper & DTOs

- `EngineeringStoryMapper`: MapStruct (`spring`), maps `project.id ↔ projectId`
- DTOs with Jakarta validation: `CreateEngineeringStoryRequest`, `StartStoryRequest`, `CompleteStoryRequest`, `EngineeringStoryResponse`

### Step 7 — Snapshot Enrichment (Part 2)

- Added `EngineeringStorySnapshot` record and `engineeringStories` field to `ProjectContextSnapshot`
- `ProjectContextProviderImpl`: injected `EngineeringStoryRepository`, added `MAX_ENGINEERING_STORIES = 20`, added `toEngineeringStorySnapshot()`, populated in `build()`
- Propagated `engineeringStories` through `AnalysisContext` and `RepositoryContextAdapter`

### Step 8 — Tests

Added 10 unit tests:
- `EngineeringStoryServiceTest` (9): register success, register missing project, start, start invalid transition, complete, complete invalid transition, get by id, story not found, get by project
- `EngineeringStoryControllerWebMvcTest` (1): all 5 routes

### Step 9 — Validation

- Compilation: ✅ (0 errors)
- Story tests: 10/10 passing
- Full suite: 528 tests, 0 failures

## Implementation Deviations

- Changed service `complete` signature to `complete(UUID storyId, CompleteStoryRequest)` to correctly identify the story from the `/stories/{storyId}/complete` route (plan defined `complete(CompleteStoryRequest)` without an identifier).
- Fixed `CreateEngineeringStoryRequest.storyNumber` annotation: `@NotNull` (the plan's `@NotBlank` is invalid for a non-CharSequence field and caused a 500 during validation).
- Added `@Mapping(project.id → projectId)` to `EngineeringStoryMapper.toResponse` for correct response mapping.

## Documentation Reconciliation

**Documentation update: Not required.**

Rationale:
- Standard CRUD pattern matching the existing Challenge/Decision entities
- New `story` package mirrors the `challenge`/`decision` package structure
- Single new schema migration (V36) follows the established Flyway convention
- No README/architecture doc depends on Engineering Stories yet

## Files Changed

| File | Change |
|------|--------|
| `story/.../EngineeringStory.java` | New entity |
| `story/.../StoryStatus.java` | New enum |
| `story/.../EngineeringStoryRepository.java` | New repository |
| `story/.../EngineeringStoryService.java` | New interface |
| `story/.../EngineeringStoryServiceImpl.java` | New implementation |
| `story/.../EngineeringStoryController.java` | New controller |
| `story/.../EngineeringStoryMapper.java` | New mapper |
| `story/.../dto/request/CreateEngineeringStoryRequest.java` | New DTO |
| `story/.../dto/request/StartStoryRequest.java` | New DTO |
| `story/.../dto/request/CompleteStoryRequest.java` | New DTO |
| `story/.../dto/response/EngineeringStoryResponse.java` | New DTO |
| `db/migration/V36__create_engineering_stories_table.sql` | New migration |
| `projectcontext/ProjectContextSnapshot.java` | +1 record, +1 field, updated constructor |
| `projectcontext/ProjectContextProviderImpl.java` | +1 injection, +1 constant, +1 mapping method, updated build() |
| `analysis/context/AnalysisContext.java` | +1 field, updated constructors |
| `analysis/context/AnalysisContextServiceImpl.java` | Pass-through `engineeringStories` |
| `projectcontext/RepositoryContextAdapter.java` | Pass-through `engineeringStories` |
| `ProjectContextProviderTest.java` | +1 mock +8 stubs |
| `RepositoryContextAdapterTest.java` | +2 constructor args |
| `ProjectDeletionPostgresIntegrationTest.java` | Migration version 35 → 36 |

## Migration

V36 — creates the `engineering_stories` table (new).

## Acceptance Criteria

| AC # | Status |
|---|---|
| AC-1: EngineeringStory entity | ✅ |
| AC-2: StoryStatus enum | ✅ |
| AC-3: V36 migration | ✅ |
| AC-4: Repository | ✅ |
| AC-5: Service (+Impl) | ✅ |
| AC-6: Controller | ✅ |
| AC-7: DTOs | ✅ |
| AC-8: Mapper | ✅ |
| AC-9: Unit tests | ✅ (10) |
| AC-10: Snapshot enrichment | ✅ |
| AC-11: SonarQube gate | ✅ PASSED (coverage 81.2%, 0 new violations) |