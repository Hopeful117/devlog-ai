# Comparative Baseline V4 Runtime Contract & Provider Parse Remediation

## Scope

This Story remediates the provider response parsing boundary and makes
experimentally significant live runtime behavior identity-visible. Benchmark,
questions, oracle, repository revision, conditions, provider, model, prompts,
tools, Policy A, ceilings, timeouts, retries, grounding, semantic evaluation,
and primary outcome were not changed.

No provider call, network call, live observation, new pilot, or official
collection was executed.

## Historical Defect Context

The accepted pilot was:

```text
v4-pilot-20260917T175238Z-5df1e336
experiment: ecfb7dd92702681c5e60f6cbbf5abe8cf0617ca9ab51c0c561f5b8ea86a1afd2
```

Its `CASE-01-COMPARATIVE:AGENT_DIRECT_OPEN:r1` observation ended in
`PROVIDER_FAILURE / RESPONSE_PARSE_ERROR`. The persisted function-call
arguments appeared valid, so the exact failing SDK value was not reconstructable.

The preceding attempt was:

```text
v4-pilot-20260917T174746Z-76a39515
```

It remains immutable and was not rewritten.

## Provider Pipeline

The live path is:

```text
OpenAI SDK Response
-> response.output item
-> SDK item model_dump or native attributes
-> function_call arguments representation
-> argument normalization
-> JSON parsing only for JSON text
-> normalized tool request
-> V4 runtime contract validation
-> RAW persistence
```

The relevant boundaries are:

| Boundary | Input | Expected output | Failure behavior |
|---|---|---|---|
| SDK response extraction | SDK response object | response envelope and output items | transport/contract diagnostic |
| item extraction | SDK item | mapping with `type` and optional `arguments` | native attribute fallback |
| argument normalization | dict, JSON text, or null-like value | argument object | parse/contract diagnostic; no repair of malformed JSON |
| tool normalization | JSON object | V4 tool payload | runtime Policy A contract validation |
| final output parsing | string `output_text` | JSON object | response parse/contract diagnostic |

The previous adapter assumed `function_call.arguments` was always a JSON
string and always accessed the serialized mapping. The corrected adapter
accepts an SDK-native dictionary directly, uses native attributes when a
serialized item mapping is unavailable, and parses only string values.
Missing/null-like arguments become an empty object so the V4 runtime can apply
the normal deterministic tool-contract error; malformed JSON is not repaired.

## Parse Operations and Root Cause

The provider path has two JSON parsing operations:

1. `json.loads(function_call.arguments)` for string-valued tool arguments.
2. `json.loads(response.output_text)` for final structured output.

Offline reproduction demonstrates the old defect: passing an SDK-native dict
to the first operation raises a type error even though the provider response is
already normalized. The correction removes that double/type-assumption parse.
Malformed string arguments still produce `RESPONSE_PARSE_ERROR`; valid JSON
arrays or other non-object values produce `RESPONSE_CONTRACT_ERROR`.

The exact historical accepted-pilot `JSONDecodeError` cannot be reproduced
from immutable RAW because the in-memory SDK representation is absent. Its
persisted function-call arguments are valid JSON. Therefore the historical
event is classified as:

```text
UNREPRODUCIBLE_WITH_AVAILABLE_EVIDENCE
```

The demonstrated latent defect is classified separately as:

```text
ADAPTER_WRONG_TYPE_ASSUMPTION
```

## Policy A and Diagnostics

Valid provider responses containing malformed model-generated tool arguments
still enter the V4 runtime and produce the deterministic tool-contract error
visible to the model. There is no argument repair, fallback acceptance, or
retry. SDK parsing failures remain provider failures and are not merged with
Policy A model-tool failures.

Diagnostics now preserve, when available:

```text
failure phase
SDK item type
value representation category
exception type
provider error category
safe message and digest
```

Only allow-listed metadata is persisted. API keys, bearer tokens, cookies,
headers, environment values, raw exception objects, and credentials are not
persisted. Offline tests verify redaction and secret absence.

## Runtime Contract Identity

The new live runtime contract is:

```text
runtime contract version: comparative-v4-live-runtime-contract-1.0.0
runtime contract digest: 6966a5b77d5f4f9469f5a556e9dad66ba82580d637d465947d230a5246c71e40
```

Its explicit experimentally significant components are:

