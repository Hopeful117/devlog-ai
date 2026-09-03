# Story 0108 — Repository Analysis

## Status

**MATERIALIZED_READY_FOR_HUMAN_DESIGN_REVIEW — ADR REQUIRED BEFORE IMPLEMENTATION**

## Baseline and Evidence

- Verified baseline SHA: `2e849641cf74361d7703e4b3f53609b9c5b3e83e`
- Branch: `main`
- Primary product evidence: `docs/investigations/post-0106-0107-canonical-analysis-product-benchmark.md`
- Relevant historical Stories: 0099 through 0107
- Governing ADRs: ADR-006, ADR-020, ADR-028, ADR-030, ADR-063, ADR-064

The benchmark report is currently untracked. It is read-only governing evidence for this Story and is not modified or included in Story materialization.

## Repository Verdicts

```text
CURRENT_ARCHITECTURE_SYNTHESIS_SUPPORTED_TODAY = NO
CURRENT_ARCHITECTURE_DELTA_SUPPORTED_TODAY = YES
NO_DELTA_CAN_STILL_PRODUCE_USEFUL_OVERVIEW_TODAY = NO

PROMPT_ONLY_SOLUTION = INSUFFICIENT
OUTPUT_CONTRACT_GAP = YES
RESULT_PROJECTION_GAP = PARTIAL
SYNTHESIS_PERSISTENCE_REQUIRED = YES

ARCHITECTURE_SPECIFIC_SOLUTION = SUFFICIENT
GENERIC_ANALYSIS_SYNTHESIS_CONCEPT = FUTURE_CANDIDATE

SYNTHESIS_IS_TRUSTED_KNOWLEDGE = NO
VALIDATABLE_PROPOSAL_GOVERNANCE_PRESERVED = YES

NEW_ADR_REQUIRED = YES
ADR_REQUIRED_BEFORE_IMPLEMENTATION = YES
ADR_064_NEXT_STEP = KEEP_PAUSED
```

`RESULT_PROJECTION_GAP = PARTIAL` means the current model never produces a synthesis for Core to discard, but `AnalysisResultResponse` also has no place to persist or expose one. The primary loss is generation/output-contract semantics; result projection must evolve only to carry the newly governed output.

## Historical Constraint Reconstruction

### Story 0099 — executable Intent contracts

Generic Analysis launch resolves a catalog Intent and lets Core derive execution behavior. The user-facing objective must not depend on callers constructing an internal task contract.

### Story 0100 — canonical Analysis result

The canonical result is a query-time read model for one Analysis. Primary outcome, proposal review, accepted knowledge, evidence, and next actions are projected through one endpoint rather than reconstructed independently by consumers.

### Story 0101 — trusted-artifact navigation

Accepted proposals resolve to trusted artifacts at query time through preserved provenance. This avoids duplicate inverse result persistence.

### Story 0102 — launch/result restoration

Canonical launch and result behavior were restored without weakening explicit evidence projection. New result semantics must not regress these paths.

### Story 0104 — structured context

Semantic Sections provide deterministic hierarchy over selected context. The current benchmark demonstrated that architecture section content is present and stable, so this Story does not redesign composition.

### Story 0105 — proposal-specific result fields

Canonical result projection preserves proposal-specific semantics such as rationale and architecture delta type. Synthesis must not be squeezed into these proposal fields.

### Story 0106 — intent-aware prompt utilization

Shared structured context is interpreted with intent-specific strategy. This Story is not further prompt tuning: it introduces a new Architecture Overview product/output contract that may require architecture-specific prompt implementation.

### Story 0107 — deterministic Fact ranking

Cross-Analysis same-score Fact selection is stable. The benchmark demonstrated the correction. Ranking is not reopened.

### ADR-006 and ADR-020

ADR-006 states that AI-generated interpretations are represented as `ValidatableProposal` objects and cannot become trusted knowledge without human validation. ADR-020 defines the callback as proposal creation plus task completion and states that only `ValidatableProposal` objects are created.

A persisted current-state synthesis that is intentionally not a proposal introduces a durable new AI-output category. It therefore requires an ADR rather than an implementation-local exception.

### ADR-028 and ADR-030

