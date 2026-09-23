# Comparative Baseline V4 Response Parse Error Forensic Diagnosis

## Scope and Controls

This is a strict offline forensic diagnosis of exactly two persisted attempts:

- `corrected-v4-runtime4-targeted-direct-validation-20260918T000000Z`
- `corrected-v4-runtime4-final-targeted-direct-validation-20260918T000000Z`

No provider or network call was made. No observation, RAW, DERIVED, manifest,
ledger, production code, test, or configuration artifact was modified. The only
file created by this diagnosis is this report.

Offline analysis-time counts:

| Quantity | Count |
|---|---:|
| Provider calls | 0 |
| Network calls | 0 |
| New observations | 0 |
| RAW artifacts modified | 0 |
| DERIVED artifacts modified | 0 |
| Manifests/ledgers modified | 0 |

The focused deterministic test command was run against the current checkout:

```text
python -m pytest -q ai-engine/tests/test_comparative_live_adapters.py ai-engine/tests/test_comparative_baseline_v4.py
80 passed
```

## Executive Finding

Both live attempts are distinct, complete one-assignment attempts with the same
provider/model, assignment, repository revision, execution identity, runtime
digest, typed schema, failure diagnostic, serialized request size, and RAW
payload. They both terminated before a response-bearing model turn and before
any tool operation.

The persisted outcome is correctly classified at the runtime level as
`PROVIDER_FAILURE` with provider category `RESPONSE_PARSE_ERROR`. It is not
evidence of a V4 typed-schema, repository-tool, Policy A, grounding, evaluator,
or persistence-wrapper failure. The precise failing SDK value cannot be proven
from the persisted evidence because the adapter raises before returning a
`ProviderResponse` and the SDK response object is not persisted.

The final report's claimed latency of `2613 ms` is not supported by its RAW
artifact. The first attempt records `assignmentLatencyMs: 2613`; the final
attempt records `assignmentLatencyMs: 1597`. Neither artifact persists a wall
clock timestamp. This is a report/evidence mismatch, not a duplicate identity.

## Attempt Identity and Artifact Integrity

| Field | Targeted | Final |
|---|---|---|
| Run/attempt ID | `corrected-v4-runtime4-targeted-direct-validation-20260918T000000Z` | `corrected-v4-runtime4-final-targeted-direct-validation-20260918T000000Z` |
| Assignment ID | `CASE-04:AGENT_DIRECT_OPEN:r1` | same |
| Observation ID | `corrected-v4-runtime4-targeted-direct-validation-20260918T000000Z:CASE-04:AGENT_DIRECT_OPEN:r1` | `corrected-v4-runtime4-final-targeted-direct-validation-20260918T000000Z:CASE-04:AGENT_DIRECT_OPEN:r1` |
| Artifact location | `artifacts/CASE-04:AGENT_DIRECT_OPEN:r1.json` | root `CASE-04:AGENT_DIRECT_OPEN:r1.json` |
| Artifact SHA | `0cf8af5d...fc867` | `8031b593...a834d` |
| RAW output SHA | `bbfb665b...eda43` | `bbfb665b...eda43` |
| DERIVED projection SHA | `85228ea3...d1ff` | `ab742678...f4dd` |
| Latency persisted in RAW resources | `2613 ms` | `1597 ms` |

The distinct observation IDs are valid because they include distinct run IDs.
The artifact SHA values recompute correctly from each immutable wrapper. The
RAW output SHA also recomputes correctly in both artifacts. The shared RAW hash
is expected: `rawOutput` contains the same question-only conversation, empty
responses, empty tool trace, and empty evidence; assignment resources such as
latency are outside `rawOutput`. The projection hashes and RAW links also
recompute correctly. There is no hash collision or evidence that one RAW file
was silently substituted for the other.

Ledger and manifest checks:

- Each ledger plans and finalizes exactly one assignment.
- Each ledger has the matching run ID, assignment ID, observation ID, status,
  and RAW output hash.
