# Engineering Report — Story 0024 Challenge Entity

## Status

✅ Completed

## Objective

Introduce a `Challenge` entity to capture engineering problems encountered during a project's
lifecycle, completing the knowledge triad alongside Decisions and Engineering Events.

## Acceptance Criteria

| AC | Description | Status |
|----|-------------|--------|
| AC-1 | `Challenge` JPA entity with required fields | ✅ |
| AC-2 | Flyway migration V34 | ✅ |
| AC-3 | `ChallengeRepository` | ✅ |
| AC-4 | `ChallengeService` with CRUD operations | ✅ |
| AC-5 | `ChallengeController` REST API | ✅ |
| AC-6 | Request/Response DTOs with validation | ✅ |
| AC-7 | MapStruct mapper | ✅ |
| AC-8 | Unit tests (service + controller) | ✅ |
| AC-9 | SonarQube Quality Gate passes | ✅ |

## Implementation

### What was built

- `ChallengeStatus` enum — `OPEN`, `RESOLVED`, `ACCEPTED`, `MITIGATED`
- `Challenge` entity — `id`, `project` (ManyToOne), `title`, `description`, `impact`,
  `status` (default OPEN), `resolution`, audit timestamps
- `ChallengeRepository` — Spring Data JPA with project queries
- `ChallengeService` / `ChallengeServiceImpl` — create, getById, getByProject, update
- `ChallengeController` — REST API at `/api/v1/challenges`
- DTOs — `CreateChallengeRequest`, `UpdateChallengeRequest`, `ChallengeResponse`
- MapStruct mapper for entity ↔ DTO
- Flyway `V34` — table, FK, CHECK constraint, index

### What was modified

- `ProjectDeletionPostgresIntegrationTest` — migration count 33 → 34

### Verification

- **494 tests** passing (0 failures, 0 errors)
- **SonarQube Quality Gate**: PASSED
- **0 new violations**

### Decisions

- **Status enum default**: Challenge defaults to `OPEN` when not specified in creation request.
  Both `@Builder.Default` on entity and explicit null check in service provide defense-in-depth.
- **Partial update**: PUT endpoint applies only non-null fields, following standard REST convention.
- **No relationships yet**: Challenge is independent for now. Knowledge relationships (Challenge ↔
  Event, Challenge ↔ Decision) are deferred to Story 0025/0026.

## Residual Risks

- **Low**: No knowledge relationships yet — Challenge exists but is not linked to other entities
- **Info**: Status transitions are unconstrained — any status can transition to any other status

## Next Steps

- Story 0025: Knowledge Relationships model
- Story 0026: Wire Challenge, Decision, and Engineering Event via relationships
