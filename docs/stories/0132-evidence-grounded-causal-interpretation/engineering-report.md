# Story 0132 - Engineering Report

## Status

`GREEN IMPLEMENTED - EVALUATED - INTERPRETATION RED`

## Repository State

- Branch: `main`
- Story0131 baseline: merged at `8b2be9f`.
- Story0132 implementation changes: present from the prior RED experiment.
- GREEN ledger implementation: complete; prior RED implementation and result
  remain reproducible and untouched.
- Live GREEN evaluation: executed with the authorized `.env` OpenAI credentials.
- Commit/push/merge: `NO`.

## Frozen RED Evidence

| Metric | Result |
|---|---:|
| Condition | `GROUND_TRUTH_CONTEXT` |
| Provider/model | `openai` / `gpt-4.1-mini` |
| Repetitions | `3` per case |
| Grounding | `PASS` |
| Evidence removal | `PASS` |
| Positive causal accuracy | `0.3777777777777777` |
| Stability | `0.3333333333333333` |
| CASE-04 | `0/3 NOT_ESTABLISHED` |
| Threshold result | `FAIL` |

## Diagnosis

The failure is an interpretation-strength failure, not a retrieval failure.
Complete evidence is supplied, but the current contract validates that cited
references exist rather than whether they support the relationship asserted by
the claim.

The model treats related ADR metadata, temporal order, implementation
compatibility and a changed target file as causal support. These facts establish
context around the entities, not the causal edge between them.

## Product Decision

The recommended GREEN design is an evidence-led causal ledger with role-
annotated existing references and a deterministic structural maximum-level
ceiling. It preserves affirmative paths for direct and materially corroborated
relationships while making contextual evidence insufficient for affirmative
causality.

`INFERRED` is not retained as a causal strength. It remains representable as
non-trusted interpretation/relation metadata and must not become project truth.

## Metric Audit

The existing `unsupportedInferenceRate` is valid for its narrow structured-field
purpose but incomplete for causal overclaiming. CASE-04 receives `0.0` because
its claim is structurally matched and grounded; the metric does not evaluate
whether the matched classification is semantically too strong.

The GREEN evaluation should preserve the historical metric and add causal
overclaim rate, abstention accuracy, role admissibility and causal stability.

The separate GREEN evaluation result is recorded in
`evaluation/ground-truth-green-2026-09-15.json`. It remains RED: positive
causal accuracy `0.15555555555555556`, CASE-04 `0/3`, grounding PASS, stability
`0.7983058608058609`, and evidence-removal FAIL.

## Readiness

`GREEN_IMPLEMENTED - INTERPRETATION RELIABILITY FAIL - HUMAN REVIEW REQUIRED`

Human Utility remains `NOT_YET_ESTABLISHED`. Direct Repository parity remains
`INCONCLUSIVE`. Retrieval, model selection, RAG and Trading OS are not next
actions from this result.