- The final ledger summary reports one `AGENT_DIRECT_OPEN` and one
  `PROVIDER_FAILURE`, with no duplicates, missing IDs, or unexpected IDs.
- Both manifests bind `openai` / `gpt-4.1-mini`, repository revision
  `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`, runtime 4.0.0, and typed schema
  2.0.0.
- The targeted manifest is the older compact manifest shape; the final manifest
  explicitly records preflight provider/network counts of `0/0`. Those are
  preflight counts, not the later live attempt count.

## Persisted Evidence and Counts

Both RAW observations contain:

```text
executionStatus: PROVIDER_FAILURE
primaryDiagnostic: PROVIDER_FAILURE
stoppingReason: PROVIDER_FAILURE
providerFailure.category: RESPONSE_PARSE_ERROR
providerFailure.exceptionType: JSONDecodeError
providerFailure.requestPhase: RESPONSE_PARSE
providerFailure.retryable: false
providerFailure.valueRepresentation: JSON_TEXT
providerFailure.safeMessage: Expecting value: line 1 column 1 (char 0)
responses: []
toolTrace: []
evidence: []
finalResponse: absent
```

Per attempt, the persisted resource counters are:

| Counter | Targeted | Final |
|---|---:|---:|
| Provider request attempted, as documented by the attempt report | 1 | 1 |
| Response-bearing model turns | 0 | 0 |
| `totalProviderCalls` resource counter | 0 | 0 |
| Network transports, as documented by the final report | 1 | 1 |
| Tool attempts / valid / invalid / skipped | 0 / 0 / 0 / 0 | 0 / 0 / 0 / 0 |
| Searches / reads / Git operations | 0 / 0 / 0 | 0 / 0 / 0 |
| Repository bytes | 0 | 0 |
| Serialized request bytes | 671 | 671 |
| Provider latency field | `NOT_MEASURED` | `NOT_MEASURED` |
| Assignment latency | 2613 ms | 1597 ms |
| Technical provider retries | 0 | 0 |

The distinction is important: the provider request/network count of one is the
live-attempt fact recorded in the reports and implied by the nonzero assignment
latency. The runtime resource counter remains zero because
`ResourceAccounting.record_provider()` is reached only after the adapter
returns a normalized response. A provider request that raises during adapter
response parsing is therefore not counted by that response-success counter.
This is an instrumentation semantic limitation, not evidence of zero live
transport activity. For this diagnosis, analysis-time provider/network counts
remain exactly zero.

## Reconstructed Pipeline

The current implementation reconstructs this path:

```text
V4 runtime builds question-only conversation
  -> ProviderRequest with typed tool schema 2.0.0
  -> OpenAI Responses API request
  -> SDK response object
  -> response.output item extraction
  -> function-call argument normalization, if a function_call item exists
  -> JSON parsing of string arguments, if needed
  -> V4 tool envelope/runtime validation
  -> ProviderResponse returned to runtime
  -> response-bearing resource accounting and conversation continuation
  -> tool execution or final answer parsing/evaluation
```

The failure occurred before `ProviderResponse` was returned. The exact source
locations in current `ai-engine/evaluations/comparative_baseline/live_adapters.py`
are:

- Lines 342-352: SDK response serialization and `response.output` iteration.
- Lines 345-349: function-call item detection and argument normalization.
- Lines 395-431, function `_normalize_function_arguments`: function-call
  argument extraction and typed normalization.
- Lines 415-422: `json.loads(value)` for a string function-call argument;
  `JSONDecodeError` becomes `TransportFailure` with
  `RESPONSE_PARSE_ERROR` and phase `RESPONSE_PARSE`.
- Lines 353-360: final `response.output_text` extraction and
  `json.loads(text)`; an invalid final JSON string becomes the same category.
