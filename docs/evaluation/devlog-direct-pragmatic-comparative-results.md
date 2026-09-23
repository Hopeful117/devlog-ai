# DevLog vs DIRECT Pragmatic Comparative Results

## Status

This report preserves the historical smoke results and the subsequent
authorized collection attempt. The authorized collection stopped after the
first DIRECT slot failed before producing an answer; no retry was performed.

No historical V3/V4 treatment, manifest, identity, staged artifact, question,
oracle, repository revision, or prior report was modified. No new runtime,
protocol, provider-schema workaround, or treatment version was created.

## Objective

The intended comparison remains:

```text
same engineering question
DEVLOG structured context vs independent repository reconstruction
PRIMARY   = answer quality
SECONDARY = token cost
```

The frozen assets were reused:

```text
questions = CASE-01-COMPARATIVE, CASE-03, CASE-04
question version = 1.0.0
repository revision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
model requested for DEVLOG = gpt-4.1-mini
oracle/evaluator contracts = existing comparative assets
```

## Smoke Scope

The requested smoke was:

```text
DEVLOG  CASE-04@1.0.0  repetition=smoke
DIRECT  CASE-04@1.0.0  repetition=smoke
```

DEVLOG used the existing frozen context adapter and existing structured answer
schema/evaluators. DIRECT used the already-installed OpenCode CLI (`1.18.31`)
against an isolated detached worktree at the frozen repository revision. The
DIRECT prompt explicitly prohibited DevLog context, oracle, expected answer,
evaluator hints, web, external memory, writes, and side effects.

## DEVLOG Smoke Result

Execution succeeded:

```text
observationId = pragmatic-smoke-devlog:smoke:CASE-04:DEVLOG
condition = DEVLOG
questionId = CASE-04
questionVersion = 1.0.0
executionStatus = COMPLETED
model = gpt-4.1-mini
repositoryRevision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
providerTransportAttempts = 1
inputTokens = 3993
outputTokens = 740
totalTokens = 4733
```

Quality dimensions:

```text
structuralValid = YES
semanticEvaluated = YES
semanticCorrect = true
groundingValid = FAIL
correctGroundedAnswer = false
```

The answer's semantic result was `NOT_ESTABLISHED`, matching the frozen
semantic oracle for CASE-04. The grounding evaluator rejected the response's
line-range representation because its locators did not contain the required
path/commit-resolvable information. This is a useful quality observation, not
an execution failure.

The answer produced was materially usable and is retained here as a smoke
record:

> The provided evidence does not explicitly state that ADR-043 caused the
> specific ExecutionConfiguration refactor delivered by Story 0042. ADR-043
> outlines architectural decisions about account identity and facts-source
> architecture, while Story 0042 focuses on implementing a paper local full
> exit feature with specific execution and settlement logic. The
> `ExecutionConfiguration.java` file shows configuration relevant to execution
> services, and the linked Story 0042 commit references the implementation of
> paper local full exit, but there is no direct evidence linking ADR-043 as the
> cause of this specific refactor.

Referenced evidence remained within the frozen DEVLOG context. No DIRECT
material entered the DEVLOG request.

## DIRECT Smoke Result

The single Luna execution completed repository inspection and produced an
answer:

```text
agent = OpenCode 1.18.31 (default-agent fallback; requested repository-capable agent was unavailable)
requested model = openai/gpt-5.6-luna
repository access = isolated detached worktree
provider response = COMPLETED
repository tool/action calls = completed read-only inspection
answer = PRODUCED
reported final-step usage = 50225 total / 1341 input / 244 output / 0 reasoning / 48640 cache-read
```

The answer was semantically correct and grounded in the frozen repository. Luna
concluded that ADR-043 did not cause the specific `ExecutionConfiguration`
refactor; the change was driven by Story 0042's PnL-enabled local PAPER EXIT
settlement, while ADR-043 influenced related identity checks.

