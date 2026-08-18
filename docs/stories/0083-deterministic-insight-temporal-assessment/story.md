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
   evidence." Specifically: absence of a deletion record is NOT sufficient for
   CURRENT — evidenceReferences must be positively verified as present at both
   baseline AND current revision, scoped to the same Source.

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

For Story 0083, use only the minimal required states:

```
CURRENT
SUSPECTED_STALE
UNKNOWN
```

Do NOT introduce STALE yet unless repository analysis proves a truly objective
deterministic case that requires it.

## V1 Reasoning Origin

Story 0083 is deterministic-only.

The produced assessments should therefore use:

```
DETERMINISTIC
```

Do NOT implement: AI_ASSISTED evaluation, HUMAN evaluation workflow,
confidence scores.

The domain model may remain future-compatible with ADR-060, but do not add dead
complexity solely for future extensibility.

## Target Domain

V1 target: **Insight only**.

Do NOT generalize to: Decision, EngineeringEvent, EngineeringStory,
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
- **Insight.evidenceReferences** (List<String> file paths) — **primary signal source**: file paths to verify against repository state. Populated by CommitScopedFactCollector from ChangedFile data.
- **Insight.project** — links Insight to Project for commit/source queries
- **Insight.analysis** — links Insight to Analysis entity
- **Analysis.targetRevision** — git commit hash the Insight was based on (**REQUIRED** baseline revision)
- **Analysis.selectedSource** — the Source/repository identity for scope boundary (**REQUIRED** for repository-state queries)
- **ProjectCommitRepository** — `findByProjectIdAndCommittedAtAfter...` with eager ChangedFile loading (enrichment); `findTopBySourceIdOrderByCommittedAtDescCommitHashDesc` (NEW — currentKnownRevision scoped to selectedSource)
- **ChangedFile** (changeType, oldPath, newPath) — enrichment only: identifies the commit that deleted the file (corroborating evidence, NOT conclusion-producing)
- **GitWorkspaceManager** — provides repository workspace operations; will gain `isFilePresentAtRevision(Source, String, String)` — single read-only git command
- **GitCommandExecutor** — executes the single `git cat-file -e` command

### Existing Capabilities NOT Reused

- **ProjectFreshnessStatus** — project-level only, NOT primary determinant
- **KnowledgeRelation.RESOLVES** — authority/lifecycle transition, NOT freshness signal (rejected per PLAN_CORRECTION_REQUIRED)
- **ProjectCommitRepository.findByProjectIdOrderByCommittedAtDesc** — project-wide latest commit, NOT used (multiple Sources may exist; must scope to selectedSource per FINAL_SOURCE_SCOPING_CORRECTION)
- **Insight.createdAt as baseline** — NOT used; must use Analysis.targetRevision (per FINAL_BASELINE_SEMANTICS_CORRECTION)
- **GitCommandExecutor** — executes individual git commands
- **ProjectFreshnessStatus** (NO_BASELINE/CURRENT/STALE/UNKNOWN) — project-level only, NOT primary determinant
- **KnowledgeLifecycleDiagnosticService** — provenance chain (ValidatableProposal → Insight)
- **MaintenanceFinding issueTypes** — consumable as supporting evidence, not as temporal truth source

### What Temporal Evidence Can Be Reconstructed

1. **Repository-state transition** (PRIMARY SIGNAL): For each evidenceReference, check presence at
   `Analysis.targetRevision` (baseline) and at `currentKnownRevision` (latest known revision for
   `Analysis.selectedSource`). If present at baseline AND absent at current → SUSPECTED_STALE.
   This distinguishes "file existed, now gone" (degradation) from "file was never there" (no
   degradation proven).

2. **Commit history enrichment**: ProjectCommit + ChangedFile history can identify which commit
   deleted the file, providing corroborating evidence. This ENRICHES the SUSPECTED_STALE assessment
   but does NOT define a second independent temporal-state algorithm. It is corroborating only.

3. **Project freshness STALE**: If ProjectFreshnessStatus for related sources is STALE, this can be
   supporting evidence but NOT the primary basis for Insight-level conclusion.

4. **ValidatableProposal provenance**: Insight's proposal chain (AI Task → Proposal → Validation →
   Promotion) can be verified via KnowledgeLifecycleDiagnosticService.

### What Temporal Evidence Cannot Be Reconstructed (Without New Infrastructure)

- Age-based staleness ("insight is N days old") — explicitly rejected by ADR-059
- Project-level freshness implying Insight-level staleness — coupling risk
- Semantic interpretation of repository changes ("this refactoring invalidates that Insight") — requires AI-assisted assessment, deferred
- Commit-occurred-after-creation — does NOT imply staleness per ADR-059 §333-335
- KnowledgeRelation RESOLVES as freshness signal — rejected: this is an authority/lifecycle transition (SUPERSEDED status), NOT a freshness dimension signal (per PLAN_CORRECTION_REQUIRED)
- Insight.createdAt as baseline revision — EXPLICITLY rejected per FINAL_BASELINE_SEMANTICS_CORRECTION. Different clocks are not equivalent to repository state.

