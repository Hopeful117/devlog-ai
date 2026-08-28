# Investigation: Analysis Depth Diagnosis — Documentation Reconstruction

Investigation-only artifact — zero production changes.
Classification tags: **OBSERVED** / **INFERRED** / **VERIFIED** / **NOT VERIFIED**.

---

# 1. Executive Conclusion

**This problem was already documented, diagnosed, and architecturally decided.**

The Analysis-depth problem — where final outputs remain too superficial despite substantial engineering evidence — was identified, measured, and architecturally resolved in **ADR-063 (Accepted, 2026-08-26)** with the **Human Context Supremacy amendment (Accepted, 2026-08-27)**. The investigation `context-composition-trusted-knowledge.md` produced exhaustive quantitative evidence of the fragmentation: five independent context mechanisms, budget starvation measured with numbers (18 insight candidates → ≤1 selected; 21 story candidates → 0 selected), intent insensitivity across five materially different questions, and visible-vs-citable reference mismatches.

**The three newly observed bottlenecks are not new discoveries.** They are downstream consequences of the same fragmentation that ADR-063 already diagnosed and prescribed shared retrieval primitives to solve. The current diagnosis is a **rediscovery of ADR-063's measured findings**, not a new architectural gap.

**ADR necessity verdict: NO_NEW_ADR_NEEDED.** ADR-063 already defines the correct architecture. The problem is incomplete implementation of ADR-063's prescribed shared retrieval primitives and incremental consumer migration.

---

# 2. Existing Documentation Map

## ADRs (63 total, 9 directly relevant)

| ADR | Title | Status | Relevance |
|-----|-------|--------|-----------|
| ADR-035 | Project History Understanding | Accepted | Defines historical model connecting Git activity to trusted knowledge |
| ADR-036 | Commit-Level Code Diff Analysis | Accepted | Defines target `CommitDiffAnalysisContext` with per-file granularity |
| ADR-037 | Repository-First Context Extraction | Accepted | Establishes repository as primary knowledge source |
| ADR-038 | Repository Context Engine | Accepted | Defines Collectors → Ranking → Selection → Budget pipeline |
| ADR-044 | Bounded Selected File Content Enrichment | Accepted | **Explicitly restricts enrichment to SOURCE_FILE/TEST_FILE** |
| ADR-048 | Project Knowledge Artifact Generation | Accepted | Future capability, not directly related |
| ADR-052 | Internal Human Context Inputs | Proposed | Human context as first-class analysis input |
| ADR-063 | Engineering Context Retrieval & Composition | Accepted | **THE governing architecture for all three bottlenecks** |

## Investigation Reports (6 total, 2 directly relevant)

| Investigation | Key Finding |
|---------------|-------------|
| `context-composition-trusted-knowledge.md` | Five independent context mechanisms; budget starvation measured; Option C adopted (shared retrieval + consumer composition) |
| `human-engineering-context-supremacy.md` | Human Context Supremacy amendment derived from this |

## Key Engineering Stories (directly relevant)

| Story | Title | Status | Relevance |
|-------|-------|--------|-----------|
| 0006 | Commit Diff Evidence Collector | Completed | Produces per-file COMMIT_DIFF evidence |
| 0014 | Bounded Selected File Content | Completed | Implements ADR-044 (SOURCE_FILE/TEST_FILE only) |
| 0095 | Trusted Knowledge Category Composition | READY_FOR_COMMIT_APPROVAL | First ADR-063 implementation: category-aware floors |
| 0096 | Expose Selected Engineering Evidence | STORY_COMPLETE | First Human Context Supremacy P0 slice |

---

# 3. Chronology of Prior Attempts