Intent owns objective, expected response structure, prompt template, validation constraints, and versioning. ADR-028 states that changing prompt semantics or output schema requires a new Intent version. The current `architecture-overview-v1` contract cannot be silently mutated.

### ADR-063

ADR-063 section 29 explicitly states that AI synthesis remains an untrusted `ValidatableProposal` and reaches trusted knowledge only through human acceptance and Core promotion. The selected non-proposal synthesis direction conflicts with that clause and cannot be introduced as an implementation detail. The prerequisite ADR must explicitly reconcile or supersede this rule while preserving its evidence identity, provenance, authorization, and human-governance guarantees.

### ADR-064

ADR-064 gives deterministic Java responsibility for composition and AI responsibility for synthesis. It explicitly says current Architecture Review behaves like delta detection and that changing those product semantics requires separate HUMAN product review. It also keeps result projection separate from context composition.

This Story is that separate product review boundary. Further ADR-064 composition work remains paused.

ADR-064 also states in its relations that AI outputs remain proposals. The prerequisite ADR must explicitly reconcile or supersede that relation for execution-scoped synthesis. Doing so does not resume ADR-064's context-composition sequence.

## Current Architecture Overview Execution Flow

### 1. Intent definition

`IntentCatalog` registers:

```text
key = architecture-overview-v1
persisted id/version = architecture-overview / v1
execution mode = GENERIC
output proposal type = INSIGHT
prompt template = architecture-overview-prompt-v1
context profiles = architecture-v1 + history-v1
allowed types = ARCHITECTURE_DESCRIPTION, TECHNOLOGY_DESCRIPTION,
                INFRASTRUCTURE_DESCRIPTION, API_DESCRIPTION
```

The output contract root is `proposals`, with `0..10` `NEW` or `ENRICHES` proposal candidates and no answer field.

Primary code:

- `backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java`
- `backend/src/main/java/com/hopeful117/devlogai/intent/model/IntentDefinition.java`

### 2. Analysis creation

`AnalysisController.create` receives `POST /api/v1/analyses`. `AnalysisServiceImpl.create` resolves the combined key, enforces generic project scope, derives `AnalysisType.ARCHITECTURE_REVIEW`, and persists `architecture-overview` / `v1` on a `PENDING` Analysis.

Primary code:

- `backend/src/main/java/com/hopeful117/devlogai/analysis/controller/AnalysisController.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/service/AnalysisServiceImpl.java`

### 3. Workflow and task type

`POST /api/v1/analyses/{id}/workflow` invokes `AnalysisWorkflowServiceImpl.start`:

```text
start Analysis
→ resolve persisted Intent
→ resolve task type
→ collect knowledge
→ generate Facts/Observations
→ build Project Profile
→ build AnalysisContext
→ select knowledge
→ persist snapshots/digests
→ submit PromptRequest
```

`AnalysisAiTaskTypeResolver` maps `outputProposalType = INSIGHT` to `INSIGHT_GENERATION`. It does not branch on architecture intent.

Both `describe-project-v1` and `architecture-overview-v1` therefore share:

- `GENERIC` execution;
- project scope;
- legacy `ARCHITECTURE_REVIEW` Analysis type;
- `INSIGHT_GENERATION` task type;
- `InsightPromptBuilder`;
- `InsightGenerationService`;
- proposal callback and persistence.

Intent identity, prompt template, context profiles, allowed categories, and architecture-only delta validation provide the semantic differentiation. A new task type is not required by current evidence.

Primary code:

- `backend/src/main/java/com/hopeful117/devlogai/analysis/workflow/AnalysisWorkflowServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/workflow/AnalysisAiTaskTypeResolver.java`

### 4. Context construction and knowledge selection

`AnalysisContextServiceImpl` builds project context, current Facts/Observations, prior analyses, architecture artifacts, decisions, relationships, and human context.

`KnowledgeSelectionServiceImpl.select` applies architecture-specific ranking and selects:

- bounded Facts and Observations;
- up to 10 active prior Insights;
- up to 5 active trusted architecture-relevant Insights as `existingArchitectureKnowledge`;
- repository context using architecture/history profiles.

