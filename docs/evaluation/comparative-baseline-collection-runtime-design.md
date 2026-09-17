# Comparative Baseline Collection Runtime Design

## Status

```text
PHASE = COMPARATIVE_BASELINE_COLLECTION_RUNTIME_DESIGN
MODE = PAIR
STATUS = COMPLETE

PROTOCOL_V1_FROZEN = YES
PROVIDER_CALLS = 0
DATA_COLLECTION = 0
IMPLEMENTATION_PERFORMED = NO
STATISTICAL_ANALYSIS = NO
```

This document designs the future collection runtime for the human-accepted
Story0133 baseline. It does not execute an assignment, call a provider, collect
an observation, modify the frozen protocol, or change production behavior.

No contradiction was found in the frozen experimental contract. The existing
Java grounding resolver is task-snapshot-oriented while AGENT_DIRECT produces an
interactive repository snapshot. The safe resolution is an evaluation-only
adapter that presents both captured evidence snapshots to the same deterministic
grounding authority. This is an implementation boundary, not a protocol change.

## Frozen Boundary

The runtime consumes, but does not edit:

- `ai-engine/evaluations/comparative_baseline/baseline-manifest.json`;
- the three Story0133 condition policies;
- the approved question, oracle, mapping, evidence, repetition, pairing,
  missingness, taxonomy, and scoring identities;
- repository identity `1feead5d-dfc9-4b2c-aa9c-045a8524a9f8`;
- repository revision `18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`.

The runtime creates no new question, evidence subset, oracle, denominator, or
semantic classification. A runtime execution manifest may freeze provider and
operational settings, but it must reference the Story0133 manifest and receive a
new execution-configuration identity if any setting changes.

## 1. Runtime Architecture

The smallest safe architecture is an evaluation-only, file-backed orchestrator
with five isolated components:

```text
frozen Story0133 manifest and policies
        |
        v
preflight + deterministic assignment scheduler
        |
        +--> DEVLOG context loader --> common provider adapter
        |
        +--> AGENT_DIRECT tool sandbox --> common provider adapter
        |
        v
raw capture writer (write-once)
        |
        v
offline structural/grounding/replay validator
        |
        v
Story0133 raw observation artifact
```

The orchestrator owns assignment identity, order, budgets, lifecycle, resume,
and artifact writes. Condition runners own only their permitted input
construction. A common provider adapter owns model invocation, raw response
capture, and technical error classification. A common evaluator adapter maps
the answer contract to the already frozen scoring and grounding contracts.

There is no production service, database entity, MCP path, RAG layer, vector
store, or generic agent framework in this architecture.

### Reuse classification

| Existing asset | Classification | Boundary |
|---|---|---|
| `comparative_baseline.infrastructure` | `REUSE_UNCHANGED` | Manifest validation, assignment matrix, pairing, completeness, immutable hash checks. |
| Story0133 manifest and policies | `REUSE_UNCHANGED` | Read-only inputs to preflight. |
| `v3_design_c.py` projection and digest helpers | `REUSE_WITH_ADAPTER` | Load Story0133 question IDs and policy-visible projections; do not use its CASE-01/03/04 smoke identity as a replacement. |
| `v3_design_c_offline.py` evidence byte/hash projection logic | `REUSE_WITH_ADAPTER` | Use only for deterministic context materialization and verification. |
| `v3_structured_output.py` capture-before-parse helpers | `REUSE_WITH_ADAPTER` | Extend capture to tool-loop requests/results and common answer parsing. |
| `v3_protocol.py` canonical hashing, safe metadata, write-once artifacts | `REUSE_UNCHANGED` where interfaces fit | Its V3 manifest/config constants must not be imported as Story0133 identities. |
| `v3_design_c_live_smoke.py` runner | `NOT_REUSABLE` | It is fail-fast, fixed to three slots, single-turn, and provider-specific. |
| production `app.providers.openai` | `NOT_REUSABLE` | Application defaults, adapter retries, and parse behavior are not the frozen evaluation boundary. |
| `TaskSnapshotEvidenceResolver` | `REUSE_WITH_ADAPTER` | Common deterministic resolver; direct captured evidence requires an evaluation-only snapshot adapter. |
| `CoreV2EvaluationBridgeTest` pattern | `REUSE_WITH_ADAPTER` | Use the bridge pattern without persisting production tasks or analyses. |

## 2. Common Model Boundary

The following must be identical for both conditions:

- provider and model identifier;
- model version/revision where the provider exposes one;
- final semantic question and question/version identity;
- final answer schema and schema digest;
- final output token limit;
- temperature, top-p, reasoning, seed, and other generation settings;
- provider SDK/API mode for the final answer;
- timeout, provider retry policy, and technical retry semantics;
- evaluator, grounding authority, oracle, repository revision, and scoring
  contract.

The following differences are unavoidable and must be captured, not hidden:

- DEVLOG has one prepared context projection and one final-answer request;
- AGENT_DIRECT has no prepared evidence list and uses read-only repository tools
  between model turns;
- AGENT_DIRECT therefore has additional intermediate provider calls, tool
  schemas, tool latency, operation counts, search-result bytes, and a direct
  repository context budget;
- DEVLOG context preparation has a separate preparation cost; AGENT_DIRECT has
  repository/tool infrastructure cost during answering.

The final answer request must use the same provider/model/configuration. The
AGENT_DIRECT tool-action requests use a separately recorded intermediate output
limit because DEVLOG has no equivalent action request. This is an explicit
condition-specific execution overhead, not a silent model difference.

## 3. DEVLOG Runtime

For each DEVLOG assignment, the runner loads a frozen, deterministic context
artifact identified by:

```text
questionId + questionVersion
repositoryId + repositoryRevision
conditionPolicyVersion
contextSelectionRevision
projectionRevision
contextDigest
providerVisibleEvidence item identities
providerVisibleEvidence byte length and SHA-256
```

The context preparation pipeline is:

```text
approved DevLog source/context
  -> frozen projection selection
  -> exact UTF-8 evidence snapshot
  -> context digest and per-item hashes
  -> deterministic prompt rendering
  -> prompt digest
  -> common final-answer provider request
```

The provider input contains only the frozen projection permitted by the DEVLOG
policy. It contains no oracle, expected classification, expected evidence IDs,
AGENT_DIRECT trace, or human answer. The runner has no repository checkout,
Git executable, shell, search client, or fallback evidence loader.

The existing Design C projection is reusable with an adapter. It already records
exact evidence bytes and validates projection digests. It is not reusable as an
identity source: Story0133's `CASE-01-COMPARATIVE`, `CASE-03`, and `CASE-04`
identities remain authoritative.

DEVLOG accounting records:

- context preparation run ID, revision, and status;
- context/projection identities and source/output digests;
- provider-visible evidence item count;
- provider-visible evidence bytes and per-item bytes;
- exact system/user input representation and UTF-8 byte length;
- prompt/schema digests;
- input tokens when reported;
- final response tokens, latency, and provider call count;
- `ONLINE_ANSWERING_COST` and `DEVLOG_CONTEXT_PREPARATION_COST` separately.

For the existing frozen projection measurements, the expected provider-visible
evidence byte budgets are approximately `32,401` for CASE-01,
`31,683` for CASE-03, and `13,504` for CASE-04. The implementation must load
and verify the actual frozen manifest values rather than hard-code these
measurements in a new protocol.

## 4. AGENT_DIRECT Runtime

AGENT_DIRECT receives the question and repository identity/revision, not a
preselected expected-evidence list. It runs in a dedicated read-only checkout
or equivalent immutable Git object store pinned to the Story0133 revision.

The model can decide what to inspect, when to stop, and whether to abstain. It
does not receive CL-01, expected classifications, expected evidence IDs, oracle
answers, DevLog projections, or another condition's output. The evaluator does
not inject hints based on the frozen authorized-evidence list.

This is fair direct-repository inspection because the agent sees the same
question, pinned repository, model, final output contract, and evaluator as
DEVLOG, while its evidence acquisition is an autonomous read-only interaction.
It is not a complete repository dump. The context budget is recorded and
enforced, and the agent's actual search/read choices remain observable.

### Bounded tools

The tool server exposes only these typed operations:

```text
read_file(path, start_line?, end_line?)
search_repository(query, path_prefix?, max_matches?)
git_log(path?, author?, max_entries?)
git_show(commit, path?)
git_diff(commit, parent?, path?)
inspect_commit(commit)
```

Rules:

- all paths and commits are resolved against the pinned revision;
- path traversal, absolute paths, shell syntax, pipes, redirects, and command
  substitution are rejected;
- search is a bounded literal/approved-regex operation implemented by the tool
  server, never arbitrary shell access;
- Git operations are read-only and restricted to the pinned repository;
- output is canonical JSON with stable ordering, line numbers, commit IDs, and
  UTF-8 byte lengths;
- tests may be read but never executed;
- the agent cannot request environment variables, package installation, network,
  subprocesses, or mutable files.

### Tool-loop limits

The initial design limits one AGENT_DIRECT observation to:

