# Engineering Story 0080 — Repository Analysis

## 1. Current Fit Strategy — Complete Trace

`AgentContextProjectionService.fit()` (lines 118–159) applies progressive
reduction steps. Each step is followed by a `fits()` check; the method returns
as soon as the projection fits the budget.

### Step ordering (current)

| Step | Method | Affects | Warning constant |
|---|---|---|---|
| 0 | `fits()` initial check | — | — |
| 1 | `removeRelatedReferences()` | Evidence only | `AGENT_PROJECTION_RELATED_REFERENCES_REMOVED` |
| 2 | `compactReasons()` | Evidence only | `AGENT_PROJECTION_REASONS_COMPACTED` |
| 3 | `removeDeclarations()` | Evidence only | `AGENT_PROJECTION_DECLARATIONS_REMOVED` |
| 4 | `removeContent()` | Evidence only | `AGENT_PROJECTION_CONTENT_REMOVED` |
| 5 | `compactSummary()` | Evidence only | `AGENT_PROJECTION_SUMMARY_COMPACTED` |
| 6 | `removeTailEvidence()` | Evidence only (removes items) | `AGENT_PROJECTION_EVIDENCE_REMOVED` + `AGENT_PROJECTION_MINIMAL_EVIDENCE_COMPACTED` + `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED` |
| 7 | `removeProfileDetails()` | ProjectContext | `AGENT_PROJECTION_PROFILE_DETAILS_REMOVED` |
| 8 | `compactHumanContextInputs()` | ProjectContext | `AGENT_PROJECTION_HUMAN_CONTEXT_INPUTS_COMPACTED` |
| 9 | `removeProjectContextLists()` | ProjectContext | `AGENT_PROJECTION_PROJECT_CONTEXT_LISTS_REMOVED` |
| 10 | `minimalProjectContext()` | ProjectContext | `AGENT_PROJECTION_PROJECT_CONTEXT_MINIMAL` |

### Observation

Steps 1–6 affect **only** evidence. Steps 7–10 affect **only** ProjectContext.
Evidence removal (step 6) is the **last** evidence-affecting step and runs
**before** any ProjectContext reduction.

## 2. Initial Projection Shape

`initial()` (lines 103–116) builds `ProjectionState`:

- `projectContext`: the full `ProjectContextSnapshot` (profile, proposals,
  analyses, relations, human context inputs, etc.)
- `evidence`: all `RepositoryEvidence` items mapped to
  `AgentRepositoryContext.Evidence` (with reasons, provenance, extraction,
  content, symbols)
- `warnings`: existing warnings from `RepositoryContext`
- Accounting counters all start at 0

## 3. Budget Calculation

`fits()` (lines 395–405) serializes the full `CanonicalProjection` to JSON via
Jackson and checks:

```
bytes <= policy.maximumBytes()  (default 32,768)
AND
estimateTokens(bytes) <= policy.maximumEstimatedTokens()  (default 8,192)
```

`estimateTokens(bytes) = max(1, (bytes + 3) / 4)` — a simple byte-to-token
ratio.

The `CanonicalProjection` includes:
- `ProjectContextSnapshot` (project, profile, all lists)
- `projectId`
- `ProjectFreshnessSummary`
- `CanonicalRepositoryContext` (evidence list, accounting metadata, warnings,
  digest)

## 4. Evidence-Only Steps (1–5) — Detail

### Step 1: `removeRelatedReferences()` (lines 161–167)

Removes all `relatedReferences` from every evidence item. Replaces with
`List.of()`. Returns unchanged state if no related references exist.

### Step 2: `compactReasons()` (lines 169–175)

Keeps only the first reason per evidence item. Removes the rest.

### Step 3: `removeDeclarations()` (lines 182–189)

Strips `symbols.declarations` from evidence items that have them. Does not
remove the `symbols` record itself.

### Step 4: `removeContent()` (lines 191–198)

Strips `content.text` from evidence items that have content. Sets status to
`TRUNCATED` in the output. Does not remove the `content` record itself.

### Step 5: `compactSummary()` (lines 200–211)

Truncates summaries longer than 160 characters with `...` suffix.

