# Comparative Baseline V4 Corrected Six-Slot Pilot Report

## Authorization and Scope

The second isolated six-slot pilot was explicitly authorized by the user on
2026-09-17. Only the frozen pilot plan was executed. The official 18-slot
collection remains unauthorized and was not executed.

## Attempts

The first post-correction attempt, `v4-pilot-20260917T174746Z-76a39515`,
completed six observations but was not accepted as the corrected pilot because
invalid calls were checked after the tool ceiling and one replay edge case
failed. Its artifacts remain immutable and isolated.

The corrected attempt is:

```text
attempt: v4-pilot-20260917T175238Z-5df1e336
experiment identity: ecfb7dd92702681c5e60f6cbbf5abe8cf0617ca9ab51c0c561f5b8ea86a1afd2
execution configuration hash: 74dfdcd2b06ea38d124d51b88cbc57f62b504d907f4cd793d1a77387e619fad2
pilot-plan identity: 1df78be6679d527d1a6e5fb4712fc6314dbabd23063076a0f05c1f83473c3e3e
artifact root: ai-engine/data/comparative-baseline-v4/live-pilot/v4-pilot-20260917T175238Z-5df1e336
```

## Outcome

```text
attempted: 6
finalized: 6
RAW: 6
DERIVED: 6
replay: 6/6 PASS
contamination: PASS
provider calls: 28
network calls: 0
official collection: NOT EXECUTED
```

Execution statuses:

```text
COMPLETED: 3
CENSORED: 2
PROVIDER_FAILURE: 1
```

The pilot is classified `PILOT_PARTIALLY_VALID`: all six observations are
immutable, isolated, replayable, and correctly instrumented, but the result is
not an official collection and contains non-completed observations.

## Additional Correction

The corrected pilot exposed one implementation-order defect: invalid tool
requests were processed before checking the operation ceiling. The runtime now
checks the ceiling first, preserving later recognizable requests as skipped.
The rule is unchanged and explicit:

```text
ceilingCountedOperations = executedToolOperations + invalidToolRequests
maxToolOperations = 12
```

The replay path also now accepts valid non-dict RAW payloads for provider
failure observations without attempting to read a tool trace from them.

## Resource Invariants

All six corrected observations satisfy:

```text
ceilingCountedOperations <= 12
largestSingleResultBytesDelivered <= 65536
```

Provider failures are attributed as `RESPONSE_PARSE_ERROR` where observable.
No API key, bearer token, or secret-like credential was persisted in provider
diagnostics.

## Verification

```text
focused tests: 87 passed
preflight: PASS
replay: 6/6 PASS
compile/import check: PASS
git diff --check: PASS
provider calls during replay: 0
network calls during replay: 0
```

The previous pilot identity remains permanently associated with the original
pilot and was not reused:

```text
01b42a88a6b94311fd912b38dbfaa4a4ceed975502cab11e80d527f1dfdad7f6
```

## Readiness

The corrected six-slot pilot is complete. No further pilot authorization is
required for this attempt. Official collection remains separately
unauthorized.
