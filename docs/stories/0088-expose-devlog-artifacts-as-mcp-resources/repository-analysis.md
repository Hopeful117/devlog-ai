# Story 0088 — Repository Analysis

Read-only reconnaissance performed before implementation (source of truth:
repository code on branch `story/0088-expose-devlog-artifacts-as-mcp-resources`).

## Existing MCP surface (mcp-server)

- Tools: `get_engineering_context`, `echo_message`; Resources:
  `devlog://server/info` (static), `devlog://projects/{projectSlug}/context`
  (**template already proves URI-template support** in Spring AI MCP 2.0.0);
  Prompt: `explain_code`. Transport: stdio SYNC, logs to stderr.
- Backend access exclusively through HTTP adapter
  (`DevlogProjectContextClient`, `@HttpExchange` + `RestClient`).
- Error handling today: none at MCP layer (exceptions propagate). MCP SDK
  provides `io.modelcontextprotocol.spec.McpError` (mcp-core 2.0.0) for clean
  JSON-RPC errors.

## Application services / REST endpoints confirmed per artifact

| Artifact | Endpoint (existing) | Response representation | Project isolation |
|---|---|---|---|
| Projects list | `GET /api/v1/projects` (`ProjectController.getAll`) | `ProjectResponse{id,name,slug,description,status,createdAt,updatedAt}` | n/a |
| Project by slug | `GET /api/v1/projects/{slug}` | `ProjectResponse` | n/a (entry point) |
| Decision | `GET /api/v1/decisions/{id}` (`DecisionController.getById` → `DecisionService.getById`) | `DecisionResponse{id,projectId,title,context,choice,rationale,consequences,createdAt,updatedAt}` | payload carries `projectId`; global lookup → membership must be enforced by caller |
| Insight | `GET /api/v1/insights/project/{projectId}` (`InsightController.getByProject`) | `List<InsightResponse>` — **ACTIVE-only by service rule** (`InsightServiceImpl.getByProject`) | server-side scoped |
| Engineering Story | `GET /api/v1/projects/{projectId}/stories/{storyId}` (`EngineeringStoryController.getById` → `storyService.getById(storyId, projectId)`) | `EngineeringStoryResponse{id,projectId,storyNumber,title,storyPath,baseCommit,targetCommit,status,createdAt,updatedAt,completedAt}` | **enforced server-side** |
| Engineering Event | `GET /api/v1/engineering-events/{id}` (`EngineeringEventController` → `EngineeringEventQueryService.get`) | `EngineeringEventResponse{version,id,projectId,analysisId,proposalId,validationId,sourceId,category,title,summary,significance,baseCommit,targetCommit,comparisonPolicy,mergeCommit,occurredAt,createdAt,confidence,supportingFactIds,supportingObservationIds,evidenceReferences}` | payload carries `projectId`; membership enforced by caller |
| Commit context | `GET /api/v1/project-history/repositories/{repositoryId}/commits/{sha}/context` (`ProjectHistoryController` → `ProjectHistoryServiceImpl.getCommitContext`) | `CommitDiffAnalysisContext` (bounded deterministic diff view: classification, candidate ADR/roadmap references, warnings) | lookup is source-scoped (`findBySourceIdAndCommitHash`) → cross-project SHA impossible once source resolved for the project |
| Sources of project | `GET /api/v1/sources/project/{projectId}` (`SourceController.getByProject`) | `SourceResponse{id,projectId,type,name,repositoryUrl,defaultBranch,provider,active,…}` | server-side scoped |

Notes:

- `getCommitContext(unknown sha)` throws `EntityNotFoundException("ProjectCommit",
  sha)` → backend 404; the MCP layer maps it to a clean error.
- Active-source resolution rule mirrors `RepositoryStructureCollector`
  (`findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc().getFirst()`), so the
  resource reads the same repository view the engine uses.
- No status field on `InsightResponse`; using the project-scoped ACTIVE-only
  list preserves governance (a SUPERSEDED/ARCHIVED insight can never be served)
  without backend changes.
- Payloads are passed through as-is (backend DTOs remain the single source of
  truth); only minimal JSON field extraction (`projectId`) is performed for
  membership checks where no scoped endpoint exists.

## Identifier correspondence (from Story 0087 evidence)

| Evidence identifier | Resource path |
|---|---|
| `decision:{uuid}` | `devlog://projects/{slug}/decisions/{uuid}` |
| `insight:{uuid}` | `devlog://projects/{slug}/insights/{uuid}` |
| `story:{uuid}` (+ metadata storyNumber/base/targetCommit) | `devlog://projects/{slug}/stories/{uuid}` |
| `event:{uuid}` (+ relatedReferences git refs) | `devlog://projects/{slug}/engineering-events/{uuid}` |
| `git:{sourceId}:{sha}` · `diff:{sha}:{path}` | `devlog://projects/{slug}/commits/{sha}` |

## Constraints respected

- No new business logic or persistence access in mcp-server (ADR-056/057).
- No ranking/profile/precision-policy change (ADR-038 untouched).
- `timeline`, `freshness`, `relations` deferred (projection/view oriented).
