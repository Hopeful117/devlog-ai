# Story 0254 — Implementation Plan and Finalization Status

## Status

**GUI-only implementation and finalization authorized by the owner on
2026-11-27.** The owner approved direct MCP result inspection as a supported
secondary capability and deferred Code Mode nested-result inspection until
reliable, proportionate native data access is available. Desktop
launch/reload, active configuration changes, additional DevLog operations,
and Git integration remain unauthorized.

## Approved implementation sequence

1. **Confirm baseline and files**
   - Recheck branch/base, worktree, Story approval, and the exact direct DTO
     fixture plus the observed Code Mode name-only host shape.
   - Keep changes within `opencode-mod/devlog-companion`; do not modify
     backend, MCP server, contracts, Claude, Gate, or infrastructure.
2. **Pure strict decoder/projection**
   - Add a focused GUI-local module or narrowly extend the existing projection
     module.
   - Decode only the exact direct completed `get_engineering_context` result
     shape in `local-context-projection-contract.md`.
   - Keep Code Mode History grouped at the outer execution; do not claim an
     individually inspectable nested result from name-only metadata.
   - Enforce parser/input/collection bounds before retaining only fixed enums,
     counts, revisions, allowlisted reason codes, and native scoped identity.
3. **Selection and lifecycle**
   - Derive eligible projections from the current bounded transcript.
   - Permit selection only for a unique native host call ID.
   - List eligible direct operations inside Context using minimal labels and
     keep the picker synchronized with the existing History action.
   - Explain the empty-picker state and explicitly state that no backend request
     is made to populate it.
   - Reconcile selected result changes and clear selection/projection on route,
     server/location, session, transcript eviction, panel close/unmount, and
     plugin disposal. Preserve existing Now and History reducers.
4. **Context view**
   - Add Context to the existing view switch, retaining Now as default.
   - Permit selection only for eligible direct operations surfaced in the
     loaded transcript. Keep the History action synchronized; a Code Mode
     name-only row remains non-selectable and displays a fixed explanation
     when it names `get_engineering_context`.
    - Render Overview, evidence counts by trust tier, section evidence counts,
      and Diagnostics only for fields that have meaningful verified values.
      Do not add a dedicated Sources section; source fields are intentionally
      omitted by the projection contract. Always show the observed-result
      versus backend-Context-Trace distinction.
   - Include accessible selection/status semantics and neutral partial/
     unavailable states.
5. **Focused automated verification**
   - Implement the deterministic cases in `validation-plan.md`.
   - Run package tests, typecheck, GUI build, package dry-run, and
     `git diff --check`; inspect the package listing and diff.
6. **Owner-led Desktop acceptance**
    - Owner-led Desktop validation preserves the actual Code Mode route as the
      primary workflow, but does not claim nested context inspection. Verify
      that the host result remains separate from nested outcome and no
      fabricated Context projection is offered.
    - Direct-result projection checks remain `NOT EVALUATED` if no eligible
      direct result already exists; do not create another DevLog operation.
    - Do not launch/reload Desktop, change active configuration, create another
      DevLog operation, or change Code Mode configuration for this revision.

## Finalization status

- GUI implementation and Context-local picker: implemented.
- Direct MCP result inspection: supported as a secondary capability.
- Code Mode: the owner's primary workflow; individual nested Context inspection
  is deferred because the examined native GUI interfaces do not expose the
  exact nested result with reliable identity and provenance.
- Projection: evidence counts by trust tier and section evidence counts;
  no dedicated Sources section. This matches the implemented contract and is
  not an outstanding implementation gap.
- Automated validation and independent review: recorded in
  [implementation-report.md](implementation-report.md).
- Owner-led Desktop validation: pending; see
  [desktop-checklist.md](desktop-checklist.md). This work does not launch or
  reload Desktop.
- Owner acceptance: pending and separate from implementation readiness.

## Expected file boundary (proposal)

- `opencode-mod/devlog-companion/src/gui.tsx`
- `opencode-mod/devlog-companion/src/gui-presence.js` or one narrowly scoped
  local projection module
- Focused deterministic tests under
  `opencode-mod/devlog-companion/tests/`
- `opencode-mod/devlog-companion/package.json` only if required to include a
  new GUI source in the established package file list
- `opencode-mod/devlog-companion/README.md` only for necessary user-facing
  behavior documentation
- Existing generated GUI bundle only if the established build/package process
  requires it

No backend Context Trace, task correlation, authentication, credential,
REST/MCP, Claude, Gate, shared-core, TUI, remote, or infrastructure work.

## Stop conditions

- The native direct-result shape differs from the confirmed DTO representation
  or cannot be distinguished from arbitrary text/JSON without widening scope.
- An exact unique native result identity is unavailable.
- Useful presentation requires displaying raw prompt, arguments, result,
  evidence bodies, arbitrary messages, or identifiers outside the contract.
- Exact nested Code Mode identity/result evidence is unavailable and the
  implementation would need to infer correlation from the outer execution.
- Any extra transport, authorization, backend work, active Desktop change, or
  real DevLog operation is needed.
- Existing Now/History behavior would need an unapproved semantic change.

## Deferred

Backend Context Trace retrieval is deferred and is not a hidden implementation
step. It requires a separate design and approval for authorized access and
exact invocation correlation.

Code Mode context inspection is deferred by owner decision until a reliable,
proportionate native interface provides the exact nested result, identity, and
provenance. The outer `execute` result and assistant-generated text are not
accepted as substitutes. Direct MCP inspection remains supported as a
secondary capability. Full Claude Context Inspector parity is not achieved.
