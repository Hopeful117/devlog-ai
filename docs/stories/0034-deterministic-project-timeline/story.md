# Story 0034 — Deterministic Project Timeline

## Status

Approved

## Priority

High

## Objective

Add a deterministic, read-model **Project Timeline** (`GET /api/v1/projects/{id}/timeline`) that merges the meaningful chronological evolutions of a project into a single, statically-typed, bounded list — **without any LLM call**. The timeline answers *"comment ce projet a-t-il évolué récemment ?"* with trusted, already-persisted data.

This aligns with the "Implementation Timing" note of ADR-048: strengthen Project History / Timeline before artifact-generation work.

## Motivation

The existing `ProjectState` overview (`GET /projects/{id}/state`) answers *"what is the current state?"* through separate, type-scoped sections (recent changes, recent knowledge, recent evolution). There is no single chronological, cross-type projection of how the project changed over time.

Stories 0032/0033 established the quality bar and read-model conventions. The timeline reuses those conventions to deliver a unified, LLM-free timeline across five trusted sources.

## Scope

### In Scope

1. **Backend — DTOs**
   - `TimelineEntryType` enum: `STORY_COMPLETED`, `ENGINEERING_EVENT`, `KNOWLEDGE_EVENT`, `DECISION`, `MILESTONE_COMPLETED`.
   - `TimelineResponse` DTO: `UUID projectId`, `String projectName`, `List<TimelineEntry> entries`.
   - `TimelineEntry` DTO (common, strongly-typed — no `Map<String,Object>`): `UUID id`, `TimelineEntryType type`, `Instant timestamp`, `String title`, `String detail`.

2. **Backend — projection**
   - New package `projectstate`-style: `timeline` with `controller`, `service`, `serviceImpl`, `mapper`, `dto`.
   - One bounded query per source (5 queries total), in-memory merge, deterministic sort `(timestamp DESC, type.name ASC, id ASC)`, global limit 20.
   - Sources: `EngineeringStory` (COMPLETED, ts `completedAt`), `EngineeringEvent` (ts `occurredAt`), `KnowledgeEvent` (ts `createdAt`), `Decision` (ts `createdAt`), `Milestone` (COMPLETED, ts `completedAt`).

3. **Backend — tests**
   - Unit `TimelineProjectionServiceTest` (populated, empty, tie-break, bounding, not-found).
   - `TimelineControllerWebMvcTest` (200 + 404).
   - `TimelineMapperTest`.

4. **Frontend — feature page**
   - New `features/timeline/` (models, service, page ts/html/scss, spec) reusing the Overview styles.
   - Route `projects/:id/timeline` + sidebar entry "Timeline".

### Out of Scope

- **Commits** — excluded from V1 (noisy, technical-journal-like, already surfaced by Overview → Recent changes; story `baseCommit`/`targetCommit` remain displayed on the story itself).
- **Proposals / analyses** — excluded (would duplicate `EngineeringEvent`, the validated representation).
- **Timeline persistence / new table / migration / caching / graph DB** — none.
- **AI summary / narrative** — none.
- **Related-entity navigation** (`TimelineEntry`), pagination, infinite scroll — none for V1.
- **Enriching the Overview further** — deliberately not touched in this story.
- No new ADR: additive read-model projection, no architectural seam, no schema change.

## Constraints

- **Pure read model**: sources of truth remain the project domains; the timeline introduces no new source of truth and no business duplication.
- **No LLM** anywhere in the projection/display chain.
- **Strongly typed contract**: a common minimal `TimelineEntry` (not a generic `Map<String,Object>`).
- **Deterministic order**: not depending on enum declaration order — explicit `(timestamp DESC, type.name ASC, id ASC)`.
- **Bounded**: one query per source (per-source limit), global limit — no unbounded loading, no N+1.
- **No invented Story↔Commit relation**: only hashes already persisted on the story entity are surfaced; no join is fabricated against `ProjectCommit`.

## Impact

- **Backend**: new `timeline` package (Controller, Service, ServiceImpl, Mapper, DTOs) + repository query additions + 3 test files. No migration, no config change.
- **Frontend**: new `features/timeline/` feature, route addition, sidebar entry. No changes to Overview.
- **CI**: no change (covered by existing backend `quality` job and frontend job).
- **Tests**: backend +~10; frontend +~6.

## Acceptance Criteria

### Backend

