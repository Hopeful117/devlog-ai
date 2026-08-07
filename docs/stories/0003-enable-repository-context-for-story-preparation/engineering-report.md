# Engineering Report

## Story

**Story 0003** — Enable Repository Context Engine for Engineering Story Preparation

Unlock the existing Repository Context Engine for Engineering Story preparation so DevLog can provide Kiko with ranked, budgeted, diverse, and explainable repository evidence when preparing an Engineering Story — without requiring a persisted Analysis.

---

## Objective

The Repository Context Engine (`RepositoryContextEngine`) already implements deterministic evidence collection, multi-criteria ranking, budget-aware selection, and explainable digests. However, it was inaccessible for story preparation because it required a persisted `AnalysisContext` + `IntentDefinition`. Stories 0001 and 0002 established `ProjectContextProvider` and `EngineeringStoryContext`, but these only returned raw project-scoped data without evidence ranking.

This Story introduced an adapter that bridges `ProjectContextProvider` to `RepositoryContextEngine`, and a dedicated Context Profile (`engineering-story-v1`) that defines evidence priorities for story preparation.

---

## Repository Analysis Summary

The Repository Analysis (human-approved) established:

- **All 4 collectors work with a synthetic `AnalysisContext`** — `GitHistoryContextCollector` only needs the project ID, `ProjectKnowledgeContextCollector` reads decisions/milestones/artifacts, `DeterministicKnowledgeContextCollector` handles empty facts/observations gracefully, and `CurrentAnalysisContextCollector` produces one additional evidence item that is naturally deprioritized.

- **No architectural conflicts** — the adapter bridges two tested subsystems (`ProjectContextProvider` and `RepositoryContextEngine`) without modifying either.

- **`KnowledgeSelectionServiceImpl` bypass is justified** — it requires `AnalysisExecutionDiagnostic` (only for persisted Analyses). The adapter calls `RepositoryContextEngine.build()` directly through the `RepositoryContextService` interface.

- **No open questions** — the implementation path was clear.

---

## Implementation Plan Summary

The approved Implementation Plan defined 8 steps:

1. Add `ENGINEERING_STORY` to `ContextProfile` enum
2. Register `engineering-story-v1` profile in `DeterministicContextIntelligence`
3. Create `RepositoryContextAdapter` service
4. Extend `EngineeringStoryContext` record with `repositoryContext` field
5. Extend `EngineeringStoryContextService` interface with `buildWithRepositoryContext`
6. Extend `EngineeringStoryContextServiceImpl` with adapter injection
7. Extend `EngineeringStoryContextController` with optional `description` parameter
8. Create unit tests

Key design decisions:
- Adapter calls `RepositoryContextEngine` directly, bypassing `KnowledgeSelectionServiceImpl`
- Synthetic `AnalysisContext` from `ProjectContextSnapshot` preserves engine interface
- Local `IntentDefinition` avoids conflating Analysis workflow with story preparation
- `engineering-story-v1` profile prioritizes git history, commits, ADR, docs, and roadmap
- `repositoryContext` nullable on `EngineeringStoryContext` for backward compatibility

---

## Implementation Summary

All 8 steps implemented exactly as planned. No deviations.

The adapter synthesizes an `AnalysisContext` from `ProjectContextSnapshot`, creates a local `IntentDefinition` with the `engineering-story-v1` profile, loads validated insights by project ID, and calls `RepositoryContextEngine.build()` directly. The existing `build(projectId)` method continues to work with `repositoryContext = null`.

---

## Modified Files

| File | Change |
|---|---|
| `ContextProfile.java` | Added `ENGINEERING_STORY` enum constant |
| `DeterministicContextIntelligence.java` | Registered `engineering-story-v1` profile with git-history/commit-diff/ADR/docs/roadmap priorities |
| `EngineeringStoryContext.java` | Added nullable `RepositoryContext repositoryContext` field |
| `EngineeringStoryContextService.java` | Added `buildWithRepositoryContext(UUID, String)` method |
| `EngineeringStoryContextServiceImpl.java` | Injected `RepositoryContextAdapter`, implemented new method |
| `EngineeringStoryContextController.java` | Added `@RequestParam(required = false) String description`, calls `buildWithRepositoryContext` |
| `EngineeringStoryContextServiceTest.java` | Added `@Mock RepositoryContextAdapter`, added test for `buildWithRepositoryContext` |
| `DeterministicContextIntelligenceTest.java` | Added `resolvesEngineeringStoryProfile` test verifying weights, layers, and primary profile |

