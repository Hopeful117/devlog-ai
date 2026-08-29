# Story 0102 — Implementation Plan

## Status

**IMPLEMENTATION_PLAN_CORRECTED_FOR_HUMAN_REVIEW**

## Corrections Applied

1. **Regression A RED/GREEN strategy corrected**: RED tests now prove regressions against CURRENT broken code FIRST (test-first). Backend generic-launch contract tests identified as missing and added to the plan.
2. **Backend generic-launch contract tests identified**: `AnalysisServiceTest` and `AnalysisControllerWebMvcTest` have **zero** coverage for type consistency validation. Tests added to plan.
3. **EvidenceItem semantic mapping re-evaluated**: All 8 category mapping tables updated with repository-derived semantics. Field meanings confirmed against collector code, frontend rendering, and MCP contract.
4. **READ_MODEL_CONTRACT_GAP risk assessed**: All 8 categories can be faithfully represented using the `layer`/`kind`/`reference`/`summary` field model. No gaps detected.

## Purpose

Restore the Analysis product to a functional state by fixing two P0 regressions:

1. **Regression A**: Story 0100 accidentally reverted the Story 0099 Angular launch contract, reintroducing the AnalysisType selector that Story 0099 intentionally removed.
2. **Regression B**: Unsafe type casting in `AnalysisResultQueryServiceImpl.curateCategory()` causes `DatabindException` when serializing evidence items to JSON.

## Governing Contracts

- **ADR-006**: proposals remain untrusted until individual human validation
- **Story 0099**: generic launch contract (AnalysisType not human-selectable, objective-driven)
- **Story 0100**: canonical Analysis Result semantics
- **Story 0101**: trusted-artifact navigation

## Regression A — Restore Story 0099 Launch Contract

### Problem Statement

Story 0100 accidentally reverted the Story 0099 Angular launch implementation. The current frontend exposes:
- An AnalysisType selector with `['ARCHITECTURE_REVIEW', 'PROJECT_EVOLUTION']`
- An Intent selector (not objective-based)
- No scope derivation from objective
- No source/revision controls for repository-scoped objectives

Story 0099 established that:
- AnalysisType is NOT human-selectable
- Generic launch is engineering-objective driven
- All four V1 objectives derive `ARCHITECTURE_REVIEW`
- Scope is fixed by objective (PROJECT_SCOPE or REPOSITORY_SCOPE)

### Files to Modify

| File | Change |
|---|---|
| `frontend/src/app/features/analyses/analysis-form.ts` | Restore objective-based form from Story 0099 |
| `frontend/src/app/features/analyses/analysis-form.html` | Restore objective-based template from Story 0099 |
| `frontend/src/app/features/analyses/analysis-form.spec.ts` | Restore Story 0099 tests |
| `frontend/src/app/features/analyses/analysis.models.ts` | Remove `LaunchableAnalysisType` (keep `AnalysisType` for persistence) |
| `frontend/src/app/features/analyses/project-analyses-section.ts` | Restore `objectives$`, `sources$`, `combined$` from Story 0099 |
| `frontend/src/app/features/analyses/project-analyses-section.html` | Restore objective-based form binding from Story 0099 |
| `frontend/src/app/features/analyses/project-analyses-section.spec.ts` | Restore Story 0099 tests with `SourceService` mock |

### Restoration Strategy

**NOT a blind `git revert` of Story 0100.** Story 0100 introduced valid canonical-result functionality that must remain intact.

Selective restoration:
1. Restore `AnalysisForm` to Story 0099 state (objective-based, no type selector)
2. Restore `ProjectAnalysesSection` to Story 0099 state (objectives$, sources$, combined$)
3. Restore `analysis.models.ts` to Story 0099 state (remove `LaunchableAnalysisType`, keep `Source`)
4. Preserve Story 0100/0101 analysis result page, routing, and models

### Pre-existing Human Modification

`frontend/src/app/features/analyses/project-analyses-section.html` has a pre-existing formatting change (indentation only, no functional changes). This is a human-owned modification that must be preserved. The Story 0099 restoration will overwrite the template content but the formatting change is purely cosmetic and can be re-applied after.

### RED Tests (Against Current Broken Code — Test-First)

**Frontend (RED against current broken implementation):**
- `analysis-form.spec.ts`: Current tests pass because they use `intentKey` and expect `type: 'ARCHITECTURE_REVIEW'` — these tests PROVE the broken contract is in place. New RED tests must be added first that assert the correct Story 0099 contract:
  - RED test: `should emit intentId without type field when objective is selected` — FAILS because current form emits `type`
  - RED test: `should not expose AnalysisType selector` — FAILS because current form has type selector
