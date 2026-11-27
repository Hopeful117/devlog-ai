# Story 0253 — Proposed GUI-local Operation History Contract

## Status and authority

**Approved implementation contract — owner authorization on 2026-11-27.**

This is a presentation-only contract, not a wire schema, authorization grant,
DevLog business contract, or claim of session isolation. It inherits Story
0252's trusted-GUI-plugin assumption: route filtering is presentation scoping,
not server-side authorization or isolation from another trusted plugin.

## Source and lifecycle

- Source only the already-loaded transcript exposed for the currently routed
  `MountedSession`; do not request or load more transcript for History.
- Recompute from that source on native/cache invalidation rather than maintain
  an independent operation ledger.
- Scope derived state to current route/session and server/location identity.
  Clear or reconcile it on route changes, panel lifecycle, and plugin disposal.
- Do not enumerate sessions or retain raw message, event, or transcript objects.

## Proposed bounded row

The derived in-memory row is limited to these fields:

| Field | Meaning and constraint |
|---|---|
| `source` | `direct-tool` or `code-mode`; identifies evidence shape, not execution certainty. |
| `toolName` | Normalized allowlisted DevLog tool name only. |
| `toolNames` | For a Code Mode outer execution, bounded distinct allowlisted names observed in nested metadata/result rows. This summarizes tool kinds, not individual call identities or counts. |
| `additionalToolNameCount` | Count of additional distinct allowlisted names omitted after the per-row display cap; not a count of nested calls. |
| `hostCallId` | Native direct tool-part ID or outer `execute` ID only, if supplied and at most 128 characters. Never synthesized; oversized IDs are omitted. |
| `platformOutcome` | Evidence-qualified `RUNNING`, `COMPLETED`, `ERROR`, `INTERRUPTED`, or `UNKNOWN`; host outcome only. |
| `taskOutcome` | Explicitly decoded DevLog task status where present; otherwise `UNKNOWN`/not applicable. |
| `observedAt` | Native timestamp only if actually present and valid; never infer from render time or array position. |
| `sequence` | Optional transcript-order index for stable rendering only; must not be labeled as wall-clock chronology. |

Never retain or render prompt text, tool arguments, raw result blocks, raw error
messages, credentials, project context, or unrelated tool content. If `intent`
is considered useful, it requires separate explicit approval and a dedicated
sanitization/privacy decision; it is excluded from this proposal.

## Evidence rules

### Direct tool parts

- Include only assistant tool parts whose normalized tool name matches the
  existing explicit DevLog allowlist.
- Use native part ID and native timestamps only when present.
- Preserve host execution outcome separately from any structured DevLog task
  outcome. Host completion alone is not business success.
- If structured result blocks or explicit task-status fields conflict, report
  the task outcome as unknown. Repeated identical status evidence is not a
  conflict. A poll/pending status is displayed as last-reported, not asserted
  to be the current task state.
- Reuse the Story 0252 decoder as-is unless a separately confirmed defect is
  demonstrated with a fixture and approved scope. Do not broaden success
  inference to fill a History row.

### Code Mode

- Include only explicit nested `toolCalls` metadata/results recognized by the
  existing allowlist.
- The pinned terminal row shape may provide only a name. In that case, group
  the row beneath the outer `execute` and show an unknown/incomplete outcome.
- Do not create a nested call ID, associate an outer result to a nested row,
  claim nested timing/order, or present nested calls as independently
  correlated operations.
- If a host-supported shape explicitly provides nested status but no identity,
  only the status fact may be shown under the outer execution; do not imply
  per-call identity.

## Bounds and retention

- At most 40 transcript messages inspected, consistent with the existing
  presence projection.
- At most 80 parts per message and 80 nested Code Mode rows per outer part.
- At most 20 History rows rendered/retained, matching the Claude Companion's
  current `MAX` as a UX reference, not as a parity requirement.
- At most 8 distinct tool names retained per Code Mode row. Further distinct
  names are summarized by `additionalToolNameCount`; repeated names do not
  imply separate identities or a displayed call count.
- At most 128 characters are retained for a native host call ID; oversized IDs
  are omitted. The ID is not shown in the UI.
- No time-based retention beyond the currently available routed transcript;
  no persistent storage. If the transcript is unavailable or loading, show a
  neutral unavailable state rather than an empty-history claim.

Limits remain explicit, independently tested, and bounded.

## Presentation semantics

- Distinguish an empty, available transcript (“no recognized DevLog operations
  in this loaded view”) from unavailable/loading transcript evidence.
- Show unknown/incomplete evidence neutrally; do not reuse presence colors as
  proof of task outcome.
- Treat a reported pending/poll status as last-known evidence, not proof that
  the task remains active now.
- Render newest-first only if the source ordering is established by the native
  transcript contract. Otherwise use transcript sequence and label no absolute
  chronology.
- A History row is an observation, not an authoritative DevLog operation record
  and not a Context Trace.
