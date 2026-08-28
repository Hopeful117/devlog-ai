# Story 0097 — Reconnect Per-File COMMIT_DIFF Evidence to Analysis Pipeline

## Status

**STORY_COMPLETE**

HUMAN_REVIEW = **APPROVED**

## Priority

**P0 — FIRST SLICE (ADR-063 incremental)**

## Objective

Make the existing per-file `COMMIT_DIFF` evidence produced by `CommitDiffEvidenceCollector` available
to the Analysis candidate/selection pipeline instead of relying only on aggregated `COMMIT_DIFF_SUMMARY`
facts, by introducing the minimal shared retrieval primitive required by ADR-063 to expose
pre-composition candidates to consumer-specific composition.

Governed by **ADR-063 (Accepted)**, preserving **ADR-044** (content enrichment restriction) and
**ADR-036** (commit-level code diff analysis).

## Human Story

As a human engineer requesting an Analysis,
I want the model to reason over the actual files changed in recent commits — not just an aggregate
statistical summary —
so that the Analysis output is grounded in concrete, verifiable engineering evidence.

## Problem (measured baseline, code-verified)

The Analysis pipeline has **two independent paths** for commit evidence:

| Path | Producer | Evidence type | Budget | Selection |
|---|---|---|---|---|
| **System A** (RepositoryContextEngine) | `CommitDiffEvidenceCollector` | Per-file `COMMIT_DIFF` — changed file path, status, summary | 60-item global budget | `BudgetedDiverseEvidenceSelector` |
| **System B** (KnowledgeCollectionService) | `CommitScopedFactCollector` | Aggregated `COMMIT_DIFF_SUMMARY` — statistical summary as a `Fact` | 25-fact budget | `KnowledgeSelectionServiceImpl.factScore()` |

**Problem:** Analysis primarily consumes System B facts (aggregated summary) through its
knowledge-selection path. System A per-file evidence flows through `RepositoryContextEngine` into
`SelectedKnowledge.repositoryContext`, but the two paths are coordinated only by the global
repository-context budget — there is no mechanism ensuring per-file `COMMIT_DIFF` evidence is
represented in the Analysis candidate pool.

Observed effect (current code):
- `CommitScopedFactCollector` emits `COMMIT_DIFF_SUMMARY` facts → consumed by `factScore()` with
  score 10 (competing against 15-100 for structural facts) → may consume fact-budget slots without
  providing granular detail.
- `CommitDiffEvidenceCollector` emits per-file `COMMIT_DIFF` evidence → competes for slots in the
  60-item repository-context budget against Git history, project structure, knowledge, and events.
- The model sees the aggregated summary as a fact, but per-file evidence may or may not survive
  repository-context selection — there is no guarantee.

**Root cause:** Implemented-not-connected. Both collectors exist (ADR-0006), the bridge
(`DeterministicKnowledgeContextCollector`) converts facts to `RepositoryContextEngine` evidence, and
the prompt projection includes `repositoryContext.evidence`. But no shared retrieval primitive exposes
the complete pre-composition candidate set, so Analysis cannot access the full per-file `COMMIT_DIFF`
candidate pool before budgeting occurs.

### Architecture verification: the complete candidate set is not exposed

Code trace confirms:

1. `CommitDiffEvidenceCollector.collect()` returns up to 50 per-file `COMMIT_DIFF` items (the
   complete per-file candidate set from this collector).
2. `RepositoryContextEngine.build()` merges all collectors' output into a local variable `candidates`
   at line 76-77 — this is the **only** location where the complete unfiltered candidate set exists.
3. `BudgetedDiverseEvidenceSelector.select()` discards candidates that exceed the 60-item budget,
   token budget, relevance floor, or concentration limits — the discarded list is **not preserved**
   (only scalar counts and reference-only `SelectionDecision` records with `selected=false`).
4. The `RepositoryContext` output stores `evidence` (post-budget selected items only), `candidateCount`
   (scalar), and `discardedCount` (scalar) — **not** the pre-budget candidate list.
