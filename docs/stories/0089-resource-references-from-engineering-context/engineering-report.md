# Engineering Report — Story 0089

## Architecture Decisions Verified

### 1. Single source of truth for the URI space
`DevlogResourceUriFactory` (devlog-contracts) is the only place building
resource URIs. The mcp-server annotations keep literal templates (Java
annotation constants) and a reflection-based sync test fails the build if
either side drifts. Backend mapper and MCP server can no longer diverge
silently.

### 2. Contracts stay MCP-runtime free
The factory emits string patterns only; devlog-contracts gains a naming
convention, not an MCP dependency. The `resource` value travels as opaque
data for REST consumers.

### 3. Mapping strictly post-engine
URIs are resolved inside `EngineeringContextContractMapper`, after the
deterministic engine result. Collectors, ranking, precision policy, budget,
enrichment and digest are untouched — verified by unchanged engine tests and
by digest construction order inspection.

### 4. Exact-only, fail-safe resolution
Five exact correspondences mapped (decision/insight/story/event/commit).
Everything else — including malformed identifiers or unknown kinds — yields
`resource = null`. No fuzzy matching, no lookups, no I/O; slug comes from the
already-loaded snapshot.

### 5. Governance alignment
INSIGHT evidence originates from the ACTIVE-only query used since Story 0087;
the insight resource enforces the same rule at read time. Emitting the URI
therefore never widens knowledge access. Project isolation holds: the URI
embeds the current project slug and resource reads re-verify membership.

## Acceptance Criteria Review

1. ✅ Addressable evidence serializes `"resource":"devlog://…"`, others `null`.
2. ✅ All 0087 fields intact (mapper tests + serialization assertions).
3. ✅ Digest semantics unchanged.
4. ✅ Live stdio: verbatim URI navigation tool → evidence.resource →
   resources/read → full artifact (decision, commit, insight exercised).
5. ✅ Pipelines green.

## Out-of-scope observations

- Evidence selection varies per intent (some runs contain no DECISION
  evidence) — expected ranking behavior.
- Indirect navigation from CHANGED_FILE relatedReferences to commit resources
  remains possible future work (documented, intentionally not implemented).
