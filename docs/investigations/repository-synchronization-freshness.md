# Repository Synchronization & Freshness — Investigation Report

> Investigation only. No functional code was modified. Evidence gathered via
> DevLog MCP first (`get_engineering_context`, `search_project_history`),
> then verified directly against the repository (code = implementation truth).

## 1. Purpose

Answer:

> What synchronization and freshness model should DevLog use so that its
> project knowledge remains sufficiently current during active development,
> without depending on manual refresh actions?

And explain precisely why two "refresh" surfaces exist, what each does, why
their results differ, and which architecture should govern future
synchronization.

## 2. Scope & Method

- Isolated worktree `/tmp/opencode/devlog-sync-investigation`, branch
  `investigation/repository-synchronization-freshness`, based on `origin/main`
  (`2124bb3`, merge of PR #73 / story 0090). The main working tree had
  uncommitted build artifacts and belongs to another session; it was left
  untouched.
- Phase 1: DevLog MCP self-investigation (see §26).
- Phase 2: full repository audit (controllers, services, Git adapters,
  migrations, contracts, frontend, tests, ADRs).
- Runtime experiment performed *by accident of reality*: this very
  investigation ran against a stale DevLog database (see §9).

## 3. Executive Summary

DevLog becomes stale **by design of its trigger model, not by accident**: every
knowledge-updating pipeline (history import, fact collection, analysis,
proposals) is strictly request-driven. There is no scheduler, no watcher, no
event listener, no startup hook anywhere in the backend. Knowledge therefore
advances only when a human clicks **"Refresh understanding"** (or a
Maintenance remediation delegates to it).

Worse, the system **pays the cost of synchronization without harvesting it**
during reads: every `get_engineering_context` call silently performs a live
`git fetch --prune` + forced checkout of the shared workspace
(`RepositoryStructureCollector.java:100`, enrichers `:208/:237`) — but uses it
only for the `repository-structure` evidence layer. All other layers
(`GIT_HISTORY`, `COMMIT_DIFF`, `ROADMAP`, `VALIDATED_INSIGHT`) come from the
database and remain frozen at the last manual refresh. The result is a
**mixed-revision context served with no warning**: file contents at HEAD,
history several days behind.

DevLog already possesses most of the needed primitives: a per-source freshness
projection (`project_source_freshness`, statuses `CURRENT/STALE/NO_BASELINE/
UNKNOWN`), a cheap-ish HEAD resolver (`GitWorkspaceManager.resolveCurrent-
Revision`), incremental idempotent history import (hash-dedup), and an
accepted ADR-041 (*Passive Project Monitoring*) that already prescribes the
target model: selective, incremental, event-driven, provider-independent.
What is missing is the **wiring**: nothing observes HEAD autonomously, nothing
compares observed-vs-synced revisions without a click, and the read contract
exposes no freshness information at all.

Recommended direction: adopt a **change-detection + sync-job architecture**
(target), whose **first step is a small scheduled HEAD-detector adapter** plus
making freshness observable (repairing a baseline-selection drift introduced
by story 0085, and exposing freshness in the MCP contract). Polling is an
adapter, not the architecture. `get_engineering_context` must stop doing
hidden heavy synchronization and start declaring what revision it represents.

## 4. Current Refresh Surfaces

All ways a project's data advances today (verified across backend, MCP server,
frontend):

| Trigger | User-facing? | Service called | Sync mode | Side effects |
|---|---|---|---|---|
| Frontend "Refresh understanding" → `POST /api/v1/projects/{id}/understanding-executions` | Yes (`project-understanding-section.ts`) | `ProjectUnderstandingService.execute` | Full pipeline, synchronous HTTP (async AI tail) | Live git sync + history import + new Analysis + facts/observations/profile + AiTask → LLM proposals |
| Frontend "Analyze evolution" → `POST .../engineering-event-executions` | Yes | `EngineeringEventExecutionService.execute` | Same preparation, pinned revision | Same as above for commit ranges |
| Manual analysis launch → `POST /analyses/{id}/workflow` | Yes | `AnalysisWorkflowServiceImpl.start` | Collection + analysis (no git sync unless collectors do it) | Facts, profile, AiTask |
| History import → `POST /api/v1/project-history/repositories/{rid}/imports` | API-only | `ProjectHistoryServiceImpl.importHistory` | Incremental (hash dedup) | Upsert `project_commits` |
| Freshness check → `POST .../freshness-checks` | Yes ("Check freshness") | `ProjectFreshnessService.check` | Cheap probe (fetch + rev-parse) | Upsert `project_source_freshness` row |
| Maintenance "Run evaluation" → `POST .../maintenance-findings/evaluations` | Yes | `MaintenanceEvaluationServiceImpl.evaluate` | **Read-only diagnosis** (DB only, zero git) | Creates/auto-resolves findings |
| Maintenance "Refresh projection" / "Refresh missing projection" | Yes | `MaintenanceRemediationServiceImpl.refreshProjection` | Probe + persist (fetch, no checkout) | Freshness rows + finding resolved |
| Maintenance "Refresh understanding" | Yes | freshness checks + `understandingService.execute(...)` per source | Delegates to normal refresh | Full pipeline per source |
| `GET /engineering-context` / `engineering-story-context` (incl. MCP tools) | Read | `RepositoryContextEngine.build` | **Hidden live git sync on every read** | Fetch + force checkout/reset of shared workspace; **no DB knowledge writes** |
| Scheduled tasks / startup hooks / event listeners / webhooks | — | **None exist** (verified: zero `@Scheduled`, `ApplicationRunner`, `@EventListener`; ai-engine is request-driven `BackgroundTasks`) | — | — |

