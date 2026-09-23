# Comparative Baseline V4 Parse Diagnostic Instrumentation

## Status

This report records diagnostic-only instrumentation for the two previously
observed pre-interaction `RESPONSE_PARSE_ERROR` failures. No provider request,
network request, live validation, or observation was created.

## Motivation and Evidence Addressed

The instrumentation addresses the previously missing branch, SDK/runtime type,
representation, text characteristics, deterministic fingerprint, provider
identifiers, and exact `JSONDecodeError` position. It does not retain complete
provider responses or change parsing semantics.

## Instrumentation Points

- `ai-engine/evaluations/comparative_baseline/live_adapters.py`
  - `_normalize_function_arguments`: function-call argument boundary immediately before the existing `json.loads` call.
  - final `output_text` branch: final-output boundary immediately before the existing `json.loads` call.
  - `_sdk_item_data`: bounded SDK item extraction without arbitrary object serialization.
- `ai-engine/evaluations/comparative_baseline/collection_runtime.py`
  - `TransportFailure.attribution` and `provider_failure_attribution`: carries safe diagnostics into the existing failure path.
- `ai-engine/evaluations/comparative_baseline_v4/protocol.py`
  - existing `providerFailure` RAW field and deterministic projection preserve the diagnostic attribution for replay.

## Diagnostic Contract

The failure attribution now includes the following where truthful:

```text
category
requestPhase
parseBoundary
sdkResponseType
sdkItemType
sdkItemDiscriminator
valueRuntimeType
valueRepresentation
textLength
textEmpty
textWhitespaceOnly
textSha256
providerResponseId
providerResponseStatus
providerRequestId
providerItemId
exceptionType
jsonErrorMessage
jsonErrorPosition
jsonErrorLine
jsonErrorColumn
```

The parsing branches are unambiguous: `FUNCTION_CALL_ARGUMENTS` and
`FINAL_OUTPUT_TEXT`. The existing `JSON_TEXT` representation category is
retained for textual parse inputs; runtime/type fields distinguish the SDK
object and value type.

Provider response ID, status, request ID, and function-call item ID are copied
only when already exposed by the SDK response/object. Otherwise they are
omitted rather than invented. No additional provider request is made.

## Privacy and Bounding

No `str(response)`, `repr(response)`, complete response JSON, argument content,
or output preview is persisted. Text diagnostics contain only UTF-8 length,
empty/whitespace booleans, SHA-256, and decoder metadata. Type and identifier
strings are bounded to 128 characters; exception message metadata is bounded
to the existing safe 256-character attribution limit and secret checks remain
enforced.

`DIAGNOSTIC_DATA_BOUNDING = PASS`.

## RAW, DERIVED, Replay, and Accounting

`providerFailure` is persisted in the V4 RAW observation as authoritative
failure evidence. DERIVED projects the same field and never reconstructs an
unavailable SDK object. Replay consumes persisted JSON only and remains
provider/network-free.

Instrumentation does not alter provider-call accounting, network transports,
model turns, tool attempts, valid/invalid requests, runtime failures, tokens,
latency, retry policy, termination, or conversation behavior. A provider call
that fails before a response-bearing turn remains a provider failure.

## Behavior Equivalence

Synthetic valid, structured, empty, whitespace-only, malformed, null, scalar,
and multiple-call fixtures cover function-call and final-output paths. The
malformed fixtures retain `RESPONSE_PARSE_ERROR`; they add attribution only.
Structured mappings, supported SDK-native argument objects, and JSON strings
remain accepted through their existing normalization paths.

Equivalent offset-zero failures now persist branch, runtime/SDK metadata, text
length/state, fingerprint, and exact decoder position.

`FUTURE_EQUIVALENT_FAILURE_DIAGNOSABLE = YES`.

## Historical Latency Mismatch

The `2613 ms` versus `1597 ms` discrepancy is a historical human-facing
summary/report-generation mismatch. The immutable RAW artifacts are correct and
were not rewritten. No report-system refactoring was performed.

## Identity Decision

The existing identity model separates instrumentation, RAW schema, projection,
and experiment identity from behavioral runtime identity. Therefore the live
runtime contract and execution configuration remain unchanged; the diagnostic
contract receives the existing next version and dependent identities are
recomputed deterministically.

