# Proposed Story 0256 — DevLog Companion Stabilization and Installation

## Status

**Gate 2 approved; implementation authorized within this Story's scope.**
Prepared from `main` at `7846fbd88b250ea54a54d586a7a25701d86a6bfa`. The owner
approved the Repository Analysis and Implementation Plan. A subsequent owner
authorization permits a dedicated Story branch, local implementation commit,
and stable package installation. Active configuration migration, OpenCode
activation/reload, Claude changes, cleanup, and push remain unauthorized.

## Objective

Make the existing Claude and OpenCode Companion integrations practical to
install and update reproducibly, and establish proportionate regression and
owner-led workflow evidence before closing the current OpenCode Companion epic.

This is a stabilization increment, not a new product capability or an
integration-infrastructure project.

## Proposed scope

1. Document one stable, pinned local OpenCode package installation using
   OpenCode's existing native plugin loading mechanisms. Cover Desktop, local
   TUI, explicit update and rollback, and the distinct local/remote TUI
   topologies. Preserve all unrelated configuration.
2. Reconcile the installed/source Claude Companion version and document its
   existing install/update/removal procedure without changing that installation.
3. Publish an evidence-qualified compatibility matrix for Claude, Desktop,
   local TUI, TUI hosted over SSH, and local TUI connected to a remote server.
4. Reuse the existing 68-test Companion baseline. Add deterministic tests only
   for demonstrated package, adapter-lifecycle, or install regressions.
5. Provide an owner-led normal-workflow checklist that does not require
   artificial DevLog operations.
6. Define practical epic closure criteria, separating required checks,
   optional topology validation, accepted limitations, and future work.
7. Reconcile delivery wording for PRs #253 and #254 in the specifically
   identified prior Story records. Correct merge status without inferring Story
   acceptance or adding runtime evidence.

## Constraints and non-goals

- No new user-facing Companion capability.
- No package registry, installer framework, daemon, automatic updater, runtime
  harness, sandbox, or container.
- No Claude installation change or broad Claude rewrite.
- No active OpenCode/Claude configuration or installation change without
  separate explicit authorization.
- No OpenCode launch/reload, DevLog operation, or model request as part of
  preparation or implementation validation unless separately authorized.
- No unsupported compatibility claim. Implementation delivery, owner
  acceptance, and runtime qualification remain separate evidence.
- Preserve the deferrals listed below.

## Acceptance criteria approved for implementation

1. The installation guide names the exact package source/revision, configuration
   file(s), topology, dependencies, update procedure, rollback procedure, and
   removal procedure. It distinguishes Desktop and TUI loading without replacing
   unrelated plugin entries.
2. Package/source and active-install versions are reconciled. The installation
   does not depend on temporary Story worktrees or floating versions.
3. Claude's installation mechanism is documented as-is; no Claude change is
   required for the OpenCode installation path.
4. The compatibility matrix distinguishes implementation, automated checks,
   independent runtime observations, owner reports, untested claims, and
   deferred capability; versions/topologies without evidence remain unknown.
5. The existing 68-test baseline remains passing. Any added test is tied to a
   concrete regression risk and does not trigger a provider or DevLog operation.
6. The owner-led checklist covers Desktop, TUI, Claude non-regression, and
   remote-topology prerequisites. Missing naturally occurring DevLog activity
   is marked `NOT EVALUATED`; no artificial operation is required.
7. Epic closure criteria separate mandatory reproducible installation/package
   checks from optional runtime topologies and accepted limitations.
8. The package exposes independent GUI/TUI exports and contains the complete
   pinned artifact/dependency contract; Claude files remain unchanged.
9. User configuration, existing installations, and remote hosts are not changed
   during preparation. Any approved install/update/rollback is scoped and
   reversible.
10. The merged status of PRs #253/#254 is accurately reflected in only the
    specified prior delivery records; Story acceptance and runtime qualification
    remain separately stated.

## Deferred capabilities preserved

- Code Mode nested Context Inspector.
- Backend Context Trace retrieval.
- Full Claude/Desktop/TUI feature parity.
- Shared Companion Core extraction.
- Stories 0249 and 0250.

These are not design questions reopened by this Story.

## Supporting proposal artifacts

- [Repository Analysis](repository-analysis.md)
- [Implementation Plan](implementation-plan.md)
- [Compatibility Matrix](compatibility-matrix.md)
- [Everyday Workflow Checklist](everyday-workflow-checklist.md)
- [Epic Closure Criteria](epic-closure-criteria.md)

The [Implementation Plan](implementation-plan.md) was approved at Gate 2.
