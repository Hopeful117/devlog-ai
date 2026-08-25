# Story 0091 — Engineering Report

Date: 2026-08-26 · Branch: `feature/story-0091-project-freshness-checkpoints`
· Base: `689ef30` (main) · Status: implementation complete, branch ready for
review (not merged).

## 1. Objective and outcome

Make DevLog able to say honestly which revision of project truth it knows —
before teaching it to synchronize automatically. Outcome:

```text
Before: repository=Y, knowledge=X → get_engineering_context silently mixes Y and X.
After:  freshnessStatus=STALE, repositoryRevision=Y, contextRevision=X,
        warning PROJECT_CONTEXT_STALE, per-source resource payload;
        when X==Y → CURRENT with no extra Maintenance action.
```

All success criteria from `story.md` are met; runtime validation executed
end-to-end against a live stack (see `implementation-report.md` §2).

## 2. Design sources

- Investigation `docs/investigations/repository-synchronization-freshness.md`
  (§8-B1 drift, §11/§17 checkpoints, §24–§25 read honesty, §28 first step).
- ADR lineage confirmed on HEAD: **ADR-041** Passive Project Monitoring
  (Accepted) — extended, not rewritten; **ADR-053/054** Maintenance — left
  intact, benefits from fixed baselines; **ADR-055/059/060** Proposed —
  unblocked follow-ups; **ADR-061** Observation Baselines (Proposed) — its
  `(Source, immutable revision)` identity is the checkpoint key.
- New **ADR-062 — Repository Synchronization Lifecycle and Freshness
  Checkpoints**, status **Proposed** (per project convention, human validation
  pending; not self-marked Accepted).

## 3. DevLog MCP usage (self-test)

- `get_engineering_context(intent="formalize and implement repository
  freshness checkpoints…")` returned useful roadmap evidence (story-0085-era
  commits on baseline persistence) but again carried **no freshness metadata**
  about itself, no ADR content, and git-history evidence ~7 days behind HEAD —
  a live reproduction of the problem this story fixes. The MCP context CHALLENGE
  (“get_engineering_context drops engine warnings/timestamps”) directly
  foreshadowed the metadata extension implemented here.
- `search_project_history` / resources were not needed beyond this: the
  investigation report (committed at base) already consolidated history.
- Post-story self-check expectation: once DevLog refreshes itself onto this
  branch’s merge, `get_engineering_context` responses for devlog-ai will
  carry the freshness block — making the tool self-describing about currency.

## 4. What changed (summary)

| Area | Change |
|---|---|
| Baselines | `findLatestComparable`: drop `targetRevision IS NULL`, accept COMPLETED+IN_PROGRESS-with-snapshot, order by createdAt |
| Checkpoints | `recordObservation`/`recordObservedBaseline`; understanding refresh records CURRENT@X without re-probe |
| Contract | additive `metadata.freshness {status, repositoryRevision, contextRevision, sources[]}` + warnings `PROJECT_CONTEXT_STALE`, `PROJECT_CONTEXT_PARTIALLY_FRESH` |
| API | `GET …/freshness-checks/summary` |
| MCP | Resource `devlog://projects/{slug}/freshness` (read-only) |
| Docs | ADR-062 (Proposed); story artifacts ×6 |

## 5. Remaining issues (documented debt)

1. **Hidden read-path Git synchronization** in `RepositoryContextEngine`
   builds stays (CASE B of mission §26): removal requires reworking structure
   collection/enrichment to pinned revisions — dedicated story candidate.
   Divergence is now observable via freshness metadata.
2. `PROJECTION_REFRESH_GAP` finding type has remediation/UI support but no
   producer (pre-existing dead enum).
3. `TemporalAssessmentService` still has no production caller.
4. PENDING/IN_PROGRESS analysis zombies remain reused after crashes (no
   reaper) — owned by the future synchronization lifecycle story.
5. Freshness rows reflect last observation only (`checkedAt` exposes
   recency); continuous observation is explicitly out of scope until the
   detector/sync-job stories land (ADR-041 roadmap).

## 6. Suggested next story (not created)

Natural successor once this foundation is validated:
*“Detect repository HEAD changes automatically”* — a thin scheduled
ls-remote/local-rev-parse adapter per source emitting observations into a
planner, per ADR-062 §7 (polling as adapter). It can compare against the
checkpoints this story made reliable.