`SemanticSectionComposer` places existing architecture knowledge into the `ARCHITECTURE` section. `SelectedKnowledgePromptProjectionService` projects it into the persisted task snapshot and `PromptRequest`.

Primary code:

- `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SemanticSectionComposer.java`
- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledgePromptProjectionService.java`

### 5. PromptRequest and AI routing

Core persists `contextSnapshot`, `selectedKnowledgeSnapshot`, Intent identity, prompt request identity, and selection digest on `AiTask`, then sends a provider-independent `PromptRequest` containing:

```text
request/correlation IDs
analysis/task IDs
task type
Intent snapshot
User Guidance
SelectedKnowledge
expected output contract
metadata
```

AI Engine routes every `INSIGHT_GENERATION` task to `InsightGenerationService`, which selects behavior by the versioned Intent and prompt template.

Primary code:

- `backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTask.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/PromptRequest.java`
- `ai-engine/app/services/task_processing_service.py`
- `ai-engine/app/services/insight_generation_service.py`

### 6. Architecture prompt and structured output

`InsightPromptBuilder` requires `existingArchitectureKnowledge` for architecture overview and instructs the model to:

- compare selected evidence with trusted architecture knowledge;
- return only material `NEW` or `ENRICHES` architecture deltas;
- target exactly one supplied trusted Insight for `ENRICHES`;
- return `proposals: []` when nothing materially new is learned.

The exact output model is:

```text
InsightGenerationOutput
└── proposals: list[InsightProposalOutput]
```

Each proposal contains:

```text
insightType
title
summary
rationale
deltaType
targetInsightId (ENRICHES only)
confidence
supportingFactIds
supportingObservationIds
evidenceReferences
```

`extra = forbid` prevents an unmodeled synthesis or no-delta explanation from surviving structured parsing.

Primary code:

- `ai-engine/app/prompts/insight.py`
- `ai-engine/app/schemas/insight.py`
- `ai-engine/app/services/insight_generation_service.py`

## Current AI Output Semantics

```text
OUTPUT_MODEL = InsightGenerationOutput { proposals: list[InsightProposalOutput] }
OUTPUT_MEANING = candidate NEW/ENRICHES Insight deltas requiring human validation
IS_OUTPUT_DELTA_ONLY = YES
CAN_OUTPUT_REPRESENT_CURRENT_STATE_SYNTHESIS = NO
CAN_OUTPUT_REPRESENT_NO_DELTA_EXPLANATION = NO
CAN_OUTPUT_REPRESENT_BOTH_SYNTHESIS_AND_PROPOSALS = NO
```

A proposal `summary` cannot safely stand in for synthesis because it semantically represents candidate knowledge and enters human validation/promotion. Returning a known-state overview as a proposal would create duplicate review work and risk promoting presentation text as project knowledge.

## Callback and Proposal Persistence

On successful generation, AI Engine sends `AiTaskResultRequest` containing:

```text
correlation/external job identity
status and completion time
proposals[]
error
prompt execution metadata
```

There is no synthesis/result-output field.

`AiTaskResultServiceImpl` validates callback state, grounding ownership, output contract, and architecture delta targets. It then atomically persists one `ValidatableProposal(PROPOSED)` per AI proposal and marks the task and Analysis completed.

A completed callback with `proposals = []` creates no proposal and still marks execution successful.

Primary code:

- `ai-engine/app/schemas/ai_task_result.py`
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/AiTaskResultRequest.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiTaskResultServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`
- `backend/src/main/java/com/hopeful117/devlogai/proposal/entity/ValidatableProposal.java`

## Proposal Lifecycle

Architecture proposals follow the established lifecycle:

```text
structured AI delta
→ ValidatableProposal(PROPOSED)
→ POST /api/v1/validations
→ ACCEPTED or REJECTED
→ if ACCEPTED, atomically promote to ACTIVE Insight
→ if ENRICHES, add DERIVED_FROM relation to target Insight
```

The target trusted Insight is not overwritten. Promotion creates a new trusted Insight with proposal/validation/analysis provenance.

Primary code:

- `backend/src/main/java/com/hopeful117/devlogai/validation/service/ValidationServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/validation/service/ProposalPromotionService.java`
- `backend/src/main/java/com/hopeful117/devlogai/insight/service/InsightPromotionService.java`

