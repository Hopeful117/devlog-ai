# Comparative Baseline V4 Runtime 4 Instrumented Direct Validation

## Validation Identity

- Authorized assignment: `CASE-04:AGENT_DIRECT_OPEN:r1`
- Question version: `1.0.0`
- Attempt ID: `v4-runtime4-case04-instrumented-direct-validation`
- Observation ID: `v4-runtime4-case04-instrumented-direct-validation:CASE-04:AGENT_DIRECT_OPEN:r1`
- Experiment identity: `bb3748cd7ec513124aa8b618a89e00321839d328c528f4d5258ff68c80697cda`
- Runtime: `comparative-v4-live-runtime-contract-4.0.0`
- Runtime digest: `3d4985ae3d5db051a6924eef0f44eac90ce66db05440bc110e69dce4cedcb7e1`
- Execution identity: `479031b0814373e2d5fc693bf8bfd81fc7c42af96af6e80d471a6ee646145bd4`
- Typed schema: `comparative-v4-typed-repository-tools-2.0.0`
- Repository revision: `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`

## Outcome

The single authorized validation terminated before any repository-tool
interaction:

```text
executionStatus = PROVIDER_FAILURE
primaryDiagnostic = PROVIDER_FAILURE
stoppingReason = PROVIDER_FAILURE
category = RESPONSE_PARSE_ERROR
```

The new instrumentation attributes the exact parser boundary:

```text
parseBoundary = FINAL_OUTPUT_TEXT
providerResponseStatus = incomplete
sdkResponseType = Response
sdkItemType = null
valueRepresentation = JSON_TEXT
valueRuntimeType = str
textLength = 0
textEmpty = true
textWhitespaceOnly = false
textSha256 = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
exceptionType = JSONDecodeError
jsonErrorMessage = Expecting value
jsonErrorPosition = 0
jsonErrorLine = 1
jsonErrorColumn = 1
```

The provider exposed safe identifiers:

```text
providerResponseId = resp_01f455e8aaf32066006aad0d75fe4887d2972b0454fc53d88f
providerRequestId = req_738678ea43b24a6da4d79acea9f4c305
```

The causal boundary is now established as: the provider returned an
`incomplete` response with empty `output_text`; the adapter attempted its
existing final-output JSON parse; `json.loads("")` raised the observed
offset-zero error; the runtime classified it as `PROVIDER_FAILURE` with
`RESPONSE_PARSE_ERROR`. The reason the provider response was incomplete remains
unresolved from the persisted evidence.

## Interaction and Accounting

- Repository-tool calls: `0`.
- Tool attempts: `0`.
- Model turns recorded: `0`.
- Technical retries: `0`.
- Runtime provider transport request: `1`.
- Network transport: `1` live provider transport is necessarily involved; the runner's `networkCalls` summary remains `0` because it is not independently instrumented.
- Replay provider calls: `0`.
- Replay network calls: `0`.
- Assignment latency: `3095 ms`.
- Provider latency: not measured by the current runtime after the failed response.

The `resources` object reports zero response-bearing provider calls because
`record_provider` runs only after `complete()` returns a `ProviderResponse`.
This validation does not change that accounting semantic. The provider request
and response identity are independently evidenced by the persisted diagnostic.

## Artifact Verification

RAW:

```text
data/comparative-baseline-v4/live-pilot/v4-runtime4-case04-instrumented-direct-validation/artifacts/CASE-04:AGENT_DIRECT_OPEN:r1.json
artifactVersion = comparative-v4-raw-observation-3.0.0
```

DERIVED:

```text
data/comparative-baseline-v4/live-pilot/v4-runtime4-case04-instrumented-direct-validation/derived/CASE-04:AGENT_DIRECT_OPEN:r1.json
projectionVersion = comparative-v4-deterministic-projection-3.0.0
```

The diagnostic is present in both RAW and DERIVED. Replay recomputed the
deterministic evaluation and projection successfully:

```text
replayed = true
deterministicEvaluationRecomputed = true
deterministicProjectionRecomputed = true
```

No historical artifact was modified.

## Behavior and Privacy

- No parser repair, fallback, retry, schema, tool, prompt, provider, or model change was made during validation.
- Existing `RESPONSE_PARSE_ERROR` classification was preserved.
- No raw provider payload, full SDK object, function arguments, or output preview was persisted.
- Diagnostic fields are bounded and content is represented by category, length, state, hash, and exception metadata.
- The failure is conclusively distinguishable from `FUNCTION_CALL_ARGUMENTS`; no function-call item existed in this response.

## Setup Note

An initial process launch failed before provider construction because it was
started from `ai-engine`, where the repository root `.env` and Maven wrapper
were not resolvable. It made no provider/network request and created no
observation. The authorized validation then ran once from the repository root
with the existing `.env` configuration.

## Readiness and Decision

This one validation is complete. No retry, second validation, official
collection, or parser remediation is authorized by this result. Human review
must decide whether to authorize a separate parser/response-contract change or
to stop the comparative baseline. The new instrumentation is sufficient for
future attribution, but this observation does not establish normal runtime/tool
contract validity.

READY_FOR_HUMAN_REVIEW_NO_RETRY_OR_OFFICIAL_COLLECTION
STOP_FOR_HUMAN_REVIEW
