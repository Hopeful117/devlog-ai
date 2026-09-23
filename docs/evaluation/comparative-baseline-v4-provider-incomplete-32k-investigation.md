# Comparative Baseline V4 Provider Incomplete 32K Investigation

## 1. Executive Summary

The investigation did not modify the failed benchmark validation, execute
another benchmark observation, retry CASE-04, or run official collection.

The exact V4 request path proves that the benchmark request passed:

```text
model = gpt-4.1-mini
max_output_tokens = 32768
six typed repository tools
strict JSON Schema response format
```

The persisted benchmark response proves:

```text
status = incomplete
incomplete_details.reason = max_output_tokens
```

It does not prove that 32768 output tokens were generated. The adapter
persisted only response-state diagnostics and discarded the SDK response's
full raw content before runtime handling. The benchmark artifact therefore
cannot answer whether usage or output items were present on the provider
response.

Three isolated, non-benchmark provider micro-calls were authorized and run:

1. One trivial function tool without strict V4 structured output completed with a function call and `output_tokens=16`.
2. Six V4 typed tools plus the strict V4 JSON Schema reproduced `incomplete / max_output_tokens`, with `output=[]` and usage fields equal to zero.
3. The same strict V4 JSON Schema without tools completed with a message and `output_tokens=62`.

This isolates a reproducible provider behavior associated with the combination
of the six-tool request shape and strict structured output. It does not prove
whether the provider's internal cause is tool/schema compilation, response
planning, or another undocumented provider-side interaction.

Confirmed secondary findings:

- `ADAPTER_DIAGNOSTIC_GAP_CONFIRMED`: incomplete responses are not persisted as raw response data.
- `ACCOUNTING_GAP_CONFIRMED`: failed/incomplete transport attempts do not increment `totalProviderCalls` or carry usage into `ResourceAccounting`.

Primary classification:

```text
PROVIDER_INCOMPLETE_OBSERVABILITY_GAP_IDENTIFIED
```

Readiness:

```text
READY_FOR_HUMAN_AUTHORIZATION_PROVIDER_REMEDIATION
```

## 2. Frozen Context

The immutable benchmark validation remains:

```text
attempt = v4-final-quality-first-live-validation-20260918T120821Z
condition = AGENT_DIRECT_OPEN
question = CASE-04@1.0.0
repetition = r1
repositoryRevision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
```

Active identities remain:

```text
execution = f72225f4bb0d7e062dab8eb4b1120f600734fac2e51a9573e910d306c29e6ea8
experiment = 073b51104c49430f949407710aa98719e45af375c78a9ec92c69c078eadc1a62
runtime = comparative-v4-live-runtime-contract-5.0.0
runtimeDigest = a3211f44aad4633ca1d725b7a48506a6dd8725ab96becf182e867f1794e80edd
instrumentation = comparative-v4-instrumentation-3.0.0
toolSchema = comparative-v4-typed-repository-tools-2.0.0
```

No benchmark RAW, DERIVED, ledger, report, or historical identity was changed.

## 3. Official Documentation

All sources were accessed on `2026-09-18` and are official OpenAI sources.

### S1: GPT-4.1 Mini model page

- Source: <https://developers.openai.com/api/docs/models/gpt-4.1-mini>
- Behavior: model ID `gpt-4.1-mini`; context window `1,047,576`; maximum output `32,768`; no reasoning step; Responses API and function calling supported.
- Implication: `32768` remains the documented maximum for the exact requested model alias.

### S2: Responses `create` reference

- Source: <https://developers.openai.com/api/reference/python/resources/responses/methods/create>
- Behavior: response objects expose `status`, `incomplete_details`, `output`, `usage`, `max_output_tokens`, tools, and text format; `max_output_tokens` is an upper bound for generated response tokens, including visible and reasoning tokens.
- Implication: the public response contract can report incomplete state and expose output/usage fields, but does not expose the provider's internal allocation explanation.

### S3: Responses streaming events

- Source: <https://developers.openai.com/api/reference/resources/responses/streaming-events>
- Behavior: incomplete responses expose `incomplete_details.reason`; `max_output_tokens` is a documented incomplete reason; examples may contain `output=[]` and `usage=null`.
- Implication: an incomplete response with no output items is provider-contract-compatible, and absent usage is possible.

### S4: Token counting guide