```text
MAX_TOOL_OPERATIONS = 6
MAX_MODEL_TURNS = 8 response-bearing model turns
MAX_TOOL_RESULT_BYTES = 8,192 per operation
MAX_CUMULATIVE_REPOSITORY_BYTES = provider-visible byte budget for the question
MAX_WALL_CLOCK = 180 seconds for the observation
MAX_TECHNICAL_RETRIES = 1 for the complete observation
```

The cumulative byte budget is the corresponding frozen DEVLOG provider-visible
evidence budget: approximately 32,401 bytes for CASE-01, 31,683 for CASE-03,
and 13,504 for CASE-04. It counts canonical tool result bytes, including search
matches and metadata, not merely file-content bytes. A result that would exceed
the remaining budget is rejected before content is returned and recorded as a
bounded tool error. This preserves autonomy while preventing a direct condition
from receiving an unbounded information-volume advantage.

Six operations is sufficient for the intended pattern of search, inspect one or
more files, inspect a relevant commit, and stop, while remaining auditable and
cost-bounded. The limit is not an evidence preselector. If the agent cannot
answer within the budget, its abstention or failure remains the observation.

The loop is:

```text
question + tool policy
  -> model action request
  -> one bounded repository tool call
  -> canonical tool result
  -> model action request or final answer
  -> ... up to six operations/eight model turns
  -> common final answer contract
```

The runtime does not use hidden evaluator feedback to steer the loop.

## 5. Common Answer Contract

Both conditions produce the same evaluator-facing JSON contract. The contract
is intentionally smaller than the broad Story Context DTO and contains no
DevLog-internal fields:

```json
{
  "questionId": "...",
  "questionVersion": "...",
  "answerText": "...",
  "relationshipResult": "ESTABLISHED | NOT_ESTABLISHED | NOT_APPLICABLE",
  "abstention": true,
  "claims": [
    {
      "text": "...",
      "claimType": "FACT | INTERPRETATION",
      "references": ["reference index or canonical reference"]
    }
  ],
  "evidence": [
    {
      "reference": "...",
      "locator": {"kind": "LINE_RANGE | SECTION | COMMIT_HUNK", "...": "..."},
      "excerpt": "...",
      "role": "DIRECT | SUPPORTING"
    }
  ],
  "confidence": "HIGH | MEDIUM | LOW"
}
```

Contract bounds for the implementation candidate:

- `answerText`: 3,000 UTF-8 characters;
- `claims`: at most 4;
- claim text: 600 characters;
- `evidence`: at most 6 assertions;
- each excerpt: 400 characters and must be an exact captured substring;
- confidence: the existing `HIGH`, `MEDIUM`, `LOW` enum;
- no unknown fields, no missing required fields, no expected-answer fields.

`relationshipResult` is present for all questions; `NOT_APPLICABLE` is used
where the question is not a causal relationship question. `abstention` is an
explicit model output, not inferred from low confidence. Claim and reference
fields are model-produced claims, not oracle labels.

The proposed final output envelope is `1,800` output tokens. This is a design
target, not yet a frozen execution identity. Before collection, offline fixtures
must serialize the maximum legal contract and prove that the selected limit is
adequate. A response that reaches the limit without a valid complete contract
is a structural failure, never repaired by truncation.

The AGENT_DIRECT action request uses a separate strict tool-action schema with a
`512` token output envelope. It permits exactly one operation name and bounded
arguments; it cannot emit an answer or evaluator metadata in that channel.

## 6. Grounding Authority

Canonical references are:

```text
repository file: path relative to repository root
Markdown section: path + exact heading, resolved to the section bounded by the
                  next heading of equal or lower level
line range: path + one-based inclusive startLine/endLine
commit: commit:<full SHA>
commit hunk: commit:<full SHA> + path + exact unified-diff hunk header
```

The direct tool server returns these same locator forms. It also returns exact
UTF-8 content, content byte length, SHA-256, repository identity, revision, and
stable locator metadata. The model may cite only references it received from a
tool result or the explicitly supplied question identity.

The same deterministic Core grounding authority should validate both conditions:
the Java `TaskSnapshotEvidenceResolver` and its existing evaluation bridge,
through an evaluation-only adapter that maps each condition's captured evidence
snapshot into the resolver's immutable input shape. Python may perform early
diagnostics, but Java/Core remains authoritative for authorization, locator
resolution, excerpt equality, revision identity, and resolved-content digest.

If the existing resolver cannot consume a direct snapshot without production
changes, the implementation Story must add an evaluation-only bridge or stop;
it must not weaken the resolver or create a second semantic grounding authority.

## 7. Raw Capture

The raw capture boundary is:

```text
provider response received
  -> capture complete SDK response and safe metadata
  -> capture raw text/structured payload and hash
  -> only then parse/validate
```

For DEVLOG, raw output includes the exact provider response representation. For
AGENT_DIRECT, raw output includes the exact final response plus every ordered
tool request and tool result. Each tool trace entry contains:

```text
operationIndex
operationType
canonicalTargetOrQuery
repositoryRevision
resultIdentity
resultByteCount
resultSha256
durationMs
truncated
errorState
```

Prompt/input representation, context digest, projection identity, model
configuration, request metadata, usage, finish status, and retry attempts are
captured alongside the raw output. Parsing and semantic fields are derived
records. They never replace the raw response.

## 8. Retry and Failure Continuation

### Technical retry

Technical retry is allowed only for a provider transport failure before a
response-bearing model output exists: timeout, connection failure, rate limit,
or provider-unavailable response. There is at most one retry for the complete
observation, with identical question, condition input, model configuration, and
tool policy. If a multi-turn attempt fails without a response, the retry starts
from a clean interaction state; its partial trace is retained and the attempts
are not merged.

An API response containing a tool call, malformed tool call, structural final
answer, semantic failure, or grounding failure is response-bearing. It receives
no technical retry.

### No semantic retry

There is no corrective prompt, semantic retry, answer selection, best-attempt
selection, excerpt repair, or evaluator-guided second answer. A response-bearing
failure is captured once and classified by the frozen taxonomy.

### Failure classes

| Failure | Scope | Action |
|---|---|---|
| Manifest/config/revision/policy/secret preflight failure | Systemic | Stop before any provider call; no assignments execute. |
| Provider outage, credentials failure, budget configuration failure, or tool sandbox unavailable | Systemic | Stop the run; retain completed artifacts; remaining assignments remain missing/not evaluated. |
| Timeout/connection/rate limit for one request | Current slot first; systemic only if classified persistent by preflight/runtime policy | One technical retry, then invalidate the slot and continue. |
| Invalid tool request or unsupported operation | Current slot model failure | Capture response, mark slot invalid/not evaluated, continue. |
| Tool target not found, result truncation, or tool execution error | Current slot | Return a bounded error to the agent if budget remains; otherwise invalidate slot and continue. |
| Structural final-output failure | Current slot | Capture raw response, no retry, continue. |
| Grounding/reference/semantic failure | Current slot | Capture response and diagnostics, no retry, continue. |
| Execution budget exhaustion | Current slot if its reservation is exhausted; systemic if aggregate reservation is unavailable | Fail closed, never exceed the manifest envelope, continue only where a reserved budget remains. |

An unrelated model, grounding, or semantic failure never prevents later
assignments from running. This corrects the historical slot-cascade problem.

## 9. Assignment Order and Isolation

The 18 assignments are generated from the frozen matrix before execution. A
seeded, deterministic constrained Fisher-Yates shuffle produces the order. The
run artifact records:

```text
orderAlgorithm = STORY0133_SEEDED_CONSTRAINED_SHUFFLE_V1
orderSeed
ordered assignment IDs
```

The scheduler avoids unnecessary same-condition and same-question runs when
ties permit, but never changes assignment identity or drops a cell. The seed is
selected and frozen before the first provider call and is independent of model
outputs, failures, and results.

Every observation receives a fresh provider conversation, fresh tool-server
session, fresh condition input, and fresh trace. No state crosses repetitions,
questions, or conditions. Immutable context artifacts may be read again; model
messages, tool traces, caches containing answers, and prior outputs may not be
reused.

## 10. Provider Configuration and Budgets

Before collection, the execution manifest must freeze:

```text
provider
model
model version/revision if exposed
temperature
top_p
seed or NOT_CONFIGURED
reasoning setting
final max output tokens
intermediate max output tokens
request timeout
SDK version
API mode
final schema revision and digest
AGENT_DIRECT tool schema revision and digest
SDK retry count
adapter technical retry count
semantic retry = DISABLED
```

The Design C evidence supports using `openai`/`gpt-4.1-mini` as a candidate
common provider/model and supports a `90` second request timeout, but the
implementation Story must freeze the actual execution identity before calls.
Provider-default temperature/top-p are not silently accepted; they must be
recorded as explicit `PROVIDER_DEFAULT` identity if the provider cannot expose
resolved values.

The initial budget design is:

```text
MAX_PROVIDER_CALLS_PER_DEVLOG_OBSERVATION = 2
  one final request + one technical retry

MAX_PROVIDER_CALLS_PER_AGENT_DIRECT_OBSERVATION = 9
  up to eight model turns + one complete-observation technical retry

MAX_PROVIDER_CALLS_FULL_BASELINE = 99
  9 DEVLOG assignments * 2 + 9 AGENT_DIRECT assignments * 9

EXPECTED_PROVIDER_CALLS_WITHOUT_TECHNICAL_FAILURE = 63
  9 DEVLOG final calls + 9 AGENT_DIRECT assignments * 6 action/final calls
```

The exact number of action calls is bounded by eight model turns and six tool
operations. Before each request, the runtime checks remaining observation and
run budget. If the next call would exceed the frozen envelope, it is not made;
the slot is finalized as an infrastructure/non-evaluated failure. Monetary cost
is not fabricated. Actual input/output tokens and provider pricing metadata are
captured when available, otherwise the field remains an explicit missing state.

Efficiency reporting later separates:

```text
inference-only = provider calls/tokens/latency plus condition-specific tool cost
end-to-end = inference-only + DevLog preparation cost or direct infrastructure cost
```

DevLog preparation is measured once per frozen question/context build and
amortized over its three repetitions only in a derived report. The raw artifact
retains both the unamortized preparation record and the allocation rule. If a
projection was prepared before runtime instrumentation, its cost is
`NOT_MEASURED`, never zero.

## 11. Offline Replay

Replay consumes only immutable artifacts and can verify completely offline:

- assignment, question, condition, repetition, benchmark, and repository
  identities;
- provider/model/configuration and schema digests;
- DEVLOG context/projection digests and provider-visible evidence;
- AGENT_DIRECT tool request/result ordering, hashes, revision, and byte budgets;
- prompt/input and raw response hashes;
- raw response parsing and common answer-schema validation;
- reference authorization and deterministic locator resolution against captured
  evidence snapshots;
- Java/Core grounding validation through the evaluation bridge;
- semantic eligibility, failure gates, completeness, and pairing identity;
- immutable artifact and raw output hashes.

Offline replay cannot reproduce provider sampling, hidden model reasoning,
provider-side token accounting when not captured, network latency, or a missing
raw response. It must report those as unavailable rather than simulate them.
Replay must never read the current mutable repository as a substitute for a
captured evidence snapshot.

## 12. Contamination Controls

Preflight and replay fail closed on:

- oracle, expected classification, expected evidence, mapping labels, or
  historical answer markers in condition input;
- AGENT_DIRECT input containing DevLog context, projection, answer, or MCP data;
- DEVLOG runner having a repository path, Git client, shell, or fallback reader;
- wrong repository ID/revision or mutable checkout state;
- tool requests outside the typed allowlist;
- direct tool results containing another condition's response or trace;
- historical V3 artifact references in official baseline assignments;
- provider response references not present in the condition's captured evidence;
- answer fields containing evaluator-only expected values;
- prompt, trace, or artifact values matching secret detectors.

The direct checkout is opened read-only, checked for exact HEAD and clean state,
and isolated from the main worktree. No package installation, web access,
external memory, arbitrary code, or test execution is available to the model.

## 13. Observation Lifecycle and Resume

The lifecycle is:

```text
ASSIGNED
  -> RUNNING
  -> RAW_CAPTURED
  -> STRUCTURALLY_VALIDATED
  -> GROUNDING_VALIDATED
  -> SEMANTICALLY_ELIGIBLE
  -> FINALIZED
```

Alternative terminal states are `INVALID`, `NOT_EVALUATED`, and
`SYSTEMIC_ABORTED`. A response-bearing semantic/grounding failure remains a
captured finalized observation with `semanticOutcome=NOT_EVALUATED` or the
frozen response-bearing taxonomy outcome as appropriate. An infrastructure
failure remains invalid/non-evaluated; it is not a semantic wrong answer.

Assignment identity remains the frozen tuple:

```text
questionId + questionVersion + condition + repetition
```

The runtime adds a run ID and an observation ID, but never changes that tuple.
An assignment ledger is written before execution. Each artifact is write-once
and contains its observation ID, assignment ID, run ID, attempt history, and
artifact hash. Resume behavior is:

- finalized assignment: skip after hash and identity verification;
- running artifact without final marker: retain as partial evidence and mark the
  old attempt interrupted; do not treat it as a complete observation;
- absent assignment: execute once if budget remains;
- existing artifact at the target path: fail closed, never overwrite;
- duplicate assignment or fourth repetition: fail preflight.

If an interruption occurs after a provider call but before finalization, the
captured partial response/trace is retained. A resumed run may execute a new
technical attempt only under the frozen retry policy and must not silently use
the partial answer as the final answer.

## 14. Secret Handling

