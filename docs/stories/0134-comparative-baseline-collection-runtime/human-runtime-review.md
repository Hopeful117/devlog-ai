# Story 0134 - Human Runtime Review

## Review Status

```text
PHASE = STORY_0134_HUMAN_RUNTIME_REVIEW
MODE = PAIR
STATUS = COMPLETE

STORY_0134_IMPLEMENTATION = COMPLETE
HUMAN_RUNTIME_REVIEW = PASS
STORY_0134_HUMAN_ACCEPTANCE_RECOMMENDED = YES

PROTOCOL_V1_CHANGED = NO
REAL_PROVIDER_CALLS = 0
NETWORK_CALLS = 0
REAL_DATA_COLLECTION = 0
EXPERIMENTAL_OBSERVATIONS_CREATED = 0
```

This review used only scripted provider responses, in-memory repository tools,
fixture grounding, and temporary dry-run artifacts. The dry-run artifacts are
marked `OFFLINE_DRY_RUN`, kept in pytest temporary directories, and are not
official baseline observations.

## Matrix and Order

```text
DRY_RUN_ASSIGNMENTS = 18
DRY_RUN_ASSIGNMENT_IDENTITIES_UNIQUE = YES
NO_DUPLICATES = YES
NO_MISSING_ASSIGNMENTS = YES
NO_FOURTH_REPETITION = YES
EXECUTION_ORDER_REPRODUCIBLE = YES
```

The review generated the complete frozen assignment matrix with seed `134`,
captured the order strategy, seed, and assignment IDs, reproduced the exact
order from the same seed, and confirmed a different seed changes only order,
not identity or count.

## Runtime Results

```text
DRY_RUN_ALL_UNRELATED_SLOTS_REACHED_AFTER_LOCAL_FAILURE = YES
OBSERVATION_ISOLATION = PASS
DEVLOG_POLICY_ISOLATION = PASS
AGENT_DIRECT_POLICY_ISOLATION = PASS
TOOL_OPERATION_BUDGET = PASS
REPOSITORY_BYTE_BUDGET = PASS
PROVIDER_CALL_BUDGET = PASS
LOGICAL_CALL_RETRY_SEPARATION = PASS
SEMANTIC_RETRY = NO
ANSWER_SELECTION = NO
SLOT_LOCAL_CONTINUATION = PASS
SYSTEMIC_FAILURE_STOP = PASS
RAW_CAPTURE_ORDER = PASS
RAW_TAMPER_DETECTION = PASS
COMMON_GROUNDING_BOUNDARY = PASS
RESUME_IDEMPOTENCY = PASS
STATE_MACHINE = PASS
COMPLETENESS_INTEGRATION = PASS
DATA_SCIENCE_CAPTURE_READINESS = PASS
SECRET_PERSISTENCE = PASS
```

The canonical 18-slot dry-run reached all assignments. A separate systemic
failure scenario stopped the scheduler before later assignments, without being
mixed into the canonical completeness result.

## Budget and Retry Semantics

```text
MAX_TOOL_OPERATIONS = 6
MAX_MODEL_TURNS = 8
CASE-01-COMPARATIVE_REPOSITORY_BYTES = 32401
CASE-03_REPOSITORY_BYTES = 31683
CASE-04_REPOSITORY_BYTES = 13504
MAX_PROVIDER_CALLS_PER_DEVLOG_OBSERVATION = 2
MAX_PROVIDER_CALLS_PER_AGENT_DIRECT_OBSERVATION = 9
MAX_PROVIDER_CALLS_FULL_BASELINE = 99
```

The review demonstrated operation-seven rejection, repository-byte exhaustion,
pre-response technical retry, response-bearing no-retry, and separate logical
turn/retry counters. The nine-call AGENT_DIRECT interpretation is eight logical
response-bearing turns plus one pre-response technical retry slot. Tool-loop
continuation is not a retry.

## Grounding and Capture

The same runtime-facing grounding interface was exercised for DEVLOG and
AGENT_DIRECT with a fixture authority representing the Java Core
`TaskSnapshotEvidenceResolver`/evaluation bridge boundary. The review covered:

