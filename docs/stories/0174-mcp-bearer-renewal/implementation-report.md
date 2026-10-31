## Implementation Report

### Repository State

- Branch: `main`
- Worktree: modified Story 0174 files; pre-existing untracked Playwright and
  OAuth artifacts remain untouched.
- Story 0173 cleanup completed before Story 0174 implementation.

### Governance

- Governing ADRs: ADR-077, ADR-078
- Governing Stories: Story 0174, with Story 0172 runtime ownership constraints
- Repository Analysis: approved by the human project owner.
- Implementation Plan: approved by the human project owner.
- DevLog context: Story Agent request failed because authentication is required;
  repository artifacts were used as authoritative context.

### Files Changed

- `tools/devlog_mcp/runtime.py`: private token lease, bounded expiry handling,
  renewal/reacquisition, owned child replacement, request tracking and fail-closed
  diagnostics.
- `tools/devlog_mcp/status.py`: read-only last-renewal event summary.
- `tools/devlog_mcp/tests/test_runtime.py`: lease, malformed response, protocol
  forwarding and in-flight fail-closed tests.
- `TOOLS.md`: renewal behavior, diagnostics and recovery documentation.
- `docs/stories/0174-mcp-bearer-renewal/story.md`: implementation status.
- `docs/stories/0174-mcp-bearer-renewal/repository-analysis.md`: approved-stage
  analysis.
- `docs/stories/0174-mcp-bearer-renewal/implementation-plan.md`: approved-stage
  plan.
- `docs/stories/0174-mcp-bearer-renewal/implementation-report.md`: this report.

### Implementation Summary

The repository-owned MCP supervisor now treats the technical bearer as a
bounded lease. Before forwarding a new request near expiry, it reacquires the
technical token, replaces only the owned Java child, replays only the MCP
`initialize` handshake, and waits for readiness before forwarding new traffic.
Already-forwarded requests are never replayed except for one explicit
`Authentication is required` / `-32600` transport-authentication response;
in-flight or ambiguous requests cause a fail-closed `MCP_REAUTH_REQUIRED`
outcome.

The implementation preserves Core authorization, actor/delegation semantics,
AI Engine provider ownership, and STDIO protocol separation.

### Architectural Decisions Preserved

- Transport bearer renewal is separate from human delegation and provider OAuth.
- Core remains authoritative for authorization and project/provider decisions.
- No bearer, client secret, delegation token or provider credential enters MCP
  tool output, prompts or runtime events.
- Only the current runtime's owned child can be replaced.
- Existing PID/start-time ownership and bounded redaction behavior remain in use.

### Commands Executed

```bash
python3 -m py_compile tools/devlog_mcp/runtime.py tools/devlog_mcp/status.py tools/devlog_mcp/tests/test_runtime.py
python3 -m unittest discover -s tools/devlog_mcp/tests -v
python3 tools/devlog_mcp/status.py status --json
./backend/mvnw -pl mcp-server test -B
git diff --check
```

### Test Results

- Python runtime tests: 15 passed.
- Python syntax compilation: passed.
- MCP module tests: 70 passed, 0 failures, 0 errors.
- Read-only status command: executed successfully without changing runtime
  state.
- `git diff --check`: passed.
- Live Keycloak renewal and child replacement: not run; no current machine
  credential or active MCP session was used as a test fixture.

### Independent Review Follow-up

The independent review identified five major gaps. They were corrected before
this report was finalized:

- replacement initialization responses are consumed internally and
  `notifications/initialized` is replayed to the replacement child;
- the first request is lease-gated even before the initial handshake is cached;
- malformed and non-object token responses fail with bounded diagnostics;
- non-finite and too-short leases are rejected;
- JSON token, delegation and prompt-like fields are covered by redaction tests.
- one bounded retry is covered for the explicit MCP transport-authentication
  error; second failures are not retried;
- process-generation isolation covers stale partial stdout output and replacement
  readiness synchronization.
- non-object JSON-RPC input is rejected before forwarding.

### Documentation Outcome

Updated `TOOLS.md` with lease renewal, child replacement, no-replay behavior,
diagnostic events and safe recovery semantics.

### Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed because this
Story is fully grounded in repository ADRs, Story artifacts, runtime code and
controlled tests.

### Known Limitations

- Renewal is checked when new newline-delimited JSON-RPC input arrives rather
  than by proactively restarting an idle child.
- The supervisor preserves the repository's existing newline-delimited STDIO
  assumption; unsupported framing or malformed JSON fails closed.
- No live Keycloak or long-session test was run with real local credentials.
- Story acceptance remains a human decision.

### Commit/Push

- Commit created: NO
- Push performed: NO

### Readiness

READY_FOR_CODE_REVIEW