Secrets exist only in process environment or an external secret provider. They
are never included in manifests, condition input, prompt captures, raw response
artifacts, tool traces, logs, or derived data.

The runtime must:

- reject unsafe configuration before the first provider call;
- redact authorization headers, API keys, bearer tokens, passwords, cookies,
  and credential-like fields from errors and metadata;
- apply recursive key and value secret detection to every artifact candidate;
- fail closed rather than write an artifact if redaction is uncertain;
- write artifacts with restrictive permissions outside Git-tracked source;
- test the final artifact tree for secret patterns before collection closes.

## 15. Data Science Capture Boundary

The raw runtime must capture fields needed later for a DataFrame and paired
analysis without calculating analysis now:

```text
runId, observationId, assignmentId, question/version, condition, repetition
benchmark/oracle/mapping/scoring identities
repository identity/revision
provider/model/configuration/schema identities
execution order/seed
context digest/projection/evidence item count and bytes
DevLog preparation timing and cost state
tool operation count, result bytes, hashes, duration, truncation, errors
provider calls, attempts, finish status, input/output/total tokens
latency and provider cost state
raw output and raw output hash
structural, grounding, semantic eligibility, primary/secondary errors
relationship result, abstention, confidence, claims, references
execution status and completeness state
pairing key and pair status
```

These are capture fields, not permission to add scoring logic or statistical
analysis to the runtime.

## 16. Pedagogical Classification

### `LEARN`

- evaluate whether the common answer contract preserves the frozen scoring
  semantics without introducing hints;
- reason about information-volume fairness and the distinction between
  inference-only and end-to-end cost;
- review failure continuation, missingness, pairing, and replay invariants;
- review the evidence resolver adapter against Core authority.

### `PAIR`

- freeze provider/model/configuration and output envelopes;
- implement the common provider boundary and AGENT_DIRECT tool protocol;
- define canonical tool-result serialization and repository locators;
- implement the run ledger, seeded order, retries, budgets, and raw capture;
- validate DEVLOG/AGENT_DIRECT contamination boundaries;
- review the offline replay and Java grounding bridge.

### `DELEGATE`

- mechanical typed tool dispatch and argument validation;
- canonical JSON serialization, byte counting, and SHA-256 hashing;
- write-once artifact/ledger plumbing;
- deterministic seeded shuffle implementation;
- repetitive schema and budget tests;
- secret-pattern test fixtures and filesystem permission checks.

## 17. Smallest Follow-up Story

Runtime implementation and analytics must be separate Stories.

The smallest safe next Story is:

```text
STORY = Comparative Baseline Collection Runtime
SCOPE = evaluation-only execution of the frozen 18-assignment Story0133 matrix
CONDITIONS = DEVLOG and AGENT_DIRECT only
OUTPUT = immutable raw observations plus offline replay/validation
EXCLUDED = data collection authorization, statistical analysis, production code,
           production semantics, RAG, ML, Story0132 reopening
```

Its acceptance criteria should require preflight identity checks, the common
answer contract, bounded direct tools, exact context/tool accounting, raw
capture before parsing, technical-only retries, slot-local failure continuation,
deterministic order/isolation, budget fail-closed behavior, immutable resume,
secret exclusion, Core grounding validation, and offline replay. A separate
later Story should own the derived dataset and Data Science analysis.

## Required Design Report

