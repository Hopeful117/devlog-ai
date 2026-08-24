# Story 0090 — Repository Analysis

## Historical data actually available in DevLog (verified on main @ efda19f)

| Information | Available? | Where | Queryable today? | Existing service/repo query |
|---|---|---|---|---|
| commit SHA | ✅ | `ProjectCommit.commitHash` (unique per source) | by hash/source only | `ProjectCommitRepository.findBySourceIdAndCommitHash` |
| subject + full message | ✅ | `subject`, `fullMessage` (TEXT) | ❌ no text query exists | loaded via `findByProjectIdOrderByCommittedAtAscCommitHashAsc` |
| author | ✅ | `authorName`, `authorEmail` | ❌ | same |
| timestamps | ✅ | `authoredAt`, `committedAt` (not null) | range only (`CommittedAtAfter`) | id. |
| changed paths (old/new) + change type + per-file +/- | ✅ | `ChangedFile` children (`@OneToMany`, lazy) | ❌ no path query | entity graph / lazy access inside transaction |
| parents / merge flags | ✅ | `CommitParent`, `root/mergeCommit` | ❌ | — |
| diff content | ❌ | not imported (only numstat metadata) | — | would require reconstruction from workspace → out of scope |
| symbols/Java declarations | ⚠️ partial | extracted on-demand by `SelectedJavaSymbolEnricher` at context time, not persisted | ❌ | out of scope V1 |
| engineering events ↔ commits | ✅ | `EngineeringEvent.baseCommit/targetCommit` (+ proposal lineage) | by event, not reverse lookup | out of scope here |
| ADR/story relationships to commits | ⚠️ heuristic only | story base/targetCommit fields; co-change detection in `CommitDiffContextBuilder` | ❌ | — |
| repository source | ✅ | `Source` (project-scoped), `commit.source.id` | scoped queries exist | — |

Conclusion: **message search and path search are fully supported by already
imported data**; both real fallback scenarios (Markdown fix = message; `git
log --follow` ≈ path+symbol via file name) are satisfiable without new
infrastructure. Diff-content and rename-following are documented limitations.

## Existing building blocks

- `ProjectHistoryService.getProjectHistory(projectId)` already loads the full
  project commit list (`findByProjectIdOrderByCommittedAtAscCommitHashAsc`,
  `@Transactional(readOnly=true)`) → project-scoped by construction
  (isolation §17 for free). Volume bounded by project size; DB-level LIKE
  optimization possible later (documented limitation, not built now).
- REST surface: `ProjectHistoryController`
  (`/api/v1/project-history/projects/{projectId}/commits…`) → new search
  endpoint lives naturally beside it (option B: backend capability + MCP
  adapter; business logic stays in DevLog, not mcp-server).
- Resource URI: `DevlogResourceUriFactory.commit(slug, sha)` (Story 0089).
- MCP error conventions: `ResourceSupport.notFound/invalidParams` (0088).

## Ranking design (deterministic)

Per query term (tokenized `[a-z0-9]+`, ≥2 chars, lower-cased), a commit is a
candidate only if **every** term matches somewhere (AND semantics,
git-grep-like). Per term the strongest field wins:

```text
FILENAME_EXACT  = 30   (term == changed file name, case-insensitive)
PATH            = 20   (term contained in a changed path)
SUBJECT         = 15   (term contained in subject)
MESSAGE         = 10   (term contained in full message body)
```

relevance = Σ per-term strengths → commits matching more terms (or more
strongly) rank first. Recency is only the final tie-breaker (committedAt
desc, then commitHash asc) — an old exact match outranks a recent weak one
(anti-recency-bias requirement §11). All weights centralized as named
constants in the ranking implementation.

## Result shape

Compact discovery contract (no diffs — inspection belongs to the resource):
sha, subject, author, committedAt, repositoryId (source disambiguation §18),
matches[] (`matchedOn`: COMMIT_MESSAGE|PATH, `matchedValue` excerpt ≤120
chars, deduplicated, bounded per commit), relevance, and `resource` URI built
with `DevlogResourceUriFactory.commit`.
