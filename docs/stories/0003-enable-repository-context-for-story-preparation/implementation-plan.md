# Implementation Plan

## Overview

This plan enables the Repository Context Engine for Engineering Story preparation by introducing:

1. A new Context Profile (`engineering-story-v1`) that prioritizes git history, commits, decisions, and documentation
2. An adapter service (`RepositoryContextAdapter`) that synthesizes `AnalysisContext` from `ProjectContextSnapshot` and calls `RepositoryContextEngine` directly
3. Extensions to the existing `EngineeringStoryContext` record, service, and controller to expose `RepositoryContext`

The adapter bypasses `KnowledgeSelectionServiceImpl` (which requires a persisted Analysis) and calls `RepositoryContextEngine.build()` directly through the `RepositoryContextService` interface. No modifications are made to the engine, existing collectors, existing profiles, or the Analysis workflow.

---

## Planned Changes

### Step 1 — Add `ENGINEERING_STORY` to `ContextProfile` enum

**Component:** `com.hopeful117.devlogai.repositorycontext.ContextProfile`

**Change:** Add `ENGINEERING_STORY` as a new enum constant.

**Reason:** The `ContextProfile` enum is used by `DeterministicContextIntelligence` to associate profiles with enum values. The new profile needs a corresponding enum entry.

**Constraint:** Follows existing pattern — each profile has exactly one enum constant.

### Step 2 — Register `engineering-story-v1` profile in `DeterministicContextIntelligence`

**Component:** `com.hopeful117.devlogai.repositorycontext.intelligence.DeterministicContextIntelligence`

**Change:** Add a new `register()` call in the `profiles()` method for `"engineering-story-v1"`.

**Profile definition:**

| Property | Value |
|---|---|
| key | `"engineering-story-v1"` |
| profile | `ContextProfile.ENGINEERING_STORY` |
| version | `"v1"` |
| preferred layers | `GIT_HISTORY`, `COMMIT_DIFF`, `ADR`, `PROJECT_DOCUMENTATION`, `ROADMAP` |
| minimum diverse layers | `3` |
| token priority | `100` |
| weights | semantic=15, architecture=15, history=25, recency=20, confidence=20, guidance=5 |

**Reason:** Story preparation prioritizes recent changes (git history, commits), existing decisions (ADR/decisions), and project documentation. Architectural relevance and semantic matching are secondary.

**Constraint:** Follows the existing `register()` + `profile()` + `weights()` pattern. No existing profiles modified.

### Step 3 — Create `RepositoryContextAdapter` service

**Component:** `com.hopeful117.devlogai.projectcontext.RepositoryContextAdapter` (new file)

**Change:** Create a new `@Service` class.

**Responsibilities:**
1. Inject `ProjectContextProvider`, `RepositoryContextService`, `ContextIntelligence`, `InsightRepository`
2. `buildRepositoryContext(UUID projectId, String storyDescription)`:
   a. Call `projectContextProvider.build(projectId)` → `ProjectContextSnapshot`
   b. Synthesize `AnalysisContext`:
      - `ProjectSnapshot` from the provider's snapshot
      - `AnalysisSnapshot` with deterministic UUID (`UUID.nameUUIDFromBytes(projectId.toString().getBytes())`), type `ARCHITECTURE_REVIEW`, status `COMPLETED`, `createdAt = Instant.now()`
      - Empty facts list
      - Empty observations list
      - Recent knowledge events, related analyses, architecture artifacts, related decisions, recent milestones, validated proposals from the provider
   c. Create `IntentDefinition` locally:
      - `id = "engineering-story-preparation"`
      - `version = "v1"`
      - `objective = storyDescription` (or `"Engineering Story preparation"` if null)
      - `supportedInsightTypes = List.of()`
      - `constraints = List.of("deterministic evidence only")`
      - `contextProfiles = List.of("engineering-story-v1")`
   d. Load validated insights: `insightRepository.findByProjectIdOrderByCreatedAtDesc(projectId)`
   e. Create `UserGuidance` from story description (or null if no description)
   f. Call `repositoryContextService.build(syntheticContext, intentDefinition, guidance, insights)`
   g. Return `RepositoryContext`

**Reason:** The adapter bridges `ProjectContextProvider` (story-scoped) to `RepositoryContextEngine` (Analysis-scoped) without modifying either.

**Constraint:** No `Analysis` entity is persisted. The synthetic analysis ID is deterministic and never stored.

