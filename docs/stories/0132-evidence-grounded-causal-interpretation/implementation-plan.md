# Story 0132 - GREEN Implementation Plan

## Authorization Boundary

This is the approved GREEN implementation plan after the Story0132 RED
experiment. Human decision: `APPROVE THE STORY0132 GREEN ARCHITECTURE AND
AUTHORIZE GREEN IMPLEMENTATION`. The Story0132 RED baseline, oracle, benchmark,
thresholds and evaluation artifacts remain unchanged.

## Design

Implement one evidence-led causal ledger, not a second retrieval or evidence
identity system.

```text
candidate relationship
  -> authorized evidence references
  -> relationship support roles
  -> deterministic maximum defensible level
  -> causal classification
  -> bounded explanation
```

## Contract Changes

1. Preserve `relationType` exactly as relationship metadata; it is not causal
   strength.
2. Preserve the three causal classifications:
   - `EXPLICITLY_DOCUMENTED`
   - `STRONGLY_SUPPORTED`
   - `NOT_ESTABLISHED`
3. Do not add `INFERRED` to the causal-strength scale. Existing
   `INFERRED_HYPOTHESIS` remains non-causal relation metadata.
4. Extend causal evidence references with an optional role field for backward
   compatibility. Require the role inside causal claims.
5. Use these roles only:
   - `DIRECT_RELATIONSHIP_STATEMENT`
   - `MATERIAL_RELATIONSHIP_SUPPORT`
   - `NON_CAUSAL_CONTEXT`
   - `CONTRADICTORY_EVIDENCE`
6. Keep `NOT_ESTABLISHED` as an explicit claim with a bounded explanation for
   causal-required questions. Do not accept an empty claim list for such a
   question.

## Maximum Defensible Level

The structural ceiling is:

- A direct relationship role may permit `EXPLICITLY_DOCUMENTED`.
- At least two distinct material relationship-support references may permit
  `STRONGLY_SUPPORTED`.
- Context-only, contradictory, chronology-only, topic-only, compatibility-only
  or insufficient evidence permits only `NOT_ESTABLISHED`.

The ceiling is a structural admissibility rule, not a deterministic proof that
arbitrary prose is causally true.

## Validation and Retry

### Python

- Validate role/classification combinations, source/target, duplicate claims,
  evidence counts and local reference shape.
- Classify failures as format, structural, grounding or semantic-support
  errors.
- Preserve exactly one corrective retry.
- Instruct retry to downgrade to `NOT_ESTABLISHED` when support is insufficient;
  never add evidence.

### Java Core

- Reuse the existing authorized reference set, registry and resolver.
- Validate reference membership, task scope, role values, minimum support counts,
  maximum defensible level and causal-required non-empty output.
- Do not attempt to prove the meaning of arbitrary evidence prose.

## Prompt Changes

Make only targeted changes:

- require an evidence ledger before classification;
- state that entity existence, chronology, related-document references, shared
  topic and compatibility are `NON_CAUSAL_CONTEXT`;
- require direct relationship language for explicit classification;
- require multiple material relationship supports for strong classification;
- require explicit `NOT_ESTABLISHED` rather than omission when causality is not
  established.

## Evaluation Changes

Preserve Story0131 metrics and historical results. Add evaluation-only
diagnostics:

- causal overclaim rate;
- abstention accuracy;
- role admissibility rate;
- causal claim stability.

Do not expose benchmark identifiers, oracle labels or thresholds to the model.

## Test Plan

- Direct relationship statement permits explicit classification.
- Two distinct material supports permit strong classification.
- One material support is insufficient for strong classification.
- Entity-only, chronology-only, temporal-only, topic-only and compatibility-only
  evidence reject affirmative classifications.
- Contradictory evidence forces `NOT_ESTABLISHED`.
- Unknown, out-of-context and unauthorized references fail closed.
- Confidence cannot promote a classification.
- `relationType` cannot establish causality.
- A causal-required question cannot return `causalClaims=[]`.
- Corrective retry cannot add an unauthorized reference.
- CASE-04 remains generic and is not special-cased.

## Execution Sequence

1. Approve causal levels, evidence roles and causal-required task semantics.
2. Extend the shared/Python causal contract with role annotations.
3. Add Python structural validation and failure categories.
4. Add Java authoritative role, scope and maximum-level validation.
5. Apply minimal prompt changes.
6. Add deterministic Python/Java/evaluation tests.
7. Run focused and full relevant validation.
8. Run the unchanged Ground-Truth benchmark with three repetitions.
9. Run evidence-removal tests for every positive relationship.
10. Diagnose results without changing retrieval, model, oracle or thresholds.
