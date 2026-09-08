# Design: Validated Knowledge Integration into Story Context Analysis

Design artifact. No production code was modified.

## Status

**DESIGN_COMPLETE — AWAITING_HUMAN_REVIEW**

## Scope

This design determines the smallest architecturally correct integration of DevLog's existing
validated knowledge selection pipeline into Story Context Analysis.

---

## 1. Reconstructed Current Pipeline

### Current flow (disconnected)

```
Engineering Story (storyId)
        │
        ▼
EngineeringContextFacade.getEngineeringContext(projectSlug, intentId, files, storyId)
        │
        ▼
EngineeringContext ──────────────────────────────────────────────────────┐
        │                                                                │
        ▼                                                                ▼
buildSelectedKnowledge(context, story)                          context.metadata().freshness()
        │                                                                │
        ▼                                                                ▼
selectedKnowledge Map                                              contextFreshness snapshot
  ├── project: context.project()                                   (captured, persisted)
  ├── analysis: Map.of()  ← EMPTY
  ├── projectProfile: context.project()  ← DUPLICATE of project
  ├── selectedFacts: List.of()  ← HARDCODED EMPTY
  ├── selectedObservations: List.of()  ← HARDCODED EMPTY
  ├── diagnostics: List.of()  ← EMPTY
  ├── selectedInsights: List.of()  ← HARDCODED EMPTY
  ├── selectionMetadata: List.of()  ← EMPTY
  ├── selectionDigest: context.metadata().contextDigest()
  ├── repositoryContext: {evidence: context.evidence()}
  └── engineeringStories: [single story]
        │
        ▼
AiTaskService.createForStoryContextAnalysisEntity(...)
        │
        ▼
AiTask (with selectedKnowledgeSnapshot, contextSnapshot)
        │
        ▼
AIEngineClient.submit(PromptRequest)
        │
        ▼
Python: StoryContextAnalysisPromptBuilder.build()
  ├── Validates required sections (including selectedFacts, selectedObservations, selectedInsights)
  ├── Builds GROUNDING CONTRACT from repositoryContext.evidence[] only
  └── Constructs prompt with INTENT + CONSTRAINTS + KNOWLEDGE + GROUNDING + SCHEMA
        │
        ▼
LLM generates StoryContextAnalysisResult
        │
        ▼
Python: _validate_output() checks grounding against repositoryContext.evidence[] only
        │
        ▼
Callback → AnalyzeStoryContextUseCase.handleCallback()
        │
        ▼
StoryContextAnalysis persisted (with contextFreshness from Story 0114)
```

### Exact disconnect

**`AnalyzeStoryContextUseCase.buildSelectedKnowledge()` (lines 119-140)** produces empty
knowledge lists:

```java
result.put("selectedFacts", List.of());        // ← DISCONNECT
result.put("selectedObservations", List.of());  // ← DISCONNECT
result.put("selectedInsights", List.of());      // ← DISCONNECT
```

The `KnowledgeSelectionService` exists, is sophisticated (intent-aware ranking, budget
constraints, grounding closure), and is already used by the standard analysis workflow.
It is simply not invoked by the Story Context Analysis use case.

### What the prompt builder already expects

The Python prompt builder (`story_context_analysis.py:76-85`) validates that these sections
exist in `selectedKnowledge`:

```python
required_sections = {
    "project", "analysis", "projectProfile", "selectedFacts",
    "selectedObservations", "diagnostics", "selectedInsights",
    "selectionMetadata", "selectionDigest", "repositoryContext", "engineeringStories",
}
```

The prompt includes them under "SELECTED KNOWLEDGE". The LLM receives them. The issue is
that they are always empty.

---

## 2. Epistemic Model of Selected Knowledge

### Fact (TECHNICAL_EVIDENCE tier)

| Property | Value |
|----------|-------|
| Deterministic | Yes — extracted by collectors during analysis |
| Human validation required | No |
| Trust tier | TECHNICAL_EVIDENCE |
| Lifecycle | Immutable once created; no status field |
| Freshness | Tied to the analysis it belongs to |
| Evidence references | `evidenceReferences: Set<String>` — file paths, commit hashes |
| Suitable for grounding | Yes — facts are direct evidence |

### Observation (TECHNICAL_EVIDENCE tier)

