# Story 0115 - Engineering Report

## Status

**IMPLEMENTED - COMMITTED ON FEATURE BRANCH, AWAITING HUMAN REVIEW**

## Delivered Architecture

```text
coherent baseline candidates
        -> Story relevance (title, path, requested files)
        -> existing intent and guidance relevance
        -> deterministic ordering
        -> existing KnowledgeBudget
```

The implementation adds deterministic Story-aware ranking to the existing knowledge-selection pipeline. For the `engineering-story-context-analysis` intent, Story terms derived from the single current Story's title and path, plus explicit requested files, are scored against Facts, Observations, and ACTIVE Insights. Story relevance precedes existing intent-specific and user-guidance scoring. Recency remains a tie-breaker. The final `KnowledgeBudget` and Observation-to-Fact closure rules are preserved. The `knowledge-selection-v5` version and `ENGINEERING_STORY_RELEVANCE` rule are emitted only for SCA; other intents retain v4 behavior.

## Contract Model

- `KnowledgeSelectionService` public interface unchanged.
- `SelectedKnowledge.SelectionMetadata` carries `selectionVersion` (`v5` for SCA, `v4` otherwise) and `appliedRules` including `ENGINEERING_STORY_RELEVANCE` for SCA.
- `HistoricalSelectedEvidenceSnapshotProjector` accepts both v4 and v5 snapshot versions additively.
- `RepositoryContextAdapter` uses the same latest-profile Analysis baseline and bounded overfetch windows; relevance ranking occurs before final candidate caps.

## Authority Assessment

Target authority model:

```text
JAVA_CORE = context construction + trust + grounding + validation + durability + DETERMINISTIC RANKING
PYTHON = generation + defensive validation only (NO ranking)
REST_AND_MCP = thin adapters over Core pipeline
```

Ranking remains fully deterministic and Java/Core-owned. No AI call participates in retrieval, scoring, or ordering. Grounding, trust, freshness, and persistence are unchanged.

## Execution Assessment

The implementation is synchronous, deterministic, and operates within existing selection boundaries. No new services, database schema changes (beyond additive projector version), or transaction boundary modifications were introduced.

## Quality Evidence

- Focused backend tests: 73 passed.
- Full backend verification: 1,120 passed; JaCoCo checks met.
- Full AI Engine regression: 173 passed.
- `git diff --check`: passed.
- Staged files: exactly seven intended files (3 production, 3 test, 1 Story doc).
- Generated artifacts under `devlog-contracts/target/` remain unstaged.

## Required Corrective Work

None identified for in-scope behavior. The implementation is complete and verified.

## Final Assessment

```text
STRUCTURAL_VERTICAL_SLICE = PRESENT
END_TO_END_FLOW = OPERATIONAL (within selection boundaries)
ARCHITECTURAL_AUTHORITY = PRESERVED
QUALITY_GATES = PASSED
STORY_ACCEPTANCE_GATE = AWAITING_HUMAN_REVIEW
NEXT_STATE = HUMAN_ACCEPTANCE_DECISION
```