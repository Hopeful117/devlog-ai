# Story 0090 — search_project_history

## Status

**Done** (implemented on `story/0090-search-project-history`;
see `implementation-report.md` and `engineering-report.md`)

## Objective

Give MCP clients a deterministic tool to answer historical questions that
produced class-A Git fallbacks during the V1 real-usage evaluation
(`docs/investigations/mcp-v1-real-usage-journal.md`):

```text
"What commit fixed Markdown rendering?"   → was: git log --grep
"When was RepositoryContextEngine introduced?" → was: git log --follow
```

```text
search_project_history(projectSlug, query[, limit])
        ↓ matching commits (sha, message, date, matches, relevance)
        ↓ resource = devlog://projects/{slug}/commits/{sha}
resources/read → CommitDiffAnalysisContext
```

The tool **discovers**; the commit-context Resource **inspects**.

## Problem

All project history is already imported (`ProjectCommit`, `CommitParent`,
`ChangedFile`) but nothing can query it by message or path. Two real tasks in
two categories required `git log` fallbacks — repeated, multi-category,
reconstructing information DevLog already owns.

## Resolution (summary)

- New read-only backend capability in the existing `history` module:
  deterministic token search over already-imported commits (subject,
  full message, changed paths), AND-semantics across query terms, exact-only
  field matches, no AI, no new index infrastructure.
- Exposed as a REST endpoint next to the existing project-history endpoints;
  consumed by a new mcp-server tool following the 0088 adapter pattern.
- Results carry `matchedOn`/`matchedValue` explanations, a documented
  deterministic relevance, and the commit-context resource URI via
  `DevlogResourceUriFactory` (Story 0089 single source of truth).
- Bounded results (default/max limit), strict project isolation, clean MCP
  errors for invalid input.

## Explicit non-goals

No semantic/vector/AI search, no boolean query language, no temporal
expressions, no `git log --follow` reconstruction (rename tracking not in
data), no timeline/freshness/relations resources, no changes to
`get_engineering_context`, ranking, precision policy or profiles. "Concept"
in V1 means: term present in a commit message, a changed path, or an exact
file name — documented as such, not semantic search.

## Acceptance criteria

1. Real scenario A: searching for the Markdown fix semantics finds the fix
   commit without `git log --grep`.
2. Real scenario B: searching `RepositoryContextEngine` finds its
   introduction/evolution commits without `git log --follow`.
3. Every result exposes its commit-context resource URI; SHA ⇄ resource match
   verified end-to-end (`search → resource → resources/read`).
4. An old highly-relevant commit ranks above a recent weak match.
5. Strict project isolation; bounded output; invalid inputs → clean MCP
   errors (no 500).
6. Full quality pipeline green.

## References

- Evaluation journal (evidence base), Stories 0087–0089, ADR-038/056/057.
