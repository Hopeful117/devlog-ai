# Story 0114 - Repository Analysis

## Status

**RETROSPECTIVE - IMPLEMENTATION MERGED, ACCEPTANCE BLOCKED**

## Baseline

- Baseline: `0a1c7199d92a03b73c8a4f37202ce13cc8f468ae` on `main`
- Implementation tip: `d35383bd7b06059f5900a916cd6144a18166fc71`
- Merge commit: `bb2641de34687b8f773b8846f690cf63a18de00a`
- Pull request: [#99](https://github.com/Hopeful117/devlog-ai/pull/99)
- Working branch: `story/0114-authoritative-freshness-snapshot` (actual); `story.md` incorrectly records `story/0114-knowledge-freshness-and-context-quality-investigation`
- `story.md` status incorrectly ends at `DESIGN — AWAITING HUMAN REVIEW`; git/PR history proves implementation and merge occurred later.

## Existing Boundaries Reused

- `EngineeringContext.metadata().freshness()` — deterministic freshness construction
- `AnalyzeStoryContextUseCase` — Core use case for Story Context Analysis
- `AiTask.contextSnapshot` — JSONB map for carrying execution provenance
- `StoryContextAnalysis` entity and `StoryContextAnalysisQueryService` — persistence and query
- Flyway migration framework for schema evolution
- REST and MCP as adapters over the Core pipeline

## Implemented Topology

```text
EngineeringContext.metadata.freshness
    -> AiTask.contextSnapshot["contextFreshness"]
    -> StoryContextAnalysis.contextFreshness JSONB
    -> StoryContextAnalysisResponse envelope
    -> REST and MCP consumers
```

The committed implementation captured the `EngineeringContextFreshness` snapshot at context construction, carried it through the `AiTask` lifecycle, persisted it in a new nullable `context_freshness` JSONB column on `StoryContextAnalysis`, and exposed it through a new response envelope consumed by REST and MCP.

## Repository Findings

The post-merge review found that the intended topology exists, but the end-to-end path has documented gaps:

1. **High**: `StoryContextAnalysisResponse` uses `Map.copyOf(contextFreshness)` which rejects null values; legitimate `NO_BASELINE` and `UNKNOWN` statuses can have null revision fields, causing retrieval to throw rather than expose the persisted snapshot.
2. **Medium**: Aggregate warnings from `EngineeringContextMetadata.warnings` are not serialized into the snapshot; only `metadata().freshness()` is captured, not the full metadata.
3. **Medium**: AC1/AC2 lack direct tests; the seven added backend tests construct entities with pre-populated maps and test query preservation but do not exercise `AnalyzeStoryContextUseCase` capture into `AiTask.contextSnapshot` or callback persistence.
4. **Medium**: REST compatibility was not resolved; the v1 retrieval endpoint changed from raw `StoryContextAnalysisResult` to a `StoryContextAnalysisResponse` envelope without a controller test or external-consumer compatibility verification.
5. No PR reviews recorded on #99; therefore no repository-grounded prior code-review approval.

## Test Coverage Assessment

Committed tests:
- `StoryContextAnalysisQueryServiceTest`: seven added cases covering null historical data, `STALE`, `PARTIALLY_FRESH`, `CURRENT`, per-source preservation, not-found response, and historical snapshot stability.
- `StoryContextAnalysisToolTest`: updated for the envelope and adds one freshness serialization case.

Missing coverage:
- No `AnalyzeStoryContextUseCase` test exercising freshness capture into `AiTask.contextSnapshot` or callback persistence.
- No migration integration test.
- No controller serialization test.
- No `NO_BASELINE` or `UNKNOWN` test.

## Conclusion

The repository contains the planned vertical-slice components, but the merged implementation does not yet satisfy the complete Story 0114 acceptance contract. Corrective work and focused end-to-end tests are required before human acceptance.