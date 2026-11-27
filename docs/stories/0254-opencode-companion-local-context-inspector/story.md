# Story 0254 — OpenCode Companion — Local Context Inspector

## Status and authorization

**Scope revision and GUI-only implementation authorized by the owner on
2026-11-27.** The owner additionally authorized direct operation selection
inside Context after observing that the History-only selection was not
discoverable. This authorization covers the GUI-local picker and its checks.
Desktop launch/reload, active plugin configuration, additional DevLog
operations, commit, push, and deployment remain unauthorized. On 2026-11-27,
the owner approved finalization of this implementation and deferred Code Mode
context inspection until a reliable, proportionate native data-access
mechanism is available. This is not human Story acceptance.

## Objective

Add a third **Context** view to the OpenCode Desktop Companion, alongside the
existing **Now** and **History** views. Let the user select an eligible direct
`get_engineering_context` operation from Context itself or from its corresponding
History entry, then inspect a bounded, sanitized projection of the structured
EngineeringContext explicitly present in that exact observed tool result.
The owner's primary workflow uses Code Mode. Code Mode context inspection is
deferred: the examined native GUI transcript exposes the outer `execute`
operation and bounded nested tool names, but not an exact nested result with
reliable identity and provenance. The outer execution result is not accepted
as the exact result of a nested MCP invocation. In this workflow, Context must
show the supported unavailable explanation rather than imply that nested
context is inspectable. Direct MCP inspection remains a supported secondary
capability.

This is **local observed-result inspection**, not backend Context Trace
inspection. The UI must state that distinction wherever the projection is
shown. Backend Context Trace inspection is deferred.

## Governing context

- Accepted [ADR-084](../../decisions/ADR-084.md), including its accepted
  amendment.
- Story 0253 and its GUI-local History projection contract.
- The current EngineeringContext contract in `devlog-contracts` and
  `EngineeringContextTool` MCP response behavior.
- The current OpenCode GUI transcript representation and Companion lifecycle.
- The bounded preflight limitations recorded in
  [repository-analysis.md](repository-analysis.md).

## Scope

- Preserve Now as the default view and retain existing Now and History
  behavior.
- Add a Context view that lists eligible direct results from the already-loaded
  History transcript and lets the user select one there; keep History selection
  synchronized with the same Context selection.
- Accept only a completed, recognized direct `get_engineering_context` tool
  part whose bounded native result contains the explicitly supported
  EngineeringContext structure described in
  [local-context-projection-contract.md](local-context-projection-contract.md).
- Derive a sanitized local projection from the exact selected result; show
  only contract-supported availability, freshness, truncation, repository
  revision, trust-tier evidence counts, section counts, and allowlisted
  diagnostic indicators.
- Present the implemented trust-tier evidence counts and section evidence
  counts. Do not add a dedicated Sources section merely to match the original
  plan: source fields are intentionally excluded by the projection contract.
- Keep evidence provenance distinct from freshness. Do not imply that freshness
  establishes trust or that staleness invalidates/fabricates evidence.
- Scope selection and projection to the currently routed server/location and
  session; reconcile on transcript/result/route changes and clear unavailable
  selections.
- Provide explicit available, partial, unavailable, unverified, and empty
  states. Identify the observed tool and explain that no backend Context Trace
  was retrieved.
- Preserve Code Mode as the owner's primary workflow while explicitly
  deferring nested context inspection. In Desktop, preserve the outer host
  result (“Host execution completed / Nested outcome unknown”) as outer-host
  evidence; do not promote it to an individual DevLog outcome or Context
  projection. If the nested name is `get_engineering_context`, explain that
  individual result identity and provenance are unavailable.
- Add deterministic tests and update the user-facing README if needed, within
  the existing OpenCode GUI package boundary.

## Strict eligibility and unsupported cases

Eligibility requires all of the following:

1. A native assistant tool part is recognized by the existing DevLog tool
   allowlist as direct `get_engineering_context` (not a name found in text).
2. Native tool state is `completed`.
3. A native host call ID is present, unique in the current bounded transcript,
   and usable to select the exact result.
4. The result representation and EngineeringContext root structure pass the
   strict decoder in the local projection contract.

`devlog_story_agent`, other DevLog tools, Code Mode nested results without an
individually supplied identity and exact result association, arbitrary JSON or
text, malformed/oversized/multi-block ambiguous results, duplicate or missing
host IDs, and unsupported contract shapes are not eligible. Show an explicit
unavailable or unverified explanation; do not guess, fall back to another
operation, or retrieve a backend trace.

## Data and UI constraints

- Keep the projection in memory and local to the currently routed panel/session.
- Never copy raw result blocks, full evidence objects, evidence bodies,
  summaries, intent, request echo, arguments, prompt text, arbitrary warning
  messages, credentials, or tokens into Companion-owned state or UI.
