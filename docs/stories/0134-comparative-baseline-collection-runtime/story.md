# Story 0134 - Comparative Baseline Collection Runtime

## Status

`IMPLEMENTED - AWAITING_HUMAN_ACCEPTANCE`

This Story implements evaluation-only runtime infrastructure for the frozen
Story0133 comparative baseline. It does not authorize provider calls, network
access, real model execution, observation collection, statistical analysis,
production changes, Story0132 reopening, or commit/push/merge.

## Authorization

```text
PHASE = COMPARATIVE_BASELINE_COLLECTION_RUNTIME_IMPLEMENTATION
MODE = PAIR
STATUS = AUTHORIZED

PROTOCOL_V1_FROZEN = YES
STORY_0133_FROZEN = YES
IMPLEMENTATION_AUTHORIZED = YES
PROVIDER_CALLS_AUTHORIZED = NO
DATA_COLLECTION_AUTHORIZED = NO
REAL_MODEL_EXECUTION_AUTHORIZED = NO
STATISTICAL_ANALYSIS_AUTHORIZED = NO
PRODUCTION_CHANGE_AUTHORIZED = NO
```

The authoritative design is:

`docs/evaluation/comparative-baseline-collection-runtime-design.md`

## Scope

### In Scope

- Offline-testable evaluation runtime contracts and orchestration.
- Frozen 18-assignment identity and reproducible seeded order.
- DEVLOG context input capture with projection, digest, evidence-item, byte, and
  prompt accounting.
- Autonomous AGENT_DIRECT typed read-only tool boundary.
- Pinned repository revision checks and question-specific repository budgets.
- Common evaluator-facing answer contract.
- Common grounding-authority interface for the Java Core evaluation bridge.
- Raw response and AGENT_DIRECT tool-trace capture before parsing/evaluation.
- Technical-only retry accounting and response-bearing no-retry semantics.
- Systemic versus slot-local failure continuation.
- Explicit observation lifecycle and illegal-transition rejection.
- Write-once artifacts, restart-safe identity, offline replay, and secret guards.
- Offline fake provider/tool fixtures and deterministic contract tests.

### Explicit Non-Goals

- No provider calls, API keys, network access, or real model execution.
- No data collection or experimental observations.
- No statistical analysis or derived Data Science DataFrame.
- No production Java, Python service, prompt, schema, MCP, frontend, database,
  repository collector, or domain-contract change.
- No modification to Story0133, Comparative Baseline Protocol V1, oracle,
  mapping, question wording, evidence scope, scoring, or missingness semantics.
- No RAG, vector database, ML infrastructure, or generic agent framework.
- No human runtime condition.
- No commit, push, merge, or human acceptance claim by the implementer.

## Frozen Runtime Decisions

```text
MAX_TOOL_OPERATIONS = 6
MAX_MODEL_TURNS = 8
CASE-01-COMPARATIVE_REPOSITORY_BYTES = 32401
CASE-03_REPOSITORY_BYTES = 31683
CASE-04_REPOSITORY_BYTES = 13504
FINAL_OUTPUT_TOKEN_ENVELOPE = 1800
AGENT_INTERMEDIATE_TOKEN_ENVELOPE = 512
MAX_PROVIDER_CALLS_PER_DEVLOG_OBSERVATION = 2
MAX_PROVIDER_CALLS_PER_AGENT_DIRECT_OBSERVATION = 9
MAX_PROVIDER_CALLS_FULL_BASELINE = 99
SEMANTIC_RETRY = DISABLED
ANSWER_SELECTION = NO
```

The nine-call AGENT_DIRECT ceiling is implemented as eight logical,
response-bearing model turns plus one technical retry call. The retry is only
available before any response-bearing output exists. A tool call is not an
answer attempt, and a response-bearing semantic/structural/grounding failure
is never retried.

Equalizing AGENT_DIRECT repository bytes to each question's DEVLOG visible
context budget is explicitly a V1 experimental design choice. It is not claimed
to be universally fair. Sensitivity analysis is a future phase and is not
implemented here.