### Step 4 — Extend `EngineeringStoryContext` record

**Component:** `com.hopeful117.devlogai.projectcontext.EngineeringStoryContext`

**Change:** Add `RepositoryContext repositoryContext` field (nullable) as the last field in the record.

**Reason:** The record must carry the repository context for the controller response.

**Constraint:** Backward compatible — existing clients that don't use the field are unaffected.

### Step 5 — Extend `EngineeringStoryContextService` interface

**Component:** `com.hopeful117.devlogai.projectcontext.EngineeringStoryContextService`

**Change:** Add new method:
```java
EngineeringStoryContext buildWithRepositoryContext(UUID projectId, String storyDescription);
```

**Reason:** Separates the new behavior from the existing `build(projectId)` method, preserving backward compatibility.

**Constraint:** Existing `build(UUID projectId)` method signature unchanged.

### Step 6 — Extend `EngineeringStoryContextServiceImpl`

**Component:** `com.hopeful117.devlogai.projectcontext.EngineeringStoryContextServiceImpl`

**Change:**
1. Inject `RepositoryContextAdapter` alongside existing `ProjectContextProvider`
2. Implement `buildWithRepositoryContext(UUID projectId, String storyDescription)`:
   - Build `EngineeringStoryContext` using existing `projectContextProvider.build(projectId)`
   - Call `repositoryContextAdapter.buildRepositoryContext(projectId, storyDescription)`
   - Return new `EngineeringStoryContext(snapshot, Instant.now(), projectId, repositoryContext)`

**Reason:** Delegates repository context assembly to the adapter while preserving the existing `build()` method.

**Constraint:** Existing `build(UUID projectId)` continues to return `EngineeringStoryContext` with `repositoryContext = null`.

### Step 7 — Extend `EngineeringStoryContextController`

**Component:** `com.hopeful117.devlogai.projectcontext.EngineeringStoryContextController`

**Change:** Add `@RequestParam(required = false) String description` to the endpoint method. Pass to `buildWithRepositoryContext()`.

**Current:**
```java
@GetMapping("/api/projects/{projectId}/engineering-story-context")
public ResponseEntity<EngineeringStoryContext> getEngineeringStoryContext(
        @PathVariable UUID projectId) {
    return ResponseEntity.ok(engineeringStoryContextService.build(projectId));
}
```

**After:**
```java
@GetMapping("/api/projects/{projectId}/engineering-story-context")
public ResponseEntity<EngineeringStoryContext> getEngineeringStoryContext(
        @PathVariable UUID projectId,
        @RequestParam(required = false) String description) {
    return ResponseEntity.ok(
        engineeringStoryContextService.buildWithRepositoryContext(projectId, description));
}
```

**Reason:** Exposes the optional description parameter for the client. When absent, `repositoryContext` is null.

**Constraint:** Backward compatible — existing clients that don't pass `description` receive a response with `repositoryContext: null`.

### Step 8 — Create unit tests

**Test 1: `DeterministicContextIntelligenceTest` update**

Add test method verifying:
- `engineering-story-v1` profile is resolvable via `plan()` with `List.of("engineering-story-v1")`
- Primary profile is `ENGINEERING_STORY`
- Preferred layers include `GIT_HISTORY`, `COMMIT_DIFF`, `ADR`, `PROJECT_DOCUMENTATION`, `ROADMAP`
- Weights match the defined values

**Test 2: `RepositoryContextAdapterTest` (new file)**

Test cases:
- Nominal: project with data → `RepositoryContext` with evidence from multiple collectors
- Empty project: project with no knowledge events/decisions/artifacts → `RepositoryContext` with only git history and current analysis evidence
- Project with no insights: `validatedInsights` is empty list → engine handles gracefully

All tests mock `ProjectContextProvider`, `InsightRepository`, and the 4 collectors to avoid database dependency.

---

## Files to Modify

| File | Nature of Modification |
|---|---|
| `ContextProfile.java` | Add `ENGINEERING_STORY` enum constant |
| `DeterministicContextIntelligence.java` | Add `engineering-story-v1` profile in `profiles()` |
| `EngineeringStoryContext.java` | Add `repositoryContext` field (nullable) |
| `EngineeringStoryContextService.java` | Add `buildWithRepositoryContext` method |
| `EngineeringStoryContextServiceImpl.java` | Inject adapter, implement new method |
| `EngineeringStoryContextController.java` | Add optional `description` parameter |
| `DeterministicContextIntelligenceTest.java` | Add test for new profile |