The answer cited the relevant Story 0042 implementation report, repository
analysis, ADR-043, commit `da84a19`, and the changed Java services/configuration.
The output also included trustworthy OpenCode usage metadata, although this
single smoke is not a comparable DEVLOG-vs-DIRECT cost observation because the
OpenCode usage includes multi-step tool-calling/cache accounting.

The OpenCode invocation was not retried and no alternate model/provider/runtime
was introduced. Exactly one Luna model execution was performed.

## Collection Decision

The bounded Luna smoke produced an answer and exposed usage metadata, so the
DIRECT execution path is no longer blocked at the basic feasibility level.
However, this does not authorize the planned comparative collection. Per the
explicit stop rule for this task:

- no CASE-01 collection;
- no CASE-03 collection;
- no three-repetition collection;
- no 18-observation collection;
- no paired quality analysis;
- no token comparison;
- no chart or notebook update;
- no DEVLOG rerun;
- no additional DIRECT provider attempt.

The task therefore has no comparative dataset and no product conclusion.

## Single Concrete Blocker

The requested repository-capable `devlog-readonly-pilot` agent was unavailable,
so OpenCode fell back to its default agent. Luna nevertheless completed the
bounded read-only smoke. A human decision is still required before choosing
whether this fallback is acceptable for the planned comparative collection.

## Historical Preservation

Preserved unchanged:

- `AGENT_DIRECT_OPEN` treatment and artifacts;
- `AGENT_DIRECT_STAGED` archived blocked result;
- frozen questions, oracle, repository revision, and evaluator contracts;
- existing V3/V4 observations and reports;
- current active branch state and unrelated worktree changes.

## Historical Next Human Decision

Authorize or reject the default-agent fallback as the DIRECT runtime for any
future comparison. Do not authorize the 18-slot collection until the DEVLOG
and DIRECT protocols, agent identity, and token accounting are explicitly
approved as comparable.

PRAGMATIC_LUNA_DIRECT_SMOKE_COMPLETED
NO_COMPARATIVE_COLLECTION_AUTHORIZED
STOP_FOR_HUMAN_DECISION

## Authorized Luna Comparative Collection

The human decision gate was subsequently resolved and the bounded collection
was authorized with the following locked matrix:

```text
model = openai/gpt-5.6-luna
questions = CASE-01-COMPARATIVE@1.0.0, CASE-03@1.0.0, CASE-04@1.0.0
conditions = DEVLOG, DIRECT
repetitions = r1, r2, r3
planned slots = 18
order = CASE-01 r1 DEVLOG, CASE-01 r1 DIRECT, then CASE-03 and CASE-04, repeated for r2 and r3
repository revision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
DIRECT runtime = OpenCode 1.18.31 default-agent fallback
```

The pre-existing frozen comparative assets were not modified. The V4 official
runner was not used because its frozen manifest is pinned to `gpt-4.1-mini` and
its provider runtime is not the approved OpenCode DIRECT treatment. The
existing frozen DEVLOG context/evaluator assets were retained for future use.

### Completion Status

```text
planned slots = 18
completed answers = 0
execution failures = 1
quality-evaluable = 0
grounding-evaluable = 0
token-measurable = 1 (OpenCode step metadata only; no final answer)
```

The first attempted slot was:

```text
observationId = CASE-01-COMPARATIVE:DIRECT:r1
condition = DIRECT
questionId = CASE-01-COMPARATIVE
questionVersion = 1.0.0
repetition = r1
model = openai/gpt-5.6-luna
agent/runtime = OpenCode 1.18.31 default-agent fallback
repositoryRevision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
executionStatus = PROVIDER_TIMEOUT / runtime command timeout
answer = NOT_PRODUCED
```

OpenCode performed read-only repository inspection and then exceeded the
120-second command boundary before emitting a final answer. The raw JSON event
trace is preserved at:

```text
docs/evaluation/luna-comparative-partial/CASE-01-COMPARATIVE-DIRECT-r1.opencodelog.jsonl
```

The trace includes step-level usage metadata, but no final answer and no
comparable completed-observation token record. This is an infrastructure/
execution failure, not an answer-quality failure.

### Stop Decision

