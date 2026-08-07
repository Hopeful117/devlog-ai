# Story 0003 — Enable Repository Context Engine for Engineering Story Preparation

## Metadata

**ID:**
`0003`

**Title:**
Enable the Repository Context Engine for Engineering Story Preparation

**Status:**
Completed

**Created:**
2026-08-08

**Author:**
Kiko (OpenClaw)

---

## Objective

Unlock the existing Repository Context Engine for Engineering Story preparation, so that DevLog can provide Kiko with ranked, budgeted, diverse, explainable, and traceable repository evidence when preparing an Engineering Story — without requiring a persisted Analysis.

---

## Motivation

Stories 0001 and 0002 established `ProjectContextProvider` and an `EngineeringStoryContext` REST endpoint. These provide a raw `ProjectContextSnapshot` — all project-scoped data (knowledge events, decisions, artifacts, milestones, analyses) returned unconditionally.

However, the Repository Context Engine (`RepositoryContextEngine`) already implements a deterministic, multi-criteria evidence pipeline: collection → ranking → budget-aware selection → explainable digest. This pipeline is currently inaccessible for Engineering Story preparation because it requires a persisted `AnalysisContext` + `IntentDefinition` as input.

The result is that Kiko receives an undifferentiated dump of all project context when preparing a story, rather than ranked, relevant, budget-limited evidence. The existing deterministic pipeline is idle.

This story introduces an adapter that bridges `ProjectContextProvider` to `RepositoryContextEngine`, and a dedicated Context Profile (`engineering-story-v1`) that defines evidence priorities for story preparation. The existing Analysis flow remains completely unchanged.

---

## Scope

### In Scope

- New `engineering-story-v1` Context Profile in `DeterministicContextIntelligence`
- `RepositoryContextAdapter` service: bridges `ProjectContextProvider` → `RepositoryContextEngine`
- Extension of `EngineeringStoryContextService` to include `RepositoryContext` in its response
- Extension of `EngineeringStoryContext` record with `repositoryContext` field
- Controller endpoint updated to accept optional story description parameter
- Unit tests for adapter and profile registration

### Out of Scope

- Modifications to `RepositoryContextEngine` itself
- Modifications to `KnowledgeSelectionServiceImpl`
- Modifications to `AnalysisContextServiceImpl`
- New `RepositoryContextCollector` implementations
- Story-to-file mapping or module identification
- AI-based semantic relevance scoring
- Cross-story dependency tracking
- `IntentCatalog` changes (no new IntentDefinition)
- Frontend
- Database migrations
- Analysis persistence for story preparation

---

## Facts / Evidence

### Established (code exists)

- `RepositoryContextEngine.build(AnalysisContext, IntentDefinition, UserGuidance, List<Insight>)` is fully implemented with 5 collectors, multi-criteria ranking, budget-aware diverse selection, and SHA-256 digest.
- `DeterministicContextIntelligence` has 6 profiles (`project-state-v1`, `architecture-v1`, `history-v1`, `documentation-v1`, `release-v1`, `knowledge-extraction-v1`), all requiring an `AnalysisContext`.
- `ProjectContextProvider.build(UUID projectId)` returns `ProjectContextSnapshot` with: project, latestProjectProfile, recentKnowledgeEvents, validatedProposals, architectureArtifacts, relatedDecisions, recentMilestones, recentAnalyses.
- `EngineeringStoryContextService.build(UUID projectId)` returns `EngineeringStoryContext` wrapping `ProjectContextSnapshot`.
- `InsightRepository.findByProjectIdOrderByCreatedAtDesc(UUID projectId)` loads all validated insights by project (project-scoped).
- The 4 existing `RepositoryContextCollector` implementations all read from `ContextRequest.analysisContext()` — specifically the project ID, analysis timestamp, and snapshot lists.
- `CurrentAnalysisContextCollector` creates evidence from the current analysis — requires an analysis snapshot.
- `GitHistoryContextCollector` reads `ProjectCommitRepository` using only the project ID from the analysis context.
- `DeterministicKnowledgeContextCollector` reads facts and observations from the analysis context (may be empty for story preparation).
- `ProjectKnowledgeContextCollector` reads decisions, milestones, insights, related analyses, and artifacts from the analysis context.
- `DeterministicEvidenceRanker` uses 6 criteria (semantic, architectural, historical, recency, confidence, guidance) with profile-composed weights.
- `BudgetedDiverseEvidenceSelector` enforces item count budget (default 60), token budget (default 6000), and layer diversity.
- `EngineeringStoryContextController` currently returns `GET /api/projects/{projectId}/engineering-story-context`.

