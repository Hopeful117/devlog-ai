# DevLog Comparative Baseline Protocol Design

## Status

```text
PHASE = DEVLOG_COMPARATIVE_BASELINE_PROTOCOL_DESIGN
MODE = PAIR
STATUS = COMPLETE
NEW_PROVIDER_CALLS = 0
IMPLEMENTATION_AUTHORIZED = NO
STORY_0132_REOPENED = NO
NEW_ENGINEERING_STORY_REQUIRED = NOT_YET_DECIDED
```

This is a design document only. It does not collect observations, call a
provider, change production code, reopen Story0132, or create Story0133.
Story0132 remains closed. Its V3 artifacts are historical technical
infrastructure and are reused only where their identities and limitations are
explicitly recorded.

## Research Question

The exact product question is:

> Does DevLog help an AI agent or a human understand project history,
> architectural decisions, causal relationships, and implementation evolution
> more effectively than reading the repository directly?

The protocol is deliberately symmetric. Negative, neutral, and mixed results
are valid outcomes. No composite score or threshold declares DevLog superior by
construction.

## Hypotheses

- `H1`: DevLog changes causal-answer accuracy relative to direct repository access.
- `H2`: DevLog changes grounding validity relative to direct repository access.
- `H3`: DevLog changes the context volume required to answer.
- `H4`: DevLog changes latency or time-to-answer.
- `H5`: DevLog changes unsupported or fabricated claim rates.
- `H6`: DevLog changes correct abstention behavior.
- `H7`: DevLog changes cost per valid and correct AI answer.
- `H8`: DevLog may reduce human reconstruction effort without reducing correctness.

These are hypotheses, not expected wins. Each is reported with denominators,
missingness, and condition policy identity.

## Conditions

The initial AI comparison contains exactly two conditions:

### `DEVLOG`

The answering agent receives only the frozen DevLog evaluation path for the
question: the authorized context selection, provider-visible projection,
prompt/input representation, and stable DevLog context metadata defined by the
baseline manifest. It receives no direct repository access, no shell, no Git,
no oracle, no expected answer, and no AGENT_DIRECT output.

The path must record the exact DevLog version, context strategy, projection
identities, context digest, visible evidence bytes/items, and warnings. A
hybrid path is not silently substituted when DevLog misses evidence.

### `AGENT_DIRECT`

The answering agent receives read-only access to the pinned repository revision
using a frozen tool policy:

- read files and directories;
- search repository text and filenames;
- inspect `git log`, `git show`, `git diff`, parents, and commit metadata;
- inspect ADRs, Engineering Stories, source code, tests, and documentation;
- record every tool operation, target, result size, and wall-clock duration.

The agent may not access DevLog, MCP/REST context, DevLog projections, DevLog
answers, oracle files, human answers, web search, external memory, package
installation, arbitrary code execution, tests with side effects, or a mutable
working tree. The repository revision and question are frozen.

### `HUMAN_DIRECT`

The human condition is designed now and collected later. A participant may use
normal direct repository inspection under the same read-only repository policy.
The session records start/end timestamps, sources consulted, operations where
practical, submitted answer, confidence, and facilitator notes. The human does
not see agent outputs, DevLog answers, or oracle data before submission.

`HUMAN_DIRECT` is not treated as statistically equivalent to AI conditions. It
has separate effort, bias, and learning controls.

## Experimental Unit

The primary unit is:

```text
one frozen question
× one condition
× one repetition
```

Every raw observation also carries immutable identifiers for benchmark version,
question version, case, category, condition policy, repetition, repository
revision, DevLog version where applicable, model/configuration where applicable,
and evaluation run.

The paired comparison key is:

```text
(benchmark_version, question_id, repository_revision, model_configuration,
 condition_policy_version, repetition)
```

DEVLOG and AGENT_DIRECT observations may be compared only when all shared keys
match and both observations are semantically eligible. Execution completeness
is reported separately so infrastructure failures do not silently become
semantic failures.

## Question Taxonomy

The reusable taxonomy is:

- `CAUSAL_RELATIONSHIP`: whether evidence establishes a relationship and when to abstain.
- `ARCHITECTURAL_DECISION`: why an architectural choice was made and its constraints.
- `IMPLEMENTATION_HISTORY`: how commits, stories, and code evolved over time.
- `FEATURE_EVOLUTION`: how a feature moved across requirements, implementation, and tests.
- `CURRENT_ARCHITECTURE`: what the current system structure and authority model are.
- `ADR_CODE_CONSISTENCY`: whether implementation and tests still reflect an ADR.
- `STORY_PREPARATION_CONTEXT`: what a future change must understand before implementation.

The initial V3 data objectively supports causal interpretation, architectural
constraints, implementation history, feature evolution, affected components,
and negative-control abstention. It does not yet provide a separately frozen,
objective dataset for general current architecture, ADR/code consistency, or
story-preparation quality. Those categories require additional oracle design,
not inferred labels from existing causal scores.

## Existing V3 Candidate Classification

The frozen `devlog-product-value:1.0.0` suite contains four questions. The
recent Design C projection manifest contains `CASE-01`, `CASE-03`, and
`CASE-04`; `CASE-02` has benchmark/oracle material but no equivalent Design C
projection.

| Candidate | Category use | Classification | Reason |
|---|---|---|---|
| `CASE-01` | implementation history + causal relationship + architecture | `ADAPTABLE` | Broad compound question; requires atomic sub-outcomes or a frozen primary category before comparison. A Design C projection exists. |
| `CASE-02` | architectural decision / constraints | `ADAPTABLE` | Oracle and benchmark exist, but a comparable frozen DevLog projection is not present in the current Design C assets. |
| `CASE-03` | feature evolution / implementation history | `READY` | Frozen expected components and tests, evidence projection, causal mapping, and oracle support objective scoring. |
| `CASE-04` | causal relationship negative control | `READY` | Frozen `NOT_ESTABLISHED` oracle and evidence projection support correct-abstention scoring. |

```text
EXISTING_V3_READY_QUESTION_COUNT = 2
EXISTING_V3_ADAPTABLE_QUESTION_COUNT = 2
EXISTING_V3_NOT_SUITABLE_COUNT = 0
```

The smallest useful first baseline contains three questions:
`CASE-01` after the required atomic adaptation, plus `CASE-03` and `CASE-04`.
It contains two ready questions and one explicitly labeled adaptable question.
`CASE-02` is the first planned expansion after a comparable projection is
frozen. Historical invalid outputs are never used as new observations.

## Ground Truth and Leakage Controls

The evaluator-side oracle remains outside all answering conditions. It contains
expected evidence, constraints, causal links, affected components/tests, and
negative-control outcomes. It is loaded only after raw answers are frozen.

The following are forbidden in both agent prompts and human materials before
submission:

- oracle answer or expected classification;
- expected evidence identifiers or benchmark mapping labels;
- another condition's answer, sources, or score;
- historical failure explanations that reveal the desired answer;
- evaluator thresholds and analysis results.

Condition contamination is prevented with separate run directories, separate
input manifests, condition-specific access adapters, immutable repository
revision, and post-run provenance checks. A condition that receives forbidden
information is `INVALID_COMPARISON`, not a lower score.

## Answer Contract

The semantic answer should use a common evaluator-facing envelope where the
condition can support it:

```text
answer_text
structured_findings[]
references[]
claims[]
confidence = HIGH | MEDIUM | LOW | NOT_PROVIDED
abstention = YES | NO | NOT_APPLICABLE
```

DEVLOG and AGENT_DIRECT may use different internal interaction formats, but the
submitted answer must be normalized to the same evaluator contract without
adding claims. Human answers are preserved verbatim and mapped by the
facilitator to canonical identifiers after submission.

## Primary Outcome

```text
PRIMARY_OUTCOME = correct_grounded_answer_rate
```

For an eligible observation:

```text
correct_grounded = semantic_correct == YES
                   AND grounding_valid == YES
                   AND structural_valid == YES
```

The primary rate is:

```text
count(correct_grounded == YES)
/
count(semantic_eligible == YES)
```

The denominator is explicitly the semantic-eligible denominator. It is never
filled with infrastructure failures. Every report must also show:

```text
execution_completeness = semantic_eligible observations / assigned observations
```

and the raw invalid count. A condition cannot hide poor execution completeness
behind a high eligible-only rate. The pair is not a product winner unless both
semantic and execution results are shown.

This is preferable to raw accuracy because a fluent but unsupported or
unauthorized answer is not a useful project-understanding answer. Semantic
accuracy, grounding validity, and infrastructure validity remain separately
visible rather than collapsed into an opaque score.

## Secondary Outcomes

### Semantic and safety outcomes

- semantic accuracy and per-question correctness;
- grounding validity;
- correct abstention;
- overclaim rate;
- fabricated evidence rate;
- unauthorized reference rate;
- evidence excerpt mismatch rate;
- structural failure rate;
- semantic eligibility and execution completeness.

### Effort and efficiency outcomes

- input tokens and output tokens for AI;
- provider cost and model-call count;
- wall-clock latency;
- provider-visible evidence bytes/items for DEVLOG;
- sources available and consulted;
- repository operations/tool calls;
- human completion time and confidence.

No metric is interpreted alone. Context efficiency is always paired with
correctness and grounding.

## Error Taxonomy

`primary_error` is one mutually understandable category; `secondary_errors` is
an ordered deduplicated list for additional observations.

```text
CORRECT_GROUNDED
CORRECT_POOR_GROUNDING
WRONG_RELATIONSHIP
OVERCLAIM
FALSE_ABSTENTION
FAILED_TO_ABSTAIN
FABRICATED_EVIDENCE
EVIDENCE_EXCERPT_MISMATCH
UNAUTHORIZED_REFERENCE
STRUCTURAL_FAILURE
INFRASTRUCTURE_FAILURE
INVALID_COMPARISON
NOT_EVALUATED
```

Definitions:

- `FABRICATED_EVIDENCE`: a source, commit, locator, quote, or evidence identity is absent from the condition-visible material or cannot be resolved at the pinned revision.
- `EVIDENCE_EXCERPT_MISMATCH`: the cited evidence identity is authorized, but the submitted excerpt/locator does not match the immutable resolved content.
- `CORRECT_POOR_GROUNDING`: the semantic answer matches the oracle, but required supporting references are missing, weak, or invalid; it is not a primary success.
- `OVERCLAIM`: the answer asserts a causal, architectural, or historical conclusion stronger than the oracle-supported evidence.
- `FALSE_ABSTENTION`: the answer abstains when the frozen oracle requires an established answer.
- `FAILED_TO_ABSTAIN`: the answer gives an affirmative or overly specific claim for a frozen negative control.
- `STRUCTURAL_FAILURE`: the answer cannot be parsed or violates the frozen output contract.
- `INFRASTRUCTURE_FAILURE`: provider, access, capture, persistence, bridge, or replay failure without a response-bearing semantic observation.

`semantic_correct` is `NOT_EVALUATED` for infrastructure failures. A model
response rejected by deterministic evidence validation remains a captured
semantic observation with a grounding/error classification; it is not silently
converted into a provider outage.

## Dataset Layers

### Raw observations

Immutable JSONL or JSON artifacts preserve the exact question, condition input,
access policy, repository identity, context identity, prompt/input, raw output,
tool trace, token/cost metadata, validation gates, and artifact hash. Raw
artifacts are append-only and never manually edited.

### Derived clean dataset

A deterministic transformation normalizes references, maps answer claims to
oracle identifiers, calculates semantic/grounding/error fields, and assigns
missing-value states. It is reproducible from raw artifacts plus the frozen
oracle. Derived files are disposable and versioned by transformation revision.

### Analytical results

Tables, confidence intervals, plots, and summaries are generated from the
derived dataset. They contain no authority absent from the raw data and are
never hand-edited.

## Normalized Observation Schema

The following is the design schema, not an implementation commitment:

```json
{
  "observation_id": "immutable UUID",
  "benchmark_version": "devlog-comparative-baseline-1.0.0",
  "question_id": "CASE-03",
  "question_version": "1.0.0",
  "case_id": "CASE-03",
  "question_category": "FEATURE_EVOLUTION",
  "condition": "DEVLOG | AGENT_DIRECT | HUMAN_DIRECT",
  "condition_policy_version": "...",
  "repetition": 1,
  "repository_id": "...",
  "repository_revision": "...",
  "devlog_version": "... | NOT_APPLICABLE",
  "context_strategy": "... | NOT_APPLICABLE",
  "provider": "... | NOT_APPLICABLE",
  "model": "... | NOT_APPLICABLE",
  "model_configuration": {},
  "answer": {},
  "causal_result": {},
  "confidence": "HIGH | MEDIUM | LOW | NOT_PROVIDED",
  "semantic_correct": "YES | NO | NOT_EVALUATED",
  "grounding_valid": "YES | NO | NOT_EVALUATED",
  "correct_abstention": "YES | NO | NOT_APPLICABLE | NOT_EVALUATED",
  "overclaim": "YES | NO | NOT_EVALUATED",
  "primary_error": "... | NOT_APPLICABLE",
  "secondary_errors": [],
  "structural_valid": "YES | NO | NOT_EVALUATED",
  "semantic_eligible": "YES | NO | NOT_EVALUATED",
  "input_tokens": "number | NOT_MEASURED | NOT_APPLICABLE",
  "output_tokens": "number | NOT_MEASURED | NOT_APPLICABLE",
  "provider_cost": "number | NOT_AVAILABLE | NOT_APPLICABLE",
  "latency_ms": "number | NOT_MEASURED",
  "provider_visible_evidence_bytes": "number | NOT_APPLICABLE",
  "provider_visible_evidence_items": "number | NOT_APPLICABLE",
  "sources_available_count": "number | NOT_MEASURED | NOT_APPLICABLE",
  "sources_consulted_count": "number | NOT_MEASURED | NOT_APPLICABLE",
  "repository_operations_count": "number | NOT_MEASURED | NOT_APPLICABLE",
  "human_time_seconds": "number | NOT_APPLICABLE",
  "human_confidence": "HIGH | MEDIUM | LOW | NOT_PROVIDED",
  "run_id": "...",
  "artifact_reference": "...",
  "oracle_version": "...",
  "raw_output_sha256": "...",
  "timestamp": "UTC timestamp"
}
```

Missing-value semantics are explicit:

- `NOT_APPLICABLE`: the field has no meaning for the condition, such as human provider cost.
- `NOT_AVAILABLE`: the value should exist but was unavailable, such as provider cost from an unsupported billing API.
- `NOT_MEASURED`: the protocol did not instrument the value.
- `INVALID`: the observation or comparison cannot be used for the intended analysis.
- `NOT_EVALUATED`: downstream scoring was blocked by an upstream failure.

Zeros mean measured zero only. They never substitute for one of these states.

## Repetition and Pairing

The initial AI design uses three repetitions per question and condition:

```text
3 questions × 2 AI conditions × 3 repetitions = 18 AI observations
```

Three repetitions are a modest continuity choice: enough to expose obvious
stochastic instability and calculate within-question paired outcomes, without
pretending to estimate a stable population distribution or spending for a
large benchmark before the protocol is validated. More repetitions require a
separate design decision.

DEVLOG and AGENT_DIRECT are paired on question, repository revision, model,
configuration, repetition index, and evaluation policy. The condition order is
randomized or counterbalanced at the run level where operationally possible;
the agent never receives the other condition's result.

HUMAN_DIRECT is not repeated by the same participant for the same question in
the initial design. Repetition creates memory and learning contamination. Use
counterbalanced sessions with independent participants if human replication is
later required.

## Human Bias Controls

The protocol records learning effects, memory, fatigue, order, researcher
facilitation, and self-evaluation bias. Mitigations are:

- counterbalance condition order;
- separate sessions and a washout period where feasible;
- use equivalent question wording and pinned revision;
- do not expose oracle or agent answers before submission;
- record start/end automatically where possible;
- have a facilitator capture operations without judging the answer;
- score after collection using the same evaluator-side oracle;
- report the project author as a non-independent participant if applicable;
- treat one-person Human Direct results as exploratory, not generalizable.

Human confidence is ordinal and analyzed by outcome strata. It is not forced
into a probabilistic calibration claim.

