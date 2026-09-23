# Comparative Baseline V4 Runtime-Bound Six-Slot Pilot Report

## Authorization and Scope

The user explicitly authorized the newly identified isolated six-slot pilot.
Only the frozen pilot plan was executed. No replacement slot, additional
repetition, or official observation was executed.

## Attempt and Identities

```text
pilot executed: YES
attempt: v4-pilot-20260917T203006Z-df4a4107
path: ai-engine/data/comparative-baseline-v4/live-pilot/v4-pilot-20260917T203006Z-df4a4107
experiment: a9864fba8bb0da4fb337de83e8890e6a30ecfcf7a8adebd6dbd40252c2af27cf
runtime contract version: comparative-v4-live-runtime-contract-1.0.0
runtime contract digest: 6966a5b77d5f4f9469f5a556e9dad66ba82580d637d465947d230a5246c71e40
execution configuration: 74dfdcd2b06ea38d124d51b88cbc57f62b504d907f4cd793d1a77387e619fad2
pilot plan: c8b3201897e823f4817a107add1a0846b24b1b94e745df0004d40934900b46a2
repository revision: 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
provider/model: openai / gpt-4.1-mini
```

Preflight passed before the first provider call. All identities, benchmark,
questions, oracle, conditions, prompts/configuration, policies, ceilings,
timeouts, retries, and repository revision matched the frozen contract.

## Completeness and Status

```text
expected assignments: 6
attempted: 6
finalized: 6
DEVLOG: 3
AGENT_DIRECT_OPEN: 3
RAW: 6
DERIVED: 6
```

Execution statuses:

```text
COMPLETED: 2
CENSORED: 1
PROVIDER_FAILURE: 3
PROVIDER_TIMEOUT: 0
TOOL_TIMEOUT: 0
RUNTIME_FAILURE: 0
```

Evaluation counts:

```text
structural valid: YES=1, NO=1, NOT_EVALUATED=4
semantic evaluated: 1
semantic correct: 0
correct grounded: 0
primary diagnostics: PROVIDER_FAILURE=3, MODEL_STRUCTURAL_FAILURE=1,
                     GROUNDING_FAILURE=1, SAFETY_CEILING_CENSORING=1
```

## Provider Parse Qualification

Three observations produced `RESPONSE_PARSE_ERROR`:

```text
CASE-01-COMPARATIVE:AGENT_DIRECT_OPEN:r1
CASE-03:AGENT_DIRECT_OPEN:r1
CASE-04:DEVLOG:r1
```

All three diagnostics contain:

```text
category: RESPONSE_PARSE_ERROR
requestPhase: RESPONSE_PARSE
valueRepresentation: JSON_TEXT
exceptionType: JSONDecodeError
retryable: false
```

The two direct failures occurred after successful tool interaction. The DEVLOG
failure occurred before a provider response was normalized into a V4 response.
No malformed SDK-native dictionary was rejected in this pilot. The adapter
correction was exercised offline by fixtures covering native dictionaries,
JSON strings, null-like arguments, malformed JSON, non-object JSON, multiple
calls, and mixed items.

The persisted evidence is consistent with provider/model output text that did
not satisfy the frozen structured-response envelope. It does not demonstrate
an adapter rejection of a valid SDK-native function call.

Classification:

```text
LEGITIMATE_PROVIDER_OR_MODEL_OUTCOME
```

No automatic retry, JSON repair, argument coercion, or hidden guidance occurred.
Provider diagnostics contain no API key, bearer token, credential, or sensitive
header. Safe message, digest, phase, representation category, and exception
type are persisted where available.

## Condition Summary

### DEVLOG

```text
slots: 3
completed: 2
censored: 0
technical failures: 1 provider failure
structural valid: 1
grounding valid: 0
semantic evaluated: 1
semantic correct: 0
correct grounded: 0
provider calls: 2
tool attempts/executed/invalid/skipped: 0 / 0 / 0 / 0
repository bytes delivered: 0
input/output tokens: 16924 / 1581
latency: 30013 ms aggregate
```

### AGENT_DIRECT_OPEN

```text
slots: 3
completed: 0
censored: 1
technical failures: 2 provider failures
structural valid: 0
grounding valid: 0
semantic evaluated: 0
semantic correct: 0
correct grounded: 0
provider calls: 18
tool attempts/executed/invalid/skipped: 29 / 16 / 12 / 1
repository bytes delivered: 124414
input/output tokens: 53032 / 1042
latency: 38018 ms aggregate
```

## AGENT_DIRECT_OPEN Trajectories

`CASE-01-COMPARATIVE:AGENT_DIRECT_OPEN:r1`:

```text
search x4 -> read x2 -> RESPONSE_PARSE_ERROR
```

Repository evidence reached the model. No malformed tool request occurred; the
provider failure happened after six executed operations and two model turns.

`CASE-03:AGENT_DIRECT_OPEN:r1`:

```text
invalid search -> search x5 -> read x2 -> invalid read/search x3 -> RESPONSE_PARSE_ERROR
```

Repository evidence reached the model. Four malformed requests were returned
through Policy A and the model naturally recovered from them. The provider
failure occurred after six model turns and ten tool attempts.

`CASE-04:AGENT_DIRECT_OPEN:r1`:

```text
search x2 -> read -> invalid read -> search -> invalid x8 -> skipped search -> CENSORED
```

Repository evidence reached the model. Eight malformed requests were exposed
through Policy A. The thirteenth recognizable request was preserved as skipped
after the operation ceiling was reached.

## Tool and Resource Accounting

The frozen rule held for every observation:

```text
ceilingCountedOperations = executedToolOperations + invalidToolRequests
ceilingCountedOperations <= 12
```

The maximum observed values were:

```text
ceilingCountedOperations: 12
provider calls per observation: 10
model turns per observation: 10
wall-clock per observation: 30013 ms
largestSingleResultBytesDelivered: 39296
```

Cumulative repository bytes were kept distinct from the per-operation byte
ceiling. No per-operation byte breach occurred.

## Replay, Contamination, and Isolation

```text
replay: 6/6 PASS
replay provider calls: 0
replay network calls: 0
contamination: PASS
attempt isolation: PASS
ledger completeness: PASS
```

DEVLOG received no direct repository investigation. AGENT_DIRECT_OPEN received
no DevLog context, oracle, evaluator information, or expected evidence.
Provider diagnostics were not placed in model context or shared across
observations.

## Technical Anomalies

No runtime, accounting, replay, contamination, identity, timeout, or RAW
corruption anomaly was demonstrated. The three parse failures are legitimate
provider/model contract-invalid output outcomes under the corrected adapter
boundary and are not grounds for intervention or rerun.

## Pilot Validity

```text
PILOT_TECHNICALLY_VALID
```

The classification is based on contract adherence, complete immutable
artifacts, deterministic replay, isolation, and resource invariants, not on
answer correctness or completion rate.

## Verification

```text
preflight: PASS
focused tests: 90 passed
compile/import: PASS
git diff --check: PASS
provider calls during qualification: 0
network calls during qualification: 0
new pilot observations during qualification: 0
official observations: 0
```

## Readiness

```text
READY_FOR_OFFICIAL_COLLECTION_HUMAN_AUTHORIZATION
```

The official 18-slot collection remains unexecuted and unauthorized. The exact
remaining human decision is:

> Review the runtime-bound six-slot pilot and explicitly decide whether to authorize the frozen 18-slot Comparative Baseline V4 official collection.
