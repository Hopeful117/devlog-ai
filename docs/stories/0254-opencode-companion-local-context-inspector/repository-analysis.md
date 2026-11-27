# Story 0254 — Repository Analysis

## Repository and governance baseline

- Prepared in a clean detached worktree at `origin/main`,
  `35b2b64277f898befcc3b38584598a4cc1ea87f2`; this is the current fetched
  `main` commit. The local branch named `main` was behind, so it was not used.
- The highest numbered Story directory on this revision is 0253; **0254 is the
  next observed repository identifier**. No DevLog Story registration was
  created or inferred.
- ADR-084 is accepted. Its relevant constraints require native platform
  boundaries, truthful unknown/partial states, session/location scoping,
  bounded sanitized projection, independent failure behavior, no implicit
  credentials in presentation, and no exact Context Trace attribution without
  a verified join.
- Story 0253 adds a bounded GUI-local transcript History view. Its decoder
  recognizes direct DevLog tools and Code Mode only at host-supported
  granularity; nested Code Mode rows do not acquire individual identity or
  result correlation. Now remains independent.

## Code Mode runtime observation and scope revision

- During Desktop use through Code Mode, the owner observed History display
  **“Host execution completed / Nested outcome unknown.”** This matches the
  owner-recorded Story 0253 runtime acceptance: Code Mode rows are grouped under
  the outer execution, and nested identity/outcome is not inferred.
- One separately requested read-only `get_engineering_context` call was made
  through the agent's Code Mode tool channel for this runtime investigation.
  Its result reported `STALE`, `truncated: true`, and
  `candidateCount: 1170` / `selectedCount: 60`; the serialized `evidence` and
  `sections` arrays were empty. This does not make its nested result
  individually selectable by Context.
- Story 0253's accepted contract says the pinned terminal row may expose only a
  name, forbids fabricating a nested ID or associating the outer result with a
  nested row, and requires unknown/incomplete outcome for that shape. The
  current `gui-presence.js` stores only distinct nested tool names and the outer
  `execute` ID for Code Mode History.
- Therefore the revised Story 0254 treats Code Mode as the required Desktop
  runtime path for **graceful-degradation acceptance**, not as an eligible
  Context projection source. The direct completed result remains the only
  eligible projection source. Lack of one means direct-projection checks are
  `NOT RUN`, not that the whole Code Mode runtime pass is blocked.
- Supporting individual nested Code Mode Context inspection would require
  host-native exact child identity and per-call result association. The current
  observation does not establish those fields; no correlation strategy is
  inferred or proposed as an accepted decision.

### DevLog preflight for the scope revision

A targeted Story Agent preflight was run for this revision
(`aiTaskId`/snapshot `f3b5b77e-e7c9-443b-b1ac-dfe307681779`, stable request key
`story0254-codemode-scope-revision-20261127`). DevLog could not resolve a Story
UUID (`resolutionState: UNKNOWN`); no unverified `storyId` was supplied. The
returned context was `PARTIALLY_FRESH` / `STALE`, bounded and truncated
(60 selected of 1,170 candidates; 5,959 tokens). Its AI analysis had LOW
confidence and explicitly lacked the Story 0254 contract files; it is not used
to establish the revised requirements. Repository Story 0253, ADR-084, current
code, and the owner's runtime observation remain authoritative.

## DevLog context and preflight limitations

The earlier Context Inspector feasibility preflight ended with
`INVALID_AI_TASK_RESULT`: cited evidence did not support
`opencode-mod/devlog-companion/src/gui-presence.js`. Its context was recorded
as stale/partially fresh and truncated. This Story does **not** investigate or
remediate that failure.

A new bounded read-only Story Agent preflight was run for this preparation
(`aiTaskId`/snapshot `3888a5bb-0de5-40ee-8e2e-d16b615f93d9`, request revision
`35b2b64277f898befcc3b38584598a4cc1ea87f2`). It completed, but reported
`PARTIALLY_FRESH` with state `STALE`, accounting truncation (`60` selected of
`1171` candidates; `11,722` of `12,000` tokens), and warnings for applied
context budget, truncated evidence/content enrichment, and stale project
context. It did not include the full 0253 implementation plan/report. Its
constraints are supplementary only; current repository code and contracts
govern physical state. No synthesis claim is taken as evidence for files it
did not cite.

## Confirmed result and DTO contract

- `EngineeringContext` consists of `project`, `intent`, `evidence`,
  `metadata`, `sections`, and `requestEcho`.
- `EngineeringContextTool` serializes that DTO as JSON text. Its MCP
  `get_engineering_context` tool uses the authenticated MCP principal and
  returns a response bounded to 25,000 characters, trimming evidence/sections
  and adding `RESPONSE_SIZE_TRUNCATED` when possible.
- `EngineeringContextMetadata` explicitly supplies candidate/selected/rejected
  counts, `truncated`, token accounting, digest, warning strings, freshness,
  and rejection counts.
