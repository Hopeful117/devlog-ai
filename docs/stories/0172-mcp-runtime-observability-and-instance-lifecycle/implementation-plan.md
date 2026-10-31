# Implementation Plan

## Overview

Implement a repository-owned local MCP runtime supervisor and keep the
machine-local OpenCode configuration as a thin entry point. The supervisor will
acquire the technical Keycloak bearer, create an instance manifest, supervise
the Java MCP child, persist redacted lifecycle events, and terminate only its
owned child when STDIO closes or the OpenCode parent disappears.

A read-only status/doctor command will inspect manifests and events without
modifying processes or credentials. The design will avoid a global singleton
lock so a fresh OpenCode session can always start an independent MCP instance.

The implementation preserves the existing MCP/Core/AI boundaries. It does not
change actor classification, delegation, provider credentials, or task
authorization.

## Planned Changes

### 1. Add a runtime supervisor

Component: local MCP runtime tooling.

- Replace the current unobservable `curl | jq | exec java` bootstrap flow with a
  small repository-owned supervisor using only the host Python standard library
  and the Java runtime already required by MCP.
- Read the client secret only from the existing protected machine-local file.
- Acquire the Keycloak token without logging the request body or response token.
- Start the MCP JAR with inherited STDOUT and controlled STDERR handling.
- Preserve STDIO protocol bytes without writing diagnostics to STDOUT.
- Forward termination, STDIN close and parent disappearance to the owned child.
- Record a final exit event and return the child exit code.

Constraint: the supervisor must remain an adapter around the existing MCP
process; it must not implement DevLog authorization or provider logic.

### 2. Define structured redacted events

Component: runtime event writer.

- Generate a random `instanceId` for every launcher invocation.
- Write JSON Lines events under
  `${XDG_STATE_HOME:-~/.local/state}/devlog/mcp/<instanceId>/`.
- Include timestamps, event names, severity, diagnostic code, process identity,
  HTTP status where safe, and bounded duration fields.
- Normalize exception text and never serialize headers, tokens, request bodies,
  prompts or model output.
- Apply a bounded retention policy to completed instance directories.

Constraint: logs must remain useful after process exit and must not become a
second secret store.

### 3. Add ownership and stale-instance handling

Component: instance manifest and cleanup logic.

- Record launcher PID, MCP PID, parent OpenCode PID and Linux process start
  times.
- Validate PID plus process start time before treating a process as owned.
- Monitor the parent process while the MCP child is running.
- Mark ambiguous records as `STALE_OWNERSHIP_RECORD` without force-killing.
- Terminate only a child whose parent/session ownership is proven.
- Make cleanup idempotent and avoid any global lock or fixed TCP port.

Constraint: PID reuse and a live unrelated OpenCode session must never cause
destructive cleanup.

### 4. Add the read-only diagnostic command

Component: runtime status/doctor tool.

- List active, stopped, orphaned and ambiguous instances.
- Show last lifecycle event, readiness state, parent ownership and recommended
  action.
- Check Java, JAR presence, configured endpoints and non-secret configuration
  completeness.
- Return stable exit categories suitable for shell automation.
- Never kill, restart, renew, revoke or modify any runtime state.

Constraint: the command must be safe to run during an incident.

### 5. Integrate the machine-local OpenCode entry point

Component: `/home/ludo/.config/opencode/devlog-mcp-start.sh` and
`opencode.jsonc`.

- Keep the OpenCode MCP command as a thin launcher entry point.
- Point it to the repository-owned runtime supervisor using an explicit
  workspace path or documented installation link.
- Preserve the existing environment contract and secret file location.
- Ensure a fresh OpenCode process starts its own supervisor and does not reuse a
  stale STDIO process.

The machine-local configuration is not a repository artifact and must be
updated only as an explicit deployment/setup step after implementation is
validated.

### 6. Add tests and operator documentation

Components: runtime tooling tests and `TOOLS.md`.

- Test redaction, event schema, bootstrap failure mapping, PID start-time
  validation, parent disappearance, STDIN close, repeated cleanup and
  ambiguous ownership.
- Test that STDOUT remains byte-compatible with MCP protocol traffic.
- Test two sequential launchers with an old instance present.
- Document runtime paths, retention, event categories, status/doctor usage and
  safe incident procedure.

## Files to Modify

- `TOOLS.md`: document the runtime supervisor, event policy and diagnostic
  commands.
- `docs/stories/0172-mcp-runtime-observability-and-instance-lifecycle/story.md`:
  update implementation status and final implementation references only after
  the implementation workflow reaches those stages.
