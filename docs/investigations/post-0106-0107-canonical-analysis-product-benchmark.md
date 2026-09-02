# Post-0106/0107 Canonical Analysis Product Benchmark

Status: `READY_FOR_HUMAN_REVIEW`

Scope: `INVESTIGATION_ONLY` / `READ_ONLY_PRODUCT_BENCHMARK`

Date: 2026-09-01

## 1. Executive Summary

This benchmark executed the real canonical Analysis workflow nine times against one stable DevLog AI project state: three executions each of `describe-project-v1`, `architecture-overview-v1`, and `analyze-engineering-decision-v1`.

The benchmark establishes four principal findings.

1. `[CONFIRMED_BY_RUNTIME]` Meaningful model-facing information was semantically stable within every three-run intent group. Candidate Fact and Observation semantics, selected Fact/Observation/Insight semantics, Semantic Section membership, and selected repository evidence were equivalent after excluding Analysis-local identities and timestamps.
2. `[CONFIRMED_BY_RUNTIME]` Story 0107's intended cross-Analysis behavior is demonstrated. Same-score/same-type boundary sets selected the same 8 of 20 Markdown Facts in six describe/decision runs and the same 25 of 37 REST-controller Facts in three architecture runs despite regenerated Fact UUIDs.
3. `[CONFIRMED_BY_RESPONSE]` Product usefulness was only `4/9`. Describe Project completed usefully once and failed twice after corrective generation. Architecture Overview completed technically three times but did not provide a usable architecture overview in any run. Engineering Decisions returned one supported ADR decision in every run.
4. `[INFERENCE]` The primary remaining product bottleneck is `OUTPUT_SYNTHESIS`, not context composition. Relevant project and architecture information was present, organized, and stable, but the descriptive outputs either remained enumerative or did not synthesize a current-state answer at all.

The two Describe Project failures are a serious secondary `GENERATION_ROBUSTNESS` issue. All three Describe Project executions needed a corrective model call; two still returned invalid Fact references and correctly failed closed.

```text
PRIMARY_BOTTLENECK = OUTPUT_SYNTHESIS
ADR_064_NEXT_STEP = KEEP_PAUSED
NEXT_ENGINEERING_STORY = Make architecture-overview-v1 provide a coherent current-state architecture synthesis while retaining conservative delta handling.
PRODUCT_VERDICT = ANALYSIS_PRODUCT_NOT_YET_RELIABLE
```

## 2. Baseline

### 2.1 Repository baseline

```text
BRANCH = main
HEAD_SHA = 2e849641cf74361d7703e4b3f53609b9c5b3e83e
WORKTREE_STATUS = DIRTY_WITH_PRE_EXISTING_UNTRACKED_ARTIFACTS
STORY_0106_PRESENT = YES
STORY_0107_PRESENT = YES
```

- `[CONFIRMED_BY_CODE]` Story 0106 is present through commit `62a0610b3c457b581aadbcc4cb4c1766bab9304c` and merge commit `2e849641cf74361d7703e4b3f53609b9c5b3e83e`.
- `[CONFIRMED_BY_CODE]` Story 0107 is present through commit `af4abfbb156e3915d29ab4d331a057a7d3bd73d6` and merge commit `af7b9b85631dd8bca7bee7f2eb2eabfd8c0d4421`.
- `[CONFIRMED_BY_CODE]` The pre-existing outer worktree entries were `data/` and `docs/investigations/post-0104-structured-context-to-analysis-output-investigation.md`. Neither was modified by this investigation.
- `[CONFIRMED_BY_RUNTIME]` The initially running AI Engine matched the Story 0106 prompt files exactly. The initially running backend image predated Story 0107 and could not establish the required executable baseline.
- `[CONFIRMED_BY_RUNTIME]` Before any benchmark run, the backend was rebuilt from current `HEAD`. The AI Engine image was unchanged, PostgreSQL data was preserved, and provider/model configuration remained unchanged.

### 2.2 Canonical project state

```text
PROJECT_ID = f3d56247-aada-4a76-982b-e6802c0b309c
REPOSITORY = https://github.com/Hopeful117/devlog-ai
GIT_HEAD = 2e849641cf74361d7703e4b3f53609b9c5b3e83e
BRANCH = main
WORKTREE_DIRTY = NO (canonical synchronized Compose workspace)
UNTRACKED_FILES_RELEVANT_TO_ANALYSIS = NONE DEMONSTRATED
```