## 5. Evidence Removal — Step 6: `removeTailEvidence()` (lines 213–252)

This is the critical step. It progressively removes evidence items from the
**tail** (end of list = lowest-ranked items):

1. While `remaining.size() > 1`: remove the last item, check `fits()`.
2. If 1 item remains and still doesn't fit: try `minimal()` on that item
   (strips provenance, extraction, content, symbols, relatedReferences,
   reasons).
3. If still doesn't fit: return empty evidence list with
   `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED` warning.

The method adds `AGENT_PROJECTION_EVIDENCE_REMOVED` warning at entry. If
minimal compaction is attempted, `AGENT_PROJECTION_MINIMAL_EVIDENCE_COMPACTED`
is added. If all evidence is removed, `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED`
is appended.

### When ALL_EVIDENCE_REMOVED is emitted

`removeTailEvidence()` emits `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED` when:
- After removing evidence down to 1 item
- After applying `minimal()` to that 1 item
- The projection still doesn't fit

In the production failure scenario (217 candidates → 60 selected → canonical
~213KB with 32KB budget):
- Steps 1–5 compact evidence but don't remove items
- Step 6 removes all 60 evidence items one by one, then minimal on the last
  one, then empties the list — but the ProjectContext alone (~68KB) still
  exceeds 32KB
- Steps 7–10 then reduce ProjectContext to fit — but evidence is already gone

## 6. ProjectContext Reduction Steps (7–10)

### Step 7: `removeProfileDetails()` (lines 254–272)

Compacts `latestProjectProfile`:
- Clears `keyObservations`, `strengths`, `risks` lists
- Truncates `deterministicSummary` to 500 chars
- Keeps `completeness` and `characteristicCount`

### Step 8: `compactHumanContextInputs()` (lines 274–296)

Truncates each `HumanContextInputSnapshot`:
- `title` to 120 chars
- `contentMarkdown` to 500 chars

### Step 9: `removeProjectContextLists()` (lines 298–306)

Replaces all 11 list fields in `ProjectContextSnapshot` with `List.of()`:
- recentKnowledgeEvents, validatedProposals, architectureArtifacts,
  relatedDecisions, recentMilestones, recentAnalyses,
  validatedEngineeringEvents, openChallenges, knowledgeRelations,
  engineeringStories, humanContextInputs

Keeps: `project` and `latestProjectProfile`.

### Step 10: `minimalProjectContext()` (lines 308–326)

Nuclear option: replaces `ProjectContextSnapshot` with only the `project`
record (id, name, slug, description, status), all text compacted to 160 chars.
Sets `latestProjectProfile` to null, all lists to `List.of()`.

## 7. Canonical Size Breakdown (Production Failure)

Measured during the Lineage Phase 2 investigation:

| Component | Size |
|---|---|
| Evidence (60 items) | ~144,565 bytes |
| ProjectContextSnapshot | ~67,892 bytes |
| Freshness + overhead | ~800 bytes |
| **Total canonical** | **~213,257 bytes** |
| **Budget** | **32,768 bytes** |

The budget is exceeded by ~6×. Even if all evidence is removed (step 6),
ProjectContext alone (~68KB) exceeds the budget by ~2×. Steps 7–10 must
reduce ProjectContext to ~32KB or less for the projection to fit.

## 8. Existing Tests — Behavior Guarantees

### `AgentContextProjectionServiceTest`

| Test | What it proves |
|---|---|
| `shouldCreateCompactTraceableDeterministicProjection` | Large budget: all 5 evidence preserved, no removals, deterministic projectionDigest |
| `shouldApplyMechanicalDegradationAndPreserveOutcomeMetadata` | Small budget (2,500B): evidence compacted (content stripped, declarations removed), evidence item preserved |
| `shouldCompactLongSummaryWhenProjectionNeedsAdditionalReduction` | Summary truncated to ≤160 chars |
| `shouldFallbackToMinimalOrEmptyEvidenceWhenCompactionGetsTight` | Very small budget (1,600B): evidence reduced to ≤1 item, MINIMAL_EVIDENCE_COMPACTED warning |
| `shouldCompactProjectContextWhenEmptyEvidenceStillDoesNotFit` | Oversized ProjectContext: profile details removed, then lists/minimal. **This test uses 1 evidence item — doesn't reproduce the 60-evidence failure** |
| `shouldRemoveOnlyTheExistingTailAsLastResort` | 8 evidence items: tail removed, head preserved, EVIDENCE_REMOVED warning |
| `shouldFailWhenOneUsableEvidenceCannotFit` | Budget too small (10B): throws `AgentContextProjectionException` |
| `shouldChangeProjectionDigestWhenSemanticEvidenceChanges` | Determinism: different evidence → different digest |

