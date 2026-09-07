# Story 0112 - Repository Analysis

## Status

**RETROSPECTIVE - IMPLEMENTATION MERGED, ACCEPTANCE BLOCKED**

## Baseline

- Design baseline: `0ca95589ff462ceb8569fda95063f4fc72316a32`
- Implementation tip: `60cc55964208d85f76f84990ae2a3982f40b395b`
- Merge commit: `ca81c6650760fa4d8fe9824fe2c23fda760dd590`
- Pull request: [#95](https://github.com/Hopeful117/devlog-ai/pull/95)

This analysis was reconstructed after merge from the committed Story 0112 diff. It does not include later uncommitted workspace changes.

## Existing Boundaries Reused

The implementation extends these existing surfaces:

- `EngineeringContextFacade` for Core-owned context construction;
- `AiTask` and the AI Engine callback pipeline for execution;
- `IntentCatalog` for the versioned `engineering-story-context-analysis-v1` intent;
- ADR-066 evaluation loading and deterministic replay;
- REST and MCP adapters over the Core use case;
- JPA and Flyway for durable analysis snapshots.

## Implemented Topology

```text
REST / MCP
    -> AnalyzeStoryContextUseCase
    -> EngineeringContextFacade
    -> AiTask + PromptRequest
    -> Python StoryContextAnalysisGenerationService
    -> Core callback
    -> StoryContextAnalysis persistence
```

The committed implementation added the Java and Python result contracts, task and intent routing, the Core use case, callback handling, persistence, REST and MCP adapters, and an ADR-066 replay scenario.

## Repository Findings

The post-merge repository review found that the intended topology exists, but the end-to-end path is not operational at the merged revision:

- nullable Story commit boundaries are inserted into `Map.of`, which rejects null values;
- the submitted task is not transitioned from `CREATED` to `SUBMITTED`;
- the callback expects a `storyId` that task creation does not retain in its context snapshot;
- Java and Python disagree on the wire shape of `confidence` and `outputClassification`;
- the Core callback path does not perform Story Context grounding, trust, relationship, classification, or digest validation;
- the Java grounding contract is not transported and Python reconstructs an incomplete allow-list;
- REST returns an empty asynchronous `202` while the MCP client expects a synchronous result;
- optional guidance is persisted but not forwarded to generation;
- the durable snapshot remains mutable through entity setters and updatable columns.

## Test Coverage Assessment

Committed tests cover intent registration, generic proposal validation changes, callback delegation, duplicate callback handling, and task-type API registration. The ADR-066 replay covers an allow-list subset check.

Missing end-to-end coverage includes:

- `AnalyzeStoryContextUseCase` with ordinary nullable Story commits;
- task submission lifecycle through callback completion;
- Python-to-Java result deserialization parity;
- authoritative Core grounding and relationship validation;
- REST response and MCP transport equivalence;
- persistence rollback, uniqueness, and immutability;
- propagation of human guidance;
- a real Story qualitative evaluation rather than a synthetic fixture.

## Conclusion

The repository contains the planned vertical-slice components, but the merged implementation does not yet satisfy the Story 0112 success boundary or acceptance gates. Corrective implementation and focused end-to-end tests are required before human acceptance.