- `[CONFIRMED_BY_RUNTIME]` The canonical Compose workspace at `/var/lib/devlog-ai/workspaces/7819103b-37e7-4e15-95ec-fff9a12d21e4` was clean and at benchmark `HEAD` before and after all runs.
- `[CONFIRMED_BY_CODE]` Generic Analysis reads collected/persisted project context and the synchronized workspace, not the outer live worktree.
- `[CONFIRMED_BY_RUNTIME]` The outer untracked `data/` workspace was not the Compose named-volume workspace used by these executions.
- `[CONFIRMED_BY_RUNTIME]` No tracked repository state changed during the runtime runs.

### 2.3 Runtime configuration

```text
PROVIDER = openai
MODEL = gpt-4.1-mini
TEMPERATURE = PROVIDER_DEFAULT
TOP_P = PROVIDER_DEFAULT
SEED = PROVIDER_DEFAULT
STRUCTURED_OUTPUT_MODE = OpenAI Responses structured parse with Pydantic output models
RETRY_POLICY = provider transport maximum 2 retries; one application corrective generation after semantic validation failure
MAX_OUTPUT_TOKENS = 2000
LLM_TIMEOUT_SECONDS = 30
AUTHORIZATION_SCOPE = no inbound application authorization; outbound provider key only
```

- `[CONFIRMED_BY_RUNTIME]` Container environment supplied provider, model, timeout, token cap, and retry values.
- `[CONFIRMED_BY_CODE]` Temperature, top-p, and seed are not supplied by the OpenAI adapter.
- `[CONFIRMED_BY_RUNTIME]` The nine Analysis executions caused 12 actual OpenAI `/v1/responses` calls: 9 initial calls and 3 Describe Project corrective calls. No provider transport retry was observed.

## 3. Method

### 3.1 DevLog context and repository authority

The investigation began with DevLog engineering context and project-history search for Stories 0106/0107, ADR-064, prior benchmarks, and known limitations. Repository code, Git state, persisted runtime data, canonical API results, and container logs remained authoritative.

`[CONFIRMED_BY_PERSISTED_DATA]` The DevLog context carried a budget warning and was partially fresh, so it was used for navigation and prior-history orientation only.

### 3.2 Verified intent mappings

| Requested key | Persisted intent | Task type | Canonical output |
| --- | --- | --- | --- |
| `describe-project-v1` | `describe-project` / `v1` | `INSIGHT_GENERATION` | Insight proposals |
| `architecture-overview-v1` | `architecture-overview` / `v1` | `INSIGHT_GENERATION` | New/enriching architecture Insight deltas |
| `analyze-engineering-decision-v1` | `analyze-engineering-decision` / `v1` | `DECISION_PROPOSAL_GENERATION` | Engineering Decision proposals |

`[CONFIRMED_BY_CODE]` All three generic launches map to Analysis type `ARCHITECTURE_REVIEW`. The displayed Analysis type is therefore not the intent discriminator.

### 3.3 Canonical workflow

Every execution used the same real workflow without guidance:

```text
POST /api/v1/analyses
  -> POST /api/v1/analyses/{analysisId}/workflow
  -> canonical collection/context/selection/projection
  -> AI Engine structured generation
  -> Core callback and proposal persistence
  -> GET /api/v1/analyses/{analysisId}/result
```

No PromptRequest was frozen, replayed, or sent directly to the AI Engine. There was no cherry-picking, failed-run retry, or discarded result.

### 3.4 Evaluation discipline

- Information stability excludes expected Analysis-local UUIDs, generated timestamps, and self-references.
- Output quality uses the user-facing intent standard, not merely schema validity or successful completion.
- A zero architecture delta is contract-valid but is not automatically a useful architecture overview.
- Decision validity requires explicit or strongly convergent project-specific decision evidence.
- Exact system-message and user-message digests are not persisted separately and are reported as `UNKNOWN`; the combined rendered prompt digest is persisted.

## 4. Canonical Input Stability

### 4.1 Candidate semantics

`[CONFIRMED_BY_PERSISTED_DATA]` All nine `contextSnapshot` records exposed the same candidate semantics:

```text
candidate Facts = 100
candidate Observations = 5
validated Engineering Events = 0
knowledge relations = 50
user guidance = absent
```

The Fact candidate pool included 20 `MARKDOWN_DOCUMENT_PRESENT` candidates and 37 `REST_CONTROLLER_DECLARED` candidates, which directly exercised same-score/same-type budget boundaries.

Complete historical unselected Insight-candidate semantics are not persisted and are `UNKNOWN`.

### 4.2 Selected semantics