```text
Problem identified
    │
    ├─ ADR-035/036 (2026-07-23): Project history understanding + commit-level diff analysis
    │   Defines target architecture for per-file granularity
    │   Status: Accepted
    │
    ├─ ADR-037/038 (2026-07-23): Repository-First + Context Engine
    │   Defines pipeline: Collectors → Rank → Select → Budget → Enrich
    │   Status: Accepted
    │
    ├─ Story 0006: Commit Diff Evidence Collector
    │   Implements per-file COMMIT_DIFF evidence from ChangedFile entities
    │   Status: Completed
    │
    ├─ Story 0014/0015/0016: Content enrichment + allocation + symbols
    │   Implements ADR-044: bounded content for SOURCE_FILE/TEST_FILE only
    │   Status: Completed
    │
    ├─ ADR-044 (2026-08-08): Bounded Selected File Content Enrichment
    │   Explicitly decides: enrichment restricted to SOURCE_FILE/TEST_FILE
    │   Status: Accepted
    │
    ├─ Investigation: context-composition-trusted-knowledge.md
    │   Measures: 5 independent systems, budget starvation, intent insensitivity
    │   Recommends: shared retrieval + consumer composition (Option C)
    │
    ├─ ADR-063 (2026-08-26): Engineering Context Retrieval & Composition Architecture
    │   Adopts Option C: shared retrieval primitives + consumer-owned composition
    │   Defines: 5 responsibilities, canonical KnowledgeReference, trust tiers
    │   Status: Accepted
    │
    ├─ Story 0095: Category-Aware Composition
    │   First ADR-063 implementation: bounded fact/observation retrieval + floors
    │   Status: READY_FOR_COMMIT_APPROVAL
    │
    ├─ ADR-063 Amendment (2026-08-27): Human Context Supremacy
    │   Strengthens: human can explore all authorized evidence
    │   Status: Accepted
    │
    ├─ Story 0096: Expose Selected Evidence to Human
    │   First P0 delivery of Human Context Supremacy
    │   Status: STORY_COMPLETE
    │
    └─ TODAY: Analysis depth diagnosis (this investigation)
        Rediscovers the same fragmentation ADR-063 already diagnosed
```

---

# 4. ADR Matrix

| ADR | Classification | Evidence |
|-----|---------------|----------|
| ADR-035 | IMPLEMENTED | History understanding exists; validated events work |
| ADR-036 | PARTIALLY_IMPLEMENTED | CommitDiffEvidenceCollector exists but Analysis pipeline uses aggregated facts |
| ADR-037 | IMPLEMENTED | Repository-First strategy is operational |
| ADR-038 | IMPLEMENTED | RepositoryContextEngine pipeline works with 6 collectors |
| ADR-044 | IMPLEMENTED_AS_DESIGNED | Content enrichment restricted to SOURCE_FILE/TEST_FILE by explicit decision |
| ADR-048 | PLANNED | Future capability |
| ADR-052 | IMPLEMENTED | Human Context Inputs exist and are consumed |
| ADR-063 | PARTIALLY_IMPLEMENTED | Story 0095 delivered first slice; shared retrieval primitives not yet built |

---

# 5. Engineering Story Matrix

| Story | Classification | Notes |
|-------|---------------|-------|
| 0006 (Commit Diff Evidence) | IMPLEMENTED_NOT_CONNECTED | Per-file evidence exists in RepositoryContextEngine but Analysis pipeline uses CommitScopedFactCollector producing aggregated summaries |
| 0014 (Content Enrichment) | IMPLEMENTED_AS_DESIGNED | ADR-044 explicitly restricts to SOURCE_FILE/TEST_FILE |
| 0095 (Category Floors) | IMPLEMENTED | First ADR-063 slice; aggregate floor, not per-category |
| 0096 (Human Evidence) | IMPLEMENTED | Persisted snapshot projection to Angular |

---

# 6. Documented Intent vs Current Implementation

## A. Aggregated Commit Evidence

**ADR-036 target** (line 96-111):
```java
public record CommitDiffAnalysisContext(
    String commitHash,
    List<ChangedFileContext> changedFiles,
    List<ChangedSymbolContext> changedSymbols,
    List<DependencyChangeContext> dependencyChanges,
    List<TestChangeContext> testChanges,
    ...
)
```

**Current implementation**:
- `CommitDiffEvidenceCollector` (Story 0006) produces per-file COMMIT_DIFF evidence from `ChangedFile` entities — this IS the per-file granularity ADR-036 designed
- `CommitScopedFactCollector` (Story 0023) produces aggregated COMMIT_DIFF_SUMMARY facts for grounding contract
- **The Analysis pipeline (KnowledgeSelectionService) uses the aggregated facts, not the per-file evidence**
- `DeterministicKnowledgeContextCollector` converts facts to evidence, but the layer assignment defaults to RELATED_SOURCE_CODE

