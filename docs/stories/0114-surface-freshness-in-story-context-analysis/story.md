# Story 0114 — Surface Freshness in Story Context Analysis

## Status

**DESIGN — AWAITING HUMAN REVIEW**

## Baseline

- Baseline SHA: `0a1c719`
- Baseline branch: `main`
- Working branch: `story/0114-surface-freshness-in-story-context-analysis`

## Problem

The Story Context Analysis produces structured, grounded analysis for
Discuss/Plan preparation. Its output — `StoryContextAnalysisResult` — is the
primary artifact consumed by engineering agents making implementation
decisions.

However, the analysis result contains no freshness awareness. When the
repository evolves after the engineering context was last refreshed, the
analysis can silently present stale knowledge as current truth. The consumer
has no mechanism to distinguish:

```text
analysis produced from fresh context
```

from:

```text
analysis produced from potentially stale context
```

This is a concrete instance of the broader temporal knowledge gap described
in ADR-059 (Proposed).

### Repository Evidence

**REPOSITORY FACT**: `EngineeringContext.metadata().freshness()` already
carries per-source freshness status (`CURRENT`, `STALE`, `PARTIALLY_FRESH`,
`NO_BASELINE`, `UNKNOWN`), repository revision, context revision, and
aggregate warnings.

**REPOSITORY FACT**: `AnalyzeStoryContextUseCase.buildSelectedKnowledge()`
discards `EngineeringContext.metadata()` entirely. The selectedKnowledge map
passed to the AI engine contains only `contextDigest` (a SHA-256 hash) and
raw evidence — no freshness status, no repository revision, no warnings.

**REPOSITORY FACT**: `StoryContextAnalysisResult` (the AI output contract)
has no field for freshness status, repository revision, or staleness
indication. The `StoryContextAnalysis` entity has no freshness metadata
beyond `createdAt`.

**REPOSITORY FACT**: The AI prompt for story context analysis
(`ai-engine/app/prompts/story_context_analysis.py`) contains no instruction
about freshness, staleness, or temporal validity. The LLM receives the
selectedKnowledge map without explicit guidance to consider whether the
context is fresh.

**REPOSITORY FACT**: The `SHARED_STRUCTURED_CONTEXT_CONTRACT` in
`ai-engine/app/prompts/structured_context.py` focuses on evidence grounding
but does not mention temporal validity or freshness.

**REPOSITORY FACT**: The `TemporalAssessmentService` provides on-demand
Insight freshness assessment (comparing evidence file paths between baseline
and current revision) but is not connected to the Story Context Analysis
pipeline.

**REPOSITORY FACT**: The `MaintenanceEvaluationServiceImpl` detects
`STALE_PROJECT_UNDERSTANDING` findings but these are not surfaced in the
Story Context Analysis output.

**SUPPORTED CONCLUSION**: Freshness information exists in the system at the
EngineeringContext level but is stripped before reaching the Story Context
Analysis consumer. The consumer therefore cannot determine whether the
analysis was produced from fresh or stale context.

### Concrete Staleness Scenario

```text
1. Analysis A produces EngineeringContext at revision R1
2. Story Context Analysis consumes EngineeringContext, produces analysis
3. Repository evolves to R2 (material changes)
4. ProjectSourceFreshness becomes STALE
5. New Story Context Analysis request arrives
6. EngineeringContext is built from stale knowledge (R1) + live structure (R2)
7. Story Context Analysis produces result from mixed-revision context
8. Consumer receives analysis with no staleness indication
9. Consumer makes implementation decisions based on stale architectural context
```

The `EngineeringContext.metadata.freshness` would correctly report `STALE`
but this information never reaches the analysis result.

## Goal

Make the Story Context Analysis pipeline aware of freshness status so that:

1. The AI engine receives freshness context and can factor it into grounding;
2. The analysis result explicitly declares the freshness of its source
   context;
3. Consumers can determine whether the analysis was produced from fresh,
   stale, or partially-fresh context without inspecting separate endpoints.

## Current Behavior

### EngineeringContext Freshness (already present)

```java
EngineeringContextFreshness(
    status: "STALE",                          // CURRENT | STALE | PARTIALLY_FRESH | NO_BASELINE | UNKNOWN
    repositoryRevision: "abc123...",
    contextRevision: "def456...",
    sources: [SourceFreshness(...)],
    warnings: ["PROJECT_CONTEXT_STALE"]
)
```

This is embedded in `EngineeringContext.metadata().freshness()` but is not
forwarded into the `selectedKnowledge` map.

### Story Context Analysis Pipeline (current)

```text
EngineeringContext (includes freshness)
    ↓
AnalyzeStoryContextUseCase.execute()
    ↓ buildSelectedKnowledge()
selectedKnowledge (NO freshness)
    ↓
StoryContextAnalysisPromptBuilder.build()
    ↓
AI Engine generates StoryContextAnalysisResult (NO freshness)
    ↓
StoryContextAnalysis persisted (NO freshness)
    ↓
Consumer receives result (NO freshness)
```

### StoryContextAnalysisResult (current output)