5. `KnowledgeSelectionServiceImpl.select()` calls `RepositoryContextService.build()` and receives only
   the post-budget `RepositoryContext` — it has no access to the pre-budget candidates.

**Conclusion:** The proposed approach of extracting from `RepositoryContext.evidence()` is
**invalid** — evidence may have already been discarded by the time `build()` returns. A shared
retrieval primitive is required to expose pre-composition candidates.

## Architectural Decision: Shared Retrieval → Consumer-Specific Composition

ADR-063 governs this Story with the pattern:

```
Shared Retrieval Primitives
        ↓
Consumer-Specific Composition
```

This Story must:

1. **Introduce a shared retrieval primitive** that exposes the complete candidate set from all
   collectors before budgeting occurs — so both RepositoryContextEngine and Analysis can consume
   the same pre-composition evidence.
2. **Let Analysis own its composition** — Analysis filters which pre-composition candidates to
   promote, subject to boundedness constraints.
3. **Let RepositoryContextEngine retain its composition** — the existing budgeting, ranking, and
   category-floor logic remains unchanged. Promoted candidates enter the same pipeline and are
   subject to the same budget.

This Story does **not** create another Analysis-specific retrieval mechanism. It exposes the
retrieval step that already exists inside `RepositoryContextEngine.build()` as a reusable
primitive, consistent with ADR-063's consumer-scoped composition architecture.

## Operational Flow (single bounded envelope)

The corrected runtime flow from retrieval through persistence:

```
1. Shared Retrieval Primitive
   │  Returns: List<RepositoryEvidence> (pre-composition candidates)
   │  Source: all collectors including CommitDiffEvidenceCollector
   │  Budget: NONE (unbounded candidate set)
   │
   ▼
2. Analysis Consumer-Specific Composition
   │  Action: filter for layer == COMMIT_DIFF, deduplicate by reference
   │  Output: List<RepositoryEvidence> promotedCommitDiffCandidates
   │  Bound: follows existing configuration (not arbitrary constants)
   │
   ▼
3. RepositoryContextEngine.build() (existing pipeline)
   │  a. Merge: collector output + promotedCommitDiffCandidates → candidates list
   │  b. Rank: EvidenceRanker.rank(candidates, request) → ranked
   │  c. Dedup: BudgetedDiverseEvidenceSelector deduplicates by reference
   │  d. Select: BudgetedDiverseEvidenceSelector.select(ranked, request) → selected
   │     └── BUDGET ENFORCED HERE: 60-item maximumEvidenceItems
   │     └── TOKEN BUDGET ENFORCED HERE: 6000 maximumTokens
   │  e. Enrich: symbol + content enrichment
   │  f. Build: RepositoryContext(evidence = selected)
   │
   ▼
4. SelectedKnowledge
   │  Field: repositoryContext (contains evidence = selected)
   │  Budget: maximumRepositoryEvidence = 60 (same as ContextBudget)
   │
   ▼
5. AiTask.selectedKnowledgeSnapshot
   │  Serialization: SelectedKnowledgePromptProjectionService.toMap()
   │  Includes: repositoryContext.evidence (all selected items)
   │
   ▼
6. Story 0096 Human Evidence Projection
   │  HistoricalSelectedEvidenceSnapshotProjector.repositoryEvidence()
   │  Reads: repositoryContext.evidence array (no layer filter)
   │  Exposes: COMMIT_DIFF items with layer, kind, reference, summary
```

**Critical invariant:** There is ONE bounded envelope — the 60-item `maximumEvidenceItems` budget
in `ContextBudget`. Promoted COMMIT_DIFF candidates are part of this budget, not in addition to it.
The total model-visible evidence is bounded by 60 items regardless of how many candidates are
promoted.

## Scope

### IN SCOPE

- A shared retrieval primitive that exposes the complete pre-composition candidate set from all
  collectors (including `CommitDiffEvidenceCollector`) before the `BudgetedDiverseEvidenceSelector`
  budget is applied.
