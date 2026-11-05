# Story 0181 - Bounded Selection Evaluation Telemetry

## Status

**IMPLEMENTATION AUTHORIZED - AWAITING HUMAN REVIEW**

This Story proposes a bounded, evaluation-only extension of the Story Context
loss ledger. It does not authorize a change to selection, ranking, enrichment,
projection, grounding, trust, provider, or context-budget policy.

## Problem

The B6000 runtime campaign is evaluable and shows that
`SELECTION_CATEGORY_CONCENTRATION_LIMIT` represents approximately 86% of
recorded loss reasons. The current bounded ledger does not preserve enough
category and requested-file information to compare policy alternatives honestly:
its 256 detail entries are consumed by sorted selection decisions, and requested
file survival cannot be measured reliably.

## Goal

Expose bounded, redacted evaluation telemetry that allows offline comparison of
the current selection policy with instrumented alternatives while preserving
Core authority and existing production behavior.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Replayable AI Intent Evaluation Harness
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0178 - Story Context Loss-Aware Composition and Projection
- Story 0179 - Story Context Runtime Loss Evaluation
- Story 0180 - Runtime Evaluation Readiness and Isolation

## Architectural Constraints

1. Java/Core remains the authority for collection, ranking, selection,
   enrichment, projection, grounding, trust, and budget policy.
2. Telemetry is evaluation metadata, not a new selection or retrieval mechanism.
3. The telemetry must contain bounded counts, identifiers, categories, trust
   tiers, and statuses only; no file content, prompts, tokens, credentials, or
   raw provider payloads.
4. Existing ledger versioning and digest integrity must remain explicit. Any
   contract version change must be intentional and validated.
5. Provider-visible evidence remains `UNKNOWN` unless the callback contract
   explicitly exposes attribution.
6. The evaluation harness may consume telemetry but must not reconstruct context
   or alter production state.

## In Scope

- Bounded candidate/selected/rejected distributions by evidence kind/category.
- Bounded requested-file metrics: requested, found, selected, projected, and
  grounded counts or explicit absence.
- Bounded trust-tier distributions for candidates, selected evidence, projected
  evidence, and grounded evidence.
- Bounded grounding and projection outcome metrics.
- Evaluation-runner extraction and offline aggregation of the new telemetry.
- Deterministic tests for bounds, redaction, digest correlation, and missing
  telemetry behavior.
- Story-local evaluation of current policy versus instrumented alternatives.

## Out of Scope

- Changing category concentration, kind share, relevance, item, token, or
  provider budgets.
- Changing ranking, selection, enrichment, projection, grounding, or retry
  behavior.
- Core submission idempotency changes.
- RAG, embeddings, vector search, or new retrieval channels.
- Provider-visible attribution not supplied by the callback contract.
- Persisting raw repository content or model payloads.

## Acceptance Criteria

### AC-1 - Bounded telemetry contract

The ledger exposes versioned, digest-covered telemetry with explicit maximum
entry/count bounds. Malformed or oversized telemetry is rejected or classified as
non-evaluable.

### AC-2 - Category distributions

The evaluation capture reports candidate, selected, rejected, projected, and
grounded distributions by evidence kind/category without raw content.

### AC-3 - Requested-file survival

For every explicitly requested file, the capture reports whether it was found,
selected, projected, and grounded, or reports a bounded explicit absence reason.

### AC-4 - Trust and grounding safety

Trust-tier and grounding metrics are derived from Java/Core-authorized state.
Python evaluation code cannot promote, infer, or redefine trust.

### AC-5 - Offline comparison

The evaluation harness can compare the current policy with at least two
instrumented alternatives without changing production behavior and reports the
trade-offs separately for category concentration, requested files, trust, and
grounding.

### AC-6 - Redaction and reproducibility

Captures contain only bounded allowlisted metadata and remain correlated to one
task, revision, context digest, projection digest, and ledger digest.

### AC-7 - No production-policy conclusion by default

The report clearly separates telemetry readiness from policy recommendation. A
policy change is not recommended unless the comparison has valid requested-file,
trust, and grounding measurements.

## Human Decisions Required

- Approve or reject this Story scope.
- Approve the bounded telemetry fields and maximum sizes.
- Approve whether the telemetry is evaluation-only or also exposed through a
  supported external API.
- Approve the Repository Analysis before implementation planning.
- Approve the Implementation Plan before implementation.

## Non-Authorization Notice

This Story authorizes no production policy change. Implementation must stop if
the required telemetry cannot be added without changing the authority boundary or
the semantics of existing selection and grounding contracts.
