# Story 0091 — Implementation Report

Date: 2026-08-26 · Branch: `feature/story-0091-project-freshness-checkpoints`
· Base: `689ef30` · Design sources: investigation report, ADR-062,
`implementation-plan.md`.

## 1. What was implemented

### Step 1 — ADR-062 (`fb526ef`, docs)

`docs/decisions/ADR-062.md` — *Repository Synchronization Lifecycle and
Freshness Checkpoints*, status **Proposed**. Formalizes: change detection ≠
synchronization ≠ understanding; per-source checkpoints with clarified
`observedRevision` / `baselineRevision` meanings; retention of existing
status enums; no-re-probe rule; single checkpoint-ownership point;
no-silent-stale-context invariant; read-purity direction; polling-is-an-
adapter; future sync-lifecycle intention only.

### Step 2 — Baseline comparability repair (`0173b8c`)

`ProjectProfileSnapshotRepository.findLatestComparable`:

- removed `analysis.targetRevision is null` (the pre-0085 proxy for “default
  branch refresh”, invalidated when story 0085 started persisting resolved
  revisions);
- replaced `status = COMPLETED` by `status in (COMPLETED, IN_PROGRESS)` — a
  snapshot row proves the deterministic phase observed that revision; AI
  completion no longer gates deterministic freshness (ADR-061 §6, ADR-062);
  FAILED/PENDING remain excluded;
- ordering switched to `createdAt desc, id desc` (always set; avoids Postgres
  NULLS-FIRST ambiguity of `completedAt desc`).

New integration test `ProjectFreshnessBaselinePostgresIntegrationTest`
(Testcontainers Postgres) — **fails on the pre-fix query**, passes after:
post-0085 completed revision-pinned snapshot becomes baseline;
IN_PROGRESS-with-snapshot eligible; newest wins with legacy rows still
eligible; FAILED never anchors.

### Step 3 — Checkpoint advancement from understanding refreshes (`f2d36ec`)

- `ProjectFreshnessPersistenceService.recordObservation(...)`: upserts the
  source row with `current = baseline = normalize(observedRevision)`,
  `status = CURRENT`, `guidance = REFRESH_NOT_NEEDED`,
  `requestedRevision = origin/<defaultBranch>` (mirrors GitWorkspaceManager
  convention), links the just-started analysis as `baselineAnalysis`.
- `ProjectFreshnessService.recordObservedBaseline(...)`: public boundary with
  the same validation guards as `check()` — the single ownership point for
  checkpoint writes (ADR-062 §4).
- `ProjectUnderstandingService.execute`: after a successful
  `workflowService.start` for a CREATED claim, records the checkpoint from
  `prepared.resolvedRevision()` — already observed by synchronization, so
  **no second fetch/rev-parse** occurs. REUSED claims skip it; recording
  failures are logged and never fail the refresh (freshness is a projection).

### Step 4 — Freshness in the engineering-context contract (`e11e6dd`)

- New contract record `EngineeringContextFreshness`
  (`status`, `repositoryRevision`, `contextRevision`, `sources[]`) and one
  additive nullable field `freshness` on `EngineeringContextMetadata`.
  Existing fields untouched → JSON evolution is additive.
- `EngineeringContextContractMapper` builds it deterministically:
  - per-source rows from `ProjectFreshnessSummary` (observed = persisted
    current revision, context = baseline analyzedRevision);
  - live revisions derived from evidence metadata/content/symbols
    (single distinct value only — mirrors
    `AgentContextProjectionService.resolvedRevisions`);
  - single-source override: if the revision served by this very build differs
    from the knowledge baseline, the exposed status becomes STALE even if the
    persisted row said CURRENT (the hidden read-path divergence becomes
    visible);
  - aggregate: all-equal → that status; CURRENT+STALE mix →
    `PARTIALLY_FRESH`; otherwise worst-of STALE > NO_BASELINE > UNKNOWN >
    CURRENT; nothing known + observation exists → NO_BASELINE;
  - warnings appended: `PROJECT_CONTEXT_STALE` (any stale or knowledge-absent
    -with-observation), `PROJECT_CONTEXT_PARTIALLY_FRESH`.
- `EngineeringContextFacadeImpl` injects `ProjectFreshnessService.summary(...)`;
  MCP server remains passthrough (adapter stays thin).

### Step 5+6 — Summary endpoint & MCP resource (`c6f027b`)

- Backend: `GET /api/v1/projects/{projectId}/freshness-checks/summary`
  delegating to the existing `summary()` business logic (no duplicate
  implementation).
- Contracts: `DevlogResourceUriFactory.freshness(slug)` →
  `devlog://projects/{slug}/freshness`.
- MCP server: `DevlogResourceClient.getFreshnessSummary(projectId)` +
  `FreshnessResource` following stories 0088/0089 conventions
  (`@McpResource`, slug→id resolution via `ResourceSupport.requireProjectId`,
  scoped endpoint, error mapping). **Read-only**: no probe, no refresh, no
  mutation; exposes last recorded observations (trade-off documented below).

## 2. Runtime validation (executed live)

Ephemeral stack: Testcontainers-style Postgres 17 (docker, port 15432),
ai-engine via local uvicorn (port 18001), backend via
`mvn spring-boot:run` on port **18099** (the developer’s own Docker stack on
18080 was detected and left strictly untouched; an accidental project created
on it during port discovery was deleted immediately). Tracked repository:
throwaway clone in gitignored `backend/target/demo-clone` at X = `13fc2c3…`.

| Step | Action | Result |
|---|---|---|
| 1 | `POST /understanding-executions` | `CREATED`, analysis IN_PROGRESS at `targetRevision=13fc2c3…` |
| 2 | `GET …/freshness-checks/latest` | **CURRENT**, `currentRevision = baseline.analyzedRevision = 13fc2c3…`, written by the refresh itself — no Maintenance click |
| 3 | empty commit Y = `6b98229…` in the clone | — |
| 4 | `POST /freshness-checks` (probe) | **STALE / REFRESH_RECOMMENDED**, observed Y ≠ baseline X; **no automatic synchronization occurred** |
| 5–6 | `GET /engineering-context?intent=…` | `metadata.freshness`: status **STALE**, `repositoryRevision=Y`, `contextRevision=X`; warnings `[REPOSITORY_CONTEXT_BUDGET_APPLIED, PROJECT_CONTEXT_STALE]` — stale knowledge is declared, never silent |
| 7–8 | `GET …/freshness-checks/summary` (resource payload) | per-source STALE state with both revisions; unknown project rejected before reaching business data |

Environment cleaned afterwards (processes stopped, container removed, demo
clone/workspaces deleted); the modified tracked `.pyc` produced by running
uvicorn was restored.

## 3. Quality gates

- `mvn -pl devlog-contracts install` — OK (contract artifact refreshed for
  dependent modules).
- `mvn -pl backend verify` — **BUILD SUCCESS**, JaCoCo total ≈ **85%**
  (7 434 missed of 50 669 instructions), including the new Testcontainers
  integration tests.
- `mvn -pl mcp-server verify` — **BUILD SUCCESS**.
- Frontend: zero changes (no frontend run needed).
- `git diff --check` clean; working tree contains only the other session’s
  pre-existing `devlog-contracts/target` artifacts (untouched).

## 4. Explicit non-goals honored

No scheduler, polling detector, RepositoryChanged event, planner, sync job,
reaper, webhooks/hooks, automatic sync/understanding/AI; no Maintenance
redesign; no frontend change; `PROJECTION_REFRESH_GAP` dead enum and orphaned
`TemporalAssessmentService` left as documented debt.
