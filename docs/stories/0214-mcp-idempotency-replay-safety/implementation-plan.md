# Implementation Plan - Story 0214

## Implementation Gate

This plan governed the implementation after the open decisions and contract
proposal were explicitly approved. Human acceptance remains a separate gate.

## Phase 1 - Freeze the Contract

1. Confirm the operation matrix in `repository-analysis.md`.
2. Decide whether the Core contract is extended to project registration,
   history synchronization, follow-ups, or none of them.
3. Define the idempotency-key format, maximum size, canonical request digest,
   scope, retention, and conflict error.
4. Define the response for an identical request that is already in progress.
5. Define whether MCP creates keys or requires a caller-provided key.
6. Confirm the boundary between retry policy, reconciliation, and manual
   recovery.

## Phase 2 - Core Contract Design

1. Map each conditional mutation to one authoritative Core use case.
2. Reuse the existing Story Agent idempotency behavior where it is compatible.
3. Ensure key reuse with a different canonical request produces a stable
   conflict without exposing the original payload.
4. Ensure the key is scoped to the operation, project, principal, and relevant
   delegation context.
5. Define the persisted state needed to distinguish `IN_PROGRESS`, completed,
   failed, and unknown outcomes.
6. Define atomic behavior for concurrent submissions with the same key.
7. Verify that response reuse does not bypass current authorization checks.

## Phase 3 - MCP Adapter and Runtime Integration

1. Pass the idempotency contract only through the approved MCP/Core adapter
   boundary.
2. Keep the runtime `operationId` as lifecycle and observability identity only.
3. Prevent generic replay of mutations whose Core contract is absent.
4. Make ambiguous mutation results return a bounded recovery status rather than
   silently retrying.
5. Ensure retry metadata and diagnostics never include raw keys, tokens, or
   sensitive payloads.
6. Preserve Story 0212 bearer renewal ownership and Story 0213 normalized error
   handling.

## Phase 4 - Tests

### Runtime tests

- safe read replay;
- mutation rejection after ambiguous result;
- distinction between `operationId` and idempotency key;
- key redaction in diagnostics;
- bounded handling of `429` if included in scope.

### MCP server tests

- key propagation for supported operations;
- no key propagation where the operation is explicitly non-replayable;
- normalized conflict response;
- follow-up correlation behavior;
- authorization and delegation preservation.

### Core tests

- identical key and identical digest returns one logical result;
- same key and different digest returns conflict;
- concurrent identical submissions create one effect;
- cross-project or cross-principal reuse is rejected;
- `IN_PROGRESS`, completed, and failed result behavior;
- no raw key or payload in persisted diagnostics/events.

### Integration tests

- response lost after Core accepted a mutation;
- client restart before replay;
- retry after an accepted result;
- manual status lookup for an ambiguous mutation;
- history synchronization behavior if explicitly included.

## Phase 5 - Documentation

1. Update the operation/retry/idempotency matrix.
2. Document client responsibilities for caller-provided keys.
3. Document the manual recovery path for non-replayable mutations.
4. Document conflict and in-progress responses.
5. Add examples without real identifiers, tokens, keys, or repository data.

## Verification Commands

Focused verification should be selected after the implementation files are
approved. Expected commands are:

```bash
cd tools/devlog_mcp && python -m pytest -q
./backend/mvnw -pl mcp-server test
./backend/mvnw -pl backend test
```

The full backend verification remains:

```bash
./backend/mvnw -pl backend -am clean verify -B
```

No command is claimed as executed for this planning pass.

## Scope Guardrails

- Do not add generic retries.
- Do not change bearer renewal or delegation behavior.
- Do not infer idempotency from repository-content deduplication alone.
- Do not make `register_project` replayable without an explicit Core contract.
- Do not add durable queues, brokers, or circuit breakers.
- Do not commit, push, merge, or declare Story acceptance.

## Readiness Gate

The Story is ready for implementation planning review, not implementation.
Implementation remains `BLOCKED` until the listed human decisions are resolved
and explicit authorization is granted.