- `project-analyses-section.spec.ts`: Current tests pass without `SourceService` mock — proves the broken contract. New RED test: `should provide objectives and sources to analysis form` — FAILS because current section doesn't provide sources.

**Backend (generic-launch contract — NOT tested, needs new tests):**
- `AnalysisServiceTest`: Currently has **zero** tests for type consistency validation. Add RED tests:
  - `shouldRejectMismatchedTypeOnCreate()` — sends `PROJECT_EVOLUTION`, asserts `IllegalArgumentException` with message "Provided AnalysisType does not match derived type for generic launch"
  - `shouldDeriveArchitectureReviewWhenTypeIsOmitted()` — sends `type=null`, asserts saved entity has `ARCHITECTURE_REVIEW`
- `AnalysisControllerWebMvcTest`: Currently has **zero** tests for type rejection at endpoint level. Add RED test:
  - `shouldRejectProjectEvolutionTypeOnCreate()` — sends `POST /api/v1/analyses` with `"type":"PROJECT_EVOLUTION"`, asserts HTTP 400

### Expected RED State

**Regression A — Frontend (before any fix):**
- New RED test in `analysis-form.spec.ts` FAILS because current form emits `type` field
- New RED test in `project-analyses-section.spec.ts` FAILS because current section doesn't provide sources

**Regression A — Backend (new tests, before any fix):**
- `shouldRejectMismatchedTypeOnCreate()` PASSES (backend is already correct)
- `shouldDeriveArchitectureReviewWhenTypeIsOmitted()` PASSES (backend is already correct)
- `shouldRejectProjectEvolutionTypeOnCreate()` PASSES (backend is already correct)
- These tests document the existing backend contract and prevent future regressions

## Regression B — Explicit Evidence Projection

### Problem Statement

`AnalysisResultQueryServiceImpl.curateCategory()` performs an unsafe cast:
```java
List<AnalysisResultResponse.EvidenceItem> evidenceItems =
    (List<AnalysisResultResponse.EvidenceItem>) items;
```

The actual list contains typed items (`FactItem`, `ObservationItem`, etc.) that are NOT `EvidenceItem`. The cast succeeds at Java level due to type erasure but fails during Jackson serialization.

### Target EvidenceItem

```java
public record EvidenceItem(
    String layer,
    String kind,
    String reference,
    String summary,
    Instant occurredAt,
    List<String> relatedReferences
) {}
```

### EvidenceItem Field Semantics (Repository-Derived)

Before defining mappings, here are the repository-derived semantics for each `EvidenceItem` field:

| Field | Semantic Meaning | Frontend Rendering |
|---|---|---|
| `layer` | **Provenance domain** — the source collector family that produced the evidence (e.g., `COMMIT_DIFF`, `RELATED_SOURCE_CODE`, `ADR`) | Not rendered in canonical result page; shown as subtitle in selected evidence detail |
| `kind` | **Artifact type label** — the specific artifact type within its layer (e.g., `DECISION`, `COMMIT`, `SOURCE_FILE`) | Rendered as **bold heading** (`<strong>`) |
| `reference` | **Opaque identity key** — structured identifier following `{type}:{uuid}` format (e.g., `decision:{uuid}`, `git:{sourceId}:{sha}`) | Rendered as `<code>` (monospace) |
| `summary` | **Bounded factual description** — human-readable content, budget-bounded to 500 chars | Rendered as `<p>` (main body text) |
| `occurredAt` | **Event timestamp** — when the evidence was created/detected | Not rendered in canonical result page |
| `relatedReferences` | **Raw provenance links** — structured identifiers pointing to parent/derived artifacts | Not rendered in canonical result page |

### Source Item Types and Semantic Mapping

#### 1. FactItem

```java
public record FactItem(
    UUID id,
    String type,
    String content,
    String source,
    List<String> evidenceReferences,
    Instant detectedAt
) {}
```

| Source Field | Target Field | Semantic Rationale | Fallback |
|---|---|---|---|
| `"FACT"` | `layer` | Constant — facts are a distinct provenance domain | `"FACT"` |
| `type` | `kind` | The fact type identifies the specific artifact (e.g., `"ARCHITECTURAL"`, `"CODE_SMELL"`) | `"fact"` |
| `"fact:" + id` | `reference` | Structured identity key following `{type}:{uuid}` format | `null` |
| `content` | `summary` | Direct — fact description is human-readable | `""` |
| `detectedAt` | `occurredAt` | Direct — when the fact was detected | `null` |
| `evidenceReferences` | `relatedReferences` | Direct — raw provenance links to source evidence | `List.of()` |