This lifecycle must remain unchanged for deltas. Synthesis must not enter it.

## Current AnalysisResult Semantics

`GET /api/v1/analyses/{id}/result` invokes `AnalysisResultQueryServiceImpl.getResult` and returns `AnalysisResultResponse`.

| Concern | Current support | Evidence |
| --- | --- | --- |
| Execution state | `SUPPORTED` | `analysis` and `execution` sections |
| Proposal results | `SUPPORTED` | proposal counts/items, status, rationale, delta type, grounding IDs |
| Supporting evidence | `SUPPORTED` | bounded evidence categories including `architectureKnowledge` |
| Current-state synthesis | `NOT_SUPPORTED` | no top-level answer/synthesis field |
| No-delta explanation | `NOT_SUPPORTED` | zero proposals only; no reason field |

Top-level fields are:

```text
analysis
execution
proposals
insights
deliverables
evidence
nextActions
```

There is no persisted AI result payload from which the query service could project a synthesis. Existing trusted architecture reaches the result only as a bounded evidence preview, not as an answer.

Primary code:

- `backend/src/main/java/com/hopeful117/devlogai/analysis/controller/AnalysisController.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/result/service/AnalysisResultQueryServiceImpl.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/result/dto/AnalysisResultResponse.java`

## Existing Architecture Knowledge Trace

### To the model

```text
accepted proposal
→ ACTIVE trusted Insight
→ project active-Insight query
→ architecture relevance filter
→ ExistingArchitectureKnowledgeSnapshot (maximum 5)
→ SelectedKnowledge.existingArchitectureKnowledge
→ persisted AI task snapshot
→ PromptRequest
→ architecture comparison baseline and valid ENRICHES targets
```

### To the canonical result

```text
persisted AI task selectedKnowledgeSnapshot
→ historical selected-evidence projection
→ result evidence.architectureKnowledge
```

The result path previews trusted items as evidence. It does not synthesize responsibilities, relationships, topology, boundaries, or principles.

## Why the Benchmark Result Is Product-Insufficient

The current implementation behaves correctly according to the delta contract:

```text
no material new/enriching knowledge
→ proposals = []
→ successful Analysis
```

It behaves incorrectly according to the user-facing product objective:

```text
user requests architecture overview
→ result says zero proposals
→ existing architecture appears only as evidence
→ no current-state answer or no-delta explanation
```

The benchmark proves this is not primarily missing context. Stable architecture information reached the model and result evidence in all three runs.

## Prompt-Only Hypothesis

```text
PROMPT_ONLY_SOLUTION = INSUFFICIENT
```

A prompt-only solution cannot cleanly represent the required semantics:

- the strict output root contains only `proposals`;
- extra synthesis fields are forbidden;
- callback transport contains only proposals and metadata;
- Core persists only proposals and task execution metadata;
- canonical result has no synthesis field;
- using a proposal for synthesis violates the delta meaning and risks inappropriate promotion.

Architecture-specific prompt changes may be required during implementation, but only as part of a new output contract, not as prompt tuning within the current proposal-only contract.

## Output Contract Gap

```text
OUTPUT_CONTRACT_GAP = YES
```

The current contract cannot represent:

```text
mandatory current-state synthesis
+
optional architecture delta proposals
```

It also cannot represent a grounded insufficient-evidence answer or a no-material-delta explanation.

ADR-028 requires a new Intent version when prompt semantics or output schema changes. The exact v1-to-new-version transition must be approved by the prerequisite ADR.

## Result Projection Gap

```text
RESULT_PROJECTION_GAP = PARTIAL
```

The AI does not currently produce synthesis, so `AnalysisResult` is not discarding an existing synthesis. However, the result model cannot represent or retrieve one after the AI contract is extended. A focused canonical result change is therefore required as a consequence of the output-contract correction, not as an independent evidence-reconstruction solution.

## Persistence Analysis

### Requirements

Current-state synthesis must survive:

- asynchronous callback completion;
- later `GET /result` requests;
- process restart;
- historical Analysis inspection;
- changes to current trusted knowledge after the Analysis;
- future consumers reading the same canonical result.

