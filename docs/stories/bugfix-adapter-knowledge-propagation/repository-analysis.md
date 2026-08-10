# Repository Analysis — Adapter Knowledge Propagation Bugfix

## 1. Exact Root Cause

`RepositoryContextAdapter.synthesizeAnalysisContext()` constructs an `AnalysisContext` using the **11-argument convenience constructor**:

```java
return new AnalysisContext(
        projectSnapshot,
        analysisSnapshot,
        snapshot.latestProjectProfile(),
        List.of(),   // facts
        List.of(),   // observations
        snapshot.recentKnowledgeEvents(),
        snapshot.recentAnalyses(),
        snapshot.architectureArtifacts(),
        snapshot.relatedDecisions(),
        snapshot.recentMilestones(),
        snapshot.validatedProposals());
```

This constructor delegates to the full 13-argument canonical constructor with **hardcoded defaults**:

```java
public AnalysisContext(..., List<ValidatedProposalSnapshot> validatedProposals) {
    this(project, analysis, projectProfile, facts, observations, recentKnowledgeEvents,
            relatedAnalyses, architectureArtifacts, relatedDecisions, recentMilestones,
            validatedProposals, null, List.of());
    //                                     ^^^^   ^^^^^^^
    //                               evolutionContext  validatedEngineeringEvents
}
```

The 13-argument constructor then sets `validatedEngineeringEvents = List.copyOf(List.of())`, permanently dropping the snapshot data.

The root cause is purely **mechanical**: the adapter calls a convenience constructor that does not accept `validatedEngineeringEvents`, and the convenience constructor hardcodes `List.of()` for that parameter.

## 2. Exact Affected Fields

| Snapshot field | Propagated to AnalysisContext? | Field exists in AnalysisContext? | Status |
|---|---|---|---|
| `project` | ✅ Yes | ✅ Yes | OK |
| `latestProjectProfile` | ✅ Yes | ✅ Yes | OK |
| `recentKnowledgeEvents` | ✅ Yes | ✅ Yes | OK |
| `validatedProposals` | ✅ Yes | ✅ Yes | OK |
| `architectureArtifacts` | ✅ Yes | ✅ Yes | OK |
| `relatedDecisions` | ✅ Yes | ✅ Yes | OK |
| `recentMilestones` | ✅ Yes | ✅ Yes | OK |
| `recentAnalyses` | ✅ Yes | ✅ Yes | OK |
| `validatedEngineeringEvents` | ❌ **Dropped** | ✅ Yes (field exists) | **BUG** |
| `openChallenges` | ❌ **Dropped** | ❌ **Field missing** | **Structural gap** |
| `knowledgeRelations` | ❌ **Dropped** | ❌ **Field missing** | **Structural gap** |

**Three fields are lost.** One (`validatedEngineeringEvents`) is a pure propagation bug — the field exists in `AnalysisContext` but the adapter doesn't pass it. Two (`openChallenges`, `knowledgeRelations`) are structural gaps — they do not exist in `AnalysisContext` at all.

## 3. Exact Affected Execution Path

```
EngineeringStoryContextServiceImpl.buildWithRepositoryContext()
  → RepositoryContextAdapter.buildRepositoryContext(projectId, description, snapshot)
    → synthesizeAnalysisContext(projectId, snapshot)
      → new AnalysisContext(..., 11 args)
        → this(..., null, List.of())   // validatedEngineeringEvents = empty
    → repositoryContextService.build(syntheticContext, intent, guidance, insights)
      → RepositoryContextEngine.build()
        → contextIntelligence.plan(context, intent)
        → collectors.forEach(collector -> candidates.addAll(collector.collect(request)))
        → ranker.rank(candidates, request)
        → selector.select(ranked, request)
        → ...
```

The `AnalysisContext` with dropped fields flows into `RepositoryContextEngine`, which passes it to collectors via `ContextRequest`. Currently no RepositoryContext collector reads `validatedEngineeringEvents`, `openChallenges`, or `knowledgeRelations` from the context — but the propagation contract is broken regardless.

## 4. Current Tests Covering the Adapter

**`RepositoryContextAdapterTest`** (4 tests):

| Test | What it verifies | Fixture |
|---|---|---|
| `shouldBuildRepositoryContextWithStoryDescription` | Adapter builds context, passes project ID and intent | Uses legacy 8-arg snapshot constructor |
| `shouldBuildRepositoryContextWithNullDescription` | Null description handled gracefully | Same legacy fixture |
| `shouldLoadInsightsByProjectId` | Insights loaded from repository | Same legacy fixture |
| `shouldPassIntentDefinitionWithEngineeringStoryProfile` | Intent has correct profile keys | Same legacy fixture |

**Critical gap**: The test fixture uses the legacy 8-argument `ProjectContextSnapshot` constructor, which defaults `validatedEngineeringEvents`, `openChallenges`, and `knowledgeRelations` to `List.of()`. The tests therefore **cannot detect** the propagation bug because the input already has empty lists.

**`EngineeringStoryContextServiceTest`** (4 tests): Tests the service layer, mocks the adapter. Does not exercise adapter propagation.

**`ProjectContextProviderTest`**: Tests snapshot construction. Does not test adapter propagation.

## 5. Smallest Safe Implementation

### Phase A — Propagate `validatedEngineeringEvents` (pure bugfix)

**File: `RepositoryContextAdapter.java`**

Change `synthesizeAnalysisContext()` to use the full 13-argument `AnalysisContext` constructor:

```java
return new AnalysisContext(
        projectSnapshot,
        analysisSnapshot,
        snapshot.latestProjectProfile(),
        List.of(),   // facts
        List.of(),   // observations
        snapshot.recentKnowledgeEvents(),
        snapshot.recentAnalyses(),
        snapshot.architectureArtifacts(),
        snapshot.relatedDecisions(),
        snapshot.recentMilestones(),
        snapshot.validatedProposals(),
        null,                                              // evolutionContext
        snapshot.validatedEngineeringEvents());            // ← NEW
```

**File: `RepositoryContextAdapterTest.java`**

- Update the `snapshot()` fixture to use the 11-argument `ProjectContextSnapshot` constructor with non-empty `validatedEngineeringEvents`, `openChallenges`, and `knowledgeRelations`.
- Add a test that captures the `AnalysisContext` and asserts `validatedEngineeringEvents` contains the expected elements.

### Phase B — Add `openChallenges` and `knowledgeRelations` to `AnalysisContext` (structural completion)

**File: `AnalysisContext.java`**

Add two new record fields:

```java
public record AnalysisContext(
        ...existing 13 fields...,
        List<ProjectContextSnapshot.ChallengeSnapshot> openChallenges,        // NEW
        List<ProjectContextSnapshot.KnowledgeRelationSnapshot> knowledgeRelations  // NEW
) { ... }
```

Update the canonical constructor's copyOf block to include the new fields.

Update the two convenience constructors to pass `List.of()` for the new fields.

**File: `RepositoryContextAdapter.java`**

Pass `snapshot.openChallenges()` and `snapshot.knowledgeRelations()` in the constructor call.

**File: `AnalysisContextServiceImpl.java`**

Pass `projectContext.openChallenges()` and `projectContext.knowledgeRelations()` in the canonical constructor call (line ~95).

**File: `RepositoryContextAdapterTest.java`**

Add assertions that `openChallenges` and `knowledgeRelations` survive adapter synthesis.

## 6. Migration Required

**No.** All affected objects are in-memory Java records. No database schema changes.

## 7. Regression Risk

**Low.**

- The adapter change is purely additive — it passes more data through an existing constructor.
- Existing collectors do not consume these fields, so no ranking/selection behavior changes.
- The convenience constructors in `AnalysisContext` preserve backward compatibility (default `List.of()` for new fields).
- The `AnalysisContextServiceImpl` change is also additive — it passes more data to the same constructor.
- The canonical constructor's `List.copyOf` is the only behavioral change — it now copies 2 more lists, which is a no-op for empty lists.

### Downstream impact analysis

| Consumer | Uses `validatedEngineeringEvents`? | Uses `openChallenges`? | Uses `knowledgeRelations`? | Impact |
|---|---|---|---|---|
| `KnowledgeSelectionServiceImpl` | ✅ Yes (lines 69, 79) | ❌ No | ❌ No | **None** — adapter path doesn't go through this |
| `AnalysisContextServiceImpl` | ✅ Yes (line 94) | ❌ No | ❌ No | **None** — this IS the normal path, not the adapter path |
| `RepositoryContextEngine` | ❌ No | ❌ No | ❌ No | **None** — no collector reads these |
| `AgentContextProjectionService` | ❌ No (uses snapshot directly) | ❌ No (uses snapshot directly) | ❌ No (uses snapshot directly) | **None** — projection uses `ProjectContextSnapshot`, not `AnalysisContext` |
| `RepositoryContextAdapter` (adapter) | ❌ No (this IS the broken path) | ❌ No | ❌ No | **This is what we're fixing** |

The only consumer that would benefit from this fix in the adapter path is `KnowledgeSelectionServiceImpl`, but the adapter path bypasses `KnowledgeSelectionServiceImpl` entirely (it calls `RepositoryContextService.build()` directly). Therefore, **no existing behavior changes**. The fix repairs the propagation contract so that future consumers can rely on the data being present.

## Additional Finding: `evolutionContext` is always `null` in the adapter path

The adapter always passes `evolutionContext = null`. This is correct — the adapter synthesizes a synthetic `AnalysisContext` without an actual analysis, so there is no evolution scope. This is not a bug.

## Recommendation

Both Phase A and Phase B should be implemented together. Phase A alone would propagate `validatedEngineeringEvents` but leave `openChallenges` and `knowledgeRelations` structurally absent from `AnalysisContext`. Phase B completes the propagation contract for all three fields that `ProjectContextSnapshot` provides but `AnalysisContext` does not receive.

The implementation is small (2 files changed for Phase A, 4 files for Phase B), deterministic, and carries minimal risk.
