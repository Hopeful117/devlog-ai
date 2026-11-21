# Story 0221 - Bounded Live Provider Captures and Attribution

## Status

IMPLEMENTED - READY FOR HUMAN REVIEW.

This Story follows Story 0220 and defines the missing bounded live-capture
capability needed to distinguish Core loss accounting from provider outcomes.
It does not authorize provider credential activation, budget changes, retrieval
changes, commit, push, or merge.

## Epic

- [Epic - Fiabiliser les reponses DevLog](../../epics/agent-response-reliability/epic.md)

## Objective

Produce an evaluation-only, revision-pinned capture from a real Story Context
provider execution when an authorized runtime trace is available. The capture
must correlate Core scope, freshness, digests and `ContextLossLedger` data with
the bounded provider outcome without storing secrets, full prompts, repository
payloads, or raw incomplete provider output.

The deterministic provider-free baseline from Story 0220 remains unchanged and
is never silently replaced by a live result.

## Problem

Story 0220 can classify provider-incomplete and timeout scenarios, but its
provider-free fixtures cannot establish whether provider-visible evidence was
actually received, which provider/model executed the request, or which bounded
usage and failure metadata accompanied the terminal outcome.

The repository already persists attempt-level `AiInteractionTrace` data. The
next capability is a safe evaluation projection of that existing trace, not a
second provider-trace persistence model or a trusted-knowledge workflow.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Persist a Replayable AI Intent Evaluation Harness
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-069 - Canonical Engineering Context Boundary
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0126 - AI Interaction Traceability
- Story 0220 - Revision-Pinned Runtime Loss and Provider Outcome Evaluation

## Architectural Constraints

1. Java/Core remains authoritative for project scope, revision, context,
   grounding, acceptance, and durable trace ownership.
2. Python remains a probabilistic execution and defensive evaluation boundary;
   it does not retrieve context or reconstruct Core identity.
3. Existing `AiInteractionTrace` remains the source of provider-attempt
   metadata. This Story must not create a duplicate production trace aggregate.
4. Provider-specific details are normalized into a bounded evaluation shape.
5. Provider visibility is `UNKNOWN` unless explicit attribution and bounded
   provider outcome metadata are present.
6. Provider-incomplete output, timeout, transport failure, and callback
   validation failure are terminal and never successful analysis.
7. Missing, stale, contradictory, or insufficient identity is `NOT_EVALUATED`.
8. No credentials, bearer tokens, full prompts, repository payloads, or raw
   incomplete provider output may enter normal captures or reports.
9. No production output allowance, retry policy, retrieval policy, or trust
   boundary changes are included.

## In Scope

- Define a versioned live-capture projection over existing interaction traces.
- Preserve Story 0219 `executionScope` and freshness identity.
- Correlate Story 0220 context/projection digests and loss ledger with the
  provider attempt and terminal callback state.
- Capture bounded provider/model identity, status, incomplete reason, failure
  category, effective output limit, available usage, latency, and correlation
  metadata.
- Distinguish `LIVE_PROVIDER`, `DETERMINISTIC_FIXTURE`, and invalid/unavailable
  capture sources.
- Keep incomplete, timeout, rejected, validation-failed, and contradictory
  captures out of semantic quality denominators.
- Add provider-free tests simulating complete, incomplete, timeout, transport,
  validation, attribution and redaction cases.
- Define retention and redaction behavior for evaluation artifacts without
  changing the existing `AiInteractionTrace` retention contract.
- Produce structured and human-readable live-capture reports.

## Out of Scope

- Enabling provider credentials in CI or adding new credentials.
- Increasing provider budgets or changing model/output policy.
- New retry, retrieval, RAG, embedding, vector, or autonomous-loop behavior.
- New production persistence entities for evaluation captures.
- Storing raw prompts, raw model responses, repository payloads or secrets.
- OpenTelemetry, dashboards, generic tracing platforms or frontend changes.
- Automatic model-quality judgment or LLM-as-judge scoring.
- Promoting captures, provider output, or evaluation results to trusted
  knowledge.

## Proposed Capture Contract

The evaluation artifact should contain bounded fields equivalent to:

```text
captureVersion
captureSource
taskId
correlationId
executionScope
freshness
contextDigest
projectionDigest
lossAccounting
providerOutcome
terminalOutcome
analysisAvailable
captureDiagnostics
```

`providerOutcome` must contain explicit attribution before `MEASURED` visibility
is allowed:

```text
provider
model
status
visibility
outcomeSource
failureCategory
incompleteReason
effectiveOutputLimit
usage
latencyMs
```

All strings, collections, diagnostics and report rows must have explicit bounds.

## Acceptance Criteria

- [ ] A live capture is explicitly marked `LIVE_PROVIDER` and retains the
      immutable project/source/revision identity from Core.
- [ ] Expected project, source, revision, context digest, projection digest and
      ledger digest are required before a live capture can be evaluated.
- [ ] Provider visibility is `MEASURED` only with an approved, bounded
      attribution source; otherwise it remains `UNKNOWN`.
- [ ] Provider complete, incomplete, timeout, transport, rejection and callback
      validation outcomes remain distinct and cannot contradict one another.
- [ ] Provider-incomplete output is never counted as a successful analysis or
      semantic-quality evidence.
- [ ] Missing, invalid, stale, mixed-revision or contradictory captures are
      `NOT_EVALUATED` and excluded from semantic denominators.
- [ ] Usage and latency are reported only when supplied by the provider trace;
      missing measurements remain explicitly unknown.
- [ ] Captures and reports reject or exclude credentials, tokens, full prompts,
      repository payloads and raw incomplete output.
- [ ] Live captures reuse `AiInteractionTrace` and do not add a duplicate
      production persistence model.
- [ ] Story 0220 deterministic fixtures and reports remain byte-stable.
- [ ] Retention and redaction behavior is documented and tested without silently
      changing existing diagnostic retention.
- [ ] No production budget, retrieval, retry, trust, or authority behavior
      changes.

## Human Decisions Required

1. Confirm that live captures remain evaluation-only artifacts rather than new
   durable domain state.
2. Confirm the maximum retention period for exported live metadata.
3. Confirm whether usage/cost metadata is in scope, or token usage only.
4. Confirm the approved provider attribution vocabulary.
5. Confirm whether a real provider capture is required for Story acceptance or
   may remain `NOT_EVALUATED` when credentials are unavailable.

## Gates

1. Approve this Story definition and Repository Analysis.
2. Approve the Implementation Plan.
3. Explicitly authorize implementation.
4. Complete implementation, documentation reconciliation and independent review.
5. Human acceptance remains separate from passing tests.

## References

- [Story 0126](../0126-ai-interaction-traceability/story.md)
- [Story 0220](../0220-revision-pinned-runtime-loss-evaluation/story.md)
- [ADR-066](../../decisions/ADR-066.md)
- [ADR-079](../../decisions/ADR-079.md)
