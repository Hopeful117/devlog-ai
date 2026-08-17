# Engineering Story 0079 — Repository Analysis

## 1. MCP Evidence Assessment

Queried `get_engineering_context(projectSlug="devlog-ai", intent="...Story 0079...")`.

| Evidence | Layer | Classification |
|---|---|---|
| Commit `0da15c7` "fix(#0074): dismiss without comment + filter SUPERSEDED from Knowledge view" | GIT_HISTORY | **DIRECTLY_RELEVANT** — precedent: `getByProject` switched to ACTIVE-only (SUPERSEDED no longer shown) |
| Commit `b000127` "fix: add Flyway migration for insights.status" | GIT_HISTORY | **DIRECTLY_RELEVANT** — migration V42 (`insights.status`) |
| `DeterministicKnowledgeContextCollector` / `ProjectKnowledgeContextCollector` | RELATED_SOURCE_CODE | **DIRECTLY_RELEVANT** — `ProjectKnowledgeContextCollector` turns `validatedInsights` into RepositoryEvidence |
| Story 0069 / 0074 / 0072 context-maintenance docs | ROADMAP/COMMIT_DIFF | **USEFUL_BACKGROUND** — dedup/remediation; SUPERSEDED set by duplicate resolution |
| ADR-006 (authority), ADR-058 (lineage), MCP-as-adapter note | ADR | **USEFUL_BACKGROUND** — governance constraints |
| Engineering Context Engine vision, future RAG note | GOAL | **USEFUL_BACKGROUND** — deterministic boundary + historical context intent |
| Generic Spring Boot / Docker validated Insights | VALIDATED_INSIGHT | **NOISE** |
| Exact location of other unfiltered current-context consumers | — | **MISSING** from MCP; verified via repository grep |

> MCP is discovery only; repository is implementation truth. MCP surfaced the
> critical Story-0074 precedent and the `ProjectKnowledgeContextCollector`, both
> verified against code.

## 2. Domain Semantics — `InsightStatus`

`InsightStatus` enum (`insight/entity/InsightStatus.java`), column added by
migration `V42__add_status_column_to_insights.sql`, default `ACTIVE`.

| State | Semantics (repository evidence) | Current-context classification |
|---|---|---|
| `ACTIVE` | Current trusted Insight. Used as the only status in `getByProject` (ACTIVE) and `InsightPromotionService` similarity scan (ACTIVE). | **INCLUDE_CURRENT** |
| `ARCHIVED` | Excluded signal — the row remains persisted; set by `archiveInsight`. | **EXCLUDE_CURRENT** |
| `SUPERSEDED` | Excluded signal — duplicate-resolution remediation marks an Insight superseded (Story 0072/0074), creating an `INSIGHT→INSIGHT` `RESOLVES` relation to the canonical insight. `getByProject` filters it out (Story 0074). | **EXCLUDE_CURRENT** |

**Question A — Current context semantics:** Yes — current engineering-context
generation should include only `ACTIVE` Insights. This matches the established
domain precedent in `InsightServiceImpl#getByProject` (lines 54–63) and
`InsightPromotionService` (lines 64–67), both ACTIVE-only via
`findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc`.

## 3. Historical Knowledge Preservation (Question B)

Filtering happens at **retrieval/query time**. The repository retains all rows.
Non-current Insights remain reachable by existing methods
(`findByProjectIdOrderByCreatedAtDesc`, `findByProposalIdIn`, `findById`, etc.),
so future explicit historical/evolution retrieval remains possible.

