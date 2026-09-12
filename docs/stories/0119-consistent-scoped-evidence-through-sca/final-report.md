# Story 0119 — Final Report

## Commit

- **SHA**: uncommitted (changes on `main`)
- **Branch**: `main`
- **Message**: N/A — awaiting human authorization to commit

## Governance

- **Authorizing Story**: Story 0119 (REFINEMENT)
- **Governing ADRs**: ADR-006, ADR-063, ADR-067
- **Scope**: DELEGATE subtasks only (Subtasks 2, 4, 7)
- **Commit created**: NO (awaiting human authorization)
- **Push performed**: NO

## Implementation Summary

Three delegated subtasks implemented from Story 0119:

1. **Subtask 2** — Added `STORY_DOCUMENT`, `ADR_DOCUMENT`, `ROADMAP_DOCUMENT` to `HUMAN_AUTHORED` trust tier in `classifyTrustTier()`. Previously, these document kinds were classified as `null` (EXCLUDED), silently filtering all repository document evidence from `EngineeringContext`.

2. **Subtask 4** — Replaced `Instant.now()` with `Source.lastSynchronizedAt` as `occurredAt` in `DocumentBodyCollector` for all repository document evidence. Falls back to `Instant.EPOCH` when `lastSynchronizedAt` is null. Uses a `ResolvedWorkspace` record to carry the timestamp from `resolveWorkspace()` through the collect methods. Ensures `RepositoryContext.contextDigest` is deterministic across invocations for the same revision.

3. **Subtask 7** — Added 6 tests: 3 trust-tier classification tests verifying each document kind maps to `HUMAN_AUTHORED` (AC-2), and 3 determinism tests verifying evidence uses `Source.lastSynchronizedAt` (non-null, null fallback, ADR path).

## Files Changed (4 files, +167/-3)

### Production Code
| File | Change |
|------|--------|
| `EngineeringContextContractMapper.java` | Added `STORY_DOCUMENT`, `ADR_DOCUMENT`, `ROADMAP_DOCUMENT` to HUMAN_AUTHORED block (+3/-1) |
| `DocumentBodyCollector.java` | Added `UNAVAILABLE_SYNC_TIMESTAMP` fallback; `ResolvedWorkspace` record; `resolveWorkspace()` reads `Source.lastSynchronizedAt`; threaded `occurredAt` through collect methods (+30/-6) |

### Tests
| File | Change |
|------|--------|
| `EngineeringContextContractMapperTest.java` | 3 trust-tier tests + helper method (+58) |
| `DocumentBodyCollectorTest.java` | 3 determinism tests (non-null, null fallback, ADR path) + import (+143) |

## Verification

- **Targeted tests**: 25/25 PASS (13 mapper + 6 collector + 6 integration)
- **Full backend tests**: 1182/1182 PASS (BUILD SUCCESS)
- **JaCoCo coverage**: All checks met
- **git diff --stat**: 4 backend files changed

## Key Design Decisions

1. **Source.lastSynchronizedAt as occurredAt**: Repository document evidence uses the existing persisted synchronization timestamp from the Source entity, not `Instant.now()`. Falls back to `Instant.EPOCH` when `lastSynchronizedAt` is null (source never collected). This removes execution-time nondeterminism while using the closest existing domain timestamp.
2. **Kind-based classification**: Adding kind strings to the existing `HUMAN_AUTHORED` if-block is the smallest change that fixes invariant I-4. No sourceType check needed since these kinds are exclusively produced by `DocumentBodyCollector` with `REPOSITORY_DOCUMENT` sourceType.
3. **Scope discipline**: Only DELEGATE subtasks implemented. LEARN/PAIR code paths (`RepositoryContextEngine.build(6-param)`, `AnalyzeStoryContextUseCase.buildGroundingContract()`, budget enforcement) untouched.

## Known Limitations

- `Source.lastSynchronizedAt` is a source-level persisted synchronization timestamp, not an immutable timestamp intrinsically bound to a specific Git revision. It represents the most recent prior collection timestamp, not the current SCA execution time
- When `lastSynchronizedAt` is null (source never collected), `Instant.EPOCH` is used as fallback
- Trust-tier fix partially reduces grounding divergence (Subtask 5 not yet implemented)
- Cross-document budget not enforced (Subtask 3 not implemented)
- Scope propagation defect not fixed (Subtask 1 not implemented)

## Readiness

**READY_FOR_HUMAN_REVIEW** — delegated subtasks complete, uncommitted on `main`

## Governance Checklist

- [x] Java/Core deterministic authority preserved
- [x] Python probabilistic boundary respected
- [x] Trust-tier classification corrected (I-4)
- [x] Evidence timestamp deterministic (I-6)
- [x] Scope discipline (DELEGATE subtasks only)
- [x] No LEARN/PAIR code paths modified
- [x] No self-authorized commit/push
- [x] Full backend suite passes (1182/1182)