| Property | Value |
|----------|-------|
| Deterministic | Yes — rule-derived from Facts |
| Human validation required | No |
| Trust tier | TECHNICAL_EVIDENCE |
| Lifecycle | Immutable once created |
| Evidence references | Via `supportingFacts` → Fact.evidenceReferences |
| Suitable for grounding | Indirectly — observations are conclusions, not raw evidence |
| Grounding closure | Every selected observation's supporting facts must be in selected facts |

### Insight (TRUSTED tier)

| Property | Value |
|----------|-------|
| Deterministic | No — AI-generated, human-promoted |
| Human validation required | Yes — mandatory Validation entity |
| Trust tier | TRUSTED (highest) |
| Lifecycle | ACTIVE / ARCHIVED / SUPERSEDED |
| Evidence references | `evidenceReferences: List<String>` — the original grounding |
| Suitable for grounding | Context only — insights inform analysis but are not raw evidence |
| Citation format | `insight:<uuid>` — unique, deterministic, traceable |

### Decision (TRUSTED or HUMAN_AUTHORED tier)

| Property | Value |
|----------|-------|
| Deterministic | No — AI-generated or human-authored |
| Human validation required | Context-dependent (proposal is optional) |
| Trust tier | TRUSTED (if from accepted proposal) or HUMAN_AUTHORED |
| Lifecycle | No status field; append-only |
| Suitable for V1 | DEFERRED — different selection semantics needed |

### EngineeringEvent (TRUSTED tier)

| Property | Value |
|----------|-------|
| Deterministic | No — AI-generated, human-promoted |
| Human validation required | Yes — mandatory Validation entity |
| Trust tier | TRUSTED |
| Lifecycle | Append-only; temporal via occurredAt/baseCommit/targetCommit |
| Suitable for V1 | DEFERRED — requires evolution context for proper selection |

### Selection eligibility summary

| Type | INCLUDE_V1 | Reasoning |
|------|-----------|-----------|
| Facts | YES | Deterministic evidence; directly supports grounding |
| Observations | YES | Deterministic conclusions; grounding closure enforces consistency |
| Insights | YES | Trusted knowledge; informs analysis context |
| Decisions | DEFER | Different selection semantics; not all have proposals |
| EngineeringEvents | DEFER | Requires evolution context; different scope |
| HumanContextInputs | YES | Already included in AnalysisContext; limited to 5 |
| KnowledgeRelations | YES | Already passed through without filtering |

---

## 3. Knowledge Selection Contract

### Selection intent

The existing `engineering-story-context-analysis` v1 intent is registered in the
`IntentCatalog` with:

```java
IntentDefinition(
    "engineering-story-context-analysis",
    "v1",
    "Analyze the Engineering Story context...",
    ProposalType.NONE,
    IntentExecutionMode.GENERIC,
    List.of(),  // no supported insight types
    List.of(),  // constraints
    outputSchema,
    "story-context-analysis-prompt-v1",
    List.of("engineering-story-v1", "project-state-v1", "history-v1")
)
```

This intent already exists. **Do NOT create a new intent.**

The intent has `outputProposalType = NONE`, which means the selection service does not
need to include proposal-specific knowledge.

### Selection inputs

The `KnowledgeSelectionService.select()` method requires:

```java
SelectedKnowledge select(AnalysisContext context, IntentDefinition intent, UserGuidance guidance);
```

**AnalysisContext construction for Story Context Analysis:**

The use case must construct a minimal `AnalysisContext` from project-scoped data:

```java
AnalysisContext context = new AnalysisContext(
    projectSnapshot,           // from projectRepository
    analysisSnapshot,          // from the existing Analysis entity
    projectProfile,            // from projectProfileService or project context
    factSnapshots,             // NEW: query from factRepository by project
    observationSnapshots,      // NEW: query from observationRepository by project
    recentKnowledgeEvents,     // from projectContextProvider
    relatedAnalyses,           // List.of() — not needed for this intent
    architectureArtifacts,     // List.of()
    relatedDecisions,          // List.of()
    recentMilestones,          // List.of()
    validatedProposals,        // from projectContextProvider
    evolutionContext,          // null — not an engineering event intent
    validatedEngineeringEvents,// from projectContextProvider
    openChallenges,            // from projectContextProvider
    knowledgeRelations,        // from projectContextProvider
    engineeringStories,        // from projectContextProvider
    humanContextInputs         // from projectContextProvider
);
```

**New repository methods required:**

