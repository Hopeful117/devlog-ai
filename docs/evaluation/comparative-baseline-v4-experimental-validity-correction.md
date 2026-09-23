# Comparative Baseline V4 Experimental Validity Correction

## Decision

The existing quality model is `QUALITY_MODEL_SUFFICIENT` for the frozen V4
answer contract. Structural validity, grounding validity, semantic eligibility,
semantic correctness, and correct-grounded-answer status remain separate
dimensions. No evaluator rubric or semantic authority was changed.

The historical official collection remains immutable and is classified as
`INVALID_FOR_PRIMARY_EXPERIMENTAL_CONCLUSION`: the normal 12-operation ceiling
frequently censored `AGENT_DIRECT_OPEN`, so the original quality-versus-resource
comparison was not fair.

## Corrected Runtime

The V4 family now uses natural completion with an emergency runaway guard.
The guard is intentionally generous and is not a matched resource budget:

| Guard | Value |
| --- | ---: |
| Tool operations | 96 |
| Model turns | 48 |
| Provider calls per observation | 48 |
| Wall clock seconds | 300 |
| Delivered result bytes per operation | 65536 |

Normal completion is the target outcome for both `DEVLOG` and
`AGENT_DIRECT_OPEN`. Guard-triggered observations use `CENSORED_RUNAWAY` and
diagnostic `RUNAWAY_GUARD_CENSORING`; technical provider/tool failures retain
their own statuses.

## Identity

The corrected configuration is V4 `2.0.0` and has new manifest, execution,
runtime, instrumentation, raw-schema, projection, safety, and resource
identities. The frozen benchmark, question set, oracle, repository revision,
conditions, prompt contract, and grounding/semantic contracts are unchanged.

Historical identity `a9864fba8bb0da4fb337de83e8890e6a30ecfcf7a8adebd6dbd40252c2af27cf`
is not reused.

## Offline Notebook Correction

The official notebook now normalizes `semanticCorrect` values before computing
conditional accuracy. It also excludes non-evaluable values from the
correctness denominator and recognizes both historical `CENSORED` and corrected
`CENSORED_RUNAWAY` statuses. Historical RAW and DERIVED artifacts are not
rewritten.

## Readiness

`READY_FOR_MINIMAL_LIVE_VALIDATION`, not ready for the full 18-slot collection.
Before any live call, human approval is still required for the corrected
identity and a minimal validation must confirm that both conditions can reach
natural completion under the emergency guard. No live validation or recollection
was executed in this task.

## Verification

- `python -m pytest tests/test_comparative_baseline_v4.py -q`: 30 passed.
- `python -m pytest -q`: blocked during collection because the environment lacks
  the `fastapi` dependency required by unrelated AI Engine tests.
- Notebook JSON/source validation and replay verification remain offline-only.
