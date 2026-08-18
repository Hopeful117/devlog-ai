# Story 0083 — Implementation Plan

## Execution Context

- **Branch**: story/0083-deterministic-insight-temporal-assessment
- **Base**: main at 80ad56a (Story 0082 merged)
- **ADR Compliance**: ADR-059 (Temporal Engineering Knowledge), ADR-060 (Deterministic Core), ADR-006 (Governance), ADR-058 (Data Lineage)
- **Classification**: B. SMALL_TEMPORAL_DOMAIN_MODEL_REQUIRED
- **Architecture**: 1. DERIVED_ONLY

## Corrections Applied

1. **PLAN_CORRECTION_REQUIRED**: Signal: KnowledgeRelation RESOLVES → evidence file state transition; service separated from InsightServiceImpl
2. **FINAL_PLAN_CORRECTION_REQUIRED**: CURRENT = positive verification at baseline AND current; `Analysis.targetRevision` REQUIRED
3. **FINAL_SOURCE_SCOPING_CORRECTION**: RepositoryStatePort MUST scope by Source identity; baseline and current MUST use same Source; `currentKnownRevision` (not "HEAD"); ChangedFile history is corroborating only

## Exact Production Packages / Files

### New Files

| File | Package | Purpose |
|---|---|---|
| `TemporalAssessment.java` | `com.hopeful117.devlogai.temporal.domain` | Derived domain object (NOT persisted) |
| `TemporalAssessmentService.java` | `com.hopeful117.devlogai.temporal.service` | Assessment logic — SEPARATE from InsightServiceImpl |
| `DeterministicTemporalSignal.java` | `com.hopeful117.devlogai.temporal.signal` | Signal enum |
| `RepositoryStatePort.java` | `com.hopeful117.devlogai.temporal.port` | Read-only port: `isFilePresentAtRevision(Source, String, String)` |

### Modified Files

| File | Change | Reason |
|---|---|---|
| `GitWorkspaceManager.java` | Add `isFilePresentAtRevision(Source, String, String)` | Single read-only git command (`git cat-file -e`) |
| `ProjectCommitRepository.java` | Add `findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(UUID)` | Get currentKnownRevision scoped to selectedSource |

### NO Changes

- No entity modifications
- No new JPA mappings or persistence annotations
- No Flyway/migrations
- No Context Engine changes
- No AI Engine changes
- No changes to InsightServiceImpl
- No changes to existing ProjectCommitRepository methods

## Exact Domain Object

### TemporalAssessment.java

```java
package com.hopeful117.devlogai.temporal.domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class TemporalAssessment {
    private final UUID insightId;
    private final Conclusion conclusion;
    private final ReasoningOrigin reasoningOrigin;
    private final List<String> supportingEvidence;
    private final Instant evaluatedAt;

    private TemporalAssessment(UUID insightId, Conclusion conclusion,
            ReasoningOrigin reasoningOrigin, List<String> supportingEvidence,
            Instant evaluatedAt) {
        this.insightId = insightId;
        this.conclusion = conclusion;
        this.reasoningOrigin = reasoningOrigin;
        this.supportingEvidence = List.copyOf(supportingEvidence);
        this.evaluatedAt = evaluatedAt;
    }

    public static TemporalAssessment of(UUID insightId,
            List<String> supportingEvidence, Conclusion conclusion,
            ReasoningOrigin reasoningOrigin) {
        return new TemporalAssessment(insightId, conclusion, reasoningOrigin,
                supportingEvidence, Instant.now());
    }

    public UUID getInsightId() { return insightId; }
    public Conclusion getConclusion() { return conclusion; }
    public ReasoningOrigin getReasoningOrigin() { return reasoningOrigin; }
    public List<String> getSupportingEvidence() { return supportingEvidence; }
    public Instant getEvaluatedAt() { return evaluatedAt; }

    public enum Conclusion { CURRENT, SUSPECTED_STALE, UNKNOWN }
    public enum ReasoningOrigin { DETERMINISTIC }
}
```

## Exact Deterministic Signal Source

### Signal: Repository-state transition scoped to Analysis.selectedSource

**Port**: `RepositoryStatePort.isFilePresentAtRevision(Source source, String commitHash, String relativePath)`

**Baseline**: `Analysis.selectedSource` + `Analysis.targetRevision` (REQUIRED)
**Current**: `Analysis.selectedSource` + latest commit hash from `ProjectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId)` (currentKnownRevision)

