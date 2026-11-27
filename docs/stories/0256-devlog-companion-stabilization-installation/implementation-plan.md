# Implementation Plan — Story 0256

## Approval status and authority

**Gate 2 approved by the owner; implementation authorized within the limits
below.**

- Gate 1 Repository Analysis was approved by the owner.
- The owner approved the stable, version-identifiable, user-owned OpenCode
  installation direction and this plan.
- A subsequent owner instruction explicitly authorizes a dedicated Story
  branch, local commit, artifact build, and stable installation.
- Active configuration migration, activation, OpenCode launch/reload, Claude
  changes, cleanup, and push remain unauthorized.
- The initial implementation preparation did not install a package or alter
  active configuration. Stable package installation is now explicitly
  authorized; active configuration migration and activation still require
  separate owner approval.
- No DevLog operation or model request was made.

## Objective

Ship a complete, pinned OpenCode Companion package to a stable user-owned
versioned directory and load its existing native GUI/TUI entrypoints through
OpenCode's local plugin path mechanisms. Document controlled migration, update,
rollback, and topology limits; preserve the existing Claude installation.

No new product capability, registry, custom installer framework, service,
automatic updater, or runtime harness is in scope.

## Recommended installation layout and mechanism

### Staged package location

Use a user-owned, immutable per-version artifact directory, for example:

```text
${XDG_DATA_HOME:-~/.local/share}/opencode/plugins/devlog-companion/
  <package-version>-<full-source-commit-sha>/
```

The directory name plus `package.json` version and source commit identifies the
installed artifact. Do not use a `current` symlink or overwrite an installed
version in place: OpenCode's configured path should resolve directly to the
exact artifact so rollback is a path change to the retained previous version.

Generate the artifact from a clean, reviewed commit using the existing npm
package mechanism (`npm pack`), then stage it outside all source repositories and
Story worktrees. OpenCode's documented local plugin path accepts a package
directory. This preserves the native loading mechanism without requiring npm
publication or a custom installer.

### Artifact contents and dependency resolution

The current package dry-run confirmed ten package files:

- `package.json` and README;
- `dist/gui-experimental.cjs`;
- `src/tui.tsx`;
- `src/gui.tsx`, shared projection/decoder helpers, and SVG asset.

It does **not** include `package-lock.json`, `node_modules`, or bundled
dependencies. Therefore a GUI bundle copy alone is incomplete, and the current
archive is not yet a reproducible installed dependency tree.

Planned package adjustments, limited to what staging verification confirms:

1. Keep `@opencode/plugin` pinned at the tested `2.4.0`.
2. Declare directly imported runtime host packages (`solid-js` and
   `@opentui/solid`) as explicit peer dependencies using ranges compatible with
   the supported OpenCode host, while retaining exact dev pins for tests and
   typechecking. Do not bundle a second OpenTUI renderer/runtime into the
   Companion.
3. Add `npm-shrinkwrap.json` generated from the reviewed lockfile so the lock
   graph is carried in the package archive. npm documents that
   `package-lock.json` is intentionally omitted from package archives, whereas
   `npm-shrinkwrap.json` is included and controls the installed tree.
4. Bump the private package's patch version to `0.1.1` if the package manifest
   or shipped dependency contract changes. The full source commit remains part
   of the install directory name, so version alone is never treated as artifact
   identity.
5. Keep the two public export paths distinct: `./gui-experimental` for Desktop
   and `./tui` for TUI. Verify that the archive includes all source/helper files
   those entries import and that no server/RPC entry is accidentally exposed.

Stage the extracted archive in a new, empty versioned directory and resolve its
locked runtime dependencies there (proposed command: `npm ci --omit=dev
--ignore-scripts`). Verify the exact npm/peer-dependency behavior before using
this command. Confirm the GUI bundle's `solid-js/web` import and the TUI
`@opentui/solid` JSX runtime resolve from the staged package/compatible host.
Do not silently rely on the development worktree's `node_modules`.