- Source: <https://developers.openai.com/api/docs/guides/token-counting>
- Behavior: output usage includes model-generated non-visible structure; output limits apply to generated tokens beyond visible text, including tool-call-related structure.
- Implication: a tool/structured-output request can consume output capacity in provider-generated structure, but this does not prove that the benchmark response consumed all `32768` tokens.

### S5: Function calling guide

- Source: <https://developers.openai.com/api/docs/guides/function-calling>
- Behavior: function calls are model-generated response items; tool calling is a multi-step flow in which the application receives a tool call, executes it, and sends tool output back.
- Implication: no tool call can be executed when the provider returns an incomplete response with no usable output item.

### S6: Reasoning guide

- Source: <https://developers.openai.com/api/docs/guides/reasoning>
- Behavior: reasoning-model responses can become incomplete at `max_output_tokens`, potentially before visible output; the frozen GPT-4.1 Mini model page says the frozen model has no reasoning step.
- Implication: do not label this event as hidden reasoning exhaustion. The exact provider-side allocation remains undocumented.

## 4. Installed SDK

The installed environment reports:

```text
openai = 2.54.0
module = /home/ludo/.local/lib/python3.14/site-packages/openai/__init__.py
```

The installed SDK types expose:

| Type | Fields relevant to investigation |
|---|---|
| `Response` | `status`, `incomplete_details`, `output`, `usage`, `max_output_tokens`, `tools`, `text`, `model`, IDs |
| `ResponseUsage` | `input_tokens`, `output_tokens`, `total_tokens`, token detail objects |
| `ResponseFunctionToolCall` | `arguments`, `call_id`, `name`, `status`, `type` |
| `IncompleteDetails` | `reason` |
| `ResponseIncompleteEvent` | `response`, `sequence_number`, `type` |

No SDK upgrade or source modification was made.

## 5. Effective Request Audit

The effective path is:

```text
v4-manifest.json
  -> v4_execution_configuration()
  -> V4CollectionRuntime._run_direct()
  -> _request(..., output_tokens)
  -> OpenAIProviderTransport.complete()
  -> responses.create(**kwargs)
```

Relevant source facts:

- `runtime.py` selects `finalOutputTokens` on turn 1 and `intermediateOutputTokens` on later turns.
- Both values were `32768`.
- `live_adapters.py` places `request.max_output_tokens` directly into the SDK kwargs.
- The adapter does not set `temperature`, `tool_choice`, `parallel_tool_calls`, or metadata.
- The SDK/provider defaults therefore apply to omitted fields.

Sanitized reconstruction of the failed first request:

| Request field | Effective value |
|---|---|
| `model` | `gpt-4.1-mini` |
| `instructions` | fixed DIRECT instruction, 102 UTF-8 bytes |
| `input` | one serialized user message containing CASE-04 question |
| `tools` | six typed function tools |
| `text.format.type` | `json_schema` |
| `text.format.name` | `story0134_common_answer` |
| `text.format.strict` | `true` |
| `max_output_tokens` | `32768` |
| `temperature` | omitted; SDK/provider default |
| `tool_choice` | omitted; SDK/provider default |
| `parallel_tool_calls` | omitted; SDK/provider default |
| `metadata` | omitted |

Offline request-shape measurements:

```text
question characters = 87
instructions bytes = 102
serialized V4 payload bytes = 671
serialized input items bytes = 117
six-tool schema bytes = 2183
strict text schema bytes = 2242
sanitized canonical request representation = 4738 bytes
```

These byte counts are canonical JSON measurements, not provider tokenizer
counts. No reliable local GPT-4.1 Mini request tokenizer was used.

The six tool names are exactly:

```text
read_file
search_repository
git_log
git_show
git_diff
inspect_commit
```

The offline schema/runtime tests pass and the six tools are not malformed by
the local contract.

## 6. Persisted Benchmark Response Audit

Persisted provider identifiers:

```text
response = resp_0406ce90f6f59d33006aad29b7aca087d2972f37dcc4843f98
request = req_702daeec475e4a3a8ee55b7654713d01
```

Persisted diagnostic fields:

```text
providerResponseStatus = incomplete
providerIncompleteReason = max_output_tokens
requestPhase = RESPONSE_STATE
parseBoundary = NOT_REACHED
sdkResponseType = Response
runtimeType = NOT_REACHED
valueRepresentation = NOT_REACHED
```

The persisted RAW observation contains:

```text
responses = []
conversation = one user question
toolTrace = []
evidence = []
```