## 5. Normal Refresh Pipeline

Entry: "Refresh understanding" button → `POST /understanding-executions`.

```
ProjectUnderstandingController.execute                    controller :22
└─ ProjectUnderstandingService.execute                    :24
   ├─ 1. PREPARE  ProjectUnderstandingPreparationService.prepare
   │      └─ GitWorkspaceManager.synchronize(source, null)   [TX-free]
   │           git remote set-url origin <url>
   │           git fetch --prune origin          ← NETWORK
   │           git clean -fdx
   │           git checkout --force --detach <resolved>
   │           git reset --hard <resolved>
   │           git rev-parse HEAD                → resolvedRevision (immutable SHA)
   │      └─ implicit history import (TX-A):
   │           git rev-list <resolved> → per-commit git show/diff-tree
   │           upsert project_commits (dedup: existsBySourceIdAndCommitHash)
   ├─ 2. CLAIM  ProjectUnderstandingClaimService.claim   [TX-B]
   │      execution key = project+source+requestedRevision+intent+guidance
   │      partial unique index on ACTIVE executions → race-safe;
   │      loser reuses winner (REUSED outcome)
   │      Analysis.targetRevision ← prepared.resolvedRevision()   (story 0085)
   ├─ 3. WORKFLOW  AnalysisWorkflowServiceImpl.start     [orchestration, TX-C..H]
   │      ├─ collection (TX-D ⚠ holds DB tx across git I/O):
   │      │    7 deterministic collectors → Facts/Observations
   │      │    diagnostics.resolved_revisions {sourceId → sha}    (V18)
   │      ├─ deterministic analysis (read-only) + rule engine observations
   │      ├─ ProjectProfileService.build → snapshot               (V20, TX-E)
   │      │    snapshot.resolved_revisions = de-facto knowledge baseline
   │      ├─ context assembly + deterministic knowledge selection
   │      └─ AiTask create + submit to ai-engine                  (TX-F/G/H)
   └─ 4. ASYNC TAIL (ai-engine, FastAPI BackgroundTasks)
          single LLM hop: InsightGenerationService (structured output,
          grounding validation, ≤1 corrective retry)
          → callback POST /ai/tasks/{cid}/result                 [TX-I]
             pessimistic lock, replay-idempotent, ValidatableProposals stored
          → insights only after HUMAN promotion (ProposalPromotionService)
```

Properties: creates a **new immutable snapshot per run** (facts FK-scoped to
analysis); history globally deduped per source; one LLM call per completed
run; everything else deterministic.

## 6. Maintenance Refresh Pipeline

Entry: "Context maintenance" card → "Run evaluation".

```
POST /maintenance-findings/evaluations
└─ MaintenanceEvaluationServiceImpl.evaluate        [@Transactional, ZERO git]
   ├─ freshness summary scan  ← reads PERSISTED project_source_freshness rows
   ├─ unchecked-source scan   ← counts sources never checked
   ├─ stale human-input scan  ← DB (age ≥30d && lag ≥14d)
   ├─ trusted-knowledge duplicate audit (deterministic clustering)
   ├─ auto-resolve sweep (conditions no longer detected)
   └─ AI agents (CrossSurfacePatternDetection, DuplicateAmbiguityResolution)

Finding types (7 enum values, 6 produced):
  STALE_PROJECT_UNDERSTANDING   HIGH   → action "Refresh understanding"
  MISSING_PROJECTION_REFRESH    MEDIUM → "Refresh missing projection"
  PROJECTION_REFRESH_GAP        —      → "Refresh projection"  ⚠ NEVER CREATED
                                           (dead-end type, no producer)
  STALE_HUMAN_CONTEXT_INPUT     MEDIUM → "Archive stale input"
  TRUSTED_KNOWLEDGE_{EXACT_DUPLICATE, SEMANTIC_DUPLICATE, OVERLAP_REVIEW}
                                → merge/resolve actions

Remediations:
  refresh-projection / refresh-missing-projection:
      for EACH active source → ProjectFreshnessService.check
        └─ resolveCurrentRevision: set-url + fetch --prune + rev-parse
           origin/<defaultBranch>^{commit}      ← LIVE NETWORK FETCH
        └─ upsert freshness row (status CURRENT/STALE vs snapshot baseline)
      → resolves finding. No checkout, no ingestion, no analysis.
  refresh-understanding:
      freshness checks for all sources, THEN
      understandingService.execute(projectId, {sourceId, null, null}) per source
      = EXACT same entry point as normal refresh.
```

Key nuance: **evaluation itself never touches Git** — it diagnoses from
previously persisted freshness rows. Freshness rows advance only through
explicit checks (freshness endpoint or maintenance remediations).

