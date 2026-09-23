# Comparative Baseline V4 DIRECT Tool Failure Diagnosis

## Scope

Offline forensic analysis only. No provider calls, network calls, new
observations, replay provider calls, artifact rewrites, or runtime changes were
made.

Source attempt:
`corrected-v4-minimal-live-validation-20260918T000000Z`

Source RAW:
`ai-engine/data/comparative-baseline-v4/live-pilot/corrected-v4-minimal-live-validation-20260918T000000Z/artifacts/CASE-03:AGENT_DIRECT_OPEN:r1.json`

Source DERIVED:
`ai-engine/data/comparative-baseline-v4/live-pilot/corrected-v4-minimal-live-validation-20260918T000000Z/derived/CASE-03:AGENT_DIRECT_OPEN:r1.json`

## Authoritative Accounting

| Metric | Count |
| --- | ---: |
| Tool attempts | 72 |
| Executed valid operations | 22 |
| Invalid requests | 50 |
| Skipped requests | 0 |
| Searches | 18 |
| Reads | 4 |
| Model turns/provider calls | 48/48 |
| Final-answer calls | 0 |
| Result bytes produced/delivered | 93,671 |

The reported `72/22/50` accounting is correct. The independent sum is
`22 + 50 = 72`; RAW hashes, wrapper hash, and DERIVED linkage also pass.

## Complete Observable Trajectory

The RAW contains 48 provider responses, 121 conversation entries, and 72
ordered tool traces. The ordered attempt sequence, using `E` for executed
valid, `I-A` for invalid arguments object, and `I-O` for invalid operation, is:

```text
1 E-search 2 E-search 3 E-search 4 E-read
5 I-A-search 6 I-A-search 7 I-A-search 8 I-A-search
9 E-search 10 E-search
11 I-A-read 12 I-A-search 13 I-A-search 14 I-A-search 15 I-A-search 16 I-A-search 17 I-A-read
18 I-O 19 I-A-read 20 I-A-search 21 I-A-search 22 I-A-search 23 I-A-read 24 I-A-read 25 I-A-read
26 E-search 27 I-O 28 I-A-search 29 I-A-search 30 I-A-read 31 E-read
32 I-A-search 33 I-A-search 34 I-A-search 35 I-A-search 36 E-search
37 I-O 38 I-O 39 I-A-read 40 I-A-read 41 E-read
42 I-O 43 I-A-read 44 E-read 45 I-O
46 E-search 47 I-O 48 E-search 49 I-A-read 50 I-A-read
51 E-search 52 I-O 53 E-search 54 I-A-read 55 I-A-read 56 E-search
57 I-A-search 58 I-O 59 E-search 60 E-search
61 I-A-read 62 I-A-read 63 I-A-read 64 I-A-read 65 I-O
66 E-search 67 I-O 68 E-search 69 I-O 70 E-search 71 I-A-search 72 E-search
```

For each invalid trace, the runtime appended a deterministic error tool
message. Successful results were appended as tool messages and remained in the
conversation. There was no final response, runtime exception, tool timeout,
result rejection, or skipped operation.

## Invalid Taxonomy

| Category | Count | Evidence |
| --- | ---: | --- |
| `SCHEMA_VALIDATION_FAILURE: arguments_not_object` | 40 | Provider payload had `operation` plus top-level `query`/`path`; nested `arguments` was absent. Runtime error: `tool arguments must be an object`. |
| `SCHEMA_VALIDATION_FAILURE: operation_not_string` | 10 | Payload used `tool_uses` or only `query/path`, with no string operation. Runtime error: `tool operation must be a string`. |
| Total invalid | 50 | All were `NOT_EXECUTED_INVALID_REQUEST`. |

No demonstrated malformed JSON, unsupported operation name, invalid path,
invalid range, repository capability failure, normalization failure, or tool
implementation failure occurred. The dominant category was
`arguments_not_object` (80% of invalid requests; 55.6% of all attempts).

Responsibility is split: the malformed shapes are present in provider/model
output (`MODEL_OUTPUT`), while the missing operation-specific schema detail is
a `TOOL_CONTRACT` weakness. No evidence shows that the adapter transformed a
valid request into an invalid one.

## First Invalid Request

The first invalid request was attempt/turn 5, after four successful operations
(three searches and one read). Its observable payload was:

```json
{"operation":"search_repository","query":"PAPER settlement"}
```