The absence of output items and usage in the persisted benchmark artifact is
not evidence that the provider returned none. The adapter raises immediately
after status inspection and persists only the sanitized diagnostic attribution.

## 7. Adapter State Machine

Current behavior:

```text
responses.create()
  -> model_dump(raw)
  -> assert_secret_free(raw)
  -> status == incomplete
  -> _incomplete_response_diagnostics(response, raw)
  -> TransportFailure
  -> runtime PROVIDER_FAILURE
```

On this branch:

- `_usage(response)` is not called.
- `response.output` is not inspected.
- `raw` is not attached to the exception attribution.
- `ProviderResponse` is never created.
- `ResourceAccounting.record_provider()` is never called.
- Runtime `responses` remains empty.

Runtime 5 is correct on the safety boundary: it does not parse an incomplete
response, execute partial tools, retry, repair, or continue. The diagnostic
surface is incomplete, however.

## 8. Usage and Accounting Findings

The benchmark artifact reports:

```text
runtime totalProviderCalls = 0
modelTurns = 0
inputTokens = NOT_MEASURED
outputTokens = NOT_MEASURED
```

The persisted provider request ID proves that one transport request occurred.
This is an accounting gap, not evidence that zero provider calls occurred.

`ResourceAccounting.record_provider()` increments `total_provider_calls`,
model turn counts, latency, and token usage only after a usable
`ProviderResponse` is returned. A `TransportFailure` from the incomplete branch
skips all of those fields.

The minimal future correction should distinguish, in separate fields or an
explicit accounting event:

```text
transport request attempted
usable provider response received
response-bearing model turn
```

It should record usage only when the provider exposes it, and preserve unknown
values as unknown. This investigation does not implement that correction.

## 9. Micro-Reproducer

The micro-reproducer was necessary because persisted benchmark diagnostics could
not distinguish provider omission from adapter loss. It was isolated from all
V4 storage, benchmark ledgers, repository tools, scoring, and observations.

One client-construction error occurred before a provider call because the SDK
default lookup expected `OPENAI_API_KEY`; the V4 adapter explicitly passes
`LLM_API_KEY`. That local setup error was corrected without changing the
benchmark environment or code.

Provider micro-calls: `3` total, within the authorized maximum of `3`.

### Call 1: one trivial function tool

```text
requested model = gpt-4.1-mini
returned model = gpt-4.1-mini-2025-04-14
max_output_tokens = 32768
status = completed
output item = function_call / completed
output items = 1
input_tokens = 64
output_tokens = 16
reasoning_tokens = 0
total_tokens = 80
```

This proves the model can complete a basic tool-call request at the selected
ceiling.

### Call 2: six V4 tools plus strict V4 JSON Schema

```text
requested model = gpt-4.1-mini
returned model = gpt-4.1-mini-2025-04-14
max_output_tokens = 32768
tool count = 6
status = incomplete
incomplete reason = max_output_tokens
output items = 0
input_tokens = 0
output_tokens = 0
total_tokens = 0
```

This reproduces the provider state outside the benchmark runtime, without
repository interaction.

### Call 3: strict V4 JSON Schema without tools

```text
requested model = gpt-4.1-mini
returned model = gpt-4.1-mini-2025-04-14
max_output_tokens = 32768
tool count = 0
status = completed
output item = message / completed
output items = 1
input_tokens = 454
output_tokens = 62
reasoning_tokens = 0
total_tokens = 516
```

The bounded controls support this conclusion:

```text
one trivial tool without strict V4 schema -> completed
strict V4 schema without tools -> completed
six V4 tools plus strict V4 schema -> incomplete/max_output_tokens
```

This demonstrates a provider-side behavior associated with the combined
six-tool plus strict structured-output request shape. It does not identify the
undocumented internal provider mechanism and does not prove that the original
benchmark consumed 32768 tokens.

No micro-call created a benchmark observation, RAW/DERIVED artifact, ledger
entry, score, or repository tool execution.

## 10. Root-Cause Table

