# Comparative Baseline V4 Pilot Instrumentation Corrections

## Scope

This Story corrects provider-failure observability, tool accounting, byte
accounting, and versioned projections only. It does not change questions,
oracle, benchmark, repository revision, provider, model, prompts, conditions,
Policy A, ceilings, native timeouts, grounding, semantic evaluation, or the
primary outcome.

No provider call, network call, second pilot, or official collection was made.

## Historical Pilot Defects

The first pilot remains immutable:

```text
attempt: v4-pilot-20260917T153930Z-aa59d848
experiment: 01b42a88a6b94311fd912b38dbfaa4a4ceed975502cab11e80d527f1dfdad7f6
execution: 74dfdcd2b06ea38d124d51b88cbc57f62b504d907f4cd793d1a77387e619fad2
pilot plan: 5fb719dea51b3debde68b546c1de59ab52030f0e8d76a9a5c483228f30bddeef
```

Its RAW and DERIVED artifacts were not rewritten, relabeled, or replaced.

## Provider-Failure Information Loss

The former path was:

`OpenAI client -> OpenAIProviderTransport -> TransportFailure -> V4 runtime -> RAW`

`OpenAIProviderTransport.complete` caught every exception and raised the generic
`TransportFailure("OpenAI transport failed")`, preserving the original only as a
Python exception cause. The V4 runtime caught that exception without reading any
cause or metadata and built a `PROVIDER_FAILURE` observation with an empty raw
response. Consequently, neither the provider exception type, HTTP status,
request phase, nor safe message was persisted.

The two historical direct failures contain successful preceding responses and
tool traces, but no persisted transport exception or safe failure diagnostic.

```text
HISTORICAL_PROVIDER_FAILURE_CAUSE = UNRECOVERABLE
```

No cause is inferred from timing, provider identity, or the final stopping state.

## Provider-Failure Diagnostic Contract

New-format RAW observations carry an optional `providerFailure` object:

| Field | Meaning |
|---|---|
| `category` | Deterministic taxonomy value. |
| `exceptionType` | Exception class name only, never a serialized exception. |
| `providerErrorCode` | Allow-listed provider code when safely observable. |
| `httpStatus` | Integer HTTP status when observable, otherwise `null`. |
| `requestPhase` | Request, response parsing, or response contract phase. |
| `retryable` | Deterministic classification for the observed category/status. |
| `messageDigest` | SHA-256 of the sanitized message. |
| `safeMessage` | Bounded, single-line, redacted diagnostic message. |

The taxonomy is:

```text
TIMEOUT
CONNECTION_ERROR
HTTP_ERROR
RATE_LIMIT
AUTHENTICATION_ERROR
INVALID_REQUEST
PROVIDER_SERVER_ERROR
RESPONSE_PARSE_ERROR
RESPONSE_CONTRACT_ERROR
TRANSPORT_ERROR
UNKNOWN_PROVIDER_FAILURE
```

Observable timeout, connection, HTTP, parsing, and response-contract failures
are classified explicitly. Unknown values remain
`UNKNOWN_PROVIDER_FAILURE`; no provider-specific cause is guessed.

`PROVIDER_TIMEOUT` remains distinct from `PROVIDER_FAILURE`. Native timeout
diagnostics are also safely attached to the observation while the top-level
execution status remains `PROVIDER_TIMEOUT`.

## Redaction and Security

Only the allow-listed fields above are persisted. The implementation never
persists request headers, authorization data, API keys, cookies, environment
values, or the original exception object. Safe messages are bounded and redact
Bearer values, `sk-` tokens, and common secret-like key/value patterns before
their digest is calculated. `assert_secret_free` is applied before observation
persistence.

Provider diagnostics are written after the provider interaction and are never
added to another observation's conversation or condition input.

## Tool-Accounting Contract

The new resource contract is
`comparative-v4-resource-accounting-2.0.0`.

| Counter | Definition |
|---|---|
| `toolAttempts` | Every model-originated tool request represented in the trace, including invalid and skipped requests. |
| `executedToolOperations` | Requests that passed contract and safety gates and entered tool execution, including tool errors, timeouts, and rejected results. |
| `invalidToolRequests` | Requests rejected by the frozen tool contract before tool invocation. |
| `skippedToolOperations` | Recognizable requests not invoked because a safety ceiling prevented execution. |
| `ceilingCountedOperations` | Exact numerator used by `maxToolOperations`: executed operations plus invalid requests; skipped requests do not count. |
| `toolCalls` | Backward-compatible legacy aggregate equal to `toolAttempts` in new observations. |

The exact approved rule is:

```text
maxToolOperations counts executedToolOperations + invalidToolRequests.
```

The runtime checks `ceilingCountedOperations` before executing a valid request.
Invalid requests consume the same approved operation budget as before. A
post-ceiling skipped request remains in RAW but does not consume the ceiling.

For the historical CASE-03 trace, the corrected offline projection is:

```text
toolAttempts = 13
executedToolOperations = 6
invalidToolRequests = 6
skippedToolOperations = 1
ceilingCountedOperations = 12
```

The skipped trace entry is preserved.

## Byte-Accounting Contract

New observations expose:

| Field | Definition |
|---|---|
| `resultBytesProduced` | Canonical serialized bytes produced by a tool result. |
| `resultBytesDelivered` | Produced result bytes delivered to the model. |
| `resultBytesRejected` | Produced result bytes rejected by the per-operation result ceiling. |
| `cumulativeRepositoryBytesDelivered` | Sum of delivered repository result bytes across the observation. |
| `largestSingleResultBytesProduced` | Maximum produced result size for one operation. |
| `largestSingleResultBytesDelivered` | Maximum delivered result size for one operation. |

Existing aliases remain for compatibility, but the new names are authoritative
for reporting. There is still no separate pre-execution request-byte measure;
that field is `NOT_RECONSTRUCTABLE` for historical data and unavailable when
not supplied by the tool contract.

`maxReadBytesPerOperation` is compared only with the largest relevant single
result. Cumulative bytes are never used as its denominator. Therefore a
cumulative value such as historical CASE-04's 70,105 bytes cannot imply a
107% single-result utilization; its largest individual result was 35,376 bytes.

## Historical RAW Compatibility

The first pilot's `comparative-v4-raw-observation-1.0.0` RAW remains readable by
replay. New replay logic recognizes the old schema and preserves its historical
projection semantics. The corrected accounting can be generated offline via a
versioned compatibility projection, but no historical DERIVED file is replaced
in place.

Historical reconstruction status:

| Field group | Reconstructability | Basis |
|---|---|---|
| Tool attempts, executed operations, invalid requests, skipped operations | EXACT | Immutable `toolTrace.executionStatus` entries. |
| Ceiling-counted operations | EXACT | Executed plus invalid trace states, excluding skipped states. |
| Produced, delivered, rejected, and largest result bytes | EXACT | Immutable `resultByteCount` and `delivered` fields. |
| Cumulative repository bytes delivered | EXACT | Sum of immutable delivered result bytes. |
| Pre-execution requested bytes | NOT_RECONSTRUCTABLE | Not persisted by V3/V4 1.x. |
| Historical provider-failure cause | NOT_RECONSTRUCTABLE for the two failures | Generic `TransportFailure` was persisted without cause metadata. |

The old experiment identity remains permanently associated with the first pilot;
the compatibility projection does not reinterpret it as the corrected identity.

## Replay Compatibility

Replay remains provider-free and network-free. For new RAW, deterministic replay
recomputes evaluation and corrected tool accounting from the trace and compares
them with the persisted projection. For old RAW, replay uses the historical
projection path while `reconstruct_historical_accounting` provides an offline,
non-mutating corrected view.

## Identity Impact

Instrumentation changes materially affect persisted RAW, resource accounting,
and DERIVED projection semantics. The following identities were versioned:

```text
resource accounting: comparative-v4-resource-accounting-2.0.0
raw schema: comparative-v4-raw-observation-2.0.0
projection: comparative-v4-deterministic-projection-2.0.0
instrumentation: comparative-v4-instrumentation-1.0.0
```

The benchmark, questions, oracle, provider, model, execution-envelope values,
ceilings, timeouts, retry policy, and condition meanings are unchanged. The
previous experiment identity is not reused.

New corrected identity:

```text
identityVersion: comparative-v4-experiment-identity-1.0.0
sha256: ecfb7dd92702681c5e60f6cbbf5abe8cf0617ca9ab51c0c561f5b8ea86a1afd2
```

New six-slot plan identity, prepared but not executed:

```text
identityVersion: comparative-v4-pilot-plan-identity-1.0.0
sha256: 1df78be6679d527d1a6e5fb4712fc6314dbabd23063076a0f05c1f83473c3e3e
```

The plan remains the unchanged six cells at repetition 1. A future attempt must
use a fresh isolated `LIVE_PILOT` attempt path.

## Verification

Deterministic tests cover provider connection/server/timeout/unknown and parse
failure attribution, secret redaction, no retry, Policy A accounting, tool
timeouts/errors, ceiling skips, cumulative bytes above the per-result limit,
legacy accounting reconstruction, replay, identity, and contamination.

Executed:

```bash
python -m evaluations.comparative_baseline_v4.preflight
python -m pytest tests/test_comparative_baseline_v4.py tests/test_comparative_baseline.py tests/test_comparative_collection_runtime.py tests/test_comparative_live_adapters.py -ra
python -m compileall -q evaluations/comparative_baseline_v4 evaluations/comparative_baseline/live_adapters.py
git diff --check
```

Results:

```text
preflight: PASS
provider calls: 0
network calls: 0
second pilot executed: false
official collection executed: false
focused tests: 86 passed in 0.60s
compile/import check: PASS
git diff --check: PASS
contamination: PASS
historical replay: PASS
```

## Remaining Defects

- The first pilot's two provider failure causes remain permanently
  unrecoverable; the new contract diagnoses only future observations.
- Daemon workers cannot be forcibly cancelled; native provider, Git, and bridge
  timeouts remain mandatory liveness controls.
- `bytesRequested` is not available in the current tool contract and remains
  explicitly unavailable rather than being estimated.
- `toolCalls` remains as a compatibility alias; consumers must use the explicit
  v2 counters for ceiling utilization.

## Readiness

```text
READY_FOR_SECOND_PILOT_HUMAN_AUTHORIZATION
```

The second pilot was not executed. The exact remaining human decision is to
review this instrumentation correction and explicitly authorize a new isolated
six-slot V4 pilot under the corrected experiment identity.
