# Story 0121 - Engineering Report

## Status

**IMPLEMENTED - ON STORY BRANCH, AWAITING HUMAN REVIEW**

## Delivered Architecture

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
  |     1. synthesis present -> first meaningful item -> AgentCommunication(INFORM)
  |     2. no synthesis -> highest-severity insight -> AgentCommunication(INFORM)
  |     3. neither -> null -> SILENCE
  |
  +-- OLED bounding: title <= 50, body <= 200
  |
  v
AgentCommunicationPort.communicate(communication)
  |
  v
AgentPresenceCommunicationAdapter (Story 0120, unchanged)
  -> deterministic projection -> AgentPresenceMessage
  -> HttpAgentPresenceAdapter -> ESP32/OLED
```

The delivered architecture adds a semantic composition layer on top of Story 0120's transport-independent boundary. The use case is the first real consumer of `AgentCommunicationPort`, making the analysis domain the first explicitly invoked semantic communication source.

## Contract Model

- `AnalysisCommunicationResponse(status, analysisId, agent, intent, title, body)`: response DTO
  - `COMMUNICATED`: communication sent, fields populated
  - `SILENCE`: no communication, only `analysisId` populated
- `AnalysisCommunicationUseCase.execute(UUID analysisId)`: synchronous, returns response
- `AgentCommunication(agent, intent, title, body)`: unchanged (Story 0120)
- `AgentCommunicationPort.communicate(AgentCommunication)`: unchanged (Story 0120)

## Semantic Selection Contract

| Priority | Source | Condition | Agent | Intent |
|----------|--------|-----------|-------|--------|
| 1 | Synthesis | Non-null, has items with non-blank name+content | DEVLOG | INFORM |
| 2 | Insight | Non-null, has items with non-blank title+content | DEVLOG | INFORM |
| 3 | Silence | Neither synthesis nor insights available | null | null |

**Selection detail:**
- Synthesis: first item with non-blank name AND non-blank content
- Insight: max by `InsightSeverity.ordinal()` (CRITICAL > WARNING > INFO), then by id

**OLED bounding:** applied at composition layer, not domain model
- Title: truncated to 50 characters
- Body: truncated to 200 characters

## Authority Assessment

Target authority model (per ADR-063, ADR-067):

```text
JAVA_CORE = semantic selection + OLED bounding + analysis state immutability
PYTHON = unchanged (probabilistic interpretation boundary)
ANALYSIS_DOMAIN = read-only access (no state mutation)
```

**Observed behavior:**
- Java defines semantic selection deterministically
- Synthesis (AI-generated) stays non-trusted; insights (promoted) stay trusted
- No analysis state is mutated by the use case
- No Python-side changes
- No changes to AI task lifecycle or grounding contracts

## Execution And Durability Assessment

The implementation operates within existing boundaries:

1. `AnalysisCommunicationUseCase` is a Spring `@Component` wired via constructor injection
2. No new persistence entities or transaction boundaries
3. No new runtime state beyond the existing Spring bean graph
4. No changes to existing test contracts (constructor update only)
5. Read-only path: no analysis state mutation

## Quality Evidence

- Targeted tests: 11/11 PASS (AnalysisCommunicationUseCaseTest)
- Updated tests: 5/5 PASS (AnalysisControllerWebMvcTest, constructor update)
- Full backend: 1278/1278 PASS
- Build: SUCCESS
- No new warnings introduced

## Final Assessment

```text
STRUCTURAL_VERTICAL_SLICE = PRESENT (use case + DTO + endpoint + tests)
END_TO_END_FLOW = PRESENT (AnalysisCommunicationUseCase -> AgentCommunicationPort -> Presence -> HTTP -> ESP32)
ARCHITECTURAL_AUTHORITY = PRESERVED (Java/Core deterministic authority)
TRUST_BOUNDARIES = PRESERVED (synthesis non-trusted, insights trusted, proposals never communicated)
ANALYSIS_STATE_IMMUTABLE = YES (read-only path)
QUALITY_GATES = PASSED (1278/1278)
STORY_ACCEPTANCE_GATE = AWAITING_HUMAN_REVIEW
NEXT_STATE = HUMAN_ACCEPTANCE_DECISION
```
