# Story 0255 — Owner-led TUI Validation

This checklist is for a separately authorized, pinned runtime validation. A
successful build or test suite is not TUI runtime evidence.

## Guardrails

- Use an approved disposable profile and exact version matrix.
- Do not modify the normal OpenCode profile, shared service, Desktop plugin
  configuration, Gate, Claude, or remote host without explicit scope.
- Do not use a real DevLog operation or provider call unless separately
  authorized.
- Record version/configuration source and sanitized outcomes; never record
  prompts, payloads, credentials, or unredacted logs.

## Checklist

- [ ] Record CLI/TUI, server, plugin SDK, OpenTUI, Solid, OS and terminal
      versions.
- [ ] TUI plugin discovery/loading is confirmed from the intended configuration
      source; no unrelated plugin is disabled.
- [ ] Native status contribution appears without replacing built-in footer or
      stealing global shortcuts.
- [ ] Namespaced command opens the Companion panel/route only in a valid
      session; narrow terminal layout remains usable.
- [ ] Loading, empty inspected window, Now states, History rows, and unavailable
      data are distinct and truthful.
- [ ] Direct MCP host completion is kept distinct from explicit DevLog task
      outcome.
- [ ] Code Mode is grouped at outer execution level; no fabricated nested call
      identity, chronology, result, or status appears.
- [ ] Concurrent operations, out-of-order events, late responses and
      interruption remain unknown/incomplete when evidence is insufficient.
- [ ] Session, project/workspace, location/server switch clears the previous
      view before new data is displayed.
- [ ] Panel close, plugin reload/disable and TUI exit remove listeners and
      bounded in-memory state.
- [ ] A TUI observer/presentation error does not block ordinary host operation.
- [ ] No new DevLog/MCP/REST request, credential access, persistence, or
      cross-session broadcast occurs.
- [ ] Plugin can be disabled/removed without affecting Desktop GUI or Claude.

## Outcome

- Runtime outcome: `PARTIAL` — isolated local TUI loaded the plugin and
  rendered its `DevLog:` session status slot for a synthetic empty session.
  The owner separately reported that the Companion works (2026-11-27).
- Topology: local TUI connected to a same-host isolated OpenCode server.
  Remote/SSH topology: `NOT TESTED`.
- Version tuple and config source: CLI/TUI `2.4.2`; isolated server `2.4.2`;
  plugin SDK `@opencode/plugin@2.4.0`; `@opentui/solid@0.9.4`;
  `solid-js@1.9.17`; Linux PTY, `xterm-256color`. A marker-owned temporary
  XDG profile supplied `cli.json` pointing to the local package.
- Test-data source: synthetic, newly-created empty session; isolated DB
  contained zero messages; no DevLog/MCP server configured.
- Sanitized evidence reference: local startup/setup and cleanup logs plus the
  rendered `DevLog:` marker; only boolean outcomes retained in the report.
- Cleanup verified: `YES` — isolated TUI and server stopped and temporary
  profile removed; shared service and normal configuration were untouched.
- Owner acceptance: `YES` — owner explicitly accepts the partial runtime
  evidence limitation and directs Story closure (2026-11-27). PR #254 was
  subsequently merged into `main`; this does not upgrade the partial runtime
  evidence recorded above.

The adapter's status-slot rendering is confirmed in the isolated local
topology. The panel command/history rendering was not independently confirmed
by the automation harness; the earlier owner smoke report is recorded
separately. Route-switch behavior, visible panel interaction, and lifecycle
checks beyond observed shutdown cleanup remain incomplete. Unchecked items
remain unverified and no remote topology is qualified.
