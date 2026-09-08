# Story 0116 - Engineering Report

## Status

**IMPLEMENTED - COMMITTED ON MAIN BRANCH, AWAITING HUMAN REVIEW**

## Delivered Architecture

```text
Engineering Story
        ↓
EngineeringContextFacade.getEngineeringContext()
        ↓
ProjectProfileService.getLatestByProject() → baseline Analysis
        ↓
AnalysisContextService.build(baselineAnalysisId) → AnalysisContext with SCA intent
        ↓
KnowledgeSelectionService.select() → SelectedKnowledge (Story-aware v5)
        ↓
SelectedKnowledgePromptProjectionService.toMap() → PromptRequest.selectedKnowledge
        ↓
EngineeringContext evidence → Java grounding contract (canonical references)
        ↓
AiTaskService.createForStoryContextAnalysisEntity() + submit() → CREATED → SUBMITTED
        ↓
AIEngineClient.submit(PromptRequest with selectedKnowledge + groundingContract)
        ↓
Python StoryContextAnalysisGenerationService (defensive validation against Java contract)
        ↓
AnalyzeStoryContextUseCase.handleCallback() (authoritative Java validation)
        ↓
StoryContextAnalysis persistence (analysisSnapshot + contextFreshness)
```

The implementation closes the disconnect where `AnalyzeStoryContextUseCase.buildSelectedKnowledge()` previously hard-coded empty selected knowledge lists. Story Context Analysis now receives actual validated project knowledge produced by the existing deterministic selection pipeline.

## Contract Model

- `SelectedKnowledge` projection unchanged; carries `selectionVersion="knowledge-selection-v5"` and `ENGINEERING_STORY_RELEVANCE` rule for SCA intent
- `PromptRequest` extended with `groundingContract: Map<String, Object>` carrying `allowedEvidenceReferences` (canonical `EngineeringEvidence.reference` + `relatedReferences`)
- `EngineeringEvidence` extended with `reference` field (canonical evidence identity) alongside existing `identifier` (knowledge provenance)
- `StoryContextAnalysisResult` output schema unchanged
- `EvidenceRef` shape unchanged: `{reference, resource?}`

## Authority Assessment

Target authority model (per Story 0112 D14, ADR-063, ADR-067):

```text
JAVA_CORE = context construction + trust + grounding contract + validation + durability + DETERMINISTIC RANKING
PYTHON = generation + defensive validation against Java contract + one corrective retry
REST_AND_MCP = thin adapters over Core pipeline
```

**Observed behavior:**
- Java constructs `allowedEvidenceReferences` exclusively from canonical references of citable evidence in authorized `EngineeringContext`
- Python receives explicit immutable contract via `PromptRequest.groundingContract`
- Python uses contract for prompt instructions, defensive subset validation, corrective retry
- Java authoritatively revalidates on callback before persistence
- No duplicated authority; Python defensive, Java authoritative

## Execution And Durability Assessment

The implementation is synchronous and operates within existing transaction boundaries:

1. `execute()` builds context, selects knowledge, creates task, **submits task** (CREATED → SUBMITTED), sends to Python
2. Python generates, defensively validates, retries once on failure
3. Callback arrives; Java validates authoritatively; on success: persists `StoryContextAnalysis` + completes `AiTask` in same transaction
4. On validation failure: task marked FAILED; no result delivered (fail-closed per Story 0112 D16)

Freshness capture (Story 0114) remains unchanged: snapshot taken at context construction, carried through task, persisted in `StoryContextAnalysis.contextFreshness`.

## Quality Evidence

- Backend: 1,120 tests passed; JaCoCo checks met
- AI Engine: 173 tests passed
- `git diff --check`: passed
- No generated artifacts staged

## Required Corrective Work

None identified for in-scope behavior. The implementation is complete and verified.

## Final Assessment

```text
STRUCTURAL_VERTICAL_SLICE = PRESENT
END_TO_END_FLOW = OPERATIONAL
ARCHITECTURAL_AUTHORITY = PRESERVED (Java/Core sole grounding authority)
QUALITY_GATES = PASSED
STORY_ACCEPTANCE_GATE = AWAITING_HUMAN_REVIEW
NEXT_STATE = HUMAN_ACCEPTANCE_DECISION
```