- AC-1: `GET /api/v1/projects/{id}/timeline` returns `projectId`, `projectName`, `entries`.
- AC-2: `entries` expose `id`, `type`, `timestamp`, `title`, `detail`.
- AC-3: Merged across the five sources, sorted `timestamp DESC → type.name ASC → id ASC`, bounded to 20.
- AC-4: Empty project returns an empty (not null) `entries` list.
- AC-5: Bounded queries: one per source (no data race / no unbounded load); verified per-source limits.
- AC-6: No LLM call anywhere.
- AC-7: Endpoint returns 404 for a non-existent project.
- AC-8: Unit tests cover populated, empty, tie-break and bounding; integration test covers 200 + 404.

### Frontend

- AC-9: `features/timeline` renders the entries grouped by date with type badge + title + detail.
- AC-10: Empty state placeholder when no entries.
- AC-11: Route `projects/:id/timeline` works and is reachable from the sidebar.
- AC-12: No LLM call is added anywhere.
- AC-13: `timeline-page.spec.ts` covers rendering + empty state.
- AC-14: Existing frontend tests still pass.

## Technical Context

Already verified:

- `EngineeringStory`: `status` = `StoryStatus` (`REGISTERED`|`IN_PROGRESS`|`COMPLETED`), `completedAt` `Instant` nullable, `baseCommit`/`targetCommit` `String` nullable.
- `EngineeringEvent`: `occurredAt` `Instant`, `title`, `category` (`EngineeringEventCategory`).
- `KnowledgeEvent`: `createdAt` `Instant`, `title`, `type` (`KnowledgeEventType`).
- `Decision`: `createdAt` `Instant`, `title`, `choice` (note: `choice` can be up to 5000 chars — not included in `detail`).
- `Milestone`: `status` (`MilestoneStatus` incl. `COMPLETED`), `completedAt` `Instant` nullable, `name`.
- Existing repo query patterns (bounded `Pageable` returning `List`): `KnowledgeEventRepository.findByProjectIdOrderByCreatedAtDescIdDesc`, `EngineeringEventRepository.findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc`, `DecisionRepository.findByProjectIdOrderByCreatedAtDescIdDesc`, `MilestoneRepository.findByProjectIdOrderByStartedAtDescIdDesc`.

Required new/extended repository queries (additive):

- `EngineeringStoryRepository.findByProject_IdAndStatusOrderByCompletedAtDescIdDesc(UUID, StoryStatus, Pageable)`.
- `MilestoneRepository.findByProjectIdAndStatusOrderByCompletedAtDescIdDesc(UUID, MilestoneStatus, Pageable)`.

`detail` per type (V1):

- `STORY_COMPLETED`: `#<storyNumber>`.
- `ENGINEERING_EVENT`: `<category>`.
- `KNOWLEDGE_EVENT`: `<type>`.
- `DECISION`: `null`.
- `MILESTONE_COMPLETED`: `null`.

Bound chosen: per-source `PageRequest(0, 20)`, global limit `20`.

## Dependencies

- Story 0030 (projection conventions), Story 0033 (section conventions, `recentEvolution`), Story 0032 (quality gates).
- Entities/repositories for the five sources already exist.
- No new third-party dependency.

## Risks

1. **Decision `choice` size** (5000 chars) leaking into the timeline — mitigated by excluding it; only `title` is used.
2. **Duplication between KNOWLEDGE_EVENT / DECISION / ENGINEERING_EVENT semantics** — accepted for V1: each is a distinct, user-meaningful signal; ordering stays deterministic.
3. **Per-source bounding heuristic** could miss an old item when >20 newer items exist in another source — accepted and documented (predictable, bounded, not mass-tuned).
4. **Cross-source timestamp ties** — resolved deterministically by `type.name ASC`, then `id ASC` (no array-order dependence).

## Decisions for validation (resolved)

An ADR is **not** proposed: additive read-model projection with no new architecture, schema or integration seam.

1. **Sources V1**: include `STORY_COMPLETED`, `ENGINEERING_EVENT`, `KNOWLEDGE_EVENT`, `DECISION`, `MILESTONE_COMPLETED`; **exclude** commits and proposals. ✅ Approved
2. **Surface**: dedicated page `projects/:id/timeline` + sidebar entry "Timeline"; do **not** enrich the Overview. ✅ Approved
3. **Bounds**: 20 per source then 20 global. ✅ Approved
4. **Tie-break**: `timestamp DESC → type.name ASC → id ASC` (independent of enum declaration order). ✅ Approved
5. **Model**: common minimal `TimelineEntry` + `TimelineEntryType` enum (not a generic map). ✅ Approved

## Artifacts

- `repository-analysis.md`
- `implementation-plan.md`
- `implementation-report.md`
- `code-review.md`
- `engineering-report.md`