- `EngineeringContextFreshness` supplies status, repository revision, context
  revision, and per-source freshness records. Known top-level freshness values
  are `CURRENT`, `STALE`, `NO_BASELINE`, `UNKNOWN`, and `PARTIALLY_FRESH`.
- `EngineeringEvidence` has kind, layer, summary, source type, file,
  identifiers/references, content, symbols, metadata, and trust tier.
  `TrustTier` has `TRUSTED`, `HUMAN_AUTHORED`, `TECHNICAL_EVIDENCE`, and
  `SYSTEM_METADATA`. `ContextSection` has name, trust tier, evidence, and
  rationale. Do not render free-form evidence, section rationale, prompt-like
  intent, source identifiers, or content.
- There is **no confidence field** on this EngineeringContext contract.
- Candidate-field decision: availability, top-level freshness, truncation,
  optional repository/context revisions, trust-tier evidence categories,
  section counts, and selected metadata counts are explicitly represented.
  Confidence is omitted because it is absent. Per-source counts and
  human-authored section summaries are omitted from this minimal projection:
  `sourceType`, section name, and rationale are strings rather than closed
  presentation enums, and no extra detail is needed to explain the bounded
  result. Only exact allowlisted warning **codes** may be shown; arbitrary
  warning/diagnostic messages are excluded.
- The native GUI History reducer reads the routed session's already-loaded
  transcript and native tool part `state`/`content`; it does not perform
  transcript I/O. Direct result status/content and host call ID are available
  only when supplied by that host part. The pinned Code Mode terminal shape may
  expose names without nested result identity; outer success cannot make a
  nested result eligible.

## Correlation and access boundary

The MCP observability service creates `instanceId`/`operationId` internally;
these are not exposed as operation IDs in the native History projection. The
REST Context Trace endpoints require project authorization. The MCP trace tool
requires project ID plus MCP instance and operation IDs, which the native
result does not currently provide. An `aiTaskId` is not an exact invocation
join. Therefore no trace retrieval or task-to-operation inference is in scope.

The feasible first increment is an **observed result inspector** for the
structured JSON text returned by a direct completed `get_engineering_context`
tool part. It can show backend-declared context metadata already present in
that result, while clearly stating that it did not retrieve a backend Context
Trace.

## In-Context selection usability revision

- After the owner loaded the Context view in Desktop, they reported that the
  History-only selection requirement made the panel appear empty and was not
  discoverable.
- The owner requested implementation of a picker inside Context. The picker
  uses only the existing bounded History projection and loaded transcript,
  lists only exact eligible direct results, and shares the same native scoped
  selection as the History action.
- The picker does not fetch transcript or backend data. Name-only Code Mode
  results remain absent from the selectable choices; this usability change
  does not alter Story 0253's correlation boundary.
- A targeted Story Agent preflight for this usability question completed with
  `resolutionState: UNKNOWN`; DevLog did not resolve a Story 0254 UUID. The
  result was `PARTIALLY_FRESH`/`STALE`, accounting-truncated (60 of 1,171
  candidates), and did not retain Story 0254 evidence. Its LOW-confidence
  analysis is not authoritative. The current Story, accepted 0253 contract,
  and owner instruction govern this narrow GUI change.

## Repository state and preserved work

Story 0253 is merged as PR #252 at the baseline SHA above. The requested
delivery-status corrections are retained in its Story, plan, and report files
in this worktree. A pre-existing untracked
`docs/stories/0252-devlog-opencode-live-presence/repository-analysis.md` in the
original worktree was left untouched and remains there; it is not copied into
or overwritten by this preparation.

At the initial documentation-preparation checkpoint, no production code,
Desktop configuration, or credentials had been changed. The GUI-only
implementation and Context picker described above were subsequently authorized
by the owner; they add no backend call, Desktop configuration change, or
credential handling. The earlier explicitly requested read-only
`get_engineering_context` call remains the only live context retrieval. No
separate investigation into the prior preflight failure was started.

## Owner decision: finalization and Code Mode deferral

On 2026-11-27, the owner approved finalizing the existing GUI-only
implementation, retaining direct MCP result inspection as a supported secondary
capability, and deferring Code Mode context inspection. Code Mode is the owner's
primary workflow, but the examined native GUI interfaces expose the outer
execution and nested tool names without an exact nested result, individual
identity, and provenance. The outer `execute` result is not accepted as the
exact nested MCP result. No correlation is inferred.

The projection's actual fields are Overview, evidence counts by trust tier,
section evidence counts, and Diagnostics. It intentionally has no dedicated
Sources section: free-form source fields are excluded by the projection
contract. Full Claude Context Inspector parity has not been achieved.

This finalization records the earlier bounded DevLog preflights as stale or
partially fresh, truncated, and unable to resolve a Story 0254 UUID or retain
the requested Story evidence; their low-confidence analyses are not used as
authority. The separate Context7 lookup was unavailable because the monthly
quota was exceeded. Neither limitation is being remediated by this Story.