## 7. Refresh Comparison Matrix

| Capability | Normal Refresh | Maintenance "Refresh" |
|---|---|---|
| Fetch HEAD (network) | Yes (`fetch --prune` inside synchronize) | Only via remediation actions (probe) / evaluation: never |
| Import commits | Yes (incremental, hash-dedup) | No |
| Import diffs (facts) | Yes (collectors) | No |
| Update repository structure | Workspace checkout (transient, read-time anyway) | No |
| Rebuild projections (freshness rows) | **No** ⚠ | Yes (that's mostly what it is) |
| Run enrichment | Deterministic enrichers during collection | No |
| Update knowledge (facts/observations/profile) | Yes (new snapshot) | No |
| Recompute timeline | New analysis only; no timeline recompute | No |
| Trigger AI | Exactly 1 LLM hop (proposal generation) + 2 assessment agents on evaluation | Evaluation: AI agents only; remediation: delegates to normal path |
| Delete/recreate data | `clean -fdx` / `reset --hard` on workspace only | No |
| Repair inconsistencies | Workspace self-heal (reclone fallback) | Dedup merges; finding resolution |
| Diagnose (findings) | No | Yes |

The two operations are **complementary halves of a synchronization**, not two
strengths of one operation.

## 8. Why Two Pipelines Exist — Verdict

**Primary: A — INTENTIONAL_SEPARATION (mis-named), with confirmed B — IMPLEMENTATION_DRIFT symptoms.**

Evidence for A: they were built as different domain capabilities —
*collection/analysis* (stories 0012→0085 lineage) versus *context health*
(stories 0052–0073, ADR-053/054). "Refresh projection" in Maintenance means
"update the freshness checkpoint row", not "rebuild context projections";
"refresh understanding" deliberately delegates to the normal entry point
(`MaintenanceRemediationServiceImpl:146–149`). Semantically they were never
meant to be the same operation.

Evidence of drift (B):
1. **Baseline-comparability break**: `findLatestComparable`
   (`ProjectProfileSnapshotRepository.java:20`, story 0021) selects baselines
   with `analysis.targetRevision IS NULL`. Story 0085
   (`ProjectUnderstandingClaimService.java:64`) now always persists the
   resolved revision → **new describe-project analyses can never be selected
   as freshness baselines** → freshness checks degenerate toward
   `NO_BASELINE`. Written pre-0085, never revisited.
2. `PROJECTION_REFRESH_GAP` has UI label, workflow support and remediation
   guard — **but no producer anywhere** (dead-end type).
3. Completing a normal refresh does **not** update `project_source_freshness`;
   only Maintenance actions do. The sync pipeline and the freshness projection
   have decoupled.
4. `TemporalAssessmentService` (story 0083) has no production caller.
5. Read paths perform hidden workspace synchronization unrelated to any
   knowledge update.

So: two legitimately different operations, wearing the same word ("refresh"),
whose integration seams have drifted. Not C alone (normal refresh is not a
subset of Maintenance — quite the opposite for ingestion), not D (Maintenance
is not a deeper rebuild; it cannot ingest anything).

## 9. Failure Scenario — Why the Recent Investigation Was Invisible

Runtime evidence gathered during this very investigation (2026-08-25):

- Repo HEAD: `2124bb3` (merge PR #73, story 0090 `search-project-history`,
  merged 2026-08-25 era; stories 0086–0090 docs exist in the working tree).
- `search_project_history("0090")` → **0 matches**. Latest imported commits in
  DevLog date from 2026-08-19 (story 0085 era).
- `get_engineering_context` returned 60 evidence items: `SOURCE_FILE` carried
  `revision: 2124bb3…` (**current HEAD** — live workspace fetch), while
  `GIT_HISTORY`/`COMMIT_DIFF` items stopped at 2026-08-19 (**T−6 days**) and
  no evidence referenced stories 0086–0090. `metadata.warnings` contained only
  budget/truncation notices.

Mechanism: the investigation existed only as new commits + new markdown files.
Without a manual understanding refresh: `project_commits` lacked the commits
(history layer blind), no collection ran over their diffs (facts blind), the
story was never registered as core knowledge (roadmap layer blind) — while
the structure collector happily fetched and served HEAD file content. **Same
response, two different worlds, no warning.** That is the exact production
incident described in the mission.

## 10. Current Revision Model

Persisted revision/checkpoint fields (migrations audited):

| Field | Table | Meaning | Persisted | Exposed |
|---|---|---|---|---|
| `target_revision` (V16, `updatable=false`) | `analyses` | Immutable revision actually observed by a source-scoped analysis (post-0085) | Yes | Indirectly (profile/freshness DTOs) |
| `resolved_revisions` JSONB (V18) | `analysis_execution_diagnostics` | `{sourceId → sha}` observed during collection | Yes | Diagnostics DTO |
| `requested_revision` + `resolved_revisions` (V20) | `project_profile_snapshots` | De-facto "knowledge baseline" of a completed analysis | Yes | Profile response; freshness baseline lookup |
| `requested_revision`, `current_revision`, `baseline_revision`, `status`, `checked_at` (V32) | `project_source_freshness` | Last freshness check per source | Yes | REST GET `/freshness-checks/latest`; engineering-story-context summary |
| `last_synchronized_at` (V15) | `sources` | Wall clock of last collection (NOT a revision) | Yes | Source DTO, UI |
| `commit_hash` (+ parents/files) (V29) | `project_commits` | Imported history (facts, no "current" pointer) | Yes | Search/MCP resources |
| `base_commit`/`target_commit` (V33/V36) | events/stories/scopes | Analyzed ranges | Yes | Resources |
| `SynchronizedWorkspace.resolvedRevision`, collector metadata `resolvedRevision` | runtime only | Revision observed by the last synchronize/read | **No** | Per-evidence metadata only |
| Global "knowledge revision" | — | **Does not exist** | — | — |

## 11. Current Freshness Model

- States (`ProjectFreshnessStatus`): `NO_BASELINE`, `UNKNOWN`, `CURRENT`, `STALE`.
- Guidance (`ProjectRefreshGuidance`): `ESTABLISH_BASELINE`,
  `REFRESH_NOT_NEEDED`, `REFRESH_RECOMMENDED`, `VERIFY_BASELINE`.
- Comparison axis: live `origin/<defaultBranch>` SHA vs
  `latestComparableSnapshot.resolvedRevisions[sourceId]`.
- Updated **only** by explicit freshness checks (3 call sites, all in
  MaintenanceRemediationServiceImpl) or the freshness endpoint.
- Consumed by: maintenance evaluation (creates `STALE_PROJECT_UNDERSTANDING`),
  engineering-story-context summary. **Not consumed by**
  `get_engineering_context` (contracts carry zero freshness fields).
- Derived, non-persisted sibling: `TemporalAssessment` (`CURRENT /
  SUSPECTED_STALE / UNKNOWN`, story 0083) — currently orphaned (no caller).
- Known gap: §8-B1 baseline drift; plus freshness is **never refreshed by the
  sync pipeline itself**, so "FRESH/STALE" can be false in both directions
  between checks.

What DevLog knows about being stale: **only what a human asked it to compute
recently.** Stale detection today = PARTIAL (capability exists, activation is
manual, propagation to the read contract is absent).

## 12. Incremental Capabilities (today)

- **History import**: genuinely incremental in effect — `git rev-list` walks
  to HEAD, inserts skip existing hashes. Cost grows with repo size per run
  (full walk), but writes are deltas.
- **Collection/analysis**: NOT incremental — each refresh builds a fresh,
  complete snapshot (new facts/observations/profile). Old snapshots retained;
  nothing is diffed against the previous one. There is no `oldRev..newRev`
  range concept in collection (only in engineering-event scopes).
- **Freshness check**: O(1) probe (fetch + rev-parse) — the truly incremental
  primitive.
- **Enrichment** (file content / Java symbols): lazy, computed per read in
  `RepositoryContextEngine`, not persisted, not revision-invalidated — it just
  re-reads whatever the workspace currently is.

## 13. Rebuild / Repair Capabilities

- Workspace self-healing: `clean -fdx` + `reset --hard`, delete-and-reclone on
  failure (`GitWorkspaceManager:139–154,194–234`). This is repair of the
  *mirror*, not of knowledge.
- History import is safely repeatable (idempotent upserts) — usable as a
  history reindex.
- Knowledge rebuild = rerun understanding (new snapshot; old kept). No in-place
  projection rebuild from persisted data exists; every derived artifact today
  requires re-reading Git.
- Trusted-knowledge repair exists as dedup merges (Maintenance).

## 14. Expensive Operations

Exactly one LLM hop per refresh (insight proposals, ai-engine) + optional
assessment agents during evaluation. Everything else is deterministic CPU/git.
Read-time costs: every `get_engineering_context` pays a network fetch +
checkout + rescan of the repository tree + enrichment parsing — significant,
unbounded, repeated per call, and **discarded** (nothing ingested).

Therefore the cheap/expensive split the mission asks for maps cleanly onto the
existing code: **HEAD probing and history import are cheap and deterministic;
collection snapshots and LLM proposals are expensive.** They should not share
one trigger.

## 15. Concurrency, Idempotency, Crash Recovery, Transactions

- **Locks**: per-source JVM `ReentrantLock` around git operations
  (`GitWorkspaceManager:29–30,46–50`). Single-instance only; multi-instance
  deployments lose mutual exclusion on the shared workspace directory.
- **Claim race-safety**: partial unique index on active understanding
  executions + flush-violation recovery → single winner, loser REUSED.
- **Idempotency**: strong for commits (hash dedup), facts (fingerprint
  uniqueness within analysis), callback (terminal-replay ack + pessimistic
  task lock). Weak overall: a second refresh creates a whole new snapshot
  (acceptable — snapshots are immutable by design).
- **Crash windows**: crash between claim TX-B and workflow start leaves a
  PENDING analysis that is *actively reused forever* (ACTIVE set includes
  PENDING; no reaper exists) → permanent DoS on that execution key until DB
  surgery. Crash after submit before callback: engine retries ~5×/≈1.5 s on
  409-NOT_READY only → BE restart loses completion → IN_PROGRESS zombie. No
  watchdog exists.
- **Transaction boundaries**: 9 distinct units (TX-A…I, §5). One anomaly:
  TX-D (`KnowledgeCollectionServiceImpl.collect`) holds a DB transaction
  across another git synchronize + all collectors.
- **Concurrent refresh + maintenance + future auto-sync**: nothing prevents
  interleaving beyond the git lock; maintenance remediation wraps git + full
  workflow start inside one `@Transactional` (long-held tx risk).

## 16. Observed Runtime Anomalies Explained

- `repository-structure → HEAD`, `git-history → HEAD−n`: §9 mechanism
  (live fetch for structure vs DB rows for history). Confirmed in code.
- New investigation invisible to `get_engineering_context`: §9.
- These are not bugs in a pipeline — they are **the absence of a
  reconciliation invariant between layers**.

## 17. Does a Single `lastSyncedRevision` Suffice?

No. The audit shows why multiple checkpoints are necessary — and the mission's
hypothesized shape is nearly right:

```
Per Source (identity used by ADR-061: Source + immutable revision):
  observedRevision      ← last HEAD seen by a detector/check       (missing)
  gitHistoryRevision    ← highest imported commit / import watermark (implicit)
  analysisBaseline      ← snapshot.resolvedRevisions[source]         (exists)
  freshnessCurrent      ← last probed origin/defaultBranch SHA       (exists)
  enrichmentRevision    ← n/a today (enrichment is stateless/lazy)
```

One scalar cannot answer "is my *context* fresh?" because ingestion and
analysis progress independently (structure can be at HEAD while knowledge is
6 days behind — §9). Minimum viable set: **observed (repo) vs ingested
(history/knowledge)** per source, with the analysis baseline retained as the
semantic anchor already defined by ADR-061. `PARTIALLY_FRESH` falls out
naturally: layers disagree.

## 18. Proposed Freshness State Model

Justified states (mapping to existing enums where possible):

| State | Invariant | Maps to |
|---|---|---|
| `FRESH` | observed == ingested == baseline | `CURRENT` |
| `STALE` | observed ≠ ingested, ingestible deterministically | `STALE` + `REFRESH_RECOMMENDED` |
| `SYNCING` | a sync job is active for this source | **new** (no job concept today) |
| `PARTIALLY_FRESH` | some layers current, others behind | derivable from per-layer watermarks |
| `FAILED` | last sync attempt failed; prior state preserved | **new** (sync failures currently vanish into logs) |

Invariants: transitions are caused only by observed revisions or completed
jobs; `FAILED` never destroys prior knowledge; freshness is derived data,
recomputable from checkpoints + job history.

## 19. Change Detection — How DevLog Can Know HEAD Cheaply

It already knows how: `resolveCurrentRevision` (set-url + `fetch --prune` +
`rev-parse origin/<branch>^{commit}`). Cost today is dominated by the full
fetch. Cheaper adapters, in order:

1. `git ls-remote origin <ref>` — single round-trip, no workspace mutation,
   no clone needed. Ideal detector primitive.
2. Local `rev-parse` on the managed clone + throttled `ls-remote` — near-zero
   cost when unchanged (the common case for idle projects).
3. Existing `fetch --prune` — acceptable as the *sync* step once a change is
   detected, not as the probe.

So: **yes, cheap HEAD detection is possible** with existing building blocks.

## 20. Change Detection ≠ Synchronization

Current architecture fuses them: the only observer of HEAD is the pipeline
itself (synchronize-on-write) or a manual check. Proposed separation:

```
RepositoryChangeSource (polling adapter | hook | webhook | workspace event)
        ↓
RepositoryChangeDetector  (cheap observedRevision probe, per source)
        ↓
RepositoryChanged (observed ≠ last synced)   ← pure signal, no side effects
        ↓
SynchronizationPlanner (significance triage: import-only vs full understanding)
        ↓
RepositorySyncJob (from→to, reason, status, retries)
        ↓
Deterministic ingestion → freshness checkpoint update
        ↓ (optionally, by policy) understanding/AI work
```

Answer to the mission question: **yes, detection must be separated from
processing.** It is the only way idle projects stay free, active projects get
bounded latency, and future sources (hooks/webhooks/Workspace) plug into the
same planner. ADR-041 (Accepted) already prescribes exactly this shape
("Passive monitoring observes project changes… schedules targeted analyses
when meaningful changes are detected"; incremental processing; significance
detection; provider independence).

## 21. Detection Source Options

| Source | Fit | Notes |
|---|---|---|
| Polling (scheduled local/`ls-remote` probe) | **Best V1 adapter** | simple, local, provider-independent, cheap (~1 RTT/source when idle); latency bounded by interval; wasted checks negligible at DevLog scale |
| Git hooks (post-commit/post-merge/post-checkout) | V2 | misses remote updates, IDE/agent commits vary, install/portability burden across worktrees; fine as an accelerator, not a guarantee |
| GitHub webhooks | V2/V3 | only when remote-hosted; needs auth/signature/project mapping (out of scope); DevLog must stay provider-neutral per ADR-041 |
| Workspace / Developer OS events | V3 target | `RepositoryChanged` as a Workspace domain event fits the Agent Orchestrator vision; DevLog standalone must not require it (ADR-041 keeps repository sync as valid trigger) |

Polling verdict: **ADAPTER**, not architecture. As long as the detector only
emits `RepositoryChanged` into a planner, swapping polling for hooks/webhooks
later changes zero downstream code.

## 22. Synchronization Lifecycle (proposed concept — not implemented)

`RepositorySyncJob {source, fromRevision, toRevision, reason, status,
attempts, startedAt, completedAt, failure}` gives: observability (§24),
concurrency semantics, crash recovery, and a natural home for the existing
deterministic stages. Utility: high — today's equivalent state is scattered
across Analysis rows (which conflate "sync" with "AI analysis") and log lines.

- **Concurrency**: target-revision semantics make HEAD-moves-during-sync safe:
  finish `B`, observe `C`, enqueue/coalesce `B'→C` (hash-dedup import makes
  overlap harmless). Never restart from scratch; never drop intermediate SHAs.
- **Idempotency**: inherited from hash-dedup + fingerprint uniqueness +
  replay-safe callback; job layer adds attempt accounting.
- **Crash recovery**: job row survives process death → next scheduler pass
  resumes/requeues instead of leaving PENDING zombies (fixes §15 gaps).

## 23. Dirty-State & Lazy Enrichment

Existing nearest analogues: freshness `STALE` + guidance
(`REFRESH_RECOMMENDED`) is a hand-rolled dirty flag; `TemporalAssessment.
SUSPECTED_STALE` is an unwired dirty marker for insights. No projection
invalidation exists.

Recommended conceptual model (compatible with current code): ingestion marks
`dirty` per derived layer; expensive layers (LLM proposals, future vector
indexes) refresh lazily/on-demand/on-significance — exactly ADR-041's
significance gating and ADR-060's deterministic-core/probabilistic-shell
split. Enrichment today is already lazy-at-read; making it revision-aware
(serve at recorded revision, warn if behind) would close the §9 mixed-revision
hole without new infrastructure.

## 24. `get_engineering_context` — Current Behavior & Options

Today (verified): performs hidden live `fetch --prune` + forced checkout per
call; serves DB knowledge of arbitrary age; exposes per-evidence revisions
only; emits zero staleness warnings; performs no DB writes.

| Option | Latency | Predictability | Freshness | Agent UX | Side effects | Verdict |
|---|---|---|---|---|---|---|
| A: return state + stale warning | low | high | declared, not improved | honest | none (after removing hidden sync) | **V1** |
| B: check → sync-if-stale → wait → fresh | unbounded (network + full pipeline + LLM) | low | best | timeouts likely; read triggers AI spend | heavy mutation from read path | reject for MCP read semantics |
| C: check → enqueue sync → current + warning | low | medium | improves asynchronously | good; agent can re-poll | bounded, explicit | **V2** (requires sync-job model) |
| D: read-only at pinned recorded revision + freshness block | low | high | declared precisely | excellent provenance story | none | direction for enrichment/RAG |

Recommendation: **A now, C when sync jobs exist, never B as implicit
behavior.** A read operation must not mutate workspaces or spend AI budget as
a side effect (§37 concern is validated by the current hidden-fetch behavior).
Explicit opt-in sync remains available via a dedicated tool/action.

## 25. MCP Freshness Contract & Resource

Gaps in the current contract (`EngineeringContextMetadata`: counts, tokens,
digest, warnings only): no `repositoryRevision`, no `contextRevision`, no
`freshnessStatus`, no lag, no per-source breakdown. Data to expose already
exists in memory during a build (workspace `resolvedRevision`, snapshot
baselines, freshness rows).

`devlog://projects/{slug}/freshness` — already listed as a candidate in
`docs/mcp-resource-candidates.md:20` — has **real utility**: it gives agents
the exact decision inputs they need (`fresh → use; stale → request sync or
fall back to repo; syncing → wait; failed → warn`, §40) without inflating the
main context payload. Recommended payload: per-source `{status, guidance,
observedRevision, baselineRevision, checkedAt, lag}`.

## 26. DevLog MCP Self-Evaluation

- `get_engineering_context` (intent: synchronization/freshness/…): returned
  highly relevant roadmap stories #43/#45/#53/#54, projection/baseline code
  evidence, and the resolvedRevision-bearing source file — but **zero ADR
  content** (ADR-041/059/061 are decisive and were found only via repo
  fallback), no freshness metadata about itself, and mixed-revision evidence
  presented without warning.
- `search_project_history`: reconstructed the refresh/maintenance story line
  (0021, 0054, 0067/68/70/71, 0083, 0085) well; but **could not see the
  repository's own newest PR** (query "0090" → 0 results) — the very failure
  mode under investigation, reproduced on DevLog itself.
- Resources: no freshness resource exists yet; commit/story resources useful.

**Verdict: PARTIALLY** — DevLog explains its pipelines' *history* better than
its *currency*. Notably, ADR discovery via MCP remains weak (mission §8
observation confirmed).

## 27. Architecture Options Compared

| Criterion | A Manual-improved | B Scheduled full refresh | C Detection + existing refresh | D Detection + SyncJob | E Event-driven external |
|---|---|---|---|---|---|
| Complexity | minimal | low | medium | medium-high | high |
| Robustness | poor (human memory) | poor (blind cost, zombies multiply) | medium (no lifecycle state) | good | good (source-dependent) |
| Freshness | manual | bounded, wasteful | bounded, good | bounded, tunable per project | event-latency |
| Cost | human attention | **AI budget burns on idle repos** ⚠ | low (probe + real syncs) | low + significance gating | lowest steady-state |
| Scalability (multi-project) | n/a | terrible | good | good | best |
| Developer OS alignment (ADR-041/042) | no | no | partial | **yes** | yes (as sources) |
| Implementation risk | none | medium | low-medium | medium | high now |
| Anti-pattern risk | — | §29 naive cron | low | low | vendor coupling |

Rejected: **B** as architecture — a `@Scheduled refreshAllProjects()` full
refresh (§58) is unacceptable: it spends the single expensive resource (LLM
runs) indiscriminately, ignores idle projects, stacks zombie analyses after
crashes, and encodes "sync == full AI analysis" — the exact conflation to
remove. Acceptable only as the thin *detector* variant: scheduled probe →
compare → targeted job.

## 28. Target Architecture & First Step

**TARGET_ARCHITECTURE = D — SYNC_JOB_ARCHITECTURE (change detection +
planner + sync jobs + freshness checkpoints), with E's sources as later
adapters.**

```
[Detector adapters: scheduled ls-remote/rev-parse → hooks → webhooks → Workspace events]
        ↓ RepositoryChanged(source, observed, expected)
[SynchronizationPlanner]  significance triage (ADR-041): import-only vs understanding
        ↓ RepositorySyncJob(from,to,reason,status)
[Deterministic stage: fetch → incremental import → checkpoint update]   ← cheap, always safe
        ↓ dirty markers
[Deferred stage: understanding snapshot + LLM proposals]                ← expensive, gated
        ↓
[FreshnessCheckpoint per source] → REST + MCP resource + context metadata
```

This realizes Accepted ADR-041 (passive monitoring, incremental, significance,
cost control, provider neutrality), ADR-060 (deterministic authority), and
extends ADR-061's `(Source, revision)` identity into operational checkpoints.

**FIRST_IMPLEMENTATION_STEP (candidate story):**
*"Project freshness checkpoints: restore baseline comparability (story-0085
drift), record observedRevision at every synchronize, and expose freshness in
engineering-context metadata + a `devlog://projects/{slug}/freshness`
resource."*
Rationale: every later component (detector, planner, jobs) compares against
checkpoints; checkpoints are broken/unexposed today (§8-B1, §11, §25); the
step contains no scheduling and no new pipeline — pure observability and one
bug-fix-sized query correction — while immediately enabling agents to stop
trusting silent stale contexts.

