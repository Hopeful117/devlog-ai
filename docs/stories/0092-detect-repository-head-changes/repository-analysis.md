# Story 0092 — Repository Analysis

Date: 2026-08-26 · Branch: `feature/story-0092-detect-repository-head-changes`
· Base: `4dcd0dc` (main, includes merged Story 0091 / PR #74).

## 1. Story 0091 behavior confirmed on HEAD (code, not docs)

- `projectfreshness/ProjectFreshnessService`:
  - `check(projectId, sourceId)` — live probe via
    `WorkspaceManager.resolveCurrentRevision(source)` (set-url + fetch --prune
    + rev-parse) then `persistence.save(...)`. This is the *manual* path; it
    mutates the managed workspace clone (fetch) and is therefore NOT suitable
    for an automatic detector.
  - `recordObservedBaseline(...)` — Story 0091 seam for the understanding
    refresh: records `current = baseline = resolvedRevision`, status CURRENT.
    Detector must NOT reuse it (it advances the baseline by design).
  - `latest(projectId, sourceId)`, `summary(projectId)` — persisted reads.
- `ProjectFreshnessPersistenceService.save(projectId, sourceId,
  requestedRevision, currentRevision, checkedAt)` — classifies via
  `findLatestComparable` + `ProjectFreshnessClassifier`
  (`NO_BASELINE`/`UNKNOWN`/`CURRENT`/`STALE`), normalizes identities,
  upserts the single row per source. **Exactly the boundary a detector
  observation needs** — it only lacks a public entry point that accepts an
  externally obtained revision.
- Baseline model: `findLatestComparable` selects revision-pinned
  describe-project snapshots (COMPLETED or IN_PROGRESS-with-snapshot) —
  post-0091 semantics verified in
  `ProjectFreshnessBaselinePostgresIntegrationTest`.
- Contract/resource: additive freshness metadata in
  `EngineeringContextMetadata` + read-only resource
  `devlog://projects/{slug}/freshness` reading the PERSISTED rows — so once a
  detector writes checkpoints, both surfaces update automatically with no
  changes.

Conclusion: the detector must feed `save()` through a new thin service method;
it creates no second freshness system.

## 2. Repository observation infrastructure

| Piece | State | Reuse decision |
|---|---|---|
| `GitCommandExecutor` (port) + `ProcessGitCommandExecutor` | runs `git <args>` in a working dir; `GIT_TERMINAL_PROMPT=0` (no credential prompts), 2 min timeout, virtual-thread reader | REUSED for ls-remote — credentials/transport handled by git itself exactly like clone today (ADR-041 provider independence; §44 no second credential path) |
| `WorkspaceManager.resolveCurrentRevision` | set-url + **fetch --prune** + rev-parse | REJECTED for detection (network fetch + ref mutation); remains the manual check path |
| `Source` entity | `type` (`GIT_REPOSITORY`…), `repositoryUrl` (remote URL **or** local/file path), `defaultBranch` nullable, `active`, lazy `project` relation | tracked-ref semantics: explicit branch when present, else remote symbolic `HEAD`; local paths work through the same transport |
| `SourceRepository` | per-project queries only | add one eligible-sources query (`type = GIT_REPOSITORY AND active = true`) with `@EntityGraph("project")` to avoid lazy init outside transactions |
| Scheduling infrastructure | none anywhere (verified again on main: zero `@Scheduled`/`@EnableScheduling`) | introduce minimally: one `@Configuration @EnableScheduling` guarded by the kill-switch |
| Configuration conventions | `application.properties` with `devlog.*` keys + `${ENV:default}` placeholders; typed config via `@ConfigurationProperties` records (e.g. `AIEngineProperties`) | mirror both |
| Locking/concurrency | per-source `ReentrantLock` inside GitWorkspaceManager for workspace ops; default Spring scheduler is single-threaded | detector needs none: probe touches no shared state; `fixedDelay` prevents self-overlap; single-instance assumption documented (§16) |
| ADR-041 code abstractions | decision document only — no passive-monitoring code exists | nothing to reuse; this story builds the first observation adapter it describes |

## 3. Probe mechanics (cheapest correct)

```text
git ls-remote <repositoryUrl> refs/heads/<defaultBranch>   # explicit branch
git ls-remote <repositoryUrl> HEAD                         # fallback (symbolic)
```

- Output line `<SHA>\t<ref>` → normalize 40/64-hex lowercase.
- Empty output (unknown branch) → explicit failure (never fabricate).
- No clone required — works before first synchronization and for sources
  never checked out; identical command for `https://`, `ssh://`, `file://`,
  local path (no network for local).
- Cost: one round-trip (or one exec for local). No `fetch/pull/checkout/
  reset`, no working tree, no locks.

## 4. Failure / UNKNOWN semantics

Existing domain distinguishes “revision observed” from “classification”.
Detector behavior on probe failure: log WARN (source id/name, never URL),
write NOTHING — previous checkpoint (observed/baseline/status/checkedAt)
remains. No row is overwritten with UNKNOWN (UNKNOWN stays a classification
outcome for unparseable revisions inside `save()`, matching ADR-062’s refusal
to invent a FAILED lifecycle).

## 5. Interaction with hidden read-path Git sync (mission §27)

`RepositoryContextEngine` still performs fetch+checkout during reads. The
detector cannot race it destructively: `ls-remote` shares no state with the
workspace (no refs update, no files). Worst case both observe slightly
different revisions within seconds; the freshness metadata remains truthful
because it reflects recorded observations only. Fix deferred as before.

## 6. Test infrastructure notes

Probe is mockable at the `RepositoryRevisionProbe` port; executor-level tests
capture commands to prove absence of mutation verbs (§37). Scheduler tests use
Mockito; disabled-config tested via `ApplicationContextRunner` on the
scheduling configuration class.