---

## Created Files

| File | Purpose |
|---|---|
| `RepositoryContextAdapter.java` | Bridges `ProjectContextProvider` → `RepositoryContextEngine` for story preparation without persisted Analysis |
| `RepositoryContextAdapterTest.java` | 4 unit tests: nominal path, null description, insight loading, intent definition verification |

---

## Architecture Impact

**No architectural changes.** The adapter introduces a new service in the existing `projectcontext` package that consumes `repositorycontext` through its interface. No new module boundaries, no dependency direction changes, no public contract changes.

**Preserved boundaries:**
- `ProjectContextProvider` independence maintained
- `KnowledgeSelectionServiceImpl` untouched
- `AnalysisContextServiceImpl` untouched
- `IntentCatalog` untouched
- All existing collectors untouched
- `RepositoryContextEngine` consumed as-is through `RepositoryContextService` interface

**Compatibility:** The `EngineeringStoryContext` record gains a nullable field. Existing clients that don't use `repositoryContext` are unaffected (Jackson ignores null/missing fields by default).

---

## Validation

```text
Command: mvn compile -q
Result: Passed (zero errors)

Command: mvn test -Dtest="RepositoryContextAdapterTest,DeterministicContextIntelligenceTest,EngineeringStoryContextServiceTest,ProjectContextProviderTest,RepositoryContextServiceTest"
Result: Passed (20/20 tests)

Command: mvn test (full suite)
Result: 194 pass, 4 failures (pre-existing), 2 errors (pre-existing) — 0 regressions
```

Pre-existing failures (unrelated to this Story):
- `AnalysisWorkflowServiceTest.shouldFailTaskAndAnalysisWhenSubmissionFails` — NPE on intent
- `InitialCollectorsTest` (2 tests) — scanner/spring collector assertions
- `ValidationControllerWebMvcTest` — HTTP status mismatch
- `DevlogAiBackendApplicationTests.contextLoads` — requires database
- `RestAIEngineClientIntegrationTest` — legacy submission disabled

---

## Review Outcome

**Code Review technical recommendation:** Ready for human approval with minor follow-up.

**Findings:** 1 Observation (Javadoc link inconsistency — non-blocking). 0 Blocker, 0 Major, 0 Minor.

**Architecture compliance:** ADR-037, ADR-039, ADR-040 all respected.

**Test assessment:** 13/13 acceptance criteria verified. All tests pass. No regressions.

**Human Code Review approval:** Granted.

---

## Workflow Approvals

- Repository Analysis: Human approved
- Implementation Plan: Human approved
- Code Review: Human approved

---

## Remaining Work

None required for this Story.

Optional non-blocking follow-up (documented in Code Review):
- Update Javadoc in `RepositoryContextAdapter` to reference `RepositoryContextService` instead of `RepositoryContextEngine` in the first `{@link}` tag.

---

## Lessons Learned

1. **Adapter pattern for bridging Analysis-scoped and project-scoped services** — When an existing service requires an `AnalysisContext` but the caller doesn't have a persisted Analysis, synthesizing a thin `AnalysisContext` from available project data is clean and preserves the service interface. The key insight is that collectors primarily use the project ID, not the analysis metadata.

2. **Bypassing `KnowledgeSelectionServiceImpl` is safe when only repository evidence is needed** — The service orchestrates a broader workflow (facts, observations, insights, diagnostics) than what story preparation requires. Calling `RepositoryContextEngine.build()` directly through the `RepositoryContextService` interface is the correct abstraction level.

3. **Context Profiles are extensible without modifying existing profiles** — Adding a new profile to `DeterministicContextIntelligence.profiles()` requires only one `register()` call. The ranking and selection pipeline works generically with any profile definition.

---

## Final Status

**Completed**

Story implementation is complete. All validation passed. Code Review received human approval. No required Story work remains.
