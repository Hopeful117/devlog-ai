# Story 0108 — Implementation Plan

## Status

**PLANNED — BLOCKED ON PREREQUISITE ADR AND HUMAN DESIGN APPROVAL**

## Authorization

This plan defines anticipated work only.

```text
IMPLEMENTATION_AUTHORIZED = NO
ADR_REQUIRED_BEFORE_IMPLEMENTATION = YES
PRODUCTION_CHANGES_IN_THIS_EXECUTION = NO
TEST_CHANGES_IN_THIS_EXECUTION = NO
```

No implementation step may begin until the prerequisite ADR and Story design are approved by a HUMAN.

## Planned Outcome

One canonical Architecture Overview execution should produce, in one valid structured model call:

```text
mandatory grounded current-state synthesis
+
zero or more governed NEW/ENRICHES architecture proposals
```

The synthesis is persisted as a non-trusted point-in-time Analysis-task result and projected through the canonical Analysis result endpoint. Proposals retain their current persistence, validation, and promotion lifecycle.

## Plan Preconditions

1. A HUMAN-reviewed ADR defines synthesis authority, persistence, retention, callback semantics, failure atomicity, grounding, future reuse, Intent-version transition, and explicit reconciliation with ADR-006, ADR-020, ADR-063, and ADR-064.
2. Story 0108 design is approved.
3. Baseline and current contracts are re-verified before implementation.
4. Exact next Flyway migration number is re-verified.
5. No parallel change has introduced an existing canonical answer artifact.

## Phase 1 — Establish Versioned Product Contract

### Expected component

- `backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java`
- existing Intent output-contract model under `backend/src/main/java/com/hopeful117/devlogai/intent/model/`
- focused Intent catalog/contract tests

### Why

The current `architecture-overview-v1` contract defines only Insight proposals. ADR-028 requires new Intent versioning for changed prompt semantics and output schema.

### Responsibility

Core defines the provider-independent business objective, required synthesis output, optional architecture delta contract, allowed architecture proposal types, and exact version identity.

### Expected behavior

- Resolve the ADR-approved Architecture Overview version.
- Require current-state synthesis for completed output.
- Continue allowing zero to ten `NEW`/`ENRICHES` architecture proposals.
- Keep old v1 contract immutable and historical results readable.
- Keep `describe-project-v1` and decision contracts unchanged.

### Test coverage

- exact version/key resolution;
- required output-contract semantics;
- old v1 immutability;
- unsupported version rejection;
- other Intent snapshots unchanged.

## Phase 2 — Define Architecture-Specific Structured AI Output

### Expected components

- new architecture-specific schema module under `ai-engine/app/schemas/`
- `ai-engine/app/prompts/insight.py`
- existing prompt-builder tests
- schema tests under `ai-engine/tests/`

### Why

`InsightGenerationOutput` contains only `proposals[]` and cannot represent synthesis, no-delta explanation, or insufficient architecture evidence.

### Responsibility

AI Engine owns a strict architecture-specific response model and architecture-specific prompt semantics. Core's Intent snapshot remains the business contract.

### Expected behavior

- Require one synthesis result.
- Represent components, responsibilities, relationships, boundaries, principles, supported topology/persistence, limitations, and delta conclusion in a bounded form approved by the ADR.
- Permit categories to be empty when evidence is absent.
- Carry bounded grounding references for substantive claims.
- Carry zero or more existing architecture proposal outputs without changing their delta fields.
- Forbid extra fields.
- Use one provider call for valid output.

### Test coverage

- valid sufficient-evidence synthesis with no delta;
- valid synthesis plus `NEW` delta;
- valid synthesis plus `ENRICHES` delta;
- insufficient-evidence synthesis;
- missing synthesis rejection;
- unsupported/unknown grounding rejection;
- forbidden extra fields;
- output bounds;
- exact architecture version/template matching;
- unchanged v1 and Describe Project prompt/output behavior.

## Phase 3 — Generate and Validate One Combined Output

### Expected component

- `ai-engine/app/services/insight_generation_service.py`
- focused service tests

### Why

All `INSIGHT_GENERATION` intents currently use one proposal-only output model and one service. Architecture Overview requires exact Intent-specific output selection without creating a duplicate task pipeline.

### Responsibility

Select the structured response model by exact versioned Intent, validate synthesis references and proposal contracts, apply the ADR-approved correction/failure policy, and map both output categories to the callback.

### Expected behavior

- Valid architecture output takes one model call.
- Synthesis and proposals use the same prompt/context digest.
- Synthesis grounding validates against selected Facts, Observations, trusted Insights, and exact repository references permitted by the ADR-approved contract.
- Delta proposals retain current grounding and target validation.
- Invalid combined output follows bounded corrective behavior.
- No behavior changes for other Intents.

### Test coverage

- exact Intent branch;
- one valid-path provider call;
- synthesis validation;
- proposal validation;
- correction after invalid synthesis;
- correction after invalid proposal;
- terminal invalid-output behavior;
- no proposal emitted for known architecture only;
- no callback synthesis for old/other Intent contracts.