- Lines 361-363: valid JSON that is not an object becomes
  `RESPONSE_CONTRACT_ERROR`, not `RESPONSE_PARSE_ERROR`.

The exact persisted message, `Expecting value: line 1 column 1 (char 0)`,
shows that the failing JSON text was empty or whitespace at the parser entry
point. It does not identify whether that text was a function-call `arguments`
value or final `output_text`. The persisted diagnostic lacks `sdkItemType`, and
the adapter does not persist the SDK response after the exception. Therefore
the strongest exact cause chain is:

```text
provider request completed far enough to yield an SDK-side parse input
  -> adapter selected a JSON_TEXT value
  -> json.loads(value) raised JSONDecodeError at offset 0
  -> adapter raised non-retryable TransportFailure(RESPONSE_PARSE_ERROR)
  -> V4 runtime stopped with PROVIDER_FAILURE
  -> no ProviderResponse, tool call, final answer, or provider response envelope persisted
```

It is not possible to choose the function-call branch over the final-output
branch from these RAW files alone.

## Typed Normalization Matrix

Current `_normalize_function_arguments` behavior is:

| SDK `arguments` value | Current behavior | Classification |
|---|---|---|
| Native/dict object | Accepted directly | Continue to tool envelope/runtime validation |
| JSON string containing object | `json.loads`, object accepted | Continue |
| JSON string containing array/scalar/null | Parses, then rejected because object required | `RESPONSE_CONTRACT_ERROR` |
| Empty or whitespace string | `json.loads` fails at offset 0 | `RESPONSE_PARSE_ERROR` |
| Malformed JSON string | `json.loads` fails | `RESPONSE_PARSE_ERROR` |
| `None`/missing | Normalized to `{}` | Runtime tool-contract validation, not parse error |
| Native list/scalar/bytes/other | Not a supported representation | `RESPONSE_CONTRACT_ERROR` |

SDK item extraction first uses `model_dump(mode="json", by_alias=True)` when
available, then fills missing `type`, `arguments`, `name`, and `call_id` from
native attributes. Without `model_dump`, it reads those native attributes
directly. A typed tool `name` matching an allowed operation is wrapped into
`{"operation": name, "arguments": parsed}`; otherwise the parsed object is
passed through for V4 contract validation.

Final structured output has a separate matrix:

| `output_text` value | Current behavior |
|---|---|
| String containing JSON object | `FINAL` ProviderResponse |
| Empty/whitespace or malformed string | `RESPONSE_PARSE_ERROR` |
| String containing valid array/scalar/null | `RESPONSE_CONTRACT_ERROR` |
| `None` or non-string | `RESPONSE_CONTRACT_ERROR` |

The current typed schema is `comparative-v4-typed-repository-tools-2.0.0`.
Its operation-specific fields and static constraints are built in
`comparative_baseline_v4/protocol.py` and passed by the V4 runtime. Schema 1.0
was broader; the documented remediation changed it to 2.0 together with runtime
4.0.0 to represent path/query/positive-limit constraints and full lowercase
40-character Git object IDs provider-side. The schema/runtime remediation does
not control the JSON parsing exception observed here.

## Historical Comparison and Boundaries

Historical evidence has three relevant classes:

1. Older V4 2.0/3.0 pilots had provider parse failures where persisted
   function-call arguments appeared valid. The SDK in-memory representation was
   absent, so those failures were classified as unresolved or unreproducible,
   not proven schema defects.
2. The accepted six-slot pilot's parse failure occurred after tool interaction
   and had six provider responses plus seven trace entries. Its persisted
   arguments parsed as valid JSON, but the exact failing SDK value was not
   persisted. That is materially different from these two attempts, which have
   zero persisted responses and zero tool traces.
3. Current offline fixtures prove the corrected adapter accepts native dicts,
   multiple calls, typed tool names, and reports malformed JSON at the parse
   boundary without secrets. Those fixtures do not reproduce either live SDK
   object because no SDK response object was persisted.