```java
StoryContextAnalysisResult(
    objectiveUnderstanding: ...,
    architectureFindings: ...,
    decisionFindings: ...,
    evidenceFindings: ...,
    historicalContext: ...,
    constraintFindings: ...,
    impactedComponentFindings: ...,
    uncertainties: ...,
    missingInformation: ...,
    implementationQuestions: ...,
    confidence: ...,
    provenance: ...,
    outputClassification: ...
    // NO freshness field
)
```

## Desired Behavior

### Freshness-Aware Pipeline

```text
EngineeringContext (includes freshness)
    ↓
AnalyzeStoryContextUseCase.execute()
    ↓ buildSelectedKnowledge()
selectedKnowledge (INCLUDES freshness)
    ↓
StoryContextAnalysisPromptBuilder.build()
    ↓ freshness instructions in prompt
AI Engine generates StoryContextAnalysisResult (INCLUDES freshness)
    ↓
StoryContextAnalysis persisted (INCLUDES freshness)
    ↓
Consumer receives result (INCLUDES freshness)
```

### StoryContextAnalysisResult (desired output)

```java
StoryContextAnalysisResult(
    objectiveUnderstanding: ...,
    architectureFindings: ...,
    decisionFindings: ...,
    evidenceFindings: ...,
    historicalContext: ...,
    constraintFindings: ...,
    impactedComponentFindings: ...,
    uncertainties: ...,
    missingInformation: ...,
    implementationQuestions: ...,
    confidence: ...,
    provenance: ...,
    outputClassification: ...,
    contextFreshness: ContextFreshness  // NEW
)

ContextFreshness(
    status: "STALE",                     // CURRENT | STALE | PARTIALLY_FRESH | NO_BASELINE | UNKNOWN
    repositoryRevision: "abc123...",
    contextRevision: "def456...",
    warnings: ["PROJECT_CONTEXT_STALE"]
)
```

## Scope

### In Scope

- Forward `EngineeringContext.metadata().freshness()` into the
  `selectedKnowledge` map in `AnalyzeStoryContextUseCase.buildSelectedKnowledge()`;
- Add freshness-awareness instructions to the Story Context Analysis prompt;
- Extend `StoryContextAnalysisResult` with a `contextFreshness` field;
- Persist freshness metadata in the `StoryContextAnalysis` entity;
- Add targeted tests verifying freshness flows through the pipeline;
- Add a test verifying that STALE freshness status is correctly propagated
  to the analysis result.

### Explicit Non-Goals

- Knowledge selection changes (filtering by freshness status in
  `KnowledgeSelectionServiceImpl`);
- Insight temporal assessment integration;
- Context maintenance finding generation for story context analysis;
- Local worktree inspection or uncommitted code awareness;
- RAG, vector search, OpenClaw, or generic agent frameworks;
- Repository synchronization lifecycle changes;
- ADR-059 temporal knowledge state model implementation;
- Broader context construction freshness changes beyond Story Context
  Analysis;
- frontend changes;
- MCP resource changes;
- new architectural decisions.

## Design

### 1. selectedKnowledge Freshness Injection

`AnalyzeStoryContextUseCase.buildSelectedKnowledge()` will forward
`context.metadata().freshness()` as a `contextFreshness` entry in the
selectedKnowledge map. This makes freshness available to the prompt builder
without changing the EngineeringContext contract.

```java
// In AnalyzeStoryContextUseCase.buildSelectedKnowledge()
var freshness = context.metadata().freshness();
selectedKnowledge.put("contextFreshness", freshness != null ? freshness : Map.of());
```

### 2. Prompt Freshness Instructions

The Story Context Analysis prompt will include a freshness-awareness rule:

```text
CONTEXT FRESHNESS AWARENESS
The supplied project context includes a contextFreshness declaration.
When contextFreshness.status is STALE or PARTIALLY_FRESH, qualify
architectural and historical findings with appropriate uncertainty.
Do not present context-dependent conclusions as current engineering
truth when the context freshness indicates staleness.
When freshness status is NO_BASELINE or UNKNOWN, note the limitation
in uncertainties or missingInformation.
```

This ensures the LLM factors freshness into its grounding without requiring
it to make temporal assessments.

### 3. StoryContextAnalysisResult Extension

The `StoryContextAnalysisResult` contract record will add:

```java
public record StoryContextAnalysisResult(
    // ... existing fields ...
    ContextFreshness contextFreshness
) {}

public record ContextFreshness(
    String status,
    String repositoryRevision,
    String contextRevision,
    List<String> warnings
) {}
```

### 4. StoryContextAnalysis Entity Extension

The `StoryContextAnalysis` entity will add a `contextFreshness` JSONB column
to persist the freshness snapshot at analysis time. This provides:

- traceability (which freshness state produced this analysis);
- auditability (consumers can verify freshness at time of analysis);
- historical reconstruction (freshness at time of analysis vs. current
  freshness).

### 5. AI Engine Validation

`StoryContextAnalysisGenerationService` will validate that the generated
result includes `contextFreshness` and that its `status` field is one of the
valid freshness states.

## Acceptance Criteria

### AC1 — Freshness Is Forwarded to AI Engine

