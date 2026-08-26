# Story 0092 — Engineering Report

Date: 2026-08-26 · Branch: `feature/story-0092-detect-repository-head-changes`
· Base: `4dcd0dc` · Status: implementation complete, branch ready for review
(not merged).

## 1. Objective and outcome

DevLog now autonomously notices that the world changed:

```text
baseline = X, observed = X            → CURRENT
external commit Y arrives
USER DOES NOTHING
detector cycle observes Y (≤ interval)
observedRevision = Y, baseline = X    → STALE (+ REFRESH_RECOMMENDED)
get_engineering_context warns PROJECT_CONTEXT_STALE automatically
freshness Resource shows observed=Y / baseline=X
```

…while DevLog knowledge remains at X — proven by the negative runtime test:
the commit message of Y is invisible to history search after detection
(totalMatches 0), and X-era knowledge remains intact (20 matches).

## 2. DevLog MCP self-test

- Initial `get_engineering_context` (intent: automatic HEAD detection per
  ADR-062) against the currently deployed backend returned **no freshness
  metadata**: metadata carried only budget/truncation warnings; newest
  GIT_HISTORY evidence was 2026-08-19 while the SOURCE_FILE snapshot carried
  resolvedRevision `4dcd0dc…` (= current main HEAD). The mixed-revision
  phenomenon was again reproduced live — and remains invisible to consumers
  until this repository’s stack is redeployed with Story 0091 + 0092 code.
  The deployed stack predates the merge; contract behavior itself is covered
  by unit tests and by runtime validation against a local build.
- `search_project_history` was not needed for historical questions (Story
  0091 artifacts on HEAD served as consolidated memory); it was used as the
  negative-proof instrument during runtime validation.
- MCP Resources: `devlog://projects/{slug}/freshness` payload verified via
  its backing summary endpoint during runtime validation.

## 3. Existing infrastructure reused

`GitCommandExecutor`/`ProcessGitCommandExecutor` (credential-safe git exec),
`GitCommitIdentity` (normalization), `ProjectFreshnessService.save()`
classification chain (`findLatestComparable`, classifier), checkpoint row
schema (V32, no migration), `ProjectFreshnessSummary`/resource surfaces from
0091. Nothing duplicated; ADR-041 contains no code abstractions to reuse.

## 4. Detector architecture

```
@Scheduled fixedDelay (devlog.repository-observation.interval)
        ↓ ScheduledRepositoryChangeDetector        (orchestration only)
RepositoryRevisionProbe port
        └─ LsRemoteRepositoryRevisionProbe         (git ls-remote <url> <ref>)
        ↓ observedRevision
ProjectFreshnessService.recordObservedRevision     (existing classification)
        ↓ project_source_freshness checkpoint
Resource devlog://projects/{slug}/freshness + engineering-context metadata
```

Tracked ref: `refs/heads/<defaultBranch>` when configured, else remote
symbolic `HEAD`. Local/file repositories use the same command without
network. Failure of one source preserves its previous checkpoint and never
blocks others. Kill-switch: `devlog.repository-observation.enabled=false`.

## 5. Tests added

| File | Coverage |
|---|---|
| `LsRemoteRepositoryRevisionProbeTest` | ls-remote only, ref selection, SHA normalization (40/64), unknown-ref failure, **no fetch/pull/checkout/reset/clone/clean** |
| `ProjectFreshnessServiceTest` (+3) | external observation through existing classification; guard rejections; workspace untouched |
| `ScheduledRepositoryChangeDetectorTest` | per-source recording; failure isolation + preservation; idempotent repeated cycles; baseline never advanced |
| `RepositoryObservationConfigurationTest` | kill-switch: scheduling infrastructure present iff enabled |

Totals: full backend suite green (`verify` exit 0, JaCoCo ≈ 85 %); mcp-server
suite green; `git diff --check` clean; frontend untouched.

## 6. Remaining issues (demonstrated debt only)

1. Hidden read-path Git synchronization in `RepositoryContextEngine` remains
   (pre-existing, explicitly deferred). Interaction with the detector is
   benign: the probe shares no state with workspaces; both may observe
   slightly different revisions within one interval window, and freshness
   metadata stays truthful because it reflects recorded observations.
2. Polling latency ≤ interval before STALE appears (inherent to adapters).
3. Single-instance assumption for scheduling; multi-instance deployments
   would duplicate probes (harmless writes, wasted round-trips) until the
   future sync-lifecycle layer owns coordination.

## 7. Suggested next story (not created)

Evidence now supports the next architectural layer from ADR-041/062:
**“Synchronize changed repository sources deterministically”** — a planner +
sync-job model advancing ingested state (history import, checkpoints) toward
the observed revision without Understanding/AI, gated by significance. The
detector’s stable observation stream is exactly the input it needs.
Prerequisite check passed: checkpoints are reliable, exposed end-to-end, and
detection is provably decoupled from synchronization.