| Candidate cause | Evidence for | Evidence against | Status | Remediation class |
|---|---|---|---|---|
| Provider generated full output allowance | Provider returned documented `max_output_tokens` incomplete reason | Benchmark usage absent; micro call 2 reports output/total tokens `0` | `UNSUPPORTED` as a claim of actual 32768 usage | `NO_CHANGE_REQUIRED` |
| Usage hidden/lost by adapter | Incomplete branch never calls `_usage()` and does not persist raw response | Exact benchmark provider usage cannot be recovered after the fact | `CONFIRMED` adapter loss | `OBSERVABILITY_ONLY` |
| Output items discarded | Incomplete branch does not inspect or persist `response.output` | Benchmark output-item presence is unknown; micro call 2 had none | `CONFIRMED` diagnostic gap; benchmark content `UNKNOWN` | `OBSERVABILITY_ONLY` |
| Tool-call generation issue | Six tools + strict schema reproduces; one-tool control completes | No provider-internal cause or partial call is exposed | `SUPPORTED` combined-request behavior | `NO_CHANGE_REQUIRED` pending human decision |
| Request construction defect | None; `max_output_tokens=32768`, model, tools, schema, and fields match code | Offline schema/request checks pass | `REFUTED` | `NO_CHANGE_REQUIRED` |
| Tool schema issue | Combined six-tool control reproduces | Six tools are valid, deterministic, and schema/runtime tests pass | `POSSIBLE` provider interaction, not malformed schema | `EXPERIMENTAL_DESIGN_DECISION` only if later authorized |
| SDK limitation/behavior | SDK 2.54.0 exposes Response output/usage fields; it parsed micro responses | No evidence SDK caused provider incomplete status | `UNKNOWN` as root cause; no SDK defect demonstrated | `NO_CHANGE_REQUIRED` |
| Runtime response handling defect | Runtime does not parse incomplete and does not retry, as required | Diagnostic/usage loss occurs in adapter before runtime | `REFUTED` for safety handling | `NO_CHANGE_REQUIRED` |
| Accounting/observability gap | One request ID exists while `totalProviderCalls=0`; usage is not measured | Does not explain provider termination itself | `CONFIRMED` secondary finding | `ACCOUNTING_ONLY` |

## 11. Remediation Options

### Option A: Bounded adapter observability fix

On the incomplete branch, preserve a secret-free response-state snapshot with:

```text
status
incomplete_details
output item types/statuses and safe IDs
usage when present
model
max_output_tokens
provider request/response IDs
```

Do not parse it as a final answer, execute partial tool calls, or retry.

Classification: `OBSERVABILITY_ONLY` plus a possible adapter change.

### Option B: Bounded accounting fix

Record a transport attempt before invoking the provider and separately record a
usable response only after successful normalization. Preserve unknown token
usage when unavailable.

Classification: `ACCOUNTING_ONLY`.

### Option C: Provider/request-shape design decision

The isolated controls show a reproducible issue for six V4 tools combined with
the strict structured-output schema. No automatic tool/schema simplification is
authorized. A human may later decide whether that provider behavior is suitable
for the experiment, but this investigation does not change the frozen
configuration or run another benchmark validation.

Classification: `EXPERIMENTAL_DESIGN_DECISION`.

## 12. Identity Impact

No identities changed during this investigation.

Potential future impact must be decided before implementation:

| Remediation | Runtime | Instrumentation | Execution | Experiment | Validation | Official |
|---|---|---|---|---|---|---|
| Adapter diagnostic fields | likely unchanged unless runtime contract changes | likely new if persisted schema/version changes | unchanged | unchanged unless identity binds instrumentation | unchanged | unchanged |
| Transport-attempt accounting | new runtime/resource behavior decision | likely new accounting/instrumentation version | unchanged | likely new if resource contract or instrumentation identity changes | new transitively | new transitively |
| Tool/schema/request change | potentially new runtime/tool identity | potentially new | new | new | new | new |

These are impact analyses only. No identity values were computed or changed.

## 13. Experimental Implications

- The `32768` ceiling was actually passed to the SDK in the benchmark request.
- The single benchmark outcome cannot establish actual generated token count.
- The provider-side incomplete behavior is reproducible outside the benchmark for the combined six-tool/strict-schema request shape.
- This does not authorize another benchmark observation or official collection.
- Runtime 5 must continue to treat incomplete responses as non-answers.
- The missing diagnostic and accounting data should be remediated before relying on incomplete-response resource analysis.
- No claim is made that `gpt-4.1-mini` internally reasoned, consumed 32768 tokens, or generated a partial tool call.

## 14. Mandatory Answers A-Z