| Identity | Old | New | Reason |
|---|---|---|---|
| Runtime | `comparative-v4-live-runtime-contract-4.0.0` / digest `3d4985ae3d5db051a6924eef0f44eac90ce66db05440bc110e69dce4cedcb7e1` | unchanged | Parsing/runtime semantics are unchanged |
| Instrumentation | `comparative-v4-instrumentation-2.0.0` | `comparative-v4-instrumentation-3.0.0` | New bounded diagnostic fields |
| RAW schema | `comparative-v4-raw-observation-2.0.0` | `comparative-v4-raw-observation-3.0.0` | Persisted provider failure attribution |
| Projection | `comparative-v4-deterministic-projection-2.0.0` | `comparative-v4-deterministic-projection-3.0.0` | DERIVED provider-failure projection |
| Execution | `479031b0814373e2d5fc693bf8bfd81fc7c42af96af6e80d471a6ee646145bd4` | unchanged | Frozen execution dimensions |
| Experiment | `8a18746c1d387cb9375d186a2b8ab8da38c190eb854a918dddbe20dcf38d5910` | `bb3748cd7ec513124aa8b618a89e00321839d328c528f4d5258ff68c80697cda` | Depends on instrumentation/RAW/projection identities |
| Pilot plan | `01b42a88a6b94311fd912b38dbfaa4a4ceed975502cab11e80d527f1dfdad7f6` | `8fe9c97746f45e9b3ca7c266b9b0584e91a58cc2aee72810821b84ee02cef7d2` | Depends on new experiment identity |

Typed schema `comparative-v4-typed-repository-tools-2.0.0` is unchanged.
Frozen benchmark, questions, oracle, repository revision, conditions,
repetitions, prompts, provider/model, evaluators, taxonomy, Policy A,
completion, capabilities, retries, guards, and resource semantics are
unchanged.

## Verification

- Focused comparative tests: `pytest -q tests/test_comparative_live_adapters.py tests/test_comparative_baseline_v4.py` passed.
- V4 preflight: `PASS`; `officialCollectionAllowed = false`.
- `git diff --check`: `PASS`.
- Provider calls: `0`.
- Network calls: `0`.
- New observations: `0`.
- Historical RAW/DERIVED/manifests/ledgers/reports: untouched.
- Application production behavior: unchanged; evaluation adapter/runtime and tests were modified only for diagnostics and existing frozen-runtime support.

## Mandatory Answers

| Question | Answer |
|---|---|
| A. Distinguish function-call from final-output parsing? | `YES` |
| B. Identify SDK/runtime value type? | `YES` |
| C. Identify representation category? | `YES` |
| D. Text length and empty/whitespace state? | `YES` |
| E. Correlate malformed values without unrestricted raw content? | `YES` |
| F. Capture exposed provider response/request IDs? | `YES` |
| G. Persist exact JSON exception position? | `YES` |
| H. Future offset-zero failure sufficiently diagnosable? | `YES` |
| I. Parsing behavior altered? | `NO` |
| J. Retry behavior altered? | `NO` |
| K. Failure classification altered? | `NO` |
| L. Accounting semantics altered? | `NO` |
| M. Typed schema unchanged? | `YES` |
| N. Experimental dimensions preserved? | `YES` |
| O. Historical artifacts preserved? | `YES` |
| P. Provider/network calls made? | `NO / NO` |
| Q. New observations created? | `NO` |
| R. Another live validation technically ready for separate authorization? | `YES` |

## Unresolved Limitations

- Historical failures remain without the new fields because immutable artifacts were not backfilled.
- Provider/request IDs remain unavailable when the SDK does not expose them at the failure boundary.
- The diagnostic category `JSON_TEXT` identifies the textual path; the added runtime/type fields provide the finer SDK representation detail.
- No live occurrence has yet confirmed the new fields against the real provider.

## Readiness

The next live validation is not authorized by this Story. Human review must
decide whether to authorize exactly one frozen `AGENT_DIRECT_OPEN / CASE-04@1.0.0`
direct validation using the new diagnostics. No automatic retry, second
validation, or official collection is recommended.

READY_FOR_HUMAN_AUTHORIZATION_INSTRUMENTED_DIRECT_VALIDATION
STOP_FOR_HUMAN_REVIEW