**Classification: IMPLEMENTED_NOT_CONNECTED**
The per-file evidence exists in RepositoryContextEngine. The Analysis pipeline independently produces and uses aggregated facts. The two paths are not wired together.

## B. Selection Budget Exhaustion

**ADR-063 measured finding** (line 33-35):
> "System A's candidate pool is fixed (~238) and Git-dominated (~70 of 60-item budget); category starvation is measured, not anecdotal: 18 insight candidates → ≤1 selected; 21 story candidates → 0 selected in every run."

**Story 0095 implementation**:
- Added `selectKnowledgeFloor()` in `BudgetedDiverseEvidenceSelector`
- Floor = `clamp(budget/10, 2, 8)` = 6 at current 60-item budget
- Only applies to RepositoryContextEngine's evidence selection
- Does NOT apply to KnowledgeSelectionService's fact selection (which has its own 25-fact budget)

**The `factScore()` method** in `KnowledgeSelectionServiceImpl` (line 139-140):
```java
if (intentId.equals("architecture-overview"))
    return containsAny(type, "SPRING", DOCKER, "REST", BUILD, "MODULE") ? 100 : 10;
```
COMMIT_DIFF_SUMMARY scores 10, while REST_CONTROLLER_DECLARED scores 100. With 25-fact budget, the 14×REST_CONTROLLER + 8×SPRING + 3×CONFIGURATION consume the entire budget.

**Classification: PARTIALLY_IMPLEMENTED**
Floors exist for RepositoryContextEngine (System A) but not for KnowledgeSelectionService (System B). The fact-level diversity gap in System B is not addressed by Story 0095.

## C. Content Enrichment Gap

**ADR-044 explicit decision** (line 19-21):
> "DevLog enriches only selected SOURCE_FILE and TEST_FILE evidence through a versioned post-selection phase."

**ADR-063 §11** (line 253-258):
> "Repository-authored documents are retrievable, not auto-trusted. ADRs, Engineering Story markdown, roadmap and other repository documents become first-class retrieval candidates as HUMAN_AUTHORED items with provenance (path, commit)."

**ADR-063 §28** (line 756-765):
> "The current system does not yet retrieve these document bodies as complete first-class evidence."

**Classification: DESIGNED_NOT_IMPLEMENTED**
The architecture (ADR-063 §11/§28) explicitly calls for repository document retrieval as HUMAN_AUTHORED evidence. This is not yet implemented. ADR-044's restriction to SOURCE_FILE/TEST_FILE was the correct V1 decision; expanding to documents requires the shared retrieval layer ADR-063 prescribes.

---

# 7. Analysis of Aggregated Commit Evidence

**Was per-file evidence already designed?**
Yes. ADR-036 defines `CommitDiffAnalysisContext` with `List<ChangedFileContext>`.

**Was per-file evidence already implemented?**
Yes. `CommitDiffEvidenceCollector` (Story 0006) produces per-file COMMIT_DIFF/CHANGED_FILE evidence from `ChangedFile` entities. This evidence carries `reference = "diff:{sha}:{path}"` and `relatedReferences` to other commits touching the same file.

**Does the Analysis pipeline use it?**
No. The Analysis pipeline (KnowledgeSelectionService) uses `CommitScopedFactCollector` facts (aggregated COMMIT_DIFF_SUMMARY) and `DeterministicKnowledgeContextCollector` which converts facts to evidence. The per-file COMMIT_DIFF evidence from `CommitDiffEvidenceCollector` is only consumed by RepositoryContextEngine for the MCP engineering-context path.

**Is there a wiring gap?**
Yes. `DeterministicKnowledgeContextCollector` converts ALL facts to evidence, but the COMMIT_DIFF_SUMMARY fact's content is just `"41 commits: 707 files changed, +30738/-3072 lines"` — the per-file granularity is in the fact's `evidenceReferences` field but not in the evidence content. The collector at line 32-38 uses `fact.evidenceReferences().stream().findFirst()` as the location, which picks one arbitrary file path.

**Classification: IMPLEMENTED_NOT_CONNECTED**
The per-file evidence exists. The Analysis pipeline's fact-to-evidence bridge does not expose it.

---

# 8. Analysis of Selection/Budget Behavior