### Signal Detection Logic

```text
Given Insight with evidenceReferences, analysis.selectedSource, analysis.targetRevision:

1. If insight.status != ACTIVE → throw IllegalStateException (NOT_APPLICABLE)

2. If evidenceReferences is empty → UNKNOWN
   ("Insufficient evidence: no evidence references to evaluate")

3. If analysis.selectedSource is null → UNKNOWN
   ("Insufficient evidence: Analysis.selectedSource unavailable")

4. If analysis.targetRevision is null or blank → UNKNOWN
   ("Insufficient evidence: baseline revision (Analysis.targetRevision) unavailable")

5. Get currentKnownRevision:
   a. selectedSourceId = analysis.selectedSource.getId()
   b. latestCommit = ProjectCommitRepository.findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(selectedSourceId)
   c. If latestCommit is empty → UNKNOWN
      ("Insufficient evidence: currentKnownRevision cannot be determined for source")
   d. currentKnownRevision = latestCommit.getCommitHash()

6. Enrichment (for supportingEvidence ONLY, not for conclusion):
   a. Query ProjectCommitRepository.findByProjectIdAndCommittedAtAfterOrderByCommittedAtDescCommitHashDesc(projectId, insight.createdAt)
      with @EntityGraph(changedFiles)
   b. Find ChangedFiles with changeType=DELETED and oldPath matching evidenceReferences
   c. Record: commit hash, committedAt, deleted file path (corroborating evidence)

7. Primary signal evaluation (for each evidenceReference):
   a. baselinePresent  = RepositoryStatePort.isFilePresentAtRevision(selectedSource, targetRevision, evidenceRef)
   b. currentPresent   = RepositoryStatePort.isFilePresentAtRevision(selectedSource, currentKnownRevision, evidenceRef)
   c. If RepositoryStatePort throws → UNKNOWN (repository-state verification unavailable)

8. Conclusion (based on RepositoryStatePort results ONLY):
   a. If baselinePresent=true AND currentPresent=false → SUSPECTED_STALE for this ref
   b. If ALL evidenceReferences evaluable (baselinePresent=true for all) AND all currentPresent=true → CURRENT
   c. If some refs baselinePresent=false → skip (not a degradation signal)
      Evaluate remaining refs independently
      If all remaining verified → CURRENT
      If none evaluable (all baselinePresent=false) → UNKNOWN
   d. If RepositoryStatePort unavailable for any ref → UNKNOWN
   e. If RepositoryStatePort indicates any SUSPECTED_STALE → SUSPECTED_STALE (regardless of enrichment)

9. supportingEvidence: include RepositoryStatePort results + ChangedFile enrichment (corroborating)

10. reasoningOrigin = DETERMINISTIC
```

### RepositoryStatePort.java

```java
package com.hopeful117.devlogai.temporal.port;

import com.hopeful117.devlogai.source.entity.Source;

/**
 * Read-only port for checking repository file existence at a specific git revision,
 * scoped to a Source (repository identity).
 *
 * Temporal Knowledge domain depends on this interface, NOT on GitWorkspaceManager
 * directly (ADR-059 §22).
 */
public interface RepositoryStatePort {
    /**
     * Checks if a file exists at the given commit hash within the given Source's repository.
     *
     * @param source the Source (repository identity) to scope the check
     * @param commitHash git commit hash to check against
     * @param relativePath repository-relative file path
     * @return true if file exists at that revision in that repository, false otherwise
     */
    boolean isFilePresentAtRevision(Source source, String commitHash, String relativePath);
}
```

### GitWorkspaceManager.java (Add Method)

```java
// Added to existing GitWorkspaceManager:

public boolean isFilePresentAtRevision(Source source, String commitHash, String relativePath) {
    // Single read-only git command: checks if <commitHash>:<relativePath> exists
    // in the given source's workspace/repository
    // Exit code 0 = exists, non-zero = absent
    // No working tree checkout, no synchronization, no fetch
    requireSupportedSource(source);
    ReentrantLock lock = sourceLocks.computeIfAbsent(source.getId(), ignored -> new ReentrantLock());
    lock.lock();
    try {
        Path workspace = resolveWorkspace(source.getId());
        if (!isGitWorkspace(workspace)) {
            return false;
        }
        git.execute(workspace, List.of("cat-file", "-e", commitHash + ":" + relativePath));
        return true;
    } catch (GitCommandException exception) {
        return false;
    } finally {
        lock.unlock();
    }
}
```