### Design Constraints

- `RepositoryContextEngine` expects `AnalysisContext` as input — the adapter must synthesize one from `ProjectContextSnapshot`.
- `AnalysisContext` requires an `AnalysisSnapshot` with an `analysisId` — the adapter generates a deterministic UUID (not persisted).
- `CurrentAnalysisContextCollector` will produce one evidence item for the synthetic analysis — acceptable overhead, naturally deprioritized by ranking.
- `DeterministicKnowledgeContextCollector` will produce empty lists for facts and observations (these are analysis-scoped by ADR-040) — acceptable for V1.
- The `recency` criterion in `DeterministicEvidenceRanker` uses `request.analysisContext().analysis().createdAt()` as the reference timestamp — the adapter sets this to `Instant.now()`, which is correct for story preparation (evidence recency is measured against "now").
- `KnowledgeSelectionServiceImpl` requires `AnalysisExecutionDiagnostic` (analysis-scoped) — the adapter bypasses this service entirely.
- The adapter calls `RepositoryContextEngine` directly through `RepositoryContextService` interface.

---

## Acceptance Criteria

### AC-1 : New `engineering-story-v1` Context Profile exists

**Evidence** `DeterministicContextIntelligence` contains a profile with key `"engineering-story-v1"`, enum value `ContextProfile` entry for engineering story preparation.

The profile must define:

| Property | Expected Value |
|---|---|
| Preferred layers | `GIT_HISTORY`, `COMMIT_DIFF`, `ADR`, `PROJECT_DOCUMENTATION`, `ROADMAP` |
| Minimum diverse layers | ≥ 2 |
| Evidence weights | Historical relevance ≥ 25, Confidence ≥ 25, Recency ≥ 15, Architectural relevance ≤ 15, Semantic relevance ≤ 15, Guidance ≤ 10 |

**Rationale:** Story preparation prioritizes what changed recently (git history, commits), what decisions exist (ADR/decisions), and what documentation is available. Architectural relevance and semantic matching are secondary — the story description drives relevance through guidance, not through profile weights.

### AC-2 : `RepositoryContextAdapter` exists and is injectable

**Evidence** `RepositoryContextAdapter` is annotated `@Service` with `@RequiredArgsConstructor`. It injects `ProjectContextProvider`, `RepositoryContextService`, `ContextIntelligence`, and `InsightRepository`.

### AC-3 : Adapter synthesizes `AnalysisContext` from `ProjectContextSnapshot`

**Evidence** `RepositoryContextAdapter.buildRepositoryContext(projectId, storyDescription)` calls `ProjectContextProvider.build(projectId)`, constructs a synthetic `AnalysisContext` containing:
- `ProjectSnapshot` from the provider's snapshot
- Synthetic `AnalysisSnapshot` with a deterministic UUID (UUID.nameUUIDFromBytes), type `ARCHITECTURE_REVIEW` (used for fallback profile selection), `createdAt = Instant.now()`, status `COMPLETED`
- Empty facts list (analysis-scoped, unavailable without Analysis)
- Empty observations list (analysis-scoped, unavailable without Analysis)
- `recentKnowledgeEvents` from the provider
- `relatedAnalyses` from the provider (all, no exclusion needed)
- `architectureArtifacts` from the provider
- `relatedDecisions` from the provider
- `recentMilestones` from the provider
- `validatedProposals` from the provider
- `projectProfile` = null (or latest from provider — acceptable either way)

