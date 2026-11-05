# Story 0183 - Authoritative Candidate Trust in Evaluation Fixture

## Status

**IMPLEMENTATION AUTHORIZED - AWAITING HUMAN ACCEPTANCE**

This Story authorizes a Core-only extension of the Story Context evaluation
fixture. It does not authorize production policy changes or a new retrieval
mechanism.

## Problem

Story 0182 provides a bounded, redacted candidate fixture for offline selection
comparison. The fixture currently resolves trust through the canonical trust map
used by selected evidence. Rejected candidates therefore frequently receive
`UNKNOWN`, even though Core already carries candidate `kind` and `sourceType`
metadata. This prevents an authoritative trust comparison of the `current`,
`balanced`, and `unrestricted` replay policies.

## Goal

Expose authoritative, deterministic trust classification for every candidate in
the evaluation fixture, including rejected candidates, while preserving the
existing Core authority boundary and all production selection behavior.

## Governing Artifacts

- ADR-006 - AI Proposal and Knowledge Promotion Workflow
- ADR-063 - Engineering Context Retrieval and Composition Architecture
- ADR-066 - Replayable AI Intent Evaluation Harness
- ADR-067 - DevLog Engineering Story Context Agent
- ADR-079 - Multi-Layer Context Budgets and Provider-Incomplete Diagnostics
- Story 0179 - Story Context Runtime Loss Evaluation
- Story 0181 - Bounded Selection Evaluation Telemetry
- Story 0182 - Core Redacted Candidate Fixture for Offline Selection Comparison

## Architectural Constraints

1. Java/Core remains the sole authority for candidate identity, classification,
   trust, selection, projection, grounding, and repository scope.
2. Trust classification must reuse the existing deterministic classification
   rules and must not infer trust from relevance, score, selection state, or
   model output.
3. Python may validate, aggregate, and replay the trust metadata but must not
   classify or promote trust independently.
4. `UNKNOWN` remains valid for genuinely unsupported or incomplete candidate
   metadata; it must not be replaced with a guessed tier.
5. The fixture remains bounded, redacted, digest-covered, and evaluation-only.
6. No production selection, ranking, retrieval, enrichment, projection,
   grounding, retry, provider, or context-budget policy may change.
7. Existing REST and MCP contracts must not be expanded by this Story.

## In Scope

- Deterministic Core trust classification for all candidate fixture rows.
- Reuse or extraction of the existing trust classification rules without
  introducing a second contradictory policy.
- Explicit handling of excluded, unvalidated, AI-generated, unsupported, and
  incomplete candidate metadata.
- Preservation of the trust value in the existing bounded fixture and digest.
- Python validation and offline aggregation of candidate trust distributions.
- Tests covering selected and rejected candidates across every permitted trust
  tier, including legitimate `UNKNOWN` cases.
- Story-local replay of `current`, `balanced`, and `unrestricted` using the
  enriched fixture.
- A decision report separating authoritative observations from any future policy
  recommendation.

## Out of Scope

- Changing the current selection or category-concentration policy.
- Changing candidate ranking, relevance scoring, or budget limits.
- Increasing fixture, context, item, token, enrichment, or provider budgets.
- Adding RAG, embeddings, vector search, or another retrieval channel.
- Adding repository or database access to Python.
- Promoting `UNKNOWN` or excluded candidates into trusted knowledge.
- Adding a supported REST or MCP response for candidate trust metadata.
- Making a production policy recommendation automatically from the replay.

## Acceptance Criteria

### AC-1 - Core-authoritative classification

Every candidate row receives its trust value from Java Core classification based
only on authorized candidate metadata and the existing trust rules. Python does
not create, infer, or rewrite the trust value.

### AC-2 - Selected and rejected coverage

The fixture reports trust distributions independently for selected, rejected,
projected, and grounded candidate states. Rejected candidates are not silently
omitted from trust accounting.

### AC-3 - Conservative unknown handling

Unsupported or incomplete metadata remains `UNKNOWN` or the explicitly defined
excluded classification. The implementation must not use score, category,
selection state, or provider visibility as a trust substitute.

### AC-4 - Contract and digest integrity

The existing fixture bounds, redaction rules, correlation fields, and digest
validation remain valid. A changed trust value changes the canonical fixture
and telemetry digests deterministically.

### AC-5 - Cross-language validation

The Python evaluation harness rejects malformed or unauthorized trust values and
accepts the Core-produced enriched fixture without reconstructing repository
context.

### AC-6 - Replay evidence

The story-local evaluation replays `current`, `balanced`, and `unrestricted`
against the same enriched fixtures and reports selected counts, rejected counts,
trust distributions, grounding, requested-file survival, and category mixes
separately.

### AC-7 - Decision separation

The final report distinguishes measurement from policy judgment and does not
change or recommend changing production selection policy automatically.

## Human Decisions Required

- Approve or reject this Story scope.
- Approve reuse or extraction of the existing Core trust classification rules.
- Approve that `UNKNOWN` remains a valid outcome for unsupported or incomplete
  candidate metadata.
- Approve the Repository Analysis before Implementation Planning.
