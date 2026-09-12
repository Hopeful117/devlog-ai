# Story 0119 — Consistent Scoped Evidence Through Story Context Analysis

> **Status**: REFINEMENT  
> **Type**: Corrective / Conformance  
> **Created from**: Post-merge evidence of Story 0118  
> **Governing ADRs**: ADR-006, ADR-063, ADR-067  
> **Authoritative HEAD**: `e730c870b64bd67c61bf6450ce1d9e3cb48feac4`

---

## 0. Provenance

This Story is a corrective/conformance Story created from post-merge evidence of Story 0118. Story 0118's historical acceptance record is not rewritten. The orphaned local commit `1596c57` is treated as historical evidence only; current `main` remains authoritative.

---

## 1. Current Production Call-Path Map

### 1.1 End-to-end path (SCA intent)

```
AnalyzeStoryContextUseCase.execute(projectSlug, storyId, files, guidance)
│
├─ resolveRevisionScope(project, story, baselineAnalysisId)   [line 128]
│   ├─ Source = sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc()
│   ├─ sources.getFirst()                                     [line 395, AMBIGUITY]
│   ├─ Resolution cascade:
│   │   1. story.targetCommit → SOURCE_STORY_TARGET
│   │   2. analysis.targetRevision → SOURCE_ANALYSIS_TARGET
│   │   3. workspaceManager.resolveCurrentRevision(source) → SOURCE_CURRENT_REVISION
│   │   4. workspaceManager.synchronize(source, null) → SOURCE_HEAD
│   ├─ workspaceManager.synchronize(source, targetRevision)   [line 440, validates existence]
│   └─ returns RepositoryRevisionScope(projectId, sourceId, resolvedRevision, path, revisionSource)
│
├─ engineeringContextFacade.getEngineeringContext(...)         [line 96]
│   └─ Builds EngineeringContext via deterministic collectors (separate path, no scope needed)
│
├─ knowledgeSelectionService.select(analysisContext, intent, guidance, revisionScope)  [line 129]
│   └─ KnowledgeSelectionServiceImpl.select(...)              [line 67]
│       ├─ requireMandatoryKnowledge()
│       ├─ storyTerms(), factOrder, observationOrder ranking
│       ├─ selectGroundingConsistentKnowledge()
│       ├─ insight selection, existing architecture knowledge
│       ├─ promotedCommitDiffCandidates()                     [line 129-130]
│       ├─ repositoryContextService.build(context, intent, guidance, insightCandidates, promotedCommitDiff, revisionScope)  [line 131-132]
│       │   └─ RepositoryContextEngine.build(6-param overload) [line 102]
│       │       ├─ contextIntelligence.plan(context, intent)  [line 111]
│       │       ├─ request = new ContextRequest(context, intent, guidance, validatedInsights, contextPlan, budget, revisionScope)  [line 112-113]
│       │       ├─ candidates = retrieveCandidates(context, intent, guidance, validatedInsights)  [line 114-115]  ←── DEFECT: 4-param overload
│       │       │   └─ RepositoryContextEngine.retrieveCandidates(4-param)  [line 67]
│       │       │       ├─ contextIntelligence.plan(context, intent)  [line 73]
│       │       │       ├─ request = new ContextRequest(context, intent, guidance, validatedInsights, contextPlan, budget)  [line 74-75]  ←── SCOPE LOST: 6-param constructor defaults revisionScope=null
│       │       │       └─ collectors.forEach(c -> candidates.addAll(c.collect(request)))  [line 77]
│       │       │           └─ DocumentBodyCollector.collect(request)  [line 87]
│       │       │               ├─ scope = request.revisionScope()  [line 88]
│       │       │               ├─ if (scope == null) return List.of()  [line 89-91]  ←── RETURNS EMPTY
│       │       │               └─ ...
│       │       ├─ candidates.addAll(additionalCandidates)   [line 116]
│       │       ├─ ranker.rank(candidates, request)          [line 117]
│       │       ├─ selector.select(ranked, request)          [line 118]
│       │       ├─ symbolEnricher.enrich(...)                [line 119-120]
│       │       ├─ contentEnricher.enrich(...)               [line 121-122]
│       │       ├─ selected sorted by relevanceScore         [line 124-129]
│       │       ├─ digest(...) includes selected evidence     [line 146-147]  ←── Instant.now() in evidence makes non-deterministic
│       │       └─ returns RepositoryContext                 [line 148]
│       │
│       ├─ diagnosticRepository.findById(analysisId)         [line 133]
│       ├─ selection metadata + digest computation           [line 139-167]
│       └─ returns SelectedKnowledge(repositoryContext, ...)
│
├─ promptProjectionService.toMap(selectedKnowledge)           [line 131-132]
│   └─ projectRepositoryContext(selectedKnowledge.repositoryContext())  [line 57]
│       └─ Projects RepositoryContext evidence to prompt-ready map
│
├─ buildGroundingContract(engineeringContext)                 [line 141]
│   └─ Extracts allowedEvidenceReferences from EngineeringEvidence
│       └─ Uses EngineeringContext, NOT the selected Knowledge
│           └─ CONSISTENCY PROBLEM: grounding contract may reference different evidence than prompt
│
├─ aiTaskService.createForStoryContextAnalysisEntity(...)     [line 144-154]
├─ aiTaskService.submit(aiTask.getId(), ...)                 [line 172]
├─ new PromptRequest(requestId, correlationId, ..., selectedKnowledge, groundingContract, ...)  [line 175-188]
└─ aiEngineClient.submit(promptRequest)                      [line 189]
```