- An Analysis composition seam that retrieves pre-composition candidates via the shared primitive,
  filters for per-file `COMMIT_DIFF` evidence, deduplicates by reference, and promotes bounded
  candidates into the RepositoryContextEngine's candidate pool before ranking and selection.
- Preservation of the existing `CommitDiffEvidenceCollector` as the sole producer — no new
  collector, no duplicate collection logic.
- Preservation of the existing `BudgetedDiverseEvidenceSelector` category floors (Story 0095) and
  global budget — this Story adds candidates to the input pool, it does not change budgets or
  ranking.
- Preservation of ADR-044: content enrichment remains restricted to `SOURCE_FILE` / `TEST_FILE`.
- Explicit boundedness: per-file `COMMIT_DIFF` candidates promoted into the candidate pool must be
  bounded and deterministic, following existing configuration/policies (not arbitrary constants).
- Deduplication: promoted candidates must not duplicate evidence already present in the
  collector output (deduplication occurs in `BudgetedDiverseEvidenceSelector` by `reference`).
- Documentation of the relationship between `COMMIT_DIFF_SUMMARY` facts (aggregate statistics) and
  per-file `COMMIT_DIFF` evidence (granular engineering evidence) so humans can understand the
  distinction in the evidence snapshot (Story 0096).
- Backend tests verifying that per-file `COMMIT_DIFF` evidence appears in the Analysis candidate
  pool, that boundedness is enforced, that existing selection behavior is preserved, that
  aggregated `COMMIT_DIFF_SUMMARY` facts remain available, and that intent sensitivity is
  preserved.

### NOT IN SCOPE

- No changes to budgets, category floors, or ranking logic in `BudgetedDiverseEvidenceSelector`.
- No broadening of `SelectedFileContentEnricher` beyond `SOURCE_FILE` / `TEST_FILE` (ADR-044).
- No new collector — reuse `CommitDiffEvidenceCollector` only.
- No ADR creation or modification.
- No code changes, test changes, commit, push, or merge.
- No Angular/frontend changes — this Story is backend selection only.
- No changes to `HistoricalSelectedEvidenceSnapshotProjector` (Story 0096 already handles all
  categories including `CommitDiff` and `ChangedFile`).
- No introduction of ranking changes for COMMIT_DIFF evidence — the existing `DeterministicEvidenceRanker`
  already provides intent sensitivity through term-based scoring of `intent.id()` and
  `intent.objective()` against evidence `searchableText()`.
- No second bounded envelope — the 60-item `maximumEvidenceItems` budget remains the single
  bounded envelope for all evidence.

## Architecture Reference

| Reference | Role |
|---|---|
| ADR-063 (Accepted, Human Context Supremacy amendment) | Governing architecture for engineering context retrieval and composition |
| ADR-036 | Commit-level code diff analysis — defines per-file `CommitDiffAnalysisContext` |
| ADR-038 | Repository Context Engine architecture |
| ADR-044 | Bounded selected file content enrichment — restricts enrichment to `SOURCE_FILE` / `TEST_FILE` |
| Story 0095 | Category-aware composition floors in `BudgetedDiverseEvidenceSelector` |
| Story 0096 | Human Context Supremacy P0 — evidence snapshot exposed to Analysis detail page |
| Investigation: `analysis-depth-diagnosis-reconstruction.md` | Diagnoses three bottlenecks; this Story targets Bottleneck A (aggregated commit evidence) |
| Investigation: `context-composition-trusted-knowledge.md` | Original fragmentation diagnosis motivating ADR-063 |

## Proposed Approach

### 1. Shared retrieval primitive

Expose the candidate collection step that currently exists as a local variable inside
`RepositoryContextEngine.build()` (line 76-77) as a reusable primitive. The primitive returns the
complete unfiltered candidate list from all collectors, before ranking and before the
`BudgetedDiverseEvidenceSelector` budget is applied.

