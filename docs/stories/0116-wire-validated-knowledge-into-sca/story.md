# Story 0116 — Wire Validated Knowledge into Story Context Analysis

## Status

**IMPLEMENTED — AWAITING HUMAN REVIEW**

## Baseline

- Baseline SHA: `982c538` (Story 0115 merge commit on `main`)
- Baseline branch: `main`
- Implementation branch: `story/0116-wire-validated-knowledge-into-sca`
- Governing design: `docs/investigations/validated-knowledge-sca-integration-design.md`
- Governing ADRs: ADR-006, ADR-063, ADR-066, ADR-067
- Story 0115: **ACCEPTED** — Story-Aware Knowledge Selection

## Problem

Story 0115 implemented deterministic Story-aware ranking in `KnowledgeSelectionService`, but `AnalyzeStoryContextUseCase.buildSelectedKnowledge()` still hard-codes empty selected knowledge lists:

```text
selectedFacts = []
selectedObservations = []
selectedInsights = []
```

Story Context Analysis therefore executes with no validated project knowledge, defeating the purpose of the selection pipeline.

The current grounding path also has pre-existing defects:
- Java builds a grounding contract from `EngineeringEvidence.identifier` and `relatedReferences`, but `AiTaskServiceImpl.createForStoryContextAnalysisEntity()` accepts and ignores it
- Python reconstructs an allow-list from `repositoryContext.evidence[]` instead of consuming the Java contract
- `RepositoryEvidence.reference` is the canonical evidence identity, but `EngineeringContextContractMapper` maps only `provenance.identifier` into `EngineeringEvidence.identifier`
- Callbacks do not perform authoritative Java grounding revalidation (Story 0112 D14, ADR-067)

## Goal

Wire the existing Story-aware `KnowledgeSelectionService` into `AnalyzeStoryContextUseCase` so Story Context Analysis receives actual selected validated knowledge, while preserving Java/Core authority over grounding through the accepted `PROVENANCE_INTERSECTION` model.

```text
Engineering Story
        ↓
EngineeringContext
        ↓
Story-aware KnowledgeSelectionService
        ↓
SelectedKnowledge
        ↓
Story Context Analysis AI task
        ↓
AI analysis
        ↓
Core callback validation (authoritative)
        ↓
persisted StoryContextAnalysis
```

## Scope

### In Scope

- Replace `AnalyzeStoryContextUseCase.buildSelectedKnowledge()` empty map with `KnowledgeSelectionService.select()` projection
- Establish proper Analysis/diagnostics lifecycle before selection (selector requires valid AnalysisContext)
- Persist and transport the Java-authored immutable grounding contract with the AI task
- Add `reference` field to `EngineeringEvidence` preserving canonical `RepositoryEvidence.reference`
- Add grounding contract to `PromptRequest` so Python receives authoritative contract
- Implement authoritative Java callback validation: evidence subset, required grounding for factual/interpretative findings, relationType validity, classification validity, context digest consistency, forbidden outputs
- Remove Python-side grounding contract reconstruction; consume Java contract only
- Preserve Story 0114 freshness capture and Story 0115 selection semantics unchanged
- Preserve existing `StoryContextAnalysisResult` output schema

### Explicit Non-Goals

- RAG, vector search, embeddings, semantic retrieval
- New retrieval services or new AI agents
- Proactive communication, alerting, Presence Device, WebSocket/SSE/MQTT
- Per-item freshness, fact/insight invalidation, TemporalAssessment integration
- Maintenance automation
- Decisions/EngineeringEvents as selected knowledge (deferred per design)
- Story persistence or Markdown parsing redesign
- Historical backfill, local worktree awareness
- OpenClaw integration, generic agent frameworks
- Frontend changes

## Design

Per `docs/investigations/validated-knowledge-sca-integration-design.md`:

### 1. Selection Integration

`AnalyzeStoryContextUseCase.execute()` flow:

```text
1. Build EngineeringContext (existing)
2. Synthesize AnalysisContext from EngineeringContext for SCA intent
   - Use latest ProjectProfile Analysis as coherent baseline (per RepositoryContextAdapter pattern)
   - Include current Story only in engineeringStories
   - Include selected Facts/Observations from RepositoryContextAdapter bounded candidates
   - Include ACTIVE Insights from project
3. Call KnowledgeSelectionService.select(context, intent, guidance)
4. Project SelectedKnowledge to PromptRequest map
5. Construct Java grounding contract from EngineeringContext canonical evidence references
6. Create AiTask with selectedKnowledge + groundingContract
7. Submit task → send to Python
```

### 2. Grounding Contract

Java constructs `allowedEvidenceReferences` exclusively from canonical references of citable evidence in the authorized, scoped `EngineeringContext`. Scope and citability remain Java decisions; trust tier is preserved but does not alone imply citability.

The contract is persisted with the task, sent in `PromptRequest`, and reused for authoritative callback validation.

