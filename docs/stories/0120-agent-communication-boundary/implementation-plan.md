# Story 0120 - Implementation Plan

## Status

**IMPLEMENTED - ON STORY BRANCH, AWAITING HUMAN REVIEW**

This plan records the implementation sequence for Story 0120.

## Planned Vertical Slice

Per governing design (Story 0120 refinement):

1. **Communication model** — `AgentCommunication` record with agent, intent, title, body
2. **Intent enum** — `AgentCommunicationIntent` with INFORM, ASK, WARN, SUGGEST
3. **Application port** — `AgentCommunicationPort` interface
4. **Presence projection adapter** — deterministic mapping from intent to `AgentPresenceLevel`
5. **Source propagation** — `AgentPresenceMessage.source` field; HTTP adapter consumes it
6. **Tests** — contract, projection seam, source propagation, failure isolation, transport compatibility

## Actual Implementation Sequence

| Step | Change | Files |
|------|--------|-------|
| 1 | Create Story 0120 documentation | `docs/stories/0120-agent-communication-boundary/story.md` |
| 2 | Add `AgentCommunication` record | `backend/.../agentcommunication/AgentCommunication.java` |
| 3 | Add `AgentCommunicationIntent` enum | `backend/.../agentcommunication/AgentCommunicationIntent.java` |
| 4 | Add `AgentCommunicationPort` interface | `backend/.../agentcommunication/AgentCommunicationPort.java` |
| 5 | Add `AgentPresenceCommunicationAdapter` with deterministic projection | `backend/.../agentcommunication/adapter/presence/AgentPresenceCommunicationAdapter.java` |
| 6 | Add `source` field to `AgentPresenceMessage` | `backend/.../agentpresence/AgentPresenceMessage.java` |
| 7 | Replace hardcoded `"DEVLOG"` with `message.source()` in HTTP adapter | `backend/.../agentpresence/adapter/http/HttpAgentPresenceAdapter.java` |
| 8 | Add `AgentCommunicationContractTest` (reflection-based independence) | `backend/src/test/.../AgentCommunicationContractTest.java` |
| 9 | Add `AgentPresenceCommunicationAdapterTest` (parameterized mappings + failure isolation) | `backend/src/test/.../AgentPresenceCommunicationAdapterTest.java` |
| 10 | Update `HttpAgentPresenceAdapterTest` for dynamic source | `backend/src/test/.../HttpAgentPresenceAdapterTest.java` |

## Verification Planned

- Focused `AgentCommunicationContractTest` (contract + independence)
- Focused `AgentPresenceCommunicationAdapterTest` (4 intent mappings + failure isolation)
- Focused `HttpAgentPresenceAdapterTest` (JSON contract + dynamic source + failure scenarios)
- Full backend Maven verification with coverage (`./backend/mvnw -pl backend -am clean verify -B`)
- `git diff --check`

## Explicitly Unchanged

- No agent runtime, orchestrator, scheduler, or reasoning loop
- No workflow-state-to-device wiring (SCA completion, etc.)
- No communication persistence, retries, queues, brokers, or outbox patterns
- No LLM calls for communication decisions
- No device firmware changes
- No API/MCP contract changes
- No new persistence entities
- No changes to `ai.task` or `ai.engine` packages
- No new ADR required