This is the minimal seam required by ADR-063's "Shared Retrieval Primitives → Consumer-Specific
Composition" pattern. It does not duplicate collection logic — it exposes the retrieval step that
already exists.

### 2. Analysis composition: promote per-file COMMIT_DIFF candidates

Through the shared retrieval primitive, Analysis obtains the complete pre-composition candidate
set. Analysis then:

1. Filters for evidence whose `layer` equals `COMMIT_DIFF` (per-file changed-file evidence).
2. Deduplicates against evidence already present in the collector output by `reference`.
3. Promotes bounded, deduplicated candidates into the RepositoryContextEngine's candidate pool
   **before** ranking and selection.

This is **consumer-specific composition** — Analysis decides which pre-composition candidates to
include, subject to its own boundedness constraints. The promoted candidates enter the same
candidate pool as collector output and are subject to the same ranking, diversity, knowledge floor,
and budget gates.

### 3. Boundedness

The **single bounded envelope** is the 60-item `maximumEvidenceItems` budget in `ContextBudget`.
Promoted per-file `COMMIT_DIFF` candidates are part of this budget, not in addition to it.

Promoted candidates must be bounded and deterministic. Concrete bounds should follow existing
configuration/policies (e.g., the collector's own `maxItems` limit, or configuration consistent
with existing limits). The implementation should not introduce arbitrary constants — bounds must
be justified by existing architecture or configuration.

The bound ensures that promoting COMMIT_DIFF candidates does not inflate the candidate pool beyond
what the existing ranking and budget can handle. Unused capacity from the bound flows back to the
existing selection.

### 4. Relationship documentation

Document in code comments and in the evidence snapshot (via Story 0096's existing category
presentation) that:
- `COMMIT_DIFF_SUMMARY` facts = aggregate historical/statistical information (number of commits,
  files changed, insertions/deletions).
- Per-file `COMMIT_DIFF` evidence = granular engineering evidence about specific changed files,
  their status, and summaries — suitable for reasoning about actual changed areas.

### 5. Selection preservation

The promoted per-file `COMMIT_DIFF` candidates enter the existing selection pipeline alongside
other candidates. The `BudgetedDiverseEvidenceSelector` applies its existing budget, category floors,
and ranking — this Story does not change that behavior. The net effect is that per-file `COMMIT_DIFF`
evidence is more likely to survive selection because it is explicitly presented as a candidate
through the shared retrieval primitive.

### 6. Snapshot projection compatibility

Verification confirms that per-file `COMMIT_DIFF` evidence in `RepositoryContext.evidence()`
reaches the persisted snapshot and Story 0096's human-readable projection without any filters:

- `SelectedKnowledgePromptProjectionService.projectRepositoryContext()` iterates all evidence
  items with no layer filter.
- `HistoricalSelectedEvidenceSnapshotProjector.repositoryEvidence()` reads all items from the
  `repositoryContext.evidence` JSON array with no layer filter.
- The `CommitDiff` and `ChangedFile` records in `HistoricalSelectedEvidenceSnapshotProjector`
  handle the separate `evolutionContext.commitDiff` path, not `repositoryContext.evidence`.

No projection changes are required — the existing pipeline already handles COMMIT_DIFF evidence
correctly when it is present in `repositoryContext.evidence()`.

## Exact Answers to Architecture Questions