## Metrics and Analysis Plan

Directly comparable between DEVLOG and AGENT_DIRECT:

- semantic correctness;
- grounding validity;
- correct abstention;
- overclaim and fabricated evidence rates;
- unauthorized references;
- structural validity and semantic eligibility;
- per-question error categories;
- input/output tokens only when both instrumentation contracts measure them;
- latency only with equivalent start/end boundaries.

Human time, sources consulted, confidence, and repository interactions require
separate interpretation. Human correctness can be compared descriptively, but
not collapsed with AI latency or provider cost.

The initial analysis is descriptive:

- raw counts and proportions with denominators;
- paired success/failure tables per question and condition;
- absolute percentage-point differences;
- relative differences only where denominators make them meaningful;
- median and interquartile latency/context/cost summaries;
- error taxonomy distributions;
- per-question paired outcome matrix;
- bootstrap intervals only when the resampling unit and small-sample limits are
  made explicit.

Significance testing is not required for the initial baseline. A McNemar-style
paired binary test becomes reasonable only after enough complete paired
observations exist, the pairing assumptions hold, and the analysis plan is
versioned before inspecting the result. It must remain secondary to effect
sizes and denominators.

Required effect reporting includes:

```text
DevLog: 18/30 correct-grounded
Agent Direct: 21/30 correct-grounded
absolute difference: -10 percentage points
paired denominator: 30
execution completeness by condition: explicit
```

Future plots:

- grounded correctness by condition with denominators;
- per-question paired outcome matrix;
- error taxonomy by condition;
- context volume versus correctness and grounding;
- latency versus correctness;
- cost versus valid/correct answer;
- confidence versus correctness and grounding failures;
- DevLog-version progression on the same frozen benchmark.

## Cost and Context Efficiency

For AI, record input tokens, output tokens, model calls, tool operations,
latency, and provider price metadata where available. `cost_per_correct_grounded_answer`
is:

```text
total measured provider cost /
count(correct_grounded == YES)
```

It is `NOT_AVAILABLE` when pricing is unavailable and undefined when there are
zero correct-grounded answers; it must not be reported as zero. Invalid and
incomplete observations are shown separately and included in execution cost
accounting, not silently discarded.

Context efficiency is reported as bytes, input tokens, and visible sources per
correct-grounded answer, alongside correctness and execution completeness. A
short wrong answer is not efficient. Human context efficiency uses sources and
operations only when measured, never inferred from answer length.

## Confidence

AI `HIGH`, `MEDIUM`, and `LOW` are ordinal metadata. Report accuracy,
grounding-failure, abstention, and overclaim rates by confidence level. Report
high-confidence error rate explicitly. Do not claim calibrated probabilities
without a predeclared calibration method and sufficient observations.

Human confidence uses the same ordinal labels only as a compatible reporting
dimension; it does not imply identical cognitive meaning.

No global DevLog effectiveness score is created initially. A single composite
would hide the tradeoff between correctness, grounding, context volume, cost,
and human effort. A later composite requires an explicit product decision and
sensitivity analysis.

## Longitudinal Versioning

The following identities remain frozen for a legitimate longitudinal comparison:

- benchmark suite/version and question versions;
- oracle version and approval status;
- repository ID and exact revision;
- condition access policy version;
- model/provider/configuration;
- DevLog version and context strategy/digest;
- prompt/output contract and scoring transformation version;
- repetition policy and analysis version.

`same benchmark, new DevLog version` is a valid comparison when repository,
oracle, questions, model, condition policy, and scoring remain fixed.

The following require a new benchmark version or explicit comparability break:

- changed question text, scope, category, or expected evidence;
- changed oracle answer or threshold;
- changed repository revision that changes available evidence;
- changed model/provider or material generation configuration;
- changed access policy or condition contamination boundary;
- changed scoring semantics or error taxonomy meaning.

Only one controlled variable should change in a longitudinal claim. If multiple
variables change, report a new benchmark/configuration identity and do not call
the result improvement.

## Historical CASE-01

The recent Design C CASE-01 observation is classified:

```text
HISTORICAL_CASE01_CLASSIFICATION = HISTORICAL_PRE_BASELINE
HISTORICAL_INVALID_V3_RUNS_INCLUDED_IN_SEMANTIC_DATASET = NO
```

It remains valuable evidence of schema acceptance, raw capture, and strict Core
grounding behavior:

- provider schema accepted;
- response completed;
- 548 output tokens;
- structural, context, reference, and evaluation-layer evidence gates passed;
- Java Core evidence validation failed due to excerpt mismatch;
- semantic eligibility was `NO`.

It cannot enter the official comparative baseline because it has one condition,
one repetition, no AGENT_DIRECT pair, no finalized comparative policy, and a
Core-invalid response. It must not be relabeled as a semantic baseline result.

## V3 Reuse Boundary

Reuse from V3:

- frozen questions, cases, oracle/mapping, and repository revision;
- typed reference and evidence validation gates;
- raw provider capture and response hashing;
- Java Core bridge and fail-closed validation;
- exact projection locators, bytes, digests, ordering, and budgets;
- immutable artifacts and offline replay discipline;
- historical invalidity classification.

Do not inherit blindly:

- causal-only scope;
- Design C's provider-only context assumptions;
- one-condition smoke semantics;
- historical invalid runs as semantic data;
- V3 token limits or model configuration as universal baseline settings;
- the assumption that one provider response or one smoke proves product value.

## Product Interpretation

The report must preserve tradeoffs:

- DevLog accuracy approximately equal to direct accuracy with much smaller
  context may indicate context-efficiency value.
- Lower DevLog accuracy and grounding indicate current negative value or a
  context/contract defect, not automatic product failure without diagnosis.
- Higher DevLog grounding with slightly lower accuracy indicates a safety and
  utility tradeoff, not a simple winner.
- Lower cost is meaningful only when validity and correctness remain comparable.
- Human time reduction does not compensate for materially worse correctness.

No result establishes Human Utility, AI Utility, or product acceptance alone.

## Reproducibility and Publication

An article-ready release contains sanitized manifests, raw response hashes,
question/oracle versions, condition policies, derived data, transformation
version, and analysis code/version. It excludes API keys, secret environment
values, unnecessary private repository content, and raw data not needed to
reproduce the published aggregate.

An article would be scientifically misleading if it:

- mixed historical invalid observations with finalized baseline observations;
- compared different model/configuration/repository revisions without labeling;
- exposed oracle answers to one condition;
- treated infrastructure failures as semantic failures or silently dropped them;
- used only successful observations without execution completeness;
- selected the primary metric after seeing results;
- claimed causal superiority from descriptive differences in a tiny sample;
- presented one human participant as a general population;
- used an AI judge to invent evidence matches;
- reported a composite score without predeclared weights and sensitivity analysis.

## Data Science Learning Plan

### `LEARN`

- formulate hypotheses and estimands;
- define categorical variables and missingness states;
- inspect paired data and denominators;
- calculate descriptive proportions and effect sizes;
- reason about variance, confounding, and small-sample limits;
- interpret confidence intervals and visualizations;
- critique longitudinal comparability and publication claims.

### `PAIR`

- finalize the observation schema and oracle-to-answer coding rules;
- review condition-isolation and human-bias controls;
- design bootstrap/pairing analysis once observations exist;
- review plots and error-taxonomy interpretation;
- decide whether a future composite metric is product-justified.

### `DELEGATE`

- mechanical JSONL import/export and schema validation;
- immutable artifact hashing and manifest checks;
- deterministic tabular reshaping;
- repeatable plot rendering after the analysis plan is approved.

The initial analytical stack should remain Python, Pandas, Matplotlib, and the
standard library. A statistical package is added only when a justified paired
analysis needs it. No ML platform, vector database, RAG, or large data-science
platform is required.

Recommended storage:

- raw observations: immutable JSONL/JSON;
- derived analysis-ready data: Parquet when nested raw fields are flattened,
  otherwise CSV for the small initial dataset;
- analytical results: generated CSV/JSON plus versioned PNG/SVG plots.

## Smallest Next Implementation Slice

No implementation is authorized in this phase. The smallest future slice is a
design-reviewed, offline-only schema and manifest validator that:

