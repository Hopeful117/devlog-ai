# Repository Analysis — Story 0093

## 1. DevLog MCP findings before implementation

Recorded against the rebuilt stack at `main @ de890f8` (Stories 0091+0092
deployed and verified behaviorally).

### get_engineering_context (intent: deterministic synchronization boundary)

Returned:

- project snapshot (devlog-ai) with human context notes;
- **freshness metadata** introduced by 0091 (status/revisions/warnings) —
  proof the deployed runtime contains Stories 0091/0092;
- evidence selected almost exclusively from `GIT_HISTORY` (COMMIT entries with
  `devlog://projects/devlog-ai/commits/<sha>` resources) and `COMMIT_DIFF`
  (CHANGED_FILE entries).

Freshness at investigation time: `STALE`,
`currentRevision = de890f8b…`, `baseline.analyzedRevision = a31ddba6b…`,
guidance `REFRESH_RECOMMENDED`.

Useful: freshness block directly framed the X/Y reasoning; commit resources
grounded the "what is already ingested" question.

Missing / where manual inspection was still necessary:

- no ADR/Engineering-Story/document evidence surfaced on this path (known
  read-path gap: `RepositoryContextAdapter` supplies empty facts/observations;
  explicitly NOT fixed in this Story);
- nothing in context describes history-import internals (dedup mechanism,
  transaction boundaries), job infrastructure, or probe scheduling — all had to
  be read from source.

### search_project_history

Story 0092's runtime proof stands: after detection advanced the observed
revision to an external commit Y, searching Y's commit message returned zero
results — deterministic knowledge had not moved. This Story makes exactly that
query truthful after sync, without any user action or AI run.

## 2. Current lifecycle trace (code)

### Detection (ADR-062 outer adapter, Story 0092)

`ScheduledRepositoryChangeDetector.detectRepositoryChanges()` — every active
`GIT_REPOSITORY` Source: `LsRemoteRepositoryRevisionProbe.probeHead()` (one
`git ls-remote`, read-only, URL never logged) → compare with previous
observation → `ProjectFreshnessService.recordObservedRevision()`. Detection
never imports/synchronizes/analyzes.

### Freshness persistence

`ProjectSourceFreshness` (`project_source_freshness`, V32): one row per Source
(unique), holding `requested/current/baseline_revision`, `status`, `guidance`,
`checked_at`. `ProjectFreshnessClassifier.classify(baselineExists, current,
baseline)` produced only NO_BASELINE/CURRENT/STALE/UNKNOWN.
`recordObservation()` (Understanding completion) forces CURRENT by equating
baseline to observed — it never touched ingestion because ingestion did not
exist yet.

### Deterministic history import

`ProjectHistoryServiceImpl.importSynchronizedHistory()`:
`historyProvider.readHistory(workspace.path(), resolvedRevision)` → for each
commit `existsBySourceIdAndCommitHash` dedup → persist `ProjectCommit` +
parents + `ChangedFile` rows. Persisted payload = SHA, author name/email,
authored/committed timestamps, subject/full message, root/merge flags,
numstat totals, changed paths (type/old/new/binary/±). No diff content, no
symbols, no semantics — exactly the minimum SYNC payload identified by the
investigation. Uniqueness `(source_id, commit_hash)` makes replay idempotent.

### Workspace synchronization

`WorkspaceManager.synchronize(source, revisionExpr)` resolves the expression
to a canonical immutable commit, checks out detached, returns
`SynchronizedWorkspace{sourceId, path, resolvedRevision}`. Passing a full SHA
therefore pins Phase 1 to the job's immutable target.

### Transaction-boundary debt confirmed

Existing entry points wrap clone/fetch inside `@Transactional`
(`importHistory(UUID, String)` holds a DB tx across network Git). The new
pipeline must not reproduce this: synchronize (network) runs outside any
transaction; persistence runs inside its own short one.

### Job infrastructure

None existed (no outbox/job tables; only `@Scheduled` detectors and
user-initiated endpoints). `RepositorySyncJob` is therefore new, modeled after
entity conventions (`UUID` PK, auditing listeners, enum-as-string columns,
FK cascade to project/source).

## 3. Design decisions grounded in this analysis

1. **Claim returns an immutable snapshot record** (`SyncTarget`) resolved
   inside the claim transaction — the executor never touches lazy entities
   outside a session and cannot observe a moving branch.
2. **Checkpoint ordering**: `ingestedRevision` advances BEFORE the job is
   marked COMPLETED; crash between the two writes re-runs a replay-safe import
   instead of losing a completed sync. Reverse order could strand ingested
   work behind a COMPLETED job forever.
3. **Dedup guard at scheduling**: `existsBySourceIdAndStatusIn(PENDING,
   RUNNING)` prevents job spam while a cycle-long sync is in flight.
4. **Ancestry guard** via `git merge-base --is-ancestor` inside the
   synchronized workspace: incremental jobs whose from-revision is not an
   ancestor of the target fail safely (possible rewrite); V1 offers no repair.
5. **Failure sanitization**: persisted failure = exception simple name +
   message with `scheme://…` sequences redacted, bounded to 300 chars —
   mirrors the existing convention of never logging repository URLs.
6. **Recovery**: `ApplicationReadyEvent` requeues RUNNING jobs left by a dead
   process (single-instance deployment assumption carried over from 0092);
   PENDING jobs are naturally picked up by the scheduled claimer.
7. **PARTIALLY_FRESH placement**: classification gains a fourth input
   (persisted ingested revision); observations preserve — never wipe — the
   stored checkpoint, so repeated detector cycles cannot regress it.

## 4. Per-source isolation

Jobs, checkpoints and dedup are keyed by `source_id`; aggregate freshness
remains a per-source summary (`uncheckedSourceCount` preserved). A change in
source A schedules work only for A; source B's rows are untouched.

## 5. What remains outside SYNC (verified in code)

Collection/facts (`DocumentationCollector`), profile build, AI tasks,
proposal validation, story registration (REST), ADR discovery — none are
invoked anywhere on the new pipeline's call graph.