Follow-on steps (ordered): ② scheduled local HEAD-detector adapter emitting
`RepositoryChanged` (polling as adapter); ③ `RepositorySyncJob` + planner with
import-only tier; ④ significance-gated deferred understanding; ⑤ hook/webhook/
Workspace adapters.

## 29. Terminology Recommendation

Retire the overloaded "refresh". Map to three verbs matching real semantics:

- **SYNC** — bring ingested state to observed HEAD (detect → import →
  checkpoints). Deterministic, cheap, safe to automate. (Today: only the
  front half of "Refresh understanding".)
- **REBUILD** — recompute derived artifacts from persisted or re-read data
  (new understanding snapshot, future projection/vector rebuilds). Today:
  "Refresh understanding" whole.
- **REPAIR** — fix inconsistencies without new observations (workspace
  self-heal/reclone, history reindex, dedup merges, zombie reaping). Today:
  parts of Maintenance.

UX translation: a **Synchronization** panel (Status: FRESH/STALE; Repository
`abc123` vs DevLog `abc123`; last synchronized 2 min ago; Sync now = force/retry)
and a **Maintenance** panel (Rebuild projections, Repair index, dedup) —
labels matching semantics (today's "Refresh projection" actually writes a
freshness row, which no user would guess).

## 30. Operational Notes

