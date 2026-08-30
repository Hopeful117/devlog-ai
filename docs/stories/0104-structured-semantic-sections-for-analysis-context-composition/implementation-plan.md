# Story 0104 — Implementation Plan

## Status

**IMPLEMENTATION_READY_FOR_HUMAN_REVIEW**

## Executed Plan

1. Verified authoritative repository state at baseline `24d5bb2`
2. Created implementation branch `story/0104-structured-semantic-sections`
3. Created `SemanticSection.java` — SectionId enum, section records, classification maps using `EnumSet.of()`
4. Created `SemanticSectionComposer.java` — deterministic composition logic consuming `SelectedKnowledge`
5. Modified `SelectedKnowledgePromptProjectionService.java` — added `semanticSections` to `PromptProjection`, called composer, preserved insight IDs in `projectInsight()`
6. Created `SemanticSectionComposerTest.java` — 21 comprehensive tests
7. Modified `SelectedKnowledgePromptProjectionServiceTest.java` — updated insight ID test, added section propagation tests, updated constructor
8. Modified `RestAIEngineClientTest.java`, `RestAIEngineClientIntegrationTest.java`, `ProjectUnderstandingServiceTest.java` — updated constructor calls
9. Ran targeted tests — 32 PASS
10. Ran Story 0103 regression tests — 4 PASS
11. Ran Story 0098 regression tests — 9 PASS
12. Ran full backend suite — 1040 PASS
13. Ran `mvn clean verify` — BUILD SUCCESS
14. Updated lifecycle artifacts
15. Stopped for HUMAN review with no commit/push/merge

## Production Targets

- `SemanticSectionComposer.java` (create)
- `SemanticSection.java` (create)
- `SelectedKnowledgePromptProjectionService.java` (modify)

## Explicit Non-Targets

- `SelectedKnowledge.java` — no new fields needed
- `KnowledgeSelectionServiceImpl.java` — selection layer, not composition
- `BudgetedDiverseEvidenceSelector.java` — selection layer, not composition
- Python prompt builders — automatic shared propagation
- Python schemas — no changes needed
- Frontend files — no changes needed
- Persistence / migrations — no changes needed

## RED/GREEN Honesty

`RETROSPECTIVE_RED_NOT_SAFELY_REPRODUCED`

The existing `shouldOmitSelectedInsightIdentifiersFromPromptPayload` test was the closest RED indicator — it asserted IDs were stripped. This test was intentionally changed to assert IDs are preserved (GREEN), which is the correct Story 0104 behavior.

## Verification Sequence

- Targeted Story 0104 tests: 32 PASS
- Story 0103 selector regression suite: 4 PASS
- Story 0098 category-cap regression tests: 9 PASS
- Full backend suite: 1040 PASS
- `mvn clean verify`: BUILD SUCCESS
- `git diff --check`: PASS
- Real product AFTER benchmark: NOT_EXECUTED (requires running application with database)

## Final Readiness

- Implementation: complete
- Tests: 1040/1040 PASS
- Build: BUILD SUCCESS
- Benchmark: NOT_EXECUTED (requires running application)
- Lifecycle docs: updated
- HUMAN implementation review: PENDING
- Authorized for commit: NO
