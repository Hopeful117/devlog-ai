# Story 0121 - Repository Analysis

## Status

**IMPLEMENTED - ON STORY BRANCH**

## Baseline

- Baseline SHA: `cf9c3df` (Story 0120 merge on `main`)
- Branch: `story/0121-devlog-analysis-communication-prototype`
- Governing ADRs: ADR-006, ADR-067
- Prior Story: Story 0120 — Agent Communication Boundary (merged)

## Existing Components Consumed

- `AgentCommunication(agent, intent, title, body)` — transport-independent value record (Story 0120)
- `AgentCommunicationPort.communicate(AgentCommunication)` — application port (Story 0120)
- `AnalysisService.getById(UUID)` — deterministic analysis lookup
- `AnalysisResultQueryService.getResult(UUID)` — result assembly (synthesis + insights)
- `AnalysisResponse` — analysis metadata (status, type, project)
- `AnalysisResultResponse.SynthesisSection` — AI-generated synthesis items
- `AnalysisResultResponse.InsightsSection` — trusted knowledge insights
- `InsightSeverity` — INFO, WARNING, CRITICAL ordinal ordering

## Pre-Implementation Findings

1. **No agent runtime exists.** Analysis completion does not automatically trigger communication. Story 0121 is a **manually invoked prototype** — a controller endpoint, not a production trigger.
2. **`AgentCommunicationPort` has zero consumers.** Story 0120 defined the boundary but no production code invokes it. Story 0121 is the first real consumer.
3. **Synthesis is non-trusted AI output.** `AiTask.synthesisSnapshot` is assembled by `AnalysisResultQueryServiceImpl.buildSynthesis()` from AI-generated JSON. It is **not** trusted knowledge (ADR-006).
4. **Insights are trusted knowledge.** `Insight` entities are promoted from accepted `ValidatableProposal`. Already trusted (ADR-006).
5. **No semantic composition pattern exists.** The codebase has no prior use of `AgentCommunication` for composing messages from analysis results.
6. **Silence is valid.** The Story explicitly requires that nothing be communicated when no meaningful content exists.

## Implemented Topology

```text
Story 0121 (first consumer of AgentCommunicationPort)
  |
  v
POST /api/v1/analyses/{id}/communicate
  |
  v
AnalysisCommunicationUseCase.execute(analysisId)
  |
  +-- AnalysisService.getById(analysisId) -> status check
  |     not completed -> SILENCE
  |
  +-- AnalysisResultQueryService.getResult(analysisId) -> result
  |
  +-- compose(result) -> semantic selection:
  |     1. synthesis present -> first meaningful item -> AgentCommunication
  |     2. no synthesis -> highest-severity insight -> AgentCommunication
  |     3. neither -> null -> SILENCE
  |
  +-- OLED bounding: title <= 50, body <= 200
  |
  v
AgentCommunicationPort.communicate(communication)
  |
  v
AgentPresenceCommunicationAdapter (Story 0120)
  -> deterministic projection -> AgentPresenceMessage
  -> HttpAgentPresenceAdapter -> ESP32/OLED
```

## Repository Findings

1. **Use case owns composition, not adapter.** `AgentPresenceCommunicationAdapter` remains a generic intent→level mapper. The semantic decision of *what* to communicate lives in `AnalysisCommunicationUseCase`.

2. **No severity→intent mapping introduced.** Intent remains `INFORM` regardless of synthesis or insight severity. The OLED surface level is determined downstream by the adapter projection, not by the use case.

3. **Analysis state is immutable.** `AnalysisCommunicationUseCase` never calls `AnalysisService.start()`, `AnalysisService.fail()`, or any write operation. It is a pure read+communicate path.

4. **No `agentpresence` dependency.** The use case package contains zero imports from `agentpresence`. Verified by package inspection.

5. **Controller follows existing conventions.** `AnalysisController` already had 5 endpoints. The new `POST /{id}/communicate` follows the same pattern: inject service, delegate to service, return `ResponseEntity.ok()`.

6. **Existing tests updated minimally.** `AnalysisControllerWebMvcTest` needed 6 constructor call updates (new dependency injection). No behavioral changes to existing tests.

## Test Coverage Assessment

**New tests (11 tests across 1 file):**
- `AnalysisCommunicationUseCaseTest`: 11 tests covering synthesis communication, insight fallback (highest severity), silence (no content, analysis not completed), OLED truncation, agent identity, no analysis state mutation, no `agentpresence` dependency, blank-content insight skipping.

**Existing tests (updated, all passing):**
- `AnalysisControllerWebMvcTest`: 5 tests updated for new constructor dependency. No behavioral changes.
- Total: 1278 backend tests pass

## Conclusion

All Story 0121 ACs are satisfied. The use case is the first real consumer of `AgentCommunicationPort`, performs deterministic semantic selection (synthesis → insight → silence), applies OLED bounding, preserves analysis state immutability, maintains ADR-006 trust boundaries, and follows existing controller conventions. Full backend verification (1278/1278) passes.
