# V4 Safety-Ceiling Qualification

## Status

This document is an offline qualification and recommendation for the five V4
safety ceilings. It does not approve the values, authorize a pilot, modify the
V4 manifest, execute a provider, make a network call, or alter the immutable V3
artifacts.

The V4 manifest therefore remains `PENDING_HUMAN_APPROVAL` with all five
values `null`.

## Qualification Inputs

The qualification uses only:

- the 18 immutable V3 RAW observations under
  `data/comparative-baseline/story0133-comparative-baseline-1.0.0/raw/`;
- the V3 normalized diagnosis in `docs/evaluation/v3-protocol-diagnosis.md`;
- the offline V4 representative-path fixtures in
  `ai-engine/tests/test_comparative_baseline_v4.py`;
- the V4 implementation and pre-pilot hardening report.

The V3 observations were `OFFLINE_DRY_RUN` artifacts representing the frozen
official collection responses. They are evidence of observed interaction
shapes and censoring, not new V4 provider measurements.

## V3 Direct-Condition Measurements

The following table reports all nine `AGENT_DIRECT` observations. `toolOps`
includes successful and failed tool operations; `repositoryBytes` is the
persisted V3 delivered repository-byte field and is not treated as an
equivalent DEVLOG context budget.

| Case/repetition | Status/error | Provider calls | Logical calls | Tool ops | Searches/reads | Repository bytes | Latency ms |
|---|---|---:|---:|---:|---:|---:|---:|
| CASE-01 r1 | INVALID / infrastructure | 2 | 2 | 4 | 3 / 1 | 8,609 | 2,754 |
| CASE-01 r2 | INVALID / infrastructure | 2 | 2 | 4 | 3 / 1 | 8,609 | 5,285 |
| CASE-01 r3 | INVALID / infrastructure | 3 | 3 | 4 | 3 / 1 | 18,317 | 11,907 |
| CASE-03 r1 | INVALID / infrastructure | 7 | 7 | 6 | 6 / 0 | 0 | 11,190 |
| CASE-03 r2 | INVALID / infrastructure | 7 | 7 | 6 | 6 / 0 | 0 | 11,139 |
| CASE-03 r3 | INVALID / infrastructure | 7 | 7 | 6 | 6 / 0 | 0 | 7,025 |
| CASE-04 r1 | INVALID / infrastructure | 3 | 3 | 6 | 6 / 0 | 12,367 | 4,652 |
| CASE-04 r2 | INVALID / infrastructure | 7 | 7 | 6 | 6 / 0 | 0 | 7,378 |
| CASE-04 r3 | INVALID / infrastructure | 2 | 2 | 2 | 1 / 1 | 3,618 | 3,318 |

Observed direct ranges:

| Metric | Minimum | Maximum | Interpretation |
|---|---:|---:|---|
| Provider calls | 2 | 7 | Seven calls were consumed by repeated malformed tool requests in CASE-03 and CASE-04. |
| Logical model calls | 2 | 7 | No direct observation reached a final answer. |
| Tool operations | 2 | 6 | Six was the V3 operation ceiling, so the maximum is right-censored. |
| Search operations | 1 | 6 | Representative discovery requires more than one search in some cases. |
| Read operations | 0 | 1 | Decisive reads were often rejected before delivery. |
| Persisted repository bytes | 0 | 18,317 | Zero means the direct path stopped before a result was delivered, not zero work. |
| Latency | 2,754 ms | 11,907 ms | Lower-bound operational evidence only; no live V4 distribution. |

The V3 traces also show rejected read results of approximately 35,376 and
39,296 bytes. Those reads were rejected because the old cumulative question
budget had insufficient remaining bytes. This supports qualifying a V4 result
size above those values, but it does not justify treating 32,401, 31,683, or
13,504 bytes as V4 direct-condition ceilings.

## Functional Lower Bound

The offline V4 representative fixture exercises:

`QUESTION -> search_repository -> result -> ranged read -> result -> second ranged read -> FINAL`

It consumes exactly:

- 3 tool operations;
- 4 provider calls;
- 4 model turns;
- 1 search and 2 ranged reads.

This is a minimum functional-path bound, not a recommended ceiling. It does not
represent every valid navigation strategy or guarantee live-provider behavior.

## Candidate Configuration

The following is the primary offline candidate for human review:

| Manifest field | Candidate | Basis | Confidence |
|---|---:|---|---|
| `maxToolOperations` | 12 | Four times the minimum fixture path; leaves room above V3's censored six-operation trace and one malformed-call recovery sequence. | Medium |
| `maxModelTurns` | 12 | Three times the four-turn minimum; exceeds every observed V3 direct trace while bounding loops. | Medium |
| `maxProviderCallsPerObservation` | 12 | Three times the four-call minimum and above the observed seven-call maximum, including malformed-call churn. | Medium |
| `maxWallClockSeconds` | 120 | Above the observed 11.9-second V3 maximum with operational headroom; must remain distinct from native provider/tool timeouts. | Low/Medium |
| `maxReadBytesPerOperation` | 65,536 | Above the observed 39,296-byte rejected read and compatible with ranged reads while bounding one delivered result. | Medium |

The candidate intentionally has no cumulative repository-byte ceiling for
`AGENT_DIRECT_OPEN`. Repository bytes requested, delivered, rejected, and useful
must remain observed dependent variables rather than being equated to prepared
DEVLOG context bytes.

The candidate is not a claim that the values are optimal. It is a conservative
qualification point that preserves a representative direct path while retaining
finite protection against repeated tool calls, provider loops, hung execution,
and oversized individual results.

## Sensitivity and Risks

Lower values are unsafe for interpretability:

- fewer than 4 model/provider turns cannot contain the validated minimum path;
- fewer than 3 tool operations cannot contain search plus two ranged reads;
- a result ceiling at or below 39,296 bytes risks reproducing the V3 decisive-read
  censoring;
- a provider-call ceiling at or below 7 can censor repeated schema recovery before
  a final answer;
- a wall-clock ceiling near the observed 11.9 seconds leaves no live operational
  headroom.

Higher values increase cost and censoring delay. In particular, increasing the
byte ceiling does not solve provider context-window limits, serialization limits,
or unbounded repository scraping. The runtime must still use native bounded
timeouts in the provider and repository adapters; daemon-thread timeout wrappers
alone are not sufficient.

The candidate also assumes Policy A / `NATURAL_MODEL_RECOVERY`, as recorded in
the V4 manifest. If human approval selects Policy B or another recovery rule,
the experiment identity and ceiling qualification must be regenerated rather
than silently reusing this recommendation.

## Pilot Revision Policy

Before the six-slot pilot, human approval must freeze the five values and the
recovery policy. The pilot must stop without expanding to the 18-slot collection
if any of these occur:

1. direct tool results are not visible to the next model turn;
2. a ceiling is reached before the minimum search/read/final-answer path;
3. malformed-call handling is not uniform and fully accounted;
4. grounding cannot resolve against the same immutable content domain;
5. replay changes or loses evaluation state;
6. native provider or tool timeouts are absent or exceed the approved liveness
   policy.

Any ceiling revision after pilot observations requires a new execution identity,
new qualification record, and a separately authorized pilot. It must not relabel
or overwrite V3 or earlier V4 artifacts.

## Human Decisions Required

1. Approve, reject, or replace the primary candidate values.
2. Confirm Policy A / `NATURAL_MODEL_RECOVERY`, or select a different frozen
   recovery policy.
3. Approve native provider/tool timeout values separately from
   `maxWallClockSeconds`.
4. Authorize the six-slot pilot only after the approved values are written to a
   new manifest identity and preflight passes.

## Conclusion

The offline evidence supports a bounded V4 candidate, not a final approved
configuration. The primary recommendation is `(12 tool operations, 12 model
turns, 12 provider calls, 120 seconds, 65,536 bytes per operation)`. The V4
manifest remains unchanged and collection remains blocked pending explicit human
approval.
