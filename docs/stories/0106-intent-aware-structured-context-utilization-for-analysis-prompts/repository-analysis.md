# Story 0106 — Repository Analysis

## Status

**STORY_0106_FINALIZATION_READY_FOR_HUMAN_COMMIT_REVIEW**

## Baseline

- Verified baseline SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Baseline branch: `main`
- Story 0105 merged: `YES`
- Governing ADR: `docs/decisions/ADR-064.md`
- Governing investigation: `docs/investigations/post-0105-prompt-utilization-and-analysis-synthesis.md`
- Governing design: `docs/investigations/intent-aware-structured-context-utilization-prompt-architecture-design.md`

## Current Prompt Architecture

Current repository code uses:

- `ai-engine/app/prompts/insight.py`
  shared prompt builder for `describe-project-v1`, `architecture-overview-v1`, and `generate-readme`
- `ai-engine/app/prompts/decision.py`
  dedicated prompt builder for `analyze-engineering-decision-v1`

Current builder behavior:

- authoritative instructions outside the untrusted context block
- full `selectedKnowledge` JSON inside `BEGIN UNTRUSTED SELECTED KNOWLEDGE`
- grounding contract appended
- output contract appended
- broad intent wording only

## Measured Bottleneck Preserved

- `PRIMARY_GAP = PROMPT_UTILIZATION_GAP`
- `SECONDARY_GAP = PROMPT_SYNTHESIS_GAP`
- `SELECTION_PRIMARY_BOTTLENECK = NO`
- `RESULT_PROJECTION = SUFFICIENT_FOR_CURRENT_ANALYSIS_EVALUATION`
- `SEMANTIC_SECTION_COMPOSITION = SUFFICIENT_V1`
- `SEMANTIC_SECTION_MODEL_UTILIZATION = LOW`

## Expected Change Surface

### Production Candidates

- `ai-engine/app/prompts/insight.py`
- `ai-engine/app/prompts/decision.py`
- optionally one minimal shared prompt helper or shared instruction constant if repository architecture supports it cleanly

### Test Candidates

- `ai-engine/tests/test_prompt_builder.py`
- `ai-engine/tests/test_decision_generation_service.py`
- additional prompt-focused Python tests if needed for intent-specific fragment assertions

## Expected Untouched Areas

- Java selection
- `BudgetedDiverseEvidenceSelector`
- `KnowledgeSelectionServiceImpl`
- `SemanticSectionComposer`
- `SelectedKnowledgePromptProjectionService`
- `PromptRequest` transport schema
- persistence
- `AnalysisResultQueryService`
- Angular
- database
- ADR-064

## Available Quality Gate Tooling

Actual repository tooling discovered:

- AI engine tests: `pytest` from `ai-engine/`
- `pytest-asyncio` configured in `ai-engine/pyproject.toml`
- no configured `ruff`
- no configured `mypy`
- no configured `black`

## Story Materialization Conclusion

Story 0106 is a bounded prompt-utilization story on the Python AI-engine prompt layer. Repository reality does not conflict with the approved hybrid design.

## Final Canonical Pipeline Understanding

```text
AnalysisWorkflowServiceImpl
→ canonical Analysis invocation path

Knowledge Collection
→ upstream information construction

Knowledge Selection
→ deterministic selection responsibility
→ Analysis-local Fact UUID ranking defect handled separately by Story 0107

PromptProjection
→ canonical model-facing selectedKnowledge projection

AI Engine
→ Story 0106 structured-context interpretation
```

- `MCP_ANALYSIS_LAUNCHER = NOT_IMPLEMENTED`
- `PARALLEL_ANALYSIS_PIPELINE = NOT_PRESENT`
- `ADR_064_SEQUENCE = KEEP_PAUSED`