```text
PHASE = COMPARATIVE_BASELINE_COLLECTION_RUNTIME_DESIGN
MODE = PAIR
STATUS = COMPLETE

PROTOCOL_V1_FROZEN = YES

PROVIDER_CALLS = 0
DATA_COLLECTION = 0
IMPLEMENTATION_PERFORMED = NO

RUNTIME_ARCHITECTURE = evaluation-only file-backed orchestrator with isolated
  DEVLOG projection and AGENT_DIRECT tool runners, common provider/capture
  boundary, immutable artifacts, and offline replay.

DEVLOG_RUNTIME_STRATEGY = load and hash frozen context projection; send only
  policy-permitted evidence; use common final-answer contract; record context,
  prompt, token, latency, and preparation-cost identities.

AGENT_DIRECT_RUNTIME_STRATEGY = autonomous bounded read-only tool loop over the
  pinned revision; no preselected expected evidence; six operations, eight model
  turns, question-specific cumulative byte budget, common final answer.

COMMON_MODEL_REQUIRED = YES
COMMON_ANSWER_CONTRACT_DEFINED = YES
COMMON_GROUNDING_AUTHORITY = Java Core TaskSnapshotEvidenceResolver/evaluation
  bridge through a condition-neutral captured-evidence adapter.

DESIGN_C_REUSE = projection and hashing WITH ADAPTER; structured capture WITH
  ADAPTER; protocol helpers UNCHANGED where compatible; live smoke runner NOT
  REUSABLE; production provider NOT REUSABLE.

AGENT_DIRECT_MAX_TOOL_OPERATIONS = 6
AGENT_DIRECT_MAX_MODEL_TURNS = 8 response-bearing turns
AGENT_DIRECT_MAX_REPOSITORY_BYTES = question-specific DEVLOG provider-visible
  budget: approximately CASE-01 32401, CASE-03 31683, CASE-04 13504 bytes.

DEVLOG_CONTEXT_ACCOUNTING = projection/context identity, item count, exact UTF-8
  bytes, per-item hashes, prompt bytes/tokens, prompt/context digests.
DEVLOG_PRECOMPUTATION_COST_ACCOUNTING = separate unamortized preparation record,
  then explicitly amortized across three repetitions; unknown historical cost is
  NOT_MEASURED, never zero.

RAW_CAPTURE_BOUNDARY = complete SDK response/tool request/result capture before
  parsing, repair, grounding, or semantic interpretation.

TECHNICAL_RETRY_POLICY = at most one clean complete-observation retry for
  transport/provider execution failure before any response-bearing output.
SEMANTIC_RETRY_POLICY = DISABLED; no corrective retry, answer selection, or
  excerpt repair.
SYSTEMIC_FAILURE_POLICY = stop before calls for preflight failures; stop the
  run for systemic unavailable infrastructure; preserve completed artifacts.
SLOT_LOCAL_FAILURE_POLICY = invalidate only the current slot and continue later
  assignments for model, tool, structural, grounding, semantic, or exhausted
  slot-local budget failures.

EXECUTION_ORDER_POLICY = frozen seeded constrained shuffle of all 18 assignments;
  seed and generated order are captured before execution.
REPETITION_ISOLATION = fresh provider conversation, tool session, input, and
  trace for every assignment; no cross-condition/question/repetition state.

MODEL_CONFIGURATION_FIELDS_FROZEN = provider, model/version, temperature, top_p,
  seed/reasoning, final/intermediate limits, timeout, SDK/API mode, schema/tool
  digests, SDK retries, technical retry, semantic retry.

FINAL_OUTPUT_TOKEN_ENVELOPE = 1800 design target, to be preflighted offline
AGENT_INTERMEDIATE_TOKEN_ENVELOPE = 512
MAX_PROVIDER_CALLS_PER_DEVLOG_OBSERVATION = 2
MAX_PROVIDER_CALLS_PER_AGENT_DIRECT_OBSERVATION = 9
MAX_PROVIDER_CALLS_FULL_BASELINE = 99

OFFLINE_REPLAY_COMPLETE = PARTIAL
NON_REPLAYABLE_ELEMENTS = provider sampling/hidden reasoning, unreported provider
  usage, network latency, and absent raw responses.

CONTAMINATION_GUARDS_DEFINED = YES
OBSERVATION_STATE_MACHINE_DEFINED = YES
RESUME_IDEMPOTENCY_DEFINED = YES
SECRET_PERSISTENCE_GUARDS_DEFINED = YES
DATA_SCIENCE_FIELDS_CAPTURED = YES

LEARN_TASKS = scoring-preserving contract review, information-volume fairness,
  cost boundaries, failure/missingness/replay invariants, Core adapter review.
PAIR_TASKS = provider/tool boundary, configuration/envelopes, canonical locators,
  capture, budgets, ordering, isolation, replay, contamination, Core bridge.
DELEGATE_TASKS = typed dispatch, serialization/hashing, write-once ledger,
  deterministic shuffle, repetitive tests, secret fixture checks.

NEXT_IMPLEMENTATION_STORY_RECOMMENDED = YES
PRODUCTION_CODE_CHANGE_REQUIRED = NO
RAG_REQUIRED = NO
ML_REQUIRED = NO
COMMIT = NO
PUSH = NO
MERGE = NO
```

## Explicit Answers

1. DEVLOG receives frozen prepared context; AGENT_DIRECT autonomously acquires
   bounded repository evidence through typed read-only tools. AGENT_DIRECT also
   has intermediate tool calls and direct infrastructure cost.
2. Provider, model, final generation settings, question, output contract,
   revision, evaluator, grounding authority, oracle, and scoring identity stay
   identical.
3. AGENT_DIRECT is fair because it sees the same question and pinned revision,
   chooses evidence itself, has no expected-evidence hint, and has a measured
   byte/call budget rather than a complete repository dump.
