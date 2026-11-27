# Story 0251 — Repository Analysis

## Workflow status

**Repository Analysis prepared — awaiting explicit owner approval.**

This analysis is not approval of an Implementation Plan or authorization for
implementation, installation, Desktop reload, runtime experiments, or changes
to the user's OpenCode profile. The Implementation Plan must wait for approval
of this analysis.

## Repository state and scope boundary

- Worktree: `/home/ludo/workspace/devlog-ai-story-0248`
- Branch: `story/0248-opencode-native-compatibility-spike`
- HEAD: `13c27737a97f7b86a00a0011b80bd4c92c671f78`
- The worktree already contains modified Story 0248 and OpenCode Companion
  files, plus untracked Story 0250, `runtime-tests/`, and Story 0251 artifacts.
  These are existing work and must be preserved. No cleanup, reset, or
  replacement is part of this Story.
- Story 0249 is not in this worktree. Story 0250 records that a prepared 0249
  worktree exists at `/tmp/devlog-ai-story-0249`; that separate worktree is
  outside this analysis and must not be changed.
- The owner has explicitly left Stories 0249 and 0250 suspended. This proposal
  does not resume their implementation, runtime qualification, or acceptance.
- Story 0251 was absent from the repository catalog and DevLog number lookup
  before this draft. It has since been registered in DevLog as record
  `6756950c-1d46-444f-8fe9-20e4aeee39d2`, status `REGISTERED`. Registration is
  not implementation authorization.

## DevLog context and evidence limits

The canonical project mapping in `TOOLS.md` is `devlog-ai`. The bounded
`devlog_story_agent` preflight completed as task/snapshot
`28cf7237-b921-4661-bfaa-ce6b292c6291`, at repository revision
`94fdd24c19189c6f9fb633511a04eb667ee5efe5`.

- Freshness: `STALE` / `PARTIALLY_FRESH`; source baseline was last analyzed at
  `7ecc16806e3dea3c412f98139952ed0cfabb6f54`.
- Selection: 60 of 1,161 candidates; accounting reported truncation and
  warnings for budget application, truncated evidence/content enrichment, and
  stale project context.
- Confidence: `LOW`; no decision findings or implementation preparation were
  returned.
- The useful bounded findings are that Story 0247 protects Claude from
  unapproved modification, and Story 0248's retained evidence does not certify
  the proposed GUI behavior. These findings agree with current repository
  artifacts.
- The result explicitly could not ground exact Claude visuals or prove the
  complete OpenCode MCP/RPC boundary. The current repository was inspected
  directly for those physical facts; no DevLog interpretation is treated as
  authoritative over the worktree.
- Context7 was attempted to verify current OpenCode GUI API documentation but
  could not be used because its monthly quota was exceeded. SDK-specific
  reduced-motion support and theme behavior therefore remain unverified here.

## Governing sources

- `docs/decisions/ADR-084.md` is the governing accepted decision supplied in
  this repository. It requires native platform presentation, preserves
  independent platform boundaries, rejects fabricated events or misleading
  status, and identifies the OpenCode Desktop GUI API as experimental.
- Story 0248 establishes the OpenCode static GUI milestone and expressly
  separates the GUI-only package from server observer/RPC behavior.
- Story 0247 and its `companion-baseline-v1.md` establish Claude as a protected
  baseline, not an implementation source to modify or a runtime parity claim.
- Story 0250 and its implementation report establish the suspended runtime
  harness scope and its separate authorization gates.
- Current OpenCode package source, manifest, lockfile, tests, and the pinned
  `@opencode/plugin` declarations establish the physical GUI baseline.

## Current OpenCode implementation

The current GUI implementation is in
`opencode-mod/devlog-companion/src/gui.tsx`:

- It declares one closable, transient side-panel tab and a session-panel menu
  launcher.
- It renders the DevLog icon, static `IDLE`, and a static explanatory sentence.
- It has no state selector, preview controls, animation, or reduced-motion
  behavior.
- The current source imports the OpenCode GUI plugin surface and a local SVG.
  It does not import the server observer, RPC module, or operation projection.
- The panel's displayed `IDLE` is presentation text in this GUI. It currently
  does not claim to have checked live connectivity.

The current package boundary is enforced by `package.json` and
`tests/gui-package.test.js`:

- Only `./gui-experimental` is exported, resolving to
  `dist/gui-experimental.cjs`.
- The package allowlist contains `src/gui.tsx`, the DevLog SVG, and `dist`; it
  excludes server/RPC/projection sources.
- The current checks use Node's built-in test runner, TypeScript, Vite GUI
  build, and `npm pack --dry-run`; no UI automation framework is configured.
- `vite.config.js` has one GUI build. There is no existing development-versus-
  production build flavor or preview-control exclusion mechanism. Therefore,
  “development-only preview” and “exclude preview controls from normal package
  output” are requirements without an established packaging convention. The
  Implementation Plan must define and test how those two modes differ rather
  than assume that `private: true` or the current allowlist makes controls
  development-only.

The same worktree contains `src/model.js`, its types/tests, `src/rpc.js`, and
`src/server.js` from the broader spike. Those sources include observed-operation
projection and RPC/server paths; the currently exported GUI does not import
them. Their mere presence in the repository is not evidence that a preview may
use them. Presentation inputs for this Story must remain separate from observed
projection facts and must not invoke those paths.

