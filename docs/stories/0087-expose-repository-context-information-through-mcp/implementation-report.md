# Story 0087 — Implementation Report

## Changes made

### devlog-contracts (additive)

- `EngineeringEvidence`: + `occurredAt` (Instant), `relatedReferences`
  (List<String>), `extractionMetadata` (Map<String,String>), `content`,
  `symbols`. Compact constructor normalizes nulls to empty collections.
- New records: `EngineeringEvidenceContent(status,text,reason,revision)`,
  `EngineeringEvidenceSymbols(status,truncated,returnedSymbolCount,
  availableSymbolCount,extractorId,extractorVersion,revision,declarations[])`,
  `EngineeringSymbolDeclaration`, `EngineeringSymbolParameter`,
  `EngineeringSymbolLocation`. Statuses mirror the internal enums by name
  (no second taxonomy).
- `EngineeringContextMetadata`: + `warnings` (List<String>, normalized to empty).

### backend

- `EngineeringContextContractMapper`: maps the five new evidence fields
  (null-safe for content/symbols), and `RepositoryContext.warnings()` into
  metadata. No other behavior change.
- `ProjectKnowledgeContextCollector` **v1 → v2**:
  - validated Engineering Events emitted as
    `layer=GIT_HISTORY, kind=ENGINEERING_EVENT, reference=event:{uuid}`,
    summary `title — summary`, `occurredAt=occurredAt`,
    `relatedReferences=[git:{sourceId}:{baseCommit}, git:{sourceId}:{targetCommit}]`
    when present, metadata `category/baseCommit/targetCommit/proposalId`.
  - open Challenges emitted as `layer=ROADMAP, kind=CHALLENGE,
    reference=challenge:{uuid}`, metadata `status/impact`.
  - Both use the existing `EvidenceFactory` bounds (summary truncation) and
    sourceType `CORE_KNOWLEDGE`.

No ranking/selection/enrichment algorithm was modified.

## G1/G2 resolution

| Gap | Resolution |
|---|---|
| G1 — contract drops content/symbols/timestamps/metadata/warnings | Contract + mapper now expose all of them; values are the exact internal ones (`occurredAt` business timestamp, `resolvedRevision` in extractionMetadata, engine warning codes). Nothing is invented: absent information stays `null`/empty. |
| G2 — loaded-but-never-emitted collections | EngineeringEvents + Challenges → **A**: emitted as evidence (collector v2). KnowledgeRelations → **C** (future Resource; flat emission would be noise and relation traversal belongs to future retrieval). ValidatedProposals → **D** in this path (lineage intermediaries; promoted artifacts already exposed). KnowledgeEvents (not in mission list) → **C** (current-state/timeline resource candidate). DeterministicKnowledgeContextCollector → **B**: not dead globally — it serves the AI workflow (`KnowledgeSelectionServiceImpl.build`) where facts/observations exist; it is inert on the MCP path because no Analysis is persisted there, which is correct. |

## Newly exposed information

Verified end-to-end against the running stack (devlog-ai project):

- Every evidence item now carries `occurredAt` (60/60), `relatedReferences`
  (59/60 — commits carry parent refs, etc.), `extractionMetadata` incl.
  `resolvedRevision=3cd37232…` on repository-structure items.
- File content enrichment visible: `content.status=TRUNCATED`, bounded text,
  `reason`, pinned `revision`.
- Java symbols visible: 12 declarations with kind/name/location for a selected
  test file.
- `metadata.warnings = [REPOSITORY_CONTEXT_BUDGET_APPLIED,
  CONTENT_ENRICHMENT_TRUNCATED]`.
- A Challenge created through the legitimate REST API surfaced as CHALLENGE
  evidence (`relevanceScore=81`, `SELECTED_BY_RANK`) once lexically related to
  the intent; an unrelated challenge was correctly filtered by the precision
  policy (score < 35) — engine behavior preserved, not a mapping loss.

## Intentionally not exposed

See table above (Relations=C, Proposals=D, KnowledgeEvents=C,
facts/observations=B, per-criterion scores/diagnostics/rejected-decisions kept
internal until a consumer need is demonstrated).

## Compatibility

Additive JSON evolution: all V1 fields keep name/order/type. New fields are
appended; unenriched items serialize `content:null` / `symbols:null`.
Frontend does not consume this contract (grep verified). The record
constructor signature changed, which required updating in-repo test fixtures
only. Digest semantics unchanged (computed before mapping, over the internal
result); its value changes only because the internal result legitimately
changed (new collector output, collectorVersion v2).

## Tests

- Updated: `EngineeringContextContractMapperTest` (+2 cases: full enrichment
  mapping incl. symbols/content/metadata/warnings; clean absence case),
  `ProjectKnowledgeContextCollectorTest` (version v2, event emission with git
  refs/metadata, null-commit clean absence, challenge emission),
  `EngineeringContextControllerWebMvcTest` (JSON assertions for occurredAt,
  relatedReferences, extractionMetadata, warnings, content/symbols absence),
  mcp-server `EngineeringContextToolUnitTest` (JSON contains timestamps,
  references, metadata, warnings).
- Full quality pipeline: `./backend/mvnw -pl backend -am clean verify -B` →
  **856 tests, 0 failures**, JaCoCo coverage checks met. mcp-server suite:
  8/8.

## Resource candidates discovered

See `docs/mcp-resource-candidates.md` (projects list, decision, insight,
story, engineering event, commit context, timeline, freshness, relations —
each with stable identifier and existing service confirmed in code).

## Remaining issues

1. Missing `intent` query parameter still yields HTTP 500 instead of 400
   (pre-existing, out of scope).
2. `InsightRepository.findByProjectIdAndStatusInOrderByCreatedAtDescIdDesc`
   used by `RepositoryContextAdapter` is unbounded (pre-existing).
3. `get_engineering_context` triggers live Git synchronization of the shared
   workspace via `RepositoryStructureCollector`/enrichers (latency/network on
   a nominally read-only operation) — pre-existing, documented in audit §G7.
4. Current dataset contains 0 EngineeringEvents (governed workflow not yet run
   here); their emission is covered by unit tests, not observable live.
