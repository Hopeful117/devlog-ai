# Repository Analysis

## Story Understanding

Story 0003 requests enabling the existing Repository Context Engine (`RepositoryContextEngine`) for Engineering Story preparation. Currently, `EngineeringStoryContextService` returns a raw `ProjectContextSnapshot` — all project-scoped data returned unconditionally. The Repository Context Engine already implements deterministic evidence collection, multi-criteria ranking, budget-aware selection, and explainable digests, but is inaccessible for story preparation because it requires a persisted `AnalysisContext` + `IntentDefinition`.

The Story introduces an adapter that bridges `ProjectContextProvider` to `RepositoryContextEngine`, and a dedicated Context Profile (`engineering-story-v1`) that defines evidence priorities for story preparation. No Analysis is persisted. The existing Analysis workflow is completely unchanged.

Explicit scope: adapter service, context profile, controller endpoint extension, unit tests.
Explicit exclusions: modifications to `RepositoryContextEngine`, `KnowledgeSelectionServiceImpl`, `AnalysisContextServiceImpl`, `IntentCatalog`, new collectors, AI interpretation, file/module identification.

---

## Repository Summary

DevLog AI is a Java/Spring Boot backend with a Python/FastAPI AI engine. The backend implements a knowledge pipeline: Git → Facts → Observations → Project Profile → Context → Proposals → Insights → Deliverables.

The repository context subsystem lives in `com.hopeful117.devlogai.repositorycontext`:

- `RepositoryContextEngine` — orchestrates collection → ranking → selection → digest
- `DeterministicContextIntelligence` — versioned profiles that define evidence priorities
- 4 collectors: `CurrentAnalysisContextCollector`, `DeterministicKnowledgeContextCollector`, `GitHistoryContextCollector`, `ProjectKnowledgeContextCollector`
- `DeterministicEvidenceRanker` — multi-criteria ranking (6 criteria)
- `BudgetedDiverseEvidenceSelector` — budget + diversity enforcement

The project context subsystem lives in `com.hopeful117.devlogai.projectcontext`:

- `ProjectContextProvider` — builds `ProjectContextSnapshot` from 8 repositories (project-scoped, no Analysis dependency)
- `EngineeringStoryContextService` — wraps `ProjectContextSnapshot` in `EngineeringStoryContext`
- `EngineeringStoryContextController` — REST endpoint

---

## Affected Modules

### 1. `repositorycontext.intelligence` — Context Intelligence

**Package:** `com.hopeful117.devlogai.repositorycontext.intelligence`

**Why involved:** The new `engineering-story-v1` profile must be registered in `DeterministicContextIntelligence.profiles()`. The `ContextProfile` enum needs a new `ENGINEERING_STORY` value.

**Current responsibility:** Contains 6 predefined, versioned profiles that determine evidence collection strategies. Each profile defines: preferred layers, minimum diverse layers, evidence criterion weights, and token priority.

**Impact:** One new profile added to the static `profiles()` map. One new enum value in `ContextProfile`. No existing profiles modified.

### 2. `projectcontext` — Engineering Story Context

**Package:** `com.hopeful117.devlogai.projectcontext`

**Why involved:** The adapter service (`RepositoryContextAdapter`) lives here. `EngineeringStoryContext` and `EngineeringStoryContextService` are extended.

**Current responsibility:** `ProjectContextProvider` builds project-scoped snapshots. `EngineeringStoryContextService` wraps them for Kiko.

**Impact:** New adapter service. Extended record. Extended service interface and implementation. Extended controller.

### 3. `repositorycontext` — Repository Context Engine

**Package:** `com.hopeful117.devlogai.repositorycontext`

**Why involved:** The adapter calls `RepositoryContextService.build()` directly. No modifications to the engine itself.

**Current responsibility:** Orchestrates evidence collection, ranking, selection, and digest.

**Impact:** None — consumed as-is through the existing interface.