- A: `NO`.
- B: `NO` benchmark retry/second observation.
- C: `NO` official collection.
- D: `2.54.0`.
- E: `gpt-4.1-mini` was sent; isolated responses resolved to `gpt-4.1-mini-2025-04-14`.
- F: `32768`, proven by manifest, runtime selection, adapter kwargs, and micro-call response echo.
- G: `YES`; official model documentation lists `32,768` maximum output tokens.
- H: `NOT_PERSISTED` in the benchmark artifact; micro-call 2 exposed a usage object with zero token values.
- I: `NO` on the incomplete adapter branch; `_usage()` is bypassed and no raw usage is persisted.
- J: `NOT_PERSISTED`; benchmark artifact does not distinguish provider omission from adapter loss.
- K: `NOT_PERSISTED` for the benchmark; micro-call 2 had no complete tool call.
- L: `NOT_PERSISTED` for the benchmark; no partial structure was available in persisted diagnostics.
- M: `UNKNOWN`; no evidence proves usable output existed, but the adapter discarded the raw response before persistence.
- N: `YES`.
- O: `NO`.
- P: `NO`.
- Q: `YES`; failed/incomplete transport attempts do not increment `totalProviderCalls`.
- R: `DEPENDS_ON_PROVIDER_METADATA`; if usage exists, the current branch loses it; if absent, it remains unknown.
- S: `NO`; offline schema/runtime parity and six-tool structure are valid.
- T: `NO`; effective request fields are consistent with the frozen adapter contract.
- U: `YES`.
- V: `3` provider micro-calls; one client-construction error made no provider call.
- W: `YES` for the combined six-tool/strict-schema request shape.
- X: `UNKNOWN`; no evidence proves actual 32768 generated output tokens.
- Y: `NO` runtime safety defect; `YES` adapter diagnostic/accounting gaps exist but do not explain provider allocation.
- Z: `YES`; next remediation can be chosen without another benchmark observation.

## 15. Additional Answers AA-AL

- AA: `YES`.
- AB: A failed/incomplete transport currently does not increment `totalProviderCalls`; the field remains `0` despite one provider request ID.
- AC: `YES`, before official collection, subject to a separate human-authorized implementation Story.
- AD: `YES`, when the provider supplies usage; otherwise preserve `NOT_MEASURED`.
- AE: `YES` diagnostically when safely available; never execute partial items.
- AF: `NO`.
- AG: `NO`.
- AH: `NO`.
- AI: `NO`.
- AJ: `NO`.
- AK: `NO`.
- AL: `NO`.

## 16. Files and Artifacts

Inspected:

- `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json`
- `ai-engine/evaluations/comparative_baseline_v4/runtime.py`
- `ai-engine/evaluations/comparative_baseline_v4/protocol.py`
- `ai-engine/evaluations/comparative_baseline/live_adapters.py`
- `ai-engine/pyproject.toml`
- installed OpenAI SDK `2.54.0` response types
- immutable validation RAW/DERIVED/ledger under `ai-engine/data/comparative-baseline-v4/live-pilot/v4-final-quality-first-live-validation-20260918T120821Z`

Modified:

- `docs/evaluation/comparative-baseline-v4-provider-incomplete-32k-investigation.md` (this report only)

The failed validation artifact, historical artifacts, runtime, adapter,
instrumentation, tests, identities, benchmark, and official collection were
not modified.

## 17. Final Decision

Decision tree branch:

```text
BRANCH B — OBSERVABILITY/ACCOUNTING GAP PREVENTS COMPLETE DIAGNOSIS
```

Exact next human decision:

> Authorize a bounded adapter observability and transport-accounting remediation Story. It must persist safe incomplete-response output/usage metadata when exposed, distinguish transport attempts from usable responses, preserve runtime-5 no-parse/no-retry behavior, and recompute identities only if the existing identity semantics require it. Do not authorize another benchmark observation or official collection in that remediation task.

Primary classification:

```text
PROVIDER_INCOMPLETE_OBSERVABILITY_GAP_IDENTIFIED
```

Secondary findings:

```text
ADAPTER_DIAGNOSTIC_GAP_CONFIRMED
ACCOUNTING_GAP_CONFIRMED
PROVIDER_COMBINED_TOOL_SCHEMA_BEHAVIOR_SUPPORTED
```

Readiness:

```text
READY_FOR_HUMAN_AUTHORIZATION_PROVIDER_REMEDIATION
```

PROVIDER_INCOMPLETE_OBSERVABILITY_GAP_IDENTIFIED
READY_FOR_HUMAN_AUTHORIZATION_PROVIDER_REMEDIATION
STOP_FOR_HUMAN_REVIEW