- valid grounding;
- invalid excerpt/locator classification;
- unauthorized reference;
- unresolvable reference.

Raw provider responses and direct tool traces were present before structural,
grounding, or semantic validation. Copy mutation changed the raw hash and was
rejected during replay. No AI judge was used.

## Resume and State

The review finalized one assignment, interrupted another with retained partial
trace metadata, reconstructed the runtime, and resumed. Finalized work was not
rerun, the partial assignment was not treated as final, no artifact was
overwritten, and no duplicate assignment identity was produced.

The normal lifecycle and failure finalization were exercised:

```text
ASSIGNED
→ RUNNING
→ RAW_CAPTURED
→ STRUCTURALLY_VALIDATED
→ GROUNDING_VALIDATED
→ SEMANTICALLY_ELIGIBLE
→ FINALIZED
```

Illegal transitions were rejected.

## Completeness and Data Readiness

The 18 temporary results passed Story0133 completeness validation:

```text
assigned = 18
executed = 18
semantic eligible = 18
paired complete = 9
```

No comparative rate, product-value metric, or statistical result was computed.
Representative artifacts carried identity, condition, repetition, configuration,
validation, token state, logical calls, technical retries, latency state,
context/repository bytes, tool operations, confidence, abstention, raw hashes,
and grounding results.

## Secret Review

A recognizable fake secret was injected into runtime configuration. Recursive
secret detection rejected it before runtime persistence. The temporary artifact
directory remained empty, and a recursive text scan of the Story0134 runtime
files found no API-key, bearer-token, or secret literal.

## V1 Context-Budget Assumption

```text
V1_CONTEXT_BUDGET_ASSUMPTION_REVIEWED = YES
```

The review confirms that AGENT_DIRECT repository bytes are capped to the
question-specific DEVLOG visible-context bytes. This is a frozen V1 experimental
design choice, not a proven universal fairness rule. Sensitivity analysis is a
future candidate and was not performed.

## Human LEARN Review

1. AGENT_DIRECT must choose its own evidence because preselecting expected
   evidence would turn the direct condition into a replay of the DevLog
   projection and contaminate the context-access comparison.
2. Equal byte budgets are an experimental control for V1, not objective fairness:
   bytes do not equal information quality, search effort, or human usability.
3. A logical model turn is part of the bounded agent interaction. A technical
   retry is an exceptional transport recovery before any response-bearing output.
4. A response-bearing bad answer is retained as evidence of the runtime outcome;
   retrying it would select for a better answer and bias the experiment.
5. Slot-local failures must not stop unrelated assignments because otherwise one
   model failure changes the planned denominator and creates avoidable missingness.
6. Raw capture must precede evaluation so structural, grounding, and semantic
   validators cannot erase the response that caused a failure.
7. Resume/idempotency matters because duplicate or overwritten observations
   destroy repetition identity, call accounting, and auditability.
8. Fake dry-run outputs have zero comparative product-value meaning: they verify
   control flow and contracts only, not model quality, grounding capability, or
   DevLog effectiveness.

## Verification

```text
FOCUSED_TESTS = 41 passed
FULL_RELEVANT_TESTS = 326 passed
PYTHON_COMPILATION = PASS
SECRET_PERSISTENCE_SCAN = PASS
GIT_DIFF_CHECK = PASS
```

## Decision

```text
LIVE_PILOT_READINESS = READY_FOR_LIVE_PILOT_REVIEW
```

This is a technical readiness conclusion only. It does not authorize a live
pilot, provider call, data collection, or baseline execution.

## Files

- `ai-engine/evaluations/comparative_baseline/collection_runtime.py`
- `ai-engine/evaluations/comparative_baseline/testing.py`
- `ai-engine/tests/test_comparative_collection_runtime.py`
- `ai-engine/tests/test_story0134_human_runtime_review.py`
- `docs/stories/0134-comparative-baseline-collection-runtime/story.md`
- `docs/stories/0134-comparative-baseline-collection-runtime/implementation-report.md`
- `docs/stories/0134-comparative-baseline-collection-runtime/human-runtime-review.md`

```text
COMMIT = NO
PUSH = NO
MERGE = NO
```
