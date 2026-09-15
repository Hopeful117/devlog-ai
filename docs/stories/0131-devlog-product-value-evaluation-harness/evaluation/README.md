# Story 0131 Evaluation Artifacts

The benchmark remains immutable. The human oracle is now frozen for the pinned
revision:

- `oracle-freeze-v1.json`: human-approved, frozen causal classifications and
  resolved oracle status, applied as an evaluator-side overlay.
- `oracle-validation-report-2026-09-15.md`: final evidence and approval record.

The remaining template/capture files are not formal model measurements:

- `artifact-resolution-manifest-v1.json`: must be populated by resolving every
  frozen expected artifact at the pinned Trading OS revision.
- `human-validation-record-v1.md`: completed with explicit human approval.
- `capture-template-v1.json`: shape for a real direct, DevLog or replay capture;
  its `null` and `NOT_EXECUTED` values are not measurements.

The dated files are measurements from the live DevLog context projection or
explicit blocked conditions:

- `devlog-live-capture-2026-09-15.json`: four real context calls.
- `artifact-resolution-manifest-2026-09-15.json`: 19 explicit DevLog
  retrieval resolutions, including 9 unresolved entries.
- `repository-ground-truth-manifest-2026-09-15.json`: independent exact-Git
  verification of all 19 artifacts at the pinned Trading OS revision.
- `repository-vs-devlog-comparison-2026-09-15.json`: deterministic separation
  of repository existence from DevLog retrieval.
- `oracle-validation-report-2026-09-15.md`: final human-reviewed causal,
  constraint, component and test oracle evidence.
- `direct-repository-capture-2026-09-15.json`: honest `NOT_EXECUTED` condition.
- `ground-truth-context-2026-09-15.json`: complete evidence-only context built
  from the pinned Trading OS Git revision; mechanical leakage validation passed.
- `interpreter-capture-partial-2026-09-15.json`: 24 real OpenAI interpretation
  calls using `gpt-4.1-mini`, covering three repetitions for each case in the
  DevLog and Ground-Truth conditions.
- `interpreter-metrics-partial-2026-09-15.json`: deterministic scoring of those
  two executed conditions.
- `formal-red-baseline-2026-09-15.json` and
  `formal-red-baseline-report-2026-09-15.md`: partial RED result and the
  remaining Direct Repository blocker.
- `preliminary-evaluation-2026-09-15.json`: historical pre-freeze acceptance
  refusal; it is not a formal RED result.
- `preliminary-baseline-report-2026-09-15.md`: case comparison with direct
  baseline and Human Utility still unmeasured.

The empty manifest must not be treated as a successful resolution. Formal RED
execution remains blocked until comparable Direct, DevLog and Ground-Truth
interpretation captures exist.

```bash
python3 -m evaluations.product_value.runner \
  --benchmark ../docs/stories/0130-devlog-product-value-parity-investigation/evaluation/benchmark-suite-v1.json \
  --artifact-manifest ../docs/stories/0131-devlog-product-value-evaluation-harness/evaluation/artifact-resolution-manifest-v1.json
```
