# Story 0180 - Runtime Evaluation Readiness and Isolation

## Status

**IMPLEMENTATION AUTHORIZED - AWAITING HUMAN ACCEPTANCE**

This Story proposes to make the Story Context runtime evaluation trustworthy and
reproducible before using it to justify a production policy change. It does not
authorize implementation, deployment, budget changes, retrieval changes, or
selection-policy changes.

## Problem

The post-Story-0179 runtime campaign executed twelve terminal requests, but none
was a valid measurement:

- the twelve repetitions reused four task identifiers;
- no analysis result was available;
- no loss ledger was present in the captured snapshots;
- observed context budget was `6000` while the `C0-current` profile declared
  `32000`;
- snapshots reported revisions `0272f37...` and `0630e42...`, while the current
  repository revision was `2a92d65...`.

The existing bounded validator correctly rejected the campaign, but the runner
still spent a full campaign against a runtime that was not ready for evaluation.
The resulting data cannot support conclusions about selection concentration,
enrichment, projection, provider quality, or a budget change.

## Goal

Add an evaluation-readiness and isolation gate so a Story Context runtime campaign
only produces evidence when the runtime revision, task identity, snapshot
completeness, and declared budget are coherent. When readiness fails, the runner
must report the infrastructure failure separately and stop or clearly exclude the
campaign from engineering conclusions.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-069 - Canonical Engineering Context Boundary
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0173 - Story Context Budget Observability
- Story 0175 - Story Context Complex Response Completion
- Story 0178 - Story Context Loss-Aware Composition and Projection
- Story 0179 - Story Context Runtime Loss Evaluation
- `docs/stories/0179-story-context-runtime-loss-evaluation/decision-report.md`

## Architectural Constraints

1. Java/Core remains the authority for repository revision, context construction,
   selection, enrichment, projection, grounding, budget policy, and provider
   authorization.
2. The evaluation runner must use the existing MCP/Core runtime path and must not
   reconstruct context, access the repository database, or add retrieval logic.
3. Runtime captures remain bounded and redacted. They must not contain prompts,
   provider payloads, credentials, tokens, or unbounded repository content.
4. Provider-visible references remain `UNKNOWN` when the callback contract does
   not expose per-reference visibility.
5. A missing, stale, duplicated, or incomplete runtime capture is an evaluation
   readiness failure, not evidence of a product-quality regression.
6. No production selection, retrieval, enrichment, projection, prompt, provider,
   retry, or budget policy changes are authorized by this Story.

## In Scope

- Add a target-revision preflight to the runtime evaluation procedure.
- Verify that every repetition receives a distinct task identity and that task
  identity is correlated to exactly one scenario and repetition.
- Verify that the authenticated snapshot contains the expected context and
  projection digests, ledger version/digest, freshness revision, and bounded
  provider outcome fields before counting the capture as evaluable.
- Record expected profile budget and observed Core budget as separate values.
- Detect and report profile-versus-runtime budget mismatches before interpreting
  loss stages.
- Stop or short-circuit a campaign when its runtime readiness contract fails,
  while preserving bounded diagnostics explaining the failure.
- Keep terminal provider outcomes distinct from readiness failures and successful
  analyses.
- Produce a bounded readiness report and a deterministic campaign report only for
  captures that pass the readiness gate.
- Re-run the four existing Story Context scenarios with at least three
  repetitions after readiness succeeds.

## Out of Scope

- Increasing the current `6000` context budget or changing the `32000` profile
  declaration.
- Changing selection category concentration, relevance, item-budget, enrichment,
  projection, prompt, completion, retry, or provider policies.
- Production hybrid retrieval, RAG, embeddings, vector search, or new indexes.
- Changing MCP/Core task idempotency semantics without a separate authorized
  production Story.
- Treating a readiness failure as provider failure, selection loss, or model-quality
  evidence.
- Persisting raw runtime captures, prompts, provider responses, credentials, or
  sensitive payloads in the repository.

## Acceptance Criteria

### AC-1 - Target revision is explicit and enforced

The campaign accepts an explicit target repository revision. Every evaluable
capture has exactly that revision in its authenticated snapshot. Mixed, missing,
stale, or unknown revisions are classified as readiness failures and cannot enter
the campaign aggregate.

### AC-2 - Repetitions are isolated

Each scenario/repetition pair receives one unique task identifier. A reused task
identifier, duplicate scenario/repetition pair, or task correlated to multiple
inputs fails the readiness gate and is reported with bounded diagnostics.

### AC-3 - Snapshot completeness is required

Before a capture is evaluable, the runner verifies the presence and correlation of
the context digest, projection digest, freshness revision, versioned loss ledger,
ledger digest, and provider outcome. Missing ledger or analysis data is reported
as an explicit readiness or terminal outcome rather than silently producing an
empty measurement.

### AC-4 - Budget declarations are not conflated

The report keeps profile budget, observed Core budget, and provider budget
accounting separate. A mismatch is visible in the readiness report and prevents
loss-stage conclusions unless the Story explicitly permits that runtime profile.

### AC-5 - Readiness and execution outcomes remain distinct

The report distinguishes at least:

- readiness failure;
- completed provider execution;
- provider-incomplete result;
- timeout;
- failed terminal execution;
- valid successful analysis.

A readiness failure is never counted as a provider failure or a successful
analysis.

### AC-6 - Captures remain bounded and redacted

Readiness and campaign reports contain only allowlisted bounded fields, digests,
counts, statuses, short diagnostics, and normalized reasons. They contain no raw
prompt, provider response, credential, token sequence, or unbounded repository
payload.

### AC-7 - A valid campaign is reproducible

After readiness succeeds, the runner completes the four existing scenarios with
the configured repetitions, produces one bounded aggregate, and verifies that all
evaluable captures share one target revision and unique task identities.

### AC-8 - Decision evidence is honest

The final report states whether the campaign was evaluable. It may recommend a
single next bounded improvement only when the readiness gate passes. If readiness
fails, it recommends runtime remediation and explicitly makes no production
policy recommendation.

## Expected Deliverables

- Updated bounded runtime evaluation procedure or runner.
- Readiness preflight and bounded failure report.
- Bounded runtime capture output outside the repository.
- Deterministic campaign aggregate after a successful readiness gate.
- Decision report that separates runtime infrastructure findings from product
  behavior findings.

## Validation

- Verify backend, MCP, and AI Engine runtime images correspond to the target
  revision before the campaign.
- Run focused validation for duplicate task IDs, stale revisions, missing ledger,
  budget mismatch, and malformed bounded diagnostics.
- Run the four-scenario campaign with the configured repetitions.
- Verify report redaction, digest correlation, revision consistency, and task
  uniqueness.
- Run applicable backend and evaluation checks without claiming unavailable Python
  pytest coverage.

## Human Decisions Required

- Approve or reject this Story scope.
- Approve whether runtime image/workspace synchronization is part of the readiness
  gate or a prerequisite operated outside the runner.
- Approve the behavior when readiness fails: stop immediately or complete the
  bounded diagnostic sweep while excluding all captures from conclusions.
- Approve the Repository Analysis before implementation planning.
- Approve the Implementation Plan before any runner or evaluation-harness change.

## Non-Authorization Notice

This Story authorizes no production behavior change. In particular, it does not
authorize increasing context budgets, changing selection policies, changing task
idempotency semantics, adding retrieval channels, or interpreting the current
invalid campaign as evidence for any of those changes.
