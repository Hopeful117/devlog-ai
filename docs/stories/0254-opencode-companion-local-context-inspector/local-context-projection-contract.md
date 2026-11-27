# Story 0254 — Local Context Projection Contract

## Authority and distinction

This is a GUI-local, sanitized projection of a **native observed result**. It is
not a wire schema, authorization grant, task lookup, or backend Context Trace.
Every Context view must identify the source as an observed
`get_engineering_context` result and state: **“Local inspection of the observed
tool result; no backend Context Trace was retrieved.”**

The Java/Core DTO remains authoritative for the values. The GUI only validates,
selects a bounded subset, and presents declared values. It does not reconstruct
context, verify evidence truth, or calculate freshness.

## Native eligibility

Eligibility is evaluated from the currently routed native transcript:

1. Inspect only the existing bounded transcript window (at most 40 messages
   and 80 parts/message, matching Story 0253).
2. Require an assistant tool part with `type === "tool"` and a name accepted by
   the existing allowlist after normalization to exactly
   `get_engineering_context`.
3. Require native `state.status === "completed"`.
4. Require exactly one result content block, a text block, with no native
   `isError` marker. Its text must be at most 25,000 JavaScript characters and
   parse as one JSON object. Do not infer from assistant prose, other blocks,
   guessed `structuredContent`, tool name alone, or success of an outer Code
   Mode `execute`.
5. Require the recognized EngineeringContext root structure below.
6. Require a non-empty native part `id` or `callID`, at most 128 characters,
   unique among the bounded transcript's direct DevLog tool parts. Without a
   unique native ID the entry may be listed as unverified but cannot be selected
   for a retained projection.

Any failed check yields `UNAVAILABLE` or `UNVERIFIED` with a fixed reason code.
Do not recover by searching another part or joining on timestamp, tool name,
project slug, result task ID, or backend operation ID.

### Code Mode host shape

Code Mode is a supported Desktop usage path for History observation, but it is
not an eligible Context source when the loaded transcript exposes only the
outer `execute` part and nested allowlisted tool names. Story 0253 explicitly
keeps those rows grouped beneath the outer execution and forbids fabricating a
nested ID or associating the outer result with a nested row. In this shape:

- Preserve the host outcome (`COMPLETED`) separately from nested DevLog outcome
  (`UNKNOWN`).
- Do not use the outer `execute` native ID as the identity of a nested
  `get_engineering_context` call.
- Do not decode arbitrary outer result text as the nested tool result.
- Do not offer a selectable Context projection for a name-only nested row.
- When the nested tool name is `get_engineering_context`, show a fixed
  explanation that an individual nested result identity is unavailable.
- A future host representation with explicit nested identity and exact
  per-call result association requires a separately reviewed contract change;
  names, order, timestamps, task IDs, or outer completion are not substitutes.

Direct-result-specific Desktop projection checks are `NOT EVALUATED` if no
eligible direct result is already present. Automated validation does not imply
Desktop runtime acceptance.

## Supported wire fields

The supported top-level DTO fields are:

| Field | Validation/use |
|---|---|
| `project` | Require an object. Do not retain or render its ID, name, description, notes, or slug. |
| `intent` | Require a string for contract-shape validation only. Never retain or render it. |
| `evidence` | Require an array. Inspect bounded entries only for valid `trustTier`; ignore all other fields. |
| `metadata` | Require an object with non-negative integer `candidateCount`, `selectedCount`, and `rejectedCount`, boolean `truncated`, and `freshness` object. Other metadata is ignored except allowlisted warning codes below. |
| `sections` | Require an array. Inspect only recognized `trustTier` and bounded `evidence` array lengths; do not retain/render `name`, `rationale`, or evidence bodies. |
| `requestEcho` | Require an object for the current DTO shape, but do not inspect or retain its values. |

Unknown extra properties are ignored and never traversed. Incorrect required
types, missing required fields, invalid count values, or unsupported DTO shape
are `UNVERIFIED`; they are not coerced.

Known trust-tier enum values: `TRUSTED`, `HUMAN_AUTHORED`,
`TECHNICAL_EVIDENCE`, `SYSTEM_METADATA`. Missing/unknown tiers are counted in
an `UNKNOWN` bucket and make the projection partial. Do not infer trust tier
from freshness or source labels.

Known freshness statuses: `CURRENT`, `STALE`, `NO_BASELINE`, `UNKNOWN`,
`PARTIALLY_FRESH`. Missing/unrecognized freshness status is `UNKNOWN` and
partial; preserve the declared `STALE` status as stale, not as fabricated or
invalid evidence.

No confidence field is part of this DTO. The projection has no confidence
value or inferred score.

## Local view model

The only retained projection fields are:

| Field | Meaning |
|---|---|
| `state` | `AVAILABLE`, `PARTIAL`, `UNAVAILABLE`, or `UNVERIFIED`; `empty` separately identifies a verified zero-evidence result. |
| `reason` | Fixed allowlisted reason code, never raw error text. |
| `provenance` | Constant `OBSERVED_TOOL_RESULT`; allowlisted tool name; native `hostCallId`; local scope key for server/location/session. |
| `freshness` | Validated status enum, else `UNKNOWN`. Kept separate from evidence counts. |
| `repositoryRevision`, `contextRevision` | Optional 40- or 64-character hexadecimal commit revisions; invalid values are omitted and set a partial indicator. Display may abbreviate to 12 characters. |
| `truncated` | `metadata.truncated`, plus `true` when the result has the allowlisted `RESPONSE_SIZE_TRUNCATED` warning. Missing value is unknown/partial, not false. |
| `truncationUnknown` | True when the bounded warning scan cannot establish whether a truncation warning exists; state is partial. |
| `evidenceCountsByTrustTier` | Counts from actual bounded `evidence[].trustTier` values, with `UNKNOWN` for missing/unknown. At most five fixed buckets. |
| `sectionCountsByTrustTier` | Evidence-item counts grouped by the enclosing recognized section trust tier; at most four fixed buckets. |
| `reportedCounts` | Candidate, selected, rejected counts from metadata. |
| `countConsistency` | `CONSISTENT`, `MISMATCH`, or `UNKNOWN`; compare declared `selectedCount` with observed evidence count and flattened evidence counts with per-section evidence counts only when the result is not marked truncated and arrays are complete. Never repair a mismatch. |
| `diagnostics` | At most eight warning codes from the explicit allowlist below; unknown warning values are represented only by `OTHER_WARNING_PRESENT`. No messages. |

Section counts preserve the DTO's four known tiers and count evidence items
within those sections; they display only fixed labels:
Trusted knowledge, Human-authored context, Technical evidence, and System
metadata. Evidence counts are by trust tier, not by an inferred source label;
do not display free-form `sourceType` values or treat these buckets as
freshness/confidence.

The implemented view presents evidence counts by trust tier and section
evidence counts. It has no dedicated Sources section. This is intentional:
free-form source details are outside this projection contract and are not
required to complete the supported view.

The warning allowlist is:
`RESPONSE_SIZE_TRUNCATED`, `PROJECT_CONTEXT_STALE`,
`PROJECT_CONTEXT_PARTIALLY_FRESH`, `REPOSITORY_CONTEXT_BUDGET_APPLIED`,
`EVIDENCE_SUMMARY_TRUNCATED`, `CONTENT_ENRICHMENT_TRUNCATED`,
`CONTENT_ENRICHMENT_UNAVAILABLE`, `CONTENT_ENRICHMENT_SKIPPED`,
`CONTENT_ENRICHMENT_LIMIT_APPLIED`, and `CONTENT_BUDGET_EXHAUSTED`.
Keep only these exact codes and cap display to eight; map any other non-empty
warning to `OTHER_WARNING_PRESENT`.

## Bounds and non-retention

- One JSON text block, maximum 25,000 UTF-16 code units; reject above the cap.
- Do not traverse recursively. Inspect only the DTO's fixed known paths.
- At most 40 messages, 80 parts/message, 8 result blocks (eligibility accepts
  exactly one), 128 characters for a native host ID, and 64 characters for
  each validated revision.
- At most four recognized trust tiers/sections, five count buckets including
  `UNKNOWN`, eight diagnostic codes, and 1,000 evidence entries counted in
  each flat/section evidence array. If an array exceeds this cap, mark its
  counts incomplete/partial; never present a capped count as a total.
- Non-negative metadata counts must be safe integers no greater than 1,000,000;
  larger or non-integral values make the result unverified.
- Project only counts and fixed enums; never retain evidence rows. Do not store
  raw JSON, prompt/intent, requestEcho values, evidence strings/IDs/content,
  arbitrary warnings/errors, credentials, or tokens in Companion-owned state,
  logs, storage, or UI.
- The native transcript remains host-owned. The Companion holds only the
  sanitized projection plus its scoped native selection key.

## Lifecycle and selected state

Selection key is the tuple `(server/location scope, session ID, native host
call ID)`. It is usable only when unique in the current bounded transcript.
No synthetic operation ID is created. Recompute projections from the current
transcript when the existing native invalidation fires. If the selected call
disappears, changes result/eligibility, becomes duplicated, or its route scope
changes, clear selection and projection. Panel close/unmount and plugin
disposal release local state and subscriptions. Never carry selection or
projection into another session, server, location, or reopened panel.

The Context view may expose a picker over the already-derived bounded History
rows. Include only direct rows that pass the same native result eligibility
checks; project each choice to the fixed tool label, native call ID, and
optional observed time. Do not copy result payloads or add a transcript/backend
fetch. History and Context selection actions address the same scoped native
selection.

## Required user-visible states

- **Available:** verified projection from the selected observed result.
- **Partial:** valid structure but missing/unknown fields, count mismatch, or
  truncation; explain that the view is incomplete.
- **Unavailable:** no selected result or source no longer available.
- **Unverified:** unsupported/malformed result, no unique native identity, or
  unsupported contract. Do not present this as an empty context.
- **Empty:** a verified, non-truncated context with zero evidence and zero
  section counts. Keep distinct from unavailable and partial.

Every state keeps the “observed result, not backend Context Trace” provenance
notice.

## Deferred capability

Full backend Context Trace inspection is deferred. This projection does not
call REST/MCP, access credentials, infer `aiTaskId` joins, or assert an exact
backend invocation trace.

Code Mode context inspection is deferred by owner decision. The owner uses
Code Mode as the primary workflow, but the examined native GUI interfaces
provide no reliable access to the exact nested MCP result, individual identity,
and provenance. The outer `execute` result and assistant-generated text are not
accepted as the nested result. Reconsider only when a reliable, proportionate
native data-access mechanism is available and approved. Direct MCP inspection
remains a supported secondary capability.

Full Claude Context Inspector parity has not been achieved. Backend Context
Trace retrieval remains separately deferred and is not implemented here.