Regenerating on read would add model calls, break point-in-time repeatability, and allow historical results to drift. Reconstructing prose deterministically in Core would move semantic synthesis into the wrong responsibility.

```text
SYNTHESIS_PERSISTENCE_REQUIRED = YES
```

### Options

| Option | Description | Verdict |
| --- | --- | --- |
| A | Transient AI response only | Rejected: unavailable to later canonical reads |
| B | Persist as `ValidatableProposal` | Rejected: wrong lifecycle and trust semantics |
| C | Persist immutable non-trusted output snapshot on the Analysis execution task boundary | Selected direction, subject to ADR |
| D | Reconstruct through existing evidence structures | Rejected: enumeration is not synthesis and historical reads could drift |
| E | Dedicated architecture-synthesis aggregate/table | Deferred: unnecessary lifecycle and query model for the demonstrated V1 need |

`AiTask` already owns immutable input snapshots and prompt/provider/model traceability for one execution. A nullable versioned result snapshot at that boundary is the smallest coherent persistence direction. Exact field/DTO names remain implementation design, not Story requirements.

The synthesis must not be inserted into selected knowledge or treated as future trusted context. Any future reuse policy requires the prerequisite ADR and must preserve the non-trusted label.

## Design Alternatives

### Alternative A — Prompt-only synthesis through existing proposals

Ask the model to emit one overview proposal even when no delta exists, or place the overview in a proposal summary.

Rejected because:

- it conflates current presentation with candidate new knowledge;
- it creates duplicate human-review work;
- acceptance could promote an overview as a trusted Insight;
- zero proposals would still erase the answer;
- the existing schema cannot carry answer-plus-deltas separately.

### Alternative B — One architecture-specific structured output

Generate one mandatory synthesis and zero or more optional delta proposals in one structured provider call.

Selected because:

- directly represents the product distinction;
- uses one selected context and one point-in-time interpretation;
- keeps valid-path model calls at one;
- preserves proposals as independently governed deltas;
- supports no-delta and delta scenarios;
- permits canonical persisted retrieval;
- avoids new task orchestration.

The output should be strict and typed. It should include bounded grounding references for synthesis claims and an explicit supported no-delta or delta conclusion. Exact shape is implementation design after ADR approval.

### Alternative C — Separate synthesis and proposal generation phases

Use separate model calls/tasks for current-state answer and delta detection.

Rejected for current scope because:

- duplicates substantially the same context and cost;
- increases latency;
- creates two task/callback lifecycles or phase orchestration;
- creates context/version drift risk;
- requires broader partial-success and Analysis completion semantics;
- no evidence shows separate models or contexts are necessary.

### Alternative D — Deterministic Core synthesis from trusted architecture evidence

Project the five trusted architecture items as the answer or concatenate evidence summaries.

Rejected as the product solution because:

- it remains an inventory rather than a mental model;
- deterministic Java would need to infer responsibilities and relationships;
- ADR-064 assigns natural-language interpretation and synthesis to AI;
- it cannot adequately explain architecture gaps or topology from heterogeneous evidence.

The existing evidence preview remains useful supporting material, not the answer.

### Alternative E — Dedicated synthesis domain aggregate

Create an independently queryable, versioned Architecture Synthesis entity.

Rejected for current scope because no independent editing, sharing, review, search, lifecycle, or cross-Analysis query requirement is demonstrated. An execution-owned immutable snapshot is smaller and preserves historical auditability.

## Selected Recommendation

Subject to the prerequisite ADR:

1. Define versioned Architecture Overview product semantics that require a current-state synthesis and permit zero or more architecture delta proposals.
2. Keep `INSIGHT_GENERATION`; branch structured output behavior by exact Intent identity/version.
3. Generate synthesis and deltas in one strict structured model response.
4. Validate synthesis grounding and proposal contracts independently before one atomic callback persistence operation.
5. Persist synthesis as an immutable, non-trusted, Analysis-execution-scoped task result snapshot.
6. Continue persisting only deltas as `ValidatableProposal` records.
7. Project synthesis through the canonical `AnalysisResultResponse` with explicit non-trusted authority and point-in-time semantics.
8. Keep the first implementation architecture-specific; do not add a generic answer framework.

## AI Call Count

