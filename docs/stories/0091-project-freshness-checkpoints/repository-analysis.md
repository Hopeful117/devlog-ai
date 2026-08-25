# Story 0091 — Repository Analysis

Date: 2026-08-26 · Branch: `feature/story-0091-project-freshness-checkpoints`
· Base: `689ef30` (main, includes the investigation report)

Design source: `docs/investigations/repository-synchronization-freshness.md`.
This file records the code-level facts verified on HEAD before implementation.

## 1. Freshness package (backend/src/main/java/com/hopeful117/devlogai/projectfreshness/)

| Class | Role | Key facts |
|---|---|---|
| `ProjectFreshnessService` | Entry point | `check()` = live probe via `WorkspaceManager.resolveCurrentRevision` → `persistence.save(...)`; `latest()`; `summary(projectId)` = persisted rows for up to 10 active sources + `uncheckedSourceCount`/`truncated`. |
| `ProjectFreshnessPersistenceService` | Single writer | `save()` upserts `project_source_freshness` row: classification via `findLatestComparable` baseline (`snapshot.resolvedRevisions[sourceId]`), `GitCommitIdentity.normalize`, review counts from proposals. Package-private. |
| `ProjectFreshnessClassifier` | Pure | `NO_BASELINE` (no baseline) / `UNKNOWN` (unparseable) / `CURRENT` / `STALE`. |
| `ProjectSourceFreshness` | Entity | Columns per V32: `status`, `guidance`, `requested_revision` NOT NULL(300), `current_revision` NOT NULL(64), `baseline_revision`(64), `checked_at`, `baseline_analysis_id`. |
| `ProjectFreshnessController` | REST | `POST /api/v1/projects/{projectId}/freshness-checks`; `GET .../latest?sourceId=` (204 when absent). |
| `ProjectFreshnessResponse` | DTO | `version("project-freshness-v1"), id, projectId, Source{id,name,defaultBranch,requestedRevision,currentRevision}, checkedAt, status, guidance, Baseline{analysisId,completedAt,analyzedRevision}, ReviewCounts`. |
| `ProjectFreshnessSummary` | DTO | `version("project-freshness-summary-v1"), projectId, checkedSources, uncheckedSourceCount, truncated`. |

Callers of `check()`: only `MaintenanceRemediationServiceImpl` (3 sites) and
the controller. **No writer advances freshness during normal understanding.**

## 2. Baseline drift (confirmed on HEAD)

`profile/repository/ProjectProfileSnapshotRepository.java:16-25`:

```java
... and analysis.status = AnalysisStatus.COMPLETED
    and analysis.intentId = 'describe-project' and analysis.intentVersion = 'v1'
    and analysis.targetRevision is null ...
```

Since story 0085 (`projectunderstanding/ProjectUnderstandingClaimService.java:64`
→ `.targetRevision(prepared.resolvedRevision())`), every new understanding
analysis has a non-null `targetRevision`, so `findLatestComparable` can only
return pre-0085 legacy rows. Post-0085 projects degrade to `NO_BASELINE`.

Additional coupling defect: requiring `COMPLETED` ties a deterministic
freshness question to AI-callback completion — right after a refresh (analysis
`IN_PROGRESS`, snapshot already built), the baseline lookup still returns the
old snapshot.

## 3. Understanding flow (checkpoint insertion seam)

`ProjectUnderstandingService.execute`: prepare (git synchronize →
`prepared.resolvedRevision()`) → claim (`CREATED`/`REUSED`) →
`workflowService.start(analysisId)` (synchronous through collection +
profile snapshot build + AiTask submit) → response.

The workflow's synchronous phase builds `project_profile_snapshots` at the
resolved revision before returning; the async tail only adds LLM proposals.
Therefore recording the checkpoint after `workflowService.start` succeeds
(`CREATED` outcome) is honest for the deterministic tier: snapshot@X exists.
No re-probe needed (ADR-062 §3): X was just observed by synchronization.
`REUSED` outcome skips checkpoint write (the first execution writes it).

## 4. Engineering-context metadata plumbing

- Contract: `devlog-contracts/.../EngineeringContextMetadata.java` = record
  `(candidateCount, selectedCount, truncated, usedTokens, contextDigest,
  warnings)` — warnings is `List<String>`, copied in compact constructor.
  Additive extension possible by appending fields (JSON consumers tolerate).
- Built solely in `EngineeringContextContractMapper.mapMetadata(context)`
  from `RepositoryContext`; facade `EngineeringContextFacadeImpl` wires
  `ProjectContextProvider` + `RepositoryContextAdapter` + mapper. Adding a
  freshness parameter to `toContract` + one dependency in the facade covers
  backend wiring; MCP server is passthrough (thin adapter stays thin).
- Live revisions during a build exist only per-evidence
  (`extractionMetadata["resolvedRevision"]`, `content.revision`,
  `symbols.revision`). Precedent for deriving a distinct revision set:
  `AgentContextProjectionService.resolvedRevisions(evidence)` (:538-552).
  `RepositoryStructureCollector` synchronizes only the first active source.
- Warning vocabulary is inline string literals; new values
  `PROJECT_CONTEXT_STALE` / `PROJECT_CONTEXT_PARTIALLY_FRESH` follow the same
  style.

## 5. MCP resource conventions (stories 0088/0089)

- Resources are `@Component` classes with `@McpResource(uri=..., name=...,
  description=..., mimeType="application/json")` methods returning JSON
  strings; clients via `DevlogResourceClient` (`@HttpExchange("/api/v1")`,
  `@GetExchange`, String passthrough); shared validation/error mapping in
  `ResourceSupport` (`requireProjectId(slug)`, ownership checks, 404→
  RESOURCE_NOT_FOUND mapping). Templates declared declaratively; discovery
  verified live in 0088 (static vs templates lists).
- Anti-drift gates: `mcp-server/.../resource/ResourceUriTemplateSyncTest.java`
  (reflective annotation ⇄ `DevlogResourceUriFactory`) and
  `backend/.../contracts/engineeringcontext/DevlogResourceUriFactoryTest.java`
  (`shouldBuildEveryArtifactUri`).
- Story 0088 explicitly deferred `freshness` as natural candidate:
  “timeline & freshness … would map naturally to
  `devlog://projects/{slug}/timeline|freshness`”;
  `docs/mcp-resource-candidates.md:20` documents the candidate with
  `ProjectFreshnessService.summary` as backing projection.

## 6. Backend endpoint gap

Existing endpoints are per-source (`POST check`, `GET latest?sourceId=`).
The resource needs the project-wide summary; add
`GET /api/v1/projects/{projectId}/freshness-checks/summary` delegating to
`ProjectFreshnessService.summary(projectId)` (no second business
implementation).

## 7. Tests inventory

Existing: `ProjectFreshnessClassifierTest`, `ProjectFreshnessPersistenceServiceTest`,
`ProjectFreshnessServiceTest`, `ProjectFreshnessControllerWebMvcTest`,
`MaintenanceEvaluationServiceTest`, `MaintenanceRemediationServiceTest`,
`ProjectUnderstandingServiceTest`/`ClaimServiceTest`,
`EngineeringContextContractMapperTest`, resource tests incl.
`ResourceUriTemplateSyncTest`. Postgres integration infra exists
(`*PostgresIntegrationTest`). Gaps confirmed: no test pins baseline
comparability post-0085; none covers context freshness metadata or a
freshness resource.

## 8. Constraints respected

- No scheduler/poller/job/event code anywhere (verified vocabulary absent).
- Frontend untouched.
- Hidden read-path Git sync left as-is (CASE B) but made observable through
  metadata; removal documented as next-story candidate.