### AC-4 : Adapter creates `IntentDefinition` for engineering story

**Evidence** The adapter creates an `IntentDefinition` with:
- `id` = `"engineering-story-preparation"`
- `version` = `"v1"`
- `objective` = the `storyDescription` parameter
- `supportedInsightTypes` = empty list (no AI interpretation)
- `constraints` = minimal (deterministic evidence only)
- `contextProfiles` = `List.of("engineering-story-v1")`

This `IntentDefinition` is not registered in `IntentCatalog` — it is created locally by the adapter. This avoids modifying the catalog for a use case that doesn't go through the Analysis workflow.

### AC-5 : Adapter calls `RepositoryContextEngine` directly

**Evidence** The adapter calls `repositoryContextService.build(syntheticAnalysisContext, intentDefinition, UserGuidance.from(...), validatedInsights)` where:
- `validatedInsights` are loaded from `InsightRepository.findByProjectIdOrderByCreatedAtDesc(projectId)`
- `UserGuidance` is derived from the `storyDescription` (or null/empty if no description provided)

The adapter does **not** call `KnowledgeSelectionServiceImpl`.

### AC-6 : `EngineeringStoryContext` includes `RepositoryContext`

**Evidence** `EngineeringStoryContext` record gains a new field `repositoryContext` of type `RepositoryContext` (nullable). The existing fields (`projectContext`, `generatedAt`, `projectId`) are unchanged.

### AC-7 : `EngineeringStoryContextService` populates `RepositoryContext`

**Evidence** `EngineeringStoryContextServiceImpl.buildWithRepositoryContext(projectId, storyDescription)` calls the adapter and wraps the result. A new method is added; the existing `build(projectId)` method continues to return `EngineeringStoryContext` with `repositoryContext = null`.

### AC-8 : Controller accepts optional description parameter

**Evidence** `GET /api/projects/{projectId}/engineering-story-context?description=X` passes the description to the service. The `description` parameter is `@RequestParam(required = false)`. When absent, `repositoryContext` is null (backward compatible).

### AC-9 : No `Analysis` is persisted

**Evidence** The adapter does not inject `AnalysisRepository`, `AnalysisService`, or any Analysis-creating service. The synthetic `AnalysisSnapshot` uses a generated UUID that is never stored.

### AC-10 : `ProjectContextProvider` independence is preserved

**Evidence** `ProjectContextProvider.build(projectId)` is unchanged. `AnalysisContextServiceImpl.build(analysisId)` is unchanged. All existing tests pass without modification.

### AC-11 : Existing Analysis flow is unchanged

**Evidence** `KnowledgeSelectionServiceImpl.select()` continues to work identically. No modifications to `KnowledgeSelectionServiceImpl`, `RepositoryContextEngine`, `DeterministicContextIntelligence` (existing profiles), or any existing collector.

### AC-12 : Deterministic output

**Evidence** For the same `projectId`, `storyDescription`, and persisted data, the `RepositoryContext` is identical across calls. The synthetic analysis UUID is deterministic (based on `projectId`). The `Instant.now()` timestamp for the synthetic analysis affects recency scoring but is acceptable — evidence recency is measured relative to "now".

### AC-13 : Tests pass

**Evidence** New unit tests for `RepositoryContextAdapter` (nominal case, empty project, project with no insights) and `DeterministicContextIntelligence` (profile registration) pass. Existing tests unaffected.

---

## Impacted Components

### New Files

| File | Type | Package |
|---|---|---|
| `RepositoryContextAdapter.java` | Service | `repositorycontext` |
| `RepositoryContextAdapterTest.java` | Test | `repositorycontext` |

### Modified Files