1. freezes the three-question baseline manifest and condition policies;
2. validates raw observation envelopes and missing-value semantics;
3. validates pairing keys and provenance without provider calls;
4. imports the existing historical CASE-01 artifact only as
   `HISTORICAL_PRE_BASELINE`;
5. emits no comparative score and touches no production code.

The next slice should become a new engineering story only after the protocol
design is explicitly accepted. It must not reopen Story0132 or create Story0133
implicitly.

## Required Report

```text
PRIMARY_RESEARCH_QUESTION = Does DevLog help an AI agent or human understand project history, architectural decisions, causal relationships, and implementation evolution more effectively than direct repository reading?
PRIMARY_OUTCOME = correct_grounded_answer_rate
PRIMARY_OUTCOME_DEFINITION = correct semantic answer with valid grounding among semantic-eligible observations; execution completeness reported separately

INITIAL_CONDITIONS = DEVLOG, AGENT_DIRECT
HUMAN_DIRECT_DESIGNED = YES
HUMAN_DIRECT_COLLECTED_IMMEDIATELY = NO
EXPERIMENTAL_UNIT = frozen question × condition × repetition

INITIAL_QUESTION_COUNT = 3
INITIAL_QUESTION_CATEGORIES = IMPLEMENTATION_HISTORY, FEATURE_EVOLUTION, CAUSAL_RELATIONSHIP, ARCHITECTURAL_DECISION
EXISTING_V3_READY_QUESTION_COUNT = 2
EXISTING_V3_ADAPTABLE_QUESTION_COUNT = 2
EXISTING_V3_NOT_SUITABLE_COUNT = 0

AI_REPETITIONS_PER_QUESTION = 3
HUMAN_REPETITION_POLICY = no same-participant repeats initially; counterbalanced independent sessions where feasible

AGENT_DIRECT_ACCESS_POLICY = read-only pinned repository/Git/ADR/Story/source/test access; no DevLog, oracle, web, external memory, code execution, or tests
DEVLOG_ACCESS_POLICY = frozen DevLog evaluation context/projection only; no direct repository, oracle, or other condition output

ERROR_TAXONOMY_DEFINED = YES
MISSING_VALUE_SEMANTICS_DEFINED = YES
RAW_DATA_LAYER_DEFINED = YES
DERIVED_DATA_LAYER_DEFINED = YES
ANALYTICAL_LAYER_DEFINED = YES

PAIRING_STRATEGY = same question, revision, model/configuration, policy, and repetition index
STATISTICAL_ANALYSIS_PLAN = descriptive paired proportions, denominators, effect sizes, error distributions, medians, and justified bootstrap intervals
SIGNIFICANCE_TESTING_REQUIRED_FOR_INITIAL_BASELINE = NO

COST_METRICS_DEFINED = YES
CONTEXT_EFFICIENCY_METRICS_DEFINED = YES
HUMAN_EFFICIENCY_METRICS_DEFINED = YES
HISTORICAL_CASE01_CLASSIFICATION = HISTORICAL_PRE_BASELINE
HISTORICAL_INVALID_V3_RUNS_INCLUDED_IN_SEMANTIC_DATASET = NO

LONGITUDINAL_VERSIONING_DEFINED = YES
ARTICLE_REPRODUCIBILITY_DESIGNED = YES
RECOMMENDED_RAW_FORMAT = immutable JSONL/JSON
RECOMMENDED_ANALYTICAL_FORMAT = Parquet when flattened, otherwise CSV
RECOMMENDED_INITIAL_ANALYSIS_STACK = Python, Pandas, Matplotlib, standard library

DATA_SCIENCE_LEARN_TASKS = experimental design, hypotheses, missingness, descriptive statistics, paired comparisons, variance, effect sizes, bias, visualization, reproducibility
DATA_SCIENCE_PAIR_TASKS = schema/oracle coding, condition isolation, bootstrap design, plots, composite-metric review
MECHANICAL_DELEGATE_TASKS = serialization, hashing, validation, reshaping, plot rendering

NEXT_IMPLEMENTATION_SLICE = offline-only manifest/schema/provenance validator for the three-question baseline
NEW_ENGINEERING_STORY_RECOMMENDED = NO, pending protocol acceptance
PRODUCTION_CODE_CHANGE_REQUIRED = NO
RAG_REQUIRED = NO
ML_REQUIRED = NO
NEW_PROVIDER_CALLS_REQUIRED_FOR_DESIGN = NO
COMMIT = NO
PUSH = NO
MERGE = NO
```