### ProjectCommitRepository.java (Add Method)

```java
// Added to existing ProjectCommitRepository:

/**
 * Find the latest commit (currentKnownRevision) for a specific Source.
 * Used to determine the current repository state for temporal assessment.
 */
Optional<ProjectCommit> findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(UUID sourceId);
```

## Exact Conclusion Rules

| Condition | conclusion | supportingEvidence |
|---|---|---|
| baselinePresent=true, currentPresent=false for ≥1 ref | SUSPECTED_STALE | ["File 'X' present at baseline 'B' (source S), absent at currentKnownRevision 'C'" + enrichment if available] |
| All evaluable refs: baseline=true, current=true | CURRENT | ["All N evidence references verified present at baseline 'B' and currentKnownRevision 'C' (source S)"] |
| evidenceReferences empty | UNKNOWN | ["Insufficient evidence: no evidence references to evaluate"] |
| selectedSource is null | UNKNOWN | ["Insufficient evidence: Analysis.selectedSource unavailable"] |
| targetRevision null/blank | UNKNOWN | ["Insufficient evidence: baseline revision unavailable"] |
| currentKnownRevision unavailable | UNKNOWN | ["Insufficient evidence: currentKnownRevision cannot be determined for source"] |
| RepositoryStatePort fails | UNKNOWN | ["Insufficient evidence: repository state verification unavailable"] |
| Insight status != ACTIVE | NOT_APPLICABLE | — (service throws) |
| All refs baselinePresent=false | UNKNOWN | ["No evidence references evaluable: files already absent at baseline"] |

## V1 Final Semantics

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

## CURRENT Semantics (Final)

CURRENT means:

> All evidence references that are evaluable within the V1 deterministic assessment scope
> were successfully verified against baseline (Analysis.targetRevision) and currentKnownRevision
> repository state for the same Source, and no supported temporal degradation was observed.

CURRENT does NOT mean:

> The semantic truth of the Insight has been globally proven.

## Tests

### Unit Tests

| Test | Expected |
|---|---|
| `current_When_PositivelyVerified` | ACTIVE Insight, selectedSource present, targetRevision present, currentKnownRevision present, all refs present at both → CURRENT |
| `current_When_NonEvaluableRefs_Skipped` | Some refs baselinePresent=false (skipped); all evaluable refs present at both → CURRENT |
| `suspectedStale_From_BaselineToCurrent` | ref present at baseline, absent at currentKnownRevision → SUSPECTED_STALE |
| `suspectedStale_With_Enrichment` | SUSPECTED_STALE + ChangedFile DELETED enrichment in supportingEvidence |
| `unknown_When_EvidenceRefs_Empty` | Empty evidenceReferences → UNKNOWN |
| `unknown_When_SelectedSource_Missing` | selectedSource is null → UNKNOWN |
| `unknown_When_TargetRevision_Missing` | targetRevision is null/blank → UNKNOWN |
| `unknown_When_CurrentKnownRevision_Missing` | No commits for source → UNKNOWN |
| `unknown_When_Port_Fails` | RepositoryStatePort throws → UNKNOWN |
| `unknown_When_AllRefs_NonEvaluable` | All refs baselinePresent=false → UNKNOWN |
| `authority_Unchanged` | Insight.status unchanged; no save() calls |
| `repeatable` | Same state → same conclusion + same supportingEvidence |
| `nonActive_Rejected` | SUPERSEDED/ARCHIVED → service throws |
| `source_Scoped_Queries` | RepositoryStatePort called with correct Source for baseline and current |

### Service Integration Tests

| Test | Expected |
|---|---|
| `assess_Returns_Current` | assess(activeInsight) → CURRENT, RepositoryStatePort called with selectedSource for both baseline and current |
| `assess_Returns_SuspectedStale` | assess(insightWithDeletedEvidence) → SUSPECTED_STALE |
| `assess_Returns_Unknown_EmptyRefs` | assess(insightWithNoEvidence) → UNKNOWN |
| `assess_Returns_Unknown_NoBaseline` | assess(insightWithNullTargetRevision) → UNKNOWN |
| `assess_Returns_Unknown_NoSelecedSource` | assess(insightWithNullSelectedSource) → UNKNOWN |
| `authority_Unchanged` | After assess(): Insight.status equals original |
| `no_Status_Save_Occurs` | No insightRepository.save() calls during assessment |
| `port_Scoped_To_Source` | RepositoryStatePort.isFilePresentAtRevision called with selectedSource for both baseline and current |

