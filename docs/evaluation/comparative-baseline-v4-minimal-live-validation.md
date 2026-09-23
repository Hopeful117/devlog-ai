# Comparative Baseline V4 Minimal Live Validation

## Purpose

This validation tested only whether the corrected V4 runtime gives one frozen
question to both conditions a realistic opportunity to produce a natural final
answer. It is not a comparative quality or efficiency estimate.

## Authorization and Selection

- Authorized observations: exactly 2; one `DEVLOG` and one `AGENT_DIRECT_OPEN`.
- Question: `CASE-03`, version `1.0.0`.
- Rationale: this frozen question exercises direct repository investigation and
  therefore validates the corrected execution envelope without changing the
  expected comparative task.
- Repository revision: `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`.
- Attempt: `corrected-v4-minimal-live-validation-20260918T000000Z`.
- Path: `ai-engine/data/comparative-baseline-v4/live-pilot/corrected-v4-minimal-live-validation-20260918T000000Z/`.

## Frozen Identity and Preflight

- Experiment identity: `fb0c7e484534a1483a93011e17f1bb682d710732c6e45a6b145ec97c4c68b51e`.
- Execution identity: `198e46172caeb1a267e04685cf548c19446083d19e5ae0215f1f5fe7458f61b0`.
- Runtime contract: `comparative-v4-live-runtime-contract-2.0.0`.
- Runtime digest: `a8cd7b4712e473804cf2eb2500be808c87788ae56715a7a7da51d3001cbd6664`.
- Instrumentation: `comparative-v4-instrumentation-2.0.0`.
- Benchmark: `devlog-comparative-baseline-1.0.0`.
- Oracle: `1.0.0`, frozen.
- Provider/model: `openai` / `gpt-4.1-mini`.
- Policy: `NATURAL_COMPLETION_WITH_EMERGENCY_RUNAWAY_GUARD`.
- Preflight: `PASS`; natural completion active, historical 12-operation ceiling
  inactive, resource equalization inactive, benchmark/oracle/conditions/Policy
  A frozen, identity valid, Java grounding and isolated pilot storage ready.
- Preflight provider/network calls: `0/0`.

Emergency guard values were 96 tool operations, 48 model turns, 48 provider
calls, 300 seconds wall clock, and 65536 delivered result bytes per operation.

## Observations

### DEVLOG

- Status: `COMPLETED`.
- Final answer: available.
- Structural validity: `YES`.
- Semantic evaluation: evaluated; `semanticCorrect=false`.
- Grounding: `FAIL`.
- Correct-grounded answer: `false`.
- Guard: not triggered; stopping reason `NOT_STOPPED`.
- Resources: 1 provider call, 1 model turn, 0 tool attempts, 0 executed tools,
  8467 input tokens, 721 output tokens, 9188 total tokens, 7885 ms latency,
  0 repository bytes delivered, cost unavailable.

### AGENT_DIRECT_OPEN

- Status: `CENSORED_RUNAWAY`.
- Final answer: unavailable.
- Structural, semantic, and grounding evaluation: `NOT_EVALUATED` where no final
  answer existed.
- Guard: triggered at `RUNAWAY_GUARD_MODEL_TURNS`.
- Resources: 48 provider calls/model turns, 72 tool attempts, 22 executed valid
  operations, 50 invalid requests, 0 skipped operations, 18 searches, 4 reads,
  93671 bytes delivered, 682297 input tokens, 2506 output tokens, 684803 total
  tokens, 160038 ms latency, cost unavailable.
- Ordered trajectory: the model began with successful searches and reads; it
  then alternated valid searches/reads with malformed tool requests. The full
  ordered trajectory is preserved in the RAW artifact; it ended with repeated
  search requests and no final response. No manual navigation hint or evidence
  was provided.

## Interpretation

- Maximum observed tool operations: `72` attempts, `22` executed valid operations.
- Maximum observed model turns: `48`.
- Maximum observed provider calls: `48`.
- Maximum observed wall-clock duration: `160038 ms`.
- Emergency guard triggered: `YES`, for `AGENT_DIRECT_OPEN` only.
- Historical 12-operation boundary exceeded: `YES` for DIRECT; it was not used
  as a termination condition.
- Resource differences were observed, not equalized.
- Provider calls: `49` live calls total, excluding preflight.
- Network calls: `2` live provider transport calls; replay network calls `0`.
- No observation was retried or replaced.

The `AGENT_DIRECT_OPEN` result shows that the historical 12-operation ceiling
was not the termination mechanism, but it does not establish that the corrected
guard is sufficiently permissive for this model behavior. The repeated invalid
requests are preserved as model/runtime diagnostic evidence and are not
converted into semantic incorrectness.

## Replay and Integrity

- RAW count: `2`; DERIVED count: `2`.
- RAW artifact hashes: `PASS`.
- Deterministic replay: `PASS`; provider replay calls `0`, network replay calls
  `0`.
- Resource accounting reconstruction: `PASS`.
- Contamination checks on condition inputs: `PASS`.
- Condition isolation: `PASS`.
- Historical artifacts and official storage were not modified.

## Verification

- Focused V4/comparative tests: `64 passed`.
- Notebook JSON/source and execution checks: `PASS`.
- `compileall`: `PASS`.
- `git diff --check`: `PASS`.
- Full AI Engine suite remains environment-blocked by missing `fastapi` during
  unrelated test collection.

## Mandatory Questions

- A. Did DEVLOG have a realistic opportunity to produce its final answer? `YES`.
- B. Did AGENT_DIRECT_OPEN have a realistic opportunity to investigate and
  produce its final answer? `YES`, under the corrected guard; it did not use
  that opportunity successfully.
- C. Did the emergency guard behave as technical protection rather than the
  normal treatment? `INCONCLUSIVE`.
- D. Was either observation terminated merely for exceeding 12 operations? `NO`.
- E. Were resource differences observed rather than artificially equalized?
  `YES`.

## Classification and Next Decision

`MINIMAL_LIVE_VALIDATION_INCONCLUSIVE`.

Readiness: `NOT_READY_FOR_CORRECTED_V4_OFFICIAL_COLLECTION_HUMAN_AUTHORIZATION`.

The exact next human decision is whether to authorize a separately designed
follow-up validation or revise the corrected runtime/agent tool-call contract.
No additional observation, automatic retry, runtime change, pilot, or official
collection was performed.
