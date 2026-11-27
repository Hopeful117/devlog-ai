# Story 0253 — OpenCode Companion — Session Operation History

## Status

**Owner accepted on 2026-11-27 after implementation and focused Desktop
validation. Route-change testing was skipped; remaining runtime scenarios are
listed in the implementation report.**

The owner approved this Story and its scoped implementation direction on
2026-11-27. Authorization covers the GUI-only implementation and automated
validation described here. Changing the active Desktop plugin path, reloading
Desktop, and triggering a new real DevLog operation require separate owner
approval. Delivery was requested by the owner on 2026-11-27; branch creation,
commit/push, and PR creation were authorized and completed. PR #252 is open;
GitHub checks, PR review, and merge remain pending their approval gates.

Implementation evidence is recorded in
[implementation-report.md](implementation-report.md); use
[desktop-checklist.md](desktop-checklist.md) for the remaining owner-led runtime
validation.

Stories 0249 and 0250 remain suspended and are not resumed by this Story.
Story 0252's pending Desktop validation and human acceptance remain separate.

## Objective

Give the owner a bounded History view of recognizable DevLog operations already
present in the native transcript cache for the currently routed OpenCode
session. The view should help answer which DevLog operations are visible in this
session and what their available host/task evidence says, without implying
missing outcomes or creating a new source of truth.

## Governing context

- [ADR-084](../../decisions/ADR-084.md), including its accepted amendment.
- [Story 0252](../0252-devlog-opencode-live-presence/story.md), its minimal
  presence contract, implementation plan, implementation report, and Desktop
  checklist.
- The current OpenCode GUI Companion implementation and deterministic tests.
- The Claude Companion History/Now views as read-only UX reference only; no
  Claude behavior, files, or shared runtime are in scope.

## Approved scope

- Add a History view to the OpenCode Desktop Companion, derived only from the
  already-loaded native transcript for the currently routed `MountedSession`.
- Keep the projection bounded, sanitized, in-memory, and scoped to the current
  route. Reconcile it on transcript/session changes and discard it when the
  panel or route lifecycle requires.
- Show only allowlisted DevLog tool activity. Keep platform execution outcome
  separate from any explicitly decoded DevLog task outcome.
- Represent Code Mode only at the granularity supported by host metadata. Do
  not invent nested call IDs, chronology, per-call concurrency, or result
  correlation.
- Reuse the existing GUI package boundary and presentation patterns. Add
  deterministic tests, package/build validation, and an owner-led Desktop
  checklist for a plugin artifact already loaded in Desktop.

## Explicit non-goals

- Companion RPC, server observer, transcript fetch/sync, new MCP/REST calls,
  backend or contract changes, credentials, or external transmission.
- Persistence, raw transcript/prompt/argument/result/error retention, or
  cross-session history.
- Claude changes, shared-core extraction, TUI/remote support, or compatibility
  claims beyond the evidence available.
- Stories 0249/0250, Preflight Gate, runtime infrastructure, active Desktop
  configuration changes, installation, launch/reload, deployment, or user-data
  changes.
- Any change to Story 0252's `READY`/`DISCONNECTED` semantics or to its
  decoder solely to make History appear more complete.

## Acceptance criteria

1. History is derived only from the routed session's already-loaded native
   transcript and contains no operations from other sessions.
2. The list is capped independently of transcript size; rendering and
   projection work are bounded by explicit limits.
3. Each row identifies the recognized source/tool and presents only evidence
   available from that transcript. Unknown, missing, stale, or incomplete
   outcomes remain unknown/incomplete and never become success or failure.
4. Native IDs are used only where the host supplies them. Code Mode nested
   rows without IDs/status are grouped under the outer execution and do not
   acquire fabricated identity or correlated outcomes.
5. No prompt, raw arguments, raw result, credentials, or raw error payload is
   retained in Companion-owned state, logged, or displayed as History content.
6. Route changes, panel close/reopen, and plugin disposal cannot paint stale
   rows into a different route and clean up any added subscriptions.
7. No Companion RPC, observer, transcript fetch, MCP/REST call, persistence,
   external transmission, backend, Claude, Gate, or infrastructure changes are
   introduced.
8. Focused deterministic tests, GUI typecheck/build, and package dry-run pass.
   These checks do not certify Desktop runtime behavior.
9. Owner-led Desktop validation is recorded separately, uses only the
   already-installed artifact, and does not trigger a real DevLog operation
   without its separate authorization.

## Remaining approval gates

The scope and derived-field contract are approved for implementation by the
owner's authorization. Changing the active Desktop plugin path, reloading
Desktop, or triggering a new real DevLog operation remain separate
authorization gates. Human Story acceptance is separate from implementation
completion.
