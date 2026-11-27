# Story 0252 — Rollback Procedure (Proposed)

**Preparation only.** No rollback or profile action is authorized now. The
implementation must preserve a known-good static 0251 GUI fallback and must not
require a DevLog backend migration or user-profile mutation.

## Trigger conditions

Rollback or disable the live contribution if any of the following occurs:

- routed-session state leaks into another route/window or stale state survives
  a session/server-location change;
- the UI reads/enumerates unrelated sessions or stores raw transcript/tool
  payloads;
- presence claims success, failure, or disconnection without required evidence;
- live integration affects normal OpenCode tool execution, the Preflight Gate,
  DevLog MCP policy, or another Companion platform;
- Desktop crashes, hangs, or leaks listeners/state across route/panel/plugin
  lifecycle;
- pinned runtime behavior differs materially from the approved contract.

## Containment

1. Stop live testing and preserve sanitized diagnostic evidence; do not retry
   actions that could expose more data.
2. If live activation or profile changes were separately authorized, have the
   owner disable/remove only the Story 0252 Companion contribution using the
   documented OpenCode UI/configuration procedure. Do not edit profile files
   without separate authorization.
3. Through the owner's normal approved plugin lifecycle, unload the GUI
   contribution only if separately authorized. Verify cleanup removes native
   subscriptions and all route-scoped in-memory presence state. No RPC/server
   observer exists in the approved design.
4. Restore the known-good Story 0251 static GUI artifact from its recorded
   revision/build provenance, or use the owner's normal plugin-disable path.
   Do not copy artifacts from the separate dirty 0248 worktree.
5. Keep the Companion unavailable/static if native evidence is unreliable. Do
   not add a snapshot endpoint, broad event broadcast, broader data reads, or
   treat route filtering as authorization.

## Verification after rollback

- [ ] No Story 0252 RPC endpoint or server observer hooks were introduced.
- [ ] Native GUI subscriptions are removed; normal DevLog tools and the Gate
      behave as before.
- [ ] No live session projection remains in memory or persistent storage.
- [ ] Desktop shows the known-good static/unavailable fallback or the plugin is
      disabled, with no false live status.
- [ ] Claude Companion, Stories 0249/0250, user configuration, and unrelated
      DevLog services remain unchanged.
- [ ] Record exact revisions, actions, observations, and remaining risk for
      owner review.

## Code and data policy

The implementation plan must identify a reversible GUI-only change boundary
and avoid irreversible migrations. Prefer reverting only the Story 0252 change
set after confirming its exact file list; never reset/discard unrelated work.
Do not delete evidence needed to investigate an exposure. If any data was
persisted contrary to the approved in-memory contract, stop and obtain owner
direction before cleanup.
