# Story 0115 - Implementation Plan

## Status

**IMPLEMENTED - COMMITTED ON FEATURE BRANCH**

This plan records the implementation sequence from the feature branch commit. It is not a new implementation authorization.

## Planned Vertical Slice

1. Extend `KnowledgeSelectionServiceImpl` to derive Story terms for `engineering-story-context-analysis` when exactly one current Story is present.
2. Rank Facts by Story overlap across type, content, source, and evidence references; then intent/guidance; then stable semantic fields and IDs.
3. Rank Observations by Story overlap across type and content; then intent/guidance; then stable semantic fields and IDs.
4. Rank eligible ACTIVE Insights by Story overlap across type, severity, title, content, source type, and evidence references; then recency; then ID.
5. Emit `knowledge-selection-v5` with `ENGINEERING_STORY_RELEVANCE` rule for SCA; retain v4 for other intents.
6. Update `HistoricalSelectedEvidenceSnapshotProjector` to accept v5 snapshot version.
7. Extend `RepositoryContextAdapter` to resolve the requested Story before retrieval, compose selection text from intent, Story title/path, and requested files, score candidates against Story/file terms and intent terms before recency, apply final 8/6 caps after relevance ranking, and enforce Observation-to-Fact closure from the same baseline.
8. Add behavioral tests for relevance, files, guidance, intent, closure, lifecycle, empty matches, and determinism.
9. Update Story 0115 document with implementation evidence.

## Actual Commit Sequence

| Commit | Outcome |
|---|---|
| `e025122` | `feat: add Story-aware knowledge selection` — single commit containing all production changes, behavioral tests, and Story documentation |

## Verification Planned

- Focused knowledge-selection and repository-context tests.
- Full backend Maven verification with coverage (`./backend/mvnw -pl backend -am clean verify -B`).
- Full AI Engine regression (`cd ai-engine && python3 -m pytest -q`).
- `git diff --check`.
- No generated artifacts committed.

## Explicitly Unchanged

- `KnowledgeSelectionService` public interface and final budget.
- Grounding, provenance, trust, freshness, SCA prompt/output, persistence.
- `AnalyzeStoryContextUseCase.buildSelectedKnowledge()` (SCA integration deferred).
- No RAG, embeddings, vectors, AI ranking, new agents, services, or infrastructure.
- No Decisions, EngineeringEvents, HumanContextInputs, or KnowledgeRelations redesign.
- No freshness changes or historical backfill.