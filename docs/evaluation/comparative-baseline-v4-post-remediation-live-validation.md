# V4 Post-Remediation Technical Live Validation

## 1. Authorization Scope

This execution used the explicit authorization for exactly one post-remediation
technical live validation. No retry, second observation, DEVLOG execution,
pilot, official collection, treatment change, or automatic remediation occurred.

Exact observation:

```text
condition = AGENT_DIRECT_OPEN
question = CASE-04@1.0.0
repetition = r1
assignment = CASE-04:AGENT_DIRECT_OPEN:r1
```

## 2. Repository and Identities

```text
repositoryRevision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
executionConfigurationHash = f72225f4bb0d7e062dab8eb4b1120f600734fac2e51a9573e910d306c29e6ea8
experimentIdentity = 5cef12f1e9767b9b8ede4213f56cb18be00f7880ef2f16736d55df5c9d413ee4
runtime = comparative-v4-live-runtime-contract-5.1.0
runtimeDigest = 71dfe66779cba85f5439e7eb18b3af7cd95b0e4d9bc46c797316d81d705598a1
instrumentation = comparative-v4-instrumentation-4.0.0
rawSchema = comparative-v4-raw-observation-4.0.0
projection = comparative-v4-deterministic-projection-4.0.0
```

Frozen treatment verified from the current manifest:

```text
provider = openai
model = gpt-4.1-mini
finalOutputTokens = 32768
intermediateOutputTokens = 32768
providerMaxRetries = 0
tools = six V4 typed repository tools
structuredOutput = strict V4 JSON Schema
```

Safety ceilings verified:

```text
maxToolOperations = 96
maxModelTurns = 48
maxProviderCallsPerObservation = 48
maxWallClockSeconds = 300
maxReadBytesPerOperation = 65536
```

## 3. Preflight

The corrected provider-free preflight passed:

```text
status = PASS
providerCalls = 0
networkCalls = 0
deterministicReplay = READY
provider = openai
model = gpt-4.1-mini
secret configured = YES, not printed or persisted
```

A preliminary wrapper invocation failed before provider construction because
stdin-based `__file__` resolution supplied the wrong repository root to the
Java bridge. It made zero provider/network calls, changed no configuration, and
was corrected by using the explicit repository root. The authoritative corrected
preflight above passed before the live observation.

## 4. Observation and Storage

```text
runId = v4-post-remediation-live-20260918T125409Z-e1b727a9
observationId = v4-post-remediation-live-20260918T125409Z-e1b727a9:CASE-04:AGENT_DIRECT_OPEN:r1
attemptRoot = data/comparative-baseline-v4/live-pilot/v4-post-remediation-live-20260918T125409Z-e1b727a9
```

Persisted files:

- `artifacts/CASE-04:AGENT_DIRECT_OPEN:r1.json`
- `derived/CASE-04:AGENT_DIRECT_OPEN:r1.json`
- `ledger.json`

The ledger contains exactly one planned and finalized assignment, with no
missing or unexpected assignments.

## 5. Provider Trajectory

The provider received the frozen first-turn DIRECT request and returned before
the first usable tool call:

```text
requested model = gpt-4.1-mini
resolved model = gpt-4.1-mini-2025-04-14
max_output_tokens = 32768
status = incomplete
incomplete reason = max_output_tokens
```

No tool-call trajectory began. The behavior reproduced under the corrected
measurement contract.

Provider identifiers:

```text
providerResponseId = resp_041839fa5cb4d609006aad347451bc87d28db2bb8698dfe820
providerRequestId = req_0c4bd524b5864a599c344ea4eec947ab
```

## 6. Repository Tool Trajectory

```text
repository tool execution began = NO
valid repository operations = 0
invalid repository attempts = 0
executed tool operations = 0
```

The provider response contained no output items, so there was no incomplete
function-call-like item to execute.

## 7. Transport and Response Accounting

Persisted resources:

```text
providerTransportAttempts = 1
providerResponsesReceived = 1
usableProviderResponses = 0
responseBearingModelTurns = 0
totalProviderCalls = 0
modelTurns = 0
```

The new lifecycle accounting correctly records one attempted transport and one
received provider response while preserving the historical successful-
normalization semantics of `totalProviderCalls` and `modelTurns`.

Latency:

```text
providerTransportLatencyMs = 2933
providerLatencyMs = NOT_MEASURED
assignmentLatencyMs = 2933
```

The transport latency is retained even though no usable response was produced.

## 8. Usage

The provider explicitly exposed:

```text
input = 0
output = 0
```

These explicit zeros were preserved as zeros, not converted to
`NOT_MEASURED`. No token usage was inferred from `max_output_tokens=32768` and
no cost was invented (`NOT_AVAILABLE`).

## 9. Incomplete Diagnostics

The persisted `rawOutput.providerAttempts[0]` contains:

```text
transportAttempted = true
providerResponseReceived = true
usableProviderResponse = false
responseBearingModelTurn = false
usage = {inputTokens: 0, outputTokens: 0, totalTokens: 0}
providerResponseId = resp_041839fa5cb4d609006aad347451bc87d28db2bb8698dfe820
providerRequestId = req_0c4bd524b5864a599c344ea4eec947ab
requestedModel = gpt-4.1-mini
resolvedProviderModel = gpt-4.1-mini-2025-04-14
providerResponseStatus = incomplete
providerIncompleteReason = max_output_tokens
maxOutputTokens = 32768
outputItemCount = 0
outputItems = []
outputItemsTruncated = false
parseBoundary = NOT_REACHED
transportLatencyMs = 2933
```

