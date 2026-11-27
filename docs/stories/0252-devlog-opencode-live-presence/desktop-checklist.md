# Story 0252 — Short Manual Desktop Validation

**Owner procedure only, after formal ADR-084 amendment and Story/plan approval,
implementation authorization, and separate authorization for the real
operation.** This preparation does not launch/reload Desktop, install/enable a
plugin, or change active configuration.

## Before the check

- [ ] OpenCode Desktop is already running with the already-installed local
      Companion artifact loaded. If not, stop; do not install, edit config, or
      reload without separate authorization.
- [ ] Record Desktop, sidecar/CLI, `@opencode/plugin`, OS, local topology, and
      built artifact revision/hash. Do not record secrets or personal paths.
- [ ] Confirm the current route/session is the intended test session. Do not
      enumerate or inspect unrelated sessions.
- [ ] The owner explicitly authorizes the read-only DevLog operation used for
      visual validation. Do not trigger other MCP/REST calls.

## Procedure

1. Open the Companion panel for the already-routed session. Confirm a neutral
   initial/idle presentation only after the current native session view is
   available; it must not imply DevLog/MCP health.
2. In that same session, perform the owner-authorized read-only DevLog operation.
   Observe whether native evidence reports a recognized direct tool part as
   running. If it completes too quickly for the host to expose a running state,
   record `NOT OBSERVED`; do not fabricate `WORKING`.
3. After completion, confirm `READY` appears only if the direct result
   explicitly contains a successful DevLog task outcome. Host/tool completion
   alone must not be presented as business-task success. A confirmed failure
   may show `ERROR` with its evidence class; do not intentionally cause a real
   failure just to exercise the color.
4. If Code Mode is already enabled/configured and the owner authorizes its use,
    perform one read-only DevLog operation through it. Confirm the UI recognizes
    only explicit nested `toolCalls` rows. With the pinned name-only terminal
    rows, a recognized DevLog name should remain `UNKNOWN`; do not infer a
    running/terminal state, nested call ID, or correlate a nested result to a
    row. Otherwise mark this case `NOT RUN`.
5. Switch the panel's routed session using normal UI navigation, then return.
   Confirm the prior session's live state is cleared/reconciled and no stale
   update paints into the new route. Do not inspect the other session's
   transcript/activity.
6. Close and reopen the panel on the same route. Confirm it uses only already-
   loaded native state, adds no Companion request, and falls back neutrally if
   evidence is unavailable or incomplete. Do not reload Desktop or alter
   plugin/profile configuration.

## Record outcome

- [ ] Mark each step `PASS`, `FAIL`, `BLOCKED`, or `NOT RUN` with concise
      sanitized evidence.
- [ ] Do not record prompts, raw arguments/results, credentials, task payloads,
      or session content. Use only a sanitized screenshot/status and version/hash
      metadata where needed.
- [ ] Record unsupported states honestly. `DISCONNECTED` is expected to remain
      unsupported unless reliable native evidence for the displayed boundary
      was established separately.
- [ ] Owner records acceptance separately. This check does not establish Claude
      qualification, TUI/remote support, or full cross-platform compatibility.