---

## Existing Implementation

### Repository Context Engine (`RepositoryContextEngine`)

**File:** `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java`

The engine is fully implemented:

```java
public RepositoryContext build(
    AnalysisContext context,
    IntentDefinition intent,
    UserGuidance guidance,
    List<Insight> validatedInsights
)
```

Internal flow:
1. `contextIntelligence.plan(context, intent)` → `ContextPlan`
2. For each collector: `collector.collect(request)` → `List<RepositoryEvidence>`
3. `ranker.rank(candidates, request)` → ranked evidence
4. `selector.select(ranked, request)` → selected evidence + decisions
5. Compute SHA-256 digest
6. Return `RepositoryContext`

The engine injects: `List<RepositoryContextCollector>`, `ContextIntelligence`, `EvidenceRanker`, `EvidenceSelector`, `ObjectMapper`.

**Key observation:** The engine creates a `ContextRequest` internally and passes it to collectors. The `ContextRequest` wraps `AnalysisContext`, `IntentDefinition`, `UserGuidance`, validated insights, the `ContextPlan`, and budget. The adapter must provide a synthesizable `AnalysisContext`.

### Collectors — What They Read from AnalysisContext

| Collector | Reads from AnalysisContext | Would work with synthetic context? |
|---|---|---|
| `CurrentAnalysisContextCollector` | `analysis().id()`, `analysis().createdAt()`, `intent().objective()` | Yes — creates 1 evidence item from the synthetic analysis |
| `DeterministicKnowledgeContextCollector` | `facts()`, `observations()` | Yes — empty lists are handled gracefully |
| `GitHistoryContextCollector` | `project().id()` | Yes — only needs the project ID |
| `ProjectKnowledgeContextCollector` | `relatedDecisions()`, `recentMilestones()`, `relatedAnalyses()`, `architectureArtifacts()` | Yes — reads from provider's snapshot data |

All 4 collectors work with a synthetic `AnalysisContext` that carries project-scoped data from `ProjectContextSnapshot`.

### Context Intelligence (`DeterministicContextIntelligence`)

**File:** `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/intelligence/DeterministicContextIntelligence.java`

6 profiles registered in `profiles()` method. Each profile is a static `ContextProfileDefinition` with:
- key (e.g., `"architecture-v1"`)
- `ContextProfile` enum value
- version (`"v1"`)
- criterion weights (6 values: semantic, architecture, history, recency, confidence, guidance)
- preferred layers (ordered list of `RepositoryContextLayer`)
- minimum diverse layers (int)
- token priority (int)

The `plan()` method resolves profiles by key from the `IntentDefinition.contextProfiles()` list.

### Evidence Ranking (`DeterministicEvidenceRanker`)

**File:** `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/ranking/DeterministicEvidenceRanker.java`

6 criteria scored per evidence item:
- `SEMANTIC_RELEVANCE`: matches intent ID + objective against evidence kind + summary + originating file
- `ARCHITECTURAL_RELEVANCE`: based on layer (ADR=100, SOURCE_CODE=80, etc.) + keyword boost
- `HISTORICAL_RELEVANCE`: based on layer (GIT_HISTORY=100, ROADMAP=85, etc.)
- `RECENCY`: days between evidence timestamp and analysis creation (≤7d=100, ≤30d=80, etc.)
- `CONFIDENCE`: based on provenance source type (GIT=100, DETERMINISTIC=95, etc.)
- `USER_GUIDANCE_BOOST`: keyword match between guidance and evidence summary

Final score is weighted average using profile-composed weights.

### Budget Selection (`BudgetedDiverseEvidenceSelector`)

**File:** `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/selection/BudgetedDiverseEvidenceSelector.java`

Enforces:
- Maximum evidence items (default: 60, configurable via `devlog.repository-context.max-evidence-items`)
- Maximum tokens (default: 6000, configurable via `devlog.repository-context.max-tokens`)
- Layer diversity: ensures minimum N layers are represented before filling by rank