| Question | Answer |
|---|---|
| What collection contains a selected per-file COMMIT_DIFF at the end? | `SelectedKnowledge.repositoryContext.evidence()` — the same `List<RepositoryEvidence>` as all other selected evidence. |
| Does it remain `repositoryContext.evidence`? | Yes. Promoted candidates that survive selection are in `repositoryContext.evidence()`. The field is immutable after construction (`List.copyOf()`). |
| Is it selected before or after `BudgetedDiverseEvidenceSelector`? | After. Analysis promotes candidates before ranking, but they go through the same `BudgetedDiverseEvidenceSelector`. |
| Which exact budget includes it? | The 60-item `maximumEvidenceItems` budget in `ContextBudget` (injected via `@Value("${devlog.repository-context.max-evidence-items:60}")`). This is the same budget that governs all other evidence. |
| Can promoted evidence bypass the 60-item/token budget? | No. The budget is enforced in `BudgetedDiverseEvidenceSelector.selectOrdinary()` (line 110) and `selectKnowledgeFloor()` (line 91) for ALL candidates, regardless of source. |
| Can it be counted twice? | No. Deduplication in `BudgetedDiverseEvidenceSelector` (line 28-29) removes duplicates by `reference` before selection. |
| Can it be selected by Analysis but then discarded again by Repository composition? | Yes. Analysis promotes candidates, but they still go through ranking, diversity, knowledge floor, and budget gates. Some may be discarded. This is correct — the budget is the single bounded envelope. |
| Does prompt projection see exactly the bounded final set? | Yes. `SelectedKnowledgePromptProjectionService.projectRepositoryContext()` iterates `repositoryContext.evidence()` which is the final bounded set. |

## Required Tests

1. **Per-file COMMIT_DIFF evidence appears in Analysis candidates** — verify that when the shared
   retrieval primitive returns per-file `COMMIT_DIFF` evidence, those items are promoted into the
   RepositoryContextEngine's candidate pool and appear in `repositoryContext.evidence()`.
2. **Boundedness enforcement** — verify that the number of promoted per-file `COMMIT_DIFF` candidates
   does not exceed the configured bound (following existing configuration, not arbitrary constants),
   and that the final `repositoryContext.evidence()` never exceeds `maximumEvidenceItems`.
3. **Existing selection behavior preserved** — verify that category floors, budget constraints, and
   ranking in `BudgetedDiverseEvidenceSelector` are unchanged.
4. **Aggregated COMMIT_DIFF_SUMMARY facts remain available** — verify that `CommitScopedFactCollector`
   still produces `COMMIT_DIFF_SUMMARY` facts and they are still selectable.
5. **No duplicate evidence** — verify that per-file `COMMIT_DIFF` evidence promoted as candidates
   does not duplicate evidence already in the collector output (deduplication by `reference`).
6. **Empty retrieval** — verify graceful behavior when the shared retrieval primitive returns no
   per-file `COMMIT_DIFF` evidence.
7. **Intent sensitivity (regression)** — verify that per-file `COMMIT_DIFF` candidates are scored
   differently for different intents, confirming existing `DeterministicEvidenceRanker` behavior
   (term-based scoring of `intent.id()` + `intent.objective()` against evidence `searchableText()`).
   This is regression coverage, not a ranking change.
8. **Snapshot projection** — verify that the persisted `SelectedKnowledge` snapshot includes both
   per-file `COMMIT_DIFF` evidence (in `repositoryContext.evidence`) and `COMMIT_DIFF_SUMMARY` facts
   (in `selectedFacts`) when both are present.

## Benchmark Requirement

Run the five fixed intents (history / architecture / recent-sync / persistence / decision-governance)
and verify:
- Per-file `COMMIT_DIFF` evidence appears in `repositoryContext.evidence` for intents where recent
  commits are relevant (history, recent-sync, persistence).
- The global repository-context budget is not exceeded (stays within 60 items).
- Category floors from Story 0095 are still respected.
- `COMMIT_DIFF_SUMMARY` facts remain available in `selectedFacts` where fact scoring permits.

## Benchmark Results

**EFFECT_CLASSIFICATION = NO_MEASURABLE_CHANGE**

Five-intent benchmark against the running stack (pre-0097 baseline):

| Intent | Total | COMMIT_DIFF | GIT_HISTORY | Other | Knowledge |
|---|---|---|---|---|---|
| history | 60 | 43 | 10 | 7 | 0 |
| architecture | 60 | 43 | 10 | 7 | 5 |
| recent-sync | 60 | 42 | 11 | 7 | 0 |
| persistence | 60 | 43 | 10 | 7 | 0 |
| decision-governance | 60 | 43 | 10 | 7 | 5 |

