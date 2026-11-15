## Implementation Report

### Repository State

- Branch: `main`
- HEAD: `c43c7f85baf0a3171e6e4519bb8043349d4ac093`
- Worktree: Modified by this Story; pre-existing untracked Playwright artifacts
  and `oauth-network.txt` remain untouched.

### Governance

- Governing ADRs: ADR-067, ADR-077, ADR-078
- Authorized Story: Story 0172 - MCP Runtime Observability and Instance Lifecycle
- DevLog context: unavailable through the connected DevLog MCP interface during
  analysis; repository and existing investigation used instead.

### Files Changed

- `tools/devlog_mcp/runtime.py`: supervised MCP STDIO launcher with token
  bootstrap, redacted events, process ownership and parent cleanup.
- `tools/devlog_mcp/status.py`: read-only status and doctor command.
- `tools/devlog_mcp/tests/test_runtime.py`: standard-library runtime tests.
- `TOOLS.md`: runtime paths, redaction policy and diagnostics usage.
- `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/story.md`:
  implementation status.
- `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/implementation-plan.md`:
  approved implementation plan.
- `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/implementation-report.md`:
  implementation evidence.
- `/home/ludo/.config/opencode/devlog-mcp-start.sh`: machine-local launcher now
  delegates to the repository supervisor.

### Implementation Summary

The launcher now creates a unique runtime instance, writes a non-secret
manifest and bounded JSONL lifecycle events, acquires the technical bearer
without logging it, and supervises the MCP Java child while preserving STDIO
protocol traffic. It detects parent-session disappearance, validates process
start times to avoid PID reuse, and never uses a global MCP lock.

The read-only doctor reports local endpoint reachability and instance state. MCP
readiness is recorded only after observing an `initialize` JSON-RPC response
with a protocol version.

### Architectural Decisions Preserved

- Core remains the authority for actor, project, delegation, task and provider
  authorization.
- `AI_AGENT`, human delegation and `AiCredentialBinding` semantics are
  unchanged.
- Provider credentials and OAuth tokens remain outside the repository runtime
  logs.
- STDOUT remains reserved for MCP protocol traffic.

### Tests Added/Modified

- `tools/devlog_mcp/tests/test_runtime.py`: redaction, structured events and
  Linux process identity.

### Commands Executed

```bash
python3 -m py_compile tools/devlog_mcp/runtime.py tools/devlog_mcp/status.py tools/devlog_mcp/tests/test_runtime.py
python3 -m unittest discover -s tools/devlog_mcp/tests -v
./backend/mvnw -pl mcp-server test -B
bash -n /home/ludo/.config/opencode/devlog-mcp-start.sh
git diff --check
```

### Test Results

- Runtime tests: 3 passed.
- MCP module tests: 69 tests passed, 0 failures, 0 errors.
- Controlled missing-secret launcher test: failed closed with status `1` and
  `CONFIGURATION_INVALID`.
- Read-only doctor: Core reachable with HTTP `200`; Keycloak token endpoint
  reachable with HTTP `405` for the intentionally unsupported GET probe.
- `git diff --check`: passed.

### Known Limitations

- No live successful token acquisition or MCP handshake was run in this
  implementation pass, to avoid creating another active MCP session and to
  avoid using the current machine secret for a runtime test.
- Existing MCP processes from the previous launcher format have no manifests
  and are not automatically terminated by this change.
- The machine-local OpenCode configuration remains outside Git and must be
  reviewed during deployment validation.
- Python is available as `python3` in this environment; `python` is not.

### Implementation-Level Decisions Made

- Use Python standard library for the supervisor to avoid new runtime packages
  and to make process ownership and redaction directly testable.
- Use Linux `/proc/<pid>/stat` start ticks together with PIDs to prevent PID
  reuse from authorizing cleanup.
- Keep only the latest bounded completed runtime records and cap each event log
  at 5 MiB.

### Blockers / Repository Inconsistencies

- Human acceptance is still required.
- Full live validation with a fresh OpenCode session remains a deployment-level
  check because the active MCP server is owned by another OpenCode instance.

### Git Diff Summary

The Story implementation adds the runtime supervisor, status/doctor tool,
standard-library tests and operator documentation. No Core, AI Engine, database
migration or MCP authorization contract was changed.

### Commit/Push

- Commit created: NO
- Push performed: NO

### Documentation Outcome

Updated `TOOLS.md` with runtime paths, redaction rules, status/doctor commands
and cleanup ownership behavior.

### Vault Outcome

The Obsidian vault was not consulted. No vault action is proposed; the Story is
fully grounded in repository, runtime and process evidence.

### Post-Acceptance Addendum (2026-11-15)

Human accepted on 2026-11-15. Regression tests added in worktree
`claude/story-0172-tests-f01-f02-e20a92`:

- **F-01** (`test_duplicate_request_ids_are_rejected_while_in_flight` —
  strengthened): verifies that a JSON-RPC collision is rejected with
  `MCP_PROTOCOL_INVALID`, the in-flight operation is preserved with its
  identity and `ACTIVE` state, no second `MCP_TOOL_STARTED` is emitted,
  the subsequent response is processed normally, and exactly one terminal
  event and correct cleanup follow.

- **F-02** (`test_two_outstanding_operations_blocks_renewal_on_auth_failure` —
  new): verifies that when an authentication failure arrives while two
  operations are active, `renew_if_needed` refuses renewal, the first
  operation ends as `FAILED/MCP_REAUTH_REQUIRED`, the second is
  interrupted as `INTERRUPTED/MCP_INSTANCE_STOPPING`, no additional
  `MCP_TOOL_STARTED` events are emitted, and all reservations are released.

Full suite result: **33 passed, 0 failures**. `git diff --check` clean.

Known limitation accepted: concurrent-renewal block when multiple MCP operations
are outstanding (see story.md § Statut).

### Readiness

ACCEPTED
