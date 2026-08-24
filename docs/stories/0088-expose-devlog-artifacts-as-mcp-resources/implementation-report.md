# Story 0088 — Implementation Report

## Branch

`story/0088-expose-devlog-artifacts-as-mcp-resources`, based on
`story/0087-expose-repository-context-information-through-mcp`. Not merged to
main (left for human review).

## Resources implemented

All resources live in `hopefull117.devlogai_mcp.mcp_server.resource`, backed by
a new read-only HTTP facade `DevlogResourceClient` (`@HttpExchange("/api/v1")`)
registered in `DevlogBackendClientConfiguration`. Shared resolution/validation/
error logic in `ResourceSupport`.

| Resource | URI / template | Identifier | Application service reused | Returned contract |
|---|---|---|---|---|
| `projects` | `devlog://projects` | — | `GET /api/v1/projects` → `ProjectController.getAll` | passthrough `ProjectResponse[]` |
| `project-decision` | `devlog://projects/{projectSlug}/decisions/{decisionId}` | decision UUID (= evidence `identifier`) | `GET /api/v1/decisions/{id}` → `DecisionService.getById` | passthrough `DecisionResponse`, membership enforced on payload `projectId` |
| `project-insight` | `devlog://projects/{projectSlug}/insights/{insightId}` | insight UUID | `GET /api/v1/insights/project/{projectId}` → `InsightServiceImpl.getByProject` (**ACTIVE-only**) | matched element of `InsightResponse[]` |
| `project-story` | `devlog://projects/{projectSlug}/stories/{storyId}` | story UUID | `GET /api/v1/projects/{projectId}/stories/{storyId}` → `EngineeringStoryService.getById(storyId, projectId)` | passthrough `EngineeringStoryResponse` |
| `project-engineering-event` | `devlog://projects/{projectSlug}/engineering-events/{eventId}` | event UUID | `GET /api/v1/engineering-events/{id}` → `EngineeringEventQueryService.get` | passthrough `EngineeringEventResponse`, membership enforced |
| `project-commit-context` | `devlog://projects/{projectSlug}/commits/{commitSha}` | full SHA (40/64 hex) | `GET /api/v1/sources/project/{projectId}` (active source selection, same ordering rule as `RepositoryStructureCollector`) then `GET /api/v1/project-history/repositories/{repositoryId}/commits/{sha}/context` → `ProjectHistoryServiceImpl.getCommitContext` | passthrough `CommitDiffAnalysisContext` |

No backend code was modified in this story. No new business logic, no
persistence access from MCP (ADR-056/057 boundaries respected).

## URI scheme

Retained `devlog://projects/{projectSlug}/…` (slug, not internal UUID):

- consistent with the existing `devlog://projects/{projectSlug}/context`
  resource;
- DevLog documents the slug as the stable human-readable integration
  identifier (`docs/engineering-context-v1.md`) while UUIDs remain an
  implementation detail;
- artifact identifiers are the exact UUIDs/SHAs exposed by
  `get_engineering_context` evidence.

Discovery split observed on this SDK (java-sdk 2.0.0 / Spring AI 2.0.0):
static resources via `resources/list`; URI templates via
`resources/templates/list` (verified live: 2 resources + 6 templates).

## Identifier mapping (get_engineering_context → Resource)

| Evidence field value | Resource path |
|---|---|
| `identifier` = `<decision-uuid>` (kind DECISION) | `devlog://projects/{slug}/decisions/<uuid>` |
| `identifier` = `<insight-uuid>` (kind INSIGHT) | `devlog://projects/{slug}/insights/<uuid>` |
| `identifier` = `<story-uuid>` (kind ENGINEERING_STORY) | `devlog://projects/{slug}/stories/<uuid>` |
| `identifier` = `<event-uuid>` (kind ENGINEERING_EVENT) | `devlog://projects/{slug}/engineering-events/<uuid>` |
| `relatedReferences` = `git:{sourceId}:{baseCommit|targetCommit}` | `devlog://projects/{slug}/commits/<sha>` |
| `identifier` = `diff:<sha>:<path>` (kind CHANGED_FILE) | `devlog://projects/{slug}/commits/<sha>` |