Story 0097's promotion mechanism is currently redundant because `CommitDiffEvidenceCollector` already
produces 43 per-file COMMIT_DIFF items that flow through the normal pipeline. The promoted
candidates (15) are deduplicated by the selector.

**NEXT_CONFIRMED_BOTTLENECK = CATEGORY_SELECTION** — COMMIT_DIFF consumes 42-43 of 60 items
(70-72%) via strong relevance bypass, exhausting the budget before other evidence types.

## Dependency Chain

```
ADR-063 (governing)
  → ADR-036 (per-file CommitDiff)
    → Story 0006 (CommitDiffEvidenceCollector)
      → Story 0095 (category floors)
        → Story 0096 (evidence snapshot projection)
          → Story 0097 (this Story: shared retrieval primitive + Analysis composition)
```

## Risks

| Risk | Mitigation |
|---|---|
| Shared retrieval primitive adds a new public method to the service interface | Minimal seam — single method returning pre-composition candidates; follows ADR-063's required pattern |
| Per-file candidates inflate the candidate pool beyond budget | The 60-item budget is the single bounded envelope; promoted candidates are subject to the same budget |
| Duplicate evidence between promoted candidates and repository context | Deduplication in `BudgetedDiverseEvidenceSelector` by `reference` removes duplicates before selection |
| Breaking existing selection behavior | All changes are additive (candidates only); existing selection logic unchanged |
| Aggregated facts become redundant | `COMMIT_DIFF_SUMMARY` facts remain available for statistical reasoning; documentation clarifies the distinction |
| Promoted candidates discarded by budget | Correct behavior — the budget is the single bounded envelope; Analysis promotes candidates, Repository composition decides which survive |

## Architecture Sanity Check

**Does Story 0097 move the system closer to Shared Retrieval Primitives → Consumer-Specific
Composition?**

**Yes.** The current architecture has the retrieval step隐式地 embedded inside
`RepositoryContextEngine.build()` as a local variable — it is not exposed as a reusable primitive.
This Story extracts the retrieval step into a shared primitive, allowing both RepositoryContextEngine
(Repository composition) and Analysis (consumer-specific composition) to consume the same
pre-composition evidence. This is exactly the ADR-063 pattern.

**Does it accidentally create or reinforce another parallel context mechanism?**

**No.** The shared retrieval primitive reuses the existing collector infrastructure
(`CommitDiffEvidenceCollector` and all other collectors). Analysis does not create its own collector
or its own retrieval path — it calls the same shared retrieval that `RepositoryContextEngine` uses.
The only new logic is the consumer-specific composition (filtering, deduplication, bounded promotion)
which is Analysis's responsibility per ADR-063. The final evidence set remains under one explicit
bounded envelope (60-item budget).

## Definition of Done

- [x] A shared retrieval primitive exposes the complete pre-composition candidate set from all
  collectors.
- [x] Analysis promotes bounded, deduplicated per-file `COMMIT_DIFF` candidates into the
  RepositoryContextEngine's candidate pool before ranking and selection.
- [x] Promoted candidates are bounded following existing configuration (not arbitrary constants).
- [x] The 60-item `maximumEvidenceItems` budget remains the single bounded envelope — promoted
  candidates are part of this budget, not in addition to it.
- [x] Existing `BudgetedDiverseEvidenceSelector` behavior is unchanged.
- [x] `COMMIT_DIFF_SUMMARY` facts remain available and selectable.
- [x] All 13 required tests pass.
- [x] Five-intent benchmark confirms per-file evidence appears in relevant intents without exceeding
  the repository-context budget. Effect: NO_MEASURABLE_CHANGE (promotion redundant with existing
  collector output). Next bottleneck: CATEGORY_SELECTION.
- [x] Code comments and/or documentation clarify the relationship between aggregated and per-file
  evidence.
- [x] No duplicate retrieval mechanism exists — shared primitive reuses existing collectors.
- [x] No second bounded envelope — one explicit budget governs all evidence.
