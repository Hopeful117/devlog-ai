# V4 Provider Observability & Transport Accounting Remediation

## 1. Objective

Implemented the bounded observability and accounting remediation authorized by
the Story. No benchmark observation, live provider request, pilot, official
collection, retry, treatment change, prompt change, model change, token change,
tool change, or structured-output change was made.

The unresolved provider behavior remains unresolved:

```text
six V4 typed tools
+ strict V4 structured output
+ gpt-4.1-mini
```

This Story only makes the provider boundary observable and economically
accountable.

## 2. Repository State

```text
branch = main
HEAD = f0dfffa13a77ff753254912dd7a270d03160ee52
```

The worktree already contained unrelated V4 implementation, reports, notebook,
and live-adapter changes. They were not reverted. This remediation touched only
the provider request contract, V4 accounting/runtime/adapter paths, their tests,
identity/version references, and this report.

## 3. Confirmed Prior Gaps

Before this Story:

- `totalProviderCalls` was incremented only after normalization into a usable `ProviderResponse`.
- Incomplete provider responses raised before usage or output-item diagnostics were persisted.
- Incomplete attempts could therefore appear as zero provider calls.
- Runtime 5 correctly stopped on incomplete responses and did not parse, execute, retry, repair, or continue them.

## 4. Architecture Before / After

Before:

```text
provider.complete()
  -> normalized ProviderResponse
  -> record_provider()
  -> totalProviderCalls/modelTurns/tokens

incomplete/transport failure
  -> exception
  -> no provider resource record
```

After:

```text
provider boundary
  -> ProviderAttempt.transportAttempted
  -> provider result, if any
  -> ProviderAttempt.providerResponseReceived
  -> bounded provider diagnostics/usage
  -> usable normalization, if accepted
  -> usableProviderResponses/responseBearingModelTurns
```

The runtime still stops immediately for incomplete responses. The new callbacks
are provider-agnostic and the OpenAI SDK inspection remains in
`live_adapters.py`.

## 5. Transport Attempt Model

`ProviderAttempt` in
`ai-engine/evaluations/comparative_baseline_v4/protocol.py` is the typed lifecycle
record. The exact persisted metric is:

```text
resources.providerTransportAttempts
```

The runtime records the attempt after payload validation and at the provider
transport boundary. The OpenAI adapter also invokes the callback immediately
before `responses.create(...)`; recording is idempotent. For generic offline
transports, the boundary callback represents invocation of the provider
transport abstraction. A client-side failure before the SDK invocation is
reported with `providerResponseReceived=false`; the adapter reports the exact
pre-SDK state when it can distinguish it.

## 6. Provider Response Model

The exact metric is:

```text
resources.providerResponsesReceived
```

It is incremented only when a provider response object is exposed to the
adapter/runtime boundary. A completed, incomplete, or parse-invalid provider
response can be received. A timeout/connection failure without a response
object cannot.

## 7. Usable Provider Response Model

The exact metric is:

```text
resources.usableProviderResponses
```

Only a normalized accepted `ProviderResponse` increments it. An incomplete
response never becomes usable and never enters the final-output parser.

## 8. Response-Bearing Model Turn

The exact new metric is:

```text
resources.responseBearingModelTurns
```

It increments only with a usable normalized provider response. The existing
`modelTurns` field remains the historical successful-normalization count. This
preserves its prior meaning while making the explicit model-turn metric
available.

## 9. Historical Accounting Semantics

The existing fields retain their pre-remediation semantics:

- `totalProviderCalls`: successful normalized provider responses, historically counted as provider calls; it is not redefined retroactively.
- `modelTurns`: successful normalized response turns.
- `navigationProviderCalls`: successful normalized non-final/tool responses.
- `finalAnswerProviderCalls`: successful normalized final responses.
- `inputTokens`, `outputTokens`: sums of authoritative provider usage values received, including incomplete responses when exposed.
- `totalTokens`: sum of authoritative provider `total_tokens` values.
- `providerLatencyMs`: latency of usable normalized provider responses, preserving the prior field's meaning.
- `cost`: remains `NOT_AVAILABLE`; no cost is inferred without an existing authoritative pricing rule.