Note: the `decision:`/`story:` prefixed strings seen in the audit are the
internal `reference` values; the MCP contract exposes the bare identifier.
Per mission §17, `get_engineering_context` was NOT modified to emit resource
URIs — this mapping is documented for the next story.

## Error behavior (verified live)

- Unknown project → error containing `Project '<slug>' not found`.
- Unknown artifact → `<Kind> '<id>' not found …`.
- Cross-project artifact → `… does not belong to project '…'` (isolation:
  decisions/events checked against payload `projectId`; stories server-side;
  insights ACTIVE-list membership; commits source-scoped).
- Invalid UUID or SHA → `Invalid … identifier` / `Invalid commit SHA`
  (SHA normalized to lowercase; 40 or 64 hex accepted).

## Tests

mcp-server suite: **28 tests, 0 failures** (was 8):
`ProjectsResourceTest` (2), `DecisionResourceTest` (5: happy/not-found/
cross-project/invalid-id/unknown-project), `InsightResourceTest` (3 incl.
ACTIVE-only governance), `EngineeringStoryResourceTest` (2),
`EngineeringEventResourceTest` (3 incl. cross-project),
`CommitContextResourceTest` (5 incl. SHA normalization, unknown SHA,
no-active-source), plus pre-existing tool/resource/prompt/context tests.
Backend pipeline unchanged and green (no backend modification).

## Quality pipeline

- `./backend/mvnw -pl backend -am clean verify -B` → **856 tests, 0 failures**,
  JaCoCo coverage checks met.
- `./mvnw test` (mcp-server) → **BUILD SUCCESS**.

## Manual validation (stdio MCP client vs local stack)

Executed a real JSON-RPC stdio session against the built server jar
(`DEVLOG_BACKEND_BASE_URL=http://localhost:18080`):

1. `initialize` → serverInfo `devlog-mcp 0.1.0`;
   `resources/list` → 2 static resources; `resources/templates/list` → 6
   templates (5 new + existing project context).
2. Navigation: `read devlog://projects` → 6 projects → selected `devlog-ai`.
3. Evidence correspondence: ran `get_engineering_context` on devlog-ai, took
   the real DECISION evidence identifier (`ae47a47d…`) → read
   `devlog://projects/devlog-ai/decisions/ae47a47d…` → full trusted decision
   (title + rationale) returned.
4. Insight (`severity=INFO`), Story (#58, status REGISTERED), Commit context
   (SHA taken from a `diff:` evidence identifier; classified files + candidate
   ADR references returned).
5. Error cases verified live with explicit business messages (see above).

## Documentation

- `docs/stories/0088-expose-devlog-artifacts-as-mcp-resources/`: story.md,
  repository-analysis.md, implementation-plan.md, this report,
  engineering-report.md.
- No changes to `docs/mcp-engineering-context-contract.md` needed
  (`get_engineering_context` untouched); identifier mapping lives in this
  report per §16/§24.

## Deferred resources

- `timeline`, `freshness`, `relations`: not implemented (mission §18/§19).
  Observations for later: timeline & freshness are projection-style reads
  already served by REST (`TimelineController`, freshness endpoints) and would
  map naturally to `devlog://projects/{slug}/timeline|freshness`; relations
  need a representation choice (flat list vs navigation nodes anchored at the
  new artifact resources — Decision/Insight/Event resources are now natural
  relation anchors).

## Remaining issues

1. The Spring AI annotation layer wraps resource exceptions into a generic
   `-32602 ("Error invoking resource method …")` envelope; our explicit
   business messages are preserved inside the message text but the specific
   `McpError` codes are not propagated to the JSON-RPC `code`. Documented as
   SDK behavior; acceptable for V1 since messages stay deterministic.
2. EngineeringEvents could not be exercised end-to-end manually: the local
   dataset contains zero validated events (governed workflow never run here);
   covered by unit tests instead.
3. Pre-existing tracked build artifacts under `devlog-contracts/target/`
   remain dirty in the working tree (hygiene issue explicitly out of scope);
   none were staged or committed.

## Suggested next Story

**Resource references from `get_engineering_context`** (mission-preferred
candidate): the identifier mapping table above is ready to be encoded as an
optional `resource`/`uri` field on `EngineeringEvidence` (additive contract
evolution, Story-0087 pattern). Prerequisites are met; no dependency on
timeline/freshness/relations resources was observed.