### Repository Integration Tests

| Test | Expected |
|---|---|
| `findTopBySourceId_Returns_Latest` | findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId) returns latest commit hash |
| `commitsAfterQuery_Returns_ChangedFiles` | findByProjectIdAndCommittedAtAfter returns commits with ChangedFiles (deleted files found for enrichment) |

## Test Categories

A. CURRENT_WHEN_POSITIVELY_VERIFIED
B. SUSPECTED_STALE_FROM_BASELINE_TO_CURRENT_TRANSITION
C. UNKNOWN_WHEN_EVIDENCE_INSUFFICIENT (empty refs, no selectedSource, no targetRevision, no currentKnownRevision, port failure, no evaluable refs)
D. AUTHORITY_UNCHANGED
E. REPEATABLE
F. NON_ACTIVE_HANDLING
G. SOURCE_SCOPED_VERIFICATION
H. ENRICHMENT_CORROBORATING_ONLY

## Transaction Behavior

- `TemporalAssessmentService.assess()` is `@Transactional(readOnly = true)`
- No writes to any entity
- Read-only: ProjectCommitRepository queries + RepositoryStatePort calls
- No `persist()`, `merge()`, or `save()` calls

## No-Write Guarantees

1. Insight.status NEVER modified
2. No archiveInsight/supersedeInsight calls
3. No RESOLVES relation creation/modification
4. No ValidatableProposal/Validation modification
5. No database writes (TemporalAssessment not persisted)
6. No maintenance findings
7. No context eligibility changes
8. No changes to InsightServiceImpl

## Explicit Non-Goals

- ❌ No persistence/TemporalAssessment entity
- ❌ No new database tables or migrations
- ❌ No AI-assisted assessment
- ❌ No AI Engine changes
- ❌ No context eligibility policy changes
- ❌ No Context Engine modifications
- ❌ No automatic supersession or invalidation
- ❌ No age-based heuristics
- ❌ No project-freshness-to-Insight-stale shortcut
- ❌ No dead-end future abstractions
- ❌ No broad lineage changes
- ❌ No generic Temporal Knowledge framework
- ❌ No changes to InsightServiceImpl
- ❌ No automatic context exclusion
- ❌ No broad Git subsystem (only `git cat-file -e` — single read-only command)
- ❌ No repository synchronization behavior added
- ❌ No rename tracking
- ❌ No repository-history reconstruction
- ❌ No Insight.createdAt as baseline replacement
- ❌ No KnowledgeRelation RESOLVES as freshness signal
- ❌ No project-wide latest commit for sourcescoped currentKnownRevision
- ❌ No ChangedFile history as independent conclusion-producing signal (corroborating only)

## Expected Production Diff Size

| Category | Add | Modify | Delete |
|---|---|---|---|
| Java Source | +4 files | +2 methods (1 GitWorkspaceManager + 1 ProjectCommitRepository) | 0 |
| Test Source | +1 file | 0 | 0 |
| **Total** | **+5** | **+2** | **0** |

## Approval Checklist

- [ ] InsightStatus never modified
- [ ] No authority mutation
- [ ] No automatic supersession
- [ ] No persistence
- [ ] No migration
- [ ] No Context Engine changes
- [ ] No AI changes
- [ ] No changes to InsightServiceImpl
- [ ] RepositoryStatePort includes Source identity (not just revision + path)
- [ ] Baseline = Analysis.selectedSource + Analysis.targetRevision (REQUIRED)
- [ ] Current = same selectedSource + currentKnownRevision (not project-wide HEAD)
- [ ] Analysis.targetRevision is REQUIRED (not Insight.createdAt)
- [ ] Repository-state comparison is ONLY conclusion-producing signal
- [ ] ChangedFile history is corroborating only (enrichment)
- [ ] RepositoryStatePort unavailable → UNKNOWN (not SUSPECTED_STALE from commit history alone)
- [ ] CURRENT requires positive verification at both baseline AND currentKnownRevision
- [ ] UNKNOWN for: empty refs, missing selectedSource, missing targetRevision, missing currentKnownRevision, port failure, no evaluable refs
- [ ] SUSPECTED_STALE only when baselinePresent=true AND currentPresent=false
- [ ] baselinePresent=false refs are SKIPPED (not stale)
- [ ] TemporalAssessmentService is separate from InsightServiceImpl
- [ ] RepositoryStatePort is domain port (NOT GitWorkspaceManager direct dependency)
- [ ] Only single read-only git command (`git cat-file -e`) added
- [ ] No repository synchronization behavior added
- [ ] No rename tracking
- [ ] No repository-history reconstruction
- [ ] Tests prove behavior
- [ ] Diff size ~7 files

