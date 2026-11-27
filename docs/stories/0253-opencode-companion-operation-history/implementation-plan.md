# Story 0253 — Proposed Implementation and Validation Plan

## Approval and authorization status

**Implemented; automated validation passed; manual Desktop validation pending.**
This is the owner-authorized plan (2026-11-27). Implementation is
authorized within the approved GUI-only scope. Changing the active Desktop
plugin path, reloading Desktop, and any new real DevLog operation remain
separate gates.

## Proposed implementation sequence

1. **GUI-local projection**
   - Reuse the existing allowlist and decoder in `src/gui-presence.js` or add a
     narrowly scoped pure projection beside it.
   - Derive bounded sanitized rows from the current session's already-loaded
     transcript only. Preserve platform outcome and task outcome as separate
     facts.
   - Keep Code Mode rows under the outer execution when identity/correlation is
     unavailable.
2. **Desktop presentation**
   - Add a History view in `src/gui.tsx`, reusing established panel, route,
     accessible status, and styling patterns.
   - Distinguish unavailable/loading from an available empty view; show
     incomplete evidence without success/error color implication.
3. **Lifecycle and privacy**
   - Confirm route/session/server changes reconcile rows and added listeners
     clean up on close/disposal.
   - Ensure no raw transcript or payload is retained, logged, persisted, or
     transmitted by the Companion.
4. **Focused validation**
   - Add fixtures for direct calls, explicit task outcomes, unknown/missing
     outcomes, Code Mode name-only rows, unrelated tools, limits, ordering,
     route changes, empty/loading data, cleanup, and privacy.
   - Run Companion unit tests, typecheck, GUI build, package dry-run, and
     `git diff --check`; inspect the bundle/package boundary and final diff.
5. **Owner-led Desktop validation**
   - Use only the already-installed artifact after separate authorization for
     any required Desktop action. Do not install, edit active config, launch, or
     reload Desktop under this Story unless separately authorized.
   - No real DevLog operation is needed for a transcript-derived view. If one
     is proposed for validation, obtain separate authorization first.

## Expected file boundary (proposal)

- `opencode-mod/devlog-companion/src/gui.tsx`
- `opencode-mod/devlog-companion/src/gui-presence.js` or one narrowly scoped
  projection module, subject to the approved design
- focused GUI tests and package tests
- `opencode-mod/devlog-companion/README.md` if user-facing behavior changes
- regenerated `dist/gui-experimental.cjs` only if required by the package's
  established build workflow
- Story implementation report after authorized implementation

No Claude, shared runtime, server/RPC, backend, MCP/REST, Gate, infrastructure,
active Desktop configuration, or user data changes.

## Stop conditions

- Proposed behavior depends on fetching transcript, extra DevLog calls, server
  authorization/observer work, persistence, or cross-session access.
- Native metadata cannot establish the requested identity, chronology, status,
  or correlation.
- Implementation would reopen Story 0252's presence semantics or alter its
  decoder without a separately evidenced defect.
- Validation would require an unauthorized Desktop action or real DevLog call.