| Intent | Facts | Observations | Insights | Architecture knowledge | Repository evidence | Information stability |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| Describe Project | 40 | 1 | 10 | 0 | 60 | `STABLE` |
| Architecture Overview | 40 | 4 | 10 | 5 | 60 | `STABLE` |
| Engineering Decisions | 40 | 1 | 10 | 0 | 60 | `STABLE` |

- `[CONFIRMED_BY_PERSISTED_DATA]` Normalized Fact, Observation, Insight, repository-evidence, and Semantic Section semantics were equivalent within each intent group.
- `[CONFIRMED_BY_PERSISTED_DATA]` Describe Project and Engineering Decisions selected the same meaningful 40-Fact set, including stable Markdown boundary membership.
- `[CONFIRMED_BY_PERSISTED_DATA]` Architecture Overview selected the same 25 REST controllers at its boundary in all three runs.
- `[CONFIRMED_BY_PERSISTED_DATA]` Exact selection and prompt digests differed because canonical snapshots include run-local identities, generated profile data, repository-context identity, and changing discarded counts.
- `[INFERENCE]` Exact digest inequality does not demonstrate meaningful information variance in this benchmark.

### 4.3 Semantic Section counts

| Section | Describe | Architecture | Decision |
| --- | ---: | ---: | ---: |
| `PROJECT_STATE` | 31 | 30 | 31 |
| `ARCHITECTURE` | 16 | 50 | 16 |
| `DECISIONS` | 2 | 1 | 2 |
| `VALIDATED_KNOWLEDGE` | 22 | 22 | 22 |
| `HISTORY` | 55 | 36 | 52 |
| `REPOSITORY_CHANGES` | 60 | 60 | 60 |
| `HUMAN_CONTEXT` | 3 | 3 | 3 |

`[CONFIRMED_BY_PERSISTED_DATA]` Membership semantics and counts were stable within each group. Counts overlap because one item may belong to more than one section.

### 4.4 Story 0107 validation signal

```text
CROSS_ANALYSIS_FACT_SELECTION_STABILITY = DEMONSTRATED
```

Evidence:

- `[CONFIRMED_BY_RUNTIME]` The same 8 of 20 same-score Markdown Fact semantics were selected across all six Describe Project and Engineering Decision runs.
- `[CONFIRMED_BY_RUNTIME]` The same 25 of 37 same-score REST-controller Fact semantics were selected across all three Architecture Overview runs.
- `[CONFIRMED_BY_CODE]` Ranking now breaks Fact ties by source, content, and sorted evidence references instead of Fact UUID.

No Story 0107 regression is indicated.

## 5. Describe Project Results

### 5.1 Run 1

```text
CLASSIFICATION = ACCEPTABLE
ANALYSIS_ID = 9e2ef239-d317-420c-bfdc-56ab53bd1e36
REQUEST_ID = a2e3c206-2ad7-496e-aec4-6e5e704a0643
TASK_ID = 3f772ce6-13a7-4875-933b-b49d5a562c85
STATUS = COMPLETED
MODEL_CALLS = 2
PROPOSALS = 7
```

`[CONFIRMED_BY_RESPONSE]` The result correctly covered project purpose, Spring REST, Maven modules, Docker/Compose, ADRs, testing, and project documentation. It was grounded and understandable.

`[CONFIRMED_BY_RESPONSE]` It remained a seven-item inventory rather than a coherent description. It omitted the Angular frontend, Python AI Engine, PostgreSQL persistence, MCP server, service relationships, current post-0106/0107 state, and meaningful project evolution. It also stretched test presence into a continuous-integration claim without direct CI evidence.

Proposal IDs:

```text
7ef2359d-b41b-47c8-9875-dc7f1163a6df
d8645ca9-136d-4f99-9ff6-036701badbbc
6dded47e-ba62-4ed1-b39f-1e7050cb30a9
af50c7ee-9ddc-463e-8762-7faadf1b654e
5ceab548-1b96-4c4e-960a-d62fce6971f5
eb85e608-21e7-45eb-9c8e-1cb23b4827c9
d776b8b9-9eac-4cc6-8371-2df2f2d7147e
```

### 5.2 Run 2

```text
CLASSIFICATION = FAILED
ANALYSIS_ID = 19bd83f0-933a-4f59-af70-2ff99ed6756e
REQUEST_ID = db4d2cc0-62d2-49c0-a4fe-ab894133b220
TASK_ID = efe50349-0cde-4409-a35b-5d45de9d50aa
STATUS = FAILED
FAILURE_CODE = INVALID_LLM_OUTPUT
MODEL_CALLS = 2
PROPOSALS = 0
```

