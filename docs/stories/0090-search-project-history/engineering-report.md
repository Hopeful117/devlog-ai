# Engineering Report — Story 0090

## Architecture Decisions Verified

### 1. Discovery vs inspection separation preserved
The tool returns compact, explained candidates (sha, subject, date, matches,
relevance, resource). No diffs, no classifications — `resources/read` on the
provided URI is the inspection path. Tool searches, Resource inspects.

### 2. Business logic lives in DevLog, not in MCP
Search semantics (tokenization, AND-matching, ranking) are implemented in the
backend `history` module behind a read-only application service and a REST
endpoint consistent with its siblings. mcp-server only resolves the project,
validates inputs and proxies JSON — same adapter pattern as Stories 0088/0089.

### 3. No new search infrastructure
Pure deterministic iteration over already-imported commits. No
Elasticsearch/vector/embeddings/LLM. Same-state queries are reproducible;
the ranking weights are named constants with a documented order of magnitude.

### 4. Anti-recency by construction
Relevance is purely textual/structural; recency appears only as final
tie-breaker — directly addressing the evaluation finding where an old pivotal
commit was invisible under `get_engineering_context`.

### 5. Single source of truth for URIs maintained
Result resources are built via `DevlogResourceUriFactory.commit`; a corrupted
SHA degrades to `resource = null` instead of failing the whole search.

### 6. Isolation and bounded output
Project scoping inherited from the existing repository query; unknown project
→ not-found; results hard-bounded (default 20 / max 100 / totalMatches +
truncated flags); matched values truncated at 120 chars, ≤8 per commit.

## Acceptance Criteria Review

1. ✅ Scenario A: fix commit found via MCP alone (no git log).
2. ✅ Scenario B: introduction + evolution chain found (no git log --follow).
3. ✅ resource ⇄ SHA equality verified live through resources/read.
4. ✅ old-relevant > recent-weak covered by dedicated test.
5. ✅ isolation + bounded output + clean errors tested (MCP + backend).
6. ✅ pipelines green (876 backend / 39 mcp-server).

## Notable implementation findings

- Spring AI: tool parameters require `@McpToolParam(required = false)` for
  optionality; `@McpArg(required = false)` on a tool method is ignored by the
  schema generator (`limit` became mandatory). Fixed during validation.
- RestClient `@RequestParam` needs explicit `required = false` when the value
  can be null (optional limit omitted from the HTTP call).
- FILENAME_EXACT compares the basename **without extension**, so searching
  `RepositoryContextEngine` matches `RepositoryContextEngine.java`.