## Direct Answers

1. The question is whether DevLog improves effective project understanding versus direct repository reading, for AI and eventually human users.
2. The primary metric is `correct_grounded_answer_rate`.
3. It rewards answers that are both semantically correct and supported by authorized evidence, unlike raw accuracy.
4. One frozen question, one condition, one repetition.
5. Reuse V3 questions, oracle/mapping, repository revision, projections, validation gates, Core bridge, capture, replay discipline, and artifact identity.
6. Start with three: CASE-01 adapted, CASE-03, and CASE-04.
7. Three AI repetitions per question and condition.
8. Pair by question, revision, model/configuration, policy, and repetition index.
9. Read-only repository/Git/ADR/Story/source/test access, without DevLog, oracle, web, external memory, code execution, or tests.
10. Only the frozen DevLog context/projection and its declared metadata.
11. Keep oracle and scoring evaluator-side until raw outputs are frozen.
12. Separate manifests/runs, policies, inputs, directories, and provenance checks.
13. Record `INFRASTRUCTURE_FAILURE`, keep semantic fields `NOT_EVALUATED`, and report execution completeness.
14. Keep the response as an observation and classify deterministic failures using the error taxonomy.
15. Any unsupported or unresolvable source, locator, quote, or evidence identity.
16. A semantically correct answer whose required evidence is absent, weak, invalid, or mismatched.
17. Compare against the frozen negative oracle; affirmative unsupported answers are `FAILED_TO_ABSTAIN`.
18. Exact inputs, outputs, traces, hashes, configuration, validation, and provenance.
19. Normalized references, semantic flags, error classes, metrics, and analytical summaries.
20. Correctness, grounding, abstention, overclaim, fabrication, authorization, structural validity, eligibility, and equivalently instrumented AI cost/latency.
21. Human time, sources, operations, confidence, learning, fatigue, and self-report require separate interpretation.
22. Label the author as a non-independent exploratory participant and avoid population claims.
23. No same-participant repeats initially; use counterbalanced independent sessions later.
24. Descriptive paired tables, proportions, medians, error distributions, effect sizes, and cautious bootstrap intervals.
25. When enough complete paired observations exist and pairing assumptions hold; McNemar-style analysis is secondary.
26. Absolute percentage-point difference, relative difference when meaningful, interval, and raw denominator.
27. Condition correctness, paired outcomes, errors, context/correctness, latency/correctness, cost/correctness, and confidence/outcome.
28. Divide context volume by correct-grounded outcomes and always show correctness and execution completeness.
29. Record measured provider cost and calls; divide total cost by correct-grounded answers, with undefined/not-available semantics.
30. Analyze ordinal confidence by accuracy, grounding failure, abstention, and high-confidence error rate.
31. No global score initially.
32. Keep benchmark, oracle, revision, policy, model, and scoring frozen while changing only the Retrieval Layer version.
33. Benchmark/questions/oracle, repository revision, condition policy, model/configuration, DevLog strategy, output/scoring contracts, repetitions, and analysis version.
34. Any changed question/scope/oracle/revision/model/policy/scoring semantics.
35. No. It is `HISTORICAL_PRE_BASELINE`.
36. Three questions, two AI conditions, three repetitions, immutable raw artifacts, evaluator-side oracle, and descriptive paired analysis.
37. Hypotheses, estimands, missingness, descriptive statistics, paired comparisons, effect sizes, bias, and interpretation.
38. Mechanical validation, hashing, import/export, reshaping, and reproducible rendering after review.
39. Mixing historical invalid data, leaking oracle information, changing uncontrolled variables, hiding invalid runs, selecting metrics post hoc, or overstating tiny-sample results.
40. An offline-only baseline manifest/schema/provenance validator, after protocol acceptance and without provider or production changes.
