# Story 0179 - Story Context Runtime Loss Evaluation

## Status

**IMPLEMENTATION AUTHORIZED - AWAITING HUMAN ACCEPTANCE**

This Story defines a bounded runtime evaluation of the Story Context composition
and provider path after Stories 0177 and 0178. It does not authorize production
policy changes, budget changes, retrieval changes, or provider changes.

## Problem

The first live capture after the Story 0178 merge completed successfully and
recorded the new loss ledger. It showed 605 candidates reduced to 60 selected
and projected references, with losses dominated by category concentration,
item-budget rejection, and insufficient relevance. The capture also exposed a
real provider timeout in the broader four-scenario runner, but one run is not
enough to distinguish stable composition pressure from runtime variance.

## Goal

Produce revision-pinned runtime evidence for the four existing Story Context
scenarios and classify the dominant loss or execution stage without changing
Core policies or provider behavior.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-069 - Canonical Engineering Context Boundary
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0177 - Evaluate Hybrid Retrieval for Story Context
- Story 0178 - Story Context Loss-Aware Composition and Projection
- `docs/stories/0178-story-context-loss-aware-composition/decision-report.md`

## Architectural Constraints

1. Java/Core remains the authority for collection, ranking, selection,
   enrichment, projection, grounding, budget policy, and provider authorization.
2. The evaluation must use the existing runtime path and must not add retrieval,
   RAG, embeddings, vector search, or a semantic candidate producer.
3. Captures must be revision-pinned and bounded. They must not contain tokens,
   credentials, full prompts, raw provider output, or unbounded repository data.
4. Provider-visible references remain `UNKNOWN` when the callback contract does
   not expose per-reference visibility. The evaluation must not infer them.
5. A provider timeout or incomplete result remains a terminal outcome and must
   not be counted as a successful analysis.
6. No production selection, enrichment, projection, prompt, provider, or budget
   policy may change as part of this Story.

## In Scope

- Run the existing broad, targeted, insufficient-evidence, and truncated-context
  scenarios through the live MCP/Core/provider path.
- Use a fixed repository revision and at least three repetitions per scenario,
  unless the runtime environment prevents completion; record that limitation.
- Capture task status, context and projection digests, ledger counts, normalized
  loss reasons, projection warnings, preflight outcome, provider terminal status,
  elapsed time, and bounded incomplete diagnostics.
- Correlate each capture to one task and one revision-pinned snapshot.
- Measure completion, timeout, provider-incomplete, preflight-rejected, and
  successful-analysis rates separately.
- Compare requested-file survival and grounding references across scenarios.
- Produce a decision report identifying the dominant stage and the next bounded
  engineering action.

## Out of Scope

- Changing selection category concentration or item-budget policies.
- Increasing context, enrichment, projection, prompt, or provider budgets.
- Production hybrid retrieval, RAG, embeddings, vector search, or new indexes.
- Changing provider adapters, retry policy, completion policy, or callback
  contracts.
- Treating one live run as proof of general model quality.
- Persisting raw prompts, raw model responses, credentials, or sensitive payloads.

## Acceptance Criteria

### AC-1 - Runtime captures are revision-pinned

Every capture records one task identifier, repository revision, context digest,
projection digest, scenario identifier, repetition, and terminal task status.
Captures from missing or mixed revisions are rejected from the report.

### AC-2 - All four scenarios are exercised

The evaluation runs broad, targeted, insufficient-evidence, and deliberately
truncated scenarios. Each scenario reports completed, timed-out, failed,
provider-incomplete, and invalid-capture outcomes separately.

### AC-3 - Loss stages are measured

The report includes candidate, unique, ranked, selected, enriched, projected,
grounded, discarded, and provider-visible counts, plus normalized loss reasons
and projection warnings. Unknown provider visibility remains explicit.

### AC-4 - Provider outcomes are not over-interpreted

Completed provider execution, incomplete output, timeout, and preflight rejection
remain distinct. A callback trace does not imply per-reference provider
visibility unless the contract explicitly supplies that attribution.

### AC-5 - Results are reproducible and bounded

The same revision and scenario inputs can be replayed for the configured number
of repetitions. Output is bounded, redacted, digest-correlated, and stored
outside the repository unless a canonical report is explicitly required.

### AC-6 - Decision report defines the next action

The report classifies the dominant bottleneck as composition/selection,
enrichment, projection, prompt preflight, provider execution, or unresolved
variance. It recommends at most one next bounded Story and explicitly states
which changes remain unjustified.

## Expected Deliverables

- Runtime capture procedure and command.
- Bounded JSONL capture output outside the repository.
- Aggregated runtime evaluation report.
- Decision report for the next bounded improvement.
- Explicit statement of whether hybrid retrieval remains deferred.

## Validation

- Verify the backend and AI Engine runtime images correspond to the target
  revision.
- Run the four-scenario evaluation with the configured repetitions.
- Inspect task snapshots for ledger and provider outcome correlation.
- Run redaction, digest, and revision consistency checks.
- Run applicable backend and evaluation tests without claiming unavailable
  Python pytest coverage.

## Human Decisions Required

- Approve or reject this Story scope.
- Approve the capture repetition count and retention location.
- Approve the Repository Analysis before implementation planning.
- Approve the Implementation Plan before any code or evaluation harness change.

## Non-Authorization Notice

This Story does not authorize retrieval architecture changes, budget increases,
provider changes, or production behavior changes. Any such action requires a
separate Story and explicit architectural and implementation approval.
