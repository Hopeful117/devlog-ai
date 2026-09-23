# Comparative Baseline V4 Provider Incomplete Response Contract Remediation

## Problem and Audit Evidence

The instrumented validation
`v4-runtime4-case04-instrumented-direct-validation:CASE-04:AGENT_DIRECT_OPEN:r1`
persisted:

```text
providerResponseStatus = incomplete
output_text = ""
parseBoundary = FINAL_OUTPUT_TEXT
JSONDecodeError at position 0
```

The OpenAI SDK installed locally is `2.54.0`. Its `Response` model exposes the
following status literals:

```text
completed
failed
in_progress
cancelled
queued
incomplete
```

It also exposes `incomplete_details.reason`, whose local type permits:

```text
max_output_tokens
content_filter
```

The latest live response did not expose an incomplete reason in the persisted
diagnostic, so:

```text
INCOMPLETE_REASON_AVAILABLE = PARTIALLY
```

## Existing and Corrected Contract

Before remediation, the adapter ignored `response.status`, iterated output
items, then passed `output_text` to the final JSON parser for every response.
Thus an explicit incomplete response with empty output became a misleading
`RESPONSE_PARSE_ERROR`.

The audited contract is:

| Provider/SDK status | May parse final output? | Runtime handling | Evidence |
|---|---:|---|---|
| `completed` | Yes | Existing function-call/final-output behavior | SDK `ResponseStatus`, existing adapter |
| `incomplete` | No | `PROVIDER_FAILURE` with `PROVIDER_INCOMPLETE_RESPONSE` | SDK status and `incomplete_details` contract |
| `failed` | No | Provider-state failure should remain non-successful; not changed in this scoped remediation | SDK status; no fixture/live case authorized |
| `cancelled` | No | Provider-state failure should remain non-successful; not changed in this scoped remediation | SDK status; no fixture/live case authorized |
| `in_progress` | No | Non-terminal provider state; not changed in this scoped remediation | SDK status; adapter is not authorized to continue |
| `queued` | No | Non-terminal provider state; not changed in this scoped remediation | SDK status; adapter is not authorized to continue |
| `null/unknown` | No | Existing contract handling remains unchanged pending separate audit | SDK field is optional |

The minimum implementation intercepts only explicit `incomplete` before output
item extraction and final-output parsing. It does not retry, continue, repair,
fallback, or fabricate a response.

## Implementation

Changed:

- `ai-engine/evaluations/comparative_baseline/live_adapters.py`
  - Intercepts `response.status == "incomplete"` before parsing.
  - Persists bounded status, reason, SDK type, provider IDs, and
    `parseBoundary = NOT_REACHED`.
- `ai-engine/evaluations/comparative_baseline/collection_runtime.py`
  - Adds the single category `PROVIDER_INCOMPLETE_RESPONSE` to the existing
    provider-failure allowlist.
- `ai-engine/evaluations/comparative_baseline_v4/protocol.py`
  - Binds the behavioral change to runtime contract `5.0.0`.
- `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json`
  - Records runtime `5.0.0` and its recomputed digest.
- `ai-engine/evaluations/comparative_baseline_v4/pilot.py` and `official.py`
  - Update future plan identity guards to the new runtime-bound identities.
- `ai-engine/tests/test_comparative_live_adapters.py`
  - Adds incomplete, partial-incomplete, completed-malformed, completed-empty,
    reason, and parser-invocation fixtures.
- `ai-engine/tests/test_comparative_baseline_v4.py`
  - Updates runtime/experiment identity expectations.

For an equivalent future incomplete response, the diagnostic now contains:

```text
category = PROVIDER_INCOMPLETE_RESPONSE
requestPhase = RESPONSE_STATE
parseBoundary = NOT_REACHED
valueRepresentation = NOT_REACHED
providerResponseStatus = incomplete
providerIncompleteReason = max_output_tokens | content_filter, when exposed
```

No final-output parser is invoked. `PROVIDER_FAILURE` remains the high-level
execution status. An incomplete response remains unsuccessful.

## Behavior Preservation

- `completed` valid final output remains successful.
- `completed` malformed JSON remains `RESPONSE_PARSE_ERROR`.
- `completed` empty output remains `RESPONSE_PARSE_ERROR`.
- Function-call extraction and supported argument representations are unchanged.
- Incomplete partial output is not treated as a completed final answer.
- No retry, continuation, fallback, repair, fabricated answer, or tool change was introduced.
- Typed repository schema `comparative-v4-typed-repository-tools-2.0.0` is unchanged.
- Diagnostic instrumentation remains bounded, RAW-authoritative, DERIVED-compatible, and replay-safe.

