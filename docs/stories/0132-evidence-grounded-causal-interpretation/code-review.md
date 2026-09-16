# Story 0132 - Code Review

## Review Scope

This is the implementation review of the Story0132 GREEN ledger against the
approved architecture. The live Ground-Truth rerun remains a separate
evaluation gate.

## Findings

### High - Causal support is asserted, not evidenced

`CausalClaim` currently distinguishes classification and a broad evidence basis,
but individual evidence references have no relationship-support role. A model
can therefore label ordinary context as direct or material support. CASE-04
demonstrates the defect with `3/3 EXPLICITLY_DOCUMENTED` results.

**Required GREEN direction:** add role annotations to existing authorized
references and enforce a structural maximum defensible level.

### High - Empty causal output can hide a failed interpretation

The causal list defaults to empty and the validator does not know whether the
task requires a causal answer. The live CASE-03 retry path recorded empty
causal claims after structural/grounding failures.

**Required GREEN direction:** causal-required tasks must return an explicit
`NOT_ESTABLISHED` claim or fail closed. Empty claims remain valid only for
non-causal tasks.

### Medium - Retry categories are collapsed

`StoryContextAnalysisGenerationService` retries `ValidationError`, grounding
errors and semantic structural errors through the same corrective path. The
retry must be allowed to repair structure and references but must not convert
insufficient evidence into affirmative support.

**Required GREEN direction:** classify retry failures and explicitly permit a
downgrade to `NOT_ESTABLISHED`.

### Medium - Historical unsupported metric is incomplete for causal overclaim

The evaluator counts unmatched structured values, not semantically excessive
causal classifications. CASE-04 therefore reports unsupported inference `0.0`
while remaining causally wrong.

**Required GREEN direction:** preserve the metric and add versioned causal
overclaim/abstention diagnostics.

### Medium - Stability is affected by claim granularity

The model produces different claim counts and source/target descriptions across
repetitions. The current adapter can canonicalize evaluator positions after
generation, but it does not make the production claim structure stable.

**Required GREEN direction:** require a candidate relationship ledger and
deterministic claim cardinality rules for causal-required questions.

## Preserved Strengths

- Java remains the authoritative grounding boundary.
- Unknown causal evidence references fail closed.
- Existing typed-reference infrastructure is reused.
- Evidence-removal tests pass without retrieval changes.
- `relationType` remains separate from causal classification.
- AI output is not promoted to trusted knowledge.
- Corrective retry remains bounded to one attempt.

## Review Decision

`GREEN IMPLEMENTATION REVIEWED - LIVE EVALUATION RED`

The evidence-role ledger, causal-required empty-claim policy, retry taxonomy and
diagnostic metric plan are implemented. The unchanged frozen Ground-Truth run
executed, but the model still overclaims causal relationships and fails the
CASE-04 negative control. The structural boundary is functioning, while the
semantic role assignment remains unreliable.