New fields:

- `providerTransportAttempts`: actual provider transport-boundary attempts.
- `providerResponsesReceived`: provider response objects received.
- `usableProviderResponses`: normalized accepted responses.
- `responseBearingModelTurns`: usable response-bearing turns.
- `providerTransportLatencyMs`: wall-clock latency for every attempted provider transport, including failures.

Pilot/official top-level `providerCalls` summaries now use
`providerTransportAttempts`, so actual attempts cannot disappear from the
collection summary.

## 10. Usage Semantics

The adapter now extracts `input_tokens`, `output_tokens`, and `total_tokens`
from the SDK usage object. Provider-supplied integer zero is preserved as zero.
Missing, null, or non-integer usage is normalized to:

```text
NOT_MEASURED
```

`max_output_tokens` is never used as usage and never used as cost.

## 11. Incomplete Diagnostic Projection

For `status == incomplete`, the adapter persists a bounded diagnostic projection
through provider failure attribution and the RAW `providerAttempts` record:

```json
{
  "turn": 1,
  "transportAttempted": true,
  "providerResponseReceived": true,
  "usableProviderResponse": false,
  "responseBearingModelTurn": false,
  "usage": {
    "inputTokens": 123,
    "outputTokens": 456,
    "totalTokens": 579
  },
  "providerResponseDiagnostics": {
    "providerResponseId": "...",
    "providerRequestId": "...",
    "requestedModel": "gpt-4.1-mini",
    "resolvedProviderModel": "gpt-4.1-mini-2025-04-14",
    "providerResponseStatus": "incomplete",
    "providerIncompleteReason": "max_output_tokens",
    "maxOutputTokens": 32768,
    "outputItemCount": 0,
    "outputItems": [],
    "outputItemsTruncated": false,
    "parseBoundary": "NOT_REACHED"
  },
  "transportLatencyMs": 123
}
```

IDs, status, reason, model, max output, usage, item count, item types/statuses,
safe item IDs, and bounded item names are retained. Full response content,
arguments, and arbitrary raw generated content are not added to incomplete
diagnostics.

## 12. Output-Item Safety

The adapter inspects incomplete `response.output` only for diagnostic metadata.
It records at most the first 32 item summaries and a truncation flag. The
summary may contain `type`, `status`, `name`, `id`, and `call_id`; arguments are
never included. No incomplete item is normalized, executed, appended to the
conversation, or parsed as final JSON.

## 13. Secret Safety

All new usage and diagnostic projections pass `assert_secret_free`. Known
secret-like values and fields remain rejected. New tests verify that function
call arguments are absent from the incomplete diagnostic projection. API keys,
authorization headers, environment variables, and credentials are not
persisted.

## 14. Adapter Changes

`live_adapters.py` now:

- invokes transport accounting immediately before `responses.create(...)`;
- reports received provider responses before incomplete-state handling;
- extracts usage including `totalTokens` with explicit unknown handling;
- captures requested and resolved model identifiers;
- captures bounded incomplete output-item summaries;
- attaches transport/response state to provider failure diagnostics;
- preserves the existing no-retry and no-parse incomplete branch.

`ProviderRequest` gained optional provider-agnostic accounting callbacks. The
SDK-specific inspection remains at the adapter boundary.

## 15. Runtime Changes

`runtime.py` now creates one typed `ProviderAttempt` per provider turn,
accounts transport latency for success, incomplete, timeout, and failure paths,
uses transport attempts for runaway provider-call ceilings, and persists the
attempt list into RAW. It does not import or depend on OpenAI SDK classes.

Runtime 5 safety behavior is unchanged:

```text
incomplete response -> diagnostic capture -> PROVIDER_FAILURE -> STOP
```

No parsing, tool execution, continuation, retry, repair, or fallback was added.

## 16. RAW Changes

Current RAW schema version:

```text
comparative-v4-raw-observation-4.0.0
```

Every V4 RAW observation now includes:

```text
rawOutput.providerAttempts[]
```

This is the source of provider transport facts, response facts, usage, bounded
diagnostics, usability, model-turn status, and transport latency. Historical
RAW files are untouched.

