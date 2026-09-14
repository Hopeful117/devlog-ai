# Story 0127 - Implementation Report

**Branch:** `main`
**Base:** `c4facb6`
**Commit created:** No

## 1. Scope Implemented

Story 0127 establishes the Core-side AI reference model and execution mapping
without changing the provider-facing contract.

Implemented phases:

- Core `AiReference` value model with explicit type, token and scope;
- deterministic `AiReferenceRegistry` and registry factory;
- binding uniqueness and relationship endpoint lookup;
- grounding capability isolation;
- architecture-knowledge aliasing to underlying Insight bindings;
- immutable mapping snapshot with contract version and SHA-256 digest;
- nullable JSONB persistence on `AiTask`;
- standard Analysis preparation wiring;
- Story Context Analysis preparation wiring;
- legacy null compatibility and JSONB reload coverage.

## 2. Reference Semantics

| Namespace | Scope | Allocation |
|-----------|-------|------------|
| `FACT` | `ANALYSIS_CONTEXT` | `F001...` in selected-list order |
| `OBSERVATION` | `ANALYSIS_CONTEXT` | `O001...` in selected-list order |
| `ANALYSIS` | `ANALYSIS_CONTEXT` | Root only: `A001` |
| `PROJECT_PROFILE` | `ANALYSIS_CONTEXT` | `PP001` |
| `PROJECT` | `PROJECT` | Stable typed reference |
| `INSIGHT` | `PROJECT` | Stable typed reference |
| `ENGINEERING_EVENT` | `PROJECT` | Stable typed reference |
| `HUMAN_CONTEXT` | `PROJECT` | Stable typed reference |
| `REPOSITORY_EVIDENCE` | `REPOSITORY` | Existing canonical evidence reference |

`ExistingArchitectureKnowledgeSnapshot` reuses the underlying `INSIGHT`
binding. `ENGINEERING_STORY`, `DECISION`, and `CHALLENGE` do not receive
speculative independent bindings.

## 3. Persistence

`AiTask.aiReferenceMappingSnapshot` stores the internal execution mapping as a
nullable JSONB field, separate from `selectedKnowledgeSnapshot`.

Snapshot contents:

- `contractVersion`: `AI_REFERENCE_MAPPING_V1`;
- `mappingDigest`;
- ordered bindings containing type, ref, scope,
  `canonicalSourceIdentity`, and grounding capabilities.

Migration added:

- `V49__add_ai_reference_mapping_snapshot_to_ai_tasks.sql`.

The mapping digest is not added to `contextDigest`.

## 4. Preparation Wiring

### Standard Analysis

The existing `SelectedKnowledge` instance is selected once and passed through
`attachSelectedKnowledge`. The task service creates the registry from that same
instance, persists the mapping snapshot, and continues to build the unchanged
legacy provider projection.

### Story Context Analysis

`AnalyzeStoryContextUseCase` creates the registry from the same authorized
`SelectedKnowledge` used to create `selectedKnowledgeSnapshot`. The registry is
passed to the task service and persisted separately from provider input.

No repository access, selection, ranking, retrieval, or relationship discovery
was added to registry creation.

## 5. Files Added

| File | Purpose |
|------|---------|
| `AiReferenceBinding.java` | Immutable registry binding |
| `AiReferenceRegistry.java` | Lookup, uniqueness, grounding candidates and digest |
| `AiReferenceRegistryFactory.java` | Projection-only deterministic registry creation |
| `AiReferenceMappingSnapshot.java` | Persistence-safe mapping snapshot |
| `V49__add_ai_reference_mapping_snapshot_to_ai_tasks.sql` | Nullable JSONB schema migration |
| `AiReferenceTest.java` | Reference value invariants |
| `AiReferenceRegistryTest.java` | Binding, alias, relationship and digest tests |
| `AiReferenceMappingSnapshotTest.java` | Snapshot contract and field preservation |

## 6. Files Modified

| File | Purpose |
|------|---------|
| `AiTask.java` | Added internal JSONB mapping field |
| `AiTaskMapper.java` | Kept mapping out of `AiTaskResponse` |
| `AiTaskService.java` | Added internal Story Context registry overload |
| `AiTaskServiceImpl.java` | Creates and persists mapping snapshots |
| `AnalyzeStoryContextUseCase.java` | Builds registry from selected knowledge |
| `AnalyzeStoryContextUseCaseTest.java` | Verifies registry propagation |
| `AiTaskSelectedEvidencePersistenceIntegrationTest.java` | Verifies reload and legacy null |
| `story.md` | Updated implementation baseline metadata |

## 7. Architecture Compliance

- [x] Java Core remains the deterministic reference authority.
- [x] `AiReference` remains distinct from domain identity.
- [x] Binding uniqueness uses type, scope and canonical source identity.
- [x] Semantic sections and relationships consume identity rather than allocate it.
- [x] Grounding capabilities remain isolated from contextual entities.
- [x] Architecture knowledge aliases Insight identity.
- [x] Root Analysis uses `A001`; related analyses remain repository evidence.
- [x] Mapping metadata remains separate from provider input.
- [x] Provider contract remains unchanged.
- [x] Python remains unchanged.
- [x] Public API remains unchanged.
- [x] Callback resolution and provider migration remain deferred.

## 8. Verification

```text
Focused reference tests:
    18 passed

AiTask and relevant service tests:
    passed

Persistence integration:
    6 passed
    JSONB reload and legacy null compatibility verified

Full backend verification:
    1,316 tests passed
    0 failures
    JaCoCo coverage checks passed

Flyway:
    49 migrations validated and applied through V49
```

## 9. Known Limitations and Follow-Up

- The new typed references are not sent to the provider in Story 0127.
- Callback typed-reference resolution and output contract migration remain
  deferred to the provider migration work and Story 0128.
- Generated build artifacts under `devlog-contracts/target/` remain existing
  working-tree output and are not part of the Story implementation.

## 10. Readiness

**READY_FOR_HUMAN_REVIEW**

No commit or push was performed.