**CURRENT CONTEXT = ACTIVE only; HISTORICAL/EVOLUTION = may include SUPERSEDED/**
ARCHIVED later.** This Story implements only the current-context side; historical
retrieval is intentionally deferred.

## 4. Earliest Correct Filter Boundary (Question C)

| Boundary | Feasible | Verdict |
|---|---|---|
| A. `InsightRepository` query | Yes | **PREFERRED** — earliest authoritative deterministic boundary; prevents staleness from flowing downstream |
| B. `KnowledgeSelectionServiceImpl` | yes via its query call | Filter is realised at the query invocation in this service |
| C. `RepositoryContextAdapter` | yes via its query call | Filter is realised at the query invocation in this adapter |
| D. RepositoryEvidence ranking | No | Rejects using ranking penalties to repair an upstream correctness issue |
| E. MCP | No | Too late; also violates MCP-as-thin-adapter principle |
| F. Other | — | — |

Recommended: close the gap at the **repository query invocation** in the two load
sites, reusing the existing ACTIVE-only query method. This is the earliest point
where the two deterministic paths obtain their trusted Insight lists.

## 5. Repository Query Design (Question D)

Reuse the **existing** `InsightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc(projectId, Collection<InsightStatus>)`
— already used by `getByProject` and `InsightPromotionService`.

- Ordering `createdAt DESC, id DESC` is stable and compatible with current
  selection behaviour.
- **Do not** add a duplicate repository method.
- `findByProjectIdOrderByCreatedAtDesc` (unfiltered) is retained for other /
  historical consumers and is not removed.

## 6. Shared Current-Status Definition (Question E)

**Do not** introduce a shared constant/abstraction for Story 0079. `ACTIVE` at
both call sites (`List.of(InsightStatus.ACTIVE)`) is the clear, minimal,
domain-meaningful expression and exactly mirrors the established
`getByProject` precedent. No demonstrable multi-consumer reuse requires a new
constant. A shared `CURRENT_TRUSTED_INSIGHT_STATUSES` could be considered in a
future consolidation Story if a third divergent consumer appears.

## 7. Affected Paths (Question F)

**Path 1 — AI selection:**
`InsightRepository` → `KnowledgeSelectionServiceImpl#select`
(`findByProjectIdOrderByCreatedAtDesc`, line 68) → `insightCandidates`
→ `toInsight` (prompt) / `selectExistingArchitectureKnowledge` /
`repositoryContextService.build(..., validatedInsights)` (line 79).

**Path 2 — Story preparation / Deterministic Knowledge:**
`InsightRepository` → `RepositoryContextAdapter#buildRepositoryContext`
(`findByProjectIdOrderByCreatedAtDesc`, line 60) → `repositoryContextService.build`
→ `ProjectKnowledgeContextCollector` (line 48 `request.validatedInsights()`) →
`RepositoryEvidence` → Engineering Story / Engineering Context.

Filtering at both load sites with the ACTIVE-only query closes both gaps.

## 8. Other Consumers of Unfiltered Insight Retrieval (Question G)

Found by grep of `findByProjectIdOrderByCreatedAtDesc`, `...IdDesc`, and status-in query:

| Consumer | Location | Status filter today | Classification |
|---|---|---|---|
| `KnowledgeSelectionServiceImpl#select` (validatedInsights) | `KnowledgeSelectionServiceImpl.java:68` | none | **MUST_FIX_IN_STORY_0079** |
| `RepositoryContextAdapter#buildRepositoryContext` | `RepositoryContextAdapter.java:60` | none | **MUST_FIX_IN_STORY_0079** |
| `InsightServiceImpl#getByProject` | `InsightServiceImpl.java:58` | ACTIVE | **ALREADY_SAFE** |
| `InsightPromotionService` (similarity scan) | `InsightPromotionService.java:64` | ACTIVE | **ALREADY_SAFE** |
| `TrustedKnowledgeDuplicateAuditService#audit` | `TrustedKnowledgeDuplicateAuditService.java:23` | ACTIVE + ARCHIVED | **OUT_OF_SCOPE** (purposeful: dedupe audit reviews currently-known + archived; distinct domain objective) |
| `TrustedKnowledgeDuplicateGuard#assertCanAccept` | `TrustedKnowledgeDuplicateGuard.java:29` | none | **OUT_OF_SCOPE** (validation-time dedup guard over all present Insights; not current-context assembly) |
| `DeliverableServiceImpl` | `DeliverableServiceImpl.java:46,48` | none | **OUT_OF_SCOPE** (deliverable generation; separate AI-output feature, not in the two current-context paths; candidate for a future Story) |
| Other `findByProjectIdOrderByCreatedAtDesc` hits (Artifact, Challenge, Decision, KnowledgeEvent/Relation, Analysis, Source, Timeline, ProjectState, Maintenance) | various | — | **OUT_OF_SCOPE** (non-Insight repositories or projection/history consumers) |

Only the two `MUST_FIX_IN_STORY_0079` consumers are changed. No broad sweep.

## 9. Human Context Comparison (Question H)

Confirmed: `ProjectContextProviderImpl#build` loads Human Context only with
`findByProject_IdAndStatusOrderByUpdatedAtDescIdDesc(projectId, ProjectHumanContextInputStatus.ACTIVE)`
(lines 155–164). Human Context is already current-status filtered.

Contrast:

```text
Human Context      → already current-status filtered (ACTIVE only)
Trusted Insight    → current gap (unfiltered in two deterministic paths)
```

Human Context is **not** part of Story 0079.

## 10. Authority vs Freshness (Question I)

Preserved. This Story consumes the existing authoritative `InsightStatus`
produced by the ADR-006 acceptance/promotion workflow and duplicate-resolution
remediation. It must **not** introduce `SUSPECTED_STALE`, `effectiveFrom`,
`effectiveUntil`, `lastConfirmedAt`, or new authority transitions. No detector
mutates authority. Authority (accepted proposal → trusted Insight) and
current-state (`ACTIVE` vs `ARCHIVED`/`SUPERSEDED`) remain orthogonal.

## 11. SUPERSEDED Without Successor (Question J)

Engineered behaviour: a SUPERSEDED Insight is excluded from current context
because **status is authoritative** (`InsightServiceImpl#supersedeInsight`
transition + story-0072 remediation). The hypothetical missing-successor case is
handled consistently:

- **A. still exclude** because `SUPERSEDED` is authoritative — **RECOMMENDED**.
- B. include because successor unavailable — rejected (reintroduces
  non-current knowledge implicitly via heuristic recovery).
- C. report diagnostic but still exclude — a future Temporal-Knowledge
  concern; out of scope here.
- D. other — n/a.

No heuristic recovery; no lifecycle repair in this Story. Recorded for
ADR-059 Temporal Knowledge Semantics.

## 12. Fallback Behaviour (Question K)

If filtering yields zero ACTIVE Insights, the current code continues normally
with empty Insight knowledge:

- `KnowledgeSelectionServiceImpl` — `insightCandidates` empty → empty insights /
  empty existing-architecture knowledge; no fallback logic.
- `RepositoryContextAdapter` — `validatedInsights` empty → `ProjectKnowledgeContextCollector`
  emits no Insight evidence; Repository Context builds fine.

No silent fallback to archived/superseded; no historical fallback added.

## 13. Context Quality Impact (Question L)

| | Before | After |
|---|---|---|
| Current context Insights | ACTIVE A + ARCHIVED B + SUPERSEDED C may all enter | only ACTIVE A |
| Historical knowledge | persisted | still persisted (B, C retrievable via existing methods / future historical capability) |
| Ordering | (createdAt-based) | `createdAt DESC, id DESC` — stable, compatible |
| Token budget / ranking / diversity | non-current Insights can consume budget and rank | only ACTIVE candidates, so selection is not diluted by non-current knowledge |

No ranking tuning in this Story. Secondary effect: when non-current Insights
previously entered context, they are now removed, freeing candidate/token budget
and removing stale signal — an intended improvement.

## 14. Test Strategy (Question M)

Minimum high-value deterministic tests (prefer parameterized status cases):

1. `KnowledgeSelectionServiceImpl` includes ACTIVE Insight.
2. `KnowledgeSelectionServiceImpl` excludes ARCHIVED Insight.
3. `KnowledgeSelectionServiceImpl` excludes SUPERSEDED Insight.
4. `RepositoryContextAdapter` includes ACTIVE Insight.
5. `RepositoryContextAdapter` excludes ARCHIVED Insight.
6. `RepositoryContextAdapter` excludes SUPERSEDED Insight.
7. Empty ACTIVE result does not fall back to non-current Insights.
8. Ordering of ACTIVE Insights remains deterministic (`createdAt DESC, id DESC`).
9. Existing `InsightServiceImpl#getByProject` / promotion / audit behaviour unchanged
   (guard against regression).
10. Human Context filtering unaffected.

Target the service/repository boundary to prove behaviour (which Insights actually
enter `select` output / RepositoryContext evidence), not just that a method was
called with a status.

## 15. Data / Migration Impact (Question N)

**None.** No migration, no entity change, no backfill, no new temporal fields.
The filtering is purely at query invocation in two services. `insights.status`
(column from migration V42) already exists.

## 16. Temporal Knowledge Boundary (Question O)

Story 0079 is a **current-context correctness fix using existing authoritative
`InsightStatus` semantics**. It is **not**:

- Temporal Knowledge V1
- semantic-state redesign
- effective-time modelling (`effectiveFrom`/`effectiveUntil`)
- supersession redesign
- transition timestamps
- Event Sourcing
- Retrieval/RAG

Intentionally deferred to ADR-059 / future Temporal Knowledge work:

- current vs historical/evolution context split
- effective time semantics
- `SUSPECTED_STALE` + detector-authority separation
- supersession relation semantics (`SUPERSEDES` vs current `RESOLVES`)
- transition timestamps (`supersededAt`/`archivedAt`)
- historical query mode