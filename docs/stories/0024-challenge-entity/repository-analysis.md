# Repository Analysis — Story 0024 Challenge Entity

## Story Summary

Introduce a `Challenge` entity to capture engineering problems encountered during a project's
lifecycle. This is the first missing piece of the Phase 3 Knowledge Model, complementing the
existing `Decision` and `EngineeringEvent` entities.

## Current State

### Existing Knowledge Entities

| Entity | Package | Table | Status |
|--------|---------|-------|--------|
| `Decision` | `decision/` | `decisions` | ✅ Complete — CRUD API, tests |
| `EngineeringEvent` | `engineeringevent/` | `engineering_events` | ✅ Complete — validated, immutable |
| `Insight` | `insight/` | `insights` | ✅ Complete — validated proposals |
| `KnowledgeEvent` | `knowledge/entity/` | `knowledge_events` | ⚠️ Legacy raw occurrence layer |
| **Challenge** | — | — | ❌ Does not exist |

### Existing Patterns (Decision as Reference)

The `Decision` entity is the closest reference for a new knowledge entity:

- **Entity**: `decision/entity/Decision.java` — JPA `@Entity`, Lombok `@Builder`, `@AuditingEntityListener`
- **Repository**: `decision/repository/DecisionRepository.java` — extends `JpaRepository<Decision, UUID>`
- **Service**: `decision/service/DecisionService.java` (interface) + `DecisionServiceImpl.java`
- **Controller**: `decision/controller/DecisionController.java` — `@RestController` at `/api/v1/decisions`
- **DTOs**: `decision/dto/request/CreateDecisionRequest.java`, `decision/dto/response/DecisionResponse.java`
- **Mapper**: `decision/mapper/DecisionMapper.java` — MapStruct `@Mapper(componentModel = "spring")`
- **Tests**: `DecisionServiceTest.java` (Mockito unit tests), `DecisionControllerWebMvcTest.java` (MockMvc)
- **Flyway**: Table created by initial migration (V1–V9 era), FK to `projects(id)`

### Architecture Notes

- All entities belong to their own package under `com.hopeful117.devlogai`
- `Project` relationship is always `@ManyToOne(fetch = LAZY, optional = false)` with `@JoinColumn`
- `@CreatedDate` / `@LastModifiedDate` from Spring Data JPA auditing
- UUID primary keys with `@GeneratedValue(strategy = UUID)`
- DTOs are Java records (response) or Lombok `@Data` classes (request)
- Controllers use `@RequiredArgsConstructor` for constructor injection
- Tests extend `ControllerWebMvcTestSupport` for WebMvc tests

### Database

- Current latest migration: `V33__add_validated_engineering_events.sql`
- PostgreSQL 17 with Flyway
- Tables use `UUID PRIMARY KEY`, `TIMESTAMP WITH TIME ZONE`, check constraints for enums

## Affected Modules

- New package: `challenge/` (entity, repository, service, controller, dto, mapper)
- New test package: `challenge/` (service, controller)
- New migration: `V34__create_challenges_table.sql`

## Risks

- **Low**: New entity following well-established patterns — minimal architectural risk
- **Low**: No changes to existing entities or APIs
- **Info**: Challenge status enum needs a check constraint in PostgreSQL

## Constraints

- Follow existing Decision patterns exactly (same package structure, same test patterns)
- Status enum: `OPEN`, `RESOLVED`, `ACCEPTED`, `MITIGATED`
- Must not modify existing entities, APIs, or migrations
- SonarQube Quality Gate must pass with 0 new violations

## Missing Information

- None — the Decision entity provides a complete reference pattern
