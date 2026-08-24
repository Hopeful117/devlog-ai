# Story 0090 — Implementation Plan

- **Branch**: `story/0090-search-project-history` (from `main` @ efda19f —
  contains merged 0087–0089; the evaluation journal lives on its own
  `evaluation/mcp-v1-real-usage` branch and is quoted in repository-analysis).

## 1. devlog-contracts

New package `com.hopeful117.devlogai.contracts.projecthistory`:

```java
ProjectHistorySearchResult(String projectSlug, String query, int totalMatches,
        boolean truncated, List<ProjectHistoryCommitMatch> results)
ProjectHistoryCommitMatch(String commitSha, String subject, String authorName,
        Instant committedAt, UUID repositoryId, int relevance,
        List<ProjectHistoryMatch> matches, String resource)
ProjectHistoryMatch(ProjectHistoryMatchedOn matchedOn, String matchedValue)
enum ProjectHistoryMatchedOn { COMMIT_MESSAGE, PATH }
```

## 2. backend (history module)

- `ProjectHistorySearchService.search(projectId, query, Integer limit)`
  (+ Impl, `@Transactional(readOnly=true)`):
  - validation: query blank → `InvalidParameterException`; limit outside
    [1,100] → `InvalidParameterException`; default limit = 20;
  - loads project commits via existing repository query; tokenizes; AND-match
    across terms over subject/fullMessage/paths;
  - ranking per repository-analysis weights; ties: committedAt desc,
    commitHash asc;
  - builds contract incl. `DevlogResourceUriFactory.commit` URIs. Slug needed
    for URI → resolve once via `ProjectService.getBySlugId`? No: service takes
    the slug-resolved projectId from controller; controller also passes the
    slug? Simplest: controller endpoint is id-based (consistent with sibling
    endpoints); MCP resolves slug→projectId already and knows the slug for the
    resource URI… but the URI must be built backend-side to keep one source of
    truth in the payload. Decision: controller accepts `{projectId}` + query +
    limit and returns results with resource built from the **project slug**
    resolved internally via `ProjectService.getBySlugId`-style lookup
    (`projectRepository.findById`) — read-only, no extra HTTP hop from MCP.
- `ProjectHistoryController`: `GET
  /api/v1/project-history/projects/{projectId}/commits/search?query=&limit=`
  returning the contract.

## 3. mcp-server

- `DevlogResourceClient`: `searchCommits(projectId, query, limit)` GET.
- New tool `SearchProjectHistoryTool`:
  `search_project_history(projectSlug, query, limit?)` — required slug/query,
  optional limit (default 20, bounds validated client-side too);
  errors via `ResourceSupport` conventions (unknown project → notFound;
  blank/short query or bad limit → invalidParams); passthrough JSON.

## 4. Tests

- Backend: service unit tests (mock repository) covering §27 matrix — exact/
  partial/case-insensitive message, path & filename-exact, multi-term AND,
  dedup+multi-matches, old-relevant-beats-recent-weak, isolation by
  construction, limit/truncation/totalMatches, blank query, bad limit, empty
  result; controller WebMvc test (200 shape + 400s).
- mcp-server: tool tests (passthrough, empty result, unknown project,
  invalid query, invalid limit) + registration smoke.

## 5. Validation

Full pipeline; rebuild backend container; live stdio session replaying both
real scenarios without git, then `resources/read` on a result.resource and
SHA ⇄ resource equality check. BEFORE/AFTER comparison documented in the
implementation report.

## 6. Closure

implementation-report.md, engineering-report.md, MCP tools documentation
(`docs/mcp-tools.md`), granular commits, branch left unmerged.