| File | Nature of Modification |
|---|---|
| `EngineeringStoryContext.java` | Add `repositoryContext` field (nullable) |
| `EngineeringStoryContextService.java` | Add `buildWithRepositoryContext(UUID projectId, String storyDescription)` method |
| `EngineeringStoryContextServiceImpl.java` | Implement new method, inject `RepositoryContextAdapter` |
| `EngineeringStoryContextController.java` | Add `@RequestParam(required = false) String description` to endpoint |
| `DeterministicContextIntelligence.java` | Register `engineering-story-v1` profile |
| `ContextProfile.java` | Add `ENGINEERING_STORY` enum value |

### Unchanged Files

| File | Reason |
|---|---|
| `RepositoryContextEngine.java` | Consumed as-is through `RepositoryContextService` interface |
| `KnowledgeSelectionServiceImpl.java` | Bypassed by adapter — no modifications |
| `AnalysisContextServiceImpl.java` | Existing Analysis flow untouched |
| `ProjectContextProvider.java` | Independence preserved |
| `ProjectContextProviderImpl.java` | No changes |
| `IntentCatalog.java` | No new IntentDefinition registered |
| All existing collectors | No modifications |
| `DeterministicEvidenceRanker.java` | No modifications |
| `BudgetedDiverseEvidenceSelector.java` | No modifications |

---

## Risks

### Risk-1 : Synthetic `AnalysisContext` carries a fake analysis ID

**Impact:** Low. The analysis ID is used as a reference in evidence provenance (`CurrentAnalysisContextCollector` creates one evidence item with `analysis:<fakeId>`). This evidence is naturally deprioritized by ranking because it has no meaningful summary. The fake ID is never persisted.

**Mitigation:** The synthetic ID is deterministic (`UUID.nameUUIDFromBytes(projectId bytes)`), ensuring reproducibility. The adapter docstrings clearly state this is a synthetic context.

### Risk-2 : Recency scoring uses `Instant.now()` as reference

**Impact:** Low. The `recency` criterion in `DeterministicEvidenceRanker` measures evidence age relative to `analysis.createdAt`. For story preparation, this means "how recent is the evidence relative to now" — which is the correct semantic. Evidence from the last 7 days scores 100, last 30 days scores 80, etc.

**Mitigation:** This is the intended behavior for story preparation. No mitigation needed.

### Risk-3 : `DeterministicKnowledgeContextCollector` produces empty facts/observations

**Impact:** Low. Facts and observations are analysis-scoped by ADR-040. For story preparation without a persisted Analysis, these are unavailable. The collector produces empty lists, which is handled gracefully by the engine.

**Mitigation:** Accepted for V1. Future stories may introduce project-scoped knowledge extraction if needed.

### Risk-4 : `CurrentAnalysisContextCollector` produces one artificial evidence item

**Impact:** Low. This collector creates evidence from the synthetic analysis. The evidence summary is the intent objective (story description), which may be relevant. The ranking will score it based on the profile weights — for `engineering-story-v1`, this evidence layer is not in the preferred layers, so it receives low architectural relevance (20) and low historical relevance (20).

**Mitigation:** Acceptable overhead (1 item). Could be addressed in V2 by making the collector conditional.

### Risk-5 : `IntentDefinition` created locally by adapter, not in catalog

**Impact:** Low. The adapter creates a local `IntentDefinition` for the engine. This is not registered in `IntentCatalog`, which means it won't appear in `IntentCatalog.all()`. This is intentional — the engineering story preparation flow is separate from the Analysis workflow.

**Mitigation:** If catalog registration is needed later, it can be added in a follow-up story.

---

## Architecture Notes

### Why bypass `KnowledgeSelectionServiceImpl`

`KnowledgeSelectionServiceImpl` orchestrates a broader workflow than what story preparation needs:

1. It requires `AnalysisExecutionDiagnostic` — which only exists for persisted Analyses
2. It loads facts, observations, insights, and repository context
3. It computes a `SelectedKnowledge` digest

For story preparation, we need only the repository context evidence. The adapter calls `RepositoryContextEngine.build()` directly, which is the lower-level service that produces `RepositoryContext`. This avoids:
- Requiring a persisted Analysis for diagnostics
- Loading facts/observations (unavailable without Analysis)
- Computing a `SelectedKnowledge` digest (not needed for story preparation)