#### 2. ObservationItem

```java
public record ObservationItem(
    UUID id,
    String type,
    String content,
    String ruleId,
    String ruleVersion,
    List<UUID> supportingFactIds,
    Instant createdAt
) {}
```

| Source Field | Target Field | Semantic Rationale | Fallback |
|---|---|---|---|
| `"OBSERVATION"` | `layer` | Constant — observations are a distinct provenance domain | `"OBSERVATION"` |
| `ruleId` | `kind` | The rule ID identifies the specific observation type | `"observation"` |
| `"observation:" + id` | `reference` | Structured identity key following `{type}:{uuid}` format | `null` |
| `content` | `summary` | Direct — observation description is human-readable | `""` |
| `createdAt` | `occurredAt` | Direct — when the observation was created | `null` |
| `supportingFactIds.stream().map(id -> "fact:" + id).toList()` | `relatedReferences` | Transformed to structured provenance links | `List.of()` |

#### 3. PriorInsightItem

```java
public record PriorInsightItem(
    String type,
    String severity,
    String title,
    String content
) {}
```

| Source Field | Target Field | Semantic Rationale | Fallback |
|---|---|---|---|
| `"VALIDATED_INSIGHT"` | `layer` | Matches existing `RepositoryContextLayer` enum value for validated insights | `"VALIDATED_INSIGHT"` |
| `severity` | `kind` | The severity identifies the insight's classification | `"insight"` |
| `type` | `reference` | The insight type is the identity key (e.g., `"ARCHITECTURE"`, `"PATTERN"`) | `null` |
| `title` | `summary` | Direct — insight title is human-readable | `""` |
| `null` | `occurredAt` | N/A — no timestamp available | `null` |
| `null` | `relatedReferences` | N/A — standalone artifacts | `List.of()` |

#### 4. ArchitectureKnowledgeItem

```java
public record ArchitectureKnowledgeItem(
    UUID insightId,
    UUID proposalId,
    String normalizedType,
    String severity,
    String sourceType,
    String title,
    String content,
    String rationale,
    List<String> evidenceReferences,
    Instant createdAt
) {}
```

| Source Field | Target Field | Semantic Rationale | Fallback |
|---|---|---|---|
| `"VALIDATED_INSIGHT"` | `layer` | Matches existing `RepositoryContextLayer` enum value for validated insights | `"VALIDATED_INSIGHT"` |
| `normalizedType` | `kind` | The normalized type identifies the specific architecture knowledge type | `"architecture"` |
| `"insight:" + insightId` | `reference` | Structured identity key following `{type}:{uuid}` format | `null` |
| `title` | `summary` | Direct — insight title is human-readable | `""` |
| `createdAt` | `occurredAt` | Direct — when the knowledge was created | `null` |
| `evidenceReferences` | `relatedReferences` | Direct — raw provenance links to source evidence | `List.of()` |

#### 5. EngineeringEventItem

```java
public record EngineeringEventItem(
    UUID id,
    String category,
    String title,
    String summary,
    UUID sourceId,
    String baseCommit,
    String targetCommit,
    Instant occurredAt,
    UUID proposalId
) {}
```

| Source Field | Target Field | Semantic Rationale | Fallback |
|---|---|---|---|
| `"COMMIT_DIFF"` | `layer` | Engineering events produce commit diffs — matches existing `RepositoryContextLayer` enum value | `"COMMIT_DIFF"` |
| `category` | `kind` | The event category identifies the specific artifact type (e.g., `"FEATURE"`, `"REFACTOR"`) | `"engineering-event"` |
| `"event:" + id` | `reference` | Structured identity key following `{type}:{uuid}` format | `null` |
| `title` | `summary` | Direct — event title is human-readable | `""` |
| `occurredAt` | `occurredAt` | Direct — when the event occurred | `null` |
| `"git:" + sourceId + ":" + baseCommit`<br>`"git:" + sourceId + ":" + targetCommit` | `relatedReferences` | Structured provenance links to git commits (same format as `GitHistoryContextCollector`) | `List.of()` |

#### 6. HumanContextItem

```java
public record HumanContextItem(
    UUID id,
    String type,
    String title,
    String contentMarkdown,
    String status,
    Instant updatedAt
) {}
```

