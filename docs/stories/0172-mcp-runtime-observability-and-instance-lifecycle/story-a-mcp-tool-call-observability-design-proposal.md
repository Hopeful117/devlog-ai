# Story A - MCP Tool Call Observability Design Proposal

## Status

**IMPLEMENTED - READY FOR COMMIT REVIEW**

This document records the approved retry/correlation contract and the implemented
runtime behavior. Final repository acceptance remains a human delivery decision.

## Context and Objective

Story A extends Story 0172 with observability for one logical MCP tool
invocation. It does not change the MCP protocol, authorization, Java business
logic or provider ownership. Its purpose is to make retries, collisions,
terminal transitions and Java-generation changes observable without exposing
transport payloads or secrets.

The current runtime and its deterministic tests are the implementation reference.
`ToolOperation` and the `MCP_TOOL_*` events are implemented in the files listed
below.

## Sources

| Source | Status | Applicable requirements |
|---|---|---|
| `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/story.md` | Implemented, awaiting human review | Redacted structured events, STDIO integrity, bounded diagnostics, safe lifecycle and no credential/payload logging. See lines 97-120, 183-189, 245-274. |
| `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/implementation-plan.md` | Historical implementation plan | JSONL lifecycle boundary and standard-library runtime; no tool-operation contract. |
| `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/implementation-report.md` | Historical implementation report | Confirms the current supervisor scope; reports no tool-level observability model. |
| `tools/devlog_mcp/runtime.py` | Current implementation | Current transport, retry, generation and shutdown behavior. |
| `tools/devlog_mcp/tests/test_runtime.py` | Current tests | 32 deterministic runtime tests, including operation, retry, generation, shutdown and redaction coverage. |
| This Story A document | Approved contract | JSONL schema, transition rules and acceptance matrix implemented below. |

Git history contains the Story 0172 implementation commit `7eb31cc5`. Story A
extends that runtime without changing the MCP protocol, Java Core, or AI Engine.

## Current Runtime Flow

The implementation keeps one logical operation reservation for each active
JSON-RPC request.

1. `relay_stdin()` parses a client line and calls `forward()` (`runtime.py:537-560`).
2. `forward()` validates the JSON-RPC ID, creates a `ToolOperation`, reserves the
   ID, emits one `MCP_TOOL_STARTED` event and writes the payload to Java.
3. `start_child()` increments `self.generation` and passes that value to the
   `relay_stdout` thread (`runtime.py:282-307`).
4. `relay_stdout()` rejects stale thread generations, classifies transport
   authentication errors, and calls `retry_authentication()` before relaying
   the response (`runtime.py:333-363`).
5. `retry_authentication()` preserves the active operation and payload, renews
   the token, updates `current_generation` and retransmits without a second
   `STARTED` event.
6. `observe_response()` performs the single terminal transition and releases
   the ID reservation for ordinary JSON-RPC responses.
7. `consume_initialize_response()` is the path that calls `observe_response()`
   for ordinary JSON-RPC responses.
8. `stop_child()` increments `self.generation`, interrupts active operations
   and then terminates the child; old stdout threads become stale.

The implementation preserves the logical reservation across the complete retry
sequence and keeps retry state in `ToolOperation` rather than parallel maps.

## Decisions

| Subject | Decision | Justification | Status |
|---|---|---|---|
| P1 identity | Add an opaque `operationId`; distinguish `NEW_REQUEST`, `INTERNAL_RETRY` and `ID_COLLISION` at the call boundary. | Active JSON-RPC ID alone cannot identify retry provenance after `observe_response()`. | Approved and implemented |
| P1 reservation | Reserve the JSON-RPC ID for the whole logical operation, including renewal and retransmission. | Prevents a concurrent client request from occupying the ID between attempts. | Approved and implemented |
| P2 terminal state | Classify authentication before terminal transition. A recoverable first auth response leaves the operation `ACTIVE`. | Prevents an intermediate auth response from producing a false terminal event. | Approved and implemented |
| P2 retry failure | Renewal or retransmission failure becomes `FAILED` with `MCP_REAUTH_REQUIRED`, unless the stop barrier wins first. | A failed retry is a runtime failure, not automatically an interruption. | Approved and implemented |
| P3 generation | Keep `initial_generation` and `current_generation` on the logical operation. | A retry may cross Java generations while `operationId` remains stable. | Approved and implemented |
| ToolOperation | Keep one in-memory record per active logical operation. Fold retry count into it. | Avoids parallel maps with independently mutable retry state. | Approved and implemented |
| JSONL | Add four tool-operation event types to the existing redacted JSONL stream. | Preserves Story 0172's bounded local observability boundary. | Approved and implemented |

