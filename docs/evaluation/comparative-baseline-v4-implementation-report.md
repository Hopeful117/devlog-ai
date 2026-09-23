# Implementation Report

## Scope

This implementation was authorized for the Engineering Story
`Comparative Baseline V4 Runtime Correctness`. It changes only the V4 runtime,
V4 manifest/contracts, and V4 deterministic tests. No V3 semantics, benchmark,
oracle, repository revision, provider configuration, or safety-ceiling value
was changed. No provider or network call was made.

## Implementation Summary

V4 now has an isolated `V4Assignment` with the real
`AGENT_DIRECT_OPEN` identity, an explicit multi-turn conversation, natural
model recovery for malformed tool calls, typed execution states, separated
provider/tool accounting, deterministic grounding/semantic evaluation, and
post-provider replay. A V4 orchestrator now supports assignment scheduling,
write-once observations, a restart-safe ledger, and deterministic summaries.

The runtime refuses execution while the manifest remains
`PENDING_HUMAN_APPROVAL`. Offline fixtures use an explicit `test_only=True`
escape hatch and do not authorize a pilot.

## Files Changed

- `ai-engine/evaluations/comparative_baseline_v4/protocol.py`: V4 assignment,
  execution status, resource accounting, grounding, semantic authority,
  deterministic projection, manifest validation, persistence, and replay.
- `ai-engine/evaluations/comparative_baseline_v4/runtime.py`: multi-turn
  provider/tool lifecycle, Policy A recovery, tool tracing, ceiling handling,
  V4 configuration use, and orchestration/ledger support.
- `ai-engine/evaluations/comparative_baseline_v4/v4-manifest.json`: frozen
  natural-recovery policy and versioned output-envelope configuration; safety
  values remain null and pending approval.
- `ai-engine/evaluations/comparative_baseline_v4/__init__.py`: exports V4
  assignment, status, runtime, and orchestrator contracts.
- `ai-engine/tests/test_comparative_baseline_v4.py`: replaced obsolete
  fail-stop/V3-budget tests with deterministic V4 lifecycle, recovery,
  accounting, grounding, semantic, range-read, replay, censor, and orchestration
  fixtures.
- `ai-engine/evaluations/comparative_baseline/live_adapters.py`: accepts and
  serializes the V4 conversation envelope through the existing provider
  adapter without changing V3 mode behavior.
- `ai-engine/tests/test_comparative_live_adapters.py`: offline qualification of
  V4 message ordering, tool identity/arguments/results, and final response
  handling.

## Corrected Lifecycle

The direct path is now:

`question → model request → tool request → deterministic tool result/error → model request with accumulated conversation → ... → final answer`

The next `ProviderRequest.input_payload["conversation"]` contains prior user,
assistant tool-call, and tool-result/error messages. Tool results are not
summarized or replaced with evaluator information. Evidence captures preserve
reference, content, content digest, result identity, and delivery status.

`AGENT_DIRECT_OPEN` is used in the assignment, request mode, input condition,
raw observation, resource attribution, and replay identity. V3 `AGENT_DIRECT`
remains unchanged.

## Resource Accounting

V4 now persists separate counters for:

- model turns;
- total, navigation, final-answer, and technical-retry provider calls;
- valid and invalid tool calls;
- repository searches and reads;
- serialized request/result bytes;
- bytes delivered to the model;
- bytes rejected;
- cumulative repository bytes delivered;
- input/output tokens when supplied;
- assignment/provider latency;
- cost as `NOT_AVAILABLE` when not observable;
- immutable first stopping reason.

`totalProviderCalls` is the sum of mutually exclusive navigation and
final-answer calls. Tool operations are never used as provider-call counts.
Rejected results remain in the trace with produced size and rejection reason.
Ceiling-skipped calls are also represented as non-executed attempts.

## Typed Execution and Censoring

V4 execution status values are:

- `COMPLETED`
- `CENSORED`
- `MODEL_FAILURE`
- `PROVIDER_FAILURE`
- `RUNTIME_FAILURE`