```text
AI_CALLS_EXPECTED_PER_VALID_ANALYSIS = 1
```

The same selected context supports synthesis and delta comparison. There is no current justification for two calls. Existing bounded corrective behavior may add a call only after invalid structured output.

## Failure Semantics Assessment

The one-response design permits four logical states:

| Synthesis | Proposals | Required decision |
| --- | --- | --- |
| Valid | Valid | Complete and persist both outputs atomically |
| Valid | Invalid | Correct, then either fail atomically or use ADR-governed partial success |
| Invalid | Valid | Correct, then either fail atomically or use ADR-governed partial success |
| Invalid | Invalid | Correct, then fail if still invalid |

Current ADR-020 callback semantics are atomic around proposal creation and task completion and have no partial-success state. The smallest compatible baseline is all-or-nothing after corrective generation. This can hide a valid synthesis because an optional delta is invalid, so it must be an explicit ADR decision rather than an accidental service behavior.

No implementation may invent a warning-only, proposal-dropping, or partial-completion policy without that decision.

## Backward Compatibility

### Historical persistence

- Historical AI tasks have no synthesis snapshot and must remain readable.
- Historical `AnalysisResult` responses must represent synthesis as absent, not corrupt or reconstruct it from current state.
- New persistence must be nullable for historical rows.

### Intent versioning

- ADR-028 prohibits changing v1 output schema or prompt semantics in place.
- The default direction is a new versioned Architecture Overview Intent.
- The prerequisite ADR must decide canonical launch migration and whether old v1 remains executable, deprecated, or hidden from new objective selection.

### Callback compatibility

- Existing AI task callbacks for other Intents must remain valid.
- New synthesis transport must be absent/forbidden for Intents that do not declare it.
- Core must validate the exact Intent/output pairing rather than trusting a generic JSON payload.

### Other `INSIGHT_GENERATION` intents

- `describe-project-v1` must continue to use `InsightGenerationOutput` and proposal-only handling.
- Shared service changes must branch on exact versioned Intent and have explicit regression tests.

### API consumers

- A new optional architecture-synthesis section is additive for historical/other-intent results.
- Exact wire compatibility and version exposure require contract tests.
- Frontend rendering is explicitly outside this Story.

## Task-Type Coupling Verdict

```text
NEW_TASK_TYPE_REQUIRED = NO
```

`INSIGHT_GENERATION` currently describes the shared execution infrastructure, while Intent carries product semantics. Adding a new task type solely for an architecture-specific output would duplicate routing and orchestration without evidence of a different lifecycle, model, queue, or authorization boundary.

The shared Python service will need intent-specific output-model selection. This is acceptable only if v1 behavior remains explicitly covered and the architecture branch remains bounded.

## Generalization Boundary

```text
ARCHITECTURE_SPECIFIC_SOLUTION = SUFFICIENT
GENERIC_ANALYSIS_SYNTHESIS_CONCEPT = FUTURE_CANDIDATE
```

Only Architecture Overview currently has benchmark evidence and approved scope for answer-plus-delta semantics. The implementation may need a minimal optional transport/persistence slot, but the payload, validation, and canonical projection should remain typed and architecture-specific.

A future cross-intent answer abstraction requires separate evidence and HUMAN review.

## Trust-Boundary Implications

The synthesis is:

- AI-generated;
- grounded on the immutable selected task snapshot;
- point-in-time and execution-scoped;
- non-authoritative;
- non-promotable;
- not project knowledge;
- not a replacement for accepted Insights or Decisions;
- not automatically eligible as future trusted context.

Architecture proposals remain:

- AI-generated candidate knowledge;
- persisted as `ValidatableProposal`;
- reviewable and rejectable;
- promotable only after human acceptance.

This distinction is the reason a new ADR is required.

## Expected Change Surface

The exact implementation remains subject to ADR approval and repository re-verification. Expected areas are:

### AI Engine

- versioned architecture-specific structured output schema;
- architecture-specific prompt registration/semantics;
- `InsightGenerationService` output-model selection, synthesis validation, and callback mapping;
- callback schema extension for the architecture result snapshot.

### Java Core