### 1.2 Callback path (Python → Java)

```
AnalyzeStoryContextUseCase.handleCallback(correlationId, request)  [line 451]
│
├─ aiTaskRepository.findByCorrelationIdForUpdate(correlationId)
├─ validateStoryContextAnalysisResult(result, task)           [line 475]
│   ├─ groundingContract = task.contextSnapshot["groundingContract"]  [line 515-517]
│   ├─ allowedRefs = groundingContract["allowedEvidenceReferences"]   [line 520]
│   ├─ contextDigest consistency check                         [line 524-528]
│   ├─ validateGroundedFindings(architectureFindings, allowedRefs)
│   ├─ validateGroundedFindings(decisionFindings, allowedRefs)
│   ├─ validateGroundedFindings(evidenceFindings, allowedRefs)
│   ├─ validateGroundedFindings(historicalContext, allowedRefs)
│   ├─ validateGroundedFindings(constraintFindings, allowedRefs)
│   ├─ validateGroundedFindings(impactedComponentFindings, allowedRefs)
│   ├─ validateUncertainties(uncertainties, allowedRefs)
│   ├─ validateOutputClassification(entries)
│   ├─ validateConfidence(level)
│   └─ ValidatableProposal forbidden in V1
│
├─ storyContextAnalysisRepository.save(analysis)
└─ task.setStatus(COMPLETED), save
```

### 1.3 Summary of paths

| Path | Scope source | Consistent? |
|------|-------------|-------------|
| `KnowledgeSelectionServiceImpl → RepositoryContextEngine.build(6-param)` | `revisionScope` from method param | Scope created in `ContextRequest` but **lost** inside `build()` |
| `RepositoryContextAdapter → RepositoryContextEngine.build(4-param)` | `null` | Scope always absent (Analysis/MCP path) |
| `AnalyzeSCA grounding contract` | `EngineeringContext` (deterministic, no scope) | Diverges from prompt evidence path |
| `AnalyzeSCA prompt evidence` | `SelectedKnowledge.repositoryContext` | Scope lost before it reaches collectors |

---

## 2. Confirmed Invariants Currently Violated

