# Story 0178 - Story Context Loss-Aware Composition and Projection

## Status

**IMPLEMENTATION AUTHORIZED - AWAITING HUMAN ACCEPTANCE**

This Story is derived from the Story 0177 retrieval evaluation and the
composition/enrichment/projection investigation. Offline implementation is
authorized by the human approval gates completed for this Story. Story
acceptance and deployment remain subject to the established human approval
gates.

## Problem

Story Context currently has several independent bounded stages:

```text
candidate collection
  -> ranking and selection
  -> symbol/file enrichment
  -> repository-context projection
  -> prompt preflight
  -> provider execution
```

Each stage has its own budget and can remove or reduce information. The current
diagnostics expose aggregate counts and digests, but do not provide a complete
per-reference loss ledger. As a result, an operator can know that a context was
truncated without knowing whether the answer-critical evidence was rejected by
selection, skipped during enrichment, removed by projection, or never made
provider-visible.

The Story 0177 offline evaluation showed that structural retrieval already
reached `1.0` recall on its frozen corpus, while semantic/hybrid conditions
increased noise. The prior runtime investigation also observed hundreds of
candidates reduced to 60 and provider completion failures that were independent
of Core projection truncation. The strongest current hypothesis is therefore a
composition, enrichment, projection, and multi-layer budget problem rather than
an established need for production hybrid retrieval.

## Goal

Make Story Context information loss measurable and actionable across selection,
enrichment, projection, prompt preflight, grounding, and provider execution,
then use that evidence to identify the next bounded improvement without
weakening Core authority or fail-closed behavior.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-069 - Canonical Engineering Context Boundary
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0112 - DevLog Engineering Story Context Agent
- Story 0173 - Story Context Budget Observability
- Story 0175 - Story Context Complex Response Completion
- Story 0176 - Story Context Grounding and Retry Efficiency
- Story 0177 - Evaluate Hybrid Retrieval for Story Context
- `docs/investigations/story-0177-composition-enrichment-projection-investigation.md`
- `docs/investigations/story-context-truncation-and-provider-incomplete-response.md`

## Architectural Constraints

1. Java/Core remains the sole authority for scope, revision, candidate identity,
   ranking, selection, enrichment authorization, projection, grounding, budget
   policy, and provider-call authorization.
2. Python receives only the Core-authorized projection and must not reconstruct
   context, access repositories, access the database, or access retrieval
   indexes.
3. Diagnostics must be bounded and non-sensitive. They must not contain
   credentials, authorization headers, full prompts, raw provider output, or
   unbounded repository payloads.
4. Existing context, selection, grounding, and projection digests remain
   authoritative. The ledger must not introduce provider-generated identity.
5. Incomplete provider output remains a terminal failure and must never be
   parsed or persisted as successful analysis.
6. Similarity scores, diagnostics, or budget pressure must never change trust,
   revision validity, citability, grounding eligibility, or human validation
   semantics.
7. No production RAG, embeddings, vector database, semantic retrieval channel,
   autonomous follow-up retrieval, or generic agent loop is introduced.
8. No global context, item, enrichment, projection, or provider budget is
   increased by this Story. Any future budget change requires separate measured
   approval.

## In Scope

- Define a versioned, evaluation-safe loss ledger for a Story Context execution.
- Track bounded per-reference stage outcomes for candidates that enter the
  Core pipeline, including:
  - collected;
  - ranked;
  - selected or rejected;
  - enriched, skipped, truncated, or unavailable;
  - projected, compacted, or removed;
  - included in the grounding allow-list;
  - visible to the provider when technically measurable.
- Record one normalized reason and relevant bounded budget for each material
  loss, such as:
  - relevance threshold;
  - category or diversity policy;
  - item budget;
  - repository-context token budget;
  - enrichment file/character/token budget;
  - projection byte/token budget;
  - projection compaction step;
  - prompt input capacity;
  - provider output allowance;
  - provider incomplete response.
- Distinguish candidate count, unique candidate count, selected count,
  enriched count, projected count, grounded count, and provider-visible count.
- Preserve category, evidence family, originating file, revision, trust tier,
  citability, and bounded provenance for ledger entries.
- Expose bounded aggregate diagnostics in the existing Story Context snapshot,
  interaction trace, or evaluation capture surfaces without duplicating the
  retrieval universe.
- Measure context and projection size at every reliable boundary, including
  units and certainty (`EXACT`, `ESTIMATED`, or `UNKNOWN`).
- Compare targeted, broad, multi-source, empty/insufficient, and deliberately
  truncated Story Context scenarios.
- Evaluate whether requested-file priority survives all downstream stages.
- Evaluate whether enrichment increases cost without improving the final
  provider-visible evidence.
- Evaluate whether projection compaction removes answer-critical content while
  preserving evidence identity.
- Reuse the Story 0177 offline corpus and add bounded stage expectations where
  appropriate.
- Produce a decision report identifying the dominant loss stage and the next
  recommended improvement.

## Out of Scope

- Production hybrid retrieval, RAG, embeddings, vector search, or a vector
  database.
- Changing retrieval collectors or adding a semantic candidate producer to the
  runtime.
- Increasing production context, item, enrichment, projection, or provider
  budgets.
- Replacing deterministic selection with a similarity score.
- Python-side repository access, database access, authorization, selection, or
  context construction.
