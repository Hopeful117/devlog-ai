# Story 0120 — Final Report

## Commit

- **SHA**: uncommitted (changes on `story/0120-agent-communication-boundary`)
- **Branch**: `story/0120-agent-communication-boundary`
- **Message**: N/A — awaiting human authorization to commit

## Governance

- **Authorizing Story**: Story 0120 (REFINEMENT)
- **Governing ADRs**: ADR-006, ADR-067
- **Commit created**: NO (awaiting human authorization)
- **Push performed**: NO

## Implementation Summary

Story 0120 implemented — a transport-independent communication boundary:

1. **Communication model** — `AgentCommunication(agent, intent, title, body)` record with zero external dependencies. `AgentCommunicationIntent` enum (INFORM, ASK, WARN, SUGGEST).

2. **Application port** — `AgentCommunicationPort` interface with single `communicate()` method.

3. **Presence projection** — `AgentPresenceCommunicationAdapter` maps each intent to a `AgentPresenceLevel`: INFORM→INFO, SUGGEST→INFO, WARN→WARNING, ASK→ATTENTION. Deterministic, no randomness.

4. **Source propagation** — `AgentPresenceMessage` extended with `source` field. `HttpAgentPresenceAdapter` now serializes `message.source()` instead of hardcoded `"DEVLOG"`.

5. **Best-effort preservation** — `AgentPresenceCommunicationAdapter` catches `RuntimeException` (log and drop). `HttpAgentPresenceAdapter` catches `RestClientException` (log and drop). No retry, persistence, or queue.

6. **Tests** — 12 focused tests: contract (2), projection seam (4 parameterized mappings), failure isolation (1), source propagation (1), transport compatibility (4).

## Files Changed (6 new + 3 modified)

### New Production Code
| File | Change |
|------|--------|
| `AgentCommunication.java` | NEW — transport-independent value record |
| `AgentCommunicationIntent.java` | NEW — intent enum |
| `AgentCommunicationPort.java` | NEW — application port |
| `AgentPresenceCommunicationAdapter.java` | NEW — Presence projection adapter |

### Modified Production Code
| File | Change |
|------|--------|
| `AgentPresenceMessage.java` | Added `source` field (+1 line) |
| `HttpAgentPresenceAdapter.java` | Dynamic source instead of hardcoded `"DEVLOG"` (+1/-1) |

### Tests
| File | Change |
|------|--------|
| `AgentCommunicationContractTest.java` | NEW — 2 tests (contract + independence) |
| `AgentPresenceCommunicationAdapterTest.java` | NEW — 6 tests (parameterized mappings + failure + source) |
| `HttpAgentPresenceAdapterTest.java` | MODIFIED — dynamic source fixture (+8/-6) |

## Verification

- **Targeted tests**: 12/12 PASS (2 contract + 6 adapter + 4 HTTP)
- **Full backend tests**: 1267/1267 PASS (BUILD SUCCESS)
- **JaCoCo coverage**: All checks met
- **git diff --check**: PASS

## Key Design Decisions

1. **Transport-independent communication model**: `AgentCommunication` has zero imports from `agentpresence`, `http`, `esp32`, or `ai.task`/`ai.engine`. Verified by reflection-based contract test.
2. **Deterministic projection**: Exhaustive `switch` expression maps each intent to exactly one `AgentPresenceLevel`. No external state, no randomness.
3. **Source propagation**: `AgentCommunication.agent()` → `AgentPresenceMessage.source()` → HTTP `source`. No source invented by adapter.
4. **Best-effort delivery**: Synchronous, log-and-drop on failure. No retry, persistence, queue, or broker. Intentionally lossy.
5. **Minimal scope**: 3 new types + 1 adapter. No agent runtime, orchestrator, scheduler, or reasoning loop.

## Known Limitations

- No wiring to `main()` / application startup (intentional — no agent runtime exists)
- Presence failure isolation is log-and-drop by design (no retry, queue, or persistence)
- V1 communication is lossy: delivery failures are logged and dropped
- No observability beyond existing logging
- No additional Presence surfaces or communication channels

## Readiness

**READY_FOR_HUMAN_REVIEW** — full Story 0120 implemented on branch

## Governance Checklist

- [x] Transport independence preserved
- [x] Deterministic Presence projection
- [x] Agent identity propagated end-to-end
- [x] Best-effort semantics preserved
- [x] No agent runtime introduced (AC-7)
- [x] No `ai.task`/`ai.engine` packages modified
- [x] Existing transport contract preserved (AC-6)
- [x] Full backend suite passes (1267/1267)
- [x] JaCoCo coverage checks met
- [x] No self-authorized commit/push