`[CONFIRMED_BY_RESPONSE]` Final validation failed because `supportingFactIds` contained `ce6912d5-474a-41e2-85b2-f9b0c9adff83`, which was an Insight ID rather than an allowed Fact ID. The corrective call did not repair the contract violation. Validation correctly failed closed, but the user received no project description.

### 5.3 Run 3

```text
CLASSIFICATION = FAILED
ANALYSIS_ID = 447f2926-87de-46c0-8124-8747c9c3eea0
REQUEST_ID = 7f91d398-b0c3-4e3c-ac3f-d8f1567a78a1
TASK_ID = e21fc3e5-8781-4efa-a2aa-d7f01d92c208
STATUS = FAILED
FAILURE_CODE = INVALID_LLM_OUTPUT
MODEL_CALLS = 2
PROPOSALS = 0
```

`[CONFIRMED_BY_RESPONSE]` Final validation failed because `supportingFactIds` contained unsupported UUID `ba95a637-5c8a-4820-94ec-b203fe74b539`. It was not present in selected knowledge or the inspected Fact/Insight data. The corrective call did not repair the violation, and no project description reached the user.

### 5.4 Describe Project summary

```text
RUN_1 = ACCEPTABLE
RUN_2 = FAILED
RUN_3 = FAILED
CLEAN_RATE = 1/3
INFORMATION_STABILITY = STABLE
OUTPUT_CONSISTENCY = LOW
PRIMARY_DEFECT = Grounding-reference generation is unreliable; the only successful output is still an enumerative, incomplete project description.
```

Output comparison:

```text
CORE_CONCLUSIONS_EQUAL = NO
MAJOR_FACTS_CONSISTENT = PARTIAL
MAJOR_OMISSIONS_CONSISTENT = NO
QUALITY_CLASSIFICATION_CONSISTENT = NO
```

`[CONFIRMED_BY_RUNTIME]` First-pass semantic-contract adherence was `0/3`; every run needed one corrective call. Final completion was `1/3`.

## 6. Architecture Overview Results

All three executions returned zero new/enriching architecture proposals.

### 6.1 Contract correctness versus product usefulness

- `[CONFIRMED_BY_CODE]` The current prompt and output contract ask for meaningful architecture deltas against selected existing architecture knowledge and permit an empty proposal array.
- `[CONFIRMED_BY_RESPONSE]` Each result exposed the same five trusted architecture items under supporting evidence: Spring Boot REST, Maven multi-module build, Docker/Compose, ADR use, and testing.
- `[CONFIRMED_BY_RESPONSE]` No result synthesized components, responsibilities, relationships, boundaries, data flow, persistence, runtime topology, or architectural principles.
- `[CONFIRMED_BY_RESPONSE]` No result explicitly told the user that no material architecture delta was found or converted the existing trusted items into the requested overview.
- `[INFERENCE]` Zero proposals were conservative and contract-defensible, but the canonical product result did not meaningfully answer the architecture-overview intent.

### 6.2 Run classifications

```text
RUN_1 = FAILED
ANALYSIS_ID = a0c0c1b5-2326-4ed8-9348-6465db952331
REQUEST_ID = aca6a2ef-f723-4147-90c0-1afb1e7276b3
TASK_ID = 7d0c3a22-43f5-4658-b70e-a9d44b0f9cf5
PROPOSALS = 0

RUN_2 = FAILED
ANALYSIS_ID = 9f466ecf-e448-4bb4-a854-048a51f94426
REQUEST_ID = 30f71f6c-043e-480a-acba-d46af10acbda
TASK_ID = 4f3baf3d-c27c-491a-b1e6-7fd13a9c049d
PROPOSALS = 0

RUN_3 = FAILED
ANALYSIS_ID = 1aa9854b-0570-4419-a235-51f083141b86
REQUEST_ID = e8239eaa-fd71-4500-a7bd-ecafc6468fc1
TASK_ID = 66a9fc0a-c52a-417d-bbc2-9a9b8353b00e
PROPOSALS = 0
```

The `FAILED` classification is a product-quality classification, not an execution-status claim. `[CONFIRMED_BY_RUNTIME]` All three executions completed successfully with one model call each.

### 6.3 Architecture Overview summary

```text
RUN_1 = FAILED
RUN_2 = FAILED
RUN_3 = FAILED
CLEAN_RATE = 0/3
INFORMATION_STABILITY = STABLE
OUTPUT_CONSISTENCY = HIGH BUT CONSISTENTLY NOT USEFUL
PRIMARY_DEFECT = The delta-only result does not synthesize the available current architecture into a usable mental model.
```

Output comparison:

```text
CORE_CONCLUSIONS_EQUAL = YES
MAJOR_FACTS_CONSISTENT = YES
MAJOR_OMISSIONS_CONSISTENT = YES
QUALITY_CLASSIFICATION_CONSISTENT = YES
```

## 7. Engineering Decision Results

### 7.1 Proposal support audit

| Run | Proposal ID | Category | Choice supported | Rationale supported | Consequences supported | Project specific | Generic knowledge as evidence | Unsupported causality |
| ---: | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | `dbb88eb0-d196-4bce-9157-8c1a993b4cea` | `EXPLICIT_DECISION` | YES | YES | YES | YES | NO | NO |
| 2 | `e34d4dd1-97a8-425c-9f74-c8305f0c3587` | `EXPLICIT_DECISION` | YES | YES | PARTIAL | YES | YES | YES |
| 3 | `b188b016-32b1-43f9-b3f6-c447d45bec45` | `EXPLICIT_DECISION` | YES | YES | PARTIAL | YES | YES | YES |

All three proposals restated the same explicit trusted project decision: continue using ADRs as the standard way to document and manage architectural decisions.

- `[CONFIRMED_BY_PERSISTED_DATA]` Choice and principal rationale were directly supported by trusted decision `ae47a47d-65fa-4a30-810c-f114b37755bd`, selected ADR Insights, and an ADR-directory Fact.
- `[CONFIRMED_BY_RESPONSE]` Run 1 remained within selected support.
- `[CONFIRMED_BY_RESPONSE]` Run 2 added limited generic maintainability and evolution-risk consequences not directly supported by model-facing evidence.
- `[CONFIRMED_BY_RESPONSE]` Run 3 added limited governance, knowledge-transfer, transparency, and decision-management consequences beyond the selected model-facing summary.
- `[CONFIRMED_BY_RESPONSE]` Proposal-level traceability arrays were empty in all three runs even though semantic support was recoverable from the selected task snapshot.

### 7.2 Run classifications

```text
RUN_1 = STRONG
RUN_2 = ACCEPTABLE
RUN_3 = ACCEPTABLE
CLEAN_RATE = 3/3
INFORMATION_STABILITY = STABLE
OUTPUT_CONSISTENCY = HIGH
PRIMARY_DEFECT = Two runs broaden consequences beyond direct model-facing support, and proposal-level evidence links are absent.
```

Run 1 is `STRONG` because it emitted only a supported, conservative, useful decision. Runs 2 and 3 are `ACCEPTABLE` because the decision itself remained explicit and valid, while unsupported causality was limited to secondary consequence wording.

Output comparison:

```text
CORE_CONCLUSIONS_EQUAL = YES
MAJOR_FACTS_CONSISTENT = YES
MAJOR_OMISSIONS_CONSISTENT = PARTIAL
QUALITY_CLASSIFICATION_CONSISTENT = NO
```

### 7.3 Decision metrics

```text
TOTAL_DECISION_PROPOSALS = 3
VALID_DECISION_PROPOSALS = 3
TECHNOLOGY_ONLY_PROPOSALS = 0
GENERIC_ENGINEERING_PROPOSALS = 0
UNSUPPORTED_CAUSALITY_PROPOSALS = 2
VALID_DECISION_RATE = 3/3
```

`[INFERENCE]` The fresh evidence does not justify reopening prompt-level decision eligibility as the primary problem. The principal choices were explicit and stable; only secondary consequences exceeded direct support.

## 8. Cross-Run Comparison

| Intent | Run | Information Stability | Output Quality | Main Strength | Main Defect |
| --- | --: | --- | --- | --- | --- |
| `describe-project-v1` | 1 | `STABLE` | `ACCEPTABLE` | Grounded purpose and key repository characteristics | Enumerative and omits major components, relationships, and current state |
| `describe-project-v1` | 2 | `STABLE` | `FAILED` | Invalid grounding was rejected safely | Insight UUID emitted as a Fact ID after correction |
| `describe-project-v1` | 3 | `STABLE` | `FAILED` | Unsupported output was rejected safely | Fabricated/unavailable Fact UUID after correction |
| `architecture-overview-v1` | 1 | `STABLE` | `FAILED` | Conservative zero delta against trusted knowledge | No architecture synthesis or explicit no-delta explanation |
| `architecture-overview-v1` | 2 | `STABLE` | `FAILED` | Same trusted baseline and no speculative proposal | No components, boundaries, responsibilities, or relationships |
| `architecture-overview-v1` | 3 | `STABLE` | `FAILED` | Third consistent contract-safe result | Canonical result still does not answer the user-facing intent |
| `analyze-engineering-decision-v1` | 1 | `STABLE` | `STRONG` | Explicit ADR decision with supported rationale/consequences | Proposal-level evidence arrays are empty |
| `analyze-engineering-decision-v1` | 2 | `STABLE` | `ACCEPTABLE` | Same explicit project decision | Limited generic maintainability/risk causality |
| `analyze-engineering-decision-v1` | 3 | `STABLE` | `ACCEPTABLE` | Same explicit project decision | Limited unsupported consequence expansion |

