# Story 0033 — Project State v2: Recent Knowledge & Recent Evolution

## Status

Approved

## Priority

High

## Objective

Enrich the existing deterministic `ProjectState` projection (`GET /api/v1/projects/{id}/state`) with two new human-readable sections built exclusively from already-persisted, LLM-free data:

- **recentKnowledge** — the knowledge most recently acquired by the project, from `KnowledgeEvent`.
- **recentEvolution** — the important recent evolutions of the project, from validated `EngineeringEvent`.

This strengthens the Overview's answers to: *"Qu'avons-nous appris récemment ? Qu'est-ce qui vient de changer ?"*, without any AI call.

## Motivation

Story 0030 delivered the deterministic 5-section projection, but it deliberately drew on 8 repositories and **excluded two trusted knowledge sources**:

- `KnowledgeEvent` — the persisted knowledge layer (features, bugs, architecture, decisions…). Recent knowledge is currently invisible in the projection.
- `EngineeringEvent` — validated, immutable human-approved evolutions (the core "what changed" signal of DevLog).

Both entities and their **list-by-project queries already exist**. Adding them to the read model closes the gap between the ambition of the projection and the actual knowledge DevLog holds, while preserving the read-model principle: DevLog's domains remain the source of truth.

## Scope

### In Scope

1. **Backend — DTOs**
   - `KnowledgeSummary` inner DTO: `id`, `type` (`KnowledgeEventType`), `title`, `createdAt`.
   - `EvolutionSummary` inner DTO: `id`, `category` (`EngineeringEventCategory`), `title`, `baseCommit`, `targetCommit`, `occurredAt`.
   - `RecentKnowledgeSection` DTO: `List<KnowledgeSummary> recentKnowledge`.
   - `RecentEvolutionSection` DTO: `List<EvolutionSummary> recentEvolution`.

2. **Backend — projection**
   - `ProjectStateResponse` gains two fields: `recentKnowledge`, `recentEvolution`.
   - `ProjectStateProjectionServiceImpl` injects `KnowledgeEventRepository` and `EngineeringEventRepository`, builds the two sections, bounded by a fixed limit (proposal: 5 each).
   - `ProjectStateMapper` gains two mapping methods (entity → summary).

3. **Backend — tests**
   - Unit: `ProjectStateProjectionServiceTest` — populated and empty cases for both new sections.
   - Integration: `ProjectStateControllerWebMvcTest` — endpoint returns the new sections.
   - No new repository queries required (verified below).

4. **Frontend — models**
   - `project-state.models.ts`: add `KnowledgeSummary`, `EvolutionSummary`, `RecentKnowledgeSection`, `RecentEvolutionSection`; extend `ProjectState`.

5. **Frontend — Overview page**
   - `project-state-page.html`: two new panels/sections with clear headings (e.g. "What have we learned recently?" and "What recently changed?"), reusing the existing panel/item/empty-state styles.
   - Empty state placeholders when no items.
   - `project-state-page.spec.ts` updated for the new sections.

### Out of Scope

- Dashboard global (multi-project aggregation) — deliberately deferred; this story only improves the project Overview.
- `BLOCKED` / `CANCELLED` story lifecycle and any "in progress for X days = blocked" heuristic — explicitly excluded (separate business decision).
- Knowledge Graph / graph database / new persistence / caching — none introduced.
- Editing/writing via the projection.
- Adding new KnowledgeEvents or EngineeringEvents (the projection only reads them).

## Constraints

- **ProjectState stays a deterministic read model** of existing domains. No new source of truth, no business duplication.
- **No LLM** anywhere in the projection/display chain.
- **No N+1**: each new section uses a single repository query.
- **No new persistence** (no migration, no new table).
- Sources of truth remain: Engineering Stories, Engineering Events, Knowledge Events, decisions, milestones, proposals, Git history.
- Response stays fast (target < 100 ms, unchanged constraint from 0030).

## Impact

- **Backend**: extend `projectstate` package (Service, Mapper, DTOs) + 2 tests files. No `application`/`config` changes. No migration.
- **Frontend**: `project-state.models.ts`, `project-state-page.html`, `project-state-page.spec.ts`. No new route, no new service.
- **CI**: no change (covered by the existing backend `quality` job and frontend `frontend` job).
- **Tests**: backend +~4; frontend +~2-3 assertions/cases.

## Acceptance Criteria