- Do not expose evidence identifiers, resource URIs, paths, names, or raw
  revision strings except the explicitly bounded repository/context revision
  values allowed by the projection contract.
- Do not infer a confidence score: the EngineeringContext DTO does not define
  one.
- Do not infer backend task/operation identity, join by time or tool name, or
  label the projection as a Context Trace.
- Route filtering is presentation scoping, not server authorization.

## Explicit non-goals

- Backend Context Trace retrieval or full parity with Claude's Context
  Inspector. This Story does not achieve that parity.
- New Companion RPC, server observer, transcript fetch/sync, new MCP/REST call,
  backend/API/contract change, authorization or credential work, token
  extraction, or external transmission.
- Persistence, cross-session inspection, TUI, remote support, shared-core
  extraction, Claude changes, Code Mode configuration/host changes, or inferred
  correlation of nested Code Mode results.
- Changing global Code Mode configuration, triggering another DevLog operation
  for validation, modifying active Desktop configuration, launching/reloading
  Desktop, or adding runtime infrastructure.
- Stories 0249/0250, Gate changes, DevLog freshness/grounding remediation, or
  investigation/remediation of the recorded preflight error.

## Acceptance criteria

1. **Three views:** Now, Context, History are available; Now remains selected
   by default and its existing behavior is unchanged.
2. **Strict decoder:** only a completed direct `get_engineering_context` native
   result with a unique host call ID and supported EngineeringContext shape is
   eligible. Tool names alone, arbitrary JSON/text, task IDs, and outer
   Code Mode success never establish eligibility.
3. **Exact association:** the displayed projection is derived only from the
   exact selected native result and is cleared if that result changes, leaves
   the bounded transcript, becomes ambiguous, or its route/session/server
   scope changes.
4. **Truthful fields:** the UI reflects supported availability, freshness,
   truncation, repository/context revision, trust-tier evidence counts,
   section counts, and allowlisted diagnostics. Unsupported confidence is
   omitted. Missing, conflicting, unknown, and truncated values remain
   explicit.
5. **Sanitization and bounds:** projection and rendering obey the contract
   limits and contain no raw prompt/arguments/result/evidence, arbitrary
   diagnostic text, sensitive credentials, or backend-derived data.
6. **Clear provenance:** Context labels itself as information from an observed
   tool result and explicitly says it is not a retrieved backend Context Trace.
7. **Unsupported states:** agent results, Code Mode nested results without
   exact individual identity/result association, malformed/oversized or
   unsupported results, and unavailable transcript/selection produce neutral
   unavailable/unverified states, not empty or successful context claims. A
   Code Mode `get_engineering_context` row explains why it cannot be selected.
8. **Discoverable selection:** Context itself lists only eligible direct
   `get_engineering_context` results from the currently loaded, bounded session
   history. Selecting one updates the same scoped projection as selecting it
   from History. With none available, Context explains the empty picker; no
   fetch or backend operation is triggered.
9. **Lifecycle:** session, location/server, selection, result and panel
   lifecycle changes cannot display context from a previously routed session;
   added subscriptions are cleaned up.
10. **Non-regression and GUI-only:** Now/History tests continue to pass; no
   RPC, observer, MCP/REST, backend, credential, persistence, external
   transmission, Claude, Gate, or infrastructure changes are introduced.
11. **Validation:** focused deterministic tests, Companion test suite,
     TypeScript check, GUI build, package dry-run, and `git diff --check` pass.
     Desktop validation is recorded separately and is not implied by builds.
12. **Owner-led Desktop validation:** verify the owner's normal Code Mode
       workflow remains truthful and exposes no fabricated nested context.
       Code Mode inspection itself is deferred, not an acceptance claim for
       inspecting nested results. Verify direct-result inspection only if an
       eligible result is already present; otherwise record it as `NOT
       EVALUATED` and do not manufacture one.

## Approval gates and deferred capability

The owner authorized the GUI-only implementation and the in-Context picker.
This is not human Story acceptance. Desktop configuration changes,
launch/reload, further real DevLog operations, and delivery actions remain
separate gates.

Code Mode context inspection is **deferred by owner decision**. The current
native GUI interfaces do not expose a nested EngineeringContext result with
reliable individual identity and provenance. An outer `execute` result,
assistant-generated text, tool names, order, timestamps, outer success, or
unverified task identifiers are not substitutes. Reconsider only when a
reliable and proportionate native data-access mechanism is available, without
silently reopening Story 0253's accepted no-inferred-correlation rule.

Full backend Context Trace inspection is **deferred**, not implemented by this
Story. Any future proposal requires a separately authorized design for
server-side authorization, exact invocation correlation, and safe degradation.

Direct MCP result inspection is supported as a secondary capability. Full
Claude Context Inspector parity has not been achieved.
