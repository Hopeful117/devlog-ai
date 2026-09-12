# Story 0119 - Repository Analysis

## Status

**IMPLEMENTED - DELEGATED SUBTASKS ON MAIN**

## Baseline

- Baseline SHA: `e730c87` (Story 0118 merge on `main`)
- Worktree: Uncommitted changes (delegated subtasks)
- Governing ADRs: ADR-006, ADR-063, ADR-067
- Story 0118: **ACCEPTED** — Revision-Pinned Repository Documents in SCA

## Existing Boundaries Reused

- `EngineeringContextContractMapper.classifyTrustTier()` for trust-tier classification
- `DocumentBodyCollector` for repository document evidence production
- `EvidenceFactory` for evidence construction
- `RepositoryContextEngine` for context composition
- `RepositoryContext.Digest` for deterministic digest computation
- `DocumentBudgetPolicy` for budget configuration

## Implemented Topology

```text
Story 0119 delegated subtasks
  |
  v
Subtask 2: classifyTrustTier()
  adds STORY_DOCUMENT, ADR_DOCUMENT, ROADMAP_DOCUMENT
  to HUMAN_AUTHORED block (was: null/EXCLUDED)
  |
  v
Subtask 4: DocumentBodyCollector
  resolveWorkspace() returns ResolvedWorkspace(workspace, occurredAt)
  occurredAt = Source.lastSynchronizedAt ?? Instant.EPOCH
  replaces Instant.now() in evidence identity
  |
  v
Subtask 7: Conformance tests
  3 trust-tier classification tests
  3 determinism tests (non-null, null fallback, ADR path)
  -> invariant I-4 verified, I-6 verified
```

## Repository Findings

The delegated subtasks fix two invariants violated by Story 0118's post-merge evidence.

**Key findings:**

1. **Invariant I-4 (trust tier)**: `classifyTrustTier()` did not recognize `STORY_DOCUMENT`, `ADR_DOCUMENT`, `ROADMAP_DOCUMENT`. These kinds fell through to `return null` (EXCLUDED), silently filtering all repository document evidence from `EngineeringContext`. Fixed by adding the three kind strings to the existing `HUMAN_AUTHORED` if-block.

2. **Invariant I-6 (deterministic timestamp)**: `DocumentBodyCollector` used `Instant.now()` as `occurredAt` for document evidence. This made `RepositoryContext.contextDigest` non-reproducible across invocations for the same revision. Fixed by reading `Source.lastSynchronizedAt` (the existing persisted synchronization timestamp) in `resolveWorkspace()`, returning it as `occurredAt` via a `ResolvedWorkspace` record, and threading it through all collect methods. Falls back to `Instant.EPOCH` when `lastSynchronizedAt` is null.

3. **No scope propagation changes**: Subtask 1 (LEARN) not implemented. The `RepositoryContextEngine.build(6-param)` defect where scope is lost in `retrieveCandidates(4-param)` remains.

4. **No budget enforcement changes**: Subtask 3 (PAIR) not implemented. `DocumentBudgetPolicy.maxTotalCharacters()` remains unused by `DocumentBodyCollector.collect()`.

## Test Coverage Assessment

**New unit tests (6 tests):**
- `EngineeringContextMapperDocumentTrustTierTest`: 3 tests verifying STORY_DOCUMENT, ADR_DOCUMENT, ROADMAP_DOCUMENT classify as HUMAN_AUTHORED
- `DocumentBodyCollectorTest`: 3 tests verifying evidence uses `Source.lastSynchronizedAt` (story non-null, story null fallback, ADR non-null)

**Existing tests (unchanged, all passing):**
- `EngineeringContextContractMapperTest`: 13 tests (10 existing + 3 new trust-tier)
- `DocumentBodyCollectorTest`: 6 tests (3 existing + 3 new determinism)
- `DocumentBodyCollectorIntegrationTest`: 6 tests (unchanged)

## Conclusion

The delegated subtasks restore two invariants from Story 0119's refinement document. All automated quality gates pass. The changes are minimal and local, affecting only the trust-tier classification and evidence timestamp behavior. Remaining LEARN/PAIR subtasks are explicitly out of scope.