## Runtime Components

`ai-engine/evaluations/comparative_baseline/collection_runtime.py` provides:

- frozen runtime configuration and budgets;
- common answer contract validation;
- DEVLOG context input construction;
- pinned typed repository tool interface;
- bounded AGENT_DIRECT loop;
- provider abstraction and raw response capture;
- technical retry/failure accounting;
- state machine and assignment continuation;
- write-once observation persistence;
- offline replay and secret checks.

`testing.py` provides scripted provider fixtures. It is not a production
provider adapter.

## Acceptance Criteria

1. [x] The runtime imports and validates the frozen Story0133 manifest without
   changing it.
2. [x] DEVLOG receives only an injected frozen context and has no repository
   tool capability.
3. [x] AGENT_DIRECT exposes only typed read-only repository operations.
4. [x] Repository operations carry and validate the frozen revision.
5. [x] Oracle, expected-evidence, cross-condition, and secret contamination
   attempts fail closed.
6. [x] The common answer contract is strict, bounded, and condition-neutral.
7. [x] Grounding is represented by one injectable deterministic authority.
8. [x] Raw responses are captured before structural or semantic validation.
9. [x] AGENT_DIRECT captures model turns, tool requests/results, hashes, and
   operation counts.
10. [x] Technical retry calls are separately counted from logical model calls.
11. [x] Response-bearing output receives no semantic retry or answer selection.
12. [x] Slot-local failures do not prevent unrelated assignments from running.
13. [x] The 6-operation, 8-turn, question-byte, token, and provider-call limits
   fail closed.
14. [x] Seeded execution ordering is deterministic and reproducible.
15. [x] Observation lifecycle transitions reject illegal transitions.
16. [x] Finalized artifacts are write-once and restart-safe.
17. [x] Offline replay verifies raw identities/hashes without a provider,
   network, or API key and reports non-replayable elements.
18. [x] Offline fake transport and repository fixtures cover normal, failure,
   retry, budget, structural, isolation, and secret cases.
19. [x] No provider call, network call, data collection, or production change is
   performed by the tests.
20. [x] No Protocol V1 or Story0133 artifact is modified.

## Known Limitations

- The real provider transport is deliberately not implemented or invoked.
- The Java Core grounding bridge adapter is represented by an injectable
  authority; no live Core bridge or repository evaluation is executed.
- The offline fixture authority is not evidence of production grounding.
- Replay is partial: provider sampling, hidden reasoning, unreported usage,
  network latency, and absent raw responses cannot be reconstructed.
- No real repository checkout adapter is exposed to the runtime in this Story.
- No Data Science dataset or analysis is implemented.

## Pedagogy

### `LEARN`

- Information-volume fairness is a V1 assumption, not a universal claim.
- Logical model calls differ from technical retries.
- Slot-local failure must not cascade to unrelated assignments.
- Raw capture precedes semantic evaluation and finalized artifacts are immutable.

### `PAIR`

- State machine, tool-loop boundary, budget enforcement, answer contract,
  grounding adapter, retry semantics, resume, and contamination controls.

### `DELEGATE`

- Serialization, hashing, typed dispatch, fake transport, fixture generation,
  deterministic seed utilities, and repetitive validation tests.

## Required Report

```text
PHASE = COMPARATIVE_BASELINE_COLLECTION_RUNTIME_IMPLEMENTATION
MODE = PAIR
STATUS = IMPLEMENTED_AWAITING_HUMAN_ACCEPTANCE

PROTOCOL_V1_CHANGED = NO
PROVIDER_CALLS = 0
NETWORK_CALLS = 0
DATA_COLLECTION = 0
EXPERIMENTAL_OBSERVATIONS_CREATED = 0
PRODUCTION_CODE_CHANGED = NO
DATA_SCIENCE_ANALYSIS_IMPLEMENTED = NO
COMMIT = NO
PUSH = NO
MERGE = NO
```
