# Story 0116 - Code Review

## Status

**NO_BLOCKING_FINDINGS - Ready for Human Review**

## Review Scope

- Baseline: `982c538` (Story 0115 merge on `main`)
- Reviewed implementation: `b753e8d` (HEAD of `main`, ahead of origin by 1)
- No prior merge; implementation committed directly to `main` after Story 0115 acceptance.

## Confirmed Findings

No blocking or behavioral findings were identified in the implementation.

## Observations

The implementation correctly:
- Replaces empty `buildSelectedKnowledge()` with `KnowledgeSelectionService.select()` using baseline AnalysisContext
- Preserves Story 0115 selection semantics (v5, `ENGINEERING_STORY_RELEVANCE` rule)
- Constructs Java grounding contract from canonical `EngineeringEvidence.reference` (RepositoryEvidence.reference) and `relatedReferences`
- Stores grounding contract in `AiTask.contextSnapshot` for callback validation
- Calls `aiTaskService.submit()` before sending to Python (fixes task lifecycle)
- Adds `reference` field to `EngineeringEvidence` preserving canonical evidence identity
- Adds `groundingContract` to `PromptRequest`; Python consumes it instead of reconstructing
- Implements authoritative Java callback validation per Story 0112 D14 and ADR-067:
  - Evidence reference subset validation
  - Required grounding for factual/interpretative findings
  - relationType presence and validity
  - Classification validity
  - Uncertainty reference validation
  - Context digest consistency
  - Forbidden outputs check
- Updates all affected test files for new constructor signatures

## Tests Executed

### Backend (Java)
- Full test suite: 1,120 tests, 0 failures/errors/skips
- JaCoCo: all configured checks met
- Focused: `AiTaskServiceTest`, `AnalysisWorkflowServiceTest`, `EngineeringContextContractMapperTest`, `AiTaskResultServiceTest` all pass
- Compilation: Clean

### Python AI Engine
- Full suite: 173 tests passed
- No evaluation harness regressions

### Quality Gates
- `git diff --check`: passed (trailing whitespace removed)
- Generated artifacts under `devlog-contracts/target/` remain unstaged

## Verification Checklist

- [x] AC1: Non-empty selected knowledge reaches SCA (KnowledgeSelectionService.select() integrated)
- [x] AC2: Selection is Story-aware (v5 with ENGINEERING_STORY_RELEVANCE preserved)
- [x] AC3: Java constructs grounding contract (from EngineeringEvidence.reference)
- [x] AC4: Python consumes Java grounding contract (PromptRequest.groundingContract)
- [x] AC5: Python defensive validation uses Java contract (_validate_output against allow-list)
- [x] AC6: Authoritative Java callback validation (handleCallback() validates all requirements)
- [x] AC7: Canonical reference preserved (EngineeringEvidence.reference added)
- [x] AC8: Freshness and trust unchanged (Story 0114/0115 semantics preserved)
- [x] AC9: Output schema stable (StoryContextAnalysisResult unchanged)
- [x] AC10: Quality gates pass (1120 backend, 173 AI Engine, git diff --check)

## Remaining Work

1. **Human acceptance** — Required before any claim of Story acceptance.
2. ADR-066 fixture update for `engineering-story-context-analysis-v1` provenance intersection cases (design §9) — deferred to follow-up Story.

## Conclusion

The implementation satisfies all in-scope acceptance criteria. All automated quality gates pass. The complete implementation is contained in commit `b753e8d` on `main`.

**Recommendation**: Ready for human acceptance review.