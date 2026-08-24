# Story 0088 — Implementation Plan

- **Branch**: `story/0088-expose-devlog-artifacts-as-mcp-resources` (based on
  `story/0087-expose-repository-context-information-through-mcp`)
- **Classification**: facade-only — reuses existing application services through
  the established HTTP adapter; zero new business logic.

## 1. HTTP client

New `DevlogResourceClient` (`@HttpExchange("/api/v1")`) in mcp-server, exposing
only the read endpoints identified in repository-analysis:

```java
listProjects()                                  GET /projects
getProjectBySlug(slug)                          GET /projects/{slug}
listInsights(projectId)                         GET /insights/project/{projectId}
getDecision(decisionId)                         GET /decisions/{decisionId}
getEngineeringEvent(eventId)                    GET /engineering-events/{eventId}
getStory(projectId, storyId)                    GET /projects/{projectId}/stories/{storyId}
listSources(projectId)                          GET /sources/project/{projectId}
getCommitContext(repositoryId, commitHash)      GET /project-history/repositories/{repositoryId}/commits/{commitHash}/context
```

All return `String` (JSON passthrough) except where membership checks need a
parsed field. Registered alongside the existing client in
`DevlogBackendClientConfiguration`.

## 2. Resources (package `hopefull117.devlogai_mcp.mcp_server.resource`)

| Class | URI template | Behavior |
|---|---|---|
| `ProjectsResource` | `devlog://projects` | passthrough of projects list |
| `ProjectArtifactResources` (grouped by artifact kind) | `devlog://projects/{projectSlug}/decisions/{decisionId}` etc. | resolve slug→project, read artifact via scoped endpoint or membership-checked global endpoint |

Common helper `ResourceSupport`: slug→project resolution (parse `id` from
`ProjectResponse`), UUID/SHA validation (UUID format; SHA = 40 or 64 hex),
`projectId` membership assertion (tree extraction), active-source selection
(first active source ordered by createdAt,id — engine rule), error mapping:

- unknown project/artifact → `McpError("... not found ...")`
- cross-project identifier → `McpError("... does not belong to project ...")`
- invalid identifier → `McpError("Invalid ... identifier ...")`
- backend connectivity errors propagate as MCP transport errors (existing
  behavior).

## 3. Governance rules

- Insights: resolved exclusively via ACTIVE-only project query → non-ACTIVE
  insights are indistinguishable from absent ones (never served).
- Stories: server-side ownership check reused.
- Commits: source-scoped context lookup; the active source belongs to the
  requested project by construction.
- Decisions/Events: payload `projectId` must equal resolved project id.

## 4. Tests (mcp-server)

Per resource: happy path JSON passthrough assertions + not-found + isolation +
invalid identifier (unit tests with mocked client, following
`ProjectContextResourceTest` style). Commit SHA validation cases. No backend
changes ⇒ no backend test impact beyond pipeline greenness.

## 5. Validation

1. Full quality pipeline (`./backend/mvnw -pl backend -am clean verify -B`) +
   mcp-server suite.
2. Manual stdio MCP session against locally built server + running stack:
   initialize → resources/list → resources/read for each artifact on
   `devlog-ai`, including one decision identifier taken from a real
   `get_engineering_context` response.

## 6. Story closure

Update `story.md` status, add `implementation-report.md` and
`engineering-report.md`; granular commits; leave branch unmerged for review.
