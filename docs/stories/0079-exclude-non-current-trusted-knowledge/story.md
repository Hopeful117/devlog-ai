# Engineering Story 0079 — Exclude Non-Current Trusted Knowledge from Deterministic Engineering Context

## Status

Prepared / Proposed.

## Priority

High.

## Context

The Temporal Knowledge Readiness investigation
(`docs/investigations/temporal-knowledge-readiness.md`) identified a concrete
context-correctness gap in the current trusted-knowledge retrieval paths.

The existing domain provides an authoritative `InsightStatus` lifecycle:

```text
InsightStatus:
  ACTIVE
  ARCHIVED
  SUPERSEDED
```

Migration `V42` persisted `Insights.status`, and the "Knowledge section" already
filters `SUPERSEDED` out (Story 0074). Both `InsightServiceImpl#getByProject` and
`InsightPromotionService` already treat **ACTIVE-only** as the current set via
`findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, List.of(ACTIVE))`.

However, two deterministic context-assembly paths still load project Insights
**without** filtering their semantic status:

1. `KnowledgeSelectionServiceImpl#select`
2. `RepositoryContextAdapter#buildRepositoryContext`

As a result, `ARCHIVED` and `SUPERSEDED` trusted Insights can still be exposed to:

- AI prompts / selected knowledge
- deterministic knowledge selection
- Repository Context (`RepositoryEvidence` via `ProjectKnowledgeContextCollector`)
- Engineering Story preparation
- downstream Engineering Context / MCP consumers

This Story is therefore **not** a Temporal Knowledge subsystem. It is a small,
deterministic correctness fix that consumes the existing authoritative
`InsightStatus` semantics to exclude non-current trusted Insights from *current*
engineering context, while actively preserving historical knowledge.

## Problem Statement

Current deterministic engineering context does not consistently respect the
existing `InsightStatus` semantics: `ARCHIVED` and `SUPERSEDED` trusted Insights
may still be included in current context.

## Repository Analysis

See `repository-analysis.md` for the full analysis.

Summary:

- **Status semantics:** `ACTIVE` = current; `ARCHIVED` = excluded; `SUPERSEDED`
  = excluded from current context. Established by `InsightServiceImpl#getByProject`
  and `InsightPromotionService` (both ACTIVE-only), and Story 0074 (SUPERSEDED
  filtered from Knowledge view).
- **Affected paths (non-current trusted Insights currently leak):**
  - `KnowledgeSelectionServiceImpl` (loads via `findByProjectIdOrderByCreatedAtDesc`)
  - `RepositoryContextAdapter` (loads via `findByProjectIdOrderByCreatedAtDesc`)
- **Repository query precedent:** `findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc`
  already exists and is the ACTIVE-only query used by `getByProject` /
  `InsightPromotionService`. It preserves `createdAt DESC, id DESC` ordering.
- **Human Context contrast:** `ProjectContextProviderImpl` already queries
  `ProjectHumanContextInputStatus.ACTIVE` only — Human Context is correctly
  current-status filtered already and is **not** part of this Story.
- **Historical knowledge:** filtering at repository/query time reduces the
  current-context result set only; `ARCHIVED` / `SUPERSEDED` Insights remain
  persisted and retrievable through existing query methods and `findById`, so
  future explicit historical/evolution retrieval remains possible.

## Architectural Constraints

- Deterministic correction only.
- Existing authoritative `InsightStatus` semantics only.
- No deletion of historical knowledge.
- No new Temporal Knowledge model / no new status enum.
- No stale-detection mutations / no detector may change authority.
- No ranking / selection redesign.
- No MCP-specific fix.
- No RAG / vector / Event Sourcing.
- Preserve ADR-006 authority model (accepted proposal → trusted Insight).
- Preserve ADR-058 lineage.
- No migration, no entity change, no backfill.

## Proposed Responsibility

Change the trusted-Insight load sites in the two current-context assembly paths
to reuse the existing ACTIVE-only repository query:

- `KnowledgeSelectionServiceImpl` — use
  `insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, List.of(InsightStatus.ACTIVE))`.
- `RepositoryContextAdapter` — same query, same status set.

No new repository method is required; the existing
`findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc` expresses the intended
semantics and ordering. No shared abstraction is introduced merely to avoid
repeating one enum value.

## Proposed Behavior

Current context:

```text
ACTIVE      → INCLUDE_CURRENT
ARCHIVED    → EXCLUDE_CURRENT
SUPERSEDED  → EXCLUDE_CURRENT
```