The top-level provider failure is classified as:

```text
category = PROVIDER_INCOMPLETE_RESPONSE
primaryDiagnostic = PROVIDER_FAILURE
stoppingReason = PROVIDER_FAILURE
```

Runtime safety behavior was preserved exactly: capture diagnostics, classify
provider failure, stop. No parsing, continuation, retry, repair, fallback, or
tool execution occurred.

## 10. Quality Evaluation

No final answer was produced:

```text
structural = NOT_EVALUATED
semantic = NOT_EVALUATED
correctGroundedAnswer = NOT_EVALUATED
```

This is not a semantic-quality failure. The provider behavior terminated the
technical trajectory before a usable response existed.

## 11. Replay

Deterministic replay succeeded from the persisted observation:

```text
replayed = true
providerCalls = 0
networkCalls = 0
```

Replay reproduced the provider lifecycle accounting and terminal status without
contacting the provider or executing tools.

## 12. Observability Remediation Verification

The remediation behaved as designed:

- The single provider transport was counted.
- The provider response object was counted separately.
- The incomplete response was not counted as usable.
- The incomplete response was not counted as a response-bearing model turn.
- Explicit provider zero usage was preserved.
- Provider/request IDs and requested/resolved models were persisted.
- Incomplete reason, max output, output count, bounded item list, and parse boundary were persisted.
- Transport latency was retained.
- No incomplete content was parsed or executed.
- RAW, DERIVED, and ledger artifacts were written immutably.
- Replay remained provider-free.

## 13. Experimental Interpretation

The post-remediation validation reproduced:

```text
six V4 typed tools
+ strict V4 structured output
+ gpt-4.1-mini
    -> incomplete / max_output_tokens
    -> outputItemCount = 0
    -> provider usage = 0/0/0
    -> no usable tool call
```

This does not invalidate the observability remediation. It confirms that the
provider behavior remains experimentally unresolved under correct measurement.
No token-budget conclusion is drawn, and no treatment change is authorized.

## 14. Mandatory Answers A-Z

- A: `1`.
- B: `AGENT_DIRECT_OPEN`.
- C: `CASE-04@1.0.0`.
- D: `r1`.
- E: `NO`; DEVLOG was not executed.
- F: `NO`; the pilot was not executed.
- G: `NO`; official collection was not executed.
- H: `NO`; no retries were performed.
- I: `NO`; the model was not changed.
- J: `NO`; the prompt was not changed.
- K: `NO`; the tools were not changed.
- L: `NO`; strict structured output was not changed.
- M: `NO`; the token ceiling was not changed.
- N: `1` provider transport attempt.
- O: `1` provider response received.
- P: `0` usable provider responses.
- Q: `0` response-bearing model turns.
- R: `input=0`, `output=0`, `total=0`.
- S: `NO`; repository tool execution did not begin.
- T: `0` valid repository tool operations.
- U: `0` invalid repository tool attempts.
- V: `NO`; no final answer was produced.
- W: `PROVIDER_FAILURE`.
- X: `YES`; replay succeeded with `providerCalls=0` and `networkCalls=0`.
- Y: `YES`; RAW and DERIVED record one attempted transport, one received response, zero usable responses/model turns, explicit zero usage, bounded diagnostics, and 2933 ms transport latency.
- Z: `YES`; the frozen DIRECT treatment received one real provider opportunity and the provider behavior was measured correctly, although it did not reach a usable tool/final trajectory.

## 15. Additional Answers AA-AJ

- AA: `YES`; `incomplete / max_output_tokens` occurred before the first usable tool call.
- AB: `input=0`, `output=0`, `total=0`.
- AC: `0` output items.
- AD: `NO`; no output items were exposed.
- AE: `NO`; no incomplete output item executed.
- AF: `NO`; no incomplete response was parsed as a final answer.
- AG: `YES`; `providerTransportAttempts=1` and RAW records `transportAttempted=true`.
- AH: `YES`; explicit zero usage was preserved; absent-usage handling was covered by the offline remediation tests, though absent usage was not the live outcome.
- AI: `YES`; the live run validated transport accounting, provider-response accounting, incomplete diagnostics, zero usage, latency, RAW/DERIVED persistence, and provider-free replay.
- AJ: `NO`; official collection requires a later human decision.

## 16. Classification and Readiness

The provider behavior reproduced while the new observability/accounting contract
worked correctly:

```text
classification = POST_REMEDIATION_VALIDATION_PROVIDER_BEHAVIOR_REPRODUCED
readiness = READY_FOR_HUMAN_EXPERIMENTAL_DESIGN_DECISION
```

## 17. Exact Next Human Decision

Decide whether to open a separate experimental-design investigation into the
interaction between the six typed repository tools and strict structured final
output. Do not change tokens, model, prompt, tools, schema, or retries in this
validation. Do not authorize official collection or another validation from
this report alone.

No commit, push, merge, rebase, reset, or history rewrite was performed.

POST_REMEDIATION_VALIDATION_PROVIDER_BEHAVIOR_REPRODUCED
READY_FOR_HUMAN_EXPERIMENTAL_DESIGN_DECISION
STOP_FOR_HUMAN_REVIEW
