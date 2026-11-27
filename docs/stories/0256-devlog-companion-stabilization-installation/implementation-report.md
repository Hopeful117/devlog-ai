# Implementation Report — Story 0256

## Repository state

- Repository: `Hopeful117/devlog-ai`.
- Branch: dedicated Story 0256 branch (created under the follow-up owner
  authorization).
- Package source commit: `30ca3577302bfa0c41ec663a812dad5ed03b8d40` on
  `story/0256-devlog-companion-stabilization-installation`.
- The package source tree was clean when the release artifact was built.
- A documentation-only follow-up records the final artifact/install evidence;
  it does not change package source contents.
- Push: not authorized or performed.

## Governance and scope

- Story: 0256 — DevLog Companion Stabilization and Installation.
- Gate 1 Repository Analysis and Gate 2 Implementation Plan: owner-approved.
- Governing constraint: accepted ADR-084 and the approved Story 0256 plan.
- DevLog context: skipped under the task's explicit no-DevLog-operation
  constraint. No DevLog operation or model request was made.
- The authorized active configuration migration is complete and verified below.
  Claude files/settings, remote hosts, and existing development worktrees were
  not changed.
- This assessment did not launch or restart OpenCode, modify active
  configuration, or change the stable installed package/dependency tree.

## Implementation summary

The OpenCode package version is now `0.1.1`, with exact `@opencode/plugin`
runtime dependency and declared Solid/OpenTUI host peer ranges. The package
whitelist includes a shipped `npm-shrinkwrap.json`; a deterministic package
test verifies the lock metadata, independent GUI/TUI exports, required archive
files, and exclusion of the non-exported server entry.

The README now documents stable version+commit installation, dependency
staging, native Desktop/TUI entrypoints, same-host versus remote TUI
registration, backup/update/rollback, and no-credential/no-DevLog validation
guardrails. Compatibility and prior PR delivery records were reconciled without
changing their runtime-evidence or owner-acceptance claims.

## Files changed

- `opencode-mod/devlog-companion/package.json`: version, peer dependencies,
  shrinkwrap whitelist.
- `opencode-mod/devlog-companion/package-lock.json`: matching package version
  and peer metadata.
- `opencode-mod/devlog-companion/npm-shrinkwrap.json`: packageable lock graph.
- `opencode-mod/devlog-companion/tests/gui-package.test.js`: archive and
  manifest contract regression test.
- `opencode-mod/devlog-companion/README.md`: stable installation/update/
  rollback/topology guidance and Story 0256 evidence links.
- `docs/stories/0256-devlog-companion-stabilization-installation/`: Story,
  Repository Analysis, approved Implementation Plan, compatibility matrix,
  owner checklist, closure criteria, and this report.
- `docs/stories/0254-opencode-companion-local-context-inspector/implementation-report.md`:
  PR #253 marked merged; owner acceptance remains pending.
- `docs/stories/0255-opencode-companion-native-tui/engineering-report.md`,
  `implementation-report.md`, `story.md`, and `tui-checklist.md`: PR #254
  merge status reconciled; partial runtime evidence is unchanged.

No Claude source, installer, settings, cache, or installed files were changed.

## Validation

Commands and outcomes:

- `npm test` — **PASS**, 69 tests, 0 failures (68 existing + one package
  contract test).
- `npm run pack:dry-run` — **PASS**. GUI build passed; package tests passed
  (69/69); TypeScript typecheck passed; dry-run listed 11 intended files,
  including the lock and both entrypoints. No tarball was written.
- `npm install --package-lock-only --ignore-scripts --offline --no-audit --no-fund`
  — **PASS**, lockfile-only update; no dependency installation requested.
- `npm pack --ignore-scripts --pack-destination /tmp/opencode/story-0256-final-artifact`
  — **PASS**, created `devlog-companion-0.1.1.tgz`; SHA-256
  `64f9b58bca27085b25417061b1e9e76e022cd724de367c2f6a36629b70c23b43`;
  npm shasum `069805833b3a387b6e2ee0442888a0eb7c084a3a`.
- Tar inventory and manifest inspection — **PASS**, 11 files; version and
  shrinkwrap root both `0.1.1`; only GUI/TUI exports; no `src/server.js`.
- `npm ci --omit=dev --ignore-scripts --no-audit --no-fund` — **PASS** against
  the final extracted archive in isolated temporary staging
  `/tmp/opencode/story-0256-final-extracted/package`; installed 391 packages
  with lifecycle scripts disabled.
