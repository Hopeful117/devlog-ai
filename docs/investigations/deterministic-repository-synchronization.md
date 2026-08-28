# Investigation: Deterministic Repository Synchronization

## Executive Summary

This investigation determines what deterministic work must advance DevLog's persisted repository state from revision X to revision Y without conflating synchronization with Understanding or AI automation.

After stories 0091 and 0092 established change detection and freshness checkpoints, the key invariant proved is:

```text
CHANGE_DETECTION != SYNCHRONIZATION
```

Detection observes Y → freshness becomes STALE → knowledge remains at X. The investigation answer is: **deterministic synchronization must advance commit metadata and changed-path persistence (the SYNC tier), while Understanding remains separate (the UNDERSTANDING tier).**

## Runtime Deployment Verification

- **Git state**: `main` at `de890f8` (Merge PR #75, both 0091 and 0092 merged)
- **Backend**: Docker container `devlog-backend` healthy on port 18080
- **MCP server**: Rebuilt jar, client respawned with new contract
- **Freshness baseline** (via `POST /api/v1/projects/f3d56247-aada-4a76-982b-e6802c0b309c/freshness-checks`):
  - `currentRevision`: `de890f8b25af0ecabc785034d5f49e0d9a5b2c92` (= git HEAD)
  - `baseline.analyzedRevision`: `a31ddba6b8f9ebd511428bb984f1fa05e524a8a5`
  - `status`: `STALE`
  - `guidance`: `REFRESH_RECOMMENDED`
- **Engineering context** (via `GET /api/v1/projects/devlog-ai/engineering-context?intent=get_engineering_context`):
  - Evidence array contains ONLY `CHANGED_FILE` (commit-diff) and `COMMIT` (git-history) layers
  - **NO** ADR evidence, **NO** Engineering Story evidence, **NO** facts/observations from analysis
  - This confirms the critical finding: `RepositoryContextAdapter` synthesizes `AnalysisContext` with **EMPTY facts** → zero ADR/doc evidence surfaces on the `get_engineering_context` endpoint

## Freshness Baseline

| Metric | Value |
|--------|-------|
| `observedRevision` | `de890f8` (git HEAD, also currentRevision) |
| `baselineRevision` | `a31ddba6` (analysis baseline, analyzedRevision) |
| `freshnessStatus` | `STALE` |
| `guidance` | `REFRESH_RECOMMENDED` |
| `source` | Github, origin/main, currentRevision=de890f8 |
| `evidence in get_engineering_context` | Commit-diff + git-history only |

## Current Refresh Decomposition

The current "Refresh understanding" operation implicitly combines:

```text
Git synchronize
history import
collection
profile build
AI proposals
```

These are **not** separable in the current UI. The investigation's mandatory question Q9 confirms: history import operates as a full repository walk with post-hoc SHA dedup, not an X→Y range query.

## Synchronization Boundary: What Belongs to SYNC

| Capability | Current Owner | Current Trigger | Required for SYNC? |
|------------|--------------|----------------|-------------------|
| Resolve HEAD | Detector | Autonomous observation | YES (triggers sync) |
| Import commit metadata | `ProjectHistoryServiceImpl.importHistory` | Manual endpoint + analysis preps | YES |
| Import changed paths | `ProjectHistoryServiceImpl.importHistory` | Same as above | YES |
| Build facts | `DocumentationCollector` | Full-analysis pipeline only | NO (not on every change) |
| Build observations | Same as facts | Full-analysis pipeline only | NO |
| Project profile/Understanding | Analysis pipeline | User-initiated "Refresh understanding" | NO (outside SYNC) |
| ADR discovery | Documentation collector / REST registration | Full analysis or manual REST | NO (not automatic) |
| Story discovery | REST registration | Manual API action | NO |

**Deterministic SYNC minimum**: commit SHA metadata + changed paths persistence. This is the cheapest tier (classified CHEAP in the cost classification).

## Understanding Boundary: What Must Remain Outside SYNC

- Analysis facts/observations: currently produced only through the full-analysis pipeline, not automatically on every change
- ADR evidence: requires either DocumentationCollector run or REST registration of Decision entities
- Engineering Story evidence: requires REST registration (`CREATE EngineeringStoryRequest`)
- AI proposals/semantic interpretation: explicitly outside SYNC boundary
- Significance gating: applies to Understanding tier, not SYNC

## Deterministic State Inventory

### What History Import Persists (from audit of `ProjectHistoryServiceImpl.importHistory`)

- **Persisted**: SHA, author name/email (NOT committer), authoredAt/committedAt, subject/fullMessage, root/merge flags, filesChanged/insertions/deletions/binaryFiles counts, parents (raw hashes, no FK), changed paths (changeType/oldPath/newPath/binary/insertions/deletions)
- **NOT persisted**: diff content, symbols, semantic analysis, file content
- **Dedup mechanism**: `existsBySourceIdAndCommitHash` — checked AFTER git work already paid
- **Incremental behavior**: full walk every time; no watermark/watermark would allow early termination if walking newest-first and stopping at known commit
- **Merge commits**: full ancestry walked, diff vs first parent, `MERGE_COMMIT_FIRST_PARENT_DIFF` warning
- **Force-push**: orphaned rows accumulate silently; dangling parent refs; no divergence detection

### What `get_engineering_context` Currently Surfaces

From the deployment proof, the evidence array contains:

```json
"evidence": [
  {"kind": "CHANGED_FILE", "layer": "COMMIT_DIFF", ...},       // from commit-diff collector
  {"kind": "COMMIT", "layer": "GIT_HISTORY", ...},           // from git-history collector
  ... (only these two types; no ADR, no Story, no facts)
]
```

**Critical finding**: The `RepositoryContextAdapter` (engineeringcontext/EngineeringContextFacadeImpl) synthesizes `AnalysisContext` with **EMPTY facts/observations**. Even though story 0091 added freshness metadata and the backend is rebuilt, ADRs and documents remain invisible through `get_engineering_context` because the read path does not feed facts.

### Incremental History Audit

- **Commands per import**: 1 `rev-list` + 3N subprocesses (show + name-status diff + numstat diff), with `-C --find-copies-harder` (expensive)
- **No watermark**: Full history walked each time; dedup only post-commit
- **Ordering**: `rev-list` without `--reverse` outputs newest first; to early-terminate you'd want newest-first and stop when hitting known commit, but this breaks ordering for inserts
- **Transactions**: endpoint path holds DB tx across clone/fetch; prep path: entire git read phase inside tx
- **Callers**: manual endpoint only + two user-initiated analysis preps; no automatic path
- **Force-push**: orphaned rows accumulate silently, dangling parent refs, no divergence detection
- **Merge commits**: fully walked, diff vs first parent only

## X→Y Range Semantics

### Existing style (full walk)

```text
git rev-list Y
skip already-imported SHA (post-hoc dedup)
```

- Cost: O(total commits in repository)
- Pros: simple, no state tracking beyond SHA dedup
- Cons: expensive for large repos; no incremental early termination

### Explicit range (proposed)

```text
git rev-list X..Y
```

- Would walk only commits between X and Y
- Correctness concerns: force pushes, multiple parents, branch changes, initial import, crash recovery, idempotence
- Not currently implemented; would require explicit `fromRevision`/`toRevision` tracking

## Checkpoint Model: Observed / Ingested / Baseline

The investigation proposes a three-checkpoint model:

| Checkpoint | Meaning | When it advances |
|------------|---------|-----------------|
| `observedRevision` | Highest commit the detector has seen (may be ahead of repo) | After change detection, autonomously |
| `ingestedRevision` | Highest revision for which all deterministic SYNC stages completed | After RepositorySyncJob runs |
| `baselineRevision` | Understanding knowledge snapshot | After Understanding runs |

**Current state**: only `observedRevision` and `baseline.analyzedRevision` exist (the latter is the analysis baseline, not the same as `baselineRevision` in the proposed model).

**Required**: explicit `ingestedRevision` checkpoint to make the sync boundary first-class.

## Partial Freshness Semantics

Modeling the stages:

```text
Repository = Y, Observed = Y, Ingested = X, Baseline = X → STALE

after deterministic sync: Observed = Y, Ingested = Y, Baseline = X → PARTIALLY_FRESH

after Understanding: Observed = Y, Ingested = Y, Baseline = Y → CURRENT
```

**The state after deterministic sync but before Understanding must be `PARTIALLY_FRESH`**, not `STALE` (which implies no sync ran) and not `CURRENT` (which implies full understanding). This is a central finding of the investigation.

**Current system gap**: no `ingestedRevision` exists, so the system cannot represent `PARTIALLY_FRESH`. It only has `STALE` → `CURRENT` via full Understanding refresh.

## Document / ADR / Story Synchronization

### How new artifacts become discoverable today

| Artifact | Current discovery mechanism | Becomes current after sync X→Y? |
|----------|---------------------------|--------------------------------|
| ADRs | (a) facts heuristic from DocumentationCollector — but ONLY via full-analysis path; `get_engineering_context` adapter passes EMPTY facts → zero ADR evidence; (b) registered Decision entities via REST | NO. After sync alone, ADRs invisible without collection run + read-path fix |
| Engineering Stories | REST registration only (`CREATE EngineeringStoryRequest`) | NO. Requires explicit REST registration regardless |
| Roadmap files | Scanned during full collection | NO. Same as ADRs |

**Minimum additional deterministic stage** for ADRs/stories after sync X→Y:

1. Run a collection cycle at revision Y (generates facts)
2. Change `RepositoryContextAdapter` to include facts in `AnalysisContext` for `get_engineering_context`
3. Register Stories via REST (separate from sync)

Without these, new ADR/story content is visible only in `COMMIT_DIFF` and `GIT_HISTORY` layers.

## Diff Synchronization

The history import persists changed paths but NOT full diff content. Currently:

- **Persisted**: changeType, oldPath, newPath, binary flag, insertions, deletions
- **NOT persisted**: diff content, symbolic links, semantic context

**Question for deterministic sync**: should commit diffs be persisted at metadata level only (current), or should summaries/classified context also be persisted? The current domain design appears to reject permanent full-diff persistence, preferring live diff generation on demand. Deterministic sync should therefore persist changed paths only — diff content can be regenerated from git on demand.

## Repository Structure

Currently `RepositoryContextEngine` reads structure live, causing hidden Git synchronization. Future pinned-read strategy (for after sync exists):

```text
SyncJob prepares pinned workspace/projection
↓
read path uses known ingested revision
↓
hidden synchronization removable
```

**V1 decision**: do not fix. The future design requires a pinned workspace/revision that the sync job prepares before read paths use it. This is a migration path for later, not an immediate concern.

## Sync Job Analysis

### RepositorySyncJob necessity

**REQUIRED_NOW**. The architecture gaps justify creating a durable job model:

- No watermark (no `ingestedRevision`)
- No transaction boundaries documented across long Git operations
- No crash recovery (0091 found PENDING/IN_PROGRESS zombies)
- No per-source isolation (orphaned rows on force-push)
- No significance gating separation from sync

### Minimum justified job states

```text
PENDING → RUNNING → COMPLETED
     ↘         ↙
       FAILED
```

Four states: PENDING, RUNNING, COMPLETED, FAILED. `SUPERSEDED` could be added for coalescing but is not essential V1.

### Trigger reasons

Useful reasons for the job:

```text
REPOSITORY_CHANGE_DETECTED (autonomous detector)
MANUAL_SYNC (agent-initiated)
RECOVERY (startup reaper/enqueue)
INITIAL_IMPORT (fromRevision = null)
```

### Transaction boundaries (recommendation)

**Avoid**: one DB transaction held across long Git/network operations (current evidence shows this is problematic).

**Recommendation**: divide into phases:

```text
Phase 1: Git read (fetch/rev-list) — separate tx or no tx
Phase 2: DB persist — separate tx
Phase 3: Projection/update — separate tx
```

This prevents holding locks across expensive I/O and enables crash recovery.

## Concurrency: HEAD Advances During Sync

Model:

```text
baseline/ingested = A
detector sees B
job A→B starts
repository becomes C (new commit on main)
detector observes C
```

**Target behavior**: finish B, enqueue B→C. The job targets immutable `toRevision = SHA`, not a branch name. If HEAD moves during sync, the completed job advances the ingested checkpoint to B, and a new job targets C.

**Immutable target**: `toRevision = SHA` (per ADR-061). Never `branch = main`.

## Idempotency

| Stage | Replay-safe? | Existing mechanism |
|-------|-------------:|-------------------|
| Commit import | ✅ | `existsBySourceIdAndCommitHash` dedup (after git work paid) |
| Changed paths | ✅ | Same dedup mechanism |
| Facts | ❓ | Not audited (collection path) |
| Events | ❓ | Not audited |
| Projections | ❓ | Not audited |

**Finding**: commit import and changed paths are replay-safe by SHA dedup. Other stages need auditing.

## Force Push / History Rewrite

If `observed = Y` and remote branch becomes `Z` where `Y` is not an ancestor of `Z`:

- **V1 behavior**: detect and fail safely. Flag that observed revision is not ancestor of new HEAD.
- **Repair mode**: manual intervention required. The system should NOT silently overwrite `observedRevision` with the new HEAD SHA.
- **Detection**: compare `observedRevision` against `origin/HEAD`; if not fast-forward, set freshness status to a new state (e.g., `FORCE_PUSH_REQUIRES_REPAIR`).

## Initial Synchronization

Handle the case where no ingested revision exists:

- Job type: `INITIAL_IMPORT`
- `fromRevision = null`
- Target: import all commits from scratch
- This is a special case of `RepositorySyncJob` with no prior target

## Significance Gating

**Challenge the assumption** that synchronization should be gated by significance.

### Model A

```text
change detected → significance check → maybe deterministic sync
```

### Model B

```text
change detected → always deterministic sync → significance check → maybe Understanding/AI
```

**Evaluation**: Model B is preferred. The deterministic core (history import + changed paths) is CHEAP; gating it by significance adds unnecessary delay. Significance belongs to the UNDERSTANDING/AI tier, not the SYNC tier. The evidence from cost classification (HISTORY_IMPORT = CHEAP) supports always-running deterministic sync after detection, with significance gating applied separately.

## Automatic Deterministic Sync Policy

**YES_ALWAYS** for the deterministic core (history import at minimum), with significance gating applied separately for Understanding/AI.

Rationale:
- The deterministic core is cheap (history import: CHEAP)
- Leaving the system STALE unnecessarily degrades developer experience
- Significance gating is a separate concern for the Understanding tier
- Developer expectation: "when the repo changes, I want to know what changed"

## Terminal Report

### Worktree

- **Path**: `/home/ludo/Bureau/workspace/devlog-ai`
- **Branch**: `main` at `de890f8` (PR #75)
- **Isolation**: investigation/deterministic-repository-synchronization (this document)

### DevLog rebuild/redeploy

Commands/workflow:

```text
git checkout main
git pull          # ensures de890f8 with 0091/0092 merged
docker compose down
docker compose up --build  # rebuilds backend, ai-engine, frontend, postgres
# verify health:
curl http://localhost:18080/api/v1/projects/f3d56247-aada-4a76-982b-e6802c0b309c/freshness-checks/summary
curl http://localhost:18080/api/v1/projects/devlog-ai/engineering-context?intent=get_engineering_context
```

Services health: all containers healthy (verified via `docker ps`).

### MCP baseline (before investigation)

- `get_engineering_context` returned engineering context with only commit-diff and git-history evidence
- Freshness: STALE, currentRevision=de890f8, baseline.analyzedRevision=a31ddba6
- No ADR/story evidence surfaced through MCP

### Current sync boundary (one concise definition)

```text
Deterministic synchronization = advancing commit metadata + changed-path persistence
from an observed revision X to Y via RepositorySyncJob, without running
Understanding, AI, or semantic analysis. The SYNC tier is purely deterministic
and CHEAP; UNDERSTANDING is a separate, optional tier.
```

### Deterministic state: what must advance X→Y

The minimum deterministic sync from X→Y must advance:

1. **Commit SHA metadata** (persisted via history import) — the commit hash, subject, author, timestamps, parent references, changed-path counts
2. **Changed paths** (persisted alongside commit metadata) — change type, old/new paths, binary flag, insertion/deletion counts
3. **ingestedRevision = Y** (new first-class checkpoint) — marking that deterministic sync completed

These three artifacts are sufficient for `search_project_history` to be trustworthy and for `get_engineering_context` to surface commit-derived evidence. No diff content, symbols, or semantic analysis is required.

### Ingested revision

**REQUIRED**. Definition: "highest target repository revision for which all required deterministic SYNC-stage repository facts have been persisted successfully."

Without `ingestedRevision`, the system cannot represent `PARTIALLY_FRESH` after sync but before Understanding. The current model only has `STALE` → `CURRENT` via full Understanding refresh, which conflates two different concerns.

### Baseline revision

**definition**: The Understanding knowledge snapshot. Should advance only after Understanding runs, NOT during deterministic sync. In the current implementation, `baseline.analyzedRevision` (`a31ddba6`) represents the analysis baseline and is distinct from the proposed `baselineRevision`.

**Confirmation**: deterministic sync must NOT advance `baselineRevision` if it means Understanding knowledge. The investigation (§16) explicitly evaluates and rejects this confusion.

### Post-sync freshness

After deterministic sync X→Y but before Understanding:

```text
status: PARTIALLY_FRESH
guidance: VERIFY_BASELINE (or equivalent)
```

After Understanding:

```text
status: CURRENT
guidance: ESTABLISH_BASELINE
```

The `PARTIALLY_FRESH` state is the investigation's central semantic finding — it bridges the gap between `STALE` (no sync) and `CURRENT` (full understanding).

### Significance gating

Where it belongs: **UNDERSTANDING/AI tier**, not SYNC tier.

- SYNC: always run after detected change (CHEAP core)
- UNDERSTANDING: significance-gated, may run after sync
- AI: significance-gated, may run after understanding

### RepositorySyncJob

**REQUIRED_NOW**. The architecture gaps (no watermark, no job model, no crash recovery, no per-source isolation) justify creating the job now as the smallest coherent vertical slice.

### Job lifecycle

```text
PENDING → RUNNING → COMPLETED
     ↘         ↙
       FAILED
```

After COMPLETED: `ingestedRevision` advances to `toRevision`. After FAILED: job can be retried or superseded.

### Incremental strategy

**Exact recommendation**: Introduce `RepositorySyncJob` with these minimum fields:

```text
id (UUID)
projectId (UUID)
sourceId (UUID)
fromRevision (SHA, nullable for initial import)
toRevision (SHA, immutable target)
reason (REPOSITORY_CHANGE_DETECTED / MANUAL_SYNC / RECOVERY / INITIAL_IMPORT)
status (PENDING / RUNNING / COMPLETED / FAILED)
attempt (integer, for retry)
createdAt (instant)
startedAt (instant, nullable)
completedAt (instant, nullable)
failure (string, nullable)
```

Incremental: first job targets `de890f8` (initial import / recovery), subsequent jobs target new observed revisions.

### Force push

**Strategy**: detect and fail safely.

- If `observedRevision` is not an ancestor of new `origin/HEAD`, flag freshness status
- Do NOT overwrite `observedRevision` with new HEAD SHA
- Require manual repair mode for force-push scenarios
- V1 simply detects and reports; repair is future work

### Crash recovery

**Strategy**: job persisted before execution + startup recovery.

- Job record created in DB before Git operations begin
- Startup reaper scans for orphaned PENDING/IN_PROGRESS jobs (from 0091 zombie discovery)
- Lease mechanism: each job acquires a time-limited lease; abandoned jobs are re-enqueued
- Retry semantics differentiate:
  - Repository temporarily unavailable → retry
  - Invalid revision → error, do not retry
  - Git authentication failure → retry once
  - DB error → retry with backoff
  - Domain invariant error (e.g., merge commit without first parent) → error

### Documents / ADR / Stories

How they become current after sync:

1. **History import** (deterministic sync): makes commit metadata + changed paths current
2. **Collection run** at revision Y: generates facts/observations from docs/ADRs
3. **RepositoryContextAdapter change**: include facts in `AnalysisContext` for `get_engineering_context`
4. **Story registration** via REST: `CREATE EngineeringStoryRequest` — required regardless of sync

Without steps 2-4, new ADR/story content is visible only in `COMMIT_DIFF`/`GIT_HISTORY` layers.

### Hidden read sync

**Migration direction**: once deterministic sync with pinned workspace exists,

- read path uses known `ingestedRevision` instead of live git fetch
- `RepositoryContextEngine` no longer causes hidden Git synchronization
- This is a future migration, not V1

### Existing refresh

**Future decomposition**: current "Refresh understanding" (Git synchronize + history import + collection + profile + AI) should eventually become:

```text
ensure synchronized(target)    ← RepositorySyncJob
run Understanding(target)      ← separate Understanding pipeline
```

This decomposition would reuse the sync capability instead of duplicating it.

### MCP

**Future implications**:

- `devlog://projects/{slug}/freshness` may eventually carry:
  - `SYNCING` state during job execution
  - `lastSync` timestamp
  - `pendingTargetRevision`
- Resource `devlog://projects/{slug}/synchronization` could be cleaner than a tool
- Agents may eventually have explicit `sync_project` tool, but with strict safety:
  - idempotent
  - targeted SHA
  - no AI involvement
  - explicit opt-in

### Target architecture (short diagram)

```text
Repository change
       ↓
Autonomous detector
       ↓
observedRevision advances to Y
       ↓
RepositorySyncJob(X → Y)  [deterministic, CHEAP]
       ↓
commit metadata + changed paths persisted
       ↓
ingestedRevision = Y
       ↓
freshness: PARTIALLY_FRESH  (not STALE, not CURRENT)
       ↓
optional significance-gated Understanding
       ↓
baselineRevision = Y
       ↓
freshness: CURRENT
```

### Candidate ADR

**ADR-063 — Deterministic Repository Synchronization Lifecycle**

Or: NONE if ADR-062 is extended by implementation Story instead. The investigation stops short of creating the ADR automatically; the candidate is documented for consideration.

### Candidate Story

**Story title**: Introduce `ingestedRevision` checkpoint and deterministic sync pipeline

**Objective**:

> Add `ingestedRevision` as a first-class checkpoint in the freshness model,
> introduce `RepositorySyncJob` lifecycle (minimum fields: projectId, sourceId,
> fromRevision, toRevision, reason, status), and ensure deterministic sync
> advances only commit metadata + changed paths (no AI, no semantic analysis).
> Enable `PARTIALLY_FRESH` freshness state after sync but before Understanding.

** smallest coherent vertical slice**: the `ingestedRevision` checkpoint +
`RepositorySyncJob` + freshness state transition. This enables the rest of the
architecture (significance gating, read-path migration, etc.) without forcing
full Understanding.

### DevLog MCP verdict

**PARTIALLY**. After actual redeployment (0091/0092 merged, Docker rebuilt, MCP jar respawned):

- ✅ `get_engineering_context` now returns freshness metadata block (status, repositoryRevision, contextRevision, warnings)
- ✅ Freshness Resource (`devlog://projects/{slug}/freshness`) observable via MCP
- ❌ Evidence still lacks ADR/story/facts (read-path bug: `RepositoryContextAdapter` passes empty facts)
- ❌ No `ingestedRevision` checkpoint (system cannot represent `PARTIALLY_FRESH`)
- ❌ No `RepositorySyncJob` (no durable sync lifecycle)

The new contract is materially better than pre-0091/0092 (freshness metadata now exists), but the investigation's architectural work is needed to realize the full potential.

### No implementation confirmation

**CONFIRMED ZERO functional code changes** were made during this investigation. All outputs are documentation/analysis only. The investigation answered the central question architecturally without implementing any production code.

---

## Mandatory Questions Answered

### Q1: What exact persisted state must deterministic synchronization advance?

Commit SHA metadata + changed paths. These are the minimum artifacts that must be persisted for the SYNC tier to claim X→Y completion. No diff content, symbols, or semantic analysis is required.

### Q2: Is history import alone sufficient?

**No**. History import persists commit metadata + changed paths, but additional deterministic stages are needed for full engineering context: analysis facts (via collection), ADR evidence (via REST registration or collector fix), and Story evidence (via REST registration). History import makes `search_project_history` trustworthy and provides commit-derived evidence to `get_engineering_context`, but not the full context.

### Q3: Which layers must be current for `search_project_history` to be trustworthy?

Commit metadata import (history import) + changed paths persistence. These are the SYNC-tier artifacts. Without them, `search_project_history` returns 0 results for commits not yet imported.

### Q4: Which layers must be current for `get_engineering_context` to be useful?

Commit metadata + analysis facts + ADR registration + Story registration. Currently only commit layers are populated through the read path. The critical bug is that `RepositoryContextAdapter` synthesizes `AnalysisContext` with **EMPTY facts**, so ADRs and documents never surface through `get_engineering_context` even after 0091/0092.

### Q5: Do we need an explicit `ingestedRevision`?

**REQUIRED**. It provides the third checkpoint between `observedRevision` and `baselineRevision`, making the sync boundary explicit. Without it, the system cannot represent `PARTIALLY_FRESH` after sync but before Understanding.

### Q6: How is `ingestedRevision` different from `baselineRevision`?

- `ingestedRevision`: highest revision for which deterministic SYNC stages completed (committed/persisted)
- `baselineRevision`: Understanding knowledge snapshot (advances only after Understanding)

They're semantically distinct: sync vs understanding. In the current implementation, `baseline.analyzedRevision` (`a31ddba6`) is the analysis baseline and is distinct from the proposed `baselineRevision`.

### Q7: Should deterministic sync always run after a detected change?

**YES_ALWAYS** for the deterministic core (history import at minimum). Significance gating applies separately to the Understanding tier.

### Q8: Where should significance gating apply?

To the UNDERSTANDING/AI tier, not the SYNC tier. The deterministic core is CHEAP and should always run after detection. Significance gates Understanding proposals, not commit import.

### Q9: Can current import operate X→Y efficiently?

**No**. The current import does a full repository walk every time (O(total commits)). An explicit range `git rev-list X..Y` would be more efficient but isn't implemented. For V1, the recommendation is to add `ingestedRevision` and `RepositorySyncJob` first, then consider explicit ranges in a later increment.

### Q10: Should sync use explicit immutable target SHAs?

**YES**. Always use `toRevision = SHA`, not `branch = main`. Per ADR-061, targeting a branch name is incorrect; SHAs are immutable and enable proper concurrency handling.

### Q11: How do we handle HEAD moving Y→Z mid-sync?

Finish the current job target (B), then enqueue next job targeting new revision (C). The job targets immutable `toRevision = SHA`. If HEAD moves during sync, the completed job advances `ingestedRevision` to B, and a new job schedules for C.

### Q12: Do we need `RepositorySyncJob` now?

**REQUIRED_NOW**. The architecture gaps (no watermark, no job model, no crash recovery, no per-source isolation, orphaned rows on force-push) justify creating the job now as the smallest coherent vertical slice.

### Q13: What minimum job states are justified?

PENDING, RUNNING, COMPLETED, FAILED (4 states). SUPERSEDED could be added for coalescing but is not essential V1.

### Q14: How do we recover after crash?

Job persisted before execution + startup reaper for abandoned jobs + lease mechanism + differentiated retry semantics per failure category.

### Q15: What is replay-safe today?

Commit import and changed paths (by SHA dedup via `existsBySourceIdAndCommitHash`). Other stages (facts, events, projections) need auditing.

### Q16: How should force-push/non-fast-forward be handled?

Detect and fail safely. If `observedRevision` is not an ancestor of new `origin/HEAD`, flag freshness status and do not overwrite `observedRevision`. Require manual repair mode. V1 simply detects and reports; repair is future work.

### Q17: How should initial import work?

As a special case of `RepositorySyncJob` with `fromRevision = null`, targeting full history import from scratch.

### Q18: What happens to freshness after sync but before Understanding?

`PARTIALLY_FRESH`. This is the correct state — it bridges STALE and CURRENT. The system currently cannot represent this because no `ingestedRevision` exists.

### Q19: Should that state be `PARTIALLY_FRESH`?

**YES**. This is a central finding of the investigation. After deterministic sync X→Y but before Understanding, the state must be `PARTIALLY_FRESH`, not `STALE` and not `CURRENT`.

### Q20: How do new ADRs/Stories become discoverable after sync?

Minimum three-stage process:

1. Deterministic sync (history import) — makes commit metadata + changed paths current
2. Collection run at revision Y — generates facts/observations from docs/ADRs
3. `RepositoryContextAdapter` change — include facts in `AnalysisContext` for `get_engineering_context`; Stories need REST registration regardless

Without steps 2-3, new ADR/story content is visible only in COMMIT_DIFF/GIT_HISTORY layers.

### Q21: Can hidden read-path Git synchronization be removed once this exists?

**Migration direction identified but not implemented in V1**. Once deterministic sync with pinned workspace/revision exists, the read path could use the known `ingestedRevision` instead of live git fetch, removing the hidden synchronization debt. But this is a future migration path.

### Q22: What happens to current "Refresh understanding"?

**Eventually decomposed** into `ensure synchronized(target)` (via RepositorySyncJob) + `run Understanding(target)` (separate pipeline). This would reuse the sync capability instead of duplicating it, but the current UI combines all stages implicitly.

### Q23: Should agents eventually have an explicit sync tool?

**Eventually yes**, but with strict safety guarantees: idempotent, targeted SHA, no AI involvement, explicit opt-in, proper permissions. The MCP future contract may include `devlog://projects/{slug}/synchronization` resource or `sync_project` tool, but only after the deterministic core is solid.

### Q24: What is the smallest coherent next Story?

**Introduce `ingestedRevision` checkpoint and deterministic sync pipeline** — adding `ingestedRevision` as a first-class checkpoint, introducing `RepositorySyncJob` lifecycle (minimum fields), and enabling `PARTIALLY_FRESH` freshness state after sync but before Understanding. This is the smallest coherent vertical slice that enables the rest of the architecture.
