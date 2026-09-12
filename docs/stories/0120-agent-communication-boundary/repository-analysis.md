# Story 0120 - Repository Analysis

## Status

**IMPLEMENTED - ON STORY BRANCH**

## Baseline

- Baseline SHA: `cddfc4f49e7d3595be655944742735a635e902d5` (`main`)
- Branch: `story/0120-agent-communication-boundary`
- Governing ADRs: ADR-006, ADR-067
- Prior Story: Story 0119 — Consistent Scoped Evidence Through SCA (merged)

## Existing Boundaries Reused

- `AgentPresencePort` for output-surface delivery
- `AgentPresenceMessage` as the Presence transport record (extended with `source`)
- `AgentPresenceHttpRequest` as the wire DTO (already includes `source`)
- `HttpAgentPresenceAdapter` as the synchronous best-effort HTTP adapter
- `AgentPresenceLevel` for Presence severity levels

## Pre-Implementation Findings

1. **No generic communication boundary exists.** The repository has no `AgentCommunication`, `AgentCommunicationPort`, or any transport-independent communication seam.
2. **No agent runtime exists.** The `ai.task` and `ai.engine` packages model AI execution and must not be repurposed as an agent runtime.
3. **Agent Presence is output-only.** `AgentPresencePort` has a single `send()` method; it is a Presence surface boundary, not a semantic communication boundary.
4. **HTTP adapter hardcodes source.** `HttpAgentPresenceAdapter` currently injects `"DEVLOG"` as `source` in the wire DTO, despite the DTO supporting dynamic source.
5. **No workflow-state-to-device wiring exists.** No production code invokes `AgentPresencePort.send()`.
6. **ESP32 wire contract is compatible.** The existing `{source, title, body, level}` JSON structure already supports the required source propagation.

## Implemented Topology

```text
Story 0120
  |
  v
AgentCommunication(agent, intent, title, body)
  transport-independent value record
  |
  v
AgentCommunicationPort.communicate(communication)
  application port — future agent depends on this
  |
  v
AgentPresenceCommunicationAdapter (implements AgentCommunicationPort)
  deterministic projection:
    INFORM -> INFO
    SUGGEST -> INFO
    WARN -> WARNING
    ASK -> ATTENTION
  passes communication.agent() as AgentPresenceMessage.source
  |
  v
AgentPresencePort.send(AgentPresenceMessage(source, title, body, level))
  existing Presence output boundary
  |
  v
HttpAgentPresenceAdapter
  serializes message.source() (was: hardcoded "DEVLOG")
  synchronous best-effort HTTP POST to /message
  |
  v
ESP32/OLED device
  receives {source, title, body, level} JSON
```

## Repository Findings

The implementation introduces a minimal communication seam without touching any existing production call paths. Key findings:

1. **Communication independence verified.** `AgentCommunication` and `AgentCommunicationPort` have zero imports from `agentpresence`, `http`, `esp32`, or `ai.task`/`ai.engine`. Verified by reflection-based contract test.

2. **Presence projection is deterministic.** The adapter is a pure `switch` expression over `AgentCommunicationIntent`. No randomness, no external state.

3. **Source propagation closes a gap.** The HTTP adapter previously hardcoded `"DEVLOG"` despite the wire DTO supporting dynamic source. Now the agent identity flows from `AgentCommunication.agent()` through `AgentPresenceMessage.source()` to the HTTP payload.

4. **Best-effort semantics preserved.** Both `AgentPresenceCommunicationAdapter` (catches `RuntimeException`) and `HttpAgentPresenceAdapter` (catches `RestClientException`) log and drop failures without propagating. No retry, queue, or persistence introduced.

5. **No scope creep.** No agent runtime, orchestrator, scheduler, broker, or reasoning loop was introduced. The implementation is strictly 3 new types + 1 adapter + test files.

## Test Coverage Assessment

**New tests (8 tests across 2 files):**
- `AgentCommunicationContractTest`: 2 tests (record accessors + reflection-based independence proof)
- `AgentPresenceCommunicationAdapterTest`: 6 tests (4 parameterized intent mappings + failure isolation + 2 source propagation assertions)

**Existing tests (unchanged, all passing):**
- `HttpAgentPresenceAdapterTest`: 4 tests (JSON contract + dynamic source + serialization failure + read timeout + connection failure)
- Total: 12 focused tests pass

## Conclusion

All Story 0120 ACs are satisfied. The communication boundary is transport-independent, the Presence projection is deterministic, agent identity propagates end-to-end, best-effort semantics are preserved, and no premature agent runtime was introduced. Full backend verification (1267/1267) passes with JaCoCo coverage gates met.
