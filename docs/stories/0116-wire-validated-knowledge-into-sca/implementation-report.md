# Story 0116 - Implementation Report

## Status

**IMPLEMENTED - COMMITTED ON MAIN BRANCH, AWAITING HUMAN REVIEW**

## Repository State

- Branch: `main`
- HEAD: `b753e8d feat: wire validated knowledge into Story Context Analysis (Story 0116)`
- Baseline SHA: `982c538` (Story 0115 merge)
- Commit created by this task: YES
- Push performed by this task: NO

## Governance

- Governing ADRs: ADR-006, ADR-063, ADR-066, ADR-067
- Authorized Story: Story 0116, Wire Validated Knowledge into Story Context Analysis
- Trust target: deterministic selection + authoritative grounding; no trusted knowledge changes
- Java/Core authority target: context construction, trust, grounding contract, validation, persistence

## Implementation Summary

Commit `b753e8d` implemented the wiring of validated knowledge into Story Context Analysis. The disconnect where `AnalyzeStoryContextUseCase.buildSelectedKnowledge()` hard-coded empty selected knowledge is closed. Story Context Analysis now receives actual Story-aware selected knowledge from `KnowledgeSelectionService`, with Java/Core authority over grounding preserved through the `PROVENANCE_INTERSECTION` model.

## Production Components

### Backend (Java)
- `backend/.../storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java`: 
  - `execute()`: builds AnalysisContext from baseline Analysis, calls `KnowledgeSelectionService.select()`, constructs grounding contract, creates/submits task, sends PromptRequest with groundingContract
  - `handleCallback()`: authoritative validation (grounding subset, required evidence, relationType, classification, digest, forbidden outputs)
  - New helpers: `adaptContextForSCA()`, `createMinimalSCAContext()`, `mapGuidance()`, `validateStoryContextAnalysisResult()`, `validateGroundedFindings()`
- `backend/.../ai/task/service/AiTaskServiceImpl.java`: 
  - `createForStoryContextAnalysisEntity()`: stores groundingContract in contextSnapshot, extracts storyId, uses v5 for SCA intent
- `backend/.../analysis/workflow/AnalysisWorkflowServiceImpl.java`: updated PromptRequest constructor for standard analysis
- `backend/.../engineeringcontext/mapper/EngineeringContextContractMapper.java`: populates new `reference` field
- `backend/.../ai/engine/dto/PromptRequest.java`: added `groundingContract` field

### Contracts (devlog-contracts)
- `devlog-contracts/.../EngineeringEvidence.java`: added `reference` field (canonical evidence identity)

### Python AI Engine
- `ai-engine/app/schemas/ai_task.py`: `PromptRequest` adds `grounding_contract` field
- `ai-engine/app/prompts/story_context_analysis.py`: `_grounding_contract()` uses Java-provided contract
- `ai-engine/app/services/story_context_analysis_generation_service.py`: `_validate_output()` validates against Java contract

### Tests Updated
- `backend/.../ai/engine/client/RestAIEngineClientIntegrationTest.java`: PromptRequest constructor
- `backend/.../ai/engine/client/RestAIEngineClientTest.java`: PromptRequest constructor
- `backend/.../engineeringcontext/controller/EngineeringContextControllerWebMvcTest.java`: EngineeringEvidence constructor

## Documentation

- `docs/stories/0116-wire-validated-knowledge-into-sca/story.md`: canonical Story with implementation evidence

## Recorded Verification

```text
FULL_BACKEND_TESTS = 1120 passed
JACOCO_CHECKS = MET
AI_ENGINE_TESTS = 173 passed
GIT_DIFF_CHECK = PASS
STAGED_FILES = 13 source/test/doc files (generated artifacts excluded)
```

## Architectural Decisions Preserved

- `KnowledgeSelectionService` public interface and final budget (40/25/10/5/60) unchanged
- `StoryContextAnalysisResult` output schema unchanged
- `EvidenceRef` shape unchanged (`{reference, resource?}`)
- Insight trust semantics: context only, no trust inheritance
- Story 0114 freshness capture/persistence unchanged
- Historical analyses unchanged (no backfill)
- Java/Core authority over grounding preserved (no Python-side authority reconstruction)
- No RAG, vectors, new agent, microservice, or event-driven redesign
- Decisions/EngineeringEvents as selected knowledge deferred per design

## Known Limitations

- ADR-066 fixture for `engineering-story-context-analysis-v1` not yet updated for provenance intersection cases (design §9) — deferred to follow-up Story
- No dedicated `AnalyzeStoryContextUseCaseTest` exercising full execute→callback path with real selected knowledge (would require integration test setup)

## Git Diff Summary

Commit `b753e8d`: 13 files changed, 601 insertions(+), 59 deletions(-).
Created: 1 Story document.
Modified: 9 production/test files across backend, contracts, AI engine.

## Readiness

```text
IMPLEMENTATION_PRESENT = YES
COMMITTED_ON_MAIN = YES
MERGED = NO (already on main)
PUSH_PERFORMED = NO
HUMAN_ACCEPTANCE = PENDING
READY_FOR_HUMAN_REVIEW = YES
```