## ToolOperation Model

The minimal internal record is:

```python
ToolOperation(
    operation_id: str,              # opaque runtime UUID; safe to log
    request_id_key: str,            # canonical JSON-RPC ID; internal only
    initial_generation: int,
    current_generation: int,
    state: ACTIVE | RESPONDED | FAILED | INTERRUPTED,
    retry_count: int,
    retry_payload: bytes,           # internal only; never serialized
    started_at_monotonic: float,
)
```

`outstanding` should reserve `request_id_key -> ToolOperation` for the entire
logical lifetime. `request_payloads` and `request_retries` should not remain
independent sources of truth; their data belongs in the operation record or in
a private per-attempt structure. No operation record is persisted outside the
existing runtime process.

### Field responsibilities

| Field | Responsibility | Exposure |
|---|---|---|
| `operation_id` | Stable logical correlation identity. | JSONL `operationId`; opaque value only. |
| `request_id_key` | Canonical internal reservation key for the JSON-RPC ID. | Internal only; never logged. |
| `initial_generation` | Java generation that received the first attempt. | `STARTED` generation. |
| `current_generation` | Java generation currently expected to answer. | Response/terminal event generation. |
| `state` | Single logical lifecycle state. | JSONL status. |
| `retry_count` | Number of internal retries already committed. | Internal only. |
| `retry_payload` | Exact bytes required for a retransmission. | Internal only; never logged. |
| `started_at_monotonic` | Duration measurement immune to wall-clock changes. | Only derived bounded `durationMs` may be logged. |

The existing `outstanding` map may become the logical reservation map. The
existing `request_payloads` and `request_retries` maps should be removed or
reduced to private attempt bookkeeping during implementation; they must not be
updated independently of `ToolOperation`.

### Operation lifecycle

- `NEW_REQUEST`: under `self.lock`, reject `STOPPING`, validate the ID, reject
  an active reservation, create `ToolOperation`, reserve the ID and emit one
  `MCP_TOOL_STARTED`.
- `INTERNAL_RETRY`: under `self.lock`, resolve the supplied `operationId`,
  require `ACTIVE`, increment `retry_count`, preserve the reservation and
  retransmit the stored payload.
- `ID_COLLISION`: reject without changing the existing operation and without
  emitting a tool-operation event.
- Terminal transition: under `self.lock`, check `state == ACTIVE`, set the
  terminal state, remove the ID reservation and emit exactly one terminal
  event.

The reservation is released only by `RESPONDED`, `FAILED` or `INTERRUPTED`.

## Retry and Transition Contract

