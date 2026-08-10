# Story 0029 — Engineering Story Identity and Git Evolution Tracking

## Status

Draft

## Objective

Introduce an `EngineeringStory` entity that enables DevLog to track Engineering Stories and their deterministic Git evolution, establishing the first missing edge in the feedback loop between the engineering workflow and the knowledge model.

## Motivation

The feedback loop analysis identified Edge A as the first missing deterministic connection: "Engineering Story has no domain identity in DevLog." The system knows about analyses, proposals, insights, engineering events, challenges, decisions, and knowledge relations — but does NOT know that "Story 0025" existed or that specific commits belong to it.

Without this edge, DevLog cannot:
- Answer "which Git evolution implemented Story X?"
- Provide Story-aware context to agents
- Enable Story-aware evidence ranking
- Connect the engineering workflow to the knowledge model

## Acceptance Criteria

- AC-1: `EngineeringStory` JPA entity with fields: `id`, `project`, `storyNumber`, `title`, `status`, `storyPath`, `baseCommit`, `targetCommit`, `createdAt`, `completedAt`.
- AC-2: `StoryStatus` enum with values: `REGISTERED`, `IN_PROGRESS`, `COMPLETED`.
- AC-3: Flyway migration V36 creating the `engineering_stories` table with project FK (`ON DELETE CASCADE`), unique constraint on `(project_id, story_number)`, and status CHECK constraint.
- AC-4: `EngineeringStoryRepository` Spring Data JPA repository with project queries.
- AC-5: `EngineeringStoryService` / `EngineeringStoryServiceImpl` with `register`, `startImplementation`, `complete`, `getById`, `getByProject` operations.
- AC-6: `EngineeringStoryController` REST API at `/api/v1/projects/{projectId}/stories`.
- AC-7: Request/Response DTOs with Jakarta validation.
- AC-8: MapStruct mapper for entity ↔ DTO conversion.
- AC-9: Unit tests for service and controller.
- AC-10: `ProjectContextSnapshot` enriched with recent stories.
- AC-11: SonarQube Quality Gate passes with 0 new violations.

## Artifacts

- `repository-analysis.md`
- `implementation-plan.md`
- `implementation-report.md`
- `code-review.md`
- `engineering-report.md`