The benchmark therefore distinguishes:

```text
INFORMATION_STABILITY = STRONG
OUTPUT_QUALITY = NOT RELIABLE
```

## 9. Aggregate Metrics

```text
TOTAL_RUNS = 9

STRONG_RUNS = 1
ACCEPTABLE_RUNS = 3
WEAK_RUNS = 0
FAILED_RUNS = 5

USEFUL_RUNS = 4
OVERALL_USEFUL_RATE = 4/9

MODEL_CALLS = 12
CROSS_ANALYSIS_FACT_SELECTION_STABILITY = DEMONSTRATED
```

`[CONFIRMED_BY_RUNTIME]` Seven executions reached a terminal success callback and two Describe Project executions failed, but execution success is not the product-quality denominator: the three technically successful Architecture Overview runs were still product `FAILED` because they did not meaningfully answer the intent.

## 10. Primary Bottleneck

```text
PRIMARY_BOTTLENECK = OUTPUT_SYNTHESIS
```

Rationale:

- `[CONFIRMED_BY_RUNTIME]` Necessary project and architecture information was selected stably and organized into intent-aware Semantic Sections.
- `[CONFIRMED_BY_RESPONSE]` The successful Describe Project output turned rich context into a technology/document inventory rather than a coherent project description.
- `[CONFIRMED_BY_RESPONSE]` Architecture Overview had 40 Facts, 4 Observations, 10 Insights, 5 trusted architecture items, 60 repository-evidence items, and 50 architecture-section memberships, yet yielded no current-state architecture answer in all three runs.
- `[INFERENCE]` Adding more context structure is not supported as the main remedy. The available information was sufficient to explain the major parts and their relationships at a useful V1 level.

Secondary issues:

```text
1. GENERATION_ROBUSTNESS: Describe Project needed correction in 3/3 runs and failed after correction in 2/3.
2. RESULT_PROJECTION: Architecture zero-delta results do not explain the conclusion or elevate existing architecture into the answer.
3. POST_GENERATION_VALIDATION: Current validation safely rejects invalid IDs but cannot recover a useful result after the one corrective attempt.
4. Decision consequence discipline: 2/3 decision proposals contain limited unsupported consequence expansion.
```

These are secondary, not co-primary.

## 11. Deferred Candidate Assessment

### 11.1 Model-facing identity normalization

```text
MODEL_FACING_IDENTITY_NORMALIZATION_PRIORITY = NOW
```

`[CONFIRMED_BY_RESPONSE]` In one failed Describe Project run, the model copied a selected Insight UUID into `supportingFactIds`; another run emitted an unavailable UUID. All three Describe Project runs required correction. Volatile typed identities are therefore materially affecting current product reliability, not merely prompt aesthetics.

This priority does not authorize implementation and is not the one recommended next Story because the systematic Architecture Overview product gap affects every run of a canonical intent.

### 11.2 Deterministic decision eligibility validator

```text
DETERMINISTIC_ELIGIBILITY_VALIDATOR_PRIORITY = LATER
```

`[CONFIRMED_BY_RESPONSE]` All three decision proposals were based on an explicit trusted decision. No technology-only or generic decision escaped. Two proposals expanded secondary consequences beyond direct selected support, which merits monitoring but does not establish frequent invalid decision eligibility.

### 11.3 Documentation overflow policy

```text
DOCUMENTATION_OVERFLOW_POLICY_PRIORITY = NOT_JUSTIFIED
```

`[CONFIRMED_BY_RUNTIME]` Documentation boundary selection was stable. `[CONFIRMED_BY_RESPONSE]` The benchmark does not demonstrate that selected-document volume caused the architecture failure or Describe Project enumeration. Relevant architecture information was already present.

## 12. ADR-064 Decision

```text
ADR_064_NEXT_STEP = KEEP_PAUSED
```

`[CONFIRMED_BY_CODE]` ADR-064 is accepted, while its further incremental context-composition work remains paused. `[CONFIRMED_BY_RUNTIME]` This benchmark found stable, rich, intent-aware context. `[INFERENCE]` Context composition is not the dominant product bottleneck, so the evidence does not justify resuming the paused sequence.