| # | Invariant | Violation | Severity |
|---|-----------|-----------|----------|
| I-1 | Repository evidence selected for SCA must originate from an explicitly resolved Source/revision | `DocumentBodyCollector.collect()` returns empty when scope is null; repository documents absent from SCA prompt | **Critical** |
| I-2 | Scope must survive the complete composition path without reconstruction | `RepositoryContextEngine.build(6-param)` creates a scoped `ContextRequest` but calls `retrieveCandidates(4-param)` which constructs a new unscoped request | **Critical** |
| I-3 | Prompt evidence and grounding authorization must reference the same evidence set | Grounding contract derived from `EngineeringContext` (which does not include `REPOSITORY_DOCUMENT` evidence because `DocumentBodyCollector` returns empty); prompt evidence derived from `SelectedKnowledge.repositoryContext` (also empty due to I-2) — latent divergence if I-2 is fixed | **High** |
| I-4 | Document kinds must map to consistent trust tiers | `classifyTrustTier()` in `EngineeringContextContractMapper` does not recognize `STORY_DOCUMENT`, `ADR_DOCUMENT`, `ROADMAP_DOCUMENT` — classified as null (EXCLUDED) instead of `HUMAN_AUTHORED` | **High** |
| I-5 | Source resolution must be deterministic when multiple active Sources exist | `sources.getFirst()` silently selects first active source by `createdAt` + `id` ordering — no explicit disambiguation policy | **Medium** |
| I-6 | Evidence timestamps must not affect semantic identity | `Instant.now()` used as `occurredAt` in `EvidenceFactory.create()` (lines 178, 315) — makes `RepositoryContext.contextDigest` non-deterministic across executions | **Medium** |
| I-7 | Aggregate document budget must be enforced | `DocumentBudgetPolicy.maxTotalCharacters()` exists but is never called by `DocumentBodyCollector.collect()` — no cross-document budget enforcement | **Medium** |
| I-8 | Test seam must not hide propagation failures | All layers mock the next: `AnalyzeSCA` mocks `knowledgeSelectionService`, `KnowledgeSelectionService` mocks `repositoryContextService`, `DocumentBodyCollector` receives pre-constructed `ContextRequest` — no test exercises the full path | **Medium** |

---

## 3. Proposed Architectural Correction

### 3.1 Smallest architectural seam for mandatory scope

**Location**: `RepositoryContextEngine.build(... revisionScope)` at line 102.

**Current defect**: The 6-param `build()` creates a `ContextRequest` with `revisionScope` but calls the 4-param `retrieveCandidates()` which constructs its own request without scope.

**Correction approach**: Route the scoped `ContextRequest` through to `DocumentBodyCollector.collect()` without reconstruction. Two implementation options:

**Option A — Internal request forwarding** (recommended, smallest change):
```java
// In RepositoryContextEngine.build(6-param):
ContextPlan contextPlan = contextIntelligence.plan(context, intent);
ContextRequest request = new ContextRequest(
        context, intent, guidance, validatedInsights, contextPlan, budget, revisionScope);
List<RepositoryEvidence> candidates = new ArrayList<>();
collectors.forEach(collector -> candidates.addAll(collector.collect(request)));
// ... rest of ranking/selection uses the same scoped `request`
```

**Option B — New retrieveCandidates overload**:
```java
List<RepositoryEvidence> retrieveCandidates(ContextRequest request) {
    List<RepositoryEvidence> candidates = new ArrayList<>();
    collectors.forEach(collector -> candidates.addAll(collector.collect(request)));
    return candidates;
}
```
Then `build(6-param)` calls `retrieveCandidates(request)` instead of `retrieveCandidates(context, intent, guidance, validatedInsights)`.

Option A is preferred because it eliminates the separate `retrieveCandidates` call entirely for the scoped path, reducing the surface for future reconstruction errors. The 4-param `build()` and `retrieveCandidates()` remain for backward compatibility with non-SCA intents.

### 3.2 Trust tier classification correction

**Location**: `EngineeringContextContractMapper.classifyTrustTier()` at line 205.

**Add to HUMAN_AUTHORED block**:
```java
if (kind.equals("PROJECT_NOTE")
        || kind.equals("MILESTONE")
        || kind.equals("ARTIFACT")
        || kind.equals("ENGINEERING_STORY")
        || kind.equals("CHALLENGE")
        || kind.equals("STORY_DOCUMENT")       // Story 0119
        || kind.equals("ADR_DOCUMENT")          // Story 0119
        || kind.equals("ROADMAP_DOCUMENT")) {   // Story 0119
    return TrustTier.HUMAN_AUTHORED;
}
```

Rationale: These are repository documents authored by humans, consistent with existing `ENGINEERING_STORY` kind mapping. The `REPOSITORY_DOCUMENT` sourceType is `HUMAN_AUTHORED` content (not `TECHNICAL_EVIDENCE` which is deterministic extraction from code).

