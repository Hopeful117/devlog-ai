# Repository Analysis — Story 0085

## 1. Project Understanding lifecycle

`ProjectUnderstandingService.execute()`:

1. `preparationService.prepare(projectId, request)` — resolves the Source, normalizes the
   requested revision, calls `workspaceManager.synchronize(source, revision)`, imports
   history, and builds `PreparedProjectUnderstanding(projectId, sourceId, targetRevision,
   resolvedRevision, guidance, intent, sourceSnapshot)`.
2. `claimService.claim(prepared)` — builds an `Analysis.builder()` with
   `selectedSource(source)`, persists it via `analysisRepository.saveAndFlush(...)`.
3. `workflowService.start(analysisId)` — runs knowledge collection, deterministic analysis,
   profile build, context build, AI task submission.

## 2. Preparation produces both requested and resolved revision

`ProjectUnderstandingPreparationService.prepare()` (lines 44-48):

```java
String revision = normalize(request.targetRevision());   // caller expression (may be null)
SynchronizedWorkspace workspace = workspaceManager.synchronize(source, revision);
return new PreparedProjectUnderstanding(projectId, source.getId(), revision,
        workspace.resolvedRevision(), ...);
```

`PreparedProjectUnderstanding` (record) therefore carries BOTH:

- `targetRevision()` — the caller-supplied Git revision expression (may be null);
- `resolvedRevision()` — the exact immutable commit actually observed.

## 3. The defect

`ProjectUnderstandingClaimService.claim()` previously constructed the Analysis with:

```java
.targetRevision(prepared.targetRevision())
```

This persisted the caller's expression (or `null`). When no revision was requested,
`targetRevision` was persisted as `null` even though synchronization observed the
repository at a known immutable revision. This is the exact defect ADR-061 §63-68 describes.

## 4. GitWorkspaceManager already guarantees commit-identity equivalence

`GitWorkspaceManager.synchronize(source, targetRevision)`:

1. `requestedRevision(source, targetRevision)` — non-blank expression → trimmed string;
   else source default branch (lines 156-164).
2. `resolveRevision(workspace, requested, explicitRevision)` — for an explicit revision:
   `rev-parse --verify refs/remotes/origin/<requested>^{commit}`, falling back to
   `rev-parse --verify <requested>^{commit}` (lines 166-192). Returns the canonical full
   commit hash the expression resolves to.
3. `checkout --force --detach <resolved>`, `reset --hard <resolved>`, `head = rev-parse
   HEAD` (lines 150-152).

Consequence: `SynchronizedWorkspace.resolvedRevision()` equals the exact immutable commit
the requested expression resolved to, and that exact commit is what is checked out and
observed by collectors. For `"main"`, a short SHA, or a full SHA, the resolved value is
the same immutable commit identity. No additional requested-vs-resolved guard is needed.

## 5. Execution key is REQUEST_IDENTITY

`ProjectUnderstandingExecutionKey.compute(...)` hashes projectId, sourceId,
`normalizeRevision(targetRevision)` (the raw trimmed request string, or `"<default>"`),
intent id/version, and guidance (lines 25-45). No resolution enters the hash.

Consequences:

- Two requests for the same Source/commit expressed differently (`"main"` vs the full SHA)
  produce DIFFERENT keys — they are separate REQUEST identities.
- The persisted Analysis baseline (OBSERVATION_IDENTITY) is intentionally distinct from
  the execution key (REQUEST_IDENTITY). Story 0085 preserves this distinction: it changes
  only what is persisted, not what is keyed.

## 6. Multi-source path is untouched by construction

- `AnalysisServiceImpl.create()` → `AnalysisMapper.toEntity()` ignores `selectedSource`
  and `targetRevision` (mapper lines 20-36); `CreateAnalysisRequest` has no sourceId, and
  `selectedSource` remains null.
- `KnowledgeCollectionServiceImpl.resolveSources()` (lines 179-183): `selectedSource ==
  null` ⇒ collect over ALL active project Sources — multi-source semantics.
- The Story 0085 diff is strictly inside `ProjectUnderstandingClaimService`, which always
  sets `selectedSource` from the prepared, synchronized Source. It cannot affect the
  standard multi-source path or fabricate a single baseline for it.

## 7. Downstream collection is pinned to the persisted revision

`KnowledgeCollectionService.collect()` calls `workspaceManager.synchronize(source,
analysis.getTargetRevision())` (line 83). After Story 0085, `targetRevision` is the exact
immutable commit hash R, so any later synchronization resolves exactly to R.

## 8. Failure behavior

- Source missing / inactive / wrong type → thrown in preparation; no Analysis is created.
- Synchronization failure / invalid revision / Git failure → thrown in preparation before
  claim; no Analysis with fabricated provenance is created.
- Analysis persistence failure → `DataIntegrityViolationException` handled in
  `execute()` (race → findWinner, else propagate); no false baseline.
- Collection or downstream AI failure after a valid baseline persisted → the Analysis
  retains its valid immutable baseline and is marked FAILED by `AnalysisWorkflowService`;
  no new lifecycle state required (ADR-061 §6, §8).

## 9. ProjectCommit is not authority

The proposed path uses only `SynchronizedWorkspace.resolvedRevision()`. 
`ProjectCommitRepository.findTopBySourceId...` is used ONLY by Story 0083 as
`currentKnownRevision` for comparison, never to construct the baseline.

## 10. AI is not authority

No AI component selects, infers, or repairs targetRevision or selectedSource for
purpose of provenance. Baseline capture is fully deterministic.
