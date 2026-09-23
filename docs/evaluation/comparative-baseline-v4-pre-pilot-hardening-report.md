# V4 Pre-Pilot Hardening Report

## Scope

This Story was implementation-authorized. Changes are limited to technical
hardening of the V4 runtime, the existing comparative provider adapter, and
offline tests. No question, condition, oracle, semantic criterion, grounding
meaning, V3 identity, provider identity, or safety-ceiling value was changed.
No provider or network call was made.

## Files Changed

- `ai-engine/evaluations/comparative_baseline_v4/runtime.py`: bounded provider
  and tool execution, typed technical timeout states, conversation call IDs,
  experiment identity propagation, plan validation, ledger completeness, and
  derived projection persistence.
- `ai-engine/evaluations/comparative_baseline_v4/protocol.py`: timeout-aware
  execution/accounting contracts, condition payload validation, cryptographic
  identity, frozen-cell plan validation, replay projection checks, and
  write-once derived projections.
- `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json`: frozen
  benchmark/question/oracle hashes and execution-configuration hash remain
  bound to the V4 manifest; safety values remain pending.
- `ai-engine/evaluations/comparative_baseline/live_adapters.py`: V4 mode
  handling and provider-native Responses API conversation serialization.
- `ai-engine/tests/test_comparative_baseline_v4.py`: timeout, identity, plan,
  ledger, replay, and representative-path fixtures.
- `ai-engine/tests/test_comparative_live_adapters.py`: deterministic fake-client
  qualification of the V4 provider envelope.
- `docs/evaluation/comparative-baseline-v4-pre-pilot-hardening-report.md`:
  this report.

## Timeout Design

Provider and repository calls are executed in daemon worker threads and joined
with independent technical timeouts. A blocked call therefore cannot block
observation finalization indefinitely. The original operation is not repaired,
retried, or converted into a model answer.

- Provider timeout: `PROVIDER_TIMEOUT`, `executionStatus=PROVIDER_TIMEOUT`,
  provider timeout counter, raw technical event, and provider primary diagnostic.
- Tool timeout: `TOOL_TIMEOUT`, `executionStatus=TOOL_TIMEOUT`, visible
  `EXECUTED_TOOL_TIMEOUT` tool trace, tool timeout counter, and tool primary
  diagnostic.
- Safety exhaustion remains `executionStatus=CENSORED` with
  `SAFETY_*` stopping reason and all unavailable evaluations as
  `NOT_EVALUATED`.

Technical timeout values are constructor-level liveness controls and are not
experimental safety ceilings. A daemon worker cannot be forcibly cancelled;
the real provider/tool adapters must retain their own client/subprocess
timeouts before live execution.

## Offline Provider-Adapter Qualification

`OpenAIProviderTransport` now accepts `AGENT_DIRECT_OPEN`. Its deterministic
serializer maps generic V4 history to provider-native Responses input items:

- user messages become message items;
- assistant tool requests become `function_call` items with deterministic call
  IDs, operation identity, and canonical arguments;
- tool results/errors become `function_call_output` items with matching call
  IDs and canonical output;
- final assistant content remains an assistant message/final structured output.

The fake-client tests verify ordering, two tool requests, ranged-read
arguments, tool-result identity/content, and final JSON parsing without
constructing a network client or contacting OpenAI.

## Identity Chain

`validate_v4_identity` verifies:

- V4 manifest version;
- frozen benchmark identity and raw benchmark-manifest SHA-256;
- exact question ID/version/content SHA-256;
- approved oracle version and raw oracle SHA-256;
- repository ID/revision;
- V4 conditions and natural-recovery policy;
- versioned output-envelope configuration and canonical execution-config hash;
- exactly 18 frozen assignment cells;
- unresolved safety state.

`v4_experiment_identity` additionally hashes conditions, provider/model,
execution configuration, recovery/grounding/semantic identities, assignment
plan, repetition count, and the supplied safety configuration. If the manifest
later becomes approved, runtime ceilings must exactly equal the manifest values.

## Contamination, Completeness, and Isolation

Provider payload validation now checks actual V4 serialized payload shape and
condition identity. DEVLOG payloads reject direct tool/conversation material;
direct payloads reject DevLog context, expected/evaluator fields, and oracle
material. Legitimate repository result content is not scanned as evaluator
input, avoiding false contamination from repository text.