```java
// FactRepository
List<Fact> findTopByProjectIdOrderByDetectedAtDescIdDesc(UUID projectId, Pageable pageable);

// ObservationRepository
List<Observation> findTopByProjectIdOrderByCreatedAtDescIdDesc(UUID projectId, Pageable pageable);
```

These query the most recent facts/observations across ALL analyses for the project,
not just the current analysis. This ensures the agent sees the full project knowledge.

**UserGuidance:** Passed through from the original request (nullable).

### Selection output

The `SelectedKnowledge` record is the existing output contract:

```java
SelectedKnowledge(
    project, analysis, projectProfile,
    selectedObservations, selectedFacts, diagnostics,
    selectedInsights, existingArchitectureKnowledge,
    selectedEngineeringEvents, selectedHumanContextInputs,
    knowledgeRelations, repositoryContext,
    evolutionContext, selectionMetadata, selectionDigest
)
```

This is projected into the prompt-compatible map by the existing
`SelectedKnowledgePromptProjectionService.toMap()` method.

### Selection budget

The existing budget applies unchanged:

```java
BUDGET = new KnowledgeBudget(
    40,  // maximumFacts
    25,  // maximumObservations
    10,  // maximumInsights
    5,   // maximumArchitectureKnowledge
    60   // maximumRepositoryEvidence
)
```

No new budget is needed. The existing intent-aware ranking and budget constraints
ensure the agent receives a bounded, relevant knowledge set.

---

## 4. Knowledge Types for V1

| Type | Decision | Reasoning |
|------|----------|-----------|
| **Facts** | INCLUDE_V1 | Deterministic evidence; directly supports grounding via evidenceReferences |
| **Observations** | INCLUDE_V1 | Deterministic conclusions; grounding closure ensures consistency |
| **Insights** | INCLUDE_V1 | Trusted knowledge; informs analysis context; cited as `insight:<uuid>` |
| **Decisions** | DEFER | Different selection semantics; not all have proposals; requires design |
| **EngineeringEvents** | DEFER | Requires evolution context; different scope; requires design |
| **HumanContextInputs** | INCLUDE_V1 | Already in AnalysisContext; limited to 5; human-authored context |
| **KnowledgeRelations** | INCLUDE_V1 | Already passed through; provides explicit relationship edges |

---

## 5. Grounding Contract Design

### Current grounding contract

The grounding contract extracts allowed evidence references from
`repositoryContext.evidence[]` only:

```python
def _grounding_contract(self, selected_knowledge):
    allowed_refs = set()
    repo_context = selected_knowledge.get("repositoryContext", {})
    evidence = repo_context.get("evidence", [])
    for item in evidence:
        allowed_refs.add(item.get("reference"))
        allowed_refs.update(item.get("relatedReferences", []))
    return {"allowedEvidenceReferences": sorted(allowed_refs)}
```

### Proposed grounding contract (V1 expansion)

Expand to include evidence references from selected Facts:

```python
def _grounding_contract(self, selected_knowledge):
    allowed_refs = set()
    
    # Source 1: repositoryContext.evidence (existing)
    repo_context = selected_knowledge.get("repositoryContext", {})
    evidence = repo_context.get("evidence", [])
    for item in evidence:
        ref = item.get("reference")
        if isinstance(ref, str):
            allowed_refs.add(ref)
        related = item.get("relatedReferences", [])
        if isinstance(related, list):
            for r in related:
                if isinstance(r, str):
                    allowed_refs.add(r)
    
    # Source 2: selectedFacts[].evidenceReferences (NEW)
    facts = selected_knowledge.get("selectedFacts", [])
    if isinstance(facts, list):
        for fact in facts:
            if isinstance(fact, dict):
                refs = fact.get("evidenceReferences", [])
                if isinstance(refs, list):
                    for ref in refs:
                        if isinstance(ref, str):
                            allowed_refs.add(ref)
    
    return {"allowedEvidenceReferences": sorted(allowed_refs)}
```

**Why this is correct:**

- Facts carry `evidenceReferences` that point to repository evidence (file paths, commit
  hashes). These are the same type of references already in `repositoryContext.evidence[]`.
- Adding them to the allowed set is semantically consistent — they ARE repository evidence.
- Observations do not add new evidence references because their grounding comes from their
  `supportingFacts`, which are already in the selected facts.
- Insights are NOT added to the grounding contract. They are context, not evidence. The
  agent uses insights to inform its analysis but grounds findings in repository evidence.