- **Multi-project**: probes/jobs are per-source by construction; idle projects
  cost one cheap round-trip per interval. No global refresh, ever.
- **Standalone V1**: detector+jobs live inside DevLog backend; no Workspace
  dependency (ADR-041 lists repository sync itself as a valid trigger).
- **Security (future webhooks)**: authenticity/signature/project mapping noted
  as requirements only.
- **Observability to add with jobs**: sync duration, lag (commits & time),
  last success/failure per source, pending/failed job count, observed vs
  synced revision gauges — none exist today (no scheduler ⇒ nothing to
  observe).
- **Tests**: good unit/integration coverage exists per component
  (GitWorkspaceManager, ProjectHistory*, ProjectFreshness*, Maintenance*,
  ProjectUnderstanding*). Gaps: no test pins baseline comparability against
  story-0085 semantics (drift §8-B1 proves it), no end-to-end
  "commit → detect → sync → fresh context" test, no concurrency/crash-recovery
  tests, no revision-consistency test for context layers.
- **Volume**: qualitative — DevLog's own repo ≈ 90 stories / ~600+ commits /
  moderate diffs; full refresh ≈ seconds of deterministic work + 1 LLM call;
  probe ≈ one network round-trip. Poll-everything is comfortably cheap at this
  scale; the argument against B is semantics and AI cost, not probe load.