It omitted the required nested `arguments` object and received
`tool arguments must be an object`. The next request corrected the envelope
shape, but later turns repeatedly returned to the same top-level form. This was
not an isolated failure.

## Repetition and Recovery

- Same primary malformed shape: 40 requests.
- Missing/non-string operation shape: 10 requests.
- Longest invalid runs: four attempts at 5-8, 32-35, and 37-40.
- Invalid requests after a deterministic error: repeated throughout the trace;
  many were the same top-level `operation` plus top-level argument shape.
- Recovery classification: `RECOVERED` for valid operations following an error,
  `REPEATED_SAME_ERROR` for repeated `arguments_not_object` or
  `operation_not_string` failures, and `MUTATED_ERROR` where the model switched
  between those two malformed shapes.
- Immediate next-trace recovery classification: `RECOVERED=16`,
  `REPEATED_SAME_ERROR=30`, `MUTATED_ERROR=4`, `ABANDONED_PATH=0`,
  `UNKNOWN=0`. This is a deterministic trace-based classification, not a claim
  about hidden model intent. The other six valid operations were separated from
  an immediately preceding invalid request by another event.

The runtime did not repair arguments or silently retry. Policy A feedback was
specific for the two demonstrated defects and was propagated into the next
conversation state. The model sometimes corrected the envelope, but repeatedly
regressed to malformed forms.

## Schema and Runtime Parity

The provider-visible schema was:

```json
{
  "type":"function",
  "name":"repository_tool",
  "description":"Execute one allowed read-only repository operation.",
  "parameters": {
    "type":"object",
    "additionalProperties":false,
    "properties": {
      "operation": {"type":"string","enum":["read_file","search_repository","git_log","git_show","git_diff","inspect_commit"]},
      "arguments": {"type":"object","additionalProperties":true}
    },
    "required":["operation","arguments"]
  },
  "strict":false
}
```

The internal runtime validator additionally requires `path` for `read_file`,
`query` for `search_repository`, and `commit` for the Git operations. Those
operation-specific requirements are not expressed in the provider-visible
schema or description. Therefore a provider-visible call with
`{"operation":"search_repository","arguments":{}}` can satisfy the
published schema while being rejected by runtime validation.

`SCHEMA_RUNTIME_PARITY = FAIL`.

This is a demonstrated contract deficiency, not proof that it caused every
malformed request: the observed top-level envelope violations also contradict
the published required `arguments` property.

## Temporal Phases

| Phase | Attempts | Valid | Invalid | Searches | Reads | Observable interpretation |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Initial productive exploration | 1-4 | 4 | 0 | 3 | 1 | Useful initial discovery and first report read. |
| First schema failure | 5-8 | 0 | 4 | 0 | 0 | Four malformed top-level argument requests. |
| Intermittent recovery | 9-17 | 2 | 7 | 2 | 0 | Some valid searches, malformed calls dominate. |
| Repository targeting | 18-31 | 2 | 12 | 1 | 1 | Target service and ADR paths reached, but malformed navigation persists. |
| Late mixed progress | 32-48 | 5 | 12 | 3 | 2 | Some evidence reads/searches, no synthesis attempt. |
| Runaway phase | 49-72 | 9 | 15 | 9 | 0 | Repeated malformed calls interleaved with broad searches until turn guard. |

The search/read ratio is not sufficient to call the exploration unreasonable.
The actual sequence shows initial useful discovery, but later searches often
repeated already-used queries and were interleaved with malformed calls rather
than converging toward synthesis.

## Evidence Acquired and Progress

Useful observable evidence included:

- `AUTONOMOUS_SESSION_REPORT.md` read early and again late.
- `docs/architecture/adr/ADR-042.md` searched/read repeatedly.
- `trading-core/.../PaperSettlementService.java` found and read in ranges.
- Related implementation reports and story documents under the Paper settlement
  and persisted paper execution regression paths.

The model also executed 18 searches and 4 reads successfully, so DIRECT did
acquire repository evidence. However, the late trajectory repeated broad or
near-duplicate searches, revisited files, emitted malformed batches, and never
emitted a final answer. Observable progress is `PARTIAL_PROGRESS`, tending to
`STALLED` in the late phases. There is no observable evidence of an imminent
final answer: `EVIDENCE_OF_IMMINENT_FINAL_ANSWER = NO`.

## Context and Conversation

