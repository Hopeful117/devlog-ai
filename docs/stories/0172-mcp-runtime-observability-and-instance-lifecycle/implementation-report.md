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

### Readiness

READY_FOR_HUMAN_REVIEW