**Is diversity/category-aware selection already documented?**
Yes. ADR-038 §"Evidence Selection" says "Selection should maximize usefulness, diversity, explainability, traceability." ADR-063 §5 defines "category floors and ceilings" as consumer-owned composition policy.

**Is there an existing ranking policy to prevent monoculture?**
Yes. `BudgetedDiverseEvidenceSelector` has:
- `selectDiverseEvidence()` — ensures preferred layer representation
- `selectKnowledgeFloor()` — availability-aware knowledge-kind floor (Story 0095)
- `CATEGORY_CONCENTRATION_LIMIT` — prevents single-kind domination

**But these only apply to RepositoryContextEngine, not KnowledgeSelectionService.**
KnowledgeSelectionService has its own `observationScore()` and `factScore()` methods with hardcoded intent-specific scores. No diversity mechanism exists there.

**Classification: IMPLEMENTED_NOT_CONNECTED**
Diversity mechanisms exist in RepositoryContextEngine. KnowledgeSelectionService has no equivalent.

---

# 9. Analysis of Content Enrichment

**Was referenced-file expansion already planned?**
Yes. ADR-063 §10 defines "Progressive expansion is adopted" — "canonical references plus expansion targets let consumers go deeper on demand." ADR-063 §28 says "The current system does not yet retrieve these document bodies."

**Was ADR/Story body retrieval already specified?**
Yes. ADR-063 §11: "ADRs, Engineering Story markdown, roadmap and other repository documents become first-class retrieval candidates as HUMAN_AUTHORED items."

**Is evidence expansion available elsewhere?**
Yes. MCP Resources provide detail-by-UUID navigation. `search_project_history` provides lexical recall. But neither is wired into the Analysis pipeline's composition.

**Was enrichment deliberately restricted for safety/budget reasons?**
Yes. ADR-044 explicitly decides: "Reading every candidate before ranking would increase repository I/O, context size and exposure before relevance is known." This is the correct V1 decision.

**Classification: DESIGNED_NOT_IMPLEMENTED**
The architecture calls for progressive expansion and document retrieval. Implementation is deferred to the shared retrieval layer.

---

# 10. Existing/Future Retrieval Layer Relationship

**ADR-063 defines the target architecture:**

```text
Shared Retrieval Primitives
    ├── lexical historical recall (search_project_history class)
    ├── windowed/recency candidate polling
    ├── reference/trust/temporal metadata assembly
    ├── relation-based expansion lookups
    └── grounding identity syntax and validation helpers

Consumer-Specific Composition
    ├── total budget
    ├── category floors/ceilings
    ├── ranking weights
    ├── intent-specific requirements
    ├── truncation strategy
    └── prompt/output projection + citation allow-list
```

**What was deliberately postponed:**
- ADR-063 §14: "No first-class ContextPack now"
- ADR-063 §13: "Future channels — vector similarity — plug into the shared retrieval-primitive seam"
- ADR-063 Implementation Notes: "Explicitly NOT decided here: vector/embedding technology; ContextPack schema; concrete budget/floor numbers"

**Was the Analysis-depth problem expected to be solved by this architecture?**
Yes. The investigation `context-composition-trusted-knowledge.md` explicitly diagnosed the Analysis-depth problem as a consequence of fragmentation. ADR-063's first implementation step (Story 0095) was specifically designed to fix the worst starvation. The remaining steps (freshness alignment, progressive expansion, documentation composition) are prescribed but not yet implemented.

**Does implementing a local workaround conflict with the future design?**
Yes. ADR-063 §15 explicitly rejects "patching symptoms" (Option E). Local fixes that do not use shared retrieval primitives would create a sixth parallel mechanism. The correct path is completing ADR-063's incremental migration.

---

# 11. Shared Infrastructure vs Analysis-Specific Gaps

## SHARED_CONTEXT_INFRASTRUCTURE (should be implemented once, reused by all consumers)