Python receives the explicit contract and uses it for prompt instructions, defensive subset validation, and corrective retry. It must not scan `selectedKnowledge` to create additional allowed references.

### 3. Finding Requirements

- Every `FACTUAL_EXTRACTION` and `AI_INTERPRETATION` must have `grounded=true` and at least one evidence reference from the Java allow-list
- `RECOMMENDATION` may be ungrounded but must remain explicitly classified
- Unknown or fabricated references are rejected defensively in Python and authoritatively in Java
- `EvidenceRef.resource` remains navigation-only and does not enter subset validation
- Related references do not become citable unless independently authorized canonically

### 4. Callback Validation (Authoritative Java)

On callback, Java must validate:
- All evidence references in findings are in the allowed set from the grounding contract
- Grounding classifications are valid enum values
- `relationType` is present and valid for relationship-bearing findings
- Uncertainty evidence references are in allowed set
- Output classification entries are valid
- Context digest matches
- No forbidden outputs (proposals, trusted knowledge claims)

### 5. Canonical Reference Preservation

Add `reference` field to `EngineeringEvidence` carrying `RepositoryEvidence.reference` (canonical evidence identity). `identifier` remains `provenance.identifier` (knowledge provenance). Both are distinct per Story 0112 D13 and ADR-063.

## Acceptance Criteria

### AC1 — Non-Empty Selected Knowledge Reaches SCA

When `AnalyzeStoryContextUseCase.execute()` runs for a project with historical Facts/Observations/Insights, the `PromptRequest.selectedKnowledge` contains non-empty `selectedFacts`, `selectedObservations`, and `selectedInsights` produced by `KnowledgeSelectionService`.

### AC2 — Selection Is Story-Aware

For the `engineering-story-context-analysis` intent, selected Facts/Observations/Insights reflect Story 0115 relevance signals (Story title, path, requested files) before recency.

### AC3 — Java Constructs Grounding Contract

The `PromptRequest` carries an explicit `groundingContract` map with `allowedEvidenceReferences` built by Java from authorized `EngineeringContext` evidence. Python does not reconstruct it.

### AC4 — Python Consumes Java Grounding Contract

`StoryContextAnalysisPromptBuilder._grounding_contract()` returns the Java-provided contract from `PromptRequest` rather than reconstructing from `repositoryContext`.

### AC5 — Python Defensive Validation Uses Java Contract

`StoryContextAnalysisGenerationService._validate_output()` validates evidence references against the Java-provided allow-list, not a locally reconstructed one.

### AC6 — Authoritative Java Callback Validation

`AnalyzeStoryContextUseCase.handleCallback()` validates before persistence:
- All finding evidence references ⊆ Java allow-list
- Factual/interpretative findings have `grounded=true` and ≥1 evidence reference
- `relationType` present and valid for ArchitectureFinding, DecisionFinding, HistoricalContextItem, ImpactedComponentFinding
- Classification ∈ {FACTUAL_EXTRACTION, AI_INTERPRETATION, RECOMMENDATION}
- Uncertainty references ⊆ allow-list
- Context digest consistency
- No proposals in output

Invalid output is rejected; no silent trust crossing.

### AC7 — Canonical Reference Preserved

`EngineeringEvidence.reference` carries `RepositoryEvidence.reference`. `EngineeringEvidence.identifier` remains `provenance.identifier`. Both are distinct.

### AC8 — Freshness and Trust Unchanged

Story 0114 freshness snapshot capture/persistence unchanged. Story 0115 selection version (`v5`) and `ENGINEERING_STORY_RELEVANCE` rule emitted for SCA intent.

### AC9 — Output Schema Stable

`StoryContextAnalysisResult` output schema unchanged. No new fields required.

### AC10 — Quality Gates

Focused tests, full backend verification with coverage, full AI Engine regression, `git diff --check` pass. No generated artifacts committed.

## Test Expectations

- `AnalyzeStoryContextUseCaseTest`: selected knowledge non-empty, grounding contract present, callback validation rejects invalid references, accepts valid ones
- `KnowledgeSelectionServiceTest`: Story 0115 regression (Story relevance, budgets, closure, lifecycle)
- `StoryContextAnalysisGenerationServiceTest`: Python consumes Java contract, defensive validation uses it
- `StoryContextAnalysisPromptBuilderTest`: grounding contract from PromptRequest
- ADR-066 scenario `engineering-story-context-analysis-v1`: provenance intersection, Story ranking, closure, empty context, freshness regression

## Lifecycle State

- Story materialization: completed by this task
- Repository analysis: completed
- Human design review: completed through governing design revision
- Human implementation authorization: granted by task request
- Implementation: completed
- Verification: completed (focused, backend, AI Engine)
- Human acceptance: pending
- Commit: authorized only after implementation and validation pass
- Push: not authorized
- Merge: human-only

Terminal implementation state:

`STORY_0116_IMPLEMENTED_AWAITING_HUMAN_REVIEW`