A safety-censored no-answer observation persists `structuralValid`, grounding,
semantic, and grounded-primary values as `NOT_EVALUATED`; it is not persisted as
structural `NO`. The first stopping reason is write-once in the accounting
object and is used by the deterministic primary diagnostic.

## Malformed Tool Calls

Policy A is implemented without repair. The runtime validates the operation and
required arguments, records an invalid attempt, appends only the deterministic
contract error to the conversation, and lets the model decide whether to issue
a corrected request. Repeated malformed calls consume ordinary provider,
model-turn, and tool-call resources until recovery or a safety ceiling.

Model contract errors, repository/tool execution failures, runtime adapter
contract defects, and provider failures have separate trace/error states. A
tool adapter `RuntimeContractError` is no longer automatically attributed to
the model.

## Grounding Authority

Grounding now independently evaluates:

- authorization against the frozen question evidence set;
- resolution of the provider-visible evidence item;
- `LINE_RANGE`, `SECTION`, and `COMMIT_HUNK` locator validity;
- exact excerpt occurrence;
- content digest consistency.

Unauthorized and unresolved references have distinct resolution fields. Direct
tool evidence and evaluator evidence use the same captured content and digest
domain. Exact-substring grounding remains required.

## Semantic Oracle Binding

Semantic evaluation no longer contains scattered expected answer values.

- CASE-01 derives its expected affirmative relationship from the frozen
  benchmark causal-link set and the approved oracle classifications.
- CASE-04 derives `NOT_ESTABLISHED` from the approved oracle.
- CASE-03 derives the expected affected components and tests from the frozen
  benchmark semantic contract and requires the complete authoritative set in
  the substantive answer.

The implementation did not modify the frozen oracle or benchmark files.

## Replay Scope

`replay_observation` performs zero provider/network calls. It verifies the raw
hash, reconstructs the V4 assignment and captured evidence, recomputes
structural, grounding, semantic-only, grounded-primary, execution diagnostic,
and deterministic projection fields, and fails on mismatch. Replay reports
`providerCalls=0` and `networkCalls=0`.

Provider sampling, hidden reasoning, network latency, and unavailable cost are
not falsely reconstructed. They remain explicitly non-replayable or
`NOT_AVAILABLE`.

## Orchestration Status

`V4CollectionOrchestrator` supports a supplied assignment plan, duplicate-plan
validation, V4 slot identity, write-once observation output, ledger persistence,
resume by finalized assignment ID, and a deterministic execution-status /
condition summary. It does not select safety ceilings or execute a live pilot.

## Verification

Preflight:

```text
status: PASS
provider calls: 0
network calls: 0
official collection allowed: false
safety ceilings: PENDING_HUMAN_APPROVAL
assignment count: 18
```

Focused and required comparative suites:

```bash
python -m pytest tests/test_comparative_baseline_v4.py tests/test_comparative_baseline.py tests/test_comparative_collection_runtime.py tests/test_comparative_live_adapters.py -q
```

Result: `66 passed`.

The exact requested V4 preflight command passed. The full `python -m pytest -q`
suite could not be collected because the environment lacks the existing
`fastapi` dependency; 13 unrelated application test modules fail import with
`ModuleNotFoundError: No module named 'fastapi'`. No dependency installation
was attempted.

## Unresolved Human Decisions

- Final numeric values for all five safety ceilings.
- Human approval of the execution-envelope configuration for pilot use.
- Human approval that the frozen CASE-03 semantic contract's complete affected
  component/test set is the intended deterministic criterion.
- Human approval of the six-slot pilot after the remaining defects below are
  addressed.

## Remaining Pilot-Invalidating Defects

- The runtime's wall-clock check is cooperative; it cannot interrupt a provider
  or tool call that blocks indefinitely. Provider/tool adapters need bounded
  transport/subprocess timeouts before live use.
- The actual live provider adapter is qualified only with a deterministic fake
  client. No real provider/network execution has been performed.
- The orchestrator ledger persists finalized slots and a status/condition
  summary, but contamination checks remain limited to the already validated
  condition inputs rather than a complete official-run projection.