`AnalyzeStoryContextUseCase` forwards `EngineeringContext.metadata().freshness()`
into the `selectedKnowledge` map as `contextFreshness`.

### AC2 — Prompt Includes Freshness Instructions

The Story Context Analysis prompt includes explicit instructions about
context freshness awareness, including qualification of findings when
freshness status is STALE or PARTIALLY_FRESH.

### AC3 — Analysis Result Includes Freshness

`StoryContextAnalysisResult` contains a `contextFreshness` field with
`status`, `repositoryRevision`, `contextRevision`, and `warnings`.

### AC4 — Freshness Is Persisted

`StoryContextAnalysis` entity persists `contextFreshness` as JSONB. The
persisted value matches the freshness state at the time of analysis.

### AC5 — STALE Freshness Produces Qualification

When the input context has `freshness.status = STALE`, the analysis result
reflects this in its `contextFreshness` field and the AI output qualifies
findings with appropriate uncertainty language.

### AC6 — CURRENT Freshness Preserves Unqualified Output

When the input context has `freshness.status = CURRENT`, the analysis
result reflects this and findings are produced without staleness
qualification.

### AC7 — Missing Freshness Is Handled Gracefully

When `EngineeringContext.metadata().freshness()` is null or absent, the
pipeline produces a `contextFreshness` with `status = UNKNOWN` and the
analysis proceeds without error.

### AC8 — Existing Behavior Is Preserved

The existing Story Context Analysis output structure, evidence grounding,
and analysis quality are unchanged for the non-freshness fields. No
regression in existing test suites.

### AC9 — Scope And Quality Gates Hold

Targeted tests for the freshness pipeline, the full `mcp-server` verification
suite, and `git diff --check` pass without unrelated architectural or domain
changes.

## Affected Components

| Component | Change Type |
|-----------|-------------|
| `backend/.../storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java` | Modify: forward freshness into selectedKnowledge |
| `backend/.../storycontextanalysis/entity/StoryContextAnalysis.java` | Modify: add contextFreshness JSONB column |
| `ai-engine/app/prompts/story_context_analysis.py` | Modify: add freshness instructions |
| `ai-engine/app/schemas/story_context_analysis.py` | Modify: add ContextFreshness schema |
| `ai-engine/app/services/story_context_analysis_generation_service.py` | Modify: validate contextFreshness |
| `devlog-contracts/.../engineeringcontext/StoryContextAnalysisResult.java` | Modify: add contextFreshness field |
| `ai-engine/tests/test_story_context_analysis_generation_service.py` | Modify: add freshness tests |
| `backend/src/test/.../storycontextanalysis/` | Add: freshness pipeline tests |

## Test / Validation Expectations

### Targeted Tests

- `StoryContextAnalysisFreshnessPipelineTest`: Verifies freshness flows from
  EngineeringContext through selectedKnowledge to analysis result;
- `StoryContextAnalysisStaleContextTest`: Verifies STALE freshness produces
  qualified output;
- `StoryContextAnalysisMissingFreshnessTest`: Verifies null freshness produces
  UNKNOWN status;

### Full Suite

- `./backend/mvnw -pl backend -am clean verify -B` passes;
- `cd ai-engine && python -m pytest -q` passes;
- `git diff --check` passes.

## Unresolved Questions

### Q1 — Freshness Schema in Prompt

Should the freshness information be injected into the prompt as structured
JSON within the selectedKnowledge map, or as a separate prompt section with
explicit instructions? The investigation recommends structured JSON in
selectedKnowledge with a companion instruction section, but the exact format
should be validated during implementation.

### Q2 — Freshness Qualification Depth

Should the LLM produce explicit staleness qualifications in its findings
(e.g., "This architecture finding is based on potentially stale context"),
or should it only note the freshness status in the output schema and leave
qualification to the consumer? The investigation recommends the former
(explicit qualification) for maximum transparency, but the latter is
simpler and avoids LLM-introduced staleness misinterpretation.

### Q3 — Historical Story Context Analysis Freshness

Should existing persisted `StoryContextAnalysis` records be backfilled with
freshness information? The investigation recommends no backfill — existing
records accurately reflect the context at their creation time, and no
freshness information was available. Future analyses will include freshness.

## Implementation Boundary

This Story is limited to the **Story Context Analysis pipeline**. It does
not change:

- how EngineeringContext is constructed;
- how knowledge is selected;
- how insights or decisions are temporal-assessed;
- how maintenance findings are generated;
- how the MCP server exposes freshness;
- how the frontend displays context.

The Story creates a foundation for later Stories that may:

- add freshness-based knowledge selection filtering;
- integrate TemporalAssessmentService with context construction;
- add freshness-aware context maintenance for story analysis;
- extend freshness awareness to other context consumers.

## Lifecycle State

- Story materialization: completed by this task
- Repository analysis: completed
- Human design review: pending
- Human implementation authorization: pending
- Implementation: not started
- Verification: not started
- Human acceptance: pending
- Commit: authorized only after all implementation and validation gates pass
- Push: not authorized
- Merge: human-only

Terminal design state:

`STORY_0114_DESIGN_AWAITING_HUMAN_REVIEW`
