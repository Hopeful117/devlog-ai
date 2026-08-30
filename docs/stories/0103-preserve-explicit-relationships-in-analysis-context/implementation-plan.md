# Story 0103 — Implementation Plan

## Status

**HUMAN_IMPLEMENTATION_REVIEW_APPROVED_AUTHORIZED_FOR_COMMIT**

## Corrected Executed Plan

1. Verify authoritative repository state and inspect the full incorrect diff
2. Restore selector boundary to Story 0098 baseline semantics
3. Preserve canonical `knowledgeRelations` in `SelectedKnowledge`
4. Enforce Policy A only in `SelectedKnowledgePromptProjectionService`
5. Add explicit relationship-level bound and deterministic ordering in projection
6. Move Story 0103 semantic tests into projection/selection-preservation suites
7. Run targeted tests
8. Run Story 0098 regression verification
9. Run full backend suite
10. Run `mvn clean verify`
11. Run the real product AFTER benchmark
12. Update all lifecycle artifacts
13. Stop for HUMAN review with no commit/push/merge

## Actual Production Targets

- `SelectedKnowledge.java`
- `KnowledgeSelectionServiceImpl.java`
- `SelectedKnowledgePromptProjectionService.java`

## Explicit Non-Targets

- `BudgetedDiverseEvidenceSelector.java`
- Python prompt builders
- Python schemas
- frontend files

## RED/GREEN Honesty

`RETROSPECTIVE_RED_NOT_SAFELY_REPRODUCED`

Reason: the correction was applied to an already dirty uncommitted Story 0103 working tree. Safely replaying a historical RED state would have required destructive temporary reversal of live in-progress production files and benchmark-ready runtime artifacts. Instead, GREEN was executed on the corrected implementation and the incorrect boundary was verified directly from the inspected diff.

## Executed Verification Sequence

- Targeted Story 0103 tests
- Story 0098 selector regression suite
- Full backend suite
- `mvn clean verify`
- Real product AFTER benchmark
- `git diff --check`

## Final Readiness

- Corrected implementation: complete
- Verification: complete
- AFTER benchmark: complete
- Lifecycle docs: corrected
- HUMAN implementation review approved
- Authorized for commit only