The collection was stopped immediately. The failed slot was not retried, and
the remaining DEVLOG or DIRECT slots were not executed. Therefore there are no
quality results, paired observations, error-taxonomy counts beyond the single
infrastructure failure, token comparison, charts, product interpretation, or
article-readiness evidence from this collection.

The existing smoke observations remain historical technical validation only and
are excluded from the 18-slot comparative dataset.

LUNA_COMPARATIVE_COLLECTION_PARTIAL
NO_PROTOCOL_ITERATION_AUTHORIZED
STOP_FOR_HUMAN_DECISION

## Harness Correction and Dataset Restart

The human correction decision is explicit: DIRECT is an observation of natural
repository reconstruction. It is not assigned an experiment-level resource
budget. The previous `120s` external command boundary was therefore removed;
it remains preserved above as historical evidence and is classified as
`HARNESS_INDUCED_EXECUTION_INTERRUPTION`, invalid for comparative quality
analysis.

The DIRECT path was audited for equivalent ceilings. The former wall-clock,
model-turn, provider-call, tool-operation, and delivered-result-byte guards
were artificial experimental limits and were removed. The V4 configuration
now records those values as `null` under `NO_EXPERIMENT_LIMITS`; no replacement
numeric ceiling was introduced. Provider transport timeouts and Git/tool
subprocess timeouts remain because they are native runtime/provider liveness
limits, not experimental budgets. Their failures remain observable and
classified.

The V4 orchestration wrapper no longer adds a second Python timeout around
provider or repository calls. Native adapter failures remain capturable; the
deterministic tests cover normal completion and native timeout/failure paths.

The frozen treatment remains unchanged: OpenCode 1.18.31 default agent,
`openai/gpt-5.6-luna`, read-only access to repository revision
`18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149`, with no DevLog context or hints.
The historical interrupted slot and prior smoke artifacts are excluded. The
official comparative dataset restarts from zero and consists only of the 18
independent cells: CASE-01-COMPARATIVE, CASE-03, and CASE-04, for DEVLOG and
DIRECT, repetitions r1 through r3.

At the time of the harness correction, collection status was `NOT_STARTED`.
The repository's available historical runner was an OpenAI `gpt-4.1-mini` V4
runner, not the locked OpenCode `1.18.31` / `openai/gpt-5.6-luna` DIRECT
treatment. Invoking that runner would have changed the experimental condition,
so no observation was fabricated or automatically substituted. The separate
minimal OpenCode/Luna launcher and final collection are documented below.

## Final Luna/OpenCode Collection

The minimal sequential OpenCode launcher was executed with the authorized
configuration. It used independent CLI sessions, the frozen detached worktree,
the frozen question set, and no launcher timeout or resource ceiling. All 18
slots completed with a captured final answer and raw OpenCode JSONL trace.

```text
model = openai/gpt-5.6-luna
runtime = OpenCode 1.18.31 default agent
repositoryRevision = 18f9d99751ee0da3c56a6f5d75aecb5ddb8fc149
official observations = 18 / 18
execution failures = 0
order = r1, r2, r3; CASE-01, CASE-03, CASE-04; DEVLOG then DIRECT
```

Raw observations and the launcher-produced summary are retained under
`docs/evaluation/luna-comparative-collection/official/`. The
historical smoke and interrupted artifacts above were not reused in these
results.

### Quality Results

The existing common answer evaluator classified every generated answer as a
structural failure (`18 / 18`). The failures were not repaired. DEVLOG answers
commonly emitted evidence locators as strings instead of the required locator
objects; DIRECT answers also exceeded the frozen claim-count contract in
several slots. Consequently semantic evaluation, grounding evaluation, and
correct-grounded evaluation were not eligible for any slot.

The raw answer text nevertheless reported the same high-level relationship
result in every pair:

| Case | DEVLOG | DIRECT | Paired agreement |
|---|---|---|---|
| CASE-01-COMPARATIVE | `NOT_ESTABLISHED` | `NOT_ESTABLISHED` | 3 / 3 |
| CASE-03 | `ESTABLISHED` | `ESTABLISHED` | 3 / 3 |
| CASE-04 | `NOT_ESTABLISHED` | `NOT_ESTABLISHED` | 3 / 3 |