### 3.3 Prompt/grounding consistency strategy

**Problem**: The grounding contract is derived from `EngineeringContext` (deterministic collectors), while the prompt evidence is derived from `SelectedKnowledge.repositoryContext` (scoped document collector). If scope propagation is fixed, repository document evidence will appear in the prompt but may not appear in the grounding contract.

**Resolution**: The grounding contract must be derived from the same `SelectedKnowledge` that projects to the prompt. Specifically:
- After `knowledgeSelectionService.select(...)` returns `SelectedKnowledge`
- Extract `allowedEvidenceReferences` from `selectedKnowledge.repositoryContext().evidence()` (the same evidence projected to the prompt)
- This ensures the prompt evidence and grounding authorization reference the same canonical evidence set

**Implementation**: Modify `buildGroundingContract()` to accept `SelectedKnowledge` (or its `RepositoryContext`) rather than `EngineeringContext`.

---

## 4. Source/Revision Ambiguity Policy Options

### Current behavior
```java
List<Source> sources = sourceRepository.findByProjectIdAndActiveTrueOrderByCreatedAtAscIdAsc(project.getId());
Source source = sources.getFirst();
```

Silently selects first active Source by `createdAt` + `id` ordering.

### Options

| Option | Policy | Pros | Cons |
|--------|--------|------|------|
| **A. Explicit single-source (recommended)** | Fail with `IllegalStateException` if `sources.size() > 1`. Expect exactly one active Source per project. | Fails loud, forces clean state | Requires project governance to ensure single active Source |
| **B. Preferred-type selection** | Prefer `GIT_REPOSITORY` type. Fail if multiple of same type. | Supports multi-source projects | Adds type-priority complexity |
| **C. Require explicit Source selection** | Add `sourceId` parameter to SCA request | Maximum determinism | Requires API/Story interface change |
| **D. Keep current behavior** | Document implicit "first created wins" | Zero change | Undocumented contract, silent misselection risk |

**Recommendation**: Option A. A project should have exactly one active Source for deterministic revision resolution. Multiple active Sources is a governance error, not a runtime selection problem.

---

## 5. Correct Deterministic Budget Semantics

### 5.1 Three-tier budget model

| Parameter | Default | Semantic |
|-----------|---------|----------|
| `maxSelectedDocuments` | 5 | Maximum number of documents admitted to the final selection |
| `maxCharactersPerDocument` | 4000 | Maximum characters extracted from any single document body |
| `maxTotalCharacters` | 12000 | Maximum aggregate characters across all selected document bodies |

### 5.2 Deterministic enforcement algorithm

```
INPUT: discovered document candidates, DocumentBudgetPolicy
OUTPUT: selected documents with bounded content

orderedCandidates = metadataOnlySort(candidates)
admittedCandidates = orderedCandidates.take(maxSelectedDocuments)
remainingAggregateBudget = maxTotalCharacters
selected = []

for candidate in admittedCandidates:
    if remainingAggregateBudget <= 0:
        break
    
    maxChars = min(maxCharactersPerDocument, remainingAggregateBudget)
    content = contentReader.readComplete(workspace, candidate.path, maxChars)
    
    if content.status == SKIPPED (INPUT_TOO_LARGE):
        log.warn("Document {} exceeds per-document limit, skipping", candidate.path)
        continue
    
    selected.add(candidate.withContent(content))
    remainingAggregateBudget -= content.text().length()

return selected
```

The primary Story discovery read is independent of the evidence-body character
allocation so explicit references are not lost solely because the Story exceeds
`maxCharactersPerDocument`. Existing `SecureRepositoryContentReader` safety
limits still bound that read. If admitted as evidence, the primary Story remains
subject to the same per-document and aggregate evidence budgets as every other
document.

### 5.3 Key design decisions

1. **Per-document limit is bounded by remaining aggregate budget**: A document's effective maximum is `min(maxCharactersPerDocument, remainingAggregateBudget)`. This prevents the last document from consuming the full per-document limit when only a fraction of the aggregate budget remains.

2. **`Instant.now()` removed from evidence identity**: Replace with the resolved revision from `RepositoryRevisionScope`. The `occurredAt` field becomes the workspace synchronization timestamp (deterministic per revision), not the collection execution timestamp.

