# Story 0107 — Implementation Plan

## Status

**IMPLEMENTATION_READY_FOR_HUMAN_REVIEW**

## Planned Scope

Correct only the unstable final Fact ranking tie-breaker while preserving all existing relevance and selection behavior.

## Planned Production Change

### Modify

- `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`

Extract or define a canonical Fact semantic comparator over existing `FactSnapshot` fields and replace the final `id().toString()` Fact tie-breaker. Preserve score descending and FactType ordering exactly.

## Planned Test Change

### Modify

- `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceTest.java`

Add a bounded cross-Analysis regression with five UUID permutations. Assert identical ordered semantic selections and prove the old UUID-based ordering can select different semantics.

## Planned Verification

1. Focused RED evidence against the prior comparator
2. Focused GREEN Knowledge Selection tests
3. Related selection, closure, Semantic Section, AnalysisContext, and PromptProjection tests
4. Full backend `mvn test`
5. Backend `mvn clean verify`
6. Static search confirming Fact semantic ranking no longer uses UUID
7. Scope audit with `git status`, `git diff --stat`, and `git diff`

## Explicitly Unchanged

- Fact scoring and guidance scoring
- Fact and Observation budgets
- grounding closure
- commit-diff cap
- collectors and collector limits
- documentation overflow policy
- prompts and AI Engine
- grounding contract
- persistence UUID generation
- database schema
- frontend and MCP
- ADR-064 sequence

## Actual Outcome

- Production scope remained one Java service file.
- Test scope remained one existing Knowledge Selection test file.
- Five UUID permutations select the same ordered semantic Facts.
- All focused, related, full-suite, and verification gates pass.
- No plan broadening was required.
