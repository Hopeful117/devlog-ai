# Repository Analysis - Story 0181

## Scope Reviewed

This analysis covers the current Story Context loss ledger, repository context
selection, agent projection, evaluation runner, and B6000 runtime captures at
revision `2a92d65b42e41819b4e3bd915cf486d2f1bc8b83`.

The DevLog Story Agent was not available in this session. The repository,
accepted ADRs, Story 0179/0180 artifacts, and live runtime evidence were used as
authoritative sources for current state.

## Current Evidence

- The B6000 campaign is readiness-valid: 12 captures, one revision, unique task
  identities, and 11 completed analyses plus one timeout.
- Recorded loss reasons contain `5,958` category-concentration losses out of
  `6,906` total loss-reason occurrences, approximately 86%.
- The current ledger has `maximumDetailEntries = 256` and a non-zero overflow in
  the live capture.
- Ledger entries are sorted by reference before truncation. In a targeted smoke
  capture, the bounded entries were dominated by selection decisions and did not
  contain the explicitly requested `docker-compose.yml` evidence.
- The evaluation runner now preserves bounded ledger entries and requested-file
  metadata, but this cannot recover information omitted by Core ledger truncation.

## Relevant Implementation

### Core Ledger

`backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/ContextLossLedger.java`
constructs the versioned, digest-covered ledger. It currently records per-entry
reference, kind, originating file, revision, trust, selection, enrichment,
projection, grounding, and loss metadata, but limits the serialized detail list
to 256 entries after sorting.

### Selection

`backend/src/main/java/com/hopeful117/devlogai/repositorycontext/selection/BudgetedDiverseEvidenceSelector.java`
applies kind allowance, category ceiling, relevance, item, and token gates. The
current category ceiling is derived from
`EvidencePrecisionPolicy.maximumCategorySharePercentage()` and must not change in
this Story.

### Projection

`backend/src/main/java/com/hopeful117/devlogai/projectcontext/projection/AgentContextProjectionService.java`
compacts the selected context under projection limits. Requested-file survival
must distinguish selection from projection and grounding; a selected item is not
automatically projected or grounded.

### Evaluation

`ai-engine/evaluations/story_context_budget_live.py` extracts bounded snapshots
and `story_context_runtime_report.py` aggregates readiness and loss accounting.
The runner can consume additional Core telemetry but must not infer missing
candidate distributions from aggregate loss counts.

## Contract Gap

The existing ledger detail list is suitable for bounded diagnostics but not for
category or requested-file measurement under high candidate counts. Increasing
the list bound would be an unvalidated budget change and would not guarantee
requested-file survival. The smallest coherent contract extension is a separate
bounded summary section containing deterministic distributions and requested-file
outcomes, covered by the ledger digest.

The summary must be constructed by Core from the same authorized context and
selection decisions as the existing ledger. The AI Engine should only transport
and aggregate it.

## Proposed Telemetry Shape

The exact DTO names remain subject to the Implementation Plan, but the required
semantic fields are:

- `categoryDistribution`: bounded counts keyed by evidence kind/category and
  stage (`CANDIDATE`, `SELECTED`, `PROJECTED`, `GROUNDED`, `REJECTED`).
- `requestedFileOutcomes`: bounded rows keyed by normalized requested path with
  `FOUND`, `SELECTED`, `PROJECTED`, `GROUNDED`, and an explicit bounded reason.
- `trustDistribution`: bounded counts by trust tier and stage.
- `grounding`: bounded authorized/projected/grounded counts and status.

No content, prompt, provider payload, or arbitrary model-supplied value belongs
in this section.

## Risks

- Exposing raw paths beyond the request scope could leak repository structure;
  requested paths must be normalized and bounded.
- A summary computed after projection could incorrectly describe selection;
  stage boundaries must be explicit.
- Trust counts must come from Core's authoritative trust map, never from AI
  output or evaluation heuristics.
- A contract version or digest change can invalidate existing capture reports;
  the runner must classify old and new versions separately.
- The current worktree contains unrelated uncommitted Story 0180 and agent-fix
  changes; implementation must avoid rewriting or staging them.

## Validation Direction

Before implementation planning is approved, validate the proposed shape against:

1. existing ledger construction and digest tests;
2. selection and projection stage boundaries;
3. requested-file matching semantics;
4. trust and grounding authority tests;
5. redaction and maximum-size tests;
6. offline runner aggregation with missing and overflow telemetry;
7. a bounded live smoke capture after the contract is implemented.

## Recommendation

Proceed to Implementation Planning only after human approval of this Story and
Repository Analysis. Do not change selection policy, context budgets, provider
behavior, or retrieval architecture as part of Story 0181.