## Accounting

`ACCOUNTING_REMEDIATION_REQUIRED_FOR_THIS_STORY = NO`.

The existing distinction remains: attempted transport and response-bearing
provider-call accounting are separate. This story does not redesign metrics or
retroactively alter the instrumented observation.

## Synthetic Historical Reproduction

Before remediation, synthetic `status=incomplete` plus `output_text=""`
produced:

```text
RESPONSE_PARSE_ERROR
json.loads called = 1
```

After remediation:

```text
PROVIDER_INCOMPLETE_RESPONSE
parseBoundary = NOT_REACHED
json.loads called = 0
```

Completed malformed output continues to produce:

```text
RESPONSE_PARSE_ERROR
json.loads called = 1
```

## Identities

| Identity | Old | New | Why |
|---|---|---|---|
| Runtime | `comparative-v4-live-runtime-contract-4.0.0` | `comparative-v4-live-runtime-contract-5.0.0` | Incomplete response state is now validated before parsing |
| Runtime digest | `3d4985ae3d5db051a6924eef0f44eac90ce66db05440bc110e69dce4cedcb7e1` | `a3211f44aad4633ca1d725b7a48506a6dd8725ab96becf182e867f1794e80edd` | Recomputed runtime contract |
| Instrumentation | `comparative-v4-instrumentation-3.0.0` | unchanged | Diagnostics preserved |
| Execution | `479031b0814373e2d5fc693bf8bfd81fc7c42af96af6e80d471a6ee646145bd4` | unchanged | Frozen execution dimensions |
| Experiment | `bb3748cd7ec513124aa8b618a89e00321839d328c528f4d5258ff68c80697cda` | `1af6f9b79427f6f53bca1f9466fde9ee01c652f5bd082a810566428f2606f4d5` | Runtime contract changed |
| Pilot/validation plan | `8fe9c97746f45e9b3ca7c266b9b0584e91a58cc2aee72810821b84ee02cef7d2` | `6c423c1548c68365fd432becaa1edb7dedd0f0c7fa070dd668fb5fcba7ecc638` | Experiment identity changed |
| Future official plan | `1ca5deba40f5ca40997dcc1d9e8308a8a9d980e471bb5134a3099706389b9490` | `fe85cf393dcf9da830af5476ff1e6a398758d97457955223e75cd646f3d521fe` | Experiment identity changed |

This remains Comparative Baseline V4, not V5.

## Verification

- Focused comparative tests: `92 passed`.
- Provider calls: `0`.
- Network calls: `0`.
- New observations: `0`.
- Parser invocation proof: incomplete `0`; completed malformed `1`.
- Typed schema invariance: `PASS`.
- Retry/fallback invariance: `PASS`.
- Diagnostic bounding: `PASS`.
- Historical artifacts, RAW, DERIVED, manifests, ledgers, and reports: untouched.
- `git diff --check`: `PASS`.

## Mandatory Answers

- A: `YES`
- B: `PARTIALLY`
- C: `NO`
- D: `YES`
- E: `YES`
- F: `PROVIDER_INCOMPLETE_RESPONSE`
- G: `YES`
- H: `NO`
- I: `NO`
- J: `NO`
- K: `YES`
- L: `YES`
- M: `YES`
- N: `NO`
- O: `YES`
- P: `NO / NO`
- Q: `NO`
- R: `NO`
- S: `YES`

## Unresolved Limitations

- The live incomplete response did not expose a persisted reason.
- `failed`, `cancelled`, `in_progress`, and `queued` are documented from the
  installed SDK contract but were not behaviorally broadened in this minimum
  remediation.
- Existing provider-call accounting still excludes a request when the adapter
  raises before returning `ProviderResponse`; this is separate future work.
- No post-remediation live response was executed.

## Readiness

No provider, network, live validation, or official collection is authorized by
this Story. A separate human decision is required for one future direct
validation under the new runtime and experiment identities.

READY_FOR_HUMAN_AUTHORIZATION_POST_REMEDIATION_DIRECT_VALIDATION
STOP_FOR_HUMAN_REVIEW