### Grounding contract for validation

The same expansion must be applied to `_validate_output()` in
`story_context_analysis_generation_service.py`, which builds the allowed evidence set
from the same sources.

### Evidence reference model impact

The `EvidenceRef` schema is unchanged:

```python
class EvidenceRef(StoryContextAnalysisContractModel):
    reference: str = Field(min_length=1, max_length=500)
    resource: str | None = Field(default=None, min_length=1, max_length=500)
```

Findings continue to reference repository evidence. The agent does NOT cite insights
directly in findings. This preserves the output contract and ensures all cited evidence
is deterministic and traceable.

### Why insights are not grounding references

1. **Trust model:** Insights are TRUSTED knowledge, but findings should be grounded in
   TECHNICAL_EVIDENCE. Citing an insight as evidence conflates trust tiers.
2. **Traceability:** Insight → evidence chain is already recorded on the Insight entity
   (`evidenceReferences` field). The agent does not need to re-expose this chain.
3. **Simplicity:** Keeping the grounding contract evidence-only avoids schema changes
   and output contract changes.
4. **ADR-006 compliance:** AI output (findings) grounded in human-validated knowledge
   (insights) could imply the finding inherits trust from the insight. Keeping grounding
   in repository evidence preserves the trust boundary.

---

## 6. Freshness Interaction

**No change to freshness semantics.**

- The `contextFreshness` snapshot is captured at context construction time (Story 0114).
- Selected knowledge may come from analyses that were current when created but are now
  stale relative to the current repository revision.
- The freshness snapshot describes PROJECT-LEVEL synchronization status, not per-item
  validity.
- Filtering knowledge based on freshness would imply semantic invalidation that current
  freshness semantics cannot justify (as established in the freshness investigation).
- The agent receives the freshness snapshot alongside the analysis, enabling consumers
  to assess staleness.

**This is a known limitation, not a defect.** The design preserves the established
distinction:

```
ProjectFreshnessStatus ≠ specific knowledge validity
```

---

## 7. Cross-Story Knowledge

**ALLOWED — existing behavior.**

The `KnowledgeSelectionService` queries facts and observations from the project, not
from a specific story. The selected knowledge is project-scoped. This means the agent
sees knowledge from all previous analyses, including those for other stories.

This is correct because:
- Project-level knowledge (architecture, technology, build system) is story-agnostic
- Story-specific knowledge is provided via `engineeringStories` in the selected knowledge
- The intent-aware ranking ensures story-relevant knowledge is prioritized

---

## 8. Historical Analyses

**NO backfill.**

Historical Story Context Analyses (before this integration) have empty `selectedFacts`,
`selectedObservations`, and `selectedInsights`. They remain historical snapshots.

This is consistent with:
- Story 0114 precedent (historical analyses keep their original context)
- The principle that persisted analysis is a historical record
- The absence of any requirement to regenerate historical analyses

Future analyses will include validated knowledge. The difference is visible and expected.

---

## 9. Evaluation Strategy

### ADR-066 scenario extension

The existing scenario fixture (`engineering-story-context-analysis-v1/scenario.json`)
should be extended to include:

1. Non-empty `selectedFacts` with `evidenceReferences`
2. Non-empty `selectedObservations` with `supportingFactIds`
3. Non-empty `selectedInsights` with `id`, `type`, `title`, `content`

The scenario expectation should be updated to:
- Verify that findings can reference fact evidence references
- Verify that grounding validation accepts expanded evidence set
- Verify that the agent uses insights as context (observable in findings)

### Test strategy

| Test | What it proves |
|------|---------------|
| Unit: `AnalyzeStoryContextUseCase` invokes `KnowledgeSelectionService` | Knowledge is selected |
| Unit: `buildSelectedKnowledge` populates non-empty lists | Lists are not empty |
| Unit: Grounding contract includes fact evidence references | Expanded grounding works |
| Unit: Grounding validation accepts fact evidence references | Validation passes |
| Unit: Grounding validation rejects unknown references | Validation still rejects |
| Unit: Empty knowledge remains valid | No regression when no knowledge exists |
| Integration: Full SCA flow with knowledge-enriched context | End-to-end works |
| ADR-066: Scenario with knowledge items passes | Evaluation passes |
| Regression: Existing tests pass unchanged | No regression |

### Behavioral invariants to verify

