# Story 0211 - Implementation Report

## Repository State

- Branch: `chore/devlog-preflight-and-current-worktree`
- HEAD: `cbbdf518`
- Worktree: modified; existing untracked planning artifacts are preserved.
- Commit created: NO
- Push performed: NO

## Governance

- Governing ADRs: ADR-077, ADR-078, ADR-081.
- Authorized Story: Story 0211 - Politique normative des erreurs MCP et des
  retries.
- Human implementation authorization: received in the current session.

## Files Changed

- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/resource/ResourceSupport.java`:
  backend `401` responses now carry structured Core authentication provenance
  across project, resource, snapshot and follow-up paths.
- `mcp-server/src/main/java/hopefull117/devlogai_mcp/mcp_server/tool/SyncProjectHistoryTool.java`:
  history synchronization backend `401` responses now use Core provenance.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/resource/ResourceSupportTest.java`:
  verifies Core provenance across project, resource, snapshot and follow-up
  paths, including serialized JSON-RPC `error.data`.
- `mcp-server/src/test/java/hopefull117/devlogai_mcp/mcp_server/tool/SyncProjectHistoryToolTest.java`:
  verifies the synchronization tool's Core `401` mapping.
- `tools/devlog_mcp/runtime.py`:
  transport authentication retry detection rejects non-transport structured
  error data and only replays an explicit read-only allowlist.
- `tools/devlog_mcp/tests/test_runtime.py`:
  verifies Core `401` relay behavior and prevents replay of non-idempotent tools.
- `docs/decisions/ADR-081.md`:
  accepted architectural policy governing classification and retry boundaries.
- `docs/stories/0211-mcp-error-retry-policy/story.md`:
  status updated to implementation awaiting human review.

## Implementation Summary

The critical ADR-081 collision is fixed. Backend Core `401` responses now carry
`data.source=CORE` and `data.category=CORE_AUTHENTICATION`, while preserving the
existing user-facing messages. The Python runtime accepts the legacy transport
authentication shape only when no conflicting provenance is present or when
the data explicitly identifies transport, so a Core `401` cannot renew or replay
the MCP bearer operation.

The existing one-renewal/one-retry budget and Story 0172 terminal transition
invariants are unchanged.

## Tests Added/Modified

- `ResourceSupportTest.mapsBackendUnauthorizedAsCoreAuthenticationWithProvenance`:
  verifies structured Core provenance and wire serialization.
- `ResourceSupportTest` additional project, snapshot and follow-up cases:
  verifies every `ResourceSupport` backend `401` path.
- `SyncProjectHistoryToolTest.mapsBackendUnauthorizedAsCoreAuthentication`:
  verifies the remaining direct backend client path.
- `RuntimeTests.test_core_authentication_error_does_not_trigger_transport_retry`:
  verifies relay-level non-retry behavior for the collision case.
- `RuntimeTests.test_non_idempotent_tool_is_not_replayed_after_transport_auth_failure`:
  verifies the idempotence allowlist before replay.

## Commands Executed

```bash
uv run --project ai-engine pytest tools/devlog_mcp/tests/test_runtime.py -q
./mcp-server/mvnw test -Dtest=ResourceSupportTest -B
./mcp-server/mvnw test -B
python3 -m compileall -q tools/devlog_mcp
git diff --check
```

## Test Results

- Python runtime focused suite: **40 passed**.
- Java focused suites: **13 passed**.
- Full MCP server Maven suite: **100 passed, 0 failures, 0 errors**.
- Python syntax compilation: passed.
- Diff whitespace validation: passed.

The Maven suite emits existing Mockito dynamic-agent and Spring test warnings;
they do not fail the build.

## Known Limitations

- ADR-081's broader retry matrix for backend timeouts, `5xx` and `429` is not
  implemented in this change because the repository has no single existing
  backend retry authority or operation-level idempotency contract. Transport
  auth replay is now restricted to an explicit read-only tool allowlist.
- Core `403`, provider failures and protocol failures are not yet projected
  through a complete shared taxonomy; they remain fail-closed through existing
  mappings.
- Legacy transport errors without structured data remain accepted for backward
  compatibility. Future transport producers should add explicit
  `source=TRANSPORT` provenance.
- The DevLog preflight was unavailable earlier in the session because the MCP
  connection closed; this report relies on repository evidence.

## Architectural Decisions Preserved

- Java remains authoritative for backend error provenance.
- Python does not reconstruct Core authorization or context.
- Transport bearer renewal remains separate from Core authorization,
  delegation and provider credentials.
- Story 0172 operation, generation and terminal-transition invariants remain
  unchanged.
- No secret or request payload is added to diagnostics.

## Readiness

IMPLEMENTED - AWAITING HUMAN REVIEW.

Human review is specifically required for the remaining ADR-081 phases and for
deciding whether they belong in follow-up Stories before this Story is accepted.
