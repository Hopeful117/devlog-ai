# Product Value Evaluation Harness

This package is an isolated, deterministic evaluator for the frozen Story 0130
benchmark. It evaluates captured outputs; it does not call an LLM, alter Core,
retrieve project context, or make human approval decisions.

## Three experimental conditions

Story0131 separates the evidence supplied to an interpreter from the
interpretation produced from that evidence:

- `DIRECT_REPOSITORY`: direct repository reconstruction and interpretation.
- `DEVLOG_CONTEXT`: the unchanged production DevLog context capture.
- `GROUND_TRUTH_CONTEXT`: complete approved benchmark evidence without answer
  keys, oracle classifications, thresholds or human decisions.

The evaluation-only `experimental.py` and `interpretation.py` modules enforce
the model-facing context boundary and score structured fields exactly. They do
not change production prompts, retrieval or domain contracts.

The structured response may expose `evidenceRefs`, `constraints`,
`causalClaims`, `affectedComponents` and `affectedTests`. Causal claims use
neutral task-local positions such as `claim-1`; benchmark CL identifiers stay
evaluator-side. Free-form semantic claims that cannot be judged exactly are
reported as `NOT_MEASURED`, never guessed.

## Oracle validation

An evidence adapter must first produce a manifest containing every expected
artifact resolved at the benchmark revision:

```json
{"repositoryRevision":"<pinned sha>","resolvedArtifacts":["..."]}
```

Run:

```bash
python3 -m evaluations.product_value.runner \
  --benchmark ../docs/stories/0130-devlog-product-value-parity-investigation/evaluation/benchmark-suite-v1.json \
  --artifact-manifest artifact-manifest.json
```

An unresolved artifact is `ORACLE_DEFECT_FOUND`, never a model failure.

## Captured evaluation

Captured results must contain `actualRepositoryRevision` and one result for
each case. The runner refuses acceptance scoring until a human-approved oracle
is supplied and the revision matches exactly. Human utility is intentionally
represented as `null` until the human protocol is executed.

```bash
python3 -m evaluations.product_value.runner \
  --benchmark benchmark-suite-v1.json \
  --capture capture.json \
  --oracle-approved
```

Scoring uses canonical set intersections only. Unknown aliases are not matches;
recommendations cannot satisfy factual or constraint recall. `unsupportedClaimRate`
must be supplied by a reviewed capture and is `NOT_MEASURED` when absent.

Three-condition captures use the evaluation-only runner path:

```bash
python3 -m evaluations.product_value.runner \
  --benchmark ../docs/stories/0130-devlog-product-value-parity-investigation/evaluation/benchmark-suite-v1.json \
  --experimental-capture three-condition-capture.json \
  --oracle-approved
```

This path requires all three conditions, identical case coverage, comparable
provider/model/settings/instruction metadata, exact repository revision and a
leak-free Ground-Truth Context. It remains blocked while the benchmark oracle
is not human-approved.