## Phase 4 — Extend Callback Contract Minimally

### Expected components

- `ai-engine/app/schemas/ai_task_result.py`
- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/AiTaskResultRequest.java`
- one typed architecture-output callback DTO or record under the same Core package
- callback/controller contract tests

### Why

The current callback transports only proposals and execution metadata. A transient AI-only synthesis would be inaccessible to canonical Core consumers.

### Responsibility

Transport one typed, versioned architecture result snapshot alongside proposals while preserving existing callback correlation, idempotency, failure, and compatibility rules.

### Expected behavior

- Existing callbacks remain valid without architecture output.
- Completed ADR-approved Architecture Overview callbacks require the matching output.
- Other Intent callbacks reject architecture output.
- Failed callbacks do not claim a completed synthesis.
- Output kind/version is explicit and validated by Core.
- No generic arbitrary map is accepted without exact Intent validation.

### Test coverage

- existing callback JSON compatibility;
- required architecture output;
- missing output rejection;
- wrong version/kind rejection;
- output on undeclared Intent rejection;
- duplicate callback behavior;
- failed callback behavior.

## Phase 5 — Persist Immutable Execution-Scoped Synthesis

### Expected components

- `backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTask.java`
- next verified Flyway migration under `backend/src/main/resources/db/migration/`
- AI task persistence/repository integration tests

### Why

Canonical historical result retrieval requires the exact point-in-time synthesis without another model call or reconstruction from mutable current knowledge.

### Responsibility

Persist a nullable, immutable, versioned, non-trusted architecture result snapshot at the AI task execution boundary chosen by the prerequisite ADR.

### Expected behavior

- Historical rows remain valid with no snapshot.
- New Architecture Overview completion stores one snapshot.
- Snapshot includes explicit type/version/authority metadata.
- Snapshot cannot be mistaken for trusted project knowledge.
- Snapshot is not inserted into selected knowledge.
- Later changes to trusted Insights do not alter the historical answer.
- Retention and deletion follow the ADR-approved Analysis/AiTask ownership policy and do not outlive or detach from their owning execution without an explicit lifecycle decision.

### Test coverage

- migration compatibility;
- JSON round trip;
- historical null read;
- immutability expectations;
- point-in-time persistence;
- ADR-approved retention/deletion behavior;
- no trusted-domain write.

## Phase 6 — Validate and Persist Callback Atomically

### Expected components

- `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiTaskResultServiceImpl.java`
- focused typed output validator under the same service boundary if required
- `AiTaskResultServiceTest` and related integration tests

### Why

Core must enforce exact Intent/output compatibility, grounding, trust boundaries, and the ADR-approved transaction/failure policy before marking the Analysis completed.

### Responsibility

Validate synthesis and proposals, persist the synthesis snapshot and proposal records according to one transaction boundary, preserve idempotency, and complete task/Analysis state consistently.

### Expected behavior

- Valid no-delta output persists synthesis and zero proposals.
- Valid delta output persists synthesis and proposals.
- Synthesis never creates an `Insight` or other trusted artifact.
- Existing proposal persistence remains unchanged.
- Duplicate callbacks do not duplicate synthesis or proposals.
- Invalid output follows the prerequisite ADR's atomic or partial-success decision exactly.

### Test coverage

- no-delta persistence;
- synthesis-plus-proposal persistence;
- grounding rejection;
- intent/output mismatch;
- no knowledge promotion;
- atomic rollback on persistence failure;
- duplicate callback idempotency;
- approved divergent-validity cases;
- existing callback behavior unchanged.

## Phase 7 — Project Canonical Architecture Result

### Expected components

- `backend/src/main/java/com/hopeful117/devlogai/analysis/result/dto/AnalysisResultResponse.java`
- `backend/src/main/java/com/hopeful117/devlogai/analysis/result/service/AnalysisResultQueryServiceImpl.java`
- result/query/controller serialization tests

### Why

The current result exposes execution, proposals, accepted Insights, deliverables, and supporting evidence but no current-state answer or no-delta explanation.

### Responsibility

Project the persisted architecture result snapshot without reinterpretation through `GET /api/v1/analyses/{id}/result`.

### Expected behavior

- Completed ADR-approved Architecture Overview result includes typed current-state synthesis before/alongside proposal sections.
- Synthesis exposes explicit non-trusted authority and schema version.
- No-delta conclusion is explicit while proposal count remains zero.
- Delta conclusion and proposal list remain semantically separate.
- Pending, failed, historical v1, and other Intent results expose no fabricated synthesis.
- Result reads do not call AI or re-read current project knowledge to rebuild the answer.

### Test coverage

- completed no-delta result;
- completed delta result;
- explicit authority/version;
- insufficient-evidence result;
- historical result without synthesis;
- pending and failed results;
- other Intent compatibility;
- canonical endpoint serialization;
- no result-read model call.

## Phase 8 — Preserve Proposal Governance

### Expected components

- existing proposal contract/promotion tests, modified only if the new Intent version requires fixtures
- no planned production changes to validation or promotion services

### Why

The Story must prove that current-state presentation does not bypass ADR-006.

### Responsibility

Regression verification only: proposals remain independently reviewable and synthesis remains non-promotable.

### Expected behavior

- `NEW` and `ENRICHES` validation remains unchanged.
- Accepted proposals still promote atomically to trusted Insights.
- Rejected proposals create no trusted artifact.
- Synthesis has no validation endpoint or promotion path.

### Test coverage

- existing proposal validation/promotion suite;
- architecture enrichment target validation;
- explicit assertion that synthesis creates no proposal/Insight by itself.

## Phase 9 — Verification

### Focused automated verification

1. AI schema tests
2. AI prompt-builder tests
3. AI Insight generation service tests
4. Backend Intent contract tests
5. Backend callback DTO/controller tests
6. Backend callback service/validator tests
7. Backend persistence migration/integration tests
8. Backend Analysis result query/controller tests
9. Existing proposal validation/promotion regressions
10. Full AI Engine suite
11. Full backend suite
12. Backend clean verification and formatting/static checks required by repository convention

### Runtime product verification

Run at least three fresh canonical Architecture Overview executions against one stable logical repository state with the same provider/model/configuration.

Capture:

```text
analysisId
requestId
taskId
Intent/version
selected-knowledge digest
rendered prompt digest
synthesis output
proposal IDs
model-call count
```

Score separately:

```text
CURRENT_STATE_SYNTHESIS_QUALITY
DELTA_CORRECTNESS
```

The implementation does not pass merely because JSON is valid or proposals are empty.

## Planned File Boundary

The following are anticipated existing files, subject to ADR approval and baseline re-verification:

```text
backend/src/main/java/com/hopeful117/devlogai/intent/service/IntentCatalog.java
backend/src/main/java/com/hopeful117/devlogai/ai/engine/dto/AiTaskResultRequest.java
backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiTaskResultServiceImpl.java
backend/src/main/java/com/hopeful117/devlogai/ai/task/entity/AiTask.java
backend/src/main/java/com/hopeful117/devlogai/analysis/result/dto/AnalysisResultResponse.java
backend/src/main/java/com/hopeful117/devlogai/analysis/result/service/AnalysisResultQueryServiceImpl.java
backend/src/main/resources/db/migration/<next>__add_ai_task_architecture_result_snapshot.sql
ai-engine/app/schemas/<architecture-overview-output>.py
ai-engine/app/schemas/ai_task_result.py
ai-engine/app/prompts/insight.py
ai-engine/app/services/insight_generation_service.py
```

Potential new DTO/validator names are deliberately not fixed before the prerequisite ADR. No production file is modified during materialization.

## Expected Test Boundary

Anticipated focused suites include:

```text
AI Engine schema tests
AI Engine prompt-builder tests
AI Engine InsightGenerationService tests
Core IntentCatalog/output-contract tests
Core AiTaskResultController tests
Core AiTaskResultService tests
Core AI task persistence integration tests
Core AnalysisResultQueryService tests
Core AnalysisController result serialization tests
existing proposal validation/promotion regression tests
```

Exact files should reuse current test locations after implementation-baseline inspection. No test is modified during materialization.

## Explicitly Unchanged

- deterministic collection and AnalysisContext construction;
- Knowledge Selection scoring, budgets, and Fact ranking;
- Semantic Section composition;
- architecture trusted-knowledge selection;
- `ValidatableProposal` states;
- validation and promotion transaction;
- Describe Project grounding reliability;
- model-facing identity representation;
- Engineering Decision output;
- documentation selection/overflow;
- provider/model configuration;
- frontend;
- MCP;
- agents and Workspace orchestration;
- RAG/retrieval architecture;
- ADR-064 implementation sequence.

## Rollout and Compatibility Notes

- Do not mutate v1 semantics before the ADR resolves version transition.
- Keep historical result reads null-safe for absent synthesis.
- Keep callback changes additive for existing Intent versions.
- Do not expose the new contract as canonical until Core and AI Engine versions agree.
- Do not silently fall back from required synthesis to evidence enumeration.
- Do not infer synthesis at result-read time.

## Human Approval Checklist

Before implementation authorization, HUMAN review must approve:

- the prerequisite ADR scope;
- non-trusted synthesis authority;
- Intent version/migration behavior;
- one-call combined output;
- atomic versus partial-output failure semantics;
- task-owned immutable persistence;
- retention and deletion ownership;
- architecture-specific result shape;
- grounding requirements;
- exclusion from future trusted context unless separately governed;
- explicit frontend non-scope.

## Lifecycle State

- Plan materialized: yes
- ADR approved: no
- Human design approval: pending
- Implementation authorized: no
- Production changes: none
- Test changes: none
- Runtime implementation benchmark: not run

Terminal state:

`ARCHITECTURE_OVERVIEW_SYNTHESIS_REQUIRES_ADR_HUMAN_REVIEW`