| Gap | Status | ADR Reference |
|-----|--------|---------------|
| Lexical historical recall | IMPLEMENTED (search_project_history) | ADR-063 §5 |
| Windowed/recency candidate polling | PARTIALLY_IMPLEMENTED (RepositoryContextEngine only) | ADR-063 §5 |
| Canonical KnowledgeReference identity | DESIGNED_NOT_IMPLEMENTED | ADR-063 §3 |
| Trust tier metadata transport | DESIGNED_NOT_IMPLEMENTED | ADR-063 §4 |
| Progressive expansion links | DESIGNED_NOT_IMPLEMENTED | ADR-063 §10 |
| Repository document retrieval | DESIGNED_NOT_IMPLEMENTED | ADR-063 §11/§28 |
| Grounding identity syntax | PARTIALLY_IMPLEMENTED (Engineering Event only) | ADR-063 §8 |

## ANALYSIS-SPECIFIC (consumer-owned composition)

| Gap | Status | Notes |
|-----|--------|-------|
| Intent-specific fact scoring | IMPLEMENTED | `factScore()` in KnowledgeSelectionService |
| Fact budget (25) | IMPLEMENTED | Hardcoded in `BUDGET` constant |
| Observation budget (40) | IMPLEMENTED | Hardcoded in `BUDGET` constant |
| Prompt projection | IMPLEMENTED | `SelectedKnowledgePromptProjectionService` |
| Grounding contract | IMPLEMENTED | `InsightPromptBuilder._grounding_contract()` |

---

# 12. Contradictions/Stale Documentation

| Issue | Location | Resolution |
|-------|----------|------------|
| `knowledge-usability-audit.md` identifies 6 friction points (P1-P6) | docs/knowledge-usability-audit.md | Story 0035 fixed P1 (rationale/confidence/evidence preservation); P4 (diff abandon) relates to this diagnosis; P6 (relations too thin) is acknowledged debt |
| ADR-048 says "Document generation remains valuable" but current docs are shallow | ADR-048 vs runtime output | Not a contradiction — ADR-048 is about future artifact generation, not current Analysis depth |
| Investigation says "five independent context mechanisms" but code shows shared types | context-composition-trusted-knowledge.md | The types are shared but the pipelines are independent — correct diagnosis |

---

# 13. Root Cause Map

```text
Analysis-depth problem (superficial proposals)
    │
    ├── Bottleneck A: Aggregated commit evidence
    │   ├── Root: Analysis pipeline uses CommitScopedFactCollector (aggregated)
    │   │         instead of CommitDiffEvidenceCollector (per-file)
    │   ├── Existing mechanism: CommitDiffEvidenceCollector produces per-file evidence
    │   ├── Gap: IMPLEMENTED_NOT_CONNECTED (wiring between the two pipelines)
    │   └── ADR-063 prescribed fix: shared retrieval primitives
    │
    ├── Bottleneck B: Selection budget exhaustion
    │   ├── Root: KnowledgeSelectionService.factScore() gives COMMIT_DIFF_SUMMARY score=10
    │   │         while REST_CONTROLLER_DECLARED scores 100; 25-fact budget consumed by structural facts
    │   ├── Existing mechanism: BudgetedDiverseEvidenceSelector has floors (Story 0095)
    │   ├── Gap: Floors apply to RepositoryContextEngine, not KnowledgeSelectionService
    │   └── ADR-063 prescribed fix: consumer-owned composition policy with category floors
    │
    └── Bottleneck C: Content enrichment gap
        ├── Root: ADR-044 explicitly restricts enrichment to SOURCE_FILE/TEST_FILE
        ├── Existing mechanism: SelectedFileContentEnricher works correctly for its scope
        ├── Gap: DESIGNED_NOT_IMPLEMENTED (document retrieval per ADR-063 §11/§28)
        └── ADR-063 prescribed fix: progressive expansion via canonical references
```

**Unified root cause**: The Analysis pipeline (KnowledgeSelectionService / System B) and the Repository Context Engine (System A) are two independent composition consumers that share no retrieval, ranking, or enrichment primitives. ADR-063 prescribes shared retrieval primitives but they are not yet implemented. The three bottlenecks are symptoms of this fragmentation.

---

# 14. Reuse / Reconnect / Implement / Redesign Matrix