### Selected Deterministic Signal (V1)

**Signal: Repository-state transition from baseline-present to current-absent**

This is the primary deterministic signal. For each `evidenceReference` in the Insight:

```
baselinePresent = RepositoryStatePort.isFilePresentAtRevision(selectedSource, baselineRevision, evidenceReference)
currentPresent  = RepositoryStatePort.isFilePresentAtRevision(selectedSource, currentKnownRevision, evidenceReference)
```

Where:
- `selectedSource` = `Analysis.selectedSource` (repository identity; REQUIRED for scope)
- `baselineRevision` = `Analysis.targetRevision` (git commit hash the Insight was based on; REQUIRED)
- `currentKnownRevision` = latest commit hash for `selectedSource` from ProjectCommitRepository
  (`findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId)`); NOT project-wide HEAD

**Conclusion rules:**

| baselinePresent | currentPresent | Result |
|---|---|---|
| `true` | `false` | **SUSPECTED_STALE** — evidence file existed at baseline, absent at currentKnownRevision |
| `true` | `true` | **positively verified** — no degradation observed for this reference |
| `false` | any | **not a degradation** — reference was already absent at baseline; does not prove temporal degradation |
| `null/UNKNOWN` (port error) | any | **cannot determine** |

**Overall assessment:**
- If ANY evidenceReference shows baselinePresent=true AND currentPresent=false → **SUSPECTED_STALE**
- If ALL evaluable evidenceReferences show baselinePresent=true AND currentPresent=true → **CURRENT**
- If evidenceReferences is empty → **UNKNOWN**
- If selectedSource is missing → **UNKNOWN**
- If targetRevision is missing → **UNKNOWN** (do NOT use Insight.createdAt as substitute)
- If currentKnownRevision cannot be determined → **UNKNOWN**
- If RepositoryStatePort fails/unavailable → **UNKNOWN** (ChangedFile enrichment does NOT substitute)

**Commit history enrichment**: ProjectCommit + ChangedFile query after Insight.createdAt can identify the specific commit that deleted the file, enriching the `supportingEvidence` with the commit hash and timestamp. This is corroborating evidence only — it does NOT define the temporal conclusion independently. RepositoryStatePort is the only conclusion-producing signal.

