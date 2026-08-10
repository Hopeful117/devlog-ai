# Story 0024 — Challenge Entity

## Status

✅ Completed

## Objective

Introduce a `Challenge` entity to capture engineering challenges encountered during a project's
lifecycle, with full CRUD API, database persistence, and test coverage.

## Motivation

Phase 3 of the DevLog roadmap (Knowledge Model) requires structured knowledge entities beyond
Engineering Events and Decisions. A Challenge represents a problem encountered during development:
what happened, why it mattered, and how (or whether) it was resolved. Challenges complete the
knowledge triad alongside Decisions and Events, enabling future relationship mapping.

## Acceptance Criteria

- AC-1: `Challenge` JPA entity with fields: `id`, `project`, `title`, `description`, `impact`,
  `status` (OPEN / RESOLVED / ACCEPTED / MITIGATED), `resolution`, `createdAt`, `updatedAt`.
- AC-2: Flyway migration `V34` creating the `challenges` table with project FK and status check
  constraint.
- AC-3: `ChallengeRepository` Spring Data JPA repository.
- AC-4: `ChallengeService` / `ChallengeServiceImpl` with `create`, `getById`, `getByProject`,
  and `update` operations.
- AC-5: `ChallengeController` REST API at `/api/v1/challenges` with POST, GET by id,
  GET by project, and PUT endpoints.
- AC-6: Request/Response DTOs with Jakarta validation.
- AC-7: MapStruct mapper for entity ↔ DTO conversion.
- AC-8: Unit tests for service and controller (following existing Decision test patterns).
- AC-9: SonarQube Quality Gate passes with 0 new violations.

## Artifacts

- `repository-analysis.md`
- `implementation-plan.md`
- `implementation-report.md`
- `code-review.md`
- `engineering-report.md`
