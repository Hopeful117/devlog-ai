# Story 0083 — Deterministic Insight Temporal Assessment Foundation

## Objective

Prove that DevLog can evaluate the temporal freshness of ONE trusted Insight
without mutating its governed authority.

The Story must answer:

> "Can DevLog distinguish between:
> - this Insight is currently verified,
> - this Insight should be suspected as temporally stale,
> - DevLog does not have enough evidence to decide,
> without modifying the Insight's authoritative lifecycle?"

The target is:

```
InsightStatus           = AUTHORITY
TemporalAssessment    = FRESHNESS
```

These MUST remain separate.

## Non-Negotiable Invariants

1. **Authority immutability** — Temporal evaluation MUST NOT modify
   Insight.status, call archiveInsight, call supersedeInsight, create RESOLVES
   relationships, modify Validation, modify ValidatableProposal, or promote
   replacement knowledge. An assessment is NOT an authority transition.

2. **No false freshness** — Insufficient evidence MUST NOT produce CURRENT.
   Expected: insufficient evidence → UNKNOWN

3. **Evidence-based suspicion** — A relevant deterministic temporal signal MAY
   produce SUSPECTED_STALE but not automatically SUPERSEDED.

4. **Positive verification** — CURRENT must mean DevLog was actually able to
   evaluate the relevant evidence AND no temporal contradiction/suspicion was
   found. CURRENT must NOT mean "we did not detect anything because we had no
   evidence." evidenceReferences must be positively verified as present at both
   baseline AND current revision for the same Source.

5. **Deterministic repeatability** — Same domain/repository state → same temporal
   conclusion → same logical supporting evidence. evaluatedAt may naturally differ.

6. **Baseline transition detection** — A reference that was already absent at
   baseline does NOT prove temporal degradation. Degradation is proven only by a
   transition from present-at-baseline to absent-at-current.

7. **No time-as-baseline** — Insight.createdAt MUST NOT be used as a substitute
   for Analysis.targetRevision. If targetRevision is unavailable → UNKNOWN.

8. **Source-scoped repository state** — RepositoryStatePort MUST scope all
   repository-state queries to the actual Source. A Git revision is meaningful
   only inside its repository. Baseline and current revision MUST belong to the
   SAME Source (Analysis.selectedSource).

9. **Repository-state comparison is the ONLY conclusion-producing signal** —
   ChangedFile / DELETED history is corroborating evidence only. It does NOT
   independently produce conclusions. RepositoryStatePort unavailable/failure → UNKNOWN.

## V1 Temporal States

```
CURRENT
SUSPECTED_STALE
UNKNOWN
```

Do NOT introduce STALE yet unless repository analysis proves a truly objective
deterministic case that requires it.

## V1 Reasoning Origin

Story 0083 is deterministic-only.

```
DETERMINISTIC
```

Do NOT implement: AI_ASSISTED evaluation, HUMAN evaluation workflow,
confidence scores.

## Target Domain

V1 target: **Insight only**.

Do NOT generalize: Decision, EngineeringEvent, EngineeringStory,
Documentation, generic KnowledgeReference abstraction.

Avoid speculative genericity.

## Repository Analysis Summary

### Authority Mutation Points (MUST NOT be modified)

- `InsightServiceImpl.archiveInsight(id)` — sets status = ARCHIVED
- `InsightServiceImpl.supersedeInsight(id, canonicalId)` — sets status = SUPERSEDED, creates KnowledgeRelation RESOLVES
- `InsightRepository.findByProjectIdAndStatusIn(...)` — status-filtered retrieval
- InsightStatus enum values: ACTIVE, ARCHIVED, SUPERSEDED

### Existing Temporal Capabilities Reused

