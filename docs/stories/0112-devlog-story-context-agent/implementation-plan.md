# Story 0112 - Implementation Plan

## Status

**RETROSPECTIVE - IMPLEMENTED AND MERGED**

This plan records the implementation sequence reconstructed from PR #95. It is not a new implementation authorization.

## Planned Vertical Slice

1. Add shared `EvidenceRef` and `StoryContextAnalysisResult` contracts.
2. Register the Story Context task type, proposal-free intent, and analysis type.
3. Add Core orchestration over fresh `EngineeringContext` construction and `AiTask` execution.
4. Add Python prompt construction, structured generation, defensive validation, and corrective retry.
5. Add authoritative callback handling and durable `StoryContextAnalysis` persistence.
6. Expose the capability through REST and MCP adapters.
7. Extend ADR-066 with a replayable Story Context scenario.
8. Verify contracts, task lifecycle, persistence, transports, trust boundaries, and qualitative usefulness.

## Actual Commit Sequence

| Commit | Outcome |
|---|---|
| `c8335bc` | Initial Java, Python, REST, MCP, persistence, intent, and contract implementation |
| `7b841be` | Callback delegation, canonical contract package, analysis type, evaluation scenario, and regression tests |
| `6bbc73a` | Prompt and ADR-066 loader/replay repairs |
| `86ff5b9` | MCP compilation and relationship-type contract changes |
| `60cc559` | Flyway migration, task/analysis creation correction, API routing, and final schema digest updates |

## Verification Planned

- focused Java callback, intent, persistence, REST, and MCP tests;
- focused Python prompt, generation, validation, and callback-contract tests;
- cross-language serialization fixture;
- both ADR-066 scenarios;
- full backend and AI Engine suites;
- MCP compilation and frontend regression checks;
- at least one real Engineering Story qualitative evaluation;
- final diff review against ADR-006, ADR-063, and ADR-067.

## Retrospective Variance

The implementation was merged after green CI, but the focused end-to-end and cross-language checks above were not present. The retrospective code review identified blocking lifecycle, transport, contract, validation, and persistence issues. The corrective plan must address the findings in `code-review.md` before Story acceptance.

## Explicitly Unchanged

- no RAG, embeddings, vector database, or generic agent framework;
- no autonomous triggering or repository modification;
- no `ValidatableProposal` production path for this intent;
- no Python-owned repository or database context reconstruction;
- no trusted-knowledge promotion from `StoryContextAnalysis`.