These self-reported labels are descriptive raw-answer observations, not
semantic correctness scores, because the evaluator correctly stopped at the
structural layer.

### Offline Semantic Quality

The structural result above is preserved unchanged. A separate offline
evaluation then read the human-readable answer in each existing observation
and applied the frozen human-approved oracle and CASE-03 impact contract. It
did not invoke Luna, OpenCode, repository tools, or any new evidence source.
The complete per-observation records are in
`docs/evaluation/luna-comparative-collection/official/semantic-quality-evaluation.json`.

| Dimension | DEVLOG | DIRECT |
|---|---:|---:|
| Semantic conclusion correct / evaluable | 6 / 9 | 6 / 9 |
| Causal reasoning correct / evaluable | 6 / 9 | 3 / 9 |
| Reasoning support `SUPPORTED` / 9 | 6 / 9 | 3 / 9 |
| Overclaim `YES` / 9 | 0 / 9 | 3 / 9 |
| Correct abstentions / applicable negative cases | 3 / 3 | 3 / 3 |
| False abstentions / applicable negative cases | 0 / 3 | 0 / 3 |
| Failed abstentions / applicable negative cases | 0 / 3 | 0 / 3 |

Quality categories by condition:

| Condition | Categories |
|---|---|
| DEVLOG | `INCORRECT` 3/9; `CORRECT_REASONED` 3/9; `CORRECT_ABSTENTION` 3/9 |
| DIRECT | `INCORRECT` 3/9; `OVERCLAIM` 3/9; `CORRECT_ABSTENTION` 3/9 |

The CASE-01 oracle expects the ADR-042 to Story-0039 causal relation to be
established. All six answers instead conclude `NOT_ESTABLISHED`, so all six
are semantically incorrect despite their generally coherent supporting facts.
The CASE-03 answers all identify the central affected service and settlement
test and reach the correct impact conclusion. The DEVLOG answers stay within
the frozen impact evidence; the DIRECT answers add plausible but
not-frozen-supported components and tests, so they are classified as
overclaiming with only partially supported reasoning. The CASE-04 negative
control is correctly abstained from by all six answers.

Paired comparison, by question and repetition:

| Pair group | Repetitions | Classification |
|---|---:|---|
| CASE-01-COMPARATIVE | 3/3 | `BOTH_INCORRECT` |
| CASE-03 | 3/3 | `DEVLOG_BETTER_SUPPORTED` |
| CASE-04 | 3/3 | `BOTH_FULLY_CORRECT` |

The raw 9/9 conclusion agreement therefore decomposes into six shared wrong
CASE-01 conclusions, three shared correct CASE-03 conclusions, and three
shared correct CASE-04 abstentions per condition. Agreement is not treated as
ground truth.

On the primary question, the semantic evidence favors DEVLOG for this sample's
reasoning quality: conclusions are tied on 6/9, but DEVLOG has fully supported
reasoning on 6/9 versus 3/9 for DIRECT and has no observed overclaims versus
3/9 for DIRECT. This is a descriptive sample result, not a numeric benchmark
score and not evidence that structured context corrected the CASE-01 causal
error.

### Resource Results

OpenCode usage includes cache-read and tool-workflow accounting; it is not
treated as directly equivalent to provider-only DEVLOG accounting.

| Condition | Total tokens median (range) | Input median (range) | Output median (range) | Reasoning median (range) | Cache-read median (range) | Elapsed median (range) |
|---|---:|---:|---:|---:|---:|---:|
| DEVLOG | 22,661 (17,661–22,759) | 21,621 (4,310–21,705) | 707 (386–891) | 217 (132–380) | 0 (0–12,800) | 26.5s (20.5–32.6s) |
| DIRECT | 176,443 (106,887–321,894) | 46,422 (30,880–73,845) | 1,712 (1,517–3,078) | 732 (613–915) | 122,368 (73,728–244,224) | 72.6s (55.8–93.6s) |

