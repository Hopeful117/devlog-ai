# Story 0089 — Resource references from get_engineering_context

## Status

**Done** (implemented on `story/0089-resource-references-from-engineering-context`;
see `implementation-report.md` and `engineering-report.md`)

## Objective

When an `EngineeringEvidence` returned by `get_engineering_context` corresponds
exactly to an artifact exposed as an MCP Resource (Story 0088), expose the
resource URI directly on the evidence:

```text
get_engineering_context
        ↓
EngineeringEvidence
        └── resource = devlog://projects/{projectSlug}/decisions/{uuid}
                        ↓
                 resources/read
                        ↓
                 full trusted artifact
```

Small, additive, deterministic. Zero new retrieval intelligence.

## Problem

Stories 0087/0088 delivered two complementary surfaces, but a client holding
an evidence must still reconstruct resource URIs itself using undocumented
conventions. The navigation should be carried by the payload.

## Resolution (summary)

1. Add optional `resource` field (String, nullable) at the end of
   `EngineeringEvidence` (additive).
2. Centralize URI construction in a single deterministic source of truth,
   `DevlogResourceUriFactory`, living in **devlog-contracts** so the backend
   mapper and the mcp-server share one definition without coupling contracts
   to MCP runtime details (pure string patterns, no I/O).
3. Map only exact semantic correspondences (see matrix in
   repository-analysis.md); everything else keeps `resource = null`.
4. Compute URIs in `EngineeringContextContractMapper` — strictly after the
   deterministic engine result; engine behavior untouched (digest unchanged).

## Scope

### IN SCOPE

- Contract field + factory + mapper wiring + slug propagation.
- Direct mappings: Decision, Insight (ACTIVE-only consistent), Engineering
  Story, Engineering Event, Commit (from internal reference).
- Explicit non-mappings: Diff (Case B), Challenge, Milestone, Artifact,
  Analysis, repository structure items, Facts/Observations.
- Tests: factory, mapper, contract serialization, mcp-server ↔ factory sync,
  live stdio end-to-end (`evidence.resource` → `resources/read`).

### OUT OF SCOPE

- New resources (challenge/repository-structure/diff/timeline/freshness/
  relations), new tools/prompts/profiles, ranking or precision changes,
  intent-profile fix, global `target/` cleanup, enriching
  `relatedReferences`.

## Acceptance criteria

1. An addressable evidence serializes `"resource": "devlog://…"`; others
   serialize `null`; absence is normal, never a warning/error.
2. All 0087 fields remain intact and unchanged.
3. The engine digest semantics are unchanged (URI added after the engine).
4. A live stdio session completes:
   `get_engineering_context → evidence.resource → resources/read → artifact`
   without any client-side transformation.
5. Full quality pipeline green.

## References

- Stories 0087/0088 (+ reports), `docs/mcp-engineering-context-contract.md`,
  `docs/mcp-resource-candidates.md`, ADR-038/056/057.
