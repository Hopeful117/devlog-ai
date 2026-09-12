# Story 0120 — Introduce Agent Communication Boundary

> **Status**: REFINEMENT  
> **Type**: Architecture / Capability Boundary  
> **Baseline**: `main` at `cddfc4f49e7d3595be655944742735a635e902d5`  
> **Implementation branch**: `story/0120-agent-communication-boundary`  
> **Governing ADRs**: ADR-006, ADR-067

---

## 0. Provenance

This Story is based on the human-refined Agent Communication versus Agent
Presence separation and inspection of current `main`. Current repository state
is authoritative. The existing Agent Presence HTTP integration is implementation
evidence, not the definition of the communication capability.

## 1. Product Motivation

Agent Presence is not a workflow notification system. A future agent may decide
that information, a question, a warning or a suggestion is useful enough to
communicate. That semantic communication is distinct from its projection onto a
physical or digital Presence surface.

Core principle:

> An agent does not speak merely because a workflow changed state. It speaks
> because it has information, a question, a warning, or a suggestion whose
> value justifies communicating with the user.

The ESP32/OLED device is the first Presence surface. It must not define the
future agent-facing communication contract.

## 2. Current Architecture

Current Agent Presence consists of:

- `AgentPresenceMessage(title, body, level)`;
- `AgentPresenceLevel` with `INFO`, `WARNING` and `ATTENTION`;
- `AgentPresencePort`;
- a synchronous best-effort HTTP adapter posting to `/message`;
- a wire DTO containing `source`, `title`, `body` and `level`.

The HTTP adapter currently supplies `source="DEVLOG"` itself. No production
consumer invokes Agent Presence, and no generic agent communication boundary or
future DevLog Agent runtime exists. The `ai.task` and `ai.engine` packages model
AI execution and must not be repurposed as that runtime.

## 3. Objective

Introduce a transport-independent boundary representing what an agent wants to
communicate, while keeping Presence responsible for how that communication is
projected to an optional surface.

```text
future DevLog Agent
        |
        v
AgentCommunication
        |
        v
AgentCommunicationPort
        |
        v
Presence projection adapter
        |
        v
AgentPresencePort
        |
        v
HTTP adapter -> ESP32
```

## 4. Domain Model

V1 communication contains exactly:

- `agent`: emitting identity, represented as a string;
- `intent`: `INFORM`, `ASK`, `WARN` or `SUGGEST`;
- `title`;
- `body`.

`AgentCommunication` has no dependency on Agent Presence, HTTP, device DTOs or
AI execution infrastructure.

## 5. Presence Projection

The deterministic V1 projection is:

| Communication intent | Presence level |
|---|---|
| `INFORM` | `INFO` |
| `SUGGEST` | `INFO` |
| `WARN` | `WARNING` |
| `ASK` | `ATTENTION` |

Agent identity propagates unchanged:

```text
AgentCommunication.agent
        -> AgentPresenceMessage.source
        -> AgentPresenceHttpRequest.source
```

The HTTP adapter serializes the supplied source and does not select or hardcode
an agent identity.

## 6. Invariants

1. Communication semantics are independent of Presence transport.
2. Presence projection is deterministic and contains no agent reasoning.
3. The emitting agent identity is preserved end to end.
4. Presence delivery is synchronous best effort and optional.
5. Presence failure cannot fail the originating producer operation.
6. No retry, queue, persistence, broker or transaction coupling is introduced.
7. AI output and trusted knowledge boundaries from ADR-006 and ADR-067 remain
   unchanged.

## 7. Scope

- Add the communication value and intent model.
- Add the communication application port.
- Add one Presence projection adapter over `AgentPresencePort`.
- Add `source` to `AgentPresenceMessage`.
- Make the HTTP adapter serialize the dynamic source.
- Preserve the existing ESP32 JSON contract and bounded HTTP behavior.
- Add focused contract, seam and transport tests.

## 8. Non-Goals

- A `DevLogAgentService` or any agent runtime.
- Agent orchestration, autonomous reasoning or communication decisions.
- Workflow-state-to-device wiring, including SCA completion.
- LLM calls for communication: decisions.
- Communication persistence, retries, queues, brokers or outbox patterns.
- Dynamic service discovery or multi-agent scheduling.
- A `PresencePolicy` framework.
- New agent type hierarchies.
- Device firmware changes.

## 9. Acceptance Criteria

### AC-1 — Communication model

Given an agent wants to communicate, when it creates an `AgentCommunication`,
then that value contains agent identity, intent, title and body.

### AC-2 — Presence independence

Given a communication producer, when it depends on the communication boundary,
then neither the communication model nor its port depends on HTTP, ESP32 or the
Agent Presence HTTP adapter.

### AC-3 — Deterministic Presence projection

Given each V1 communication intent, when it is projected to Presence, then the
mapping is exactly `INFORM -> INFO`, `SUGGEST -> INFO`, `WARN -> WARNING` and
`ASK -> ATTENTION`.

### AC-4 — Agent identity propagation

Given an emitting agent, when communication reaches the device adapter, then
the same value appears in `AgentPresenceMessage.source` and HTTP `source`.

### AC-5 — Best effort preserved

Given Presence is unavailable or delivery fails, when a producer communicates,
then the failure is logged and dropped without failing the producer operation;
no retry occurs.

### AC-6 — Existing transport compatibility

Given the current ESP32 wire contract, when a communication is delivered, then
the JSON remains `{source,title,body,level}` and no firmware change is required.

### AC-7 — No premature agent runtime

Given this V1 seam, when implementation is complete, then no agent runtime,
orchestrator, reasoning loop, persistence, broker, scheduler or policy framework
has been introduced.

### AC-8 — Tests

Focused tests prove all intent mappings, source propagation, communication
contract independence, traversal into `AgentPresencePort`, dynamic HTTP source,
and existing connection, timeout and serialization failure behavior.

## 10. LEARN / PAIR / DELEGATE

### LEARN

- Preserve Agent Communication versus Agent Presence separation.
- Preserve intent semantics and transport independence.
- Preserve best-effort Presence semantics.
- Do not create the future DevLog Agent runtime.

### PAIR

- Introduce the application port and deterministic Presence projection.
- Keep dependency direction explicit and report how a future agent calls it.

### DELEGATE

- Propagate `source` through Presence and HTTP DTO mapping.
- Update focused fixtures and transport tests.

## 11. Test Strategy

- Communication contract reflection test for transport-independent signatures.
- Presence projection seam test using a recording/mock `AgentPresencePort`.
- Exhaustive four-intent mapping test.
- Boundary failure-isolation test.
- Real local HTTP server contract test for dynamic source and existing JSON.
- Existing refused-connection, read-timeout and serialization-failure tests.
- Full backend Maven verification, coverage gate and `git diff --check`.

## 12. Architectural Consequences

### Positive

- Future agents receive a stable semantic communication seam.
- Presence remains replaceable presentation infrastructure.
- Agent identity is no longer invented by the HTTP adapter.
- Optional device failure remains isolated from producer operations.

### Cost

- One additional application port and one projection adapter are introduced.
- Delivery remains intentionally lossy and synchronous in V1.

### Deferred

- The agent decision mechanism that chooses whether and when to communicate.
- Additional Presence surfaces or communication channels.
- Delivery observability beyond existing logging.
- Any durability, acknowledgement or retry semantics.