The two local plugin references must point to the **same staged artifact**.
OpenCode's current docs specify:

- a package configured in `opencode.json(c)` may expose GUI and TUI parts; the
  CLI loads its TUI component from the connected server's plugin list;
- `cli.json` is for CLI-local plugins that remain active when a remote server is
  used.

Use the existing `opencode.jsonc` and `cli.json` registrations, pointing both
to the same immutable artifact in the preparation migration proposal. Although
the docs distinguish server-supplied TUI parts from client-local `cli.json`
plugins, this repository has not verified duplicate-loading behavior or
established the active topology. Preserve the explicit TUI registration until
owner-authorized runtime evidence for the actual topology demonstrates a
duplicate contribution. Do not deduplicate based only on documentation or an
assumed same-host setup. If topology/loading cannot be established without
activation, leave it `NOT EVALUATED` and do not claim it is supported.

This topology-specific rule is required to keep both entrypoints on one package
version without implying that two config files should always load the same
plugin simultaneously.

## Configuration migration procedure

The current inventory is recorded in
[Repository Analysis](repository-analysis.md): the Desktop global config and
TUI `cli.json` point to different Story worktrees. The following is a future,
separately authorized owner operation; it was not performed during preparation.

1. Inventory the exact Desktop and TUI plugin entries, whether each path exists,
   package version/source revision where readable, and the target topology.
   Print only the Companion paths and version metadata; do not print credentials
   or unrelated configuration.
2. Check whether either entry is also loaded from a project-level configuration
   or connected server. Record uncertainty instead of guessing.
3. Create owner-only backups of only the config files that will change,
   preserving permissions. Do not display or copy their contents into reports.
4. Prepare and validate the versioned artifact and dependencies before editing
   active configuration. Keep both existing worktrees and installed artifacts.
5. Verify package exports, installed module resolution, stable path readability,
   and the expected configuration diff using disposable config copies. Confirm
   unrelated plugin entries are byte-for-byte or semantically unchanged.
6. After separate owner authorization, update both existing Companion
   registrations to the one staged version. Preserve the explicit TUI
   registration and all unrelated settings/order; do not deduplicate unless
   runtime evidence from the active topology establishes duplicate loading and
   the owner separately authorizes that correction.
7. Do not launch or reload OpenCode until the owner separately authorizes
   activation. Once authorized, use the existing owner-led workflow checklist
   and ordinary/pre-existing activity only. Do not trigger a DevLog operation
   just to validate.
8. Keep the old package paths and worktrees. Remove no previous installation
   until the replacement is validated and the owner separately authorizes
   cleanup.

If package staging or either config diff fails, do not activate. Restore only
the file(s) changed by the authorized migration from their owner-only backups.

## Update and rollback

### Explicit update

1. Select an exact reviewed source commit and package version.
2. Re-run the required package/test checks.
3. Build and inspect a new archive; verify its source SHA and file list.
4. Stage dependencies into a new version+commit directory. Never overwrite the
   old installation.
5. Verify exports and installed dependency resolution before config changes.
6. Back up the current config files, then update only the Companion path(s) to
   the new version. No ranges, floating references, startup update, or
   background update are used.
7. Obtain separate activation authorization, run the owner-led checklist, and
   retain the previous artifact until validation and explicit cleanup approval.

### Rollback

1. Stop before deleting anything. Select the exact prior version directory
   recorded in the previous config backup.
2. Restore only the Companion path(s) to that directory (or restore the
   owner-only config backups if no intervening unrelated config changes occurred).
3. Compare the resulting configuration with the pre-migration snapshot while
   confirming unrelated entries remain intact.
4. Reload/launch only after separate owner authorization.
5. Keep both version directories and all source worktrees until the owner
   authorizes cleanup.

## Claude Companion compatibility

- Do not modify `claude-mod/devlog-companion`, Claude settings, marketplace, or
  cache as part of this Story.
- Document the repository source manifest `0.2.0` and local installed-plugin
  record `0.1.0` as separate facts.