| Source Field | Target Field | Semantic Rationale | Fallback |
|---|---|---|---|
| `"PROJECT_DOCUMENTATION"` | `layer` | Matches existing `RepositoryContextLayer` enum value for human-authored project documentation | `"PROJECT_DOCUMENTATION"` |
| `type` | `kind` | The type identifies the specific human context artifact (e.g., `"ADR"`, `"DECISION"`) | `"human-context"` |
| `"human:" + id` | `reference` | Structured identity key following `{type}:{uuid}` format | `null` |
| `title` | `summary` | Direct — human context title is human-readable | `""` |
| `updatedAt` | `occurredAt` | Direct — when the context was last updated | `null` |
| `null` | `relatedReferences` | N/A — standalone artifacts | `List.of()` |

#### 7. EvolutionContextItem

```java
public record EvolutionContextItem(
    String contextVersion,
    UUID projectId,
    UUID sourceId,
    String baseCommit,
    String targetCommit,
    String comparisonPolicy,
    Boolean mergeCommit,
    Instant targetCommittedAt,
    CommitDiff commitDiff
) {}
```

| Source Field | Target Field | Semantic Rationale | Fallback |
|---|---|---|---|
| `"COMMIT_DIFF"` | `layer` | Evolution context produces commit diffs — matches existing `RepositoryContextLayer` enum value | `"COMMIT_DIFF"` |
| `comparisonPolicy` | `kind` | The comparison policy identifies the evolution type (e.g., `"FULL"`, `"INCREMENTAL"`) | `"evolution"` |
| `"evolution:" + sourceId` | `reference` | Structured identity key following `{type}:{uuid}` format | `null` |
| `commitDiff?.commitMessage` | `summary` | Direct — commit message is human-readable | `""` |
| `targetCommittedAt` | `occurredAt` | Direct — when the target commit was created | `null` |
| `commitDiff?.evidenceReferences` | `relatedReferences` | Direct — raw provenance links from commit diff | `List.of()` |

#### 8. RepositoryEvidenceItem

```java
public record RepositoryEvidenceItem(
    String layer,
    String kind,
    String reference,
    String summary,
    Instant occurredAt,
    List<String> relatedReferences,
    RepositoryContent content,
    RepositorySymbols symbols
) {}
```

| Source Field | Target Field | Semantic Rationale | Fallback |
|---|---|---|---|
| `layer` | `layer` | Direct — already uses `RepositoryContextLayer` enum values | `"REPOSITORY"` |
| `kind` | `kind` | Direct — already identifies the specific artifact type | `"repository"` |
| `reference` | `reference` | Direct — already follows structured identity key format | `null` |
| `summary` | `summary` | Direct — already human-readable | `""` |
| `occurredAt` | `occurredAt` | Direct — already when the evidence was created | `null` |
| `relatedReferences` | `relatedReferences` | Direct — already raw provenance links | `List.of()` |

**Note**: `RepositoryEvidenceItem` already has the same field names as `EvidenceItem` but is a different Java type. The mapping is a direct field copy. The `content` and `symbols` fields are not projected into `EvidenceItem` (they are only available in the selected evidence detail view).

### Implementation Strategy

Replace the generic `curateCategory(Availability, int, List<?>)` method with eight dedicated mapping methods, one per evidence category. Each method:
1. Takes the typed section (e.g., `FactsSection`)
2. Returns `EvidenceCategorySection` with correctly mapped `EvidenceItem` instances
3. Applies the semantic mapping defined in the EvidenceItem Field Semantics section above
4. Preserves the existing `EVIDENCE_PREVIEW_LIMIT = 5` curation

The eight typed overloads already exist (each destructures its section and delegates to the terminal overload). The fix replaces the terminal overload's unsafe cast with explicit field-by-field construction of `EvidenceItem` from each typed item.

### Files to Modify