4. It is autonomous. Preselected evidence would erase the context-access
   comparison and make the direct condition a replay of DEVLOG.
5. Typed file read, repository search, Git log, Git show, Git diff, and commit
   inspection only.
6. Six operations, justified by the search/inspect/commit pattern and bounded
   by eight model turns and a per-question byte budget.
7. At most the corresponding frozen DEVLOG provider-visible byte budget,
   approximately 32,401/31,683/13,504 bytes for CASE-01/03/04.
8. Every operation records index, type, target/query, revision, result identity,
   bytes, SHA-256, duration, truncation, and error state.
9. Capture exact evidence item count/bytes/hashes, context/prompt digests, input
   bytes/tokens, and preparation versus online timing separately.
10. Measure preparation as its own run artifact, retain unamortized cost, and
    amortize only in a later derived report; unknown historical cost is missing.
11. The bounded common JSON contract contains question identity, answer text,
    relationship result, abstention, claims, canonical evidence locators and
    exact excerpts, and confidence.
12. Yes, through an evaluation-only captured-evidence adapter; Java/Core remains
    the single deterministic grounding authority.
13. Capture the complete SDK response immediately on receipt, before parsing or
    validation; capture tool request/results in the same raw boundary.
14. Retry one complete observation only for transport failure before any
    response-bearing output; restart cleanly and retain both attempt records.
15. Preflight identity/config/secret/sandbox failures and systemic unavailable
    infrastructure stop the run before unsafe or unbounded execution.
16. Tool, model, structural, grounding, semantic, and slot-budget failures affect
    only the current slot and produce a terminal non-eligible observation.
17. Yes. Unrelated assignments continue after a response-bearing grounding or
    model failure.
18. Use a frozen seeded constrained shuffle; record the seed and generated order.
19. Fresh provider conversation, tool session, condition input, and trace per
    assignment; no state crosses any boundary.
20. Provider/model/version, temperature, top-p, seed/reasoning, token limits,
    timeout, SDK/API mode, schema/tool digests, retries, and semantic retry mode.
21. An 1,800-token final envelope is the smallest current design target; offline
    maximum-contract preflight must pass before it is frozen.
22. 512 tokens for the strict tool-action envelope.
23. 2 DEVLOG calls, 9 AGENT_DIRECT calls, and 99 for the complete baseline at
    maximum technical retries; expected no-failure total is 63.
24. Check budget before every call and finalize the slot/run as infrastructure
    non-evaluated without making an over-budget request.
25. All identities, snapshots, traces, hashes, parsing, locator resolution,
    grounding, eligibility, pairing, and completeness can be replayed.
26. Provider sampling/hidden reasoning, unreported usage, network latency, and
    missing raw responses cannot be reconstructed.
27. Forbidden marker scans, no oracle fields in condition input, no expected
    evidence, and strict manifest/reference identity checks fail closed.
28. Separate runners and sessions, DEVLOG no repository capability, direct no
    DevLog capability, pinned read-only checkout, and no cross-condition state.
29. Write raw capture first, derive validation afterward, add terminal status and
    artifact hash, then refuse overwrite.
30. Prewritten assignment ledger, immutable assignment/observation IDs, final
    hash verification, partial-attempt retention, and no overwrite or fourth
    repetition.
31. Environment/external secret provider only, recursive redaction/detection,
    restrictive artifact permissions, and fail-closed writes.
32. Identity, order, condition, context volume, tool operations, tokens, calls,
    latency, cost state, raw output, hashes, errors, eligibility, confidence,
    references, pairing, and completeness fields.
33. Ludovic should learn contract/fairness, cost, failure, replay, and grounding
    decisions; pair on boundaries and invariants; delegate mechanical plumbing,
    serialization, hashing, dispatch, and repetitive tests.
34. Yes. Runtime collection and derived dataset/Data Science analysis have
    different authorization, evidence, and verification boundaries.
35. Implement the evaluation-only `Comparative Baseline Collection Runtime`
    Story, limited to the frozen 18 raw observations and offline replay.

## Stop Boundary

```text
PROVIDER_CALLS = 0
ASSIGNMENTS_EXECUTED = 0
OBSERVATIONS_CREATED = 0
FROZEN_PROTOCOL_MODIFIED = NO
PRODUCTION_CODE_CHANGED = NO
PRODUCTION_SEMANTICS_CHANGED = NO
RAG_INTRODUCED = NO
ML_INTRODUCED = NO
COMMIT = NO
PUSH = NO
MERGE = NO
```

The next recommended implementation phase is the separately authorized
`Comparative Baseline Collection Runtime` Story. No collection or runtime
implementation follows automatically from this design.