- Input tokens: 682,297 across 48 calls.
- Input grew from 516 tokens on turn 1 to 22,654 on turn 48.
- Serialized request bytes: 3,234,348.
- Conversation entries: 121, consisting of one user message, 48 assistant
  tool-call messages, and 72 tool result/error messages.
- 50 deterministic errors persisted in the conversation.

The implementation sends the accumulated conversation on every subsequent
provider request, preserves successful results and errors in order, and pairs
tool outputs with the generated call IDs. No duplicate stale request or
mispaired result was found in RAW. `CONVERSATION_PROPAGATION = PASS` based on
RAW structure, implementation inspection, and existing propagation tests.

The high context growth is temporally associated with degradation, but the
trace cannot establish that context growth caused it. Repeated malformed
requests and repeated search results are plausible contributors.

## Provider Normalization and Accounting

`PROVIDER_NORMALIZATION = PASS` for this observation. Provider function-call
arguments were normalized to dictionaries; no historical wrong-type assumption
or adapter conversion of a valid request into an invalid request was found.
The invalid shapes remained visible as provider response payloads and were
rejected by the runtime validator.

`TOOL_ACCOUNTING = PASS`:

- Every trace is either executed valid or invalid.
- No trace is both invalid and executed.
- No trace is skipped.
- `22 + 50 = 72` exactly.
- Resource counters match the trace and replayed deterministic accounting.

## Guard Interaction

The exact termination was `RUNAWAY_GUARD_MODEL_TURNS` at model turn 48.
Provider calls were also 48. Headroom at termination was:

- Tool attempts: 24 of 96 remaining.
- Model turns: 0.
- Provider calls: 0.
- Wall clock: approximately 139,962 ms of 300,000 remaining.
- Largest result: 39,296 of 65,536 bytes allowed.

The historical 12-operation boundary was exceeded and was not the termination
mechanism. The emergency guard acted in this observation, but the trace does
not prove whether its model-turn limit was a necessary technical protection or
an overly restrictive corrected policy.

## Causal Classification

Primary counterfactual classification:
`A. INVALID_REQUESTS_LIKELY_MATERIAL_TO_NON_COMPLETION`.

Evidence threshold: this is a likelihood diagnosis, not a counterfactual proof.
Fifty invalid interactions generated deterministic feedback, consumed most
turns, repeatedly displaced valid evidence work, and the run ended with no
final call. The trace therefore supports material contribution, while not
proving that eliminating invalid calls would have produced a final answer.

Primary root-cause classification:
`MULTIFACTOR_INTERACTION`.

The strongest supported combination is: provider/model output repeatedly
violated the envelope, the provider-visible schema omitted operation-specific
argument requirements, Policy A feedback allowed recovery but did not produce
convergence, and the growing conversation amplified the interaction. This is
not classified as an adapter/runtime implementation defect.

Confidence: `MEDIUM` for the invalid taxonomy and accounting; `LOW-MEDIUM` for
causal materiality and root cause because no counterfactual run is authorized.

## Conclusions Not Supported

The evidence does not establish that 96 tools or 48 turns are sufficient or
insufficient, that DIRECT is inefficient, that DEVLOG is better, that a higher
guard would solve the issue, that the provider SDK is defective, or that the
model would eventually produce a correct answer with more time.

## Remediation Options, Not Implemented

Smallest options for human review:

1. Clarify the provider-visible schema with operation-specific argument
   structures and required fields while preserving Policy A.
2. Improve deterministic errors to state the expected nested shape and required
   field without repairing the request.
3. Evaluate tool-contract simplification or model suitability separately.
4. Only if further evidence requires it, investigate bounded deterministic
   recovery; do not silently add argument repair.

No option was implemented.

## Verification and Readiness

- RAW/DERIVED integrity: `PASS`.
- Deterministic replay integrity: `PASS`, with zero provider/network replay
  calls.
- Contamination of condition inputs: `PASS`.
- Condition isolation: `PASS`.
- Focused comparative tests: `64 passed`.
- Compilation and `git diff --check`: `PASS`.
- Analysis-time provider calls: `0`.
- Analysis-time network calls: `0`.
- Files modified by this diagnosis: this report only. Runtime and artifacts were
  not modified.

`READY_FOR_HUMAN_REVIEW_MULTIFACTOR_DIAGNOSIS`

Exact next human decision: decide whether to authorize a tool-schema/feedback
remediation investigation, a model/tool-use suitability evaluation, or no
change. Do not authorize another live observation until that decision is made.