- The inspected cache version directory lacked the source manifest, so the
  actual loaded Claude runtime version remains unknown.
- Do not auto-update, reinstall, or infer a regression from this metadata
  difference. Carry it as an explicit limitation unless a concrete blocker is
  established.

## Focused implementation and validation plan

### Required package checks

From `opencode-mod/devlog-companion/`:

1. Clean lockfile install using the repository's existing dependency workflow.
2. Full `npm test`: preserve the 68-test baseline and run the justified new
   packaging regression guard (69 tests total after this Story's addition).
3. `npm run typecheck`.
4. Production `npm run build`; verify expected GUI bundle output.
5. `npm pack --dry-run`; assert both named exports and all imported runtime
   files are present, the shrinkwrap is shipped, and no unwanted files are
   included.
6. Install the archive into an empty **staging** directory using only the
   reviewed lock/dependency mechanism; verify `npm ls --omit=dev` and resolve
   the runtime modules from the staged path. Staging is not activation and must
   not reference a repository/worktree at runtime.
7. Validate the TUI TSX entry under the exact pinned compatible CLI/plugin SDK
   contract. The SDK source declares plugin parts; actual host loading remains
   runtime evidence, not proven by typecheck.
8. Run `git diff --check`.

### Focused deterministic regression checks

- Preserve all 68 tests.
- Extend `gui-package.test.js` to assert the packed archive contract and
  independent GUI/TUI entrypoints, if the existing test can exercise this
  without creating a reusable test framework.
- Add a narrow TUI adapter lifecycle test for session-scope change and disposal
  only if a minimal test double can drive the existing adapter without a new
  production abstraction or runtime harness. If not, retain existing projection
  tests and record adapter lifecycle as an owner-led runtime item.
- Do not add Claude tests or refactors absent a demonstrated regression in
  Claude; no source or active Claude installation changes are planned.

### Configuration migration and rollback checks

- On disposable copies of the config files, verify that only the intended
  Companion entries change and all unrelated entries survive.
- Exercise forward migration and rollback on those copies and compare the
  resulting files; do not use active configuration for this deterministic
  check.
- Verify the entrypoint loading behavior in the actual topology before any
  deduplication. Until then preserve both registrations, and leave same-host
  duplicate behavior and remote-server loading `NOT EVALUATED`.
- Actual activation, UI validation, and cleanup are separately authorized owner
  actions, not automated tests.

### Existing owner checklist

Reuse [Everyday Workflow Checklist](everyday-workflow-checklist.md). Desktop,
TUI, and Claude checks use normal work and already-present activity. No
artificial DevLog operation is required. Mark unavailable evidence
`NOT EVALUATED`. Remote/SSH checks run only in an already available suitable
environment and remain optional for epic closure.

## Documentation changes in scope

1. Update `opencode-mod/devlog-companion/README.md` as the canonical OpenCode
   installation/update/rollback/removal guide: package source and pinned
   identity, versioned location, required dependencies, Desktop activation, TUI
   activation, topology-specific `cli.json` behavior, and no-credential/
   no-DevLog-operation constraints.
2. Add an evidence-qualified compatibility table to the Companion docs or link
   the Story 0256 matrix from the README; no unverified claim becomes a
   permanent support statement.
3. Keep Claude's installer and settings unchanged. Record source/install
   metadata discrepancy in Story 0256's compatibility/installation record.
4. Reconcile only the stale delivery wording covered by this Story:
   - `docs/stories/0254-opencode-companion-local-context-inspector/implementation-report.md`
     — PR #253 is merged; retain owner acceptance as pending unless separately
     evidenced.
   - `docs/stories/0255-opencode-companion-native-tui/engineering-report.md`,
     `implementation-report.md`, `story.md`, and `tui-checklist.md` — PR #254
     is merged; retain the owner's recorded acceptance of partial runtime
     evidence and do not upgrade that evidence.
5. Leave other historical reports, including PR #252 wording, untouched unless
   separately approved.
6. Preserve the approved deferrals: Code Mode nested Context Inspector,
   backend Context Trace, full platform parity, Shared Core, Stories 0249/0250.

## Proposed epic closure decision

Close the current Companion epic when:

- the stable artifact can be installed from a pinned source into the versioned
  user-owned path without a worktree dependency;
- package contents, dependencies, separate GUI/TUI loading, and explicit
  rollback checks pass;
- the 68-test baseline plus focused justified checks, typecheck, GUI build,
  package dry-run, and diff check pass;
- the owner completes required everyday checks or records exact unavailable
  observations as `NOT EVALUATED`;
- the specified PR delivery records are reconciled without inferring acceptance;
- the owner reviews and accepts remaining compatibility limitations.

Exhaustive Desktop/server/SSH topology qualification is not a closure
prerequisite. No untested topology is claimed. See
[Epic Closure Criteria](epic-closure-criteria.md).

## Remaining risks, blockers, and owner decisions

### Risks/blockers to resolve under this plan

- At the preparation baseline, the package archive omitted a shipped lock and
  installed dependencies. The source manifest now declares host peers and the
  shrinkwrap is included. A temporary extracted-archive install resolved the
  locked dependencies; actual OpenCode host loading remains untested.
- The Solid/OpenTUI peers now have declared compatibility ranges. Confirm
  resolution against the actual pinned OpenCode host before package activation.
- Two current config sources reference different worktrees. Same-host
  de-duplication and remote-client loading must be made explicit; no OpenCode
  runtime was launched in preparation.
- Desktop and remote topologies have no exact current version qualification.
- The Claude source/cache version discrepancy leaves actual loaded version
  unknown.

### Additional owner approval required

1. Use the reviewed Story 0256 commit SHA as the version-specific directory
   identity; the owner has explicitly authorized creating that local commit.
2. Separate authorization before modifying active Desktop/TUI configuration.
3. Separate authorization before launching/reloading OpenCode for owner-led
   validation.
4. Separate explicit authorization before deleting old plugin paths, package
   versions, config backups, or any worktree.
5. A topology-specific decision only if loader evidence shows both OpenCode
   config sources cannot safely coexist under one global setup. Until then,
   remote TUI remains optional/unqualified.

No owner decision is required to change or reinstall Claude; those actions are
explicitly excluded.

## Implementation outcome

The package manifest is now `0.1.1`; the lock/shrinkwrap root metadata matches
it, the archive whitelist includes `npm-shrinkwrap.json`, and the package test
asserts both entrypoints, dependency contracts, lock metadata, and archive
contents. The OpenCode README and evidence records were updated as scoped.
See [Implementation Report](implementation-report.md) for changed files and
validation.

The pre-commit 0.1.1 archive and dependency tree were validated in temporary
staging. Under the follow-up owner authorization, a clean reviewed commit will
identify the release source and stable install directory. The final report will
record the post-commit artifact/install verification. Active config migration,
activation, OpenCode host loading, owner runtime checks, and cleanup remain
outstanding.

## Documentation references consulted

- [OpenCode plugins](https://opencode.ai/docs/plugins/) — local plugin paths,
  package parts, Desktop loading, and explicit plugin management.
- [OpenCode CLI plugins](https://opencode.ai/docs/cli/plugins/) — server-supplied
  TUI parts versus local CLI-only `cli.json` entries.
- [Building TUI plugins](https://opencode.ai/docs/build/plugins/cli/) — `./tui`
  entrypoint, TSX runtime, plugin lifecycle, and OpenTUI peer dependencies.
- [npm package-lock documentation](https://docs.npmjs.com/cli/v11/configuring-npm/package-lock-json)
  — lockfile inclusion/exclusion behavior and `npm-shrinkwrap.json`.
- [npm ci](https://docs.npmjs.com/cli/v11/commands/npm-ci) — clean locked
  dependency installation semantics.
- [npm package.json](https://docs.npmjs.com/cli/v11/configuring-npm/package-json)
  — package `files`, exports, peer dependencies, and private-package behavior.