---

## Files to Create

| File | Purpose |
|---|---|
| `RepositoryContextAdapter.java` | Bridges `ProjectContextProvider` → `RepositoryContextEngine` |
| `RepositoryContextAdapterTest.java` | Unit tests for adapter |

---

## Dependencies

No new external dependencies required. All necessary components already exist:

- `RepositoryContextEngine` (via `RepositoryContextService` interface)
- `ProjectContextProvider`
- `InsightRepository`
- `DeterministicContextIntelligence`
- `ContextProfile` enum

---

## Test Plan

### Tests to Create

1. **`RepositoryContextAdapterTest`** — validates adapter behavior
   - Nominal: project with decisions, milestones, artifacts → evidence from `ProjectKnowledgeContextCollector`
   - Empty project → evidence only from `CurrentAnalysisContextCollector` and `GitHistoryContextCollector`
   - No insights → engine handles empty list

2. **`DeterministicContextIntelligenceTest`** — validates new profile
   - `engineering-story-v1` resolves to `ContextProfile.ENGINEERING_STORY`
   - Composed weights match expected values
   - Preferred layers include all 5 specified layers

### Tests to Verify (no modifications needed)

- `ProjectContextProviderTest` — confirms provider unchanged
- `EngineeringStoryContextServiceTest` — confirms existing `build()` still works
- `RepositoryContextServiceTest` — confirms engine pipeline unchanged

### Validation Commands

```bash
mvn compile -f backend/pom.xml
mvn test -f backend/pom.xml
```

### Expected Success Conditions

- `mvn compile` — zero errors
- `mvn test` — all existing tests pass, new tests pass
- No modifications to `KnowledgeSelectionServiceImpl`, `AnalysisContextServiceImpl`, `IntentCatalog`

---

## Risks

### Risk-1 : Synthetic AnalysisContext carries a fake analysis ID

**Mitigation:** The synthetic ID is deterministic (`UUID.nameUUIDFromBytes`). The `CurrentAnalysisContextCollector` creates one evidence item with `analysis:<fakeId>` reference. For `engineering-story-v1` profile, CURRENT_ANALYSIS is not a preferred layer, so this evidence is naturally deprioritized.

### Risk-2 : No integration test with real database

**Mitigation:** Unit tests mock the provider and repository. Integration can be validated manually after implementation. The adapter is a thin coordination layer with no complex logic.

### Risk-3 : `Instant.now()` in synthetic analysis affects reproducibility

**Mitigation:** For story preparation, recency scoring relative to "now" is semantically correct. The adapter docstrings document this behavior. Determinism is preserved for the same input data.

---

## Validation Checklist

- [ ] `ContextProfile.ENGINEERING_STORY` enum value exists
- [ ] `engineering-story-v1` profile registered in `DeterministicContextIntelligence`
- [ ] `RepositoryContextAdapter` annotated `@Service` with `@RequiredArgsConstructor`
- [ ] Adapter injects `ProjectContextProvider`, `RepositoryContextService`, `ContextIntelligence`, `InsightRepository`
- [ ] Adapter synthesizes `AnalysisContext` with deterministic UUID
- [ ] Adapter creates local `IntentDefinition` with `"engineering-story-v1"` profile
- [ ] Adapter calls `repositoryContextService.build()` directly
- [ ] `EngineeringStoryContext` has nullable `repositoryContext` field
- [ ] `EngineeringStoryContextService` has `buildWithRepositoryContext` method
- [ ] `EngineeringStoryContextServiceImpl` implements new method using adapter
- [ ] Controller accepts optional `description` parameter
- [ ] `RepositoryContextAdapterTest` created and passes
- [ ] `DeterministicContextIntelligenceTest` updated and passes
- [ ] `mvn compile` — zero errors
- [ ] `mvn test` — all tests pass
- [ ] No modifications to `KnowledgeSelectionServiceImpl`, `AnalysisContextServiceImpl`, `IntentCatalog`
- [ ] No `Analysis` entity persisted during story preparation flow

---

## Recommendation

**Ready for implementation**

The implementation strategy is well-defined. The adapter pattern is clean, the new profile follows established conventions, and all necessary components exist. No blocking ambiguity remains.

---

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
