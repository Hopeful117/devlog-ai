# Code Review — Story 0093

## Scope Reviewed

All production/test files on
`feature/story-0093-ingested-revision-checkpoint-sync` (see
implementation-report.md §Production Changes), reviewed against the mission
guardrails and existing ADRs.

## Findings & Resolutions

### F1 — Scheduling keyed on observation change (found at runtime, FIXED)
Initial detector integration created jobs only inside `if (changed)`. A source
whose HEAD was unchanged but whose ingestion lagged (e.g., Understanding ran
between cycles, or a previous job failed) was never scheduled. Fixed:
scheduling is evaluated every cycle on `observed != ingestedRevision` with the
PENDING/RUNNING existence guard preventing spam. Regression test
`schedulesInitialImportEvenWhenObservationIsUnchangedButIngestionBehind`
locks the behavior; runtime validation exercised it (INITIAL_IMPORT after
Understanding).

### F2 — Shell Source rejected by WorkspaceManager (found at runtime, FIXED)
Executor passed `Source.builder().id(...).build()` into Phase 1;
`GitWorkspaceManager` enforces `isActive()` → every job FAILED with
"Inactive source cannot be synchronized". Fixed: `claim()` loads the managed
Source via join-fetch (`findWithProjectById`) inside its transaction and ships
it in the immutable `SyncTarget`; inactive sources return null (skip, no
failure noise). Proven by the successful runtime INITIAL_IMPORT/forward sync.

### F3 — Checkpoint ordering (design-reviewed, correct)
`recordIngestedRevision` executes BEFORE `markCompleted`. Crash between them
re-runs a replay-safe import; the reverse order could strand ingested work
behind a COMPLETED job permanently. Ordering asserted with Mockito InOrder.

### F4 — Transaction boundaries (verified)
No `@Transactional` wraps Phase 1 Git/network I/O. All DB work occurs in short
state-service/persistence transactions. The pre-existing pattern of holding a
transaction across `synchronize()` (legacy `importHistory(UUID,String)`) is
NOT used by the pipeline.

### F5 — Failure hygiene (unit-tested)
Persisted failures = exception simple name + redacted message
(`scheme://…` → `[redacted]`, ≤300 chars). Test proves credential/host leakage
is impossible for URL-bearing messages. Divergence message contains only
abbreviated SHAs.

### F6 — Enum/state minimalism (accepted)
Four states / four reasons only, per investigation minimum. `MANUAL_SYNC` /
`RECOVERY` currently lack producers — documented in engineering-report as
vocabulary reserved for near-term needs rather than speculative machinery.

### F7 — Projection compatibility (checked)
`ProjectFreshnessResponse.Source` gained an additive field without bumping
`project-freshness-v1`; consumers (engineering-context mapper, maintenance
evaluation, cockpit freshness surfaces) compile and all 920 tests pass.
Additive JSON field is backward compatible for MCP clients.

## Guardrail Checklist

- [x] Java/domain services own deterministic repository state
- [x] AI owns nothing in synchronization (no ai-engine references in call graph)
- [x] Immutable revision targets only
- [x] Source-scoped jobs/checkpoints
- [x] No new hidden mutations on read paths (detector/executor are schedulers/writers of their own domain rows only)
- [x] No direct persistence from controllers/adapters (new REST surface: none)
- [x] No speculative abstractions beyond the justified job model

## Verdict

**APPROVED_FOR_COMMIT_APPROVAL** — no open findings; runtime-validated against
the deployed stack including failure semantics.