3. **`SKIPPED` (INPUT_TOO_LARGE) documents are excluded, not truncated**: `SecureRepositoryContentReader.readComplete()` already rejects oversized documents. The collector should skip them rather than silently returning partial content. Admission is already bounded, so exclusion does not cause an out-of-bound candidate to be materialized as a replacement.

4. **Deterministic metadata-only ordering before admission**: Documents are sorted by `DocumentPriorityComparator` using only metadata available without candidate-body reads: primary Story, referenced ADRs by number/path, referenced Stories by number/path, then roadmap. ADR status and supersession are parsed only after an admitted ADR body is materialized. The budget applied to a specific document is deterministic given the same input set.

5. **Actual retained-length charging**: The budget loop charges the actual `content.text().length()` consumed, not a policy or discovery-read limit. This produces accurate remaining-budget accounting.

---

## 6. Prompt/Grounding Consistency Strategy

### Current state
- **Prompt evidence**: Derived from `SelectedKnowledge.repositoryContext().evidence()` (via `promptProjectionService.toMap()`)
- **Grounding contract**: Derived from `EngineeringContext.evidence()` (via `buildGroundingContract(engineeringContext)`)
- These are separate code paths producing potentially different evidence sets

### Proposed state
- **Prompt evidence**: Same as current (derived from `SelectedKnowledge.repositoryContext().evidence()`)
- **Grounding contract**: Derived from `SelectedKnowledge.repositoryContext().evidence()` — the same evidence that projects to the prompt
- **Canonical evidence identity**: Evidence reference format `{sourceId}:{relativePath}@{resolvedRevision}` (as produced by `DocumentReference.toEvidenceReference()`) is consistent between collection and validation

### Implementation
1. Modify `AnalyzeStoryContextUseCase.buildGroundingContract()` to accept `RepositoryContext` from `SelectedKnowledge`
2. Extract `allowedEvidenceReferences` from the evidence list in that `RepositoryContext`
3. This ensures prompt and grounding share the same evidence universe

---

## 7. Integration/Conformance Testing Strategy

### 7.1 Narrowest test crossing the defect seam

**Test**: `KnowledgeSelectionServiceRepositoryContextPropagationTest`

**What it proves**: When `KnowledgeSelectionServiceImpl.select(analysisContext, intent, guidance, revisionScope)` is called with a non-null `revisionScope`, the `RepositoryContext` returned in `SelectedKnowledge` contains document evidence (non-empty evidence list with `REPOSITORY_DOCUMENT` source type).

**How it crosses the seam**:
- Does NOT mock `RepositoryContextService`
- Uses a real `KnowledgeSelectionServiceImpl` with real `RepositoryContextEngine` (or a minimal stub of collectors)
- Uses a real `DocumentBodyCollector` with real `SecureRepositoryContentReader` (reading from a temporary git worktree or a test fixture directory)
- Verifies that `selectedKnowledge.repositoryContext().evidence()` contains items with `kind == "STORY_DOCUMENT"` or `kind == "ADR_DOCUMENT"`

**Scope boundary**:
- Mock: `InsightRepository`, `AnalysisExecutionDiagnosticRepository` (return empty/minimal)
- Real: `KnowledgeSelectionServiceImpl`, `RepositoryContextEngine`, `DocumentBodyCollector`, `DocumentReferenceExtractor`, `AdrStatusParser`, `SecureRepositoryContentReader` (reading real files)
- Real: `ContextIntelligence`, `EvidenceRanker`, `EvidenceSelector`, `SelectedJavaSymbolEnricher`, `SelectedFileContentEnricher`

**File structure**:
```
backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/
  KnowledgeSelectionServiceRepositoryContextPropagationTest.java
```

### 7.2 Additional targeted tests

| Test | Layer | What it proves |
|------|-------|---------------|
| `DocumentBodyCollectorAggregationBudgetTest` | Unit | Budget enforcement: per-document and aggregate limits respected |
| `DocumentBodyCollectorPriorityOrderingTest` | Unit | Documents processed in deterministic priority order |
| `EngineeringContextMapperDocumentTrustTierTest` | Unit | `STORY_DOCUMENT`, `ADR_DOCUMENT`, `ROADMAP_DOCUMENT` map to `HUMAN_AUTHORED` |
| `AnalyzeStoryContextUseCaseGroundingConsistencyTest` | Unit | Grounding contract derived from same evidence as prompt |

