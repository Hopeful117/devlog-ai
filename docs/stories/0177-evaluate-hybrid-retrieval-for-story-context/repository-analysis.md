# Repository Analysis - Story 0177

## Status

Repository Analysis completed. Human approval is required before producing the
Implementation Plan. No implementation is authorized by this artifact.

## Story Scope

Story 0177 evaluates whether a bounded hybrid retrieval channel can improve
evidence recall for Story Context without increasing noise, prompt size,
latency, timeout rate, revision leakage, or grounding risk. It is evaluation
infrastructure only. It does not authorize production RAG, embeddings, vector
search, retrieval changes, budget changes, or changes to the Java Core/Python AI
Engine boundary.

## Repository and DevLog Context

- Repository: `devlog-ai`
- Branch: `main`
- HEAD at analysis: `0630e42d88266592d19e397ab538f7424c47b5c2`
- Story: `0177-evaluate-hybrid-retrieval-for-story-context`
- Governing ADRs: `ADR-006`, `ADR-063`, `ADR-066`, `ADR-067`
- Related Stories: `0111`, `0112`, `0158`, `0173`, `0176`

The DevLog Story Agent applicability request was attempted but the MCP
connection closed before returning context. Repository sources are therefore
authoritative for this analysis. No Obsidian context was required.

The worktree contains pre-existing uncommitted changes in repository-context
selection, ranking, enrichment, tests, Docker configuration, benchmark scripts,
notebooks, Playwright traces, and OAuth network output. These changes are not
part of Story 0177 and must not be reverted or silently included.

## Governing Constraints

### ADR-063

ADR-063 separates retrieval, composition, projection, grounding, and expansion.
Retrieval returns annotated candidates; consumer-owned composition remains
responsible for budgets and category policy. Trust tiers survive retrieval, and
similarity never elevates trust. Future lexical, structured, temporal,
relation, or vector channels must emit canonical references and remain
revision-scoped. Vector/RAG infrastructure is explicitly deferred until
retrieval semantics stabilize.

### ADR-066

The evaluation harness is infrastructure outside production behavior. The
dependency direction is `evaluation -> production contracts`, never the
reverse. Replay is the primary deterministic mode; live model execution is
optional and must not become normal pull-request CI. Evaluation results do not
become trusted project knowledge.

### ADR-067 and Story 0112

Java/Core exclusively owns project scope, authorization, context construction,
trust, grounding, and acceptance. Python receives immutable authorized input
and must not access repositories, databases, indexes, or embeddings. Story 0112
explicitly excludes RAG, embeddings, and vector search from its V1.

## Current Retrieval and Context Path

```text
Story Context request
  -> RepositoryContextAdapter
  -> RepositoryContextEngine candidate collectors
  -> DeterministicEvidenceRanker
  -> BudgetedDiverseEvidenceSelector
  -> file/symbol/document enrichment
  -> bounded RepositoryContext projection
  -> Story Context grounding contract and AI projection
  -> Python generation and defensive validation
  -> Java/Core callback acceptance
```

### Core Story Context preparation

`backend/src/main/java/com/hopeful117/devlogai/projectcontext/RepositoryContextAdapter.java`
constructs the Story Context repository input, resolves source and revision
scope, passes requested files as deterministic guidance, applies Story scope,
and maps the resulting repository context into the authorized projection.

### Candidate collection and composition

`backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java`
owns candidate collection, ranking, selection, enrichment, diagnostics,
accounting, and truncation. Relevant producers include:

- `GitHistoryContextCollector`;
- `CommitDiffEvidenceCollector`;
- `DocumentBodyCollector`;
- structure, knowledge, and source-code collectors.

`DeterministicEvidenceRanker` currently uses lexical term overlap plus
architecture, history, recency, confidence, and guidance signals.

`BudgetedDiverseEvidenceSelector` applies requested-file selection, diversity,
category floors/ceilings, item limits, token limits, and explicit rejection
reasons. The recent live measurements show that increasing the item ceiling
from 60 to approximately 120 increases the estimated provider input from about
70k to 150k tokens and causes targeted scenarios to time out.

### Shared retrieval boundary

The following initial seam exists:

- `backend/src/main/java/com/hopeful117/devlogai/evidence/retrieval/SharedEvidenceRetrievalBoundary.java`;
- `SharedEvidenceRetrievalBoundaryImpl.java`;
- `EvidenceRetrievalQuery.java`;
- `EvidenceRetrievalScope.java`;
- `EvidenceCandidate.java`;
- `GitCommitRetrievalAdapter.java`.

The current implementation supports only `GIT_COMMIT` retrieval with a
validated lexical query and pagination. It is not wired as the universal source
for `RepositoryContextEngine`, and it does not yet provide a semantic,
document, source-file, or hybrid candidate producer. This seam is a possible
future integration point, not an implementation authorization for this Story.

## Existing Evaluation Infrastructure

