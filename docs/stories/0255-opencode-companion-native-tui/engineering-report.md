# Engineering Report — Story 0255

## Outcome

The native OpenCode TUI Companion implementation is complete and the owner has
authorized closure while explicitly accepting the documented runtime evidence
limitation. Package validation and independent engineering/security review pass.
Local runtime evidence is partial: the status slot was observed, but panel/history
interaction was not independently verified by the automated harness.

Delivery status at report creation: **branch, commit, push and PR pending**.
Merge remains gated on the owner's final PR validation.

## Story and Repository

- Story: 0255 — OpenCode Companion — Native TUI.
- Repository: `Hopeful117/devlog-ai`.
- Base revision: `03d22b118f1c6c747b99d90283b0c1ca0539cbf2`.
- Integration target: `main`; fetched `origin/main` matches the base revision.
- Worktree: dedicated Story 0255 worktree, initially detached at `origin/main`.
- Owner accepted delivery with the runtime evidence limitation on 2026-11-27.

## Governance and Scope

- Governing architecture: accepted ADR-084 TUI-local projection amendment.
- Story, Repository Analysis, Implementation Plan, and implementation were
  approved/authorized by the owner as recorded in the canonical Story artifacts.
- The owner separately authorized CLI activation, package dependency
  installation for validation, an isolated local runtime qualification, and
  closure with the stated evidence limitation.
- TUI behavior is a bounded, sanitized, in-memory presentation projection of
  the current session's already-loaded native data. It adds no RPC, transcript
  synchronization, DevLog/MCP request, credential handling, persistence, or
  cross-session retrieval.
- Desktop and Claude adapters, server/backend contracts, and the Preflight Gate
  remain outside the implementation scope.

## Implementation

- Added a separate `./tui` package entry with a native session status slot and a
  namespaced Companion command/panel.
- Reused the existing pure, bounded presence/history projection logic.
- Added the exact `@opentui/solid@0.9.4` development dependency required for TUI
  JSX types and corrected the SDK `Context` type import.
- Preserved the existing GUI export and generated GUI bundle.
- The implementation and file-level details are recorded in
  [Implementation Report](implementation-report.md).

## Validation and Review

- `npm ci --ignore-scripts --no-audit --no-fund`: passed from the updated lockfile.
- `npm run pack:dry-run`: passed, including build, typecheck, all tests, and
  package-content inspection.
- Tests: **68 passed, 0 failed**.
- Typecheck: passed.
- Build: passed; generated GUI bundle remained byte-identical.
- Package dry-run: passed; no tarball artifact was created.
- `git diff --check` and Story/ADR Markdown link/whitespace checks: passed.
- Independent engineering review: **PASS**, no material findings.
- Independent security/privacy review: **PASS**, no material findings.

### Runtime qualification

- Isolated local CLI/TUI and same-host server: version `2.4.2`.
- Plugin SDK: `@opencode/plugin@2.4.0`; `@opentui/solid@0.9.4`;
  `solid-js@1.9.17`.
- A temporary XDG profile loaded the local package. With a synthetic empty
  session, the TUI rendered the `DevLog:` status slot.
- The isolated database contained no session messages. No model prompt was
  submitted, and no DevLog/MCP server was configured.
- The temporary server and profile were stopped/removed. The shared service,
  normal CLI configuration, Desktop configuration, and remote hosts were not
  modified by this qualification.
- **Not independently verified:** panel/history rendering, route-change
  behavior, remote/SSH topology, Desktop runtime, and the complete lifecycle
  checklist. The owner previously reported that the Companion functions and
  explicitly accepts this evidence limitation for delivery.
- See the [TUI checklist](tui-checklist.md) for the exact recorded outcome.

## Risks and Deferred Validation

- No remote/SSH compatibility claim is made.
- The panel/history interaction and route-change behavior remain without
  independent runtime evidence.
- Exact nested Code Mode metadata remains dependent on native runtime evidence;
  unsupported/missing outcomes remain unknown rather than inferred.
- These limitations were disclosed to and accepted by the owner for this
  delivery. They do not imply that untested topologies are supported.

## Architecture and Documentation

- ADR-084's accepted local projection boundary is preserved.
- Story, plan, checklists, package README, implementation report, and code review
  report reflect the implementation and evidence.
- Vault outcome: **no vault action**. This repository-specific adapter yielded
  no transverse guidance candidate; current ADR/Story and source remain the
  evidence sources.
- DevLog lifecycle: **not completed**. The repository mapping lacks the base URL
  and project UUID required by the lifecycle script; no lifecycle completion
  operation was attempted and no DevLog lifecycle state is claimed.

## Delivery

- Commit: pending at report creation.
- Pull request: pending at report creation; no repository PR template was found.
- Merge: not performed. Owner validation of the final PR is required first.
- No deployment or release is claimed.
