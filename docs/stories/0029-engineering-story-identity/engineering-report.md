# Engineering Report — Story 0029

## Story

Story 0029 — Engineering Story Identity and Git Evolution Tracking.

## Objective

Establish the first missing edge in the feedback loop: deterministic traceability from Engineering Stories to their Git evolution. The system can now answer "which Git evolution implemented Story X" by giving Engineering Stories a domain identity (an `EngineeringStory` entity) and a lifecycle that records their `baseCommit` → `targetCommit` evolution.

## Implementation Summary

### What Was Delivered

New `story` package exposing a full CRUD + lifecycle API:

| Operation | Endpoint | Method |
|-----------|----------|--------|
| Register | `POST /api/v1/projects/{projectId}/stories` | `register()` → 201 + Location |
| Start | `POST /api/v1/projects/{projectId}/stories/{storyId}/start` | `startImplementation()` |
| Complete | `POST /api/v1/projects/{projectId}/stories/{storyId}/complete` | `complete()` |
| Read by ID | `GET /api/v1/projects/{projectId}/stories/{storyId}` | `getById()` |
| List by project | `GET /api/v1/projects/{projectId}/stories` | `getByProject()` |

Snapshot enrichment: `ProjectContextSnapshot` now exposes `engineeringStories` (`List<EngineeringStorySnapshot>`, max 20), wired through `AnalysisContext` and `RepositoryContextAdapter`.

### Architecture

- **Entity** `EngineeringStory`: UUID PK, project FK (CASCADE), storyNumber, title, status, storyPath, baseCommit, targetCommit, createdAt, completedAt.
- **Enum** `StoryStatus`: `REGISTERED → IN_PROGRESS → COMPLETED` (transitions linéaires validées en service).
- **Migration V36**: UNIQUE `(project_id, story_number)`, CHECK status, FK `ON DELETE CASCADE`, index sur `project_id`.
- **Mapper** MapStruct (`spring`), maps `project.id ↔ projectId`.
- **Scoping projet** : `getById`/`start`/`complete` valident que la story appartient au `projectId` du path.
- **Erreurs** : transitions invalides → `ConflictException` (409) ; story/projet introuvable → `EntityNotFoundException` (404).

### Testing

11 tests ajoutés :
- Service (10) : register succès + projet absent, start succès + transition invalide, complete succès + transition invalide, getById, not found, appartenance projet, list par projet.
- Controller (1) : les 5 routes (201 + Location, start/complete/get/list).

Tests existants mis à jour : `ProjectContextProviderTest`, `RepositoryContextAdapterTest`, `ProjectDeletionPostgresIntegrationTest` (version migration 35 → 36).

### Validation

- Compilation: ✅ (0 erreur)
- Story tests: 11/11
- Full suite: **529 tests, 0 échec**
- SonarQube Quality Gate: **PASSED** (AC-11)

## Documentation Reconciliation

**Documentation update: Not required.**

Standard CRUD pattern matching the existing Challenge/Decision entities, new `story` package mirrors `challenge`/`decision`. No README/architecture doc depends on Engineering Stories yet.

## Files Changed

| File | Description |
|------|-------------|
| `story/.../entity/EngineeringStory.java` | New entity |
| `story/.../entity/StoryStatus.java` | New enum |
| `story/.../repository/EngineeringStoryRepository.java` | New repository |
| `story/.../service/EngineeringStoryService.java` | New interface |
| `story/.../service/EngineeringStoryServiceImpl.java` | New implementation |
| `story/.../controller/EngineeringStoryController.java` | New controller |
| `story/.../mapper/EngineeringStoryMapper.java` | New mapper |
| `story/.../dto/request/CreateEngineeringStoryRequest.java` | New DTO |
| `story/.../dto/request/StartStoryRequest.java` | New DTO |
| `story/.../dto/request/CompleteStoryRequest.java` | New DTO |
| `story/.../dto/response/EngineeringStoryResponse.java` | New DTO |
| `db/migration/V36__create_engineering_stories_table.sql` | New migration |
| `projectcontext/ProjectContextSnapshot.java` | +`EngineeringStorySnapshot`, +`engineeringStories` |
| `projectcontext/ProjectContextProviderImpl.java` | +injection, +constant, +mapping, build() |
| `analysis/context/AnalysisContext.java` | +`engineeringStories`, constructors |
| `analysis/context/AnalysisContextServiceImpl.java` | Pass-through |
| `projectcontext/RepositoryContextAdapter.java` | Pass-through |
| `ProjectContextProviderTest.java` | +mock, +stubs |
| `RepositoryContextAdapterTest.java` | +constructor args |
| `ProjectDeletionPostgresIntegrationTest.java` | Migration 35 → 36 |
| `decision/service/DecisionServiceImpl.java` | Constante `DECISION` (fix SonarQube) |

## Workflow Approvals

- Repository Analysis: ✅ Approved
- Implementation Plan: ✅ Approved
- Code Review: ✅ Approved

## Remaining Work

None for Story 0029. (Commit `feat(story): add engineering story identity and git evolution tracking` en attente.)

## Lessons Learned

- Le lifecycle à états (`REGISTERED → IN_PROGRESS → COMPLETED`) impose un scoping projet explicite sur `getById`/`start`/`complete` — le path `projectId` ne doit-même pas pouvoir cibler une story d'un autre projet.
- Les transitions illégales doivent retourner un statut client (409/Conflict) et non un 500 : `IllegalStateException` n'est pas mappée, `ConflictException` l'est.
- Le goal Jacoco `report` n'est lié qu'au phase `verify` : un `mvn test` seul laisse un `jacoco.xml` périmé qui fausse `new_coverage` dans SonarQube (79.8% ↔ 81.2% réel). Régénérer via `jacoco:report` avant `sonar:sonar`.

## Final Status

**Completed**