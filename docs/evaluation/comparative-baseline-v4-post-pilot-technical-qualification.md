# Comparative Baseline V4 Post-Pilot Technical Qualification

## Scope

This is an offline qualification of the existing corrected pilot. No provider
call, network call, new pilot observation, or official observation was made.
Pilot RAW and DERIVED artifacts were not modified.

## Pilots Under Review

Accepted attempt:

```text
v4-pilot-20260917T175238Z-5df1e336
experiment: ecfb7dd92702681c5e60f6cbbf5abe8cf0617ca9ab51c0c561f5b8ea86a1afd2
execution: 74dfdcd2b06ea38d124d51b88cbc57f62b504d907f4cd793d1a77387e619fad2
pilot plan: 1df78be6679d527d1a6e5fb4712fc6314dbabd23063076a0f05c1f83473c3e3e
```

Intermediate attempt:

```text
v4-pilot-20260917T174746Z-76a39515
```

Both attempts finalized six isolated observations, but the intermediate
attempt is not accepted because it violated the tool ceiling in live execution
and failed at replay. Neither attempt is baseline-eligible.

## RESPONSE_PARSE_ERROR Investigation

The accepted `PROVIDER_FAILURE` is:

```text
assignment: CASE-01-COMPARATIVE:AGENT_DIRECT_OPEN:r1
model turns: 6
provider calls: 6
tool attempts: 7
executed operations: 5
invalid requests: 2
stop reason: PROVIDER_FAILURE
category: RESPONSE_PARSE_ERROR
exception type: JSONDecodeError
request phase: RESPONSE_PARSE
retryable: false
```

The preserved RAW contains six provider response envelopes and seven tool
trace entries. The last preserved response contains a `function_call` for
`repository_tool`; its persisted `arguments` string is valid JSON and parses
to a valid `search_repository` request. Earlier persisted function-call
arguments also parse successfully. Tool interaction had therefore already
occurred before the failure.

The adapter path is:

```text
Responses API response
-> model_dump raw envelope
-> function-call detection and argument parsing
-> final output_text JSON parsing
-> ProviderResponse / TransportFailure
```

The persisted diagnostic says that a JSON string was unterminated at character
2613, but the RAW envelope does not preserve enough information to identify
which SDK representation was passed to `json.loads` at the failing point. In
particular, the persisted function-call arguments are valid, while the
adapter recorded a parse failure. This is inconsistent with a straightforward
model response that merely violates the final answer schema.

The evidence proves a response-phase parse failure, but does not prove whether
the SDK exposed a separate malformed `output_text`, whether the adapter saw a
different in-memory representation, or whether serialization omitted the
failing value. It does demonstrate an unresolved adapter/runtime ambiguity.

Classification:

```text
UNRESOLVED_RUNTIME_OR_ADAPTER_DEFECT
```

The safe attribution is nevertheless adequate: category, exception type,
phase, retryability, digest, and bounded redacted message are present; no API
key, bearer token, cookie, header, environment value, or raw credential was
persisted.

## Censoring Qualification

### CASE-03:AGENT_DIRECT_OPEN:r1

```text
executionStatus: CENSORED
modelTurns: 9
providerCalls: 9
executedToolOperations: 11
invalidToolRequests: 1
skippedToolOperations: 1
ceilingCountedOperations: 12
stoppingReason: SAFETY_MAX_TOOL_OPERATIONS
```

The operation budget reconciles exactly:

```text
11 + 1 = 12
```

The thirteenth trace entry was preserved as skipped and did not consume the
budget. Classification:

```text
LEGITIMATE_SAFETY_ENVELOPE_OUTCOME
```

### CASE-04:AGENT_DIRECT_OPEN:r1

```text
executionStatus: CENSORED
modelTurns: 10
providerCalls: 10
executedToolOperations: 7
invalidToolRequests: 5
skippedToolOperations: 1
ceilingCountedOperations: 12
stoppingReason: SAFETY_MAX_TOOL_OPERATIONS
```

The operation budget reconciles exactly:

```text
7 + 5 = 12
```

The final trace entry was preserved as skipped. Malformed requests and
inefficient navigation are model behavior, not runtime defects. Classification:

```text
LEGITIMATE_SAFETY_ENVELOPE_OUTCOME
```

