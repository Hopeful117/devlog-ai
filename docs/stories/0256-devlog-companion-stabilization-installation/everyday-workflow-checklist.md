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

- [ ] Confirm the Companion panel is available and opens/closes normally.
- [ ] Open Now, Context, and History; confirm each view remains accessible.
- [ ] Confirm normal avatar and presence rendering. Presence changes only when
      existing observed DevLog activity provides evidence; absent or ambiguous
      evidence remains `UNKNOWN`.
- [ ] During naturally occurring work, observe an existing DevLog Code Mode
      operation. Confirm History records the observed execution and does not
      invent nested outcomes. Do not call a tool just to create test activity.
- [ ] Switch sessions and confirm no stale activity or Context selection is
      displayed.
- [ ] If no suitable existing activity/result occurs, record the observation as
      `NOT EVALUATED`; do not fabricate activity or invoke a tool for validation.

## OpenCode TUI

- [ ] Confirm the DevLog status contribution appears without replacing the
      built-in footer.
- [ ] Open the panel with the `devlog-companion` command in a valid session.
- [ ] Confirm History is accessible, readable, and bounded.
- [ ] During ordinary engineering work, observe activity naturally and confirm
      displayed state reflects only available evidence; missing/ambiguous
      outcomes remain unknown. Do not manufacture activity.
- [ ] Switch sessions and confirm the prior session's activity is not stale.
- [ ] Close the panel and exit normally; confirm no stale panel/listener state.
- [ ] If no suitable existing activity occurs, record `NOT EVALUATED`.

## Claude Companion

- [ ] Confirm no Claude installation or configuration change was introduced.
- [ ] Observe normal existing behavior only; record an owner-observed regression
      if one occurs.
- [ ] Do not reinstall/update Claude or claim parity from an OpenCode check.

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