- `npm ls --omit=dev @opencode/plugin @opentui/solid solid-js` — **PASS** in
  final extracted staging; resolved `@opencode/plugin@2.4.0`,
  `@opentui/solid@0.9.4`, and `solid-js@1.9.17` from its installed dependency
  tree.
- ESM `import.meta.resolve` checks in extracted staging — **PASS** for
  `@opencode/plugin/gui`, `@opencode/plugin/tui`, `@opentui/solid`,
  `@opentui/solid/jsx-runtime`, and `solid-js/web`. Package self-reference
  resolves GUI to `dist/gui-experimental.cjs` and TUI to `src/tui.tsx`. Every
  resolved target was inside the extracted package directory, not the
  development worktree.
- Node CommonJS resolution of `@opencode/plugin/gui` — **NOT RESOLVED** because
  the SDK exposes an `import` condition but no `require` condition. The CJS GUI
  bundle externalizes this SDK import. Actual OpenCode Desktop loader behavior
  remains **NOT EVALUATED**; no host compatibility claim is made.
- `git diff --check` — **PASS**.
- Story 0256 relative-link and whitespace validation — **PASS**.
- Post-commit `npm run pack:dry-run` — **PASS** from clean package source
  commit `30ca3577302bfa0c41ec663a812dad5ed03b8d40`.
- Final release artifact — version `0.1.1`, 11 files, SHA-256
  `257b55a142c647f70f142e71a3c8610c3490f2f841a3e9d9ee9724e5c6caf974`,
  npm shasum `5daba09d33a77c03d0c41776995e5fb807acbde0`. Compared with the
  previously verified staging artifact: same 11-file set; only
  `package/README.md` differs due to final documentation updates.
- Stable installation — **PASS** at
  `/home/ludo/.local/share/opencode/plugins/devlog-companion/0.1.1-30ca3577302bfa0c41ec663a812dad5ed03b8d40`.
  The user-owned parent and installation directory are mode `0700`; all 11
  packaged files match the final archive byte-for-byte.
- Stable `npm ci --omit=dev --ignore-scripts` — **PASS**, 391 packages.
  `npm ls` resolves `@opencode/plugin@2.4.0`, `@opentui/solid@0.9.4`, and
  `solid-js@1.9.17`; GUI, TUI, SDK and JSX runtime module targets resolve under
  the stable installation, with no source-worktree paths in the shrinkwrap.
- `npm audit --omit=dev` — 26 moderate, 0 high/critical. No audit fix was
  applied. This is a dependency risk requiring owner review before activation;
  reachability/exploitability has not been established.

The first package regression run failed because npm's `files` whitelist omitted
`npm-shrinkwrap.json`. The whitelist was corrected; the subsequent full test
and package dry-run pass verifies that the lock is present in the archive.

## Independent review

- Engineering review: **PASS WITH FINDINGS**. The major finding requested
  extracted-archive installation and module resolution; the temporary install
  and checks above add that evidence. The compatibility matrix was clarified
  to distinguish the prior source/plugin smoke test from package installation.
- Security review: initial package review **PASS**, no actionable findings.
  This follow-up assessment classifies the installed audit findings below;
  residual-risk acceptance is still the owner's decision.

## Stable migration and provenance verification

- The stable version `0.1.1` remains installed at
  `/home/ludo/.local/share/opencode/plugins/devlog-companion/0.1.1-30ca3577302bfa0c41ec663a812dad5ed03b8d40`.
- Read-only verification confirmed exactly one stable path in each of
  `/home/ludo/.config/opencode/opencode.jsonc` and
  `/home/ludo/.config/opencode/cli.json`; neither old worktree path remains in
  those Companion entries. The explicit TUI entry is retained.
- The migration backup remains at
  `/home/ludo/.local/share/opencode/backups/story-0256-plugin-migration-20261127-233925`
  (directory mode `0700`; both config backups mode `0600`). Each backup still
  contains its corresponding pre-migration Companion path.
- The package manifest remains `devlog-companion@0.1.1`; its release archive
  SHA-256 remains
  `257b55a142c647f70f142e71a3c8610c3490f2f841a3e9d9ee9724e5c6caf974`.
  The pinned installation directory still matches the reviewed source commit;
  shrinkwrap contains no Story worktree paths. No dependency or package content
  was changed during this assessment.