- **Insight.status** (ACTIVE/SUPERSEDED/ARCHIVED) — authority lifecycle, not to be modified
- **Insight.evidenceReferences** (List<String> file paths) — primary signal source
- **Insight.project** — links Insight to Project
- **Insight.analysis** — links Insight to Analysis entity
- **Analysis.targetRevision** — git commit hash the Insight was based on (REQUIRED baseline)
- **Analysis.selectedSource** — the Source/repository identity for scope boundary (REQUIRED)
- **ProjectCommitRepository** — `findByProjectIdAndCommittedAtAfter...` (enrichment); needs new `findTopBySourceIdOrderByCommittedAtDescCommitHashDesc` (currentKnownRevision)
- **ChangedFile** (changeType, oldPath, newPath) — corroborating evidence only
- **GitWorkspaceManager** — will gain `isFilePresentAtRevision(Source, String, String)`
- **GitCommandExecutor** — executes individual git commands

### Existing Capabilities NOT Reused

- **ProjectFreshnessStatus** — project-level only, NOT primary determinant
- **KnowledgeRelation.RESOLVES** — authority/lifecycle transition, NOT freshness signal (rejected per PLAN_CORRECTION_REQUIRED)
- **KnowledgeLifecycleDiagnosticService** — provenance verification, not temporal assessment
- **MaintenanceFinding issueTypes** — consumable as supporting evidence, not temporal truth source
- **Insight.createdAt** — NOT used as baseline (per FINAL_BASELINE_SEMANTICS_CORRECTION)
- **ProjectCommitRepository.findByProjectIdOrderByCommittedAtDesc** — project-wide latest, NOT used (multiple Sources may exist; must scope to selectedSource)

### Selected Deterministic Signal (V1)

**Repository-state transition scoped to Analysis.selectedSource**

For each `evidenceReference` in the Insight, check presence at baseline and current revision,
both scoped to `Analysis.selectedSource`:

```
baselinePresent = RepositoryStatePort.isFilePresentAtRevision(selectedSource, targetRevision, evidenceReference)
currentPresent  = RepositoryStatePort.isFilePresentAtRevision(selectedSource, currentKnownRevision, evidenceReference)
```

Where:
- `selectedSource` = `Analysis.selectedSource` (repository identity boundary; REQUIRED)
- `targetRevision` = `Analysis.targetRevision` (baseline git commit hash; REQUIRED)
- `currentKnownRevision` = latest commit hash for `selectedSource` from ProjectCommitRepository (latest known revision ingested by DevLog; REQUIRED)

**Conclusion rules:**

| baselinePresent | currentPresent | Result |
|---|---|---|
| `true` | `false` | **SUSPECTED_STALE** — evidence file existed at baseline, now absent |
| `true` | `true` | **positively verified** — no degradation for this reference |
| `false` | any | **not a degradation** — reference was already absent at baseline; skip |
| `null/UNKNOWN` (port unavailable) | any | **cannot determine** |

**Overall assessment:**
- If ANY evidenceReference shows baselinePresent=true AND currentPresent=false → **SUSPECTED_STALE**
- If ALL evaluable evidenceReferences show baselinePresent=true AND currentPresent=true → **CURRENT**
- If evidenceReferences is empty → **UNKNOWN**
- If selectedSource is missing → **UNKNOWN**
- If targetRevision is missing → **UNKNOWN**
- If currentKnownRevision cannot be determined → **UNKNOWN**
- If RepositoryStatePort fails/unavailable → **UNKNOWN**
- If ALL refs have baselinePresent=false (no evaluable refs) → **UNKNOWN**

**ChangedFile enrichment**: ProjectCommit + ChangedFile query after Insight.createdAt can identify the specific commit that deleted the file, enriching `supportingEvidence`. This is **corroborating evidence only** — it does NOT independently produce the conclusion.