## 13. Next Story Recommendation

```text
NEXT_ENGINEERING_STORY = Make architecture-overview-v1 provide a coherent current-state architecture synthesis while retaining conservative delta handling.
```

Goal:

Ensure the canonical Architecture Overview result directly answers what the important parts are, what they do, how they interact, and which boundaries/principles matter, including an explicit conservative conclusion when no new delta exists.

Evidence:

- `[CONFIRMED_BY_RESPONSE]` `0/3` Architecture Overview runs were useful under the product standard.
- `[CONFIRMED_BY_PERSISTED_DATA]` Every run had stable relevant facts, observations, trusted architecture knowledge, repository evidence, and architecture-section membership.
- `[CONFIRMED_BY_RESPONSE]` Existing trusted architecture was visible only as supporting evidence and was not synthesized into a mental model.

Minimal expected scope:

- Limit the Story to the canonical `architecture-overview-v1` answer semantics and user-facing result.
- Preserve deterministic collection/selection, human-validation boundaries, and conservative handling of new/enriching knowledge.
- Do not add RAG, new agents, MCP launch capability, or broader context-composition work.
- Verify with repeated canonical runs against one stable project state and score user-facing synthesis separately from delta correctness.

Expected product improvement:

A developer unfamiliar with the repository receives a usable current architecture mental model instead of a technically valid but functionally empty delta result.

No Story number or Story artifact is created by this recommendation.

## 14. Product Verdict

```text
ANALYSIS_OPTIMIZATION = CONTINUE
PRODUCT_VERDICT = ANALYSIS_PRODUCT_NOT_YET_RELIABLE
```

`[INFERENCE]` Stable information and strong decision behavior are meaningful progress after Stories 0106/0107, but `4/9` useful outputs, `2/3` Describe Project execution failures, and `0/3` useful Architecture Overview results do not support current V1 product readiness.

## 15. Evidence / Run IDs

### 15.1 Prompt and selection evidence

Separate system-message and user-message/context digests are `UNKNOWN`. The application persists the combined rendered prompt digest shown below.

| Intent | Run | Analysis ID | Request ID | Task ID | Selection/context digest | Rendered prompt digest |
| --- | --: | --- | --- | --- | --- | --- |
| Describe | 1 | `9e2ef239-d317-420c-bfdc-56ab53bd1e36` | `a2e3c206-2ad7-496e-aec4-6e5e704a0643` | `3f772ce6-13a7-4875-933b-b49d5a562c85` | `6e7ab71539050f4f09920d76457a176cd395f171085f28faf165a7f7e3b35135` | `3a0b387991277c410e765512a20ede4d39e9dcd5b5ae5fe6d25b4b9ebdd81579` |
| Describe | 2 | `19bd83f0-933a-4f59-af70-2ff99ed6756e` | `db4d2cc0-62d2-49c0-a4fe-ab894133b220` | `efe50349-0cde-4409-a35b-5d45de9d50aa` | `3a974724968514525871c4eeb58afba558a458ab6b5d2097806dfe76cb9bccbe` | `018b7a56bd2610544cfbe2039e26df1043365146f0267461b71f825fd63db8db` |
| Describe | 3 | `447f2926-87de-46c0-8124-8747c9c3eea0` | `7f91d398-b0c3-4e3c-ac3f-d8f1567a78a1` | `e21fc3e5-8781-4efa-a2aa-d7f01d92c208` | `8ad002f701a3b034b4e661ab145323f0dce48c96d28f7632f3fb96db48c45372` | `851eda3f18f30db5771121f6f0489d52f0acd911f841eb33ebab0ff65f039a76` |
| Architecture | 1 | `a0c0c1b5-2326-4ed8-9348-6465db952331` | `aca6a2ef-f723-4147-90c0-1afb1e7276b3` | `7d0c3a22-43f5-4658-b70e-a9d44b0f9cf5` | `c72f63b063afa2c7362393d5ce0f9d36234ad7f6422971c8dff2b9f741264509` | `ccc48a3425a4bf54387ae90354278c18d5bacb51c77fa3e7f7a3bacaafb8718d` |
| Architecture | 2 | `9f466ecf-e448-4bb4-a854-048a51f94426` | `30f71f6c-043e-480a-acba-d46af10acbda` | `4f3baf3d-c27c-491a-b1e6-7fd13a9c049d` | `277e76adc6b71994e1cc4dffa6f49ff9ca8e53ac65d76cad07841a012b4d5c48` | `a8c03d78ae03cba6565e08591c357b226f70e6313d067841d012d9cae03c87f2` |
| Architecture | 3 | `1aa9854b-0570-4419-a235-51f083141b86` | `e8239eaa-fd71-4500-a7bd-ecafc6468fc1` | `66a9fc0a-c52a-417d-bbc2-9a9b8353b00e` | `9573d3292c8861bcdacf187e534024e190f400811b7ae4ff8ba7c92ab7751fdf` | `68eb018b0bb3bb474f3a16dbe0bcaedaef0f06e322ba43080a819942d41da961` |
| Decision | 1 | `a6bd328c-2626-4f4f-aded-eaece78b8767` | `2baf87c2-406b-48cf-a0d5-533c4b01b2af` | `0028dd73-1463-4c7a-ab4b-0cf926a92aa4` | `74127751b8c3e3202bae4b35e6b8d754b27dad5a378d5266dc0b5a0122e3188c` | `2558d7aa9a7730c13cc6a320030b72e74e4be616e1f7b10a999da3e91c20e42f` |
| Decision | 2 | `6e0c2c8b-d09c-45a7-8d62-df72634b68e2` | `faa5217c-4e33-4ba8-a387-dc63d2b8159a` | `9582facd-a6d3-41a4-85ec-766e1178f5ba` | `3565f921f7460a6d325510bdbdf35d7ca57370f61376a3a1a4fc3c406e1864d8` | `0dfef568f1355c9a9db9f87e556542ea3ba9cd931febb1bddf57c2d7659a402b` |
| Decision | 3 | `969988ad-e333-492e-872c-d700acce4713` | `957da878-c4ca-4104-987b-1f1ee7beb2d5` | `b1c070d1-3b5a-4be5-800f-61f49813a32a` | `1d30049b87e40d06be532ddc7fa60677cc36b9dcda31af9ed06fd4c9788d89c5` | `4d80cad05ae6beb50b323316e3fc7a114af177f9dc99dd396c996b0d0b406588` |