- versioned Intent registration/output contract;
- callback DTO and exact Intent/output validation;
- immutable AI-task result snapshot persistence and migration;
- atomic callback persistence with existing proposals;
- typed canonical Architecture Overview result projection.

### Explicitly excluded

- frontend;
- MCP;
- other Intent behavior;
- collection, selection, Semantic Sections, or Fact ranking;
- proposal validation/promotion lifecycle changes.

## Test Strategy

### AI Engine

- strict schema: mandatory synthesis, optional proposal list, forbidden extras, bounded fields;
- sufficient-evidence synthesis with zero proposals;
- synthesis plus valid `NEW` proposal;
- synthesis plus valid `ENRICHES` proposal;
- unsupported synthesis reference rejection;
- insufficient-evidence representation;
- no rediscovery of trusted architecture as `NEW`;
- corrective output behavior and terminal failure;
- exact v1 behavior unchanged;
- one provider call on valid output.

### Java Core

- Intent version/output contract resolution;
- callback compatibility for existing Intents;
- required synthesis for the new Architecture Overview contract;
- rejection of synthesis for undeclared Intents;
- synthesis grounding validation against the selected snapshot;
- atomic persistence of synthesis, proposals, task completion, and Analysis completion;
- duplicate callback idempotency;
- historical nullable snapshot persistence/read compatibility;
- canonical completed-result projection with zero proposals;
- canonical completed-result projection with synthesis plus proposals;
- pending, failed, historical v1, and other-intent result behavior;
- no trusted artifact creation from synthesis;
- unchanged proposal acceptance/promotion behavior.

### Runtime product benchmark

- three fresh canonical executions;
- one stable project/repository state;
- same provider/model/configuration;
- synthesis scored for components, responsibilities, relationships, boundaries, principles, grounding, and uncertainty;
- delta correctness scored separately;
- no-delta result remains useful;
- model-call count captured.

## Risks

1. Synthesis may be mistaken for trusted knowledge if authority is not explicit in contract and documentation.
2. Changing v1 in place would violate Intent immutability.
3. A generic unvalidated JSON result envelope could weaken Core contract ownership.
4. Combined-output atomicity may hide a valid synthesis after proposal failure.
5. Partial success would broaden Analysis/task lifecycle semantics substantially.
6. Historical result reads could drift if synthesis is reconstructed from current knowledge.
7. Shared `INSIGHT_GENERATION` changes could regress Describe Project without exact intent tests.
8. Synthesis grounding could recreate identity/allow-list failure modes if references are not bounded and typed.
9. Treating synthesis as future input could allow unvalidated interpretation to influence later analyses.
10. Expanding into frontend or generic answer infrastructure would exceed the demonstrated product boundary.

## ADR Assessment

```text
NEW_ADR_REQUIRED = YES
ADR_REQUIRED_BEFORE_IMPLEMENTATION = YES
```

The architectural decision is not merely a DTO extension. It introduces an AI-generated, persisted, canonical, non-proposal, non-trusted result artifact. Existing ADR-006 and ADR-020 say AI-generated outputs remain proposals and the callback creates only proposals. ADR-063 section 29 and ADR-064's relations explicitly retain proposal governance for AI synthesis/output. ADR-028 also requires a new Intent version for changed semantics/schema. The prerequisite ADR must identify the clauses it supersedes or refines while preserving human validation for knowledge deltas and keeping ADR-064 composition work paused.

The required ADR must be reviewed by a HUMAN and is not created automatically by this Story.

## Scope Conclusion

Repository evidence confirms the benchmark diagnosis:

```text
CONTEXT_AVAILABILITY = SUFFICIENT FOR CURRENT STORY
PRIMARY_GAP = OUTPUT CONTRACT + SYNTHESIS SEMANTICS
CONTRADICTORY_PRODUCT_OR_IMPLEMENTATION_EVIDENCE = NONE
GOVERNING_ADR_CONFLICT = CONFIRMED / REQUIRES PREREQUISITE ADR
ADR_064 = KEEP_PAUSED
```

Story 0108 is coherent as a product Story, but implementation requires the prerequisite architectural decision.

Terminal state:

`ARCHITECTURE_OVERVIEW_SYNTHESIS_REQUIRES_ADR_HUMAN_REVIEW`