| Trigger | Initial state | Final state | Event | Normalized reason | Client behavior |
|---|---|---|---|---|---|
| Accepted client invocation | — | `ACTIVE` | `MCP_TOOL_STARTED` | — | Request is written to Java. |
| Active JSON-RPC ID | — | unchanged | None | `ID_COLLISION` internally | Existing transport behavior remains a protocol rejection. |
| Recoverable first auth response | `ACTIVE` | `ACTIVE` | None | — | Intermediate auth response is hidden; retry starts. |
| Retry after token renewal | `ACTIVE` | `ACTIVE` | None | — | Same operation and ID are retransmitted. |
| Nominal JSON-RPC response | `ACTIVE` | `RESPONDED` | `MCP_TOOL_RESPONDED` | — | Response is relayed once. |
| Non-auth JSON-RPC error | `ACTIVE` | `FAILED` | `MCP_TOOL_FAILED` | `MCP_JSONRPC_ERROR` | Error response is relayed once. |
| Second auth response after retry budget | `ACTIVE` | `FAILED` | `MCP_TOOL_FAILED` | `MCP_REAUTH_REQUIRED` | Terminal auth response is relayed once. |
| Renewal failure before retransmission | `ACTIVE` | `FAILED` | `MCP_TOOL_FAILED` | `MCP_REAUTH_REQUIRED` | No fabricated Java response is emitted. |
| STDIN write/flush failure | `ACTIVE` | `FAILED` | `MCP_TOOL_FAILED` | `MCP_PROCESS_FAILED` | No response is claimed; transport shutdown follows existing policy. |
| Stop barrier wins during retry | `ACTIVE` | `INTERRUPTED` | `MCP_TOOL_INTERRUPTED` | `MCP_INSTANCE_STOPPING` | No retry or client response is claimed. |
| Old-generation response | `ACTIVE` | unchanged | None | `STALE_GENERATION` internally | Response is discarded. |
| Response racing with terminal transition | `ACTIVE` or terminal | first terminal wins | At most one | State-specific | Later response is discarded. |
| ID reused after terminal transition | terminal | new `ACTIVE` | New `MCP_TOOL_STARTED` | — | New operation receives a new `operationId`. |

The first recoverable authentication response is not a terminal observation for
the logical operation. A retry failure is `FAILED`, not `INTERRUPTED`, unless
the stop barrier has already transitioned the operation.

## Generation Rules

- `initial_generation` is captured under `self.lock` when the operation is
  created, before the first write.
- `current_generation` initially equals `initial_generation`.
- The stdout relay supplies the generation of the Java process that produced a
  response; it must be used directly for response correlation.
- After `_renew()` starts a new Java process, the internal retry updates
  `current_generation` to that new process generation before the write.
- A response is eligible only when its relay generation equals
  `current_generation` and the operation remains `ACTIVE`.
- A stale response never changes operation state and never produces a terminal
  event.
- `stop_child()` may invalidate a generation, but it must not fabricate a new
  event generation for an old response.
- `operationId` never changes across retries or Java generations.

## JSONL Contract Proposal

All fields below are required unless marked optional. The event is appended to
the existing `events.jsonl` stream and remains subject to `EventWriter`'s
redaction and size limits.

### Common fields

```json
{
  "timestamp": "2026-01-01T00:00:00Z",
  "instanceId": "runtime-instance-id",
  "event": "MCP_TOOL_STARTED",
  "severity": "INFO",
  "operationId": "opaque-operation-id",
  "generation": 4,
  "status": "ACTIVE"
}
```

- `timestamp`: UTC ISO-8601 string.
- `instanceId`: existing runtime instance identifier.
- `event`: one of the four event names below.
- `severity`: `INFO`, `WARN` or `ERROR`.
- `operationId`: opaque runtime-generated identifier.
- `generation`: positive integer associated with the observed attempt/event.
- `status`: logical operation state.

### Event-specific fields

| Event | Required status | Optional fields |
|---|---|---|
| `MCP_TOOL_STARTED` | `ACTIVE` | `durationMs` is forbidden; no retry field is exposed. |
| `MCP_TOOL_RESPONDED` | `RESPONDED` | `durationMs` |
| `MCP_TOOL_FAILED` | `FAILED` | `reason`, `durationMs` |
| `MCP_TOOL_INTERRUPTED` | `INTERRUPTED` | `reason`, `durationMs` |

### Event examples

Each line is an independent JSON object. Values below are fictional.

```json
{"schemaVersion":"mcp-tool-observability-v1","eventType":"MCP_TOOL_STARTED","timestamp":"2026-01-01T00:00:00Z","instanceId":"instance-7f2","operationId":"operation-01","generation":4,"status":"ACTIVE","severity":"INFO"}
```

