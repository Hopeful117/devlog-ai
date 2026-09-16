# Story 0132 - Implementation Report

## Status

`GREEN_IMPLEMENTED - EVALUATED - INTERPRETATION RED`

## Contract

Production Story Context Analysis now exposes `causalClaims`. Each claim has:

- `source`
- `target`
- `causalClassification`
- `evidenceBasis`
- `evidenceReferences`
- `evidenceReferences[].role` inside causal claims
- `explanation`

`causalClassification` is `EXPLICITLY_DOCUMENTED`, `STRONGLY_SUPPORTED` or
`NOT_ESTABLISHED`. `evidenceBasis` makes the evidence-first boundary explicit:
direct documentation and material corroboration are the only affirmative bases;
chronology, temporal proximity, shared topic, compatibility, possible relevance,
conflict and insufficiency require `NOT_ESTABLISHED`.

Existing `relationType` metadata was preserved on findings. It is not part of a
causal claim and cannot determine causal strength.

The approved causal evidence roles are `DIRECT_RELATIONSHIP_STATEMENT`,
`MATERIAL_RELATIONSHIP_SUPPORT`, `NON_CAUSAL_CONTEXT` and
`CONTRADICTORY_EVIDENCE`. The Python and Java validators enforce a structural
maximum defensible level without parsing arbitrary prose in Java.

## Implementation

- Python Pydantic contract and defensive semantic validation.
- Java contract, backward-compatible result constructor, and authoritative
  callback validation against the Java-authored grounding contract.
- Prompt instructions for evidence-first causal reasoning and abstention.
- Evaluation-only adapter from production-shaped claims to Story0131's frozen
  scorer; benchmark identifiers remain evaluator-side.
- Frozen Story0132 thresholds: positive causal accuracy `>= 0.80`, CASE-04
  `3/3 NOT_ESTABLISHED`, grounding valid, unsupported inference `0`, and
  stability `>= 0.90`.
- Evidence-removal helper and deterministic tests.
- Causal-required task contract, explicit non-empty abstention validation and
  retry categories `FORMAT_ERROR`, `STRUCTURAL_CONTRACT_ERROR`,
  `GROUNDING_ERROR` and `SEMANTIC_SUPPORT_ERROR`, with one retry maximum.
- Evaluation-only diagnostics: causal overclaim rate, abstention accuracy, role
  admissibility rate and causal claim stability.

## Verification

Passed:

```text
python3 -m pytest -q --ignore tests/test_evaluation_harness.py
all relevant AI Engine tests passed
python3 -m compileall -q app evaluations tests
./backend/mvnw -pl backend -am test -Dtest=AnalyzeStoryContextUseCaseTest,AiTaskResultServiceTest -Dsurefire.failIfNoSpecifiedTests=false -B
28 tests passed
```

The complete AI Engine suite has 8 unrelated pre-existing failures caused by
the architecture-overview fixture's stale `InsightGenerationOutput` schema
digest. No unrelated fixture or production path was changed.

## Live Evaluation

The prior RED artifact remains
`evaluation/ground-truth-live-2026-09-15.json` and was not overwritten. The
separate GREEN result is
`evaluation/ground-truth-green-2026-09-15.json`.

The required `GROUND_TRUTH_CONTEXT` rerun executed with `openai`/
`gpt-4.1-mini`, four cases and three repetitions per case.

GREEN results:

- Positive causal accuracy: `0.15555555555555556`, below `0.80`.
- CASE-04: `0/3 NOT_ESTABLISHED`.
- Historical unsupported-inference gate: `FAIL`.
- Interpretation stability: `0.7983058608058609`, below `0.90`.
- Grounding: `PASS` for all 12 runs.
- Causal overclaim rate: `1.0`.
- Abstention accuracy: `0.0`.
- Role admissibility rate: `0.43333333333333335`.
- Causal claim stability: `0.5833333333333334`.
- Evidence-removal tests: `FAIL` for CASE-01, CASE-02 and CASE-03.
- Overall Story0132 threshold result: `FAIL`.

`DEVLOG_CONTEXT`: not rerun. The available Story0131 capture contains selected
references but not model-facing evidence content. The measured `10/19`
retrieval baseline remains unchanged and is not conflated with this result.

Consequently:

- CASE-04: `0/3 NOT_ESTABLISHED`
- causal accuracy after GREEN: `0.15555555555555556`
- stability after GREEN: `0.7983058608058609`
- interpretation reliability: `FAIL`
- next measured product problem: semantic role assignment and causal abstention

## Boundaries

- Frozen Story0131 oracle: unchanged.
- Retrieval/context selection: unchanged.
- Direct Repository runner: not added; `DEVLOG_VS_DIRECT = INCONCLUSIVE`.
- Trading OS: not modified.
- Human Utility: `NOT_YET_ESTABLISHED`.
- Commit/push/merge: not performed.
