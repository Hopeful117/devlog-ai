# Story 0120 - Implementation Report

## Status

**IMPLEMENTED - ON STORY BRANCH, AWAITING HUMAN REVIEW**

## Repository State

- Branch: `story/0120-agent-communication-boundary`
- HEAD: uncommitted (based on `cddfc4f` on `main`)
- Worktree: source changes + build artifacts in `devlog-contracts/target/`
- Commit created by this task: NO (awaiting human authorization)
- Push performed by this task: NO

## Governance

- Governing ADRs: ADR-006, ADR-067
- Authorized Story: Story 0120 — Introduce Agent Communication Boundary
- Trust target: transport-independent communication boundary, deterministic Presence projection
- Java/Core authority target: communication semantics, intent-to-level mapping, source propagation

## Implementation Summary

Implemented Story 0120 — a transport-independent communication boundary separating what an agent wants to communicate from how that communication is projected to a Presence surface. Added `AgentCommunication` record, `AgentCommunicationIntent` enum, `AgentCommunicationPort` interface, and `AgentPresenceCommunicationAdapter` with deterministic projection (INFORM→INFO, SUGGEST→INFO, WARN→WARNING, ASK→ATTENTION). Extended `AgentPresenceMessage` with `source` field and replaced the hardcoded `"DEVLOG"` in `HttpAgentPresenceAdapter` with dynamic `message.source()`.

## Production Components

### Backend (Java)
- `backend/.../agentcommunication/AgentCommunication.java`: transport-independent value record (agent, intent, title, body)
- `backend/.../agentcommunication/AgentCommunicationIntent.java`: INFORM, ASK, WARN, SUGGEST
- `backend/.../agentcommunication/AgentCommunicationPort.java`: application port
- `backend/.../agentcommunication/adapter/presence/AgentPresenceCommunicationAdapter.java`: deterministic Presence projection adapter
- `backend/.../agentpresence/AgentPresenceMessage.java`: added `source` field
- `backend/.../agentpresence/adapter/http/HttpAgentPresenceAdapter.java`: dynamic source instead of hardcoded `"DEVLOG"`

### Tests
- `backend/.../AgentCommunicationContractTest.java`: 2 tests (record accessors + reflection-based independence proof)
- `backend/.../AgentPresenceCommunicationAdapterTest.java`: 6 tests (4 parameterized intent mappings + failure isolation + source propagation assertions)
- `backend/.../HttpAgentPresenceAdapterTest.java`: 4 tests (updated for dynamic source)

## Documentation

- `docs/stories/0120-agent-communication-boundary/story.md`: canonical Story (REFINEMENT state)

## Recorded Verification

```text
TARGETED_TESTS = 12 passed (2 contract + 6 adapter + 4 HTTP)
FULL_BACKEND_TESTS = 1267 passed
JACOCO_CHECKS = MET
GIT_DIFF_CHECK = PASS
```

## Architectural Decisions Preserved

- Java/Core deterministic authority: communication semantics and projection remain deterministic
- Transport independence: `AgentCommunication` has zero imports from Presence, HTTP, or AI packages
- Best-effort delivery: synchronous, log-and-drop on failure, no retry/persistence/queue
- Source propagation: `AgentCommunication.agent()` → `AgentPresenceMessage.source()` → HTTP `source`
- No premature agent runtime: only 3 types + 1 adapter; no orchestrator, scheduler, broker, or reasoning loop
- ADR-006/ADR-067 trust boundaries unchanged

## Known Limitations

- No wiring to `main()` / application startup (intentional — no agent runtime exists)
- Presence failure isolation is log-and-drop by design (no retry, queue, or persistence)
- V1 communication is lossy: delivery failures are logged and dropped
- No observability beyond existing logging

## Git Diff Summary

Uncommitted changes: 3 modified files + 6 new files + build artifacts.

| File | Change |
|------|--------|
| `AgentCommunication.java` | NEW — transport-independent value record |
| `AgentCommunicationIntent.java` | NEW — intent enum |
| `AgentCommunicationPort.java` | NEW — application port |
| `AgentPresenceCommunicationAdapter.java` | NEW — Presence projection adapter |
| `AgentPresenceMessage.java` | MODIFIED — added `source` field |
| `HttpAgentPresenceAdapter.java` | MODIFIED — dynamic source |
| `AgentCommunicationContractTest.java` | NEW — contract + independence tests |
| `AgentPresenceCommunicationAdapterTest.java` | NEW — projection + failure tests |
| `HttpAgentPresenceAdapterTest.java` | MODIFIED — dynamic source fixture |
| `story.md` | NEW — Story documentation |

## Readiness

```text
IMPLEMENTATION_PRESENT = YES
COMMITTED_ON_BRANCH = NO (uncommitted changes)
MERGED = NO
PUSH_PERFORMED = NO
HUMAN_ACCEPTANCE = PENDING
READY_FOR_HUMAN_REVIEW = YES
```