- The Insight remains ACTIVE (authority unchanged)
- This is KNOWLEDGE-SPECIFIC (targets this Insight's evidenceReferences, scoped to selectedSource)
- It is EXPLAINABLE: "File X was present at baseline revision B (source S), but absent at currentKnownRevision C"
- It is TESTABLE: can mock RepositoryStatePort; verify path matching

### Why `currentKnownRevision` Is Used (Not Repository HEAD)

`currentKnownRevision` (latest committed revision ingested by DevLog for the selectedSource) is used instead of "repository HEAD" because:
1. DevLog may not have the absolute latest HEAD — it ingests revisions on a schedule
2. Calling something "HEAD" implies it is from the live repository; `currentKnownRevision` is from the ingested commit history
3. The assessment is deterministic for the DevLog state at evaluation time

If no commits are ingested for the selectedSource:
    → UNKNOWN ("currentKnownRevision cannot be determined for source")

### Why Baseline Transition Is Required

Current-absence alone is ambiguous:
- Was the file deleted AFTER the Insight was created? → degradation → SUSPECTED_STALE
- Was the file already absent when the Insight was created? → no degradation → not a staleness signal

`Analysis.targetRevision` as the baseline commit resolves this deterministically.
`Insight.createdAt` (a time proxy) does NOT — a file could be absent at the commit
closest to createdAt without any deletion record.

### Why Source Scoping Is Required

A Git revision (commit hash) is meaningful only inside its repository. The same
commit hash in different repositories refers to different snapshots. Therefore:
- Baseline revision MUST come from the same Source as currentKnownRevision
- Both are `Analysis.selectedSource` + a commit hash
- Do NOT mix revisions from different Sources

### Why ChangedFile History Is Corroborating Only

The ChangedFile DELETED records identify which commit deleted the file — useful for
explanation ("deleted in commit D, committed at Z"). But:
- This is enrichment of the `supportingEvidence` field
- It does NOT produce the temporal conclusion independently
- If RepositoryStatePort is unavailable, the assessment is UNKNOWN — NOT SUSPECTED_STALE
  based on commit history alone

### Repository Queries Required

1. **NEW**: `ProjectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId)` — get currentKnownRevision for the selectedSource
2. **EXISTING**: `ProjectCommitRepository.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(projectId, insight.createdAt)` — enrichment: find DELETED ChangedFiles (project-level, path matching is corroborating)

One new query method needed. No existing methods modified.

### Git Command Required

`git cat-file -e <commitHash>:<relativePath>` — check if a file exists at a specific git revision.
Exit code 0 = exists, non-zero = absent. Single read-only command, no checkout, no sync.

### Repository State Port

```java
package com.hopeful117.devlogai.temporal.port;

/**
 * Read-only port for checking repository file existence at a specific git revision,
 * scoped to a Source (repository identity).
 *
 * Temporal Knowledge domain depends on this interface, NOT on GitWorkspaceManager
 * directly (ADR-059 §22).
 */
public interface RepositoryStatePort {
    /**
     * @param source the Source (repository identity) to scope the query
     * @param commitHash git commit hash to check against
     * @param relativePath repository-relative file path
     * @return true if file exists at that revision for that source, false otherwise
     */
    boolean isFilePresentAtRevision(Source source, String commitHash, String relativePath);
}
```

### Architectural Dependency Assessment

- TemporalAssessmentService depends on RepositoryStatePort (port in temporal package)
- GitWorkspaceManager implements RepositoryStatePort (or an adapter delegates to it)
- Temporal domain does NOT depend on GitWorkspaceManager directly
- Single read-only git command, no broad subsystem, no sync, no rename tracking, no history reconstruction

### Why Persistence Is Unnecessary

- TemporalAssessment fully derived from existing state
- No new tables, entities, or migrations
- Single deterministic signal (repository-state transition)
- Evaluated state ephemeral; persistence deferred to V2+

### Why Context Integration Is Deferred

- Story 0083 must NOT change what the agent sees
- Temporal Assessment must exist independently first
- Future Story: KnowledgeEligibilityPolicy

### Why AI Is Deferred

No AI Engine changes. No LLM evaluation. No confidence scores. ADR-060 documented, not implemented.