## 31. ADR Audit & Candidate ADR

Relevant ADRs found via repository (not surfaced by MCP): ADR-041 Passive
Project Monitoring (Accepted — the architectural north star),
ADR-053/054 Context Maintenance (+Agent), ADR-055 Context Enrichment &
Projection (Proposed), ADR-059 Temporal Knowledge & Safe Freshness Degradation
(Proposed), ADR-060 Deterministic Core / Probabilistic Intelligence
(Proposed), ADR-061 Deterministic Repository Observation Baselines (Proposed).

**Candidate ADR**: *"Repository Synchronization Lifecycle and Freshness
Checkpoints"* — Problem: knowledge currency depends on manual triggers; HEAD
observation is fused into pipelines and read paths; context mixes revisions
silently. Decision to formalize: change detection separated from
synchronization; per-source `(observedRevision, ingestedRevision)` checkpoints
as first-class state; sync jobs with explicit status/retry/coalescing; read
paths never mutate; no silent stale context (contract carries freshness).
Alternatives: scheduled full refresh (rejected — cost/semantics), manual-only
(rejected — ADR-041 already rejects), implicit sync-in-read (rejected — read
purity). Would supersede/extend ADR-041's trigger section and unblock
ADR-059/061 follow-ups.

## 32. Answers to Mandatory Questions