- The Insight remains ACTIVE (authority unchanged); only freshness assessment is SUSPECTED_STALE
- This is a KNOWLEDGE-SPECIFIC deterministic signal (targets this Insight's evidenceReferences, not project-wide)
- It is EXPLAINABLE: "File X was present at baseline revision B, but absent at current revision C (deleted in commit D, committed at Z)"
- It is TESTABLE: can mock RepositoryStatePort; can verify path matching against ChangedFile data
- It does NOT represent authority/lifecycle (InsightStatus remains ACTIVE; file deletion is a freshness observation)

**Note on KnowledgeRelation RESOLVES**: REJECTED as primary signal per PLAN_CORRECTION_REQUIRED.
RESOLVES represents a governed authority/lifecycle transition (SUPERSEDED status), NOT a freshness
dimension signal. Using it as a freshness signal conflates authority with freshness, violating
ADR-059 §129-151.

### Rejected Signal Candidates

1. **Project freshness STALE** — rejected: project-level change does not mean Insight-level staleness (ADR-059 §333-335, §746-753)
2. **"Commit happened later"** — rejected: does NOT imply semantic invalidation (ADR-059 §333-335)
3. **Age since creation (> N days)** — rejected: explicitly rejected by ADR-059 §333-335; age ≠ staleness
4. **KnowledgeRelation RESOLVES** — rejected as freshness signal: represents authority/lifecycle transition, NOT freshness dimension (per PLAN_CORRECTION_REQUIRED)
5. **Supporting file modified (not deleted)** — rejected: modification ≠ invalidation; requires semantic interpretation deferred
6. **Repository change in unrelated file** — rejected: not knowledge-specific
7. **"No DELETED record found" alone as CURRENT** — rejected: absence of evidence is NOT positive verification (per FINAL_PLAN_CORRECTION_REQUIRED)
8. **Insight.createdAt as baseline** — rejected: different clocks are not equivalent to repository state; baseline MUST be Analysis.targetRevision (per FINAL_BASELINE_SEMANTICS_CORRECTION)
9. **Current-absence alone as SUSPECTED_STALE** — rejected: a file already absent at baseline does not prove temporal degradation; must observe transition from present-at-baseline to absent-at-current

### Why Persistence Is Unnecessary in Story 0083

- TemporalAssessment is **fully derived** from existing state (Model A from investigation)
- No new tables, entities, or migrations
- Assessment computed on-demand from: Insight + Analysis.targetRevision + ProjectCommitRepository + ChangedFile + RepositoryStatePort
- Only one deterministic signal (repository-state transition) — no signal engine infrastructure needed
- Evaluated state is ephemeral; persistence deferred to V2+ based on proven need
- Matches ADR-059 §425-436: "first determine which temporal facts can be derived reliably from existing state"

### Why Context Integration Is Deferred

- Story 0083 must NOT change what the agent sees (per workflow Step 15)
- Temporal Assessment capability must first exist independently
- Future Story will introduce explicit KnowledgeEligibilityPolicy
- Current ACTIVE-only selection (Story 0079) remains unchanged

### Authority Mutation Points That Must Remain Untouched

- Insight.status must never be modified by temporal assessment
- No archiveInsight/ supersedeInsight calls
- No RESOLVES relationship creation/ modification from temporal evaluation
- ValidatableProposal and Validation must remain unchanged
- No promotion of replacement knowledge

### Repository State Port (Read-Only Abstraction)

A small read-only port interface defines the boundary between temporal domain and repository state.
**Required method**: `isFilePresentAtRevision(Source source, String commitHash, String relativePath)` — checks if a file exists at a specific git revision, scoped to a Source.

```java
package com.hopeful117.devlogai.temporal.port;

import com.hopeful117.devlogai.source.entity.Source;

/**
 * Read-only port for checking repository file existence at a specific git revision,
 * scoped to a Source (repository identity).
 *
 * Per FINAL_SOURCE_SCOPING_CORRECTION: the port MUST include Source identity.
 * A Git revision is meaningful only inside its repository.
 */
public interface RepositoryStatePort {
    boolean isFilePresentAtRevision(Source source, String commitHash, String relativePath);
}
```

**Implementation**: GitWorkspaceManager gains a single read-only method:
```java
public boolean isFilePresentAtRevision(Source source, String commitHash, String relativePath) {
    // Single git cat-file -e command: checks if <commitHash>:<relativePath> exists
    // Exit code 0 = exists, non-zero = absent
    // Scoped to the source's workspace/repository — NOT a broad Git subsystem
}
```

### Baseline and Current-State References

- **Baseline**: `Analysis.selectedSource` + `Analysis.targetRevision` (git commit hash) — **REQUIRED**. If either is missing → UNKNOWN.
- **Current**: `Analysis.selectedSource` + latest commit hash for that source from `ProjectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId)` (currentKnownRevision). If none → UNKNOWN.
- **Both MUST use the same Source**: baseline and currentKnownRevision are both scoped to `Analysis.selectedSource`. Do NOT mix sources.

### Why `currentKnownRevision` (Not "HEAD")

`currentKnownRevision` (latest committed revision ingested by DevLog for the selectedSource) is used because:
1. DevLog may not have the absolute latest HEAD — it ingests revisions on a schedule
2. Calling something "HEAD" implies it is from the live repository; `currentKnownRevision` is from the ingested commit history
3. The assessment is deterministic for the DevLog state at evaluation time
4. Must be scoped to selectedSource, not project-wide (per FINAL_SOURCE_SCOPING_CORRECTION)

### Signal Detection Logic (Implementation)

```text
Given Insight with evidenceReferences, analysis.selectedSource, analysis.targetRevision:

1. If insight.status != ACTIVE → NOT_APPLICABLE (service boundary)

2. If evidenceReferences is empty → UNKNOWN

3. If analysis.selectedSource is null → UNKNOWN (source unavailable)

4. If analysis.targetRevision is null or blank → UNKNOWN (baseline unavailable)

5. Get currentKnownRevision:
   a. selectedSourceId = analysis.selectedSource.getId()
   b. latestCommit = ProjectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(selectedSourceId)
   c. If latestCommit is empty → UNKNOWN (currentKnownRevision unavailable)
   d. currentKnownRevision = latestCommit.getCommitHash()

6. Enrichment (for supportingEvidence ONLY, not for conclusion):
   a. Query ProjectCommitRepository.findByProjectIdAndCommittedAtAfter(projectId, insight.createdAt)
      with @EntityGraph(changedFiles)
   b. Find ChangedFiles with changeType=DELETED and oldPath matching evidenceReferences
   c. Record: commit hash, committedAt, path (corroborating evidence)

7. Primary signal evaluation (for each evidenceReference):
   a. baselinePresent = RepositoryStatePort.isFilePresentAtRevision(selectedSource, targetRevision, evidenceRef)
   b. currentPresent = RepositoryStatePort.isFilePresentAtRevision(selectedSource, currentKnownRevision, evidenceRef)
   c. If RepositoryStatePort throws → UNKNOWN (verification unavailable)

8. Conclusion (based on RepositoryStatePort results ONLY):
   a. If baselinePresent=true AND currentPresent=false → SUSPECTED_STALE
   b. If ALL evaluable evidenceReferences: baselinePresent=true AND currentPresent=true → CURRENT
   c. If some refs baselinePresent=false → skip (not degradation); evaluate remaining
   d. If no evaluable refs remain → UNKNOWN

9. reasoningOrigin = DETERMINISTIC
```

### Evaluation Outcomes

| Condition | conclusion | supportingEvidence | reasoningOrigin |
|---|---|---|---|
| baselinePresent=true, currentPresent=false for ≥1 ref | SUSPECTED_STALE | ["File 'X' present at baseline 'B' (source S), absent at currentKnownRevision 'C'" + enrichment if available] | DETERMINISTIC |
| All evaluable refs: baseline=true, current=true | CURRENT | ["All N evidence references verified present at baseline 'B' and currentKnownRevision 'C' (source S)"] | DETERMINISTIC |
| evidenceReferences empty | UNKNOWN | ["Insufficient evidence: no evidence references to evaluate"] | — |
| selectedSource is null | UNKNOWN | ["Insufficient evidence: Analysis.selectedSource unavailable"] | — |
| targetRevision null/blank | UNKNOWN | ["Insufficient evidence: baseline revision unavailable"] | — |
| currentKnownRevision unavailable | UNKNOWN | ["Insufficient evidence: currentKnownRevision cannot be determined for source"] | — |
| RepositoryStatePort fails | UNKNOWN | ["Insufficient evidence: repository state verification unavailable"] | — |
| Insight status != ACTIVE | NOT_APPLICABLE | — (service rejection) | — |

### V1 Final Semantics

```
Non-ACTIVE Insight
    -> NOT_APPLICABLE / rejected at boundary

ACTIVE + no evidenceReferences
    -> UNKNOWN

ACTIVE + missing selectedSource
    -> UNKNOWN

ACTIVE + missing targetRevision
    -> UNKNOWN

ACTIVE + missing currentKnownRevision
    -> UNKNOWN

ACTIVE + repository-state verification unavailable
    -> UNKNOWN

For each evaluable evidenceReference:
    present at baseline = true
    present at currentKnownRevision = false
        -> SUSPECTED_STALE

All evaluable references:
    present at baseline = true
    present at currentKnownRevision = true
        -> CURRENT

Reference absent at baseline:
    does not prove temporal degradation;
    if no positively evaluable references remain
        -> UNKNOWN
```

### Why This Signal Is Knowledge-Specific

The repository-state transition signal targets the particular Insight's `evidenceReferences` —
the specific file paths that were the supporting evidence for this Insight. It is not a
project-wide freshness signal, not an age heuristic, and not a semantic interpretation. It is
a concrete git fact (file present at baseline, absent at currentKnownRevision) that directly
affects the verifiability of this specific Insight's underlying evidence.

### Why This Signal Is Not Project-Freshness-to-Insight-Stale Shortcut

ProjectFreshnessStatus.STALE means the external source revision changed. It does NOT mean
the Insight about that source is now stale. The temporal signal is specifically: "files that
THIS Insight references as evidence were present at the Insight's baseline commit but are
absent at currentKnownRevision for the same Source." This is knowledge-specific, not a blanket
project-level status.

### Why This Signal Is Not Age-Based

The signal is the repository-state transition (present at baseline → absent at currentKnownRevision),
not the age of the Insight. An Insight created yesterday whose evidence file was removed yesterday
is just as suspected-stale as one from a year ago. The state transition is the signal, not
the timestamp.

### Why Baseline Transition Is Required (Not Just Current Absence)

Without baseline verification, current-absence alone is ambiguous:

- Was the file deleted AFTER the Insight was based on this revision? → degradation → SUSPECTED_STALE
- Was the file already absent when the Insight was created? → no degradation → not a staleness signal

Using `Analysis.targetRevision` as the baseline commit resolves this ambiguity
deterministically. Using `Insight.createdAt` (a time proxy) does NOT — a file could have
been absent at the commit closest to createdAt without any deletion record.

### CURRENT Semantics Clarification

CURRENT means:

> All evidence references that are evaluable within the V1 deterministic assessment scope
> were successfully verified against baseline (Analysis.targetRevision) and currentKnownRevision
> repository state for the same Source, and no supported temporal degradation was observed.

CURRENT does NOT mean:

> The semantic truth of the Insight has been globally proven.

For V1, "evaluable" means: baselinePresent=true AND currentPresent=true. References with
baselinePresent=false were already absent at baseline (not a staleness signal) and are
skipped. If ALL evaluable references are positively verified → CURRENT.
