# Story 0115 - Repository Analysis

## Status

**IMPLEMENTED - COMMITTED ON FEATURE BRANCH, NOT MERGED**

## Baseline

- Baseline SHA: `7e1d468` (revised validated knowledge SCA design commit)
- Baseline branch: `design/validated-knowledge-sca-integration`
- Implementation branch: `story/0115-story-aware-knowledge-selection` (current HEAD)
- Governing design: `docs/investigations/validated-knowledge-sca-integration-design.md`
- Governing ADRs: ADR-006, ADR-063, ADR-067
- Current HEAD: `e025122 feat: add Story-aware knowledge selection`

## Existing Boundaries Reused

- `KnowledgeSelectionService` and its public interface
- Final `KnowledgeBudget` (40 Facts, 25 Observations, 10 Insights, 5 architecture, 60 repo evidence)
- `IntentTerms` for deterministic term extraction and matching
- `RepositoryContextAdapter` with existing latest-profile Analysis baseline and 200-row overfetch windows
- `EngineeringStory` persisted fields (title, story path, status, base/target commits, timestamps)
- `HistoricalSelectedEvidenceSnapshotProjector` version acceptance
- Observation-to-Fact closure rules

## Implemented Topology

```text
coherent baseline candidates
        -> Story relevance (title, path, requested files)
        -> existing intent and guidance relevance
        -> deterministic ordering
        -> existing KnowledgeBudget
```

The committed implementation derives Story terms for `engineering-story-context-analysis` when exactly one current Story is present, ranks Facts/Observations by Story overlap before intent/guidance, ranks eligible ACTIVE Insights by Story overlap before recency, emits `knowledge-selection-v5` with `ENGINEERING_STORY_RELEVANCE` rule, and `RepositoryContextAdapter` resolves the requested Story, separates Story/file relevance from intent relevance, orders both before recency, uses existing caps (8 Facts, 6 Observations), and enforces closure-safe support.

## Repository Findings

The implementation is confined to deterministic ranking within existing selection boundaries. No project-wide Fact/Observation query was introduced. Grounding, provenance, trust, freshness, SCA prompt/output, and persistence are unchanged. No AI call participates in ranking. The SCA integration (wiring selected knowledge into `AnalyzeStoryContextUseCase`) is explicitly deferred.

## Test Coverage Assessment

New behavioral tests (10):
- `StoryAwareKnowledgeSelectionTest`: Fact/Observation/Insight Story relevance, older relevant candidates, intent/guidance preservation, closure, empty outcomes, ambiguity fallback, deterministic repetition.
- `RepositoryContextAdapterStoryAwareCandidateTest`: Story/file relevance before cap, older relevant beats newer unrelated, requested files, empty/no-match, closure.

Updated tests:
- `HistoricalSelectedEvidenceSnapshotProjectorTest`: v5 acceptance.

Existing regression suites:
- `KnowledgeSelectionServiceTest`, `KnowledgeSelectionServiceAdditionalTest`, `KnowledgeSelectionServiceImplStatusExclusionTest`: budget, ordering, closure, lifecycle, guidance, status filtering preserved.

Verification evidence:
- Focused suite: 73 tests passed.
- Full backend: 1,120 tests passed; JaCoCo checks met.
- Full AI Engine: 173 tests passed.
- `git diff --check`: passed.

## Conclusion

The repository contains the Story 0115 implementation as a single commit on the feature branch. All automated quality gates pass. The Story awaits human review and acceptance before any push or merge. No generated artifacts are staged.