### `EngineeringStoryContextServiceTest`

| Test | What it proves |
|---|---|
| `shouldBuildAgentContextFromOneProjectSnapshot` | Mock-based: projectionService.project() is called correctly |

## 9. Observable Contract — Warnings

The following warnings are part of the observable contract (returned in
`AgentRepositoryContext.warnings()`):

| Warning | Meaning |
|---|---|
| `AGENT_PROJECTION_RELATED_REFERENCES_REMOVED` | Evidence relatedReferences cleared |
| `AGENT_PROJECTION_REASONS_COMPACTED` | Evidence reasons reduced to first |
| `AGENT_PROJECTION_DECLARATIONS_REMOVED` | Symbol declarations stripped |
| `AGENT_PROJECTION_CONTENT_REMOVED` | Content text stripped |
| `AGENT_PROJECTION_SUMMARY_COMPACTED` | Summary truncated to 160 chars |
| `AGENT_PROJECTION_EVIDENCE_REMOVED` | Evidence items removed from tail |
| `AGENT_PROJECTION_MINIMAL_EVIDENCE_COMPACTED` | Last evidence item compacted to minimal |
| `AGENT_PROJECTION_ALL_EVIDENCE_REMOVED` | All evidence removed |
| `AGENT_PROJECTION_PROFILE_DETAILS_REMOVED` | Profile details compacted |
| `AGENT_PROJECTION_HUMAN_CONTEXT_INPUTS_COMPACTED` | Human context inputs truncated |
| `AGENT_PROJECTION_PROJECT_CONTEXT_LISTS_REMOVED` | All ProjectContext lists cleared |
| `AGENT_PROJECTION_PROJECT_CONTEXT_MINIMAL` | ProjectContext reduced to minimal |

## 10. Root Cause Confirmed

The investigation's finding is confirmed against current code:

**Steps 1–6 affect only evidence. Steps 7–10 affect only ProjectContext.**

When ProjectContext is independently oversized (~68KB > 32KB budget):
1. Steps 1–5 compact evidence metadata but don't remove items
2. Step 6 removes all evidence items — but the projection still doesn't fit
   because ProjectContext alone exceeds the budget
3. Steps 7–10 reduce ProjectContext to fit — but evidence is already gone

The fix must ensure that ProjectContext is reduced **before** all evidence is
removed. Evidence removal should remain as the last resort, not the
penultimate resort.

## 11. Other Affected Files

| File | Role | Change needed? |
|---|---|---|
| `AgentContextProjectionService.java` | Primary: `fit()` reordering | **YES** |
| `AgentContextProjectionPolicy.java` | Budget constants | No |
| `AgentRepositoryContext.java` | DTO records | No |
| `ProjectContextSnapshot.java` | Snapshot record | No |
| `EngineeringStoryContextServiceImpl.java` | Calls `projectionService.project()` | No |
| `AgentContextProjectionServiceTest.java` | Existing tests | Add regression test |
| `EngineeringStoryContextServiceTest.java` | Mock-based service test | No change needed |
| `EngineeringStoryContextControllerWebMvcTest.java` | Controller test | Verify no regression |

## 12. ADR-058 Consistency

ADR-058 establishes that projection is a **thin, deterministic, budget-driven**
compaction layer. It consumes the output of ranking/selection and must not
reinterpret relevance. The proposed fix (reordering reduction steps) preserves
this invariant: the same steps are applied in a different order. No new ranking
semantics are introduced.