| Component | Action | Rationale |
|-----------|--------|-----------|
| CommitDiffEvidenceCollector | RECONNECT | Per-file evidence exists; wire into Analysis pipeline's candidate pool |
| BudgetedDiverseEvidenceSelector floors | REUSE | Extend to KnowledgeSelectionService (not just RepositoryContextEngine) |
| SelectedFileContentEnricher | REUSE_AS_DESIGNED | ADR-044 restriction is correct; document retrieval is a separate concern |
| KnowledgeSelectionService.factScore() | RECONNECT | Use shared ranking primitives from ADR-063 instead of hardcoded scores |
| search_project_history | RECONNECT | Already a genuine retrieval primitive; wire into Analysis composition |
| Progressive expansion (ADR-063 §10) | IMPLEMENT | Next incremental step after Story 0095 |
| Repository document retrieval (ADR-063 §11/§28) | IMPLEMENT | Required for ADR/Story content in Analysis context |
| Shared KnowledgeReference identity | IMPLEMENT | Foundation for all incremental steps |
| ContextPack | DEFER | ADR-063 §14: "No first-class ContextPack now" |
| RAG/vector retrieval | DEFER | ADR-063 §13: "Future channels" |

---

# 15. ADR Necessity Verdict

**NO_NEW_ADR_NEEDED.**

ADR-063 (Accepted, with Human Context Supremacy amendment) already defines:
- The five distinct responsibilities (Retrieval, Composition, Projection, Grounding, Expansion)
- Shared retrieval primitives vs consumer-owned composition boundary
- Canonical KnowledgeReference semantics
- Trust tier preservation
- Progressive expansion direction
- Repository document retrieval as HUMAN_AUTHORED evidence
- Explicit rejection of patching symptoms (Option E)

The problem is incomplete implementation, not missing architecture.

---

# 16. Recommended Next Action

**Complete ADR-063's incremental migration steps in order:**

1. ✅ Story 0095: Category-aware composition (DONE)
2. ✅ Story 0096: Human evidence exposure (DONE)
3. **NEXT**: Wire per-file COMMIT_DIFF evidence from `CommitDiffEvidenceCollector` into the Analysis pipeline's candidate pool (fixes Bottleneck A)
4. **THEN**: Extend category floors to KnowledgeSelectionService (fixes Bottleneck B)
5. **THEN**: Implement progressive expansion links (ADR-063 §10)
6. **THEN**: Implement repository document retrieval as HUMAN_AUTHORED evidence (ADR-063 §11/§28) (fixes Bottleneck C)
7. **LATER**: Hybrid retrieval channels (ADR-063 §13)

Each step is an incremental, testable Engineering Story that builds on shared retrieval primitives.

---

# 17. Investigation Artifact Path

`/home/ludo/Bureau/workspace/devlog-ai/docs/investigations/analysis-depth-diagnosis-reconstruction.md`

---

# 18. Confirmation

DOCUMENTATION_RECONSTRUCTION_COMPLETE

- **This problem was already documented**: Yes, in `context-composition-trusted-knowledge.md` and ADR-063
- **Which ADR(s) already cover it**: ADR-063 (Accepted, with Human Context Supremacy amendment)
- **Which Story/Stories attempted to solve it**: Story 0095 (category floors), Story 0096 (human evidence)
- **What is implemented today**: Per-file commit evidence exists (Story 0006); category floors exist (Story 0095); content enrichment works for SOURCE_FILE/TEST_FILE (Story 0014); human evidence projection works (Story 0096)
- **What is missing**: Shared retrieval primitives (ADR-063 §5); canonical KnowledgeReference identity (ADR-063 §3); wiring between CommitDiffEvidenceCollector and Analysis pipeline; category floors for KnowledgeSelectionService; progressive expansion (ADR-063 §10); repository document retrieval (ADR-063 §11/§28)
- **Whether current code has an existing richer mechanism that Analysis simply does not use**: Yes — `CommitDiffEvidenceCollector` produces per-file COMMIT_DIFF evidence that the Analysis pipeline does not consume
- **Whether the problem should be solved by completing existing architecture or designing new architecture**: Completing existing architecture (ADR-063)
- **ADR necessity verdict**: NO_NEW_ADR_NEEDED
- **Recommended next action**: Wire per-file COMMIT_DIFF evidence into Analysis pipeline as next incremental ADR-063 implementation step
- **Investigation artifact path**: `/home/ludo/Bureau/workspace/devlog-ai/docs/investigations/analysis-depth-diagnosis-reconstruction.md`
- **Confirmation**: No production code, tests, ADR, Story, trusted knowledge, commit, push, or merge was performed
