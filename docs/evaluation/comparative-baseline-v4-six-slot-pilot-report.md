# Comparative Baseline V4 Six-Slot Pilot Report

## Pilot Scope

The explicitly authorized six-slot pilot executed. The official 18-slot
collection was not executed, no additional repetition was run, and no
observation was automatically retried.

Pilot attempt:

```text
v4-pilot-20260917T153930Z-aa59d848
```

Attempt path:

```text
ai-engine/data/comparative-baseline-v4/live-pilot/v4-pilot-20260917T153930Z-aa59d848/
```

The attempt is isolated under `LIVE_PILOT` storage and is not baseline eligible.

## Frozen Identity and Configuration

| Item | Value |
|---|---|
| Experiment identity | `01b42a88a6b94311fd912b38dbfaa4a4ceed975502cab11e80d527f1dfdad7f6` |
| Execution configuration hash | `74dfdcd2b06ea38d124d51b88cbc57f62b504d907f4cd793d1a77387e619fad2` |
| Pilot-plan identity | `5fb719dea51b3debde68b546c1de59ab52030f0e8d76a9a5c483228f30bddeef` |
| Repository revision | `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149` |
| Provider/model | `openai / gpt-4.1-mini` |
| Recovery policy | `NATURAL_MODEL_RECOVERY` |
| Safety ceilings | `12 / 12 / 12 / 120s / 65536 bytes` |

The six-cell plan was exactly:

```text
CASE-01-COMPARATIVE:DEVLOG:r1
CASE-01-COMPARATIVE:AGENT_DIRECT_OPEN:r1
CASE-03:DEVLOG:r1
CASE-03:AGENT_DIRECT_OPEN:r1
CASE-04:DEVLOG:r1
CASE-04:AGENT_DIRECT_OPEN:r1
```

## Completeness and Integrity

| Check | Result |
|---|---|
| Attempted observations | 6 |
| Finalized observations | 6 |
| Condition balance | 3 `DEVLOG`, 3 `AGENT_DIRECT_OPEN` |
| Missing assignments | 0 |
| Unexpected assignments | 0 |
| Duplicate observation IDs | 0 |
| RAW artifacts | 6 |
| DERIVED projections | 6 |
| RAW artifact hashes | PASS |
| Attempt isolation | PASS |
| Contamination preflight | PASS |
| Replay | 6/6 `REPLAY_PASS` |
| Replay provider/network calls | 0 / 0 |

The ledger reports `complete: true` for the six-cell pilot plan.

## Execution Results

| Assignment | Condition | Status | Primary diagnostic | Stop reason | Turns | Provider calls | Tool calls |
|---|---|---|---|---|---:|---:|---:|
| CASE-01-COMPARATIVE:r1 | DEVLOG | COMPLETED | GROUNDING_FAILURE | NOT_STOPPED | 1 | 1 | 0 |
| CASE-01-COMPARATIVE:r1 | AGENT_DIRECT_OPEN | PROVIDER_FAILURE | PROVIDER_FAILURE | PROVIDER_FAILURE | 2 | 2 | 4 |
| CASE-03:r1 | DEVLOG | COMPLETED | GROUNDING_FAILURE | NOT_STOPPED | 1 | 1 | 0 |
| CASE-03:r1 | AGENT_DIRECT_OPEN | CENSORED | SAFETY_CEILING_CENSORING | SAFETY_MAX_TOOL_OPERATIONS | 8 | 8 | 13 |
| CASE-04:r1 | DEVLOG | COMPLETED | GROUNDING_FAILURE | NOT_STOPPED | 1 | 1 | 0 |
| CASE-04:r1 | AGENT_DIRECT_OPEN | PROVIDER_FAILURE | PROVIDER_FAILURE | PROVIDER_FAILURE | 7 | 7 | 12 |

The displayed `tool calls` field includes invalid and not-executed trace entries;
this is relevant to the accounting anomaly documented below.

## Evaluation Results

| Assignment | Structural valid | Grounding valid | Semantic evaluated | Semantic correct | Correct grounded | Replay |
|---|---|---|---|---|---|---|
| CASE-01-COMPARATIVE DEVLOG | YES | FAIL | true | true | false | PASS |
| CASE-01-COMPARATIVE AGENT_DIRECT_OPEN | NOT_EVALUATED | NOT_EVALUATED | false | NOT_EVALUATED | NOT_EVALUATED | PASS |
| CASE-03 DEVLOG | YES | FAIL | true | false | false | PASS |
| CASE-03 AGENT_DIRECT_OPEN | NOT_EVALUATED | NOT_EVALUATED | false | NOT_EVALUATED | NOT_EVALUATED | PASS |
| CASE-04 DEVLOG | YES | FAIL | true | false | false | PASS |
| CASE-04 AGENT_DIRECT_OPEN | NOT_EVALUATED | NOT_EVALUATED | false | NOT_EVALUATED | NOT_EVALUATED | PASS |

Grounding failures exclude the DEVLOG answers from the strict primary outcome;
semantic-only results remain visible. No slot produced a correct grounded answer.
This is descriptive only and is not a winner declaration.

## Resource Results

The current V4 resource contract does not persist a distinct pre-execution
`bytesRequested` field. `serializedResultBytes` is reported as produced result
bytes; delivered and rejected bytes are reported separately.

| Assignment | Searches | Reads | Serialized result bytes | Delivered bytes | Rejected bytes | Repository delivered | Input tokens | Output tokens | Provider latency ms | Total latency ms | Cost |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| CASE-01 DEVLOG | 0 | 0 | 0 | 0 | 0 | 0 | 8,457 | 881 | 11,671 | 11,675 | NOT_AVAILABLE |
| CASE-01 AGENT_DIRECT_OPEN | 1 | 3 | 60,220 | 60,220 | 0 | 60,220 | 1,911 | 163 | 3,984 | 8,634 | NOT_AVAILABLE |
| CASE-03 DEVLOG | 0 | 0 | 0 | 0 | 0 | 0 | 8,467 | 840 | 9,506 | 9,509 | NOT_AVAILABLE |
| CASE-03 AGENT_DIRECT_OPEN | 4 | 2 | 61,266 | 61,266 | 0 | 61,266 | 42,143 | 447 | 13,558 | 13,713 | NOT_AVAILABLE |
| CASE-04 DEVLOG | 0 | 0 | 0 | 0 | 0 | 0 | 3,990 | 977 | 9,320 | 9,322 | NOT_AVAILABLE |
| CASE-04 AGENT_DIRECT_OPEN | 5 | 4 | 70,105 | 70,105 | 0 | 70,105 | 58,058 | 424 | 11,613 | 17,330 | NOT_AVAILABLE |

Condition totals:

| Condition | Provider calls | Tool calls | Repository bytes delivered | Input tokens | Output tokens | Total latency ms |
|---|---:|---:|---:|---:|---:|---:|
| DEVLOG | 3 | 0 | 0 | 20,914 | 2,698 | 30,506 |
| AGENT_DIRECT_OPEN | 17 | 29 | 191,591 | 102,112 | 1,034 | 39,677 |

Pilot total: 20 provider calls, 29 recorded tool calls, 191,591 repository
bytes delivered, 123,026 input tokens, 3,732 output tokens, and 70,183 ms of
recorded assignment latency. Cost was `NOT_AVAILABLE` for every observation.

## AGENT_DIRECT_OPEN Navigation

| Assignment | Trajectory | Evidence reached model | Ranged reads | Malformed calls | Ceiling/stop | Assessment |
|---|---|---|---|---:|---|---|
| CASE-01:r1 | search -> read -> read -> read -> provider failure | Yes | No | 0 | Provider failure | Investigation acquired four results but stopped before final answer. |
| CASE-03:r1 | search -> search -> invalid -> search -> read -> search -> invalid -> invalid -> invalid -> invalid -> invalid -> read -> ceiling | Yes | No | 6 | Tool-operation ceiling | Natural malformed requests were preserved; no final answer was produced. |
| CASE-04:r1 | invalid git_log -> invalid git_log -> search -> search -> read -> read -> read -> search -> search -> read -> search -> provider failure | Yes | No | 3 | Provider failure | Policy A errors were visible and later valid searches/reads continued. |

No direct observation reached a final answer. The direct model did receive
repository results before the stopping events. No ranged-read operation was
requested in the live pilot; this is an observed model trajectory, not an
intervention.

## Ceiling Utilization

| Assignment | Turns / 12 | Provider / 12 | Recorded tool calls / 12 | Wall clock / 120s | Largest result / 65,536 |
|---|---:|---:|---:|---:|---:|
| CASE-01 DEVLOG | 8.3% | 8.3% | 0% | 9.7% | 0% |
| CASE-01 AGENT_DIRECT_OPEN | 16.7% | 16.7% | 33.3% | 7.2% | 91.9% |
| CASE-03 DEVLOG | 8.3% | 8.3% | 0% | 7.9% | 0% |
| CASE-03 AGENT_DIRECT_OPEN | 66.7% | 66.7% | 108.3%* | 11.4% | 93.5% |
| CASE-04 DEVLOG | 8.3% | 8.3% | 0% | 7.8% | 0% |
| CASE-04 AGENT_DIRECT_OPEN | 58.3% | 58.3% | 100%* | 14.4% | 107.0%* |

`*` CASE-03 recorded 13 tool trace entries because the final skipped call is
recorded after the 12-operation ceiling; only six calls executed successfully,
and six were invalid. CASE-04's largest single result was 35,376 bytes; its
70,105 value is cumulative delivered bytes, not a single-result ceiling breach.
The resource field currently reports cumulative/result accounting in a way that
requires this distinction in interpretation.

## Technical Anomalies

1. `CASE-01 AGENT_DIRECT_OPEN` and `CASE-04 AGENT_DIRECT_OPEN` ended with
   `PROVIDER_FAILURE` after successful repository interaction. The persisted RAW
   does not contain the underlying transport exception, so the exact remote
   failure cause cannot be distinguished post hoc from the artifact. No retry
   occurred, consistent with the frozen zero-retry policy.
2. `CASE-03 AGENT_DIRECT_OPEN` reached `SAFETY_MAX_TOOL_OPERATIONS` after
   repeated malformed calls. This is a legitimate frozen-envelope outcome, not
   a reason to extend the ceiling.
3. The `toolCalls` counter includes invalid and skipped trace entries, so it can
   exceed the approved operation ceiling. Executed operations and not-executed
   entries remain visible in the trace, but the aggregate counter is not a pure
   executed-operation count.
4. The live model did not use ranged reads in any direct slot. The ranged-read
   envelope was verified offline previously and was not forced during this run.

These anomalies were preserved after the pilot. Runtime, prompts, evaluator,
ceilings, and persisted observations were not modified or rerun.

## Condition Summary

| Condition | Slots | Completed | Structural valid | Grounding valid | Semantic evaluated | Semantic correct | Correct grounded | Censored | Technical provider failures |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| DEVLOG | 3 | 3 | 3 | 0 | 3 | 1 | 0 | 0 | 0 |
| AGENT_DIRECT_OPEN | 3 | 0 | 0 | 0 | 0 | 0 | 0 | 1 | 2 |

With one repetition per question, these counts are descriptive pilot evidence
only. No significance test or superiority claim is made.

## Pilot Validity and Recommendation

The pilot classification is:

```text
PILOT_PARTIALLY_VALID
```

The plan was complete, isolated, uncontaminated, persisted correctly, and all
six deterministic replays passed. However, two direct slots ended in provider
failure before final answers, one direct slot was censored during malformed-call
navigation, and the tool aggregate accounting is ambiguous at the ceiling. The
underlying provider failure cause is not recoverable from RAW, so the direct
condition is not fully interpretable as a six-slot technical validation.

Recommendation:

```text
NOT_READY_FOR_OFFICIAL_COLLECTION
```

Do not authorize the 18-slot official collection based on this report. Human
review should first determine whether the two provider failures reflect a
transient provider-side issue or an adapter/transport defect, and whether the
tool aggregate accounting requires correction under a new explicitly approved
V4 identity. No repair or rerun was performed under the current pilot identity.

## Remaining Human Decision

Review the preserved six-slot evidence and decide whether to investigate/fix the
provider-failure attribution and tool accounting before separately authorizing
the 18-slot official collection. The official collection remains unauthorized
and was not executed.