### Backend
- AC-1: `GET /api/v1/projects/{id}/state` returns `recentKnowledge` (KnowledgeEvents most recent first, bounded) and `recentEvolution` (EngineeringEvents most recent by `occurredAt` first, bounded).
- AC-2: `recentKnowledge` items expose `id`, `type`, `title`, `createdAt`.
- AC-3: `recentEvolution` items expose `id`, `category`, `title`, `baseCommit`, `targetCommit`, `occurredAt`.
- AC-4: Empty project returns empty (not null) lists for both new sections.
- AC-5: Section limits applied (5 each); no N+1 (one query per section).
- AC-6: Existing 5 sections and behavior unchanged (backward compatible).
- AC-7: Endpoint still returns 404 for a non-existent project.
- AC-8: Unit tests cover populated + empty for both new sections.
- AC-9: Integration test asserts the new sections in the endpoint response.

### Frontend
- AC-10: `ProjectState` model includes both new sections.
- AC-11: Overview renders two new sections with clear headings.
- AC-12: Each new section shows an empty-state placeholder when no data.
- AC-13: No LLM call is added anywhere.
- AC-14: `project-state-page.spec.ts` covers the new sections (rendering + empty state).
- AC-15: Existing frontend tests still pass (37 spec files).

## Technical Context

Existing repository methods (verified, **no new query needed**):

- `KnowledgeEventRepository`:
  - `List<KnowledgeEvent> findByProjectIdOrderByCreatedAtDesc(UUID projectId)` ✅
- `EngineeringEventRepository`:
  - `List<EngineeringEvent> findRecentByProjectIdOrderByOccurredAtDescTargetCommitDescIdAsc(UUID projectId, Pageable pageable)` ✅

Existing entities (verified):

- `KnowledgeEvent`: `id`, `project`, `title`, `description`, `type` (`KnowledgeEventType`), `createdAt`, `updatedAt`.
- `EngineeringEvent`: `id`, `project`, `analysis`, `proposal`, `validation`, `source`, `category` (`EngineeringEventCategory`), `title`, `summary`, `significance`, `baseCommit`, `targetCommit`, `occurredAt`, `createdAt`.

Current projection assembly (verified):
- `ProjectStateProjectionServiceImpl` reads 8 repositories and produces 5 sections via `ProjectStateMapper`.
- `ProjectStateResponse` record currently has: `projectId`, `projectName`, `objective`, `activeWork`, `recentChanges`, `roadmapProgress`, `pendingActions`.

Note on `KnowledgeEvent` and "validated": unlike proposals, the `KnowledgeEvent` entity has **no status field**. It is the persisted/committed knowledge layer (arc/legacy occurrence layer per `docs/knowledge-model.md`), distinct from unvalidated proposals. The section therefore exposes the persisted `KnowledgeEvent`s for the project. **This interpretation requires confirmation (see "Decisions for validation").**

Out-of-scope per this story: enriching `RecentChangesSection` differently, or renaming existing sections. The two new sections are added as top-level fields.

## Dependencies

- Story 0030 (created the projection and the Overview).
- Entities/repositories delivered by earlier stories: Engineering Events (0022-0023), Challenge (0024), Knowledge relationships/wiring (0025-0026), Project Snapshot (0028), Story identity (0029).
- No new third-party dependency.

## Risks

1. **Interpretation of `KnowledgeEvent` trust level** — they have no status and may include "raw/manual" occurrences. Mitigation: render them as "recently learned" without claiming validation; confirm stance in "Decisions for validation".
2. **Adding fields to a public record** — consumers (frontend model, tests) must be updated in lockstep. Mitigation: update backend + frontend in the same change; existing sections unchanged (backward compatible).
3. **Performance** — two extra queries per projection; bounded lists keep it fast. Mitigation: limit sizes, verify < 100 ms in integration test.

## Decisions for validation (resolved)

An ADR is **not** proposed for this story: these are additive, evolutionary changes to an existing read model (two new mapped sections reusing existing repositories), with no new architecture, schema or integration seam. Decisions below are product/model-level confirmations:

1. **`KnowledgeEvent` trust level**: treated as the "recently acquired knowledge" shown to the human (UI wording "recently learned", not "validated") despite the absence of a validation status field. ✅ Approved
2. **Section placement**: `recentKnowledge` and `recentEvolution` added as two **new top-level sections**. ✅ Approved
3. **Limits**: each new section bounded to **5 items**. ✅ Approved
4. **`EvolutionSummary` content**: includes `baseCommit`/`targetCommit`. ✅ Approved

## Artifacts (after approval)

- `repository-analysis.md`
- `implementation-plan.md`
- `implementation-report.md`
- `code-review.md`
- `engineering-report.md`