### EngineeringStoryContext (current)

**File:** `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContext.java`

```java
public record EngineeringStoryContext(
    ProjectContextSnapshot projectContext,
    Instant generatedAt,
    UUID projectId
) {}
```

Currently contains only the raw snapshot. No repository evidence.

### EngineeringStoryContextService (current)

**File:** `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextServiceImpl.java`

```java
@Service
@RequiredArgsConstructor
public class EngineeringStoryContextServiceImpl implements EngineeringStoryContextService {
    private final ProjectContextProvider projectContextProvider;

    @Override
    public EngineeringStoryContext build(UUID projectId) {
        ProjectContextSnapshot snapshot = projectContextProvider.build(projectId);
        return new EngineeringStoryContext(snapshot, Instant.now(), projectId);
    }
}
```

Single method, no repository context. The adapter will be injected alongside `ProjectContextProvider`.

### EngineeringStoryContextController (current)

**File:** `backend/src/main/java/com/hopeful117/devlogai/projectcontext/EngineeringStoryContextController.java`

```java
@GetMapping("/api/projects/{projectId}/engineering-story-context")
public ResponseEntity<EngineeringStoryContext> getEngineeringStoryContext(
        @PathVariable UUID projectId) {
    return ResponseEntity.ok(engineeringStoryContextService.build(projectId));
}
```

No optional parameters. The `description` parameter will be added.

### InsightRepository

**File:** `backend/src/main/java/com/hopeful117/devlogai/insight/repository/InsightRepository.java`

Has `findByProjectIdOrderByCreatedAtDesc(UUID projectId)` — returns all validated insights for a project. The adapter will use this to supply `validatedInsights` to the engine.

### Default Budget Configuration

**File:** `backend/src/main/resources/application.properties`

```
devlog.repository-context.max-evidence-items=60
devlog.repository-context.max-summary-characters=500
devlog.repository-context.max-history-items=20
devlog.repository-context.max-tokens=6000
```

These defaults will be used by the adapter through the engine's existing configuration.

### Existing Tests

| Test | Location | Coverage |
|---|---|---|
| `DeterministicContextIntelligenceTest` | `repositorycontext/intelligence/` | Profile resolution, fallback, weight composition |
| `RepositoryContextServiceTest` | `repositorycontext/` | Full engine pipeline: collection, ranking, budget, digest |
| `ProjectContextProviderTest` | `projectcontext/` | Provider data assembly, pagination, immutability |
| `EngineeringStoryContextServiceTest` | `projectcontext/` | Service delegation to provider |

---

## Relevant Documentation

- `docs/decisions/ADR-037.md` — Repository-First Context Extraction
- `docs/decisions/ADR-038.md` — Repository Context Engine
- `docs/decisions/ADR-039.md` — Context Intelligence (profiles, composition, scoring)
- `docs/decisions/ADR-040.md` — Knowledge and Evidence Separation (facts/observations are analysis-scoped)
- `docs/stories/0001-extract-project-context-provider/story.md` — ProjectContextProvider design
- `docs/stories/0002-expose-engineering-story-context/0002-engineering-story.md` — EngineeringStoryContext design
- `docs/architecture.md` — Core principles (Knowledge First, AI as Capability, Human in Loop)
- `docs/roadmap.md` — V1 development phases

---

## Constraints

1. **ADR-037 (Repository-First):** Repository context must be assembled through the Repository Context Engine pipeline. The adapter uses the existing engine — no parallel pipeline.

2. **ADR-039 (Context Intelligence):** Evidence selection uses versioned Context Profiles. The new profile follows this pattern exactly.

3. **ADR-040 (Knowledge/Evidence Separation):** Facts and observations are analysis-scoped. The adapter synthesizes empty lists for these — acceptable for V1.

4. **`ProjectContextProvider` independence:** The provider is not modified. The adapter consumes `ProjectContextSnapshot` without altering the provider.

5. **Existing Analysis flow unchanged:** `AnalysisContextServiceImpl`, `KnowledgeSelectionServiceImpl`, and `IntentCatalog` are not modified.

6. **No Analysis persistence:** The adapter does not create, persist, or require a database `Analysis` entity.

7. **Deterministic output:** Same inputs → same `RepositoryContext`. The synthetic analysis UUID is deterministic (based on `projectId`).

8. **Backward compatibility:** `EngineeringStoryContext` gains a nullable `repositoryContext` field. Existing clients that don't use the field are unaffected (Jackson ignores unknown fields by default).

---

## Risks

### Risk-1 : Synthetic AnalysisContext carries a fake analysis ID

The adapter creates an `AnalysisSnapshot` with `UUID.nameUUIDFromBytes(projectId.toString().getBytes())`. This ID is never persisted. `CurrentAnalysisContextCollector` creates one evidence item with `analysis:<fakeId>` as reference. The ranking will score this evidence based on profile weights — for `engineering-story-v1`, CURRENT_ANALYSIS layer is not preferred, so it receives low architectural relevance (20) and low historical relevance (20).

**Impact:** Low. One additional evidence item in the collection, naturally deprioritized.

### Risk-2 : Recency scoring uses Instant.now() as reference

`DeterministicEvidenceRanker.recency()` uses `request.analysisContext().analysis().createdAt()` as the reference point. The adapter sets this to `Instant.now()`. This means evidence recency is measured relative to "now" — which is semantically correct for story preparation.

**Impact:** Low. This is the intended behavior.

### Risk-3 : DeterministicKnowledgeContextCollector produces empty facts/observations

Facts and observations are analysis-scoped (ADR-040). Without a persisted Analysis, these lists are empty. The collector handles this gracefully.

**Impact:** Low for V1. Historical extracted knowledge is unavailable. This is a known limitation documented in the story.

### Risk-4 : No existing test for the adapter path

The adapter is a new service. No existing test covers the flow from `EngineeringStoryContextService` through the adapter to `RepositoryContextEngine`.

**Impact:** Medium. Mitigated by the new unit tests required by AC-13.

### Risk-5 : IntentDefinition created locally, not in catalog

The adapter creates a local `IntentDefinition` for `engineering-story-preparation`. This is not registered in `IntentCatalog.all()`, which means it won't appear in catalog listings.

**Impact:** Low. The catalog is for Analysis workflow intents. Story preparation is a separate flow.

---

## Open Questions

None. The Story is well-defined, the existing implementation is clear, and the adapter pattern is straightforward. All necessary repositories, services, and interfaces exist and are functional.

---

## Recommendation

**Ready for planning**

The repository is sufficiently understood. The adapter pattern is clean — it bridges two existing, tested subsystems (`ProjectContextProvider` and `RepositoryContextEngine`) without modifying either. All 4 collectors work with a synthetic `AnalysisContext`. The new profile follows the established pattern exactly. No architectural conflicts exist.

---

## Implementation Readiness

The Story can be implemented using the current repository:

- `RepositoryContextEngine` is fully functional and tested
- `DeterministicContextIntelligence` supports adding new profiles via the `profiles()` method
- `ProjectContextProvider` provides all data needed for the synthetic `AnalysisContext`
- `InsightRepository` has `findByProjectIdOrderByCreatedAtDesc` for loading validated insights
- `EngineeringStoryContext` record can be extended with a nullable field
- `EngineeringStoryContextController` can accept an optional `@RequestParam`
- Default budget configuration exists in `application.properties`

No missing contracts, no missing ownership, no missing architecture, no blocking ADR conflicts, no missing technical prerequisites.

---

Repository Analysis completed.

Human approval required before Implementation Planning.

Awaiting explicit human approval.