---

## 8. Proposed Story 0119 Scope

**Goal**: Restore and prove the invariant that repository evidence selected for Story Context Analysis originates from an explicitly resolved Source/revision, preserves that scope through the complete composition path, remains bounded and deterministically selected, and uses authorization/trust representation consistent with the evidence projected to the AI.

**Scope**:
1. Fix scope propagation in `RepositoryContextEngine.build(6-param)` (defect I-2)
2. Add `STORY_DOCUMENT`, `ADR_DOCUMENT`, `ROADMAP_DOCUMENT` to trust tier classification (invariant I-4)
3. Implement cross-document budget enforcement in `DocumentBodyCollector` (invariant I-7)
4. Remove `Instant.now()` from evidence identity for deterministic digests (invariant I-6)
5. Align grounding contract with prompt evidence source (invariant I-3)
6. Add integration test proving scope propagation (invariant I-8)
7. Add unit tests for budget, ordering, trust tier, grounding consistency

**Out of scope** (explicit non-goals — see §9):
- Multi-source disambiguation policy (I-5 deferred to next Story)
- Orphaned commit `1596c57` cherry-pick
- Any production code outside the scoped path

---

## 9. Explicit Non-Goals

| Excluded | Reason |
|----------|--------|
| Git patch/diff extraction | Not part of SCA evidence scope |
| RAG, vector search, embeddings | Not authorized in current architecture |
| Historical retrieval algorithms | Existing collectors sufficient |
| Kiko orchestration | Separate concern |
| OpenClaw integration | Separate concern |
| General ContextPack redesign | ADR-063 §42 defines scope |
| Multi-source disambiguation | Deferred; option A (fail-loud) sufficient |
| Orphaned commit `1596c57` recovery | Historical evidence only |
| Broad repository refactoring | Scope discipline |
| API interface changes | No Story/MCP contract changes |

---

## 10. Proposed Acceptance Criteria

### AC-1: Scope propagation
Given `KnowledgeSelectionServiceImpl.select(...)` is called with a non-null `RepositoryRevisionScope`,  
When the resulting `SelectedKnowledge` is projected to prompt evidence,  
Then `selectedKnowledge.repositoryContext().evidence()` contains at least one item with a `REPOSITORY_DOCUMENT` source type and a non-empty `content.text()`.

### AC-2: Trust tier classification
Given a `RepositoryEvidence` with `kind` in `{STORY_DOCUMENT, ADR_DOCUMENT, ROADMAP_DOCUMENT}`,  
When mapped through `EngineeringContextContractMapper`,  
Then the resulting `EngineeringEvidence.trustTier()` is `HUMAN_AUTHORED`.

### AC-3: Aggregate budget enforcement
Given `DocumentBudgetPolicy(maxSelectedDocuments=2, maxCharactersPerDocument=4000, maxTotalCharacters=6000)`,  
When `DocumentBodyCollector.collect()` processes 3 documents of 3000 characters each,  
Then only 2 documents are returned and their total characters do not exceed 6000.

### AC-4: Per-document budget bound by aggregate
Given `DocumentBudgetPolicy(maxSelectedDocuments=5, maxCharactersPerDocument=4000, maxTotalCharacters=5000)`,  
When `DocumentBodyCollector.collect()` processes documents where the first consumes 4000 characters,  
Then the second document receives at most 1000 characters (remaining aggregate budget).

### AC-5: Deterministic digest
Given identical input (same project, analysis, facts, observations, documents),  
When `KnowledgeSelectionServiceImpl.select()` is called twice,  
Then `selectedKnowledge.selectionDigest()` is identical across both invocations.

### AC-6: Grounding/prompt consistency
Given a `SelectedKnowledge` with repository document evidence,  
When the grounding contract is built from the same `SelectedKnowledge`,  
Then `groundingContract.allowedEvidenceReferences` is a subset of the evidence references projected to the prompt.