```json
{"schemaVersion":"mcp-tool-observability-v1","eventType":"MCP_TOOL_RESPONDED","timestamp":"2026-01-01T00:00:01Z","instanceId":"instance-7f2","operationId":"operation-01","generation":4,"status":"RESPONDED","severity":"INFO","durationMs":1000}
```

```json
{"schemaVersion":"mcp-tool-observability-v1","eventType":"MCP_TOOL_FAILED","timestamp":"2026-01-01T00:00:01Z","instanceId":"instance-7f2","operationId":"operation-02","generation":4,"status":"FAILED","severity":"ERROR","reasonCode":"MCP_REAUTH_REQUIRED","durationMs":1000}
```

```json
{"schemaVersion":"mcp-tool-observability-v1","eventType":"MCP_TOOL_INTERRUPTED","timestamp":"2026-01-01T00:00:01Z","instanceId":"instance-7f2","operationId":"operation-03","generation":5,"status":"INTERRUPTED","severity":"WARN","reasonCode":"MCP_INSTANCE_STOPPING","durationMs":1000}
```

### Validation and versioning

- `schemaVersion` is the literal `mcp-tool-observability-v1` for this proposal.
- `eventType` must be one of the four event names.
- `timestamp` must be a UTC ISO-8601 string.
- `instanceId` and `operationId` must be non-empty bounded strings.
- `generation` must be a positive integer.
- `status` must match the event type exactly.
- `severity` must be `INFO`, `WARN` or `ERROR`.
- `durationMs`, when present, must be a finite non-negative integer.
- `reasonCode` is required only for `FAILED` and `INTERRUPTED`.
- Additive fields require a new schema version if they change required meaning;
  existing v1 fields are not renamed or reinterpreted.
- A future incompatible schema uses a new `schemaVersion`; no silent fallback
  or mixed interpretation is permitted.

### JSONL write reliability

The logical transition under `self.lock` is authoritative for in-memory state.
The event write is a separate best-effort persistence step protected by the
existing `EventWriter` lock.

- A transition is committed before attempting to append its event, so a failed
  event write cannot roll back the transport state or reopen an operation.
- A failed append must be reported through the existing bounded runtime
  diagnostic path, for example `MCP_OBSERVABILITY_WRITE_FAILED` on STDERR or a
  fallback diagnostic channel that cannot corrupt STDOUT.
- The runtime must not retry an event append in a way that can create an
  unbounded duplicate stream.
- Exactly-once event delivery is not guaranteed by the current file writer.
  The guarantee is at-most-one logical terminal transition in memory; the
  persisted JSONL stream is best effort and may lose an event on I/O failure.
- A lost terminal event must never cause a second terminal event to be emitted.

Allowed normalized reasons include `ID_COLLISION` for internal diagnostics,
`MCP_JSONRPC_ERROR`, `MCP_REAUTH_REQUIRED`, `MCP_PROCESS_FAILED`,
`MCP_INSTANCE_STOPPING` and `STALE_GENERATION`. `ID_COLLISION` and
`STALE_GENERATION` must not be emitted as operation events.

The JSONL contract must not contain the raw JSON-RPC ID, method arguments,
request payload, response result, raw exception text, access token, client
secret, authorization header or provider content.

## Minimal Concurrency Rules

- `self.lock` protects operation creation, ID reservation, retry count, state,
  generation updates and terminal transitions.
- `relay_stdout()` performs authentication classification before any terminal
  transition.
- `retry_authentication()` receives an operation reference, not only an ID,
  and never calls a new-request path.
- The stop barrier sets `stop_requested` and prevents new operation creation.
- A retry already holding an `ACTIVE` operation may finish only if the stop
  barrier has not won the terminal transition.
- Terminal transitions use compare-and-set semantics under `self.lock`:
  only `ACTIVE` may transition to a terminal state.
- A failed `stdin.write()` or `flush()` transitions the reserved operation to
  `FAILED` and releases the ID exactly once.
- A response observed during cleanup is ignored if its generation is stale or
  the operation is already terminal.

