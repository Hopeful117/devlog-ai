# Comparative Baseline V4 Runtime 5.0 Post-Remediation DIRECT Validation

## Authorization and Identity

- Authorization: exactly one new `AGENT_DIRECT_OPEN / CASE-04@1.0.0 / r1` observation.
- Attempt ID: `v4-runtime5-case04-post-remediation-direct-validation`.
- Observation ID: `v4-runtime5-case04-post-remediation-direct-validation:CASE-04:AGENT_DIRECT_OPEN:r1`.
- Observation count: `1`.
- Runtime: `comparative-v4-live-runtime-contract-5.0.0`.
- Runtime digest: `a3211f44aad4633ca1d725b7a48506a6dd8725ab96becf182e867f1794e80edd`.
- Instrumentation: `comparative-v4-instrumentation-3.0.0`.
- Execution identity: `479031b0814373e2d5fc693bf8bfd81fc7c42af96af6e80d471a6ee646145bd4`.
- Experiment identity: `1af6f9b79427f6f53bca1f9466fde9ee01c652f5bd082a810566428f2606f4d5`.
- Validation plan: `6c423c1548c68365fd432becaa1edb7dedd0f0c7fa070dd668fb5fcba7ecc638`.
- Future official plan: `fe85cf393dcf9da830af5476ff1e6a398758d97457955223e75cd646f3d521fe`.
- Typed schema: `comparative-v4-typed-repository-tools-2.0.0`.
- Repository revision: `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`.

## Preflight

Preflight passed before the provider request. Runtime 5, all required identities,
the six typed repository tools, natural completion, Policy A, no-retry, frozen
guards, provider/model, benchmark/question/oracle, and repository revision were
verified. Generic repository tooling was not present in the provider-visible
schema. Official collection remained disabled.

Frozen guards were unchanged:

```text
maxToolOperations = 96
maxModelTurns = 48
maxProviderCallsPerObservation = 48
maxWallClockSeconds = 300
maxReadBytesPerOperation = 65536
```

## Observation Result

```text
providerResponseStatus = incomplete
providerIncompleteReason = max_output_tokens
executionStatus = PROVIDER_FAILURE
primaryDiagnostic = PROVIDER_FAILURE
category = PROVIDER_INCOMPLETE_RESPONSE
requestPhase = RESPONSE_STATE
parseBoundary = NOT_REACHED
valueRepresentation = NOT_REACHED
final-output parser = not invoked
technical retries = 0
stoppingReason = PROVIDER_FAILURE
```

Provider diagnostics persisted:

```text
providerResponseId = resp_051dc125f56d3579006aad174bb62487d2b08425aa6bb3e9a4
providerRequestId = req_1578bc2099434fef8a294d196d1c94f0
sdkResponseType = Response
sdkItemType = null
sdkItemDiscriminator = null
runtimeType = NOT_REACHED
valueRuntimeType = null
```

This is the expected runtime-5 response-state behavior. The previous causal
parse failure was not reproduced. The provider response was correctly rejected
before final-output parsing.

## DIRECT Trajectory

- Usable model interaction: `NO`.
- Repository-tool interaction: `NO`.
- Realistic repository-inspection opportunity: `NO`; the provider terminated before returning a usable tool or final response.
- Final answer: `NO`.
- Tool attempts: `0`.
- Valid operations: `0`.
- Invalid requests: `0`.
- Runtime-state failures: `0`.
- Searches: `0`.
- Reads: `0`.
- Git operations: `0`.
- Static schema/runtime parity violations: `0`.
- Emergency guard: `NO`.
- Structural result: `NOT_EVALUATED`.
- Semantic result: `NOT_EVALUATED`.
- Grounding result: `NOT_EVALUATED`.
- Correct-grounded result: `NOT_EVALUATED`.

The only persisted conversation content was the frozen CASE-04 user question.
No DevLog context, oracle, previous answer, remediation history, expected
evidence, or repository result was exposed.

## Accounting

- Provider transport attempts: `1`.
- Response-bearing provider calls recorded by resources: `0`.
- Network transport: `1` live provider transport is evidenced by the provider response identifiers.
- Model turns: `0`.
- Technical retries: `0`.
- Repository bytes: `0`.
- Tool attempts: `0`.
- Tokens: not measured.
- Assignment latency: `2300 ms`.
- Provider latency: not measured after the adapter intercepted the response state.

The existing accounting distinction is preserved: an attempted transport is not
counted as a response-bearing `ProviderResponse` when the adapter raises first.
No accounting redesign was performed.

## Persistence and Replay

RAW:

```text
data/comparative-baseline-v4/live-pilot/v4-runtime5-case04-post-remediation-direct-validation/artifacts/CASE-04:AGENT_DIRECT_OPEN:r1.json
```

DERIVED:

```text
data/comparative-baseline-v4/live-pilot/v4-runtime5-case04-post-remediation-direct-validation/derived/CASE-04:AGENT_DIRECT_OPEN:r1.json
```

- RAW hash: `PASS`.
- DERIVED hash: `PASS`.
- RAW to DERIVED linkage: `PASS`.
- Deterministic evaluation: `PASS`.
- Deterministic projection: `PASS`.
- Replay provider calls: `0`.
- Replay network calls: `0`.
- Resource accounting projection: `PASS`.
- Diagnostic projection: `PASS`.
- Contamination/isolation: `PASS`.

## Historical Preservation

All previous V3/V4 observations, runtime-4 validations, instrumented runtime-4
validation, forensic reports, remediation reports, RAW/DERIVED artifacts,
manifests, and ledgers were left untouched. The historical runtime-4 result
remains `PROVIDER_FAILURE / RESPONSE_PARSE_ERROR`.

## Answers A-U

- A: `YES`.
- B: `YES`.
- C: `incomplete`.
- D: `NO`.
- E: `NO`.
- F: `NO`.
- G: `YES`.
- H: `YES`.
- I: `YES`.
- J: `NO`.
- K: `NO`.
- L: `NO`.
- M: `NO`.
- N: `NOT_EVALUATED`.
- O: `NOT_EVALUATED`.
- P: `NOT_EVALUATED`.
- Q: `NO`.
- R: `YES`.
- S: `YES`.
- T: `YES`; runtime 5 honored its frozen incomplete-response contract.
- U: `NO`; repository-tool runtime validity was not exercised.

## Classification and Next Decision

The remediation passed its intended live contract check:

```text
VALIDATION_PROVIDER_INCOMPLETE_CONTRACT_PASS
```

This does not validate the repository-tool path and does not authorize another
observation. Human review must decide whether the repeated provider-incomplete
condition is acceptable experimental data or requires a separate provider
failure-policy decision. Official collection remains unauthorized.

## Files Modified

- `docs/evaluation/comparative-baseline-v4-runtime5-post-remediation-direct-validation.md`.

No runtime, schema, tool, prompt, provider, model, evaluator, or guard changes
were made during this execution task. No commit or push was performed.

VALIDATION_PROVIDER_INCOMPLETE_CONTRACT_PASS
PROVIDER_FAILURE_POLICY_HUMAN_REVIEW_REQUIRED
STOP_FOR_HUMAN_REVIEW