- Prior Desktop logs show plugin discovery/watch setup and two `loading plugin`
  attempts at the stable GUI CJS entrypoint. No matching error was observed in
  the examined log window. This establishes **discovery and attempted loading**,
  not successful module initialization, visible panels, or functional behavior.
- The available TUI logs only show the earlier Story-worktree entrypoint.
  No stable TUI load is evidenced. TUI runtime loading remains **NOT
  EVALUATED**.
- Desktop visual behavior and the owner checklist below remain pending. Claude
  files/settings/installation were not changed; the previously documented
  installed/source version discrepancy remains unresolved and is not addressed
  by this Story.
- Remote-server and SSH topologies remain optional and **NOT EVALUATED**.

## Dependency security assessment

Evidence source: the existing `/tmp/opencode/story-0256-stable-audit.json`,
created by `npm audit --omit=dev` against the stable installation after its
locked install; the report records 378 production, 64 optional, and 8 peer
dependency entries. Read-only `npm explain` and installed package/shrinkwrap
inspection confirmed the paths and versions below. No audit fix or dependency
mutation was performed.

The report's **26 moderate package findings are not 26 independent advisories**.
They are transitive affected-package records that collapse to two distinct
GitHub advisories:

| Advisory and affected installed versions | Dependency path and usage assessment | Prerequisites, fixes, and classification |
|---|---|---|
| [GHSA-3g3m-2fjr-6pw4 / CVE-2026-118773](https://github.com/advisories/GHSA-3g3m-2fjr-6pw4), `glob` AbortSignal cancellation bypass. Installed: `glob@9.3.5` under `babel-plugin-module-resolver@5.0.2`; `glob@13.0.5` under `@opencode/util@2.4.0` / npm tooling; `glob@10.5.0` under `rimraf@5.0.10` → `gaxios@7.1.3` → `gcp-metadata@8.1.4`. | `@opentui/solid@0.9.4` → Babel module resolver is present for TUI TSX transformation. `@opencode/util` exports a glob scanner, but Companion source does not directly call it. No Companion-supplied attacker-controlled glob pattern or AbortSignal was found. Host/SDK behavior for all installed transitive modules is not fully established. | The advisory describes continued filesystem traversal after an aborted async glob; practical impact requires an aborted glob walk and a sufficiently large tree, causing availability/CPU/I/O cost. Advisory's patched version is `glob@14.0.2`; current locked consumers constrain older major ranges and `npm audit` reported no compatible fix for these copies. **Residual risk accepted by the owner for the current local 0.1.1 installation only; practical reachability remains partly uncharacterized.** |
| [GHSA-g3hv-75wg-m249 / CVE-2026-118764](https://github.com/advisories/GHSA-g3hv-75wg-m249), `minimatch` globparts amplification. Installed: `minimatch@8.0.7` under `glob@9.3.5`; `minimatch@10.2.5` under `glob@13.0.5` and `@opencode/util`; `minimatch@9.0.9` under `glob@10.5.0`. | `babel-plugin-module-resolver` imports `glob`; `@opencode/util/dist/glob.js` imports both glob and minimatch. Companion code uses static module paths and does not directly invoke these APIs. The module resolver can use glob patterns for configured roots; no attacker-controlled pattern source was identified in the Companion. Whether other host/SDK callers pass untrusted patterns was not established. | Exploitation requires an attacker-controlled glob pattern that triggers amplification and excessive CPU/memory. Patched version is `minimatch@11.0.1`; current consumers constrain `^8`, `^9`, and `^10`, and audit reported no compatible fix for those copies. **Residual risk accepted by the owner for the current local 0.1.1 installation only; practical reachability remains insufficiently evidenced.** |
| Audit-propagated packages, not additional root advisories: `@npmcli/arborist@9.4.0`, `@npmcli/config@10.8.1`, `@npmcli/map-workspaces@5.0.3`, `@npmcli/metavuln-calculator@9.0.3`, `@npmcli/package-json@7.0.5`, `@npmcli/run-script@10.0.4`, `@sigstore/sign@4.1.1`, `@sigstore/tuf@4.0.2`, `@tufjs/models@4.1.0`, `babel-plugin-module-resolver@5.0.2`, `cacache@20.0.4`, `gaxios@7.1.3`, `gcp-metadata@8.1.4`, `ignore-walk@8.0.0`, `make-fetch-happen@15.0.6`, `npm-packlist@10.0.4`, `npm-registry-fetch@19.1.1`, `pacote@21.5.1`, `rimraf@5.0.10`, `sigstore@4.1.1`, and `tuf-js@4.1.0`. | These are transitive under the SDK's `@opencode/util` npm/package-management graph and/or the OpenTUI Babel transform graph, and inherit glob/minimatch advisories. The Companion does not perform package installation, registry fetching, signature verification, or npm workspace scanning. The installed SDK still contains these modules; their use by other OpenCode host/plugin features is outside this package-only assessment. | **Do not count these as separate exploit paths/advisories.** Reachability in the OpenCode host is not fully known; inherited exposure remains part of the owner-accepted residual risk. |
| `@opencode/plugin@2.4.0` (direct runtime dependency) and `@opencode/util@2.4.0` (transitive); `@opentui/solid@0.9.4` (declared peer and development dependency in package metadata, present in the installed tree). | Audit marks the SDK and OpenTUI packages affected because their dependency graph reaches the above vulnerable packages. The Companion imports SDK GUI/TUI interfaces and the TUI JSX runtime; it does not directly import glob/minimatch or npm tooling APIs. | No audit-compatible SDK fix was offered for the pinned `@opencode/plugin@2.4.0` graph. For OpenTUI, audit reports `@opentui/solid@0.9.9` as a non-major compatible fix; it fits the declared `^0.9.4` range and SDK peer `>=0.9.4`. **Compatible remediation recommended for a separately authorized package revision**, followed by a fresh lock, package tests, install and runtime checks. Do not mutate this reviewed installation. |

### Overall classification and recommendation

- **Blocking release:** none established from this assessment. The two advisories are availability issues with pattern/cancellation preconditions; there is no evidence the Companion exposes an attacker-controlled pattern input. This is not proof of zero exposure.
- **Compatible remediation deferred:** the owner elected to retain current dependencies and defer updating the OpenTUI peer/dev pin from `0.9.4` to `0.9.9` to a separate maintenance revision with its own compatibility validation. Audit identifies this as a compatible route for the OpenTUI/Babel-resolver branch; it will not by itself remove the separately pinned OpenCode SDK/util branch.
- **Documented residual risk accepted:** the owner accepts the two documented advisory risks for the current local Companion 0.1.1 installation only. The advisories remain present in the dependency graph; acceptance is not a claim of vulnerability-free software or fully characterized runtime exposure.
- **Insufficient evidence:** whether any OpenCode host path supplies attacker-controlled patterns or exercises these vulnerable branches in this plugin's runtime; actual TUI and visible Desktop behavior.

### Owner residual-risk acceptance

On 2026-11-27, the owner accepted the documented residual risks from
GHSA-3g3m-2fjr-6pw4 and GHSA-g3hv-75wg-m249 for this current local
`devlog-companion@0.1.1` installation at
`/home/ludo/.local/share/opencode/plugins/devlog-companion/0.1.1-30ca3577302bfa0c41ec663a812dad5ed03b8d40`.
The decision retains the existing pinned dependencies and explicitly does not
assert the package is vulnerability-free or that runtime exposure has been
fully characterized. The `@opentui/solid@0.9.9` update is deferred to a
separate maintenance revision with independent compatibility validation; no
new Engineering Story is required solely for that maintenance work.

No dependency, shrinkwrap, installed package content, configuration, or artifact
identity was changed for this acceptance record. The existing source commit,
release archive, stable installation, and configuration backups are preserved.

## Rollback availability

The pre-migration config backups remain available at the path above. To roll
back configuration, restore only the previous Companion paths, or restore a
whole backup only if no unrelated configuration edits have occurred since it
was made. The prior temporary package and source worktrees were not deleted.
Do not delete the stable version directory or alter active config as part of
this assessment.

## Readiness

**SECURITY RISK ACCEPTED; FUNCTIONAL VALIDATION PENDING — NOT ACCEPTED.**
Perform the manual Desktop/TUI/Claude checklist below during ordinary
engineering use and record each item as PASS / FAIL / NOT EVALUATED. Final Story
acceptance remains the owner's decision. Deferred Context Inspector/Context
Trace capabilities, remote topologies, and other existing non-goals remain
deferred or unqualified.