DIRECT consumed substantially more observed workflow and cache-read tokens and
took longer. The independent semantic evaluation favors DEVLOG's supported
reasoning, while both conditions have the same semantic conclusion accuracy in
this sample. This is a descriptive resource result, not a composite score or
winner.

### Interpretation

For this 18-slot sample, the semantic evidence favors structured DevLog context
for reasoning support and restraint: DEVLOG has 6/9 fully supported reasoning
records and no overclaims, versus 3/9 and 3/9 for DIRECT. Semantic conclusion
accuracy is tied at 6/9, and both conditions correctly handle the three negative
control repetitions. The dominant format failure remains the shared answer
contract mismatch, which is separate from semantic quality. DIRECT showed the
larger resource and elapsed-time cost, consistent with more independent
repository reconstruction.

The single most valuable DevLog improvement suggested by these data is to make
the frozen answer contract executable at the generation boundary without
changing the evidence or evaluator: specifically, ensure locator objects and
claim cardinality are emitted exactly as contracted. This is a follow-up
product issue, not changed or rerun during this experiment.

The evidence supports a bounded article claim that DevLog produced better
supported and less overclaiming reasoning in this 18-slot sample, but does not
support a universal quality claim: both conditions made the same CASE-01 error,
and all 18 answers failed the strict structural contract. Human review remains
required before product correction or rerun.

## GPT-4.1-mini Model-Effect Collection

The additional model-effect collection contains exactly nine sequential
`DEVLOG_BASELINE_MODEL` observations using `gpt-4.1-mini`. It reused the
persisted historical `exactInput` projections for the frozen DevLog context;
the context digests, projection revisions, evidence counts, question versions,
and repository revision were verified before execution. OpenCode was not
invoked.

The first technical attempt used a 30-second V4 provider timeout and produced
six timeout diagnostics. Those artifacts are retained separately and excluded
from the final model-effect dataset. The final nine-slot collection used the
historical 90-second provider timeout and completed all nine slots.

| Dimension | GPT-4.1-mini + DEVLOG | Luna + DEVLOG |
|---|---:|---:|
| Semantic conclusion correct / evaluable | 9 / 9 | 6 / 9 |
| Causal reasoning correct / evaluable | 9 / 9 | 6 / 9 |
| Reasoning support `SUPPORTED` / 9 | 9 / 9 | 6 / 9 |
| Overclaim `YES` / 9 | 0 / 9 | 0 / 9 |
| Correct abstentions / applicable negative cases | 3 / 3 | 3 / 3 |

GPT-4.1-mini established CASE-01 in all three repetitions, while Luna
returned the incorrect `NOT_ESTABLISHED` conclusion in all three. Both models
were correct on CASE-03 and CASE-04 under the offline semantic rubric. The
observed model effect is therefore material on this frozen sample, especially
for CASE-01, but it is not a formal model ranking or a universal capability
claim.

The GPT semantic records are retained under
`docs/evaluation/gpt-baseline-collection/official-90s/semantic-quality-evaluation.json`.
The human-readable consolidated notebook is
`notebooks/evaluation/devlog-comparative-analysis.ipynb`.

Measured resource medians for the final model-effect collection were
approximately 8,460 input tokens, 763 output tokens, 9,101 total tokens, and
7.6 seconds elapsed. Reasoning and cache-read categories were not exposed on
the GPT provider path and are reported as not measured. Luna DEVLOG medians
were approximately 21,621 input, 707 output, 22,661 total observed tokens,
and 26.5 seconds. These resource values remain descriptive: Luna DEVLOG was
collected through OpenCode, while GPT used the historical provider transport.

LUNA_COMPARATIVE_COLLECTION_COMPLETED
QUALITY_RESULTS_AVAILABLE
TOKEN_RESULTS_AVAILABLE
ARTICLE_READINESS_EVALUATED
READY_FOR_HUMAN_ANALYSIS_REVIEW
STOP
