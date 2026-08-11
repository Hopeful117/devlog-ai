# Story 0034 — Repository Analysis

## Purpose

Identify exactly which backend repositories, entities and queries are needed to build the deterministic project timeline, and what must be added (additive only).

## Entities (sources) — verified

| Source | Entity | Timestamp field | Status filter | Title field |
| --- | --- | --- | --- | --- |
| STORY_COMPLETED | `story/entity/EngineeringStory` | `completedAt` (nullable) | `StoryStatus.COMPLETED` | `title` |
| ENGINEERING_EVENT | `engineeringevent/EngineeringEvent` | `occurredAt` | none | `title` |
| KNOWLEDGE_EVENT | `knowledge/entity/KnowledgeEvent` | `createdAt` | none | `title` |
| DECISION | `decision/entity/Decision` | `createdAt` | none | `title` |
| MILESTONE_COMPLETED | `milestone/entity/Milestone` | `completedAt` (nullable) | `MilestoneStatus.COMPLETED` | `name` |

### `detail` per type (V1)

- `STORY_COMPLETED` → `"#" + storyNumber`
- `ENGINEERING_EVENT` → `category.name()`
- `KNOWLEDGE_EVENT` → `type.name()`
- `DECISION` → `null`
- `MILESTONE_COMPLETED` → `null`

Note: `Decision.choice` is `@Column(length = 5000)` and is deliberately **not** used to keep `detail` small.

## Existing repository queries (reusable) — verified

| Repository | Query | Bound |
| --- | --- | --- |
| `EngineeringEventRepository` | `findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(UUID, Pageable)` -> `List` | ✅ |
| `KnowledgeEventRepository` | `findByProjectIdOrderByCreatedAtDescIdDesc(UUID, Pageable)` -> `List` | ✅ |
| `DecisionRepository` | `findByProjectIdOrderByCreatedAtDescIdDesc(UUID, Pageable)` -> `List` | ✅ |

## Repository queries to add (additive)

| Repository | New method | Signature |
| --- | --- | --- |
| `EngineeringStoryRepository` | COMPLETED stories most-recent-first | `List<EngineeringStory> findByProject_IdAndStatusOrderByCompletedAtDescIdDesc(UUID, StoryStatus, Pageable)` |
| `MilestoneRepository` | COMPLETED milestones most-recent-first | `List<Milestone> findByProjectIdAndStatusOrderByCompletedAtDescIdDesc(UUID, MilestoneStatus, Pageable)` |

Both follow the existing `OrderBy...DescIdDesc` convention used by `KnowledgeEventRepository`/`DecisionRepository`/`MilestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc`.

## Pattern reference (`projectstate`, story 0030/0033)

Controller → Service (interface) → ServiceImpl (aggregates queried lists) → Mapper (MapStruct, entity→DTO). Tests: `service` (Mockito unit), `controller` (`ControllerWebMvcTestSupport` WebMvc), `mapper` (`MapperImpl`).

## Commits / proposals — excluded (documented)

- **Commits**: `ProjectCommit` is high-volume and read differently; the timeline would degrade into a technical journal. Already represented in Overview → `recentCommits`. Story `baseCommit`/`targetCommit` hashes are surfaced *on the story entry itself*; **no** join against `ProjectCommit` is fabricated (no persisted Story↔Commit relation exists — see KnowledgeRelation `EntityType` which does not include STORY/COMMIT).
- **Proposals / analyses**: `EngineeringEvent` is the validated, promoted representation; including proposals would duplicate it.

## Bounding strategy

Per source: `PageRequest.of(0, 20)`. Merge in memory, sort `(timestamp DESC, type.name ASC, id ASC)`, take first 20 globally. Deterministic regardless of enum declaration order (tie-break explicit via `type.name()`).