- `/home/ludo/.config/opencode/devlog-mcp-start.sh`: machine-local thin wrapper,
  updated only during deployment/setup validation.
- `/home/ludo/.config/opencode/opencode.jsonc`: only if the command path needs
  to reference the repository-owned supervisor explicitly.

No Core Java, AI Engine, database migration or MCP authorization contract
change is planned.

## Files to Create

- `tools/devlog-mcp/runtime.py`: supervisor, token bootstrap, process ownership,
  redacted event writing and STDIO lifecycle handling.
- `tools/devlog-mcp/status.py`: read-only instance inspection and doctor checks.
- `tools/devlog-mcp/tests/test_runtime.py`: unit and subprocess tests for
  lifecycle, ownership and redaction behavior.
- `tools/devlog-mcp/README.md`: local installation and troubleshooting details
  if `TOOLS.md` alone is insufficient.

The exact Python module split may be reduced if the resulting implementation is
smaller without weakening testability or ownership safety.

## Dependencies

- Existing host Python 3 runtime, standard library only.
- Existing Java runtime and MCP JAR.
- Existing Keycloak token endpoint and client-secret file.
- Existing OpenCode local MCP configuration.
- Linux `/proc` process start-time metadata for PID reuse protection.

No new external package or service is required. The implementation must fail
clearly on platforms where the required process-identity checks are not
available rather than silently weakening cleanup safety.

## Test Plan

### Tests to create

- event schema and redaction tests covering all secret-like fields;
- token endpoint success, HTTP rejection, malformed response and timeout tests;
- missing dependency and missing JAR tests;
- process start-time mismatch and ambiguous manifest tests;
- parent disappearance and STDIN-close cleanup tests;
- idempotent cleanup tests;
- STDOUT/STDERR separation tests;
- fresh-session independence integration test;
- read-only status/doctor contract tests.

### Validation commands

```bash
python -m pytest tools/devlog-mcp/tests -q
./backend/mvnw -pl mcp-server test
git diff --check
```

When the runtime integration requires a local OpenCode process, validate with a
controlled technical actor and mock Core/Keycloak endpoints. Do not start human
OAuth, use provider credentials, or create a delegation as part of the default
test suite.

### Expected success conditions

- all lifecycle tests pass;
- no redaction test discovers a secret in events or diagnostics;
- MCP protocol output is unchanged;
- a fresh instance starts while an old instance exists;
- an owned orphan is cleaned safely;
- an ambiguous process is never killed;
- existing MCP regression tests pass.

## Risks

- **Transparent STDIO supervision can corrupt protocol traffic.** Mitigation:
  keep diagnostics on STDERR/files and test byte-preserving forwarding before
  integrating the supervisor.
- **PID reuse can cause false ownership.** Mitigation: require PID plus process
  start time and refuse ambiguous cleanup.
- **The parent PID may not identify the semantic OpenCode session on every
  platform.** Mitigation: record process start identity, document the supported
  platform, and fail closed where identity cannot be proven.
- **Machine-local configuration can drift from the repository.** Mitigation:
  keep the wrapper thin, document the expected command, and add a doctor check
  that reports the configured JAR and workspace.
- **Persistent logs can accumulate sensitive data through unexpected exception
  text.** Mitigation: normalize messages, use allow-listed fields and test
  redaction against representative failures.

No blocking architectural conflict is identified. The implementation must stop
and request clarification if process readiness cannot be proven without adding
an MCP protocol proxy that changes the approved STDIO boundary.

## Validation Checklist

- [ ] Runtime supervisor is repository-owned and has no external dependency.
- [ ] STDOUT is reserved exclusively for MCP protocol traffic.
- [ ] Every instance has a unique manifest and redacted event stream.
- [ ] Bootstrap failures have stable bounded diagnostic codes.
- [ ] Parent PID and process start time protect cleanup from PID reuse.
- [ ] Fresh sessions do not depend on stale instances or global locks.
- [ ] Parent disappearance and STDIN close clean up only owned children.
- [ ] Ambiguous ownership is reported and not force-killed.
- [ ] Status/doctor is read-only and idempotent.
- [ ] No Core, AI Engine, OAuth or delegation boundary changed.
- [ ] Launcher, MCP regression and runtime tests pass.
- [ ] `git diff --check` passes.
- [ ] Documentation describes retention and safe troubleshooting.

## Recommendation

Ready for implementation

## Approval Required

Implementation Plan completed.

Human approval required before Implementation.

Awaiting explicit human approval.
