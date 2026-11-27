# Story 0251 — DevLog Companion — Dynamic Presence in OpenCode Desktop

## Status

**Repository Analysis and Implementation Plan approved by the owner on
2026-11-27. Implementation changes are complete; the owner confirmed visual
validation on 2026-11-27. Complete Story acceptance and delivery remain pending.**

The owner has confirmed that the first static Companion milestone loads in
OpenCode Desktop and appears in the side panel. This Story proposes the next
increment: make that panel visually expressive using simulated presentation
states. The simulation is for preview and presentation only; it is not evidence
of MCP, DevLog task, or connectivity activity.

Stories 0249 and 0250 remain suspended with their existing evidence and
limitations. This Story does not resume them.

## Identifier and provenance

- The current worktree contains Stories through 0250, including the preserved
  untracked Story 0250 artifacts.
- Story 0249 is separately known from Story 0250's provenance as prepared in
  `/tmp/devlog-ai-story-0249`; that worktree and its evidence are not modified.
- Story 0251 was absent from the current repository catalog and DevLog lookup
  when this draft was prepared. It is the next available identifier observed.
- Owner-confirmed baseline: on 2026-11-27 the owner reported that the plugin
  works and DevLog appears in the Desktop side panel. The approved manual
  installation and configuration backup are recorded in Story 0248's
  implementation report. The owner's confirmation is not a screenshot or
  evidence of live integration.

## Problem

The current OpenCode Desktop panel is a static GUI-only contribution. It shows
the DevLog identity and `IDLE`, but does not animate, expose the other presence
states, or provide a development preview. The Claude Companion already contains
a recognizable nucleus-and-satellites avatar and restrained state-specific
visual treatments that can inform an OpenCode-native presentation.

The existing OpenCode projection model describes observed operations and
intentionally includes uncertainty. Reusing it as a source of simulated
presentation state would conflate display preview with runtime evidence.

## Objective

Add a small OpenCode-native presentation layer for `IDLE`, `WORKING`, `READY`,
`ERROR`, and `DISCONNECTED`. Preserve the DevLog identity, make the states
distinguishable, respect reduced-motion preferences, and provide a clearly
simulated, development-only preview. Keep the GUI independent of any eventual
event source so that a future separately authorized observer could drive the
same presentation component without rewriting its visuals.

## Scope

- Reuse the Claude Companion's nucleus/satellite visual concept, proportions,
  state palette and restrained state-specific motion where practical; adapt
  rendering to OpenCode Desktop and its theme.
- Add an explicit presentation-state type/model and a presentation component
  that receives its state as input.
- Provide distinct visual behavior for all five states:
  - `IDLE`: calm, subtle resting presence.
  - `WORKING`: visibly active, more energetic motion.
  - `READY`: positive completion feedback that settles back to `IDLE`.
  - `ERROR`: clear, restrained failure feedback.
  - `DISCONNECTED`: subdued/inactive presentation, visually distinct from
    `ERROR`.
- Add a temporary development preview that lets the owner select each state,
  observe transitions, return to `IDLE`, and compare against Claude.
- Label every preview state as simulated. The preview must not call MCP, RPC,
  DevLog, or produce synthetic operation events.
- Keep the regular Desktop experience at `IDLE`. Keep preview-only controls
  easy to remove or exclude from production packaging.
- Preserve the current panel launcher, panel close/reopen behavior, and
  GUI-only package export.
- Respect reduced-motion preferences and keep animation work bounded.

## Explicit non-goals

- Live MCP observation, RPC, connectivity checks, server hooks, or event-to-state
  mapping.
- Operation history, Context Inspector, TUI, remote workflows, or backend work.
- Changes to `claude-mod/devlog-companion/`.
- Shared Companion Core/framework extraction.
- Preflight Gate changes, Story 0249/0250 work, runtime harness qualification,
  containers, or new testing infrastructure.
- Changes to the active Desktop installation or global OpenCode configuration.
- Commit, push, merge, or Story closure before owner validation.

## Constraints

1. ADR-084 remains governing: native presentation, independent platform
   boundaries, truthful status semantics, no fabricated runtime events, and
   explicit compatibility evidence.
2. The UI's five values in this Story are **simulated presentation inputs**, not
   claims about observed operations or DevLog connectivity. Preview labeling
   must make this distinction visible.
3. Keep presentation state separate from the existing operation projection and
   from any future event-to-state mapping.
4. Do not modify or repackage the Claude Companion. Its current code is a visual
   reference, not proof of runtime parity.
5. Keep the OpenCode package GUI-only: no default server export and no RPC or
   MCP functionality enabled.
6. The normal state remains `IDLE`; unsupported/invalid state inputs must fail
   safely and must not imply live activity.
7. Preserve all pre-existing uncommitted work, including Story 0250 and
   `runtime-tests/`.

## Proposed acceptance criteria

1. The existing Desktop panel remains discoverable and openable/closable, and
   the DevLog identity remains recognizable.
2. The normal/default panel state is `IDLE`.
3. A clearly labeled simulated preview can select all five states and return to
   `IDLE`, without issuing network, MCP, RPC, or server-observer activity.
4. Each state has a distinct and documented visual treatment; `ERROR` and
   `DISCONNECTED` are not visually conflated.
5. `READY` provides positive feedback and can transition back to `IDLE`.
6. Avatar geometry and colors remain recognizably aligned with the Claude
   Companion concept, adapted to OpenCode's native theme.
7. Reduced-motion preference disables or materially reduces nonessential
   motion without removing state distinction.
8. Focused tests cover default state, each supported state, transitions,
   invalid/unknown input handling, deterministic reduced-motion policy where
   applicable, and GUI-only packaging.
9. Existing Companion package tests, TypeScript checks, GUI build, and package
   validation pass; no additional test framework or runtime harness is added.
10. The package still exposes only `./gui-experimental`; no server/RPC
    functionality is shipped or enabled by this Story.
11. The Claude Companion files and behavior are unchanged.
12. Manual Desktop validation is recorded separately from automated results.
    Human acceptance remains pending until the owner validates the visuals.

## Initial Repository Analysis questions

- Which exact Claude avatar geometry, colors, state treatments, transition
  behavior, and reduced-motion rules are present in the current implementation?
- Which current OpenCode files and tests are part of the GUI-only baseline, and
  which state helpers are coupled to actual observation facts?
- What is the smallest presentation-state interface that stays independent of
  future event sources while handling invalid inputs truthfully?
- How can the development preview be enabled for the installed local GUI
  bundle but excluded from normal package output?
- Which OpenCode Desktop 2.4.2 theme primitives and reduced-motion signal are
  available in the pinned GUI SDK, and what needs manual visual verification?
- Which focused checks can be performed with the current Node test suite and
  without introducing runtime or UI test infrastructure?

## Workflow and gates

1. Story definition and Repository Analysis are prepared for owner review.
2. Repository Analysis approval is required before producing the
   Implementation Plan.
3. A distinct approval of the Implementation Plan is required before
   implementation.
4. Implementation changes remain limited to the OpenCode presentation package
   and directly required Story documentation. Existing installed Desktop config
   is not changed.
5. Desktop reload/restart is not performed by the agent. If needed, the owner
   performs it.
6. Automated results and manual Desktop observation are reported separately.
   The owner alone confirms visual acceptance.

## Planned artifacts

- `repository-analysis.md` — current repository facts, visual reference, state
  boundaries, risks and missing implementation details.
- `implementation-plan.md` — to be prepared only after Repository Analysis
  approval.
- Implementation report — after separately approved implementation, recording
  tests, packaging, simulated-preview usage, and manual validation status.