The existing `KnowledgeSelectionServiceImpl` path remains completely untouched.

### Why a synthetic `AnalysisContext` rather than changing `RepositoryContextEngine` interface

`RepositoryContextEngine` is consumed by `KnowledgeSelectionServiceImpl` (and potentially other services). Changing its interface to accept something other than `AnalysisContext` would affect the existing Analysis flow. The adapter pattern preserves the engine's interface while providing a clean entry point for story preparation.

The synthetic `AnalysisContext` is a thin wrapper around `ProjectContextSnapshot` data. It carries:
- The real project data (from the provider)
- A synthetic analysis snapshot (for the engine's internal use)
- Empty facts/observations (analysis-scoped, unavailable)

This is sufficient for the 4 collectors to produce meaningful evidence.

### Why `engineering-story-v1` profile is separate from existing profiles

The 6 existing profiles are designed for Analysis workflows with specific intents:
- `project-state-v1`: for `describe-project` intent
- `architecture-v1`: for `architecture-overview` intent
- `history-v1`: for historical analysis
- `documentation-v1`: for `generate-readme` intent
- `release-v1`: for release summaries
- `knowledge-extraction-v1`: for knowledge extraction

Story preparation has different priorities: recent changes, existing decisions, project documentation, and roadmap. No existing profile matches these priorities. A dedicated profile ensures evidence is selected and weighted appropriately.

### Why `IntentDefinition` is not registered in `IntentCatalog`

`IntentCatalog` is used by the Analysis workflow to resolve intents for persisted Analyses. The engineering story preparation flow does not create an Analysis. Registering the intent in the catalog would:
- Conflate two distinct use cases (Analysis vs. story preparation)
- Require the catalog to support intents that don't go through `AnalysisWorkflowService`
- Create confusion about which intents require Analysis persistence

The adapter creates a local `IntentDefinition` that carries only the information the engine needs: objective, context profiles, and constraints.

---

## Definition of Done

- [ ] `ContextProfile.ENGINEERING_STORY` enum value added
- [ ] `engineering-story-v1` profile registered in `DeterministicContextIntelligence`
- [ ] `RepositoryContextAdapter` created and annotated `@Service`
- [ ] `RepositoryContextAdapter` injects `ProjectContextProvider`, `RepositoryContextService`, `ContextIntelligence`, `InsightRepository`
- [ ] `RepositoryContextAdapter.buildRepositoryContext(projectId, storyDescription)` synthesizes `AnalysisContext` and calls engine
- [ ] `EngineeringStoryContext` record extended with `repositoryContext` field
- [ ] `EngineeringStoryContextService` extended with `buildWithRepositoryContext` method
- [ ] `EngineeringStoryContextServiceImpl` implements new method using adapter
- [ ] `EngineeringStoryContextController` accepts optional `description` parameter
- [ ] `RepositoryContextAdapterTest` created and passes
- [ ] `DeterministicContextIntelligenceTest` updated for new profile
- [ ] `mvn compile` succeeds
- [ ] `mvn test` — all existing tests pass, new tests pass
- [ ] No modifications to `RepositoryContextEngine`, `KnowledgeSelectionServiceImpl`, `AnalysisContextServiceImpl`, `IntentCatalog`
- [ ] No `Analysis` entity persisted during story preparation flow

---

## Dependencies

- **Story 0001** (completed): `ProjectContextProvider` — provides project-scoped context without Analysis
- **Story 0002** (completed): `EngineeringStoryContext` — provides the endpoint and record to extend
- **`RepositoryContextEngine`** (existing): The evidence pipeline to unlock
- **`DeterministicContextIntelligence`** (existing): Where the new profile is registered
- **`InsightRepository`** (existing): For loading validated insights by project

---

*Story created: 2026-08-08*
*Author: Kiko (OpenClaw)*
