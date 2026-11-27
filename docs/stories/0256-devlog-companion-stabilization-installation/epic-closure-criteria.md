# OpenCode Companion Epic Closure Criteria — Story 0256

## Required for closure

1. One stable, pinned OpenCode package installation path is documented and no
   longer depends on temporary Story worktrees.
2. Install, explicit update, rollback, and removal instructions identify the
   affected configuration entry and preserve unrelated plugin settings.
3. Desktop and TUI exports remain independently discoverable/disableable as
   intended; package contents and runtime dependencies are checked from the
   distributed artifact, not only the source worktree.
4. The existing 68-test baseline and applicable build/typecheck/package checks
   pass. Any new deterministic checks address concrete regression risks.
5. Claude source/install version mismatch is reconciled or explicitly reported;
   its installation is not changed as a side effect of OpenCode work.
6. The compatibility matrix and everyday workflow checklist distinguish
   implementation, automated validation, independent runtime evidence,
   owner-reported behavior, and unknowns.
7. Story 0253/0254/0255 delivery documents accurately reflect actual PR merge
   status when the owner authorizes those documentation corrections. Story
   acceptance remains separately evidenced and is never inferred from merge.
8. The owner reviews the documented known limitations and accepts the required
   closure evidence.

## Optional runtime checks

- Desktop visual/runtime workflow on the actual Desktop/sidecar tuple.
- Full TUI command/panel/history/session-switch workflow on a pinned local
  topology.
- Local TUI + remote server, only when a suitable authenticated environment
  already exists.
- SSH-hosted TUI, only when a suitable host/environment already exists.

Skipped optional topologies must remain `NOT EVALUATED`; they do not block
closure unless the Story claims support for them.

## Known limitations that may remain explicit

- No claim for versions or topologies without evidence.
- Code Mode nested Context Inspector and backend Context Trace are deferred.
- No full Claude/Desktop/TUI feature parity.
- No shared Companion Core.
- Stories 0249 and 0250 remain deferred.
- Owner reports and isolated smoke tests are not represented as independent
  end-to-end runtime qualification.

## Future product/infrastructure work (not closure requirements)

- New Companion capabilities or full cross-platform parity.
- Shared runtime extraction.
- Installer framework, registry, daemon, or automatic updater.
- New remote observer, RPC, telemetry, backend trace retrieval, or permission
  model.
- Runtime harness/sandbox/container infrastructure.