For Describe Project, the persisted rendered prompt digest is the final corrective-attempt digest. Initial-attempt prompt digests are `UNKNOWN` because they are neither logged nor persisted.

### 15.2 Result/proposal IDs

There is no independent canonical result entity ID; the Analysis ID identifies the result resource.

```text
Describe run 1 proposals:
  7ef2359d-b41b-47c8-9875-dc7f1163a6df
  d8645ca9-136d-4f99-9ff6-036701badbbc
  6dded47e-ba62-4ed1-b39f-1e7050cb30a9
  af50c7ee-9ddc-463e-8762-7faadf1b654e
  5ceab548-1b96-4c4e-960a-d62fce6971f5
  eb85e608-21e7-45eb-9c8e-1cb23b4827c9
  d776b8b9-9eac-4cc6-8371-2df2f2d7147e

Describe runs 2-3 proposals: NONE
Architecture runs 1-3 proposals: NONE

Decision run 1 proposal: dbb88eb0-d196-4bce-9157-8c1a993b4cea
Decision run 2 proposal: e34d4dd1-97a8-425c-9f74-c8305f0c3587
Decision run 3 proposal: b188b016-32b1-43f9-b3f6-c447d45bec45
```

## 16. Git Hygiene

Before runtime runs:

```text
git status --short:
?? data/
?? docs/investigations/post-0104-structured-context-to-analysis-output-investigation.md

git branch --show-current: main
git rev-parse HEAD: 2e849641cf74361d7703e4b3f53609b9c5b3e83e
```

After all nine runtime runs and before this report:

```text
git status --short:
?? data/
?? docs/investigations/post-0104-structured-context-to-analysis-output-investigation.md
```

`[CONFIRMED_BY_RUNTIME]` The Analysis workflow did not alter tracked repository state or the canonical synchronized workspace. Runtime persistence added Analysis, task, and proposal records to the existing PostgreSQL Docker volume; this is expected benchmark data outside the Git worktree.

Final governance:

```text
PRODUCTION_CODE_CHANGED = NO
TESTS_CHANGED = NO
PROMPTS_CHANGED = NO
CONFIG_CHANGED = NO
NEW_ADR_CREATED = NO
NEW_STORY_CREATED = NO
STAGED = NO
COMMIT_CREATED = NO
PUSH_PERFORMED = NO
MERGE_PERFORMED = NO
HUMAN_REVIEW = REQUIRED
```

Terminal state:

```text
POST_0106_0107_CANONICAL_ANALYSIS_PRODUCT_BENCHMARK_READY_FOR_HUMAN_REVIEW
```