Last-known-good boundary for each target attempt: preflight, request
construction, static schema identity, and request serialization. The persisted
request is 671 bytes and no static violation is recorded. First failure
boundary for each: SDK response-to-adapter JSON extraction/normalization,
before the first response can be returned to the V4 runtime. There is no
evidence that typed runtime validation, repository tools, grounding, semantic
evaluation, or persistence hashing was reached as a failure boundary.

## Persisted Field Inventory

| Field/group | Status | Forensic conclusion |
|---|---|---|
| Attempt/run ID | PERSISTED | Distinguishes the two attempts |
| Assignment identity | PERSISTED | Same CASE-04 slot in both |
| Observation ID | PERSISTED | Distinct and ledger-linked |
| Artifact wrapper SHA | PERSISTED | Distinct; recomputes |
| RAW output SHA | PERSISTED | Same and recomputes for both raw payloads |
| DERIVED projection SHA/link | PERSISTED | Distinct projections; links recompute |
| Provider/model | PERSISTED | `openai` / `gpt-4.1-mini` |
| Runtime/schema/experiment identities | PERSISTED | Same frozen identities |
| Repository revision | PERSISTED | Same revision |
| Failure category/phase/type/message | PERSISTED | Exact bounded diagnostic |
| SDK item type | NOT_PERSISTED | Cannot distinguish function-call vs final-output parse branch |
| Failing JSON text | NOT_PERSISTED | Only `JSON_TEXT` and bounded error are kept |
| SDK response envelope | NOT_PERSISTED | `responses` remains empty after adapter exception |
| Provider response ID/status/headers | NOT_PERSISTED | Not available in RAW |
| Provider network timing | NOT_PERSISTED | `providerLatencyMs` is `NOT_MEASURED` |
| Wall-clock timestamp | NOT_PERSISTED | Run ID date is an identifier, not an event timestamp |
| Assignment latency | PERSISTED | 2613 ms first; 1597 ms final |
| Provider request attempted | DERIVABLE | One per attempt from reports/termination; resource success counter is zero |
| Network transport attempted | DERIVABLE | One per attempt from final report/live execution record; not in RAW |
| Provider retry count | PERSISTED | Zero technical retries; manifest policy is `NO_RETRY` |
| Tool/repository activity | PERSISTED | Exactly zero |
| Final answer/evaluation | PERSISTED | Explicitly not evaluated/absent |
| Exact upstream HTTP outcome | NOT_PERSISTED | No status, provider code, or response envelope |

## Classification

Primary taxonomy: `PROVIDER_FAILURE / RESPONSE_PARSE_ERROR`.

For the two target attempts, this is the correct operational classification:
the adapter raised while parsing a JSON text representation, before a usable
normalized response existed. It is not a legitimate upstream model/tool
outcome, because no tool request or final answer was available for the model to
be evaluated on.

Root-cause classification: `INSUFFICIENT_EVIDENCE`, with an
`UNRESOLVED_RUNTIME_OR_ADAPTER_BOUNDARY` secondary risk. The evidence supports
a real parse-boundary failure, but does not establish whether the upstream
response contained an empty final text, an empty/malformed function-call
argument string, an SDK representation mismatch, or a serialization omission.
The current adapter's native-dict handling defect is covered by offline tests,
but it cannot be asserted as the cause of these two failures.

Confidence:

- High that both runs failed before tool interaction.
- High that the adapter raised a JSON parse exception at offset zero.
- High that the V4 typed schema and repository runtime were not exercised.
- High that artifact/projection/link hashes are internally valid.
- High that the final report's `2613 ms` claim is wrong for its persisted RAW.
- Low for the exact SDK branch/value that caused the parse exception.

