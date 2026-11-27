# Repository Analysis — Proposed Story 0256

## Gate and provenance

- **Stage:** Repository Analysis and Implementation Plan approved by the owner;
  implementation authorized within the Story scope.
- **Story identifier:** 0256 is the next available identifier in the repository;
  the highest present Story is 0255 and no 0256 directory existed at baseline.
- **Repository:** `Hopeful117/devlog-ai`.
- **Baseline:** detached worktree at `7846fbd88b250ea54a54d586a7a25701d86a6bfa`;
  `origin/main` resolves to the same SHA; the initial tracked/untracked worktree
  was clean.
- **DevLog context:** deliberately not requested. The task prohibits DevLog
  operations, and the repository's Story Agent is a real DevLog context
  operation. This is `DEVLOG_CONTEXT_SKIPPED_BY_USER_CONSTRAINT`, not evidence
  that DevLog is unavailable. Repository and PR evidence below is authoritative
  for this analysis.
- **Vault:** selectively consulted read-only. `02 - Workflow/Engineering
  Workflow.md` informed the Story-as-contract and scope-discipline framing;
  `01 - Quality/DevLog AI CI-CD Quality Standard.md` informed the separation
  between technical checks and human validation. These are supporting
  transverse guidance, not repository-state authority.
- **External documentation:** Context7 library resolution was attempted but its
  monthly quota was exceeded. Current official OpenCode documentation was read
  directly on 2026-11-27:
  [Plugins](https://opencode.ai/docs/plugins/),
  [CLI plugins](https://opencode.ai/docs/cli/plugins/), and
  [Building TUI plugins](https://opencode.ai/docs/build/plugins/cli/).
  These document current loading options; they do not certify the repository's
  pinned runtime or any user's topology.

The owner approved a stable, version-identifiable, user-owned OpenCode package
installation direction for planning. The exact package/dependency and
configuration mechanism remains subject to this Implementation Plan and
subsequent validation.

## Repository and delivery reconciliation

The relevant artifacts are under `docs/stories/0253-*`, `0254-*`, and `0255-*`.
ADR-084 is the governing architecture. The Companion source is
`opencode-mod/devlog-companion/`; Claude remains in
`claude-mod/devlog-companion/`.

| PR | Actual GitHub state | Story/delivery correspondence | Documentation discrepancy |
|---|---|---|---|
| [#253](https://github.com/Hopeful117/devlog-ai/pull/253) | Merged to `main` on 2026-11-27; all four reported checks succeeded. | Story 0254 — Local Context Inspector. | `0254/implementation-report.md` still says PR open, merge pending, and owner acceptance pending. The PR body also says owner acceptance pending. Merge proves delivery, not human Story acceptance; no acceptance should be inferred. |
| [#254](https://github.com/Hopeful117/devlog-ai/pull/254) | Merged to `main` on 2026-11-27; all four reported checks succeeded. | Story 0255 — Native TUI Companion. | `0255/engineering-report.md` still says PR open/merge not performed; `implementation-report.md` still says commit/push were not performed and PR validation is pending; `story.md` and `tui-checklist.md` retain pre-merge wording. The Story separately records the owner's explicit acceptance of its partial runtime evidence; that acceptance is distinct from topology qualification. |

The reports are historical delivery records and were not edited during this
preparation. Their stale PR/merge statements are identified here for explicit
reconciliation, not silently corrected as incidental cleanup.

Story 0256 does not reopen 0253/0254 implementation acceptance or 0255's
accepted runtime limitation. It should distinguish delivery, owner acceptance,
and observed runtime evidence.

## Installation assessment

### Claude Companion

- Repository source: `claude-mod/devlog-companion/`; manifest version `0.2.0`.
- The existing `install.sh` copies `.claude-plugin`, `hooks`, and `types` into
  `~/.claude/plugins/marketplaces/devlog/plugins/devlog-companion`. It has no
  versioned target, backup, rollback, or removal procedure.
- Read-only local inventory found the Companion enabled in Claude settings and
  an installed-plugin record for cache version `0.1.0`; the configured
  marketplace source manifest is `0.2.0`. The cache path did not contain the
  source manifest at the checked location, so the actually loaded version is
  **not established**. This is a source/install-version discrepancy to resolve
  without changing Claude.
- The active Claude configuration was inspected without printing unrelated
  settings or credentials. No Claude change is needed for the proposed
  OpenCode stabilization.

### OpenCode Desktop

- Repository package is `opencode-mod/devlog-companion`, version `0.1.0`,
  `"private": true`; GUI entry is `./gui-experimental`.
- The active global `opencode.jsonc` Companion entry points to
  `/tmp/opencode/devlog-ai-story-0251/opencode-mod/devlog-companion`, a
  temporary Story worktree path. It currently exists, but is not a stable
  installation location.
- Current README directs Desktop users to a dedicated development profile.
  It does not describe a reproducible, versioned everyday install, explicit
  update, or rollback.

### OpenCode TUI

- The same package exports `./tui`. The active `cli.json` entry points to the
  Story 0255 worktree path. That directory still exists in this checkout but is
  not a stable installation location.
- The TUI path is distinct from Desktop's `opencode.jsonc` entry. Current
  official docs say a package exposing a TUI component in `opencode.json(c)` is
  loaded by the CLI from its connected server, so adding that same package to
  `cli.json` is unnecessary for the same-host case. `cli.json` is documented
  for CLI-only/local plugins and remains active when connected to a remote
  server.
- Since both active files currently reference different local worktrees, a
  duplicate/competing TUI registration is plausible. This was not tested: no
  OpenCode process was launched. Do not infer actual duplicate runtime behavior.
- The package has exact `@opencode/plugin@2.4.0`; `@opentui/solid@0.9.4` and
  `solid-js@1.9.17` are development dependencies. The TUI source imports both
  libraries. A non-local/package-manager install must verify runtime dependency
  resolution and package contents before it can be recommended as working.

### Recommended candidate approach

Use the existing native local-plugin path mechanism, not a new installer:

1. Stage one clean, pinned package artifact under a stable user-owned location
   (for example, a version/commit-specific directory under
   `~/.local/share/opencode/plugins/`), independent of Story worktrees.
2. Point Desktop's existing native plugin entry to that artifact. For the
   same-host TUI, rely on the TUI part advertised by the connected server rather
   than a duplicate `cli.json` entry.
3. For a local TUI connected to a remote server, use the local CLI plugin
   mechanism only after checking how the remote server advertises plugins and
   ensuring the Companion is not registered twice. For an SSH-hosted TUI, the
   package/config belong on the SSH host; no local machine can substitute for
   that environment.
4. Update explicitly by staging a new pinned artifact and changing only the
   Companion entry. Roll back by restoring the previous pinned path/revision.
   Preserve other configuration entries and keep the prior artifact until the
   new one is verified.

This is a candidate, not an installation instruction: package-manager/local
directory resolution, peer dependencies, GUI/TUI coexistence, and rollback
behavior need focused validation in the Implementation Plan. Do not select a
floating npm/Git range because official docs allow such ranges to update
automatically on startup. No install, update, config edit, launch, or rollback
was performed.

## Compatibility and implementation evidence

See [Compatibility Matrix](compatibility-matrix.md). Known evidence includes:

- OpenCode package baseline: 68 tests passed, typecheck/build/package dry-run
  passed at Story 0255.
- OpenCode GUI has deterministic Now/History/Context projection and packaging
  tests. Owner observations exist for select Desktop History/Now scenarios from
  0252/0253; no current independent Desktop qualification is established.
- TUI has three direct projection tests plus package/export assertions within
  the 68-test baseline. Local CLI/server 2.4.2 rendered the status slot; the
  automated probe did not independently demonstrate panel/history or session
  switching.
- Claude implementation exists and is enabled locally, but this repository has
  no dedicated Claude Companion test suite. Installation source/cache versions
  differ, and loaded runtime behavior is not established.

## Focused regression assessment

### Existing coverage worth preserving

- Presence evidence, host/task result distinction, Code Mode unknown states,
  conflicts, bounds, sanitization and event cleanup:
  `opencode-mod/devlog-companion/tests/gui-presence.test.js`.
- History recomputation, bounds, routing and subscription disposal:
  same suite.
- Context result eligibility, exact ID association, bounds, selection and
  route scope: `tests/context-inspector.test.js`.
- TUI projection semantics: `tests/tui-projection.test.js`.
- Export separation and no Companion transport in TUI:
  `tests/gui-package.test.js`.
- Story 0255 baseline: 68 tests, typecheck, build, `npm pack --dry-run`.

### Concrete residual gaps

1. No deterministic adapter-level TUI test proves session switch, panel close,
   plugin disposal and listener cleanup together. Current TUI coverage mainly
   covers projection semantics and static/package boundaries.
2. Existing package checks assert the two exports but do not demonstrate
   install/update/rollback from a stable package artifact independent of a
   Story worktree.
3. Claude has no matching deterministic regression suite or recorded
   reproducible baseline; do not create broad parity work for this Story.
4. No runtime evidence qualifies local TUI-to-remote-server or SSH-hosted TUI.

Only gaps 1 and 2 are likely required for this stabilization, subject to a
minimality check in the approved Implementation Plan. Gaps 3 and 4 can be
closed by a focused owner checklist and explicit `NOT EVALUATED` outcomes when
no suitable naturally occurring data/environment exists.

## Scope and risks

- Required: stable documented installation, package/export dependency check,
  pin/update/rollback steps, compatibility matrix, and narrow regression checks.
- Optional: owner runtime checks on Desktop/TUI and remote topologies when
  ordinary work and a suitable environment are already available.
- Risk: local package paths and versions are currently Story-worktree-specific.
- Risk: both OpenCode configuration sources currently contain package paths;
  duplicate TUI registration is an unverified possibility.
- Risk: installed Claude cache/source version discrepancy means non-regression
  cannot be claimed merely from unchanged source.
- Risk: remote topology behavior is documented as plausible but unqualified.

No material conflict with ADR-084's boundaries was found. No existing artifact
was edited outside this proposed Story directory.

## Candidate decomposition (not an approved Implementation Plan)

The likely narrow implementation work is:

1. Resolve the exact artifact/install mechanism and dependency/export behavior.
2. Document pinned installation, topology-specific loading, explicit update,
   rollback and removal; keep Claude's installer separate.
3. Add only regression tests needed for a proven lifecycle or package gap.
4. Add compatibility, owner workflow and epic exit records.

This is scoping input only. The formal Implementation Plan is deferred until the
owner approves this Repository Analysis at Gate 1.