1. Agent findings reference validated knowledge items when available
2. Output classification includes FACTUAL_EXTRACTION entries grounded in fact evidence
3. Empty knowledge produces valid analysis (no fabricated knowledge)
4. Historical analysis behavior unchanged
5. Freshness snapshot behavior unchanged (Story 0114)

---

## 10. Answers to Investigation Questions

### Q1: Knowledge budget constraints

**Answer:** Use existing budget unchanged. The `KnowledgeBudget(40, 25, 10, 5, 60)` is
appropriate for V1. The intent-aware ranking ensures story-relevant knowledge is
prioritized. No new budget is needed.

### Q2: Which knowledge types are most valuable

**Answer:** Facts and Observations (deterministic evidence, grounding-capable) and
Insights (trusted context). Decisions and Engineering Events are deferred because they
require different selection semantics.

### Q3: Cross-story knowledge

**Answer:** ALLOWED — existing behavior. The selection is project-scoped, which is correct.
Project-level knowledge is story-agnostic; story-specific context is provided via
`engineeringStories`.

### Q4: Grounding contract handling

**Answer:** Expand `_grounding_contract()` and `_validate_output()` to include fact
`evidenceReferences`. Insights are context, not grounding references. The output schema
is unchanged.

### Q5: ADR-066 evaluation

**Answer:** Extend the scenario fixture with non-empty knowledge items. Update expectations
to verify expanded grounding. Verify the agent uses insights as context.

### Q6: Historical backfill

**Answer:** NO. Historical analyses keep their original context. This is consistent with
Story 0114 precedent and the principle that persisted analysis is a historical record.

---

## 11. Minimal V1 Design

### Flow diagram

```
AnimateStoryContextUseCase.execute()
        │
        ├─ 1. Query project-scoped facts (NEW repository method)
        │     factRepository.findTopByProjectIdOrderByDetectedAtDescIdDesc(projectId, 100)
        │
        ├─ 2. Query project-scoped observations (NEW repository method)
        │     observationRepository.findTopByProjectIdOrderByCreatedAtDescIdDesc(projectId, 50)
        │
        ├─ 3. Query active insights (EXISTING repository method)
        │     insightRepository.findByProjectIdAndStatusOrderByCreatedAtDescIdDesc(projectId, ACTIVE, 10)
        │
        ├─ 4. Build AnalysisContext from project-scoped data
        │     new AnalysisContext(projectSnapshot, analysisSnapshot, profile,
        │         facts, observations, knowledgeEvents, ...,
        │         engineeringEvents, challenges, relations, stories, humanInputs)
        │
        ├─ 5. Invoke KnowledgeSelectionService.select()
        │     selectedKnowledge = selectionService.select(analysisContext, intent, guidance)
        │
        ├─ 6. Project to prompt-compatible map
        │     selectedKnowledgeMap = promptProjectionService.toMap(selectedKnowledge)
        │
        ├─ 7. Merge with repositoryContext (existing)
        │     selectedKnowledgeMap.put("repositoryContext", Map.of("evidence", engineeringContext.evidence()))
        │
        └─ 8. Pass to AI Engine (existing flow unchanged)
```

### Components changed

| Component | Change | Why |
|-----------|--------|-----|
| `AnalyzeStoryContextUseCase` | Invoke knowledge selection; populate selectedKnowledge from selection output | Core integration point |
| `FactRepository` | Add `findTopByProjectIdOrderByDetectedAtDescIdDesc` | Query project-scoped facts |
| `ObservationRepository` | Add `findTopByProjectIdOrderByCreatedAtDescIdDesc` | Query project-scoped observations |
| `StoryContextAnalysisPromptBuilder._grounding_contract()` | Include fact evidence references | Expand allowed evidence set |
| `StoryContextAnalysisGenerationService._validate_output()` | Include fact evidence references | Consistent validation |

### Components unchanged

| Component | Why unchanged |
|-----------|--------------|
| `KnowledgeSelectionService` | Reused as-is; no interface change |
| `SelectedKnowledge` | Output contract unchanged |
| `SelectedKnowledgePromptProjectionService` | Projection unchanged |
| `EngineeringContextFacade` | Context construction unchanged |
| `AiTaskService` | Task creation unchanged |
| `StoryContextAnalysisResult` (Pydantic) | Output schema unchanged |
| `EvidenceRef` (Pydantic) | Evidence reference model unchanged |
| `StoryContextAnalysis` entity | Persistence unchanged |
| `StoryContextAnalysisResponse` | Response contract unchanged |
| Story 0114 freshness snapshot | Freshness mechanism unchanged |
| AI prompt template | Template unchanged; knowledge sections already expected |
| AI system message | System message unchanged |