Historical knowledge:

```text
retained, but not selected implicitly
```

A future explicit historical/evolution context may intentionally include
`SUPERSEDED` / `ARCHIVED` (documented, deferred).

## Acceptance Criteria

- Current trusted-knowledge selection includes ACTIVE Insights.
- ARCHIVED Insights do not enter current deterministic selection.
- SUPERSEDED Insights do not enter current deterministic selection.
- `RepositoryContextAdapter` follows the same current-state semantics.
- Non-current Insights remain persisted.
- No silent fallback to archived/superseded knowledge.
- Deterministic ordering remains stable (`createdAt DESC, id DESC`).
- No migration / entity change.
- No ranking / selection redesign.
- Existing Human Context behavior is unchanged.
- Existing Insight service behavior (`getByProject`, promotion, audit) is unchanged.

## Test Strategy

Focused deterministic tests proving behavior (not mocked method names) for both
affected paths. See `repository-analysis.md` (§ Test Strategy) for the full list.
Prefer parameterized status cases to avoid matrix duplication.

## Risks

- Accidentally hiding knowledge needed by historical use cases (mitigated: rows
  remain persisted; only current-context retrieval is filtered).
- Inconsistent current-status semantics across consumers (mitigated: reuse the
  established ACTIVE-only precedent, no new ad-hoc status sets).
- Future need for explicit historical retrieval (documented as deferred; not
  implemented here).
- Prompt/context behavior changes (BEFORE has A/B/C; AFTER only A) — covered by
  tests.
- Overly broad repository-query replacement (we only touch the two load sites;
  other consumers classified in `repository-analysis.md`; no broad sweep).

## Dependencies

- Existing `InsightStatus` (migration V42).
- Existing `InsightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc`.
- Temporal Knowledge Readiness investigation.
- ADR-006 (authority model).
- ADR-058 (lineage; current `Data Lineage` / knowledge promotion context).
- Current `KnowledgeSelectionServiceImpl` / `RepositoryContextAdapter` /
  `ProjectKnowledgeContextCollector` architecture.

## Explicitly Out of Scope

- Temporal Knowledge ADR.
- New semantic-state enum / stale candidate model / `SUSPECTED_STALE`.
- `effectiveFrom` / `effectiveUntil` / `lastConfirmedAt` / transition timestamps.
- Supersession redesign (supersede relation stays `RESOLVES`).
- Historical query mode.
- Lifecycle repair (e.g. SUPERSEDED-with-no-successor heuristic).
- Lineage Phase 2 implementation.
- Ranking tuning.
- MCP changes.
- Retrieval Layer / RAG / vector database.
- Event Sourcing.
- Human Context redesign.
- Changes to `TrustedKnowledgeDuplicateGuard` / `DeliverableServiceImpl` /
  other consumers (classified in `repository-analysis.md`; external to the two
  current-context paths in this Story).

## Implementation Plan — DRAFT ONLY

> **NOT APPROVED — DO NOT IMPLEMENT**

1. In `KnowledgeSelectionServiceImpl`, replace
   `insightRepository.findByProjectIdOrderByCreatedAtDesc(context.project().id())`
   with
   `insightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(context.project().id(), List.of(InsightStatus.ACTIVE))`
   (add `List.of` import usage as needed).
2. In `RepositoryContextAdapter`, replace
   `insightRepository.findByProjectIdOrderByCreatedAtDesc(projectId)` with the
   same ACTIVE-only query.
3. Add focused deterministic service-level tests (both paths, ACTIVE/ARCHIVED/
   SUPERSEDED/empty) using parameterized status cases.
4. Run the backend suite; confirm no unrelated regression.

## Verification Plan

- Unit/integration tests prove ARCHIVED and SUPERSEDED Insights do not enter
  `KnowledgeSelectionServiceImpl#select` output and
  `RepositoryContextAdapter#buildRepositoryContext` RepositoryEvidence.
- ACTIVE Insights still appear; empty-ACTIVE result does not fall back.
- Existing tests for `InsightServiceImpl` / `InsightPromotionService` /
  Human Context remain green (behaviour unchanged).

## Future Follow-up

1. Benchmark current Engineering Context / MCP selection after Story 0079.
2. Lineage Phase 2 investigation (Trusted Knowledge → … → MCP).
3. ADR-059 Temporal Knowledge Semantics (current vs historical context, effective
   time, supersession).
4. Explicit historical/evolution context behavior (retrieve ARCHIVED /
   SUPERSEDED intentionally).