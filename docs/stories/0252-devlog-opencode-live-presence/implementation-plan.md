# Story 0252 — Implementation and Validation Plan

## Approval and blocker status

The owner approved the ADR-084 amendment and revised Story/Implementation Plan
and explicitly authorized production implementation on 2026-11-27, including
the trusted OpenCode GUI plugin model for this limited presentation feature.
Authorization is limited to the approved GUI code and automated checks. No
Desktop launch/reload, active configuration change, or real DevLog operation
is authorized.

## Minimal implementation plan

1. **GUI-only observer**
   - Extend the existing GUI entry to follow reactive `MountedSession`; scope
     the projection to its current `session.key` and server/location identity.
   - Read only the current session's already-loaded `server.data` transcript.
     Subscribe only to the needed native session/tool event types, filter by the
     current session before processing event content, and unsubscribe on route
     change, panel close, and plugin disposal.
   - Do not enumerate sessions or fetch more history. No Companion RPC,
     server observer dependency, MCP/REST call, credential, backend, shared
     runtime, or Claude change.

2. **Small in-memory projection**
   - Reuse the existing Story 0251 avatar/presentation components.
   - Recognize direct DevLog tool parts by the explicit allowlist. Use native
     tool-part IDs only for direct calls.
    - Recognize Code Mode DevLog activity only from explicit `toolCalls` rows on
      the outer `execute` part, including the structured terminal result. The
      pinned result rows expose names only; a recognized name proves an
      attempted nested call but not its state or business outcome, so retain
      `UNKNOWN`. Do not invent identity or correlate a nested result to a row.
      Use richer status only if the host explicitly exposes it.
   - Keep platform call result and explicit DevLog business/task status as
     separate facts. Never retain raw messages, prompts, inputs, outputs,
     credentials, or error payloads in Companion-owned storage/logs.

3. **Lifecycle**
   - On session or server/location change, dispose old subscriptions and clear
     all scoped activity before observing the new route.
   - Concurrent direct calls remain distinct by native call ID. Code Mode
     concurrency is represented only as aggregate recognized activity/error
     where the metadata supports it.
   - On panel reopen, derive from already-loaded current-session data; if the
     cache is unavailable or observation incomplete, use a neutral/unavailable
     presentation rather than inventing a terminal state.

4. **Focused validation**
   - Add deterministic fixtures for direct DevLog tools; Code Mode running,
     completed, error and missing metadata; unrelated tools; concurrency;
     tool-vs-task result distinction; route/server switch; panel reopen;
     interruption/missing terminal events; cleanup and privacy.
   - Run focused tests, `npm run typecheck`, `npm run build`, and the existing
     package dry-run (`npm pack --dry-run` or the repository's wrapper).
   - Do not change the active Desktop configuration. Manual visual validation
     uses the already-installed local plugin only; if the running Desktop does
     not already load the tested artifact, stop and request separate
     authorization rather than installing/reloading it.
   - A real DevLog operation is owner-performed or separately authorized.

## Files changed or expected to change

- `opencode-mod/devlog-companion/src/gui.tsx` and
  `src/gui-presence.js`: current-route GUI observation, bounded state
  derivation, and lifecycle.
- `opencode-mod/devlog-companion/src/presentation.js`: a neutral live
  `UNKNOWN` descriptor, without changing preview normalization.
- Focused GUI projection/presentation/package tests.
- `package.json`, `README.md`, and regenerated `dist/gui-experimental.cjs` for
  the GUI-only package allowlist, documented live semantics, and exported build.
- No `server.js`, `rpc.js`, backend, Claude, shared runtime, active config, or
  infrastructure changes.

No Claude, DevLog backend, Gate, Story 0249/0250, runtime harness, user profile,
or unrelated changes are planned.

## Validation plan

### Focused deterministic tests

- `IDLE`/`WORKING` and only evidence-qualified `READY`/`ERROR`; `DISCONNECTED`
  omitted absent reliable boundary-specific evidence.
- Direct recognized DevLog tool lifecycle; task status differs from host
  completion; unrelated tools are ignored.
- Code Mode metadata and name-only terminal result rows; unknown nested status
  remains `UNKNOWN`, and no nested result is correlated or promoted to success.
- Concurrent direct calls, route/server-location changes, panel reopen,
  missing/unloaded data, interruption, event ordering and disposal.
- No raw payload persistence/logging, unrelated-session enumeration, extra
  request, RPC, server observer, or package-boundary regression.

### Build and package checks

Run focused Node tests, then the Companion's existing typecheck, GUI build, and
package dry-run from `opencode-mod/devlog-companion/`. Inspect the final diff
and package contents.

### Owner-led Desktop validation

Use the short procedure in `desktop-checklist.md` against the already-installed
local plugin only after separate owner authorization for any required Desktop
action and real DevLog operation. Do not install, edit active configuration,
launch/reload Desktop, or claim runtime support as part of implementation.

## Stop conditions and acceptance boundary

- Implementation is authorized within the approved scope. Stop if the work
  needs any separately excluded capability or broader access.
- Never describe route filtering as server authorization or isolation against
  another trusted GUI plugin.
- Stop if the implementation needs broader data access, persistence, external
  transmission, RPC/server observation, backend/auth changes, Claude/shared
  runtime changes, or additional DevLog requests.
- Preserve the explicit Claude qualification limitation; do not claim full
  cross-platform compatibility until its required runtime baseline is complete.
- A passing test suite does not equal Desktop validation or Story acceptance.
- Human acceptance and any later release/deployment are separate gates.