---

## 12. Implementation Impact Map

### Must change

| Component | Current responsibility | Required change | Contract impact | Test impact |
|-----------|----------------------|----------------|----------------|-------------|
| `AnalyzeStoryContextUseCase` | Builds empty selectedKnowledge | Query facts/observations/insights; invoke selection; project result | selectedKnowledge now non-empty | New unit tests for selection invocation |
| `FactRepository` | Queries by analysisId | Add project-scoped query | New method | New repository test |
| `ObservationRepository` | Queries by analysisId | Add project-scoped query | New method | New repository test |
| `StoryContextAnalysisPromptBuilder` | Grounding from repo evidence only | Include fact evidence references | Expanded allowedEvidenceReferences | Updated grounding tests |
| `StoryContextAnalysisGenerationService` | Validation from repo evidence only | Include fact evidence references | Consistent validation | Updated validation tests |
| ADR-066 scenario fixture | Minimal knowledge | Include knowledge items | Updated fixture | Updated evaluation |

### Must NOT change

| Component | Why |
|-----------|-----|
| `KnowledgeSelectionService` interface | Reused as-is |
| `SelectedKnowledge` record | Output contract unchanged |
| `StoryContextAnalysisResult` (Pydantic) | Output schema unchanged |
| `EvidenceRef` (Pydantic) | Evidence reference model unchanged |
| `StoryContextAnalysis` entity | Persistence unchanged |
| `StoryContextAnalysisResponse` | Response contract unchanged |
| AI prompt template | Sections already expected |
| AI system message | Unchanged |
| Story 0114 freshness mechanism | Unchanged |
| MCP server/tool/client | Response contract unchanged |

---

## 13. ADR Gate

**ADR_REQUIRED = NO**

This integration connects existing architectural capabilities (KnowledgeSelectionService,
AnalysisContext, SelectedKnowledge) rather than establishing a new architectural direction.

The grounding contract expansion is a behavioral change but not an architectural decision —
it follows the established pattern from the insight generation service which already includes
fact evidence references in its grounding set.

If the investigation reveals that the grounding contract change requires broader architectural
consideration (e.g., affecting other intents), an ADR may be warranted. But for V1, the
change is scoped to the Story Context Analysis prompt builder and validation service.

---

## 14. RAG Readiness

**RAG_READINESS = NOT_READY**

This integration does not change the RAG readiness assessment. The primary bottleneck
was that the agent received no validated knowledge. After this integration, the agent
receives knowledge through the existing deterministic selection engine.

Vector retrieval would be warranted only if:
1. The deterministic selection consistently fails to find relevant knowledge
2. The knowledge corpus grows beyond what budget-constrained selection can handle
3. Semantic similarity becomes more relevant than intent-aware ranking

None of these conditions hold for V1.

---

## 15. Unresolved Risks/Questions

1. **AnalysisContext construction scope:** The use case constructs a minimal AnalysisContext.
   If the selection service evolves to require additional AnalysisContext fields (e.g.,
   `relatedAnalyses`, `architectureArtifacts`), the use case must be updated. This is
   a maintenance risk, not a design risk.

2. **Project-scoped fact/observation query performance:** Querying facts and observations
   by project ID across all analyses may be slow for projects with many analyses. The
   budget (100 facts, 50 observations) and pagination mitigate this, but monitoring
   is recommended.

3. **Knowledge freshness alignment:** Selected facts/observations may come from older
   analyses. The freshness snapshot provides project-level status but not per-item
   validity. This is a known limitation documented in the design.

4. **Insight citation format:** Insights are included as context but not as grounding
   references. If future requirements demand insight citation, the EvidenceRef schema
   would need extension. This is deferred.

5. **Grounding closure with project-scoped facts:** The grounding closure algorithm
   ensures every selected observation's supporting facts are in the selected facts.
   With project-scoped facts, the closure set is larger. This should be validated
   empirically but is architecturally sound.

---

*Generated from repository evidence on the `design/validated-knowledge-sca-integration` branch.*
*If repository reality contradicts this design, repository reality wins.*