- **Q1** Why manual refresh is needed: zero autonomous triggers exist; every knowledge path starts at an HTTP request (§4).
- **Q2** Normal refresh: prepare(git sync+import) → claim analysis → deterministic collection/profile → submit AI task → human-gated proposals (§5).
- **Q3** Maintenance: read-only diagnosis over persisted freshness/duplicates creating findings; remediations either update freshness rows (probe) or delegate to normal refresh; dedup/archive repairs (§6).
- **Q4** Results differ: they are different operations sharing a label; evaluation never touches git, remediation probes never ingest, and the normal path never updates freshness rows (§7–8).
- **Q5** Should both continue? Yes — but renamed SYNC vs REBUILD/REPAIR with the missing bridge (automatic sync) added (§29).
- **Q6** `sync` = detect+import+checkpoint (front half of understanding, automated).
- **Q7** `rebuild` = new understanding snapshot / future projection recomputes.
- **Q8** `repair` = workspace heal, history reindex, dedup merges, zombie reaping.
- **Q9** Knows it is stale? PARTIAL — computable on demand, never automatic, never exposed on the read path (§11).
- **Q10** Cheap HEAD check? Yes — `ls-remote`/local rev-parse; current probe over-fetches (§19).
- **Q11** Detector: a small backend component (scheduled adapter V1), per-source.
- **Q12** Executor: RepositorySyncJob runner in the backend (deterministic tier inline; expensive tier deferred).
- **Q13** Polling: ADAPTER (§21).
- **Q14** Checkpoints: minimum observed vs ingested per source + analysis baseline (ADR-061 identity); one global scalar insufficient (§17).
- **Q15** PARTIALLY_FRESH: per-layer watermarks disagree (e.g., structure@HEAD, history@T−n) (§17–18).
- **Q16** Should getContext trigger sync? Never implicitly/heavily; declare freshness (A), enqueue explicitly later (C) (§24).
- **Q17** MCP must expose: repositoryRevision, contextRevision/baselines, freshnessStatus, lag, warnings; plus `devlog://projects/{slug}/freshness` (§25).
- **Q18** Avoid useless full refreshes: detection-gated, significance-triaged, per-source jobs (§27–28).
- **Q19** Crash recovery: job rows + reaper; today: PENDING zombies reused forever, callback window lost (§15, §22).
- **Q20** Commits arriving mid-sync: target-to-specific-SHA jobs; coalesce/enqueue remainder; hash-dedup makes overlap harmless (§22).
- **Q21** Best long-term: D (sync-job architecture) with E's event sources as adapters (§28).
- **Q22** Smallest coherent first step: freshness checkpoints — fix comparability drift, record observedRevision, expose freshness in contract/resource (§28).

## 33. Success Criterion Check

The reframing requested by the mission is achieved: the problem is no longer
"how to auto-click Refresh" but *"how DevLog observes repository change
(detector), plans synchronization (planner/jobs), advances layered projections
(deterministic tiers + dirty markers), and knows which freshness level it can
guarantee (checkpoints + contract)"* — with polling confined to being the
first detector adapter, never the architecture.

---

*Investigation artifacts: isolated worktree
`/tmp/opencode/devlog-sync-investigation`; branch
`investigation/repository-synchronization-freshness`; base `2124bb3`
(origin/main). No functional code modified.*