Remediation category: `INSTRUMENTATION_REMEDIATION_REQUIRED`, not a production
fix authorized by this diagnosis. The minimum future diagnostic record should
distinguish the parser branch (`function_call` versus final `output_text`), SDK
item type, safe value representation/length, response status/ID where safe, and
the exception boundary, without persisting secrets or raw sensitive content.
No fix is implemented here.

## Retry and Experimental Implications

The persisted diagnostic is marked non-retryable and both manifests specify no
provider retries. The two attempts nonetheless constitute repeated
pre-interaction failures under the same frozen assignment and identities. They
should not be pooled as independent tool-contract observations, and they do not
justify an official collection or a third automatic validation.

A retry, if considered later, requires explicit human authorization and a new
attempt identity. It must not overwrite either attempt and should persist a
minimal safe parse-boundary record sufficient to identify branch, SDK item type,
value representation, provider request/response IDs where safe, and phase.
Repeating without that evidence would only establish recurrence, not cause.

The identical question-only RAW payload is expected for two failures before any
response is accepted. The differing run IDs, wrapper hashes, projection hashes,
and assignment latency show that the attempts are distinct. The shared
`rawOutputSha256` is not suspicious duplication by itself; it hashes only the
identical `rawOutput`, not resources or run identity.

## Mandatory A-O Answers

| Answer | Finding |
|---|---|
| A | Both target directories exist and each contains exactly one finalized CASE-04 assignment. `YES`. |
| B | Observation IDs, run IDs, artifact wrapper hashes, and projection hashes are distinct and valid. `YES`. |
| C | The shared RAW output hash and identical failure diagnostic show the same observable failure shape, but the missing SDK response prevents proving the same exact internal branch. `INCONCLUSIVE`. |
| D | The persisted first latency is 2613 ms; the persisted final latency is 1597 ms. The final report's 2613 ms is unsupported. `NO` for “both persisted at 2613 ms.” |
| E | Provider/model, revision, runtime digest, schema 2.0, execution hash, experiment hash, and failure diagnostic match. `YES`. |
| F | Distinct run/observation executions are proven, but the RAW does not persist network/request IDs. Two network transports are supported by the live execution records but not independently proven by RAW. `INCONCLUSIVE` at transport level; analysis-time `0/0`. |
| G | Both failures occurred before any response-bearing turn, tool call, repository read/search/Git operation, grounding, or evaluation. `YES`. |
| H | The exact failing SDK branch and value cannot be reconstructed from persisted evidence. `NO`, exact cause not identifiable. |
| I | Current normalization accepts dict/native object, valid JSON object strings, and null-to-empty-object; it rejects malformed/empty text as parse errors and scalar/list results as contract errors. `YES`. |
| J | Schema 2.0 is the current provider-visible typed schema; schema 1.0 was broader and was replaced with runtime 4.0 identity. Neither schema explains an offset-zero JSON parse exception. `YES`. |
| K | First failure boundary is adapter JSON extraction/normalization after the provider request and before `ProviderResponse`; typed runtime/tool validation was not reached. `YES`. |
| L | Operational classification `PROVIDER_FAILURE/RESPONSE_PARSE_ERROR` is correct; this is not a legitimate upstream tool/model outcome. `YES`. |
| M | Root cause is not proven as a specific adapter defect; primary classification is `INSUFFICIENT_EVIDENCE` with unresolved provider/SDK/adapter boundary risk. `YES`. |
| N | The two attempts are repeated pre-interaction provider failures and are inconclusive for runtime/tool-contract validation. No official collection should proceed. `YES`. |
| O | Readiness is `STOP_FOR_HUMAN_REVIEW`; no fix, retry, third validation, or official collection is authorized by this report. `YES`. |

## Readiness

```text
ROOT_CAUSE_INCONCLUSIVE_INSTRUMENTATION_REQUIRED
STOP_FOR_HUMAN_REVIEW
```

No implementation-level remediation was performed. Human review is required to
decide whether to authorize diagnostic instrumentation, a separately identified
qualification attempt, or no further live work.
