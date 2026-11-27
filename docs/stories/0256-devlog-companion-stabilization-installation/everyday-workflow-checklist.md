# Owner-led Everyday Workflow Checklist — Proposed Story 0256

Use during ordinary engineering work after any separately approved installation
or update. Do not create a DevLog operation, prompt, or artificial activity just
to complete this checklist. If suitable activity is not already present, record
`NOT EVALUATED`.

## Before use

- [ ] Record Companion artifact revision, platform version, and configuration
      source without copying credentials or unrelated config.
- [ ] Confirm the loaded package path is stable and is not a temporary Story
      worktree.
- [ ] Confirm unrelated plugin entries and the other Companion installation
      remain unchanged.
- [ ] Confirm rollback means restoring the previous pinned artifact/path; keep
      that artifact available until checks pass.

## OpenCode Desktop

- [ ] Companion opens and closes normally.
- [ ] Now, Context, and History remain accessible.
- [ ] Presence changes only when existing, observed DevLog activity supplies
      evidence; absent or ambiguous evidence remains `UNKNOWN`.
- [ ] Code Mode nested Context remains explicitly unavailable/unknown.
- [ ] Session switching does not display stale activity or Context selection.
- [ ] If no suitable existing activity/result is available, record the relevant
      observation as `NOT EVALUATED`; do not invoke a tool for the test.

## OpenCode TUI

- [ ] Status contribution appears without replacing the built-in footer.
- [ ] Namespaced Companion command opens the panel in a valid session.
- [ ] Existing History is readable and bounded.
- [ ] Presence reflects only available evidence; missing/ambiguous outcomes stay
      unknown.
- [ ] Session switching clears the previous session's display.
- [ ] Closing the panel and exiting the TUI leave no stale panel/listener state.
- [ ] If no suitable existing activity is available, do not manufacture one.

## Claude Companion

- [ ] Confirm the existing installation source/version without changing it.
- [ ] Observe normal existing behavior only; record an owner-observed regression
      if one occurs.
- [ ] Do not reinstall Claude or claim parity from an OpenCode check.

## Remote TUI, only when an appropriate environment already exists

- [ ] SSH-hosted TUI: verify package and CLI are installed on the actual TUI host;
      do not copy credentials or inspect the remote filesystem from another host.
- [ ] Local TUI + remote server: verify which side supplies the TUI plugin and
      session cache, normal server authentication, reconnect behavior, and that
      duplicate registration does not occur.
- [ ] If no suitable environment exists, record `NOT EVALUATED`; no remote
      compatibility claim follows.

## Evidence record

- Date and owner:
- Platform/topology and exact versions:
- Pinned artifact/config source:
- Observed outcomes (sanitized):
- `PASS` / `FAIL` / `NOT EVALUATED` per item:
- Rollback needed/performed (only if authorized):
- Claude regression observed (yes/no/not evaluated):