- Weakening grounding, trust, revision, citability, or requested-file rules.
- Parsing, persisting, or retrying incomplete provider output as success.
- Removing corrective retries solely to improve latency.
- Broad prompt redesign unrelated to measured stage loss.
- Unrelated backend, frontend, Docker, MCP, OAuth, benchmark, or Playwright
  changes.

## Evaluation Model

```text
frozen scope/revision
  -> candidates
  -> ranked candidates
  -> bounded selection
  -> enrichment outcome
  -> projection outcome
  -> grounding allow-list
  -> prompt preflight
  -> provider terminal state
```

The ledger must preserve the distinction between:

- evidence not retrieved;
- evidence retrieved but rejected by Core selection;
- evidence selected but not enriched;
- evidence enriched but compacted or removed from projection;
- evidence projected but rejected by grounding;
- evidence authorized but unavailable to the provider because of prompt or
  provider execution failure.

## Acceptance Criteria

### AC-1 - Loss ledger is bounded and revision-safe

Every measured execution has a versioned ledger summary and bounded per-reference
entries. Entries preserve canonical reference, project/source/revision identity,
evidence family, trust tier, citability, originating file, stage outcome, and
loss reason where applicable. A candidate from another revision cannot be
reported as valid provider-visible evidence.

### AC-2 - Selection loss is explainable

The evaluation distinguishes collected, unique, ranked, selected, rejected,
discarded, and duplicate counts. Rejected candidates carry deterministic
reasons matching existing Core selection semantics. The ledger does not infer
that discarded evidence was relevant merely because it was collected.

### AC-3 - Enrichment loss is explainable

For selected file and symbol evidence, the evaluation records whether content
was enriched, skipped, truncated, unavailable, or unchanged. It records the
bounded enrichment reason and before/after estimated token values when
available. Requested-file evidence is tested through the enrichment stage.

### AC-4 - Projection loss is explainable

The evaluation records projection input/output counts, bytes, estimated tokens,
digests, warning state, and compaction/removal steps. Evidence removed from the
projection is distinguishable from evidence that was never selected. Tail
evidence removal and all-evidence removal remain fail-closed and explicit.

### AC-5 - Prompt/provider budgets remain separate

Selection tokens, enrichment cost, projection size, prompt input estimate,
authorized output allowance, model capacity, and provider usage are reported as
separate measurements with explicit units and certainty. A test proves that
Core truncation, prompt preflight rejection, and provider incomplete output are
different terminal conditions.

### AC-6 - Grounding and trust safety do not regress

All ledger conditions preserve project/source/revision scope, trust exclusions,
canonical references, citability, requested-file constraints, grounding
allow-lists, and insufficient-evidence abstention. Unknown, superseded,
out-of-scope, or wrong-revision references cannot become provider-visible
grounding evidence.

### AC-7 - Composition hypotheses are tested

The replay includes targeted, broad, multi-source, insufficient, and truncated
cases and reports:

- answer-expected evidence at candidate and provider-visible levels;
- requested-file survival through every stage;
- enrichment completion and truncation rates;
- projection compaction and removal rates;
- grounding acceptance;
- prompt input and output budget outcomes;
- provider calls, latency, timeout, and incomplete response status when
  available;
- deterministic replay and digest stability.

No condition is considered better based on recall alone.

### AC-8 - Decision report identifies the dominant stage

The report explicitly classifies the next bottleneck as one or more of:

- retrieval recall;
- candidate selection;
- content enrichment;
- projection;
- prompt preflight;
- provider execution.

It states whether a hybrid retrieval Story remains justified after accounting
for downstream loss. It must not approve a retrieval technology or production
architecture.

### AC-9 - Existing contracts and runtime behavior remain stable

Existing REST, MCP, callback identity, grounding, trust, and projection digest
contracts remain compatible. Diagnostics are additive or evaluation-only. No
production selection outcome changes unless separately authorized by a later
Story.

### AC-10 - Deterministic validation is available without credentials

CI can replay the stage-loss scenarios without network access or provider
credentials. Live captures are optional, bounded, revision-pinned, and clearly
separated from deterministic CI evidence.

## Expected Deliverables

- Versioned loss-ledger schema and bounded capture model.
- Extended Story 0177 scenarios or compatible stage-loss fixtures.
- Deterministic tests for selection, enrichment, projection, grounding, and
  provider-budget separation.
- Stage-level metrics and JSON/text report output.
- Decision report identifying the next bottleneck.
- Explicit recommendation on whether hybrid retrieval should be deferred or
  reconsidered after composition improvements.

## Validation

- Run focused Core selection, enrichment, projection, grounding, and budget
  tests.
- Replay Story 0177 and the new stage-loss fixtures.
- Run existing Story Context and provider-incomplete tests.
- Verify no retrieval, embedding, vector, MCP, REST, or Python boundary changes.
- Inspect the complete diff and confirm diagnostics contain no secrets, full
  prompts, raw provider output, or unbounded repository content.

## Human Decisions Required

- Approve or reject the Story scope.
- Decide whether bounded stage-level diagnostics may be persisted in execution
  snapshots, interaction traces, or only evaluation artifacts.
- Decide which measured loss stage should receive implementation priority after
  the evaluation.
- Reconsider hybrid retrieval only if real, revision-pinned evidence shows a
  persistent answer-critical recall gap after downstream stages are measured.

## Non-Authorization Notice

Completion of this Story, a passing evaluation, or a positive recommendation
does not authorize production hybrid retrieval, RAG, embeddings, vector search,
budget increases, or changes to the Java Core/Python AI Engine boundary. Any
such work requires separate architectural approval and implementation
authorization.