### AC-7: Integration test passes
Given the `KnowledgeSelectionServiceRepositoryContextPropagationTest`,  
When executed,  
Then the test verifies that scope propagation survives the complete `KnowledgeSelectionServiceImpl → RepositoryContextEngine → DocumentBodyCollector` path.

---

## 11. Proposed Implementation Subtasks

### Subtask 1: Fix scope propagation in RepositoryContextEngine.build(6-param)
**Classification**: **LEARN**  
**Rationale**: This is the primary defect. Understanding the internal composition flow (how `ContextRequest` is constructed, how collectors receive it, how the scoped and unscoped paths diverge) is essential architectural knowledge. Delegating this would skip understanding of the core composition pattern.

**Implementation**:
- Modify `RepositoryContextEngine.build(6-param)` to use the scoped `ContextRequest` directly for candidate retrieval instead of calling the 4-param `retrieveCandidates()`
- Ensure the 4-param `build()` and `retrieveCandidates()` remain unchanged for non-SCA intents
- Verify no other callers of the 6-param `build()` depend on the implicit reconstruction behavior

**Files**:
- `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContextEngine.java`

---

### Subtask 2: Add document kinds to trust tier classification
**Classification**: **DELEGATE**  
**Rationale**: Mechanical addition of three string constants to an existing if-block. The semantics (these are human-authored documents, not technical evidence) are already established. No architectural reasoning required.

**Implementation**:
- Add `STORY_DOCUMENT`, `ADR_DOCUMENT`, `ROADMAP_DOCUMENT` to the `HUMAN_AUTHORED` block in `classifyTrustTier()`
- Verify no existing tests break

**Files**:
- `backend/src/main/java/com/hopeful117/devlogai/engineeringcontext/mapper/EngineeringContextContractMapper.java`

---

### Subtask 3: Implement cross-document budget enforcement
**Classification**: **PAIR**  
**Rationale**: The budget model design requires careful reasoning about deterministic ordering, per-document vs aggregate limits, and the interaction with `SecureRepositoryContentReader.readComplete()`. Pairing ensures the design intent is preserved while the mechanical implementation proceeds.

**Implementation**:
- Modify `DocumentBodyCollector.collect()` to process candidates in priority order
- Track `remainingAggregateBudget` across documents
- Charge `content.text().length()` per document (not `maxCharactersPerDocument`)
- Skip documents that would exceed aggregate budget (deterministic behavior: once budget exhausted, stop collecting)
- Add `DocumentPriorityComparator` (deterministic metadata-only sort: primary Story, ADR number/path, referenced Story number/path, roadmap)
- Add unit tests for budget enforcement

**Files**:
- `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/DocumentBodyCollector.java`
- `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/DocumentPriorityComparator.java` (new)
- `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/DocumentBodyCollectorBudgetTest.java` (new)

---

### Subtask 4: Remove Instant.now() from evidence identity
**Classification**: **DELEGATE**  
**Rationale**: Mechanical replacement of `Instant.now()` with workspace synchronization timestamp. The semantic change (evidence identity based on revision, not execution time) is a design decision already made; the implementation is straightforward.

**Implementation**:
- Replace `Instant.now()` in `DocumentBodyCollector.collectStoryDocument()` and `collectDocumentBody()` with the workspace synchronization timestamp (available from `SynchronizedWorkspace` or from `RepositoryRevisionScope`)
- Verify digest determinism in tests

**Files**:
- `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/collector/DocumentBodyCollector.java`
- `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/DocumentBodyCollectorTest.java`

---

### Subtask 5: Align grounding contract with prompt evidence
**Classification**: **LEARN**  
**Rationale**: This changes the semantic contract of `buildGroundingContract()`. Understanding how the grounding contract is consumed by Python callback validation, and how it interacts with the prompt evidence projection, is architectural knowledge that prevents future divergence bugs.

**Implementation**:
- Modify `AnalyzeStoryContextUseCase.buildGroundingContract()` to accept `RepositoryContext` (from `SelectedKnowledge`) instead of `EngineeringContext`
- Extract `allowedEvidenceReferences` from the repository context evidence
- Update `AnalyzeStoryContextUseCaseTest` accordingly
- Verify callback validation still passes with the new grounding source

**Files**:
- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCase.java`
- `backend/src/test/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCaseTest.java`

---

### Subtask 6: Write integration test for scope propagation
**Classification**: **PAIR**  
**Rationale**: This is the most architecturally significant test in the Story. It must cross the `KnowledgeSelectionServiceImpl → RepositoryContextEngine → DocumentBodyCollector` seam — the exact point where the current defect occurs. Designing the test boundaries (what to mock, what to leave real) requires understanding the full composition path. Pairing ensures the test is correctly scoped.

**Implementation**:
- Create `KnowledgeSelectionServiceRepositoryContextPropagationTest.java`
- Wire real `KnowledgeSelectionServiceImpl` with real `RepositoryContextEngine` and real `DocumentBodyCollector`
- Use a temporary directory as workspace with fixture documents (story markdown, ADR)
- Mock only: `InsightRepository`, `AnalysisExecutionDiagnosticRepository`
- Assert: `selectedKnowledge.repositoryContext().evidence()` contains document evidence with correct kinds

**Files**:
- `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceRepositoryContextPropagationTest.java` (new)
- Test fixture files under `backend/src/test/resources/` (new)

---

### Subtask 7: Write unit tests for trust tier, grounding consistency, budget
**Classification**: **DELEGATE**  
**Rationale**: Repetitive test matrix covering the three areas. Design is specified in AC-2, AC-3, AC-4, AC-6. Implementation is mechanical.

**Implementation**:
- `EngineeringContextMapperDocumentTrustTierTest`: verify each document kind maps to `HUMAN_AUTHORED`
- `DocumentBodyCollectorBudgetTest`: verify per-document and aggregate limits
- `AnalyzeStoryContextUseCaseGroundingConsistencyTest`: verify grounding contract derived from same evidence as prompt

**Files**:
- `backend/src/test/java/com/hopeful117/devlogai/engineeringcontext/mapper/EngineeringContextMapperDocumentTrustTierTest.java` (new)
- `backend/src/test/java/com/hopeful117/devlogai/repositorycontext/collector/DocumentBodyCollectorBudgetTest.java` (new)
- `backend/src/test/java/com/hopeful117/devlogai/storycontextanalysis/usecase/AnalyzeStoryContextUseCaseGroundingConsistencyTest.java` (new)

---

## 12. Risks and Open Decisions

### Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Scope propagation fix changes digest computation | Existing `StoryContextAnalysis` records may have inconsistent digests | Digest is recomputed on each execution; no migration needed. Historical digests are snapshots, not authoritative. |
| Grounding contract source change may reject previously valid callbacks | Python responses referencing old evidence format may fail validation | Verify callback validation tests pass with new grounding source before merge |
| Budget enforcement changes may exclude previously included documents | SCA prompts may have less context than before | Default budget (`maxTotalCharacters=12000`) is generous; verify with representative Story fixtures |
| `SecureRepositoryContentReader.readComplete()` rejects oversized documents | Documents exceeding `maxCharactersPerDocument` are `SKIPPED`, not truncated | This is the correct behavior (preserves content integrity). Document the log warning. |

### Open Decisions Requiring Human Validation

| # | Decision | Options | Recommendation |
|---|----------|---------|----------------|
| D-1 | Multi-source disambiguation policy | Fail-loud (A) / Preferred-type (B) / Explicit selection (C) / Keep current (D) | Option A: fail-loud if >1 active Source |
| D-2 | Should `DocumentPriorityComparator` be configurable or hardcoded? | Configurable via Spring bean / Hardcoded | Hardcoded for V1; configurability is premature |
| D-3 | Should `SKIPPED` documents be retried with truncated content? | Skip (current) / Retry with smaller limit | Skip — preserves content integrity |
| D-4 | Should the integration test use a real git worktree or a plain directory? | Real worktree / Plain directory | Plain directory — sufficient to prove scope propagation; git operations are out of scope |
| D-5 | Should the Story include the multi-source fix (I-5)? | Include / Defer | Defer — option A (fail-loud) is a one-line change but should be a separate Story for clean history |

---

*Generated from repository evidence at `e730c870b64bd67c61bf6450ce1d9e3cb48feac4`. If repository reality contradicts this document, repository reality wins.*