| Component | Bound contract |
|---|---|
| Provider response normalization | `comparative-v4-provider-response-normalization-2.0.0` |
| Tool lifecycle/accounting | `comparative-v4-tool-lifecycle-2.0.0` |
| Ceiling enforcement ordering | `comparative-v4-tool-ceiling-order-2.0.0` |
| Natural recovery | `comparative-v4-natural-model-recovery-1.0.0` |
| Conversation propagation | `comparative-v4-conversation-propagation-1.0.0` |
| Execution statuses | `comparative-v4-execution-status-1.0.0` |

These are explicit contract components, not a hash of the repository, source
tree, tests, documentation, comments, or formatting.

Replay and DERIVED behavior remain separate. The replay compatibility fix for
non-dict RAW payloads is replay-only and is not included in the live runtime
contract. RAW schema and projection versions remain independently bound.

## Identity Matrix

| Identity | Bound inputs | Result |
|---|---|---|
| Execution configuration | output limits, tool schema, ceilings, native timeouts, retry policy | unchanged |
| Live runtime contract | provider normalization, lifecycle, ceiling order, recovery, conversation, statuses | new digest |
| Experiment | frozen benchmark/configuration plus runtime contract and existing schema contracts | changed |
| Pilot plan | experiment identity, six assignments, repetition count | changed |
| Replay/projection | RAW/projection contracts and deterministic projection logic | existing versioned contracts retained |

The historical experiment identity remains immutable:

```text
01b42a88a6b94311fd912b38dbfaa4a4ceed975502cab11e80d527f1dfdad7f6
```

The prior corrected-pilot identity is also historical and is not attached to
the new plan.

## New Frozen Identities

```text
runtime contract:
6966a5b77d5f4f9469f5a556e9dad66ba82580d637d465947d230a5246c71e40

experiment:
a9864fba8bb0da4fb337de83e8890e6a30ecfcf7a8adebd6dbd40252c2af27cf

execution configuration:
74dfdcd2b06ea38d124d51b88cbc57f62b504d907f4cd793d1a77387e619fad2

six-slot pilot plan:
c8b3201897e823f4817a107add1a0846b24b1b94e745df0004d40934900b46a2
```

The execution configuration identity remains stable because no configuration
value changed. The experiment and pilot-plan identities changed because live
provider normalization and ceiling enforcement are now explicit runtime
contract inputs.

## Historical Ceiling-Ordering Regression

The historical intermediate attempt permitted invalid requests to be processed
after the operation budget should have been exhausted. The corrected runtime
checks the ceiling first:

```text
ceilingCountedOperations = executedToolOperations + invalidToolRequests
ceilingCountedOperations <= 12
```

Post-ceiling requests remain traceable as skipped. A regression test mutates the
ceiling-order contract component and proves that the experiment digest changes;
preflight fails closed when the declared runtime digest does not match.

## Offline Reproduction Fixtures

Fixtures cover:

```text
valid JSON-string arguments
native dictionary arguments
empty object arguments
nested arguments
multiple function calls
missing/null-like arguments
malformed JSON arguments
non-object JSON arguments
text-only final responses
mixed response items
```

No real provider response was used.

## Historical Replay and Accounting

Both historical attempts remain replayable offline:

```text
intermediate attempt: 6/6 replay PASS
accepted attempt: 6/6 replay PASS
provider calls during replay: 0
network calls during replay: 0
```

The corrected accounting rule and byte distinction remain covered. Historical
RAW is not migrated in place and no unavailable evidence is fabricated.

## Verification

```text
focused V4/provider/runtime tests: 90 passed
preflight: PASS
compile/import check: PASS
git diff --check: PASS
provider calls: 0
network calls: 0
new pilot observations: 0
official observations: 0
```

Contamination tests remain passing for oracle isolation, condition boundaries,
DEVLOG context, direct-agent context, and per-observation diagnostics.

## Remaining Uncertainty

The exact in-memory SDK value that caused the accepted pilot's historical
`JSONDecodeError` is not present in immutable RAW, so that individual event
cannot be conclusively assigned to the old dict type-assumption or to a
different SDK response representation. The old adapter defect is reproduced
offline and corrected; future diagnostics now persist the representation class
and SDK item type needed for a definitive attribution.

## Readiness

```text
READY_FOR_NEW_PILOT_HUMAN_AUTHORIZATION
```

No pilot was authorized or executed by this Story. The exact remaining human
decision is:

> Review the corrected runtime contract and provider parsing remediation, then explicitly authorize the newly identified isolated six-slot V4 pilot.