## Exhaustive Transition Matrix

| Scenario | Initial state | Trigger | Final state | Event/reason | Client response | Cleanup/concurrency |
|---|---|---|---|---|---|---|
| New invocation | — | Valid `NEW_REQUEST` | `ACTIVE` | `STARTED` | Request forwarded | Reserve ID before write under lock. |
| Active-ID collision | `ACTIVE` | Valid new request, same ID | Existing state unchanged | No operation event; internal `ID_COLLISION` | Existing protocol rejection | Existing reservation untouched. |
| Nominal response | `ACTIVE` | Current-generation result | `RESPONDED` | `RESPONDED` | Response forwarded once | Release ID under lock. |
| Non-auth JSON-RPC error | `ACTIVE` | Current-generation error | `FAILED` | `FAILED/MCP_JSONRPC_ERROR` | Error forwarded once | Release ID under lock. |
| First recoverable auth error | `ACTIVE` | Auth error, retry available | `ACTIVE` | None | Intermediate response hidden | Keep logical reservation. |
| Internal retry | `ACTIVE` | `INTERNAL_RETRY` | `ACTIVE` | None | Same payload retransmitted | Same operation ID; update generation if renewed. |
| Second auth error | `ACTIVE` | Auth error, retry exhausted | `FAILED` | `FAILED/MCP_REAUTH_REQUIRED` | Terminal auth error forwarded | First terminal transition wins. |
| Keycloak renewal failure | `ACTIVE` | Renewal raises before write | `FAILED` | `FAILED/MCP_REAUTH_REQUIRED` | No fabricated response | Release ID; stop child according to existing policy. |
| Retry write failure | `ACTIVE` | `write()` fails | `FAILED` | `FAILED/MCP_PROCESS_FAILED` | No fabricated response | Release ID; stop child. |
| Retry flush failure | `ACTIVE` | `flush()` fails | `FAILED` | `FAILED/MCP_PROCESS_FAILED` | No fabricated response | Release ID; stop child. |
| Stop during retry | `ACTIVE` | Stop barrier wins lock | `INTERRUPTED` | `INTERRUPTED/MCP_INSTANCE_STOPPING` | No retry response claimed | Release ID; no new operation. |
| Stop before write | Reserved | Stop observed before write | `INTERRUPTED` | `INTERRUPTED/MCP_INSTANCE_STOPPING` | No Java response claimed | Reservation released exactly once. |
| Old-generation response | `ACTIVE` | Response from stale relay | `ACTIVE` | None | Response discarded | No cleanup; current attempt remains active. |
| Java restart during retry | `ACTIVE` | `_renew()` creates generation N+1 | `ACTIVE` | None | Retry targets N+1 | `current_generation=N+1`; old relay invalid. |
| Response races with stop | `ACTIVE` | Both contend for lock | First terminal state | One terminal event only | Depends on winner | Later transition is no-op. |
| ID reuse after termination | Terminal | Valid `NEW_REQUEST` | New `ACTIVE` | New `STARTED` | New request forwarded | New `operationId`; old record not mutated. |
| Child already exited | `ACTIVE` | Write or flush detects closed pipe | `FAILED` | `FAILED/MCP_PROCESS_FAILED` | No fabricated response | Release ID and stop is idempotent. |

Every terminal row is a compare-and-set from `ACTIVE` under `self.lock`.

## Write and Acceptance Boundaries

An invocation is accepted when validation succeeds and the operation plus its
JSON-RPC reservation have been installed under `self.lock`. `STARTED` is then
emitted once. The reservation remains active if the subsequent write fails;
that failure must immediately transition the operation to `FAILED` or
`INTERRUPTED`, release the reservation and never claim a Java response.

The observability append cannot block or alter STDOUT protocol forwarding. A
JSONL write failure is a diagnostic failure, not a transport retry trigger.

## Targeted Test Plan

The current `tools/devlog_mcp/tests/test_runtime.py` contains **32 tests**,
including operation-level observability tests. Story 0172's implementation
report claims older counts (`3` runtime tests and `69` MCP module tests); those
counts are historical and are not used as evidence for Story A.