## 17. DERIVED Changes

Current projection version:

```text
comparative-v4-deterministic-projection-4.0.0
```

The deterministic projection now includes the new lifecycle counts and token/
latency accounting fields. It does not become the sole owner of provider facts;
the provider facts remain in RAW. Replay derives lifecycle counts from persisted
RAW `providerAttempts` and performs no provider access.

## 18. Replay Changes

`replay_observation()` now validates provider lifecycle counts against RAW
attempt records for the current schema. It continues to report:

```text
providerCalls = 0
networkCalls = 0
```

It never executes tools unexpectedly and does not fabricate missing historical
usage. Historical pre-remediation observations remain readable through their
existing schema path and are not migrated.

## 19. Backward Compatibility

Historical observations and artifacts are not rewritten. The new replay checks
are gated on the current RAW schema version. Existing accounting fields retain
their prior successful-normalization semantics; new transport metrics provide
the corrected distinction for new observations.

## 20. Versioning and Identity Impact

Version changes:

| Contract | Before | After | Reason |
|---|---|---|---|
| Manifest | `comparative-baseline-v4-2.0.0` | `comparative-baseline-v4-3.0.0` | binds changed persisted V4 contract |
| Resource accounting | `comparative-v4-resource-accounting-3.0.0` | `comparative-v4-resource-accounting-4.0.0` | explicit transport/response/model-turn metrics |
| Instrumentation | `comparative-v4-instrumentation-3.0.0` | `comparative-v4-instrumentation-4.0.0` | RAW diagnostic/accounting semantics changed |
| RAW | `comparative-v4-raw-observation-3.0.0` | `comparative-v4-raw-observation-4.0.0` | provider attempt records added |
| Projection | `comparative-v4-deterministic-projection-3.0.0` | `comparative-v4-deterministic-projection-4.0.0` | derived resource fields added |
| Runtime | `comparative-v4-live-runtime-contract-5.0.0` | `comparative-v4-live-runtime-contract-5.1.0` | provider accounting component added; safety semantics unchanged |

Deterministic runtime digest:

```text
old = a3211f44aad4633ca1d725b7a48506a6dd8725ab96becf182e867f1794e80edd
new = 71dfe66779cba85f5439e7eb18b3af7cd95b0e4d9bc46c797316d81d705598a1
```

Execution configuration identity did not change:

```text
f72225f4bb0d7e062dab8eb4b1120f600734fac2e51a9573e910d306c29e6ea8
```

Recomputed deterministic identities:

```text
experiment old = 073b51104c49430f949407710aa98719e45af375c78a9ec92c69c078eadc1a62
experiment new = 5cef12f1e9767b9b8ede4213f56cb18be00f7880ef2f16736d55df5c9d413ee4

pilot old = e0e26c17884e012137ee3912ab33df72bbd93c3546943c3773164e15783c38bc
pilot new = 6111d7f7415a0726fdaa69fd07d5b40d27cdcb6c391dd265ee7a0165ae0baa59

official old = 4972b2236c02c5d6aad74c8ae13809bb0c348dde4dcb51858b60337787668195
official new = e112f71720cebf2e4fb6f894d746b5deb03dd3d358df85a3fc50a3009c341e77
```

The execution hash remains unchanged because provider/model/prompt/tools/
structured-output/token/retry treatment did not change. The experiment, pilot,
and official plan identities changed causally through the versioned runtime,
resource, instrumentation, RAW, and projection contracts.

## 21. Tests

Added/updated deterministic tests cover:

- incomplete response with usage `123/456/579`;
- incomplete response with explicit zero usage;
- incomplete response without usage;
- incomplete response with output-item summaries;
- no incomplete tool execution or final parsing;
- completed final/tool responses;
- transport timeout with attempt but no provider response;
- three-attempt multi-turn accounting with a final incomplete attempt;
- replay with zero provider/network calls;
- secret-safe diagnostic projection;
- identity and manifest updates;
- completion/adapter normalization regressions.

Executed commands and results:

