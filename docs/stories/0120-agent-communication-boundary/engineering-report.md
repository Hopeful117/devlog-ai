# Story 0120 - Engineering Report

## Status

**IMPLEMENTED - ON STORY BRANCH, AWAITING HUMAN REVIEW**

## Delivered Architecture

```text
Story 0120
  |
  v
AgentCommunication(agent, intent, title, body)
  transport-independent value record
  no imports from agentpresence, http, esp32, ai.task
  |
  v
AgentCommunicationPort.communicate(communication)
  application port — future agent depends on this
  |
  v
AgentPresenceCommunicationAdapter
  implements AgentCommunicationPort
  deterministic projection:
    INFORM -> INFO
    SUGGEST -> INFO
    WARN -> WARNING
    ASK -> ATTENTION
  preserves communication.agent() as source
  catches RuntimeException -> log and drop
  |
  v
AgentPresencePort.send(AgentPresenceMessage(source, title, body, level))
  existing Presence output boundary (unchanged)
  |
  v
HttpAgentPresenceAdapter
  serializes message.source() (was: hardcoded "DEVLOG")
  synchronous best-effort: 1s connect, 2s read timeout
  catches RestClientException -> log and drop
  |
  v
ESP32/OLED device
  receives {source, title, body, level} JSON
```

The delivered architecture introduces a minimal communication seam. The dependency chain flows from the future agent through `AgentCommunicationPort`, through the Presence projection adapter, to `AgentPresencePort`, and finally through the HTTP adapter to the device. Neither `AgentCommunication` nor `AgentCommunicationPort` depends on Presence, HTTP, or AI execution infrastructure.

## Contract Model

- `AgentCommunication(agent, intent, title, body)`: record with no external dependencies
- `AgentCommunicationIntent`: INFORM, ASK, WARN, SUGGEST
- `AgentCommunicationPort.communicate(AgentCommunication)`: single method, void return
- `AgentPresenceMessage(source, title, body, level)`: extended with `source`
- `AgentPresenceHttpRequest(source, title, body, level)`: unchanged (already supports source)
- No changes to `AgentPresencePort`, `AgentPresenceLevel`, or `AgentPresenceProperties`

## Authority Assessment

Target authority model (per ADR-063, ADR-067):

```text
JAVA_CORE = communication semantics + deterministic projection + source propagation
PYTHON = unchanged (probabilistic interpretation boundary)
PRESENCE = output surface only (unchanged authority model)
```

**Observed behavior:**
- Java defines the communication model and projection deterministically
- Java preserves agent identity end-to-end (no source invented by adapter)
- No Python-side changes
- No changes to AI task lifecycle or grounding contracts
- Presence remains replaceable presentation infrastructure

## Execution And Durability Assessment

The implementation operates within existing boundaries:

1. `AgentPresenceCommunicationAdapter` is a Spring `@Component` wired via constructor injection of `AgentPresencePort`
2. No new persistence entities or transaction boundaries
3. No new runtime state beyond the existing Spring bean graph
4. No changes to existing test contracts
5. Best-effort semantics preserved: no retry, no queue, no persistence

## Quality Evidence

- Targeted tests: 12/12 PASS (2 contract + 6 adapter + 4 HTTP)
- Full backend: 1267/1267 PASS
- JaCoCo checks: MET
- No new warnings introduced

## Final Assessment

```text
STRUCTURAL_VERTICAL_SLICE = PRESENT (model + port + adapter + tests)
END_TO_END_FLOW = PRESENT (AgentCommunication -> Presence -> HTTP -> ESP32)
ARCHITECTURAL_AUTHORITY = PRESERVED (Java/Core deterministic authority)
QUALITY_GATES = PASSED (1267/1267)
STORY_ACCEPTANCE_GATE = AWAITING_HUMAN_REVIEW
NEXT_STATE = HUMAN_ACCEPTANCE_DECISION
```