The durable evaluation harness is under `ai-engine/evaluations/` and is
covered by `ai-engine/tests/test_evaluation_harness.py`. It supports versioned
scenario inputs, reviewed outputs, deterministic structural/semantic/grounding
checks, and reports. It does not currently measure Core candidate recall.

Relevant existing scenarios and precedents:

- `ai-engine/evaluations/scenarios/engineering-story-context-analysis-v1/`;
- `ai-engine/evaluations/scenarios/story-0158-specificity-v1/`;
- `ai-engine/evaluations/story_context_specificity.py`;
- `ai-engine/evaluations/story_context_budget_live.py`;
- `notebooks/evaluation/story-context-selection-enrichment-benchmark.ipynb`.

The Story 0158 manifest already defines broad, targeted, insufficient-evidence,
and truncated-context questions with expected evidence. The recent live runner
records budgets, warnings, digests, and timing, but not the complete selected
and discarded reference sets needed for recall evaluation.

## Gaps Required by Story 0177

The following artifacts do not currently exist and are required:

- Story 0177-specific frozen corpus and expected-evidence manifest;
- evidence-level capture containing candidate, selected, and discarded
  canonical references;
- selection and rejection reason capture by condition;
- requested-file and document coverage metrics;
- revision, trust-tier, citability, and grounding safety checks per candidate;
- offline candidate-producer comparison seam;
- semantic producer test double or offline adapter not wired into production;
- metric aggregation for recall, noise, grounding, size, latency, timeouts, and
  reproducibility;
- revision-mismatch, out-of-scope, superseded-document, and insufficient-
  evidence negative controls;
- decision report separating retrieval, selection, enrichment, projection, and
  provider bottlenecks.

## Affected Modules and Files

### Primary

- `ai-engine/evaluations/`
  - scenario corpus, capture model, candidate-condition comparison, metrics,
    and decision reporting.
- `ai-engine/tests/`
  - deterministic fixture validation, metric aggregation, and safety tests.
- `backend/src/main/java/com/hopeful117/devlogai/evidence/retrieval/`
  - only if the evaluation needs an additive, production-independent contract
    seam or metadata adapter; no production semantic retrieval is implied.
- `backend/src/test/java/com/hopeful117/devlogai/evidence/retrieval/`
  - existing boundary and canonical-reference safety coverage.

### Secondary, requiring explicit plan justification

- `backend/.../repositorycontext/`
  - only for read-side capture or an adapter that does not alter runtime
    selection behavior.
- `backend/.../storycontextanalysis/`
  - only if authorized snapshots cannot expose the required accounting without
    a bounded additive evaluation field.
- `ai-engine/evaluations/story_context_budget_live.py`
  - useful precedent, but changes must remain evaluation-only and must not
    weaken live-run validity rules.

No MCP, REST, provider, database migration, Docker runtime, or Python
repository-access change is currently justified.

## Required Evaluation Design

The implementation plan must preserve this bounded pipeline:

```text
frozen project/revision scope
  -> candidate condition
  -> deterministic project/source/revision/trust filters
  -> canonical candidate union
  -> existing bounded composition
  -> projection and grounding checks
  -> offline metrics and report
```

The control condition must use the current structured/lexical behavior. An
experimental semantic condition may use an offline adapter or test double, but
must not be imported by production runtime code. Candidate identity and
revision/trust metadata must be checked before measuring recall.

## Risks and Unknowns

- A semantic producer may increase recall while making final composition and
  provider latency worse, as the C1/C5 budget experiments already suggest for
  larger candidate sets.
- Live Story Context captures currently summarize accounting but do not expose
  enough evidence identity to calculate recall directly.
- A live embedding provider would undermine deterministic replay and introduce
  model/version/cost dependencies; the evaluation must separate offline replay
  from optional manual live validation.
- Semantic similarity may select a superseded or wrong-revision document unless
  hard filters run before ranking.
- The current uncommitted repository-context changes overlap the code surface
  under investigation. A fixed baseline and explicit attribution are required.
- A positive recall result would still not authorize production vector/RAG
  infrastructure; it would only justify a separate architecture decision.

## Validation Inputs

The Implementation Plan should include, as applicable:

- AI Engine evaluation tests and report validation;
- backend retrieval-boundary and canonical-reference tests;
- Story Context grounding and budget regression tests;
- replay of all frozen positive and negative controls;
- comparison of candidate-level and final-context-level recall;
- verification that production runtime behavior and contracts are unchanged;
- full relevant test suites when the evaluation implementation is complete.

## Analysis Result

Story 0177 is technically feasible as evaluation infrastructure. The repository
already provides a suitable deterministic harness and a partial Core retrieval
boundary, but it lacks evidence-level capture and a candidate-condition
comparison model. The first implementation slice should therefore be the
versioned corpus, capture contract, and deterministic control metrics. A
semantic producer should remain offline and replaceable.

No production implementation files were modified for this analysis. The
pre-existing uncommitted worktree changes were not reverted or included.

## Next Gate

`WAITING_FOR_ANALYSIS_APPROVAL`

Human approval of this Repository Analysis is required before producing the
Implementation Plan.