| File | Change |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/analysis/result/service/AnalysisResultQueryServiceImpl.java` | Replace terminal `curateCategory` overload's unsafe cast with eight typed mapping methods |
| `backend/src/test/java/com/hopeful117/devlogai/analysis/result/service/AnalysisResultQueryServiceImplTest.java` | Add 8 category RED tests + 1 JSON serialization regression test + evidence preview limit test |

### RED Tests (Before Fix — Test-First)

**Service-level evidence composition tests** (`AnalysisResultQueryServiceImplTest`):

The existing test class mocks `AiTaskSelectedEvidenceService` but never returns `State.AVAILABLE`, so `curateCategory()` is never reached. All eight RED tests must be added here.

Each test follows the same pattern:
1. Mock `selectedEvidenceService.getSelectedEvidence(analysisId)` to return `AiTaskSelectedEvidenceResponse.available(...)` with a populated typed section containing realistic items
2. Mock remaining dependencies (analysis repository, task repository, proposal repository, etc.) to return minimal valid fixtures
3. Call `service.getResult(analysisId)` on the real `AnalysisResultQueryServiceImpl`
4. Assert the resulting `AnalysisResultResponse` contains an `EvidenceCategorySection` whose items are real `EvidenceItem` instances with the expected semantic projection

**Do NOT assert `ClassCastException`** — the unchecked cast survives due to Java generic type erasure. Assert desired product behavior instead.

For each category, assert all six semantic mapping fields:

| Category | Typed Section | Realistic Item Type | Key Assertions |
|---|---|---|---|
| `facts` | `FactsSection` | `FactItem` | `layer == "FACT"`, `kind == item.type`, `reference == "fact:" + id`, `summary == item.content`, `occurredAt == item.detectedAt`, `relatedReferences == item.evidenceReferences` |
| `observations` | `ObservationsSection` | `ObservationItem` | `layer == "OBSERVATION"`, `kind == item.ruleId`, `reference == "observation:" + id`, `summary == item.content`, `occurredAt == item.createdAt`, `relatedReferences` mapped from `supportingFactIds` |
| `priorInsights` | `PriorInsightsSection` | `PriorInsightItem` | `layer == "VALIDATED_INSIGHT"`, `kind == item.severity`, `reference == item.type`, `summary == item.content`, `occurredAt == null`, `relatedReferences == List.of()` |
| `architectureKnowledge` | `ArchitectureKnowledgeSection` | `ArchitectureKnowledgeItem` | `layer == "VALIDATED_INSIGHT"`, `kind == item.normalizedType`, `reference == "insight:" + insightId`, `summary == item.title`, `occurredAt == item.createdAt`, `relatedReferences == item.evidenceReferences` |
| `engineeringEvents` | `EngineeringEventsSection` | `EngineeringEventItem` | `layer == "COMMIT_DIFF"`, `kind == item.category`, `reference == "event:" + id`, `summary == item.title`, `occurredAt == item.occurredAt`, `relatedReferences` contains git refs for base/target commits |
| `humanContext` | `HumanContextSection` | `HumanContextItem` | `layer == "PROJECT_DOCUMENTATION"`, `kind == item.type`, `reference == "human:" + id`, `summary == item.title`, `occurredAt == item.updatedAt`, `relatedReferences == List.of()` |
| `evolutionContext` | `EvolutionContextSection` | `EvolutionContextItem` | `layer == "COMMIT_DIFF"`, `kind == item.comparisonPolicy`, `reference == "evolution:" + sourceId`, `summary == commitDiff.commitMessage`, `occurredAt == item.targetCommittedAt`, `relatedReferences` from commitDiff |
| `repositoryEvidence` | `RepositoryEvidenceSection` | `RepositoryEvidenceItem` | Direct field copy — all six fields match source item |

**Additional RED tests:**
- `getResultShouldReturnEmptyEvidenceWhenSelectedEvidenceNotAvailable()` — verifies `State.NO_AI_TASK` returns `emptyEvidence()`
- `getResultShouldRespectEvidencePreviewLimit()` — verifies only 5 items are curated even when section contains more

### Expected RED State (Service-Level)

After adding tests but before fixing `curateCategory`:

The unchecked cast `(List<EvidenceItem>) items` does NOT throw `ClassCastException` due to type erasure. Instead, the tests will observe RED for the actual runtime reason: the resulting `EvidenceItem` objects will have **all null fields** because Jackson deserialization or field access on a `FactItem` masquerading as `EvidenceItem` produces null values for `layer`, `kind`, `reference`, `summary`, `occurredAt`, `relatedReferences`. The assertions on expected field values will fail.

### JSON Serialization Regression Test (Known FACT Failure)

**Service + Jackson serialization boundary** — `AnalysisResultQueryServiceImplTest`:

The existing `AnalysisControllerWebMvcTest` mocks `AnalysisResultQueryService` completely, so it cannot exercise composition. The controller is a thin pass-through (`return ResponseEntity.ok(analysisResultQueryService.getResult(id))`). Testing at the WebMvc boundary would require either wiring a real `AnalysisResultQueryServiceImpl` with mocked repositories (non-standard for the existing test architecture) or using a full Spring context test (heavyweight and fragile).

Therefore, the integration-level serialization test is placed at the **service + Jackson** boundary, which is the closest boundary that exercises canonical result composition without bypassing `curateCategory()`.

Test: `shouldSerializeCanonicalResultWithFactItemsViaJackson()`
1. Mock `selectedEvidenceService.getSelectedEvidence(analysisId)` to return `State.AVAILABLE` with a `FactsSection` containing 3 realistic `FactItem` instances
2. Mock remaining dependencies to return minimal valid fixtures
3. Call `service.getResult(analysisId)` — this exercises `buildEvidence()` → `curateCategory()` → the unsafe cast
4. Serialize the resulting `AnalysisResultResponse` to JSON via `new ObjectMapper().writeValueAsString(response)`
5. Assert: no `DatabindException` is thrown
6. Assert: deserialized JSON contains `evidenceCategories.facts.items` with 3 entries
7. Assert: each entry has non-null `layer`, `kind`, `reference`, `summary` fields

This test reproduces the real current failure path: typed `FactItem` → unsafe cast → `EvidenceItem` → Jackson serialization. After the production fix, the same test becomes GREEN.

## Cross-Story Regression Verification

### Story 0099

- Objective-driven generic launch restored
- No editable AnalysisType in UI
- Fixed objective scope
- Executable Intent mapping
- Legacy type compatibility validation

### Story 0100

- Canonical `/analyses/{id}/result` unchanged
- Result projection unchanged (except evidence mapping fix)
- Result states unchanged
- Evidence curation (top-5 limit) preserved
- Angular result page unchanged
- Polling behavior unchanged

### Story 0101

- Trusted artifact resolution unchanged
- AVAILABLE / UNAVAILABLE semantics unchanged
- Navigation to trusted artifacts unchanged
- Decision provenance behavior unchanged

## Quality Gates

### Frontend

- All tests pass
- Lint clean
- Production build successful
- Format check clean

### Backend

- All tests pass
- Maven clean verify successful
- JaCoCo coverage gate passed

### Repository

- `git diff --check` clean
- No unrelated files modified
- No unrelated files staged

## Implementation Sequence

1. **Regression B first** (backend evidence mapping — test-first)
   - Add RED tests in `AnalysisResultQueryServiceImplTest` proving evidence composition produces null-field `EvidenceItem` instances (8 category tests + 1 JSON serialization regression test + preview limit test)
   - Verify all RED tests FAIL as expected (null field assertions fail)
   - Implement eight typed mapping methods replacing unsafe cast
   - Verify all tests GREEN
   - Verify JSON serialization succeeds
   - Run full backend suite (`mvn clean verify`)
   - Add backend generic-launch contract tests (test-first)
   - Verify existing backend contract tests PASS (backend is correct)
   - Run full backend suite again

2. **Regression A second** (frontend launch contract — test-first)
   - Add RED tests proving current broken form violates Story 0099 contract
   - Verify RED tests FAIL as expected
   - Restore `AnalysisForm` to Story 0099 state
   - Restore `ProjectAnalysesSection` to Story 0099 state
   - Restore `analysis.models.ts` to Story 0099 state
   - Verify RED tests now GREEN
   - Run frontend quality gates (test, lint, build, format)

3. **Cross-Story regression verification**
   - Run Story 0099 acceptance criteria
   - Run Story 0100 result page tests
   - Run Story 0101 trusted artifact tests
   - Verify no regressions

## Explicit Non-Scope

- Story 0098 (category balancing)
- Analysis quality/depth improvements
- Benchmark work
- New Analysis types
- New Intents
- New agents
- LLM/provider changes
- Prompt changes
- RAG
- Vector retrieval
- Engineering Query
- Persistence changes unrelated to the defect
- Authentication/authorization work
- Unrelated Angular redesign
- Unrelated backend refactoring

## Unresolved Questions

None. Both regressions have clear root causes and minimal corrections. EvidenceItem semantic mapping has been validated against repository usage — all 8 categories can be faithfully represented.

## Repository Status

- Branch: `p0-restore-analysis-launch-and-result`
- Baseline SHA: `885c446a3ecbaa566e49ea1d42b5c18e185672d3`
- Pre-existing human modification: `frontend/src/app/features/analyses/project-analyses-section.html` (formatting only)
- No commits, no pushes, no merges