The local SDK declarations in `node_modules/@opencode/plugin/dist/gui` document
the panel/tab APIs and theme contribution types, but do not establish a
reduced-motion API. They are local installed-package evidence, not a substitute
for the unavailable current documentation lookup or a Desktop runtime test.
There is no stylesheet or media-query implementation in the current OpenCode
package. Any reduced-motion approach and theme integration must be verified
against the pinned package and visually checked in the owner-approved Desktop
environment.

## Claude Companion visual reference

`claude-mod/devlog-companion/hooks/register.tsx` contains a clear visual
reference that can be inspected without changing it:

- The avatar uses a 20×20 coordinate system centered at `(10, 10)`: a central
  circle of radius 3, a ring of radius 6.5, and three satellites near the top
  and lower diagonals.
- Its colors are green `#3fb950` (`IDLE`), amber `#fbbf24` (`WORKING`), blue
  `#58a6ff` (`READY`), red `#f85149` (`ERROR`), and gray `#8b949e`
  (`DISCONNECTED`).
- `IDLE` gently varies ring opacity; `WORKING` rotates satellites; `READY`
  performs a finite positive ring pulse; `ERROR` uses red with subdued
  satellites; `DISCONNECTED` is gray, low-opacity, and uses a dashed ring.
- The SVG includes `prefers-reduced-motion: reduce` rules that hide SVG
  `animate` and `animateTransform` elements.
- Claude derives these states from actual operation/disconnection data. That
  derivation is not appropriate for this Story's deliberately simulated
  presentation inputs.

This is an existing-code visual reference only. It neither authorizes Claude
changes nor establishes visual/runtime parity between Claude and OpenCode.
Story 0247's documentary baseline remains the relevant non-regression boundary.

## Owner-confirmed static milestone

Story 0248's report distinguishes an earlier failed isolated Desktop launch
from a later owner-authorized manual installation. The latter recorded a
backup of the global OpenCode configuration, a read-only plugin record showing
the GUI plugin active, and the owner's confirmation that the plugin works and
DevLog appears in the sidebar. This owner confirmation supersedes
`NOT_EVALUATED` for basic discovery/visibility only.

It is not an agent-captured screenshot or independent confirmation of all
panel interactions, full version compatibility, live DevLog integration, or
runtime qualification. The active user profile and backup remain in place.
This Story therefore does not authorize modifying that profile or initiating a
reload/restart.

## Architectural analysis

The proposed distinction between simulated UI input and observed operation
projection is consistent with ADR-084. The five state names alone must not be
presented as observed facts: in particular, simulated `DISCONNECTED` cannot
claim that OpenCode, MCP, or trace connectivity is actually lost. A persistent,
visible simulation label should accompany the preview controls/state, and
simulation must not produce activity records or network requests.

The feature can remain confined to the OpenCode GUI adapter. It does not need a
shared Companion Core, Claude changes, DevLog backend work, or a connection to
the existing server/RPC implementation. This is a repository-supported
interpretation, not a new architectural decision.

The existing Desktop API remains experimental. The owner's baseline
confirmation makes manual comparison meaningful, but does not convert the API
or version pairing into a qualified compatibility claim. Automated checks can
establish model behavior, packaging boundaries, type-checking, and build
success; they cannot establish the visual result or reduced-motion behavior in
Desktop.

## Risks, unknowns, and plan questions

1. **Preview packaging boundary:** only one GUI bundle currently exists. Decide
   whether the development preview uses a separate build/entry, a non-shipped
   local development flag, or another demonstrably removable mechanism. The
   normal package must be tested to exclude preview controls while retaining
   the static `IDLE` presentation.
2. **READY transition semantics:** define the finite preview duration and
   cancellation/reset behavior, including what happens when another state is
   selected before the READY pulse ends. Do not create an implicit claim about
   real task recency.
3. **Reduced motion:** determine which media-query mechanism is reliably honored
   inside the experimental Desktop GUI renderer. Preserve state distinction
   without animation and avoid claiming SDK support before verification.
4. **Theme and accessibility:** verify contrast and appearance in the actual
   OpenCode theme, labels for screen readers, and keyboard operation of preview
   controls. The SDK declarations alone do not establish these rendered
   behaviors.
5. **Small panel constraints:** choose concise copy and controls that remain
   understandable at the panel's narrow width and do not interfere with the
   existing launcher/close behavior.
6. **Preview exposure:** clarify whether the owner needs the selector in the
   locally installed bundle for manual validation or whether a separate
   development preview page/build is acceptable. Do not change the currently
   installed profile to expose it.
7. **Visual acceptance evidence:** record manual Desktop observation separately
   from automated checks. A screenshot may be useful if the owner chooses to
   provide/capture one, but must not be fabricated or required as a substitute
   for owner acceptance.

No material contradiction with ADR-084 was found. The unresolved items are
implementation-shaping details that should be answered in the Implementation
Plan or explicitly returned to the owner; they do not justify live integration
or expanding scope.

## Recommendation

Approve this Repository Analysis only as the basis for a narrowly scoped
Implementation Plan covering the OpenCode GUI presentation and a removable,
clearly simulated preview. The plan should state the preview packaging strategy,
define deterministic READY/reset behavior, specify reduced-motion handling,
and separate automated package checks from manual Desktop visual validation.

Do not begin implementation until the owner separately approves the
Implementation Plan. Do not modify Claude, DevLog integration paths, the
Preflight Gate, Stories 0249/0250, runtime infrastructure, or the user's
installed configuration.

**Workflow decision:** `ANALYSIS_AWAITING_APPROVAL`; no Implementation Plan has
been prepared.