---

## READY_FOR_IMPLEMENTATION_PLAN_APPROVAL

1. **branch**: story/0083-deterministic-insight-temporal-assessment
2. **Story title**: 0083 — Deterministic Insight Temporal Assessment Foundation
3. **selected deterministic signal**: Repository-state transition — file present at baseline (Analysis.selectedSource + Analysis.targetRevision) but absent at currentKnownRevision (same selectedSource). Checked via RepositoryStatePort.isFilePresentAtRevision(Source, commitHash, relativePath). ChangedFile DELETED records enrich supportingEvidence as corroboration only.
4. **confirmed authority boundary**: InsightStatus never modified; signal is repository-state observation, NOT KnowledgeRelation RESOLVES
5. **TemporalAssessment fields**: insightId, conclusion (CURRENT/SUSPECTED_STALE/UNKNOWN), reasoningOrigin (DETERMINISTIC), supportingEvidence (List<String>), evaluatedAt (Instant)
6. **exact production files**: New — TemporalAssessment.java, TemporalAssessmentService.java, DeterministicTemporalSignal.java, RepositoryStatePort.java; Modified — GitWorkspaceManager.java (add isFilePresentAtRevision), ProjectCommitRepository.java (add findTopBySourceIdOrderByCommittedAtDescCommitHashDesc)
7. **exact tests**: 14 unit tests + 2 repo integration tests + 8 service integration tests
8. **persistence impact**: V1 ZERO
9. **context impact**: V1 NONE
10. **AI impact**: V1 NONE
11. **lineage dependencies**: Reused ProjectCommitRepository (1 new method + 1 existing), ChangedFile (corroborating), Insight.evidenceReferences, Analysis.targetRevision, Analysis.selectedSource; New RepositoryStatePort + GitWorkspaceManager.isFilePresentAtRevision
12. **risks**: 10 identified with mitigations
13. **expected production diff size**: ~7 files (+5 source, +2 methods, +1 test)
14. **exact baseline used**: Analysis.selectedSource + Analysis.targetRevision (REQUIRED; Insight.createdAt NOT used as substitute)
15. **exact current-state verification**: RepositoryStatePort.isFilePresentAtRevision(selectedSource, currentKnownRevision, evidenceRef) — single `git cat-file -e <commit>:<path>` command scoped to selectedSource; currentKnownRevision from ProjectCommitRepository findTopBySourceIdOrderByCommittedAtDescCommitHashDesc(sourceId)
16. **precise CURRENT rule**: All evaluable evidenceReferences (baselinePresent=true) verified present at BOTH baseline (Analysis.targetRevision) AND currentKnownRevision, scoped to the same Analysis.selectedSource
17. **precise SUSPECTED_STALE rule**: Any evidenceReference with baselinePresent=true AND currentPresent=false at currentKnownRevision, scoped to same Source
18. **precise UNKNOWN rule**: Empty evidenceReferences, missing selectedSource, missing targetRevision, missing currentKnownRevision, RepositoryStatePort failure, or all refs baselinePresent=false (no evaluable refs)
19. **behavior when selectedSource missing**: UNKNOWN — "Analysis.selectedSource unavailable"
20. **behavior when Analysis.targetRevision missing**: UNKNOWN — do NOT use Insight.createdAt as substitute
21. **behavior when currentKnownRevision unavailable**: UNKNOWN — "currentKnownRevision cannot be determined for source"
22. **behavior when RepositoryStatePort unavailable/fails**: UNKNOWN — ChangedFile enrichment does NOT substitute for repository-state verification
23. **RepositoryStatePort usage**: TemporalAssessmentService depends on RepositoryStatePort (port in temporal package). GitWorkspaceManager provides implementation. NOT direct dependency.
24. **architectural dependency assessment**: APPROVED — clean architecture via port. Single read-only git command (`git cat-file -e`), scoped to Source. No broad subsystem, no sync, no rename tracking, no history reconstruction. One new ProjectCommitRepository query method (source-scoped latest commit). ChangedFile enrichment is corroborating only.

Finish: READY_FOR_IMPLEMENTATION_PLAN_APPROVAL

Do not implement before final approval.
