# Implementation Plan — Story 0081

**APPROVED**

## Branch

`story/0081-preserve-repository-context-evidence-priority`

## Approved Working Hypothesis

SelectionResult.selected represents deterministic evidence priority. RepositoryContext.evidence must preserve that order. The re-sort in RepositoryContextEngine.build() that changes score-first ordering to layer-first ordering is the defect.

## Proposed Change

Remove the re-sort in `RepositoryContextEngine.build()` (lines 85-91).

### Current Code

```java
List<RepositoryEvidence> selected = selection.selected().stream()
        .sorted(Comparator.comparingInt(
                        (RepositoryEvidence value) -> value.layer().ordinal())
                .thenComparing(Comparator.comparingInt(
                        RepositoryEvidence::relevanceScore).reversed())
                .thenComparing(RepositoryEvidence::reference))
        .toList();
```

### Proposed Code

```java
List<RepositoryEvidence> selected = selection.selected().stream()
        .toList();
```

### Rationale

- `selection.selected()` already contains evidence in rank order (score DESC within same layer)
- `AgentContextProjectionService` consumes evidence in order and removes from tail
- No other consumer depends on layer-grouped ordering
- The selector's output is the authoritative source of evidence priority
- Removing the re-score preserves the single source of ordering truth (the ranker/selector pipeline)

## Consumer Impact Analysis

| Consumer | Impact | Notes |
|---|---|---|
| AgentContextProjectionService | **FIXED** — projection now removes lowest-priority evidence first | Only consumer that depends on order |
| EngineeringContextContractMapper | No impact | ORDER_INDEPENDENT |
| SelectedKnowledgePromptProjectionService | No impact | ORDER_INDEPENDENT |
| KnowledgeSelectionServiceImpl | No impact | ORDER_INDEPENDENT (uses .size()) |
| EngineeringStoryContextServiceImpl | No impact | ORDER_INDEPENDENT (uses .size()) |

## Diversity Impact

No impact. The selector's diversity behavior is preserved. The selector output order (which includes diversity selections) is now preserved through to projection.

## Context Digest Impact

**EXPECTED CHANGE.** The context digest includes `evidence` in the digest computation (RepositoryContextEngine.java:129). Changing the evidence order changes the digest.

This is expected and acceptable. The old digest was computed over layer-sorted evidence, which was incorrect. The new digest will be computed over score-sorted evidence, which is correct.

## Deterministic Guarantees

The same candidates/profile/budget will produce:
- Same ranked list (deterministic comparator in DeterministicEvidenceRanker)
- Same selected list (deterministic iteration in BudgetedDiverseEvidenceSelector)
- Same RepositoryContext evidence order (preserving selector output order)
- Same projection survivors (deterministic tail removal)

All stages remain deterministic.

## Regression Test Strategy

### Test 1: RepositoryContextEngine preserves selector priority

Create a RepositoryContextEngine test that verifies evidence ordering after build:
- Provide evidence with high scores in ADR/ROADMAP/VALIDATED_INSIGHT layers
- Provide evidence with low scores in RELATED_SOURCE_CODE/COMMIT_DIFF layers
- Verify RepositoryContext.evidence() preserves score-based ordering (not layer-grouped)

### Test 2: Layer ordinal does not override score

Verify that a high-score DECISION appears before a low-score SOURCE_FILE in RepositoryContext.evidence().

### Test 3: Tie-breaking stability

Verify that evidence with identical scores is ordered deterministically (layer ordinal ASC, reference ASC).

### Test 4: Selector diversity preserved

Verify that diversity-selected evidence appears in the expected position relative to ordinary ranked evidence.

### Test 5: Integration with AgentContextProjectionService

Under constrained budget:
- High-priority prefix (score-based) survives
- Low-priority tail is removed
- Trusted knowledge (DECISION, INSIGHT, ENGINEERING_STORY) survives when scores are competitive

### Test 6: Existing projection tests remain green

All 12 existing projection tests must pass without modification.

## Production Files Expected to Change

| File | Change |
|---|---|
| `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java` | Remove re-sort in build() method (lines 85-91) |

## Test Files Expected to Change/Add

| File | Change |
|---|---|
| `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngineTest.java` | **NEW** — 4-5 tests for ordering preservation |

## Acceptance Criteria Mapping

| AC | Implementation |
|---|---|
| AC1 — Single Priority Authority | Selector output is the authoritative ordering; re-score removed |
| AC2 — Order Preservation | RepositoryContext.evidence preserves selector output order |
| AC3 — No Trusted-Knowledge Special Case | No special-casing; trusted knowledge survives via natural ranking |
| AC4 — Projection Remains Simple | AgentContextProjectionService unchanged; tail-removal unchanged |
| AC5 — Diversity Preserved | Selector diversity behavior unchanged; selector output order preserved |
| AC6 — Determinism | Same inputs produce same ordering (deterministic pipeline) |
| AC7 — Current Small Context Behavior | Contexts that fit without tail removal preserve same evidence set |
| AC8 — Budgets Unchanged | No budget changes |
| AC9 — Ranking Unchanged | No ranking weight changes |
| AC10 — Selection Unchanged | No selection algorithm changes |
| AC11 — Regression Test | Test 5 covers the structural failure reproduction |

## Risks

1. **Context Digest Change**: Existing context digests will change. This is expected and acceptable.
2. **Diversity Selection Ordering**: Diversity-selected evidence may appear at different positions than before. This is correct behavior.
3. **Behavioral Change**: Consumers that relied on layer-grouped ordering will see different evidence ordering. Analysis confirms no such consumer exists.

## Explicit Out of Scope

- Ranking weight changes
- Selection algorithm changes
- Token/byte budget changes
- MCP changes
- Temporal knowledge
- Evidence compaction semantics
- Lineage Phase 2 diagnostics
- Database schema changes
- Frontend changes
