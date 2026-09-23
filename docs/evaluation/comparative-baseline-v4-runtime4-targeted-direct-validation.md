# Comparative Baseline V4 Runtime 4.0 Targeted DIRECT Validation

## Observation

- Attempt: `corrected-v4-runtime4-targeted-direct-validation-20260918T000000Z`
- Path: `ai-engine/data/comparative-baseline-v4/live-pilot/corrected-v4-runtime4-targeted-direct-validation-20260918T000000Z/`
- Condition: `AGENT_DIRECT_OPEN`
- Question: `CASE-04@1.0.0`
- Repository revision: `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`
- Runtime: `comparative-v4-live-runtime-contract-4.0.0`
- Runtime digest: `3d4985ae3d5db051a6924eef0f44eac90ce66db05440bc110e69dce4cedcb7e1`
- Typed schema: `comparative-v4-typed-repository-tools-2.0.0`
- Execution identity: `479031b0814373e2d5fc693bf8bfd81fc7c42af96af6e80d471a6ee646145bd4`
- Experiment identity: `8a18746c1d387cb9375d186a2b8ab8da38c190eb854a918dddbe20dcf38d5910`
- Provider/model: `openai` / `gpt-4.1-mini`

## Preflight and Termination

Preflight: `PASS`. Runtime 4.0.0, six typed tools, static constraints,
`FULL_OBJECT_ID_ONLY`, frozen CASE-04 inputs, Policy A, natural completion, and
unchanged guards were verified before the provider call.

Termination: `PROVIDER_FAILURE` before any tool call. The persisted attribution
was:

```text
category: RESPONSE_PARSE_ERROR
requestPhase: RESPONSE_PARSE
exception: JSONDecodeError
valueRepresentation: JSON_TEXT
safeMessage: Expecting value: line 1 column 1 (char 1)
retryable: false
```

No final answer was available. No tool attempt, valid operation, invalid
request, runtime-state failure, or guard trigger occurred.

## Metrics

- Tool attempts/valid/invalid/skipped: `0/0/0/0`.
- Searches/reads/Git operations: `0/0/0`.
- Git by type: `git_log=0`, `git_show=0`, `git_diff=0`, `inspect_commit=0`.
- Model turns: `0` response-bearing; `1` provider request attempted.
- Provider calls: `1`.
- Repository bytes: `0`.
- Tokens: `NOT_MEASURED`.
- Latency: `2613 ms`.
- Cost: unavailable.
- Guard: not triggered.
- Static parity violations observed: none, because no tool call reached the
  typed schema/runtime boundary.
- Runtime-state failures: none.
- Repository evidence: none acquired.

## Integrity and Isolation

- RAW count: `1`.
- DERIVED count: `1`.
- RAW/wrapper hashes: `PASS`.
- RAW-to-DERIVED linkage: `PASS`.
- Deterministic replay: `PASS`; replay provider/network calls `0/0`.
- Resource accounting: `PASS` for zero tool activity.
- Contamination: `PASS`.
- Condition isolation: `PASS`.
- Historical artifacts: untouched.

## Validation Questions

- A. Runtime 4.0.0 active: `YES`.
- B. Six typed schema 2.0.0 tools exposed: `YES` in preflight, but no tool was
  emitted by the provider.
- C. Provider-accepted static-invalid call: `NO` observed.
- D. Known historical static parity defect recurred: `NO` observed.
- E. Repository-state failure occurred: `NO`.
- F. Runtime-state termination prevented a realistic answer: `INCONCLUSIVE`;
  no runtime-state request occurred.
- G. DIRECT received a realistic natural repository-inspection opportunity:
  `INCONCLUSIVE`.
- H. DIRECT reached a final answer: `NO`.
- I. Git exercised naturally: `NO`.
- J. `read_file` exercised naturally: `NO`.
- K. Emergency guard triggered: `NO`.
- L. Replay/integrity/accounting passed: `YES`.
- M. Contamination/isolation passed: `YES`.

## Classification

Observation validity for tool-contract validation: `INCONCLUSIVE`. The provider
response parse failure occurred before the corrected tools could be exercised;
it is not evidence of a schema/runtime defect, but it also cannot establish
that the corrected contract works for CASE-04.

Boundary coverage: `NONE`.

Runtime-failure-semantics review required: `NO` for this observation. A separate
provider response-parse review may be considered; no adapter change is made
here.

`VALIDATION_INCONCLUSIVE`

`NOT_READY_FOR_OFFICIAL_COLLECTION`

Exact next human decision: review the non-retryable provider response-parse
failure and decide whether a separately authorized targeted validation is
justified. Do not rerun this observation automatically, execute another
question, or start the official collection.

No code, schema, runtime, prompt, evaluator, guard, benchmark, identity, or
historical artifact was modified after the observation.