```text
python -m pytest -q tests/test_comparative_baseline_v4.py tests/test_comparative_live_adapters.py
97 passed

python -m pytest -q tests/test_comparative_*.py tests/test_story0134_human_runtime_review.py
148 passed

python -m pytest -q tests/test_comparative_baseline.py tests/test_comparative_collection_runtime.py tests/test_story0134_human_runtime_review.py
all passed

python -m compileall -q evaluations/comparative_baseline evaluations/comparative_baseline_v4 tests/test_comparative_baseline_v4.py tests/test_comparative_live_adapters.py
passed

V4 run_preflight()
PASS; providerCalls=0; networkCalls=0; deterministicReplay=READY
```

The broader command `python -m pytest -q` was also attempted. Collection
stopped with 13 unrelated import errors because `fastapi` is not installed in
the active environment. No dependency was installed and no changed test failed
execution.

## 22. Files Modified

Remediation changes:

- `ai-engine/evaluations/comparative_baseline/collection_runtime.py`: optional provider lifecycle callbacks on `ProviderRequest`.
- `ai-engine/evaluations/comparative_baseline/live_adapters.py`: OpenAI transport callbacks, usage extraction, bounded diagnostics, and state attribution.
- `ai-engine/evaluations/comparative_baseline/testing.py`: offline scripted transport lifecycle callbacks.
- `ai-engine/evaluations/comparative_baseline_v4/protocol.py`: versioned `ProviderAttempt`, resource metrics, RAW/projection/replay accounting.
- `ai-engine/evaluations/comparative_baseline_v4/runtime.py`: lifecycle orchestration, latency, transport-call guard, and RAW attempt persistence.
- `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json`: version and runtime digest updates only.
- `ai-engine/evaluations/comparative_baseline_v4/pilot.py`: transport-attempt summary and recomputed identity constants.
- `ai-engine/evaluations/comparative_baseline_v4/official.py`: transport-attempt summary and recomputed identity constants.
- `ai-engine/tests/test_comparative_baseline_v4.py`: accounting, incomplete, replay, timeout, multi-turn, and identity tests.
- `ai-engine/tests/test_comparative_live_adapters.py`: bounded provider diagnostic and usage tests.
- `docs/evaluation/comparative-baseline-v4-provider-observability-accounting-remediation.md`: this report.

No historical artifact, RAW, DERIVED file, ledger, notebook, benchmark,
validation, or official collection was modified by this Story.

## 23. Acceptance Criteria

| Criterion | Result |
|---|---|
| Transport attempts cannot disappear from new accounting | PASS |
| Incomplete provider state is preserved safely | PASS |
| Exposed usage is preserved | PASS |
| Explicit provider zero remains zero | PASS |
| Absent usage remains `NOT_MEASURED` | PASS |
| Incomplete output summaries are observable | PASS |
| Incomplete output items never execute | PASS |
| Incomplete responses never parse as final answers | PASS |
| Runtime-5 no-retry behavior remains | PASS |
| Completed responses remain functional | PASS |
| Multi-turn accounting works | PASS |
| Historical artifacts remain immutable | PASS |
| Replay remains provider-free | PASS |
| Secret safety remains intact | PASS |
| Experimental treatment unchanged | PASS |
| Live provider calls occurred | NO |
| Benchmark observations occurred | NO |
| Comparative tests pass | PASS |
| Broader AI-engine suite fully collected | NO, environment lacks `fastapi` |

## 24. Mandatory Answers A-Z

- A: `NO`; no benchmark observations were executed.
- B: `NO`; no live provider calls were executed.
- C: `NO`; official collection was not executed.
- D: `NO`; `gpt-4.1-mini` was unchanged.
- E: `NO`; `max_output_tokens=32768` was unchanged.
- F: `NO`; prompts were unchanged.
- G: `NO`; typed repository tools were unchanged.
- H: `NO`; strict structured output was unchanged.
- I: `NO`; retry behavior remains disabled.
- J: `YES`; `providerTransportAttempts` represents new transport-boundary attempts.
- K: `YES`; `providerResponsesReceived` is separate.
- L: `YES`; `usableProviderResponses` excludes incomplete responses.
- M: `YES`; `responseBearingModelTurns` is explicit and only counts usable responses.
- N: `YES`; exposed incomplete usage is persisted in RAW and resources.
- O: `YES`; absent usage is `NOT_MEASURED`, not zero.
- P: `YES`; explicit provider zero remains zero.
- Q: `YES`; bounded item types/statuses/IDs/names are retained.
- R: `NO`; incomplete tool calls cannot execute.
- S: `NO`; incomplete responses cannot be final-parsed.
- T: `NO`; incomplete responses cannot trigger automatic retry.
- U: `YES`; completed response regression tests pass.
- V: `YES`; multi-turn accounting tests pass.
- W: `YES`; replay reports zero provider/network calls.
- X: `NO`; historical artifacts were not modified.
- Y: `NO`; no secrets were added to persisted diagnostics.
- Z: `NO` for the entire AI Engine suite because 13 modules cannot collect without installed `fastapi`; all relevant comparative tests pass (`148 passed`).

