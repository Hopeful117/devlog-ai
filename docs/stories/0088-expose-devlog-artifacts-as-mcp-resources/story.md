# Story 0088 — Expose DevLog artifacts as MCP Resources

## Status

**Done** (implemented on `story/0088-expose-devlog-artifacts-as-mcp-resources`;
see `implementation-report.md` and `engineering-report.md`)

## Objective

Let an MCP client that already knows the identity of a DevLog artifact read it
directly, without triggering context construction through
`get_engineering_context`.

```text
Tool  get_engineering_context -> discovery / selection / ranking for a task
Resource                      -> deterministic, addressable read of a known artifact
```

This story implements MCP Resources V1 for the artifact candidates confirmed by
`docs/mcp-server-audit.md` §13 and `docs/mcp-resource-candidates.md`:

```text
projects · decision · insight · story · engineering-event · commit-context
```

## Problem

After Story 0087, `get_engineering_context` exposes evidence with stable
identifiers (`decision:{uuid}`, `insight:{uuid}`, `story:{uuid}`,
`event:{uuid}`, `git:{sourceId}:{sha}`, `diff:{sha}:{path}`), but a consumer
holding such an identifier has no MCP-native way to retrieve the full trusted
artifact. It must fall back to REST or re-run context construction.

## Resolution (summary)

Add six MCP Resources to the `mcp-server` module, acting as a thin access
facade over existing backend application services (via the same HTTP adapter
pattern already used for tools/resources). No new business logic, no new
repository access, no ranking change.

- URI scheme: `devlog://projects/{projectSlug}/…`, slug-based to stay consistent
  with the existing `devlog://projects/{projectSlug}/context` resource and with
  DevLog's "stable human-readable integration identifier" principle.
- Project membership enforced for every artifact read (no global ambiguous
  lookup).
- Insights resolved through the project-scoped ACTIVE-only query so only
  trusted knowledge is ever returned as a resource.
- Not-found, invalid identifier and cross-project access surface as clean MCP
  errors (`io.modelcontextprotocol.spec.McpError`), never as raw 500s.
- Payloads are the existing backend response representations passed through
  (single source of truth); no second taxonomy is created.

## Scope

### IN SCOPE

- Resources: projects list, decision, insight, engineering story,
  engineering event, commit context.
- Resource templates for parameterized URIs (Spring AI `@McpResource`
  templates — SDK 2.0.0 already used by the server).
- Error behavior: unknown project/artifact, invalid identifier,
  cross-project isolation.
- Unit tests at the MCP boundary + manual stdio validation against the local
  stack.
- Identifier mapping documentation
  (`get_engineering_context` identifiers → resource URIs).

### OUT OF SCOPE (deferred)

- `timeline`, `freshness`, `relations` resources (projection/view oriented).
- Injecting resource URIs into the `get_engineering_context` contract
  (next story candidate).
- `search_project_history`, `explainDecision`, prompts, new context profiles.
- Any ranking/precision-policy change in `RepositoryContextEngine`.
- Fixing pre-existing tracked `devlog-contracts/target/` hygiene.

## Acceptance criteria

1. `resources/list` exposes the six new resources/templates alongside the
   existing ones.
2. A client can discover projects, then read a known decision, insight, story,
   engineering event and commit context by URI.
3. Unknown project, unknown artifact, malformed UUID/SHA and cross-project
   identifiers produce clean MCP errors with explicit messages.
4. An insight superseded/archived (non-ACTIVE) is never returned.
5. Existing tool/resource behavior unchanged; full quality pipeline green.
6. Manual validation documents one real navigation:
   projects → devlog-ai → known artifact → expected representation, plus one
   correspondence with an actual `get_engineering_context` evidence identifier.

## References

- `docs/mcp-server-audit.md` (§13 candidates, §14 relationship)
- `docs/mcp-resource-candidates.md`
- `docs/mcp-engineering-context-contract.md`
- `docs/engineering-context-v1.md`, `docs/mcp-architecture-context.md`
- ADR-038 (Repository Context Engine), ADR-056/057 (MCP boundaries & capability
  model)
- Story 0087 (evidence enrichment enabling identifier navigation)