- Safety values remain unresolved by design; `officialCollectionAllowed` is
  false and the pilot remains blocked.

## Pre-Pilot Readiness Matrix

| Requirement | Status | Evidence |
|---|---|---|
| Multi-turn direct investigation | PASS | `test_direct_results_are_available_on_next_provider_turn_and_accounted` |
| Tool-result propagation | PASS | Provider fixture asserts the next request contains the returned reference/content. |
| Policy A recovery | PASS | `test_policy_a_returns_contract_error_and_allows_natural_recovery` |
| Provider timeout | PASS | `test_provider_technical_timeout_is_not_safety_censoring` |
| Tool timeout | PASS | `test_tool_technical_timeout_is_traced_and_not_safety_censoring` |
| Typed censoring | PASS | `test_censored_observation_is_not_structural_failure` and repeated malformed-call fixture |
| Semantic-only independence | PASS | `test_grounding_and_semantics_are_independent` |
| Grounding authority | PASS | Frozen authorized-reference, locator, excerpt, and digest checks in V4 protocol tests. |
| Provider adapter envelope | PASS | `test_v4_provider_adapter_accepts_v4_mode_with_deterministic_fake_client` |
| Ranged reads | PASS | `test_large_file_ranges_and_tool_trace_are_preserved` and adapter serialization fixture. |
| Cryptographic identity | PASS | Manifest/preflight hashes plus `test_experiment_identity_binds_execution_and_safety_configuration`. |
| Contamination checks | PASS | Condition-specific payload validation and `test_v4_identity_and_open_condition_are_frozen_without_v3_budget`. |
| Collection completeness | PASS | Frozen-cell/duplicate plan validation and orchestrator ledger plan checks. |
| Attempt isolation | PASS | Run-specific observation IDs, ledger identity, and write-once output paths. |
| Deterministic replay | PASS | `test_replay_recomputes_deterministic_evaluation`. |
| RAW/DERIVED immutability | PASS | `write_observation` and `write_derived_projection` write-once tests. |

## Technical Timeout Design

Provider and repository calls execute in daemon worker threads joined with an
explicit technical timeout. A blocked call returns a typed
`PROVIDER_TIMEOUT`/`TOOL_TIMEOUT` result without synthesizing an answer or
retrying. The worker is not forcibly killed; it remains daemonized, so a
blocking implementation cannot block observation finalization, but live
adapters must still provide their own subprocess/client timeouts before pilot
use.

Technical timeout states are separate from `CENSORED` safety exhaustion and
have independent primary diagnostics, trace entries, counters, and replay
classification.

## Representative Functional Path

The offline ranged-read fixture exercises:

`QUESTION → search_repository → search result → read_file(startLine,endLine) → second ranged read → FINAL`.

Its deterministic minimum interaction shape is 4 model turns, 4 provider
calls, 3 tool operations, 1 search, and 2 reads. The observation records
serialized request/result bytes, delivered bytes, input/output fixture tokens,
per-provider latency, and assignment latency. Timing is diagnostic only; this
fixture is a minimum functional-path reference and is not a safety-ceiling
selection.

## Test Count Traceability

The previous report's `67 passed` value was not reproduced by the checked-out
focused command. The current command discovers 16 V4 tests, 16 baseline tests,
20 collection-runtime tests, and 14 live-adapter tests: `66` before this
hardening and `72` after adding six meaningful V4/adapter behaviors. The old
V4 fail-stop test was intentionally replaced by natural-recovery and repeated
malformed-call tests; no behavioral coverage was removed to target a count.
The earlier `67` was therefore a reporting/count discrepancy, not a missing
production test restored by padding.

# Human Approval Required

1. Approve the five final safety-ceiling values.
2. Approve the live provider adapter's V4 conversation-envelope qualification.
3. Approve the typed censoring and replay output contract.
4. Approve the remaining timeout, cryptographic-identity, and completeness
   fixes before a pilot.
5. Approve the six-slot pilot explicitly after those fixes pass offline
   verification.