Required deterministic tests for this proposal:

1. accepted new request creates one operation and one `STARTED`;
2. active ID collision changes neither operation nor reservation;
3. internal retry preserves `operationId`;
4. retry emits no second `STARTED`;
5. ID reuse after termination creates a new operation ID;
6. recoverable auth response followed by success emits no intermediate terminal;
7. exhausted auth retry emits exactly one `FAILED`;
8. renewal failure emits `FAILED/MCP_REAUTH_REQUIRED`;
9. stop winning during retry emits exactly one `INTERRUPTED`;
10. stale-generation response is discarded;
11. response event uses the relay's Java generation;
12. initial generation remains unchanged;
13. current generation updates after renewal;
14. JSONL contains no raw ID, payload, result, token or exception text;
15. `STOPPING` prevents a new reservation;
16. write failure releases the reservation and emits one failure;
17. flush failure has the same behavior;
18. stop between reservation and write produces one terminal event;
19. response racing with stop produces one terminal event;
20. reused JSON-RPC ID receives a distinct `operationId`.

Use barriers/events or direct lock-controlled test doubles for races. Do not use
arbitrary sleeps, real Keycloak, external networking or real credentials.

## Compatibility and Resolved Decisions

The proposal preserves the current external transport behavior: JSON-RPC bytes
remain on STDOUT, retries remain internal, and no new protocol response is
invented. The internal `forward()` call needs an explicit retry context, and
`renew_if_needed()` needs a narrowly scoped way to permit renewal for the
active operation without treating its logical reservation as a collision.

The tool-operation JSONL vocabulary, opaque `operationId`, retry-failure
classification, Story A scope and deterministic test plan were approved for the
implementation and final review represented by this change.

## Acceptance Criteria

| ID | Criterion | Verification |
|---|---|---|
| AC-A-01 | Each accepted invocation has exactly one opaque `operationId`. | Tests 1 and 20. |
| AC-A-02 | An active JSON-RPC ID collision cannot mutate the existing operation. | Test 2. |
| AC-A-03 | A retry preserves `operationId` and emits no second `STARTED`. | Tests 3 and 4. |
| AC-A-04 | An ID is reusable only after one terminal transition and receives a new operation ID. | Tests 5 and 20. |
| AC-A-05 | A recoverable auth response produces no terminal event. | Test 6. |
| AC-A-06 | Retry exhaustion and renewal failure produce one `FAILED/MCP_REAUTH_REQUIRED`. | Tests 7 and 8. |
| AC-A-07 | A stop that wins the lock produces one `INTERRUPTED`, not `FAILED`. | Tests 9, 18 and 19. |
| AC-A-08 | A stale Java generation cannot change operation state. | Tests 10 and 11. |
| AC-A-09 | `initial_generation` is stable and `current_generation` tracks the expected retry generation. | Tests 12 and 13. |
| AC-A-10 | JSONL events conform to v1, are redacted and contain no raw request/response data. | Test 14 and schema validation. |
| AC-A-11 | `STOPPING` prevents new operation reservation. | Test 15. |
| AC-A-12 | `write()` and `flush()` failures leave no active reservation and no fabricated response. | Tests 16 and 17. |
| AC-A-13 | At most one terminal transition is committed per operation under concurrent response/retry/stop. | Tests 9, 18 and 19. |
| AC-A-14 | Story 0172 lifecycle events, STDIO routing and redaction remain unchanged. | Existing Story 0172 regression suite plus tests 14-15. |
| AC-A-15 | No Keycloak, external network or persistent operation store is required. | Test isolation and implementation review. |

The 20 scenarios are coverage obligations, not a requirement for exactly 20
test methods.

## Validation Status

`IMPLEMENTED — READY FOR COMMIT REVIEW`

The implementation and final independent review are complete. The remaining
gate is human acceptance of the commit and delivery outcome.

## Readiness

`READY_FOR_COMMIT_REVIEW`