`validate_v4_plan` rejects duplicate, unexpected, or changed frozen cells. The
orchestrator ledger records run ID, planned IDs, assignment ID, observation ID,
status, condition, and raw hash. Resume rejects a different plan, skips
finalized slots, and persists missing/unexpected/duplicate summary fields.
Output and derived files are scoped to the caller's attempt directory and are
write-once. V4 artifacts cannot enter V3 persistence paths through the V4
writer.

## RAW/DERIVED Boundary

`write_observation` persists the immutable V4 RAW observation and refuses
overwrites. `write_derived_projection` writes a separate versioned projection
containing the raw hash, experiment identity, and deterministic outcome fields.
Replay recomputes supported post-provider evaluation and projection fields and
fails on mismatch without provider/network calls. Historical RAW is never
rewritten when projection logic changes.

## Representative Functional Path

The deterministic fixture exercises:

`QUESTION → search_repository → result → ranged read → result → second ranged read → FINAL`.

The minimum path consumes exactly 4 model turns, 4 provider calls, 3 tool
operations, 1 repository search, and 2 repository reads. The runtime records
canonical serialized request/result bytes, delivered bytes, input/output
tokens when supplied, per-provider latency, assignment latency, and any
rejections. The fixture is a minimum functional-path reference only; it is not
a selected or implied safety ceiling.

## Test-Count Traceability

The earlier `67 passed` figure was not reproducible from the focused command.
The pre-hardening checkout's recorded focused result was `66 passed`; its V4
fail-stop test was intentionally replaced by Policy A recovery and repeated
malformed-call coverage. This Story added meaningful timeout, adapter, identity,
plan, ledger, and projection tests. The current focused files contain 16 live
adapter tests, 20 V4 tests, 16 baseline tests, and 20 collection-runtime tests:
`72 passed`. No test was added solely to restore a number, and no behavioral
coverage was removed to reduce the count.

## Readiness Matrix

| Requirement | Status | Evidence |
|---|---|---|
| Multi-turn direct investigation | PASS | V4 direct runtime fixtures |
| Tool-result propagation | PASS | Next-turn conversation assertion |
| Policy A recovery | PASS | Malformed → error → corrected call → final fixture |
| Provider timeout | PASS | Blocking provider fixture |
| Tool timeout | PASS | Blocking tool fixture |
| Typed censoring | PASS | Censor fixture preserves `NOT_EVALUATED` |
| Semantic-only independence | PASS | Grounding failure still evaluates semantics |
| Grounding authority | PASS | Authorization, resolution, locator, excerpt, digest checks |
| Provider adapter envelope | PASS | Fake OpenAI client, zero network calls |
| Ranged reads | PASS | Search plus two ranged-read fixture |
| Cryptographic identity | PASS | Manifest hashes and experiment identity test |
| Contamination checks | PASS | Serialized condition-payload validation |
| Collection completeness | PASS | Frozen plan and ledger summary validation |
| Attempt isolation | PASS | Run ledger identity and write-once attempt paths |
| Deterministic replay | PASS | Evaluation/projection recomputation fixture |
| RAW/DERIVED immutability | PASS | Separate write-once RAW and DERIVED writers |

## Verification

```text
preflight: PASS
provider calls: 0
network calls: 0
official collection allowed: false
safety ceilings: PENDING_HUMAN_APPROVAL
pilot executed: false
focused tests: 72 passed
```

Executed:

```bash
python -m evaluations.comparative_baseline_v4.preflight
python -m pytest tests/test_comparative_baseline_v4.py tests/test_comparative_baseline.py tests/test_comparative_collection_runtime.py tests/test_comparative_live_adapters.py -q
```

The full AI Engine suite remains unavailable in this environment because the
existing application tests require an uninstalled `fastapi` package. No
unrelated dependency was installed. No provider/network test was attempted.

## Remaining Defects

- The timeout wrapper returns control while a daemon worker may still be stuck;
  live provider and subprocess adapters must retain bounded native timeouts.
- The real provider adapter is qualified only with a fake client; no live
  provider request has been authorized or executed.
- The ledger summary is deterministic for the scheduled plan, but a future
  official collection still needs the human-approved attempt metadata and
  final safety configuration.
- Safety ceilings remain unresolved by design, so no pilot is executable.

## Unresolved Human Decisions

- Approve the five V4 safety-ceiling values in a later Story.
- Approve final live-adapter timeout values separately from experimental
  wall-clock ceilings.
- Approve the final attempt metadata and collection authorization after ceiling
  qualification.

# Human Approval Required

1. Approve live-adapter native timeout configuration after target-environment
   qualification.
2. Approve the five safety-ceiling values in the next Story.
3. Explicitly authorize the six-slot pilot only after those values are frozen
   and final preflight passes.