## Identity Contract

The experiment identity hashes the canonical payload containing the benchmark,
questions, oracle, repository revision, conditions, provider, model, execution
configuration, recovery policy, grounding/semantic contracts, resource and
RAW/projection/instrumentation identities, assignment plan, repetition count,
and safety values.

The execution hash covers the execution configuration fields and safety
values, including output token limits, tool schema, native timeouts, and retry
policy. It does not hash the Python runtime implementation or a source commit.

The pilot-plan identity hashes the experiment identity, the six frozen
assignments, and repetition count.

## Runtime-State Comparison and Identity Impact

### Tool-ceiling ordering

The intermediate attempt processed invalid calls before checking the ceiling.
The accepted attempt checks the ceiling first and records all later requests as
skipped once twelve counted operations have been reached.

This changes live execution behavior and persisted trace/accounting outcomes,
even though the corrected behavior is the already-frozen approved rule.

```text
LIVE_EXECUTION_SEMANTICS: changed between attempts
PERSISTED_OUTPUT_CONTRACT: changed in observed trace/accounting values
DERIVED_ONLY_SEMANTICS: no
REPLAY_ONLY_SEMANTICS: no
```

Identity impact:

```text
new experiment identity: YES, required to distinguish the actual runtime states
new execution configuration identity: NO, frozen configuration values did not change
new schema/projection identity: NO, the existing V4 2.0 contracts already describe the corrected fields
```

The existing identity does not include a runtime implementation/version digest.
It therefore cannot distinguish the nonconforming intermediate live runtime
from the corrected live runtime. The accepted attempt should not be treated as
fully identity-qualified under the existing contract merely because its hash
recomputes today.

### Replay handling

The replay change accepts valid non-dict RAW payloads for provider-failure
observations and avoids reading tool traces from them.

```text
LIVE_EXECUTION_SEMANTICS: no
PERSISTED_OUTPUT_CONTRACT: no
DERIVED_ONLY_SEMANTICS: no
REPLAY_ONLY_SEMANTICS: yes
```

Identity impact:

```text
new experiment identity: NO
new execution configuration identity: NO
new schema/projection identity: NO
```

This is a replay compatibility fix and does not alter live observations.

## Accepted Identity Classification

The hash is reproducible and its frozen inputs match the accepted artifact.
However, the contract omits the runtime implementation/version state needed
to distinguish the two live behaviors. Therefore:

```text
IDENTITY_CONTRACT_INSUFFICIENT
```

No identity or artifact was rewritten.

## Replay, Integrity, and Resource Invariants

Offline checks confirm:

```text
RAW: 6
DERIVED: 6
replay: 6/6 PASS
replay provider calls: 0
replay network calls: 0
attempt isolation: PASS
contamination: PASS
max ceilingCountedOperations: 12
max largestSingleResultBytesDelivered: 39296
```

Cumulative repository bytes are not compared to the per-operation limit. The
largest individual delivered result remains below 65,536 bytes.

## Verification

```text
python -m pytest tests/test_comparative_baseline_v4.py tests/test_comparative_baseline.py tests/test_comparative_collection_runtime.py tests/test_comparative_live_adapters.py -ra
87 passed

python -m evaluations.comparative_baseline_v4.preflight
PASS

python -m compileall -q evaluations/comparative_baseline_v4 evaluations/comparative_baseline/live_adapters.py
PASS

git diff --check
PASS
```

Provider calls during qualification: `0`.
Network calls during qualification: `0`.
New pilot observations during qualification: `0`.
Official observations during qualification: `0`.

## Remaining Uncertainty

The exact in-memory SDK value that triggered the accepted parse exception is
not reconstructable from immutable RAW. The persisted evidence is sufficient
to identify the response phase and the exception class, but not sufficient to
clear the adapter/runtime inconsistency.

## Readiness

```text
NOT_READY_UNRESOLVED_TECHNICAL_DEFECT
```

The unresolved response parsing inconsistency and insufficient runtime-state
identity contract must be reviewed before official collection. No new pilot
was prepared or executed in this task.

The exact remaining human decision is:

> Review the unresolved RESPONSE_PARSE_ERROR evidence and identity-contract gap, then decide whether to authorize remediation and a separately identified qualification pilot before any official 18-slot collection.