## 25. Architecture Answers AA-AL

- AA: `resources.providerTransportAttempts` (`int`), reconstructed from RAW `providerAttempts[].transportAttempted`.
- AB: `resources.providerResponsesReceived` (`int`), reconstructed from RAW `providerAttempts[].providerResponseReceived`.
- AC: `resources.usableProviderResponses` (`int`), incremented only after accepted normalization.
- AD: `resources.responseBearingModelTurns` (`int`), incremented with usable normalized responses.
- AE: `totalProviderCalls` remains the historical successful-normalized-response count; it is not retroactively redefined as transport attempts.
- AF: `modelTurns` remains the historical successful-normalized-response-turn count; `responseBearingModelTurns` is the explicit new name for that semantic.
- AG: OpenAI usage is captured in `OpenAIProviderTransport.complete()` immediately after `responses.create()` returns, before incomplete-state classification; runtime records it through `ProviderRequest.on_provider_response`.
- AH: `observation.rawOutput.providerAttempts[].providerResponseDiagnostics`, inside current RAW schema `comparative-v4-raw-observation-4.0.0`.
- AI: bounded `outputItemCount`, `outputItems[]` containing only `type`, `status`, `name`, `id`, `call_id`, and `outputItemsTruncated`; no arguments/content.
- AJ: `YES`; instrumentation changed from `comparative-v4-instrumentation-3.0.0` to `comparative-v4-instrumentation-4.0.0`.
- AK: `YES`; runtime changed from `comparative-v4-live-runtime-contract-5.0.0` to `comparative-v4-live-runtime-contract-5.1.0`; safety behavior is unchanged and provider accounting was added to the contract.
- AL: experiment `073b51104c...a62` -> `5cef12f1...ee4`; pilot `e0e26c17...8bc` -> `6111d7f7...a59`; official `4972b223...195` -> `e112f717...e77`; runtime digest `a3211f44...edd` -> `71dfe667...8a1`; execution hash unchanged at `f72225f4...ea8`. The causal change is the versioned accounting/RAW/projection/runtime contract, not experimental treatment.

## 26. Unresolved Provider Behavior

The provider behavior associated with six V4 typed tools plus strict V4
structured output on `gpt-4.1-mini` remains experimentally unresolved. This
Story does not claim to fix it and does not change the experiment to avoid it.

## 27. Exact Next Human Decision

Authorize exactly one post-remediation technical validation only after human
review. Keep it bounded to:

```text
AGENT_DIRECT_OPEN
CASE-04@1.0.0
one repetition
```

Do not authorize official collection yet. If that validation naturally reaches
a tool/final trajectory, it may become a candidate for later official
collection. If the same six-tool plus strict-schema incomplete behavior
persists, make a separate experimental-design decision; do not tune tokens
again.

## 28. Classification

```text
PROVIDER_OBSERVABILITY_ACCOUNTING_REMEDIATION_READY
READY_FOR_HUMAN_AUTHORIZATION_ONE_POST_REMEDIATION_VALIDATION
STOP_FOR_HUMAN_REVIEW
```

PROVIDER_OBSERVABILITY_ACCOUNTING_REMEDIATION_READY
READY_FOR_HUMAN_AUTHORIZATION_ONE_POST_REMEDIATION_VALIDATION
STOP_FOR_HUMAN_REVIEW
