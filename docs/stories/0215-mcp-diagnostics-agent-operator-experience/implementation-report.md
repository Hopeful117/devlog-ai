# Implementation Report - Story 0215

## Repository State

- Branch: `main`.
- Worktree: modified, not committed or pushed.
- DevLog context: unavailable for this implementation pass; repository artifacts
  and accepted ADR-081 were used as authority.

## Files Changed

- `mcp-server/.../CoreCallPolicy.java`: adds bounded diagnostic metadata to Core
  errors.
- `mcp-server/.../CoreCallPolicyTest.java`: verifies enriched Core diagnostics.
- `mcp-server/.../ResourceSupportTest.java`: verifies enriched authentication
  diagnostics and non-disclosure.
- `mcp-server/.../SyncProjectHistoryToolTest.java`: updates Core error contract
  assertions.
- `tools/devlog_mcp/runtime.py`: adds the allowlisted diagnostic catalog,
  category mapping and event enrichment.
- `tools/devlog_mcp/status.py`: projects diagnostic fields and validates runtime
  configuration in `doctor`.
- `tools/devlog_mcp/tests/test_runtime.py`: covers additional categories and
  structured diagnostic projection.
- `tools/devlog_mcp/tests/test_status.py`: covers bounded diagnostic projection.
- `TOOLS.md`: documents the diagnostic contract and redaction boundary.
- `docs/epics/mcp-error-resilience-policy/epic.md`: links Story 0215.
- `docs/stories/0215-mcp-diagnostics-agent-operator-experience/`: Story,
  Repository Analysis, Implementation Plan and this report.

## Implementation Summary

Core MCP errors now expose allowlisted `source`, `category`, `code`, `layer`,
`retryable` and `action` metadata while preserving existing messages and HTTP
mapping. The Python runtime projects the same bounded diagnostic vocabulary for
JSON-RPC failures, lifecycle events and `status`/`doctor` output. No retry,
idempotency, identity or delegation behavior was changed.

The independent review findings were addressed: explicit JSON-RPC diagnostic
codes are used in terminal events, `doctor` endpoint failures carry bounded
diagnostics, provenance fields are preserved, and Core functional, resource,
and fallback errors use the same contract.

The follow-up review findings were also addressed: known diagnostic codes are
canonical and cannot be overridden by contradictory response fields, unknown
codes normalize to `MCP_UNKNOWN_DIAGNOSTIC`, local `invalidParams` errors carry
functional diagnostics, and status projection derives metadata from either
`code` or `reasonCode`.

## Tests Executed

```bash
python3 -m unittest discover -s tools/devlog_mcp/tests -v
./backend/mvnw -pl mcp-server test -B
git diff --check
python3 tools/devlog_mcp/status.py doctor --json
```

## Results

- Python runtime/status suite: 54 tests passed.
- MCP Java module suite: 115 tests passed.
- `git diff --check`: passed.
- `status.py doctor --json`: completed and reported existing stopped/orphaned
  local runtime instances without exposing credentials.
- Maven emitted existing Mockito dynamic-agent warnings; no test failed.

## Architectural Decisions Preserved

- Java Core remains authoritative for Core error provenance.
- Python remains authoritative for transport lifecycle diagnostics.
- Retry budgets and idempotency rules remain unchanged.
- Existing observability events and projections are reused.
- No migration, persistent diagnostic table or new telemetry stream was added.

## Documentation Outcome

`TOOLS.md` was updated because the operator-facing `status`/`doctor` contract
changed. No frontend change was required: existing Activity projections already
consume stable `reasonCode` values and no new persisted contract was introduced.

## Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed.

## Known Limitations

- Live validation with real credentials was not performed.
- The detailed operator runbook remains deferred to the next Epic Story.
- Existing local runtime state contains historical orphaned instances; this was
  observed, not modified.

## Readiness

READY_FOR_HUMAN_REVIEW

Human Story acceptance remains pending. No commit or push was performed.
