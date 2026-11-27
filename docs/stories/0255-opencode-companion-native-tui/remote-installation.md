# Remote Installation Notes — Proposed TUI Companion

These notes describe documented OpenCode loading models consulted on
2026-11-27. They are not installation instructions for the current user
profile, do not authorize package publication or configuration changes, and
must be rechecked against the pinned release before use.

## Deployment models

### A. Local terminal and local OpenCode server

The TUI plugin executes in the local CLI process. A server-bearing plugin
package may expose a `./tui` part loaded alongside server plugins. A TUI-only
package can be loaded from CLI configuration. Determine which mechanism Story
0255 chooses and document one canonical installation path; do not duplicate
entries across files without evidence. Plugin loading does not itself establish
authorization for unrelated sessions. The accepted ADR-084 amendment permits only a
presentation projection from the currently routed session's already-loaded
native TUI data; it grants no new authorization.

### B. Local terminal connected to a remote OpenCode server

The current official CLI-plugin documentation states that the CLI obtains the
active plugin list from its connected server for packages exposing TUI
components. It also documents `cli.json` for CLI-only plugins; those execute
locally in the terminal and remain active while connected to a remote server.
This makes a local presentation adapter over a remote server plausible. Under
the accepted ADR-084 amendment, it may inspect only the current routed session's
already-loaded native TUI data after normal server authentication; it adds no
server authorization, RPC, observer, or DevLog request. Session filtering is
presentation scoping, not an authorization boundary.

The exact behavior on the pinned host is unverified. Before supporting this
mode, establish where plugin code executes, which server supplies already
loaded session data, how the active session/location is scoped, and what
happens on reconnect. Do not confuse the local TUI plugin with a remote server
plugin or claim authorization from local route filtering.

### C. TUI running on an SSH host

The CLI/TUI process and its CLI configuration are on the SSH host. Install or
provision the package there only under owner/admin approval and through the
approved deployment mechanism. Validate the host's OpenCode version, package
availability, terminal capabilities, authentication, remote server target,
and cleanup. Do not copy local credentials or inspect a remote filesystem from
the local machine as a fallback.

### D. Desktop

Desktop's GUI plugin path is separate from a TUI-only `cli.json` entry. The
existing `gui-experimental` export should remain usable independently. Desktop
and sidecar pairing need their own validation; TUI checks do not qualify
Desktop.

## Configuration and secrets

- Prefer a versioned package reference or an explicitly approved local path;
  pin the release rather than using an unversioned floating dependency.
- `cli.json` is CLI/TUI configuration, distinct from `opencode.json(c)` server
  configuration. Avoid global edits; prefer a disposable test profile for
  approved validation.
- Do not put DevLog credentials in TUI plugin options, client-facing state,
  terminal output, command arguments, or configuration. The proposed feature
  should need no DevLog credentials and should make no direct DevLog request.
- For remote-server authentication, use OpenCode's supported authenticated
  connection mechanism; never print or retain tokens in evidence.
- Record package source, version, configuration path (redacted where needed),
  CLI/server versions and topology.

## Install / update / rollback checklist

1. Confirm explicit owner authorization and target topology.
2. Verify the package and dependency versions against the pinned CLI/TUI build.
3. Back up only the configuration file that the approved test will change;
   verify the backup path and ownership.
4. Add or update only the Companion entry; do not replace unrelated plugin
   lists or alter Desktop settings.
5. Start/reload only as authorized; record sanitized plugin-load and UI
   outcomes.
6. Roll back the exact Companion entry and restore the approved backup if
   needed. Do not delete pre-existing profiles or stop unrelated services.
7. Verify the TUI contribution is absent after removal and Desktop/Claude
   remain unaffected.

No install, activation, update, rollback, SSH connection, or remote filesystem
operation was performed in preparing this document.
