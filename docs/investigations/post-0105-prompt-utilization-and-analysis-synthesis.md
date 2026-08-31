# Post-Story-0105 Investigation — Prompt Utilization and Analysis Synthesis

## 1. Status

- Status: `INVESTIGATION_COMPLETE`
- Scope: `READ_ONLY`
- Date: `2026-08-31`

## 2. Scope

This investigation evaluates the current post-Story-0105 Analysis pipeline to identify the next material bottleneck limiting Analysis usefulness and depth.

Hard boundary:

- no production code changes
- no test changes
- no frontend changes
- no prompt changes
- no schema changes
- no Story materialization
- no ADR creation

## 3. Baseline

- Investigated branch: `main`
- Investigation baseline SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Story 0104 merged: `YES` via `2342427`
- Story 0105 merged: `YES` via `70d5d27`

Authoritative repository state at start:

- `git status --short` -> untracked `data/`, untracked `docs/investigations/post-0104-structured-context-to-analysis-output-investigation.md`
- `git branch --show-current` -> `main`
- `git rev-parse HEAD` -> `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`

## 4. Governing Context

Documents reviewed:

- `docs/decisions/ADR-064.md`
- `docs/stories/0103-preserve-explicit-relationships-in-analysis-context/engineering-report.md`
- `docs/investigations/post-0103-zero-relationship-eligibility-investigation.md`
- `docs/stories/0104-structured-semantic-sections-for-analysis-context-composition/engineering-report.md`
- `docs/investigations/post-0104-analysis-quality-payload-and-mcp-audit.md`
- `docs/investigations/post-0104-structured-context-to-analysis-output-investigation.md`
- `docs/stories/0105-preserve-proposal-specific-information-in-canonical-analysis-results/engineering-report.md`

Diagnostic sequence preserved:

```text
selection/category dominance -> Story 0098
relationship preservation -> Story 0103
structured semantic composition -> Story 0104
canonical result information loss -> Story 0105
```

Important historical correction:

- pre-0105 shallow human-visible results must not be reused as pure generation evidence because Story 0105 removed a major presentation/projection loss boundary

## 5. Current Pipeline

Measured current path from code:

```text
AnalysisWorkflowServiceImpl.start()
    -> knowledgeCollectionService.collect()
    -> deterministicAnalysisService.analyze()
    -> projectProfileService.build()
    -> AnalysisContextServiceImpl.build()
    -> KnowledgeSelectionServiceImpl.select()
    -> SelectedKnowledgePromptProjectionService.toMap()
    -> PromptRequest.selectedKnowledge
    -> RestAIEngineClient.submit()
    -> ai-engine PromptRequest (Pydantic)
    -> intent-specific prompt builder
    -> OpenAI responses.parse()
    -> structured Pydantic output
    -> decision/insight generation service payload mapping
    -> Java AiTaskResultServiceImpl.handle()
    -> ValidatableProposal JSONB payload + grounding fields
    -> AnalysisResultQueryServiceImpl
    -> canonical GET /api/v1/analyses/{id}/result
```

Boundary findings:

- `AnalysisContextServiceImpl` preserves project snapshots, facts, observations, related analyses, decisions, engineering stories, knowledge relations, and human context.
- `KnowledgeSelectionServiceImpl` applies intent-specific ranking and bounded budgets, adds repository context, preserves knowledge relations, and selects existing architecture knowledge only for `architecture-overview`.
- `SelectedKnowledgePromptProjectionService` preserves canonical arrays and adds `relationshipHighlights`, `semanticSections`, repository evidence, selection metadata, and digest.
- `PromptRequest` wraps selected knowledge without semantic reinterpretation.
- Python prompt builders serialize selected knowledge as JSON and add limited textual instructions.
- Python generation services validate output shape, then map to `AiProposalResult`.
- `AiTaskResultServiceImpl` persists payload and separate grounding IDs.
- Story 0105 `AnalysisResultQueryServiceImpl` now exposes proposal-specific fields through the canonical result.

## 6. Benchmark Methodology

Method used:

- canonical multi-step Analysis workflow on running Docker Compose stack
- project: `devlog-ai` (`f3d56247-aada-4a76-982b-e6802c0b309c`)
- intents:
  - `describe-project-v1`
  - `architecture-overview-v1`
  - `analyze-engineering-decision-v1`

For each run, the investigation captured:

- created `analysisId`
- latest `AiTask` snapshot via `/api/v1/ai-tasks/analysis/{id}`
- `selectedKnowledgeSnapshot`
- canonical `/api/v1/analyses/{id}/result`

Provider observed at runtime:

- `provider = openai`
- `modelIdentifier = gpt-4.1-mini`

## 7. Current Context Inventory

### Measurement

`describe-project-v1` selected knowledge:

- facts: `40`
- observations: `1`
- insights: `10`
- human context inputs: `3`
- relationship highlights: `0`
- semantic sections: `7`
- repository evidence: `60`

`architecture-overview-v1` selected knowledge:

- facts: `40`
- observations: `4`
- insights: `10`
- existing architecture knowledge: `5`
- human context inputs: `3`
- relationship highlights: `0`
- semantic sections: `7`
- repository evidence: `60`

`analyze-engineering-decision-v1` selected knowledge:

- facts: `40`
- observations: `1`
- insights: `10`
- human context inputs: `3`
- relationship highlights: `0`
- semantic sections: `7`
- repository evidence: `60`

### Observation

The benchmark receives materially rich context. The current evidence does not support `INPUT_CONTEXT_GAP` as the primary bottleneck for these three intents.

## 8. PromptProjection Audit

### Fact

`SelectedKnowledgePromptProjectionService` projects these top-level structures into `selectedKnowledge`:

- `project`
- `analysis`
- `projectProfile`
- `selectedFacts`
- `selectedObservations`
- `diagnostics`
- `selectedInsights`
- `existingArchitectureKnowledge`
- `selectedEngineeringEvents`
- `selectedHumanContextInputs`
- `relationshipHighlights`
- `semanticSections`
- `repositoryContext`
- `evolutionContext`
- `selectionMetadata`
- `selectionDigest`

### Fact

Semantic Sections shape:

- `sectionId`
- `sectionTitle`
- `items[]`
  - `itemType`
  - `itemId`
  - `label`

Measured section ordering:

```text
PROJECT_STATE
ARCHITECTURE
DECISIONS
VALIDATED_KNOWLEDGE
HISTORY
REPOSITORY_CHANGES
HUMAN_CONTEXT
```

Measured section behavior:

- empty sections omitted
- items sorted by `itemType`, `label`, `itemId`
- multi-membership represented by repeated references across sections
- canonical content remains elsewhere in the payload; sections contain references only

### Observation

The projection preserves structure well, but the structure itself does not explain:

- why sections exist
- whether memberships are semantic metadata rather than duplicated content
- how a model should use multi-membership
- how sections relate to canonical arrays and repository evidence

### Classification

- `DATA_STRUCTURE = RICH`
- `MODEL_INSTRUCTION = ABSENT_AT_PROJECTION_LAYER`

## 9. Java → Python Transport Audit

### Fact

Transport path:

```text
SelectedKnowledgePromptProjectionService.toMap()
-> Java PromptRequest.selectedKnowledge : Map<String, Object>
-> RestAIEngineClient HTTP JSON
-> ai-engine app.schemas.ai_task.PromptRequest.selected_knowledge : dict[str, Any]
```

### Fact

Current Python `PromptRequest` accepts:

- `selectedKnowledge` as opaque `dict[str, Any]`
- no schema-specific stripping of `semanticSections`
- no alias mismatch for `selectedKnowledge`

### Measurement

Fresh benchmark `AiTask` snapshots contain `semanticSections` with all 7 sections for all three intents.

### Conclusion

- `TRANSPORT_PRESERVED = YES`
- `TRANSPORT_GAP = NO_PRIMARY_EVIDENCE`

## 10. Prompt-Builder Audit

### Shared insight prompt builder

`InsightPromptBuilder`:

- serializes the entire `selectedKnowledge` JSON into the user prompt
- appends a grounding contract
- appends optional user guidance
- appends expected output contract
- for `architecture-overview`, appends explicit delta comparison instructions against existing architecture knowledge

Minimal prompt fragments:

```text
Describe the project using only objectively supported characteristics.
BEGIN UNTRUSTED SELECTED KNOWLEDGE
<full canonical JSON>
END UNTRUSTED SELECTED KNOWLEDGE
```

and for architecture:

```text
When existing trusted architecture knowledge is supplied, compare the new evidence against it.
Return only meaningful architecture deltas.
If nothing materially new is learned, return an empty proposals array.
```

### Decision prompt builder

`EngineeringDecisionPromptBuilder`:

- serializes the entire `selectedKnowledge` JSON into the user prompt
- appends grounding contract and output contract
- adds general instructions to distinguish context, choice, and rationale

Minimal fragment:

```text
BEGIN UNTRUSTED SELECTED KNOWLEDGE
<full JSON>
END UNTRUSTED SELECTED KNOWLEDGE
Return an object with a proposals array.
```

### Observation

Across the inspected intents, Semantic Sections are present in serialized data but are not explicitly explained or operationalized. The model is expected to infer how to use them from raw JSON presence.

### Intent-specific assessment

- `describe-project-v1`: generic objective sentence only; no explicit cross-section synthesis behavior
- `architecture-overview-v1`: explicit delta comparison exists, but no explicit semantic-section usage guidance
- `analyze-engineering-decision-v1`: explicit field-shape guidance exists, but no explicit section prioritization, history reconstruction strategy, or cross-source synthesis instruction

## 11. Semantic Section Utilization Matrix

| Section | Present In Payload | Explained To Model | Explicitly Used | Intent-Specific Use | Cross-Section Use |
|---|---|---|---|---|---|
| PROJECT_STATE | YES | NO | NO | NO | NO |
| ARCHITECTURE | YES | NO | NO | PARTIAL for architecture intent via surrounding task objective, not via section instruction | NO |
| DECISIONS | YES | NO | NO | PARTIAL for decision intent via surrounding task objective, not via section instruction | NO |
| VALIDATED_KNOWLEDGE | YES | NO | NO | NO | NO |
| HISTORY | YES | NO | NO | PARTIAL for decision intent via context profile and objective, not via section instruction | NO |
| REPOSITORY_CHANGES | YES | NO | NO | NO | NO |
| HUMAN_CONTEXT | YES | NO | NO | NO | NO |

Classification:

- `PRESENT_IN_PROMPT = YES`
- `EXPLAINED_TO_MODEL = NO`
- `EXPLICITLY_UTILIZED = NO`
- `CROSS_SECTION_SYNTHESIS_REQUESTED = NO`
- `OBJECTIVE_SPECIFIC_UTILIZATION = PARTIAL_AT_INTENT_TEXT_ONLY`

## 12. Output-Contract Audit

### describe-project-v1 / architecture-overview-v1

Insight output contract requires:

- `insightType`
- `title`
- `summary`
- `rationale`
- `confidence`
- `deltaType`
- `supportingFactIds`
- `supportingObservationIds`
- `evidenceReferences`

Optional by behavior:

- `targetInsightId` only for `ENRICHES`

Assessment:

- rich enough for grounded descriptive synthesis
- does not prevent better synthesis

### analyze-engineering-decision-v1

Decision output contract requires:

- `title`
- `context`
- `choice`
- `rationale`
- `consequences`

Assessment:

- supports richer decision descriptions than pre-0105 humans could see
- does not support grounding fields
- therefore `GROUNDING_CONTRACT_GAP` remains real for decisions, but it is not the first limiting boundary for visible semantic quality

## 13. describe-project-v1 Analysis

### Measurement

- Analysis ID: `d8f95c65-cf1c-44d3-b4f8-fc8db363dd18`
- Status: `COMPLETED`
- Proposals: `7`
- Type: `INSIGHT`

### Observation

Positive:

- canonical result now exposes `rationale`, `insightType`, and separate grounding IDs
- several proposals are grounded in multiple facts or fact+observation
- a human can now inspect actual proposal reasoning

Limitations:

- output remains mostly category-by-category restatement of obvious repository characteristics
- little evidence of synthesis across `HISTORY`, `VALIDATED_KNOWLEDGE`, and `HUMAN_CONTEXT`
- no proposal explains how the project evolved or what major design forces shape it now
- the overview proposal remains generic and ungrounded (`supportingFactIds=[]`, `supportingObservationIds=[]`)

### Qualitative score

- factual correctness: `useful / mostly correct`
- synthesis: `limited`
- engineering interpretation: `partial`
- redundancy: `high`
- grounding: `partial`
- score: `2/3`

### First remaining material loss

`PROMPT_INSTRUCTIONS`

Reason:

- sufficient upstream structured context reaches the model
- output contract supports richer grounded synthesis
- result projection is now sufficient to inspect output
- prompt does not explicitly request cross-section synthesis or project-understanding reconstruction

## 14. architecture-overview-v1 Analysis

### Measurement

- Analysis ID: `4144b66a-3235-41cb-9f42-063c321fb9ec`
- Status: `COMPLETED`
- Proposals: `0`
- Existing architecture knowledge supplied: `5`

### Fact

The architecture prompt explicitly instructs delta comparison and empty output when nothing materially new is learned.

### Conclusion

- `ZERO_PROPOSALS = CORRECT_CURRENT_INTENT_BEHAVIOR`

### Observation

This does not prove prompt utilization is strong. It proves the model obeyed one explicit contract. The prompt still does not explain how to use Semantic Sections, how to compare section-derived evidence against existing architecture knowledge, or how to synthesize architecture and history together when a delta does exist.

### Quality assessment

- correct empty behavior: `YES`
- delta-comparison instruction: `PRESENT`
- observable synthesis quality beyond empty behavior: `LIMITED_BY_NO_OUTPUT`

### First remaining material loss

`NONE_OBSERVED` for the current empty result behavior.

Secondary weakness for future non-empty cases:

- `PROMPT_INSTRUCTIONS` remain under-specified for section-aware architecture synthesis

## 15. Engineering-Decision Analysis

### Measurement

- Analysis ID: `531529e8-9273-4848-9049-39ff9394e821`
- Status: `COMPLETED`
- Proposals: `4`
- Type: `ENGINEERING_DECISION`

### Observation

Positive:

- post-0105 presentation now exposes `context`, `choice`, `rationale`, `consequences`
- proposals are readable and structurally complete

Limitations:

- decisions are broad, generic continuations of obvious technology choices
- little sign of historical reconstruction despite rich `HISTORY` and `REPOSITORY_CHANGES` input
- rationale is plausible but often generic and framework-level rather than project-specific
- no visible connection between repository evolution, validated knowledge, ADR evidence, and a concrete decision timeline
- all decision grounding arrays remain empty by contract

### Qualitative score

- decision specificity: `1-2/3`
- historical reconstruction: `1/3`
- context quality: `2/3`
- choice quality: `2/3`
- rationale quality: `1-2/3`
- consequence quality: `2/3`
- evidence dependence: `weakly visible`
- score: `1/3`

### First remaining material loss

`PROMPT_INSTRUCTIONS`

Reason:

- enough structured input reaches the model
- output contract already allows rich decision semantics
- prompt does not tell the model how to reconstruct real engineering decisions from `DECISIONS + HISTORY + REPOSITORY_CHANGES + VALIDATED_KNOWLEDGE + HUMAN_CONTEXT`

## 16. Input Richness vs Output Richness

### Measurement

All three intents received:

- bounded selected facts and observations
- validated insights
- repository evidence
- semantic sections
- project state metadata
- human context

Two intents produced shallow visible synthesis despite this.

### Inference

The current outputs are not primarily shallow because the inputs are absent. They are shallow because the prompts mostly provide a serialized evidence blob plus broad task wording, leaving cross-source synthesis to model guesswork.

## 17. Context / Generation / Presentation Quality

### A. Context Quality

Classification: `SUFFICIENT_V1`

Evidence:

- 40 facts + repository evidence + insights + human context + semantic sections consistently present
- architecture intent additionally receives existing architecture knowledge

### B. Generation Quality

Classification: `PARTIAL_AND_NOW_DOMINANT_LIMITATION`

Evidence:

- describe-project produces useful but mostly enumerative summaries
- engineering-decision produces complete but generic narratives
- architecture-overview obeys delta-empty contract correctly

### C. Presentation Quality

Classification: `SUFFICIENT_FOR_CURRENT_ANALYSIS_EVALUATION`

Evidence:

- Story 0105 exposes the missing proposal-specific fields needed to inspect the generated semantics

## 18. First Remaining Material Loss Per Intent

- `describe-project-v1` -> `PROMPT_INSTRUCTIONS`
- `architecture-overview-v1` -> `NONE_OBSERVED` for current empty behavior
- `analyze-engineering-decision-v1` -> `PROMPT_INSTRUCTIONS`

## 19. Gap Classification

### Primary

- `PROMPT_UTILIZATION_GAP`
- Severity: `HIGH`
- Affected intents: `describe-project-v1`, `analyze-engineering-decision-v1`, and likely future non-empty `architecture-overview-v1`
- First underutilization boundary: Python prompt builders
- Evidence: semantic sections, validated knowledge, history, repository evidence, and human context are serialized into prompts but not explained or operationalized
- Product impact: outputs remain shallow, generic, or weakly synthetic despite rich input

### Secondary

- `PROMPT_SYNTHESIS_GAP`
- Severity: `HIGH`
- Affected intents: `describe-project-v1`, `analyze-engineering-decision-v1`
- First underutilization boundary: prompt instructions
- Evidence: prompts do not explicitly request cross-section synthesis, contradiction detection, project-evolution reconstruction, or architecture/decision integration
- Product impact: model mostly enumerates or narrates obvious facts instead of building engineering understanding

### Tertiary

- `GROUNDING_CONTRACT_GAP`
- Severity: `MEDIUM`
- Affected intents: `analyze-engineering-decision-v1`
- First loss boundary: decision output contract / decision generation service
- Evidence: schema and payload mapping omit grounding fields; persisted decisions always carry empty grounding arrays
- Product impact: decisions remain less auditable than insights even when the narrative is visible

Not primary based on current evidence:

- `RESULT_PROJECTION_GAP` -> corrected by Story 0105
- `TRANSPORT_GAP` -> no evidence
- `SELECTION_GAP` -> not primary
- `COMPOSITION_GAP` -> composition works as designed

## 20. Selection, Semantic Sections, and Relationships

### Selection

Measurement:

- fresh benchmark repository evidence still contains `COMMIT_DIFF = 12/60 = 20%` for all three intents where present in repository context

Conclusion:

- `SELECTION_PRIMARY_BOTTLENECK = NO`

Reason:

- Story 0098 balance expectations remain satisfied
- inputs are already rich and balanced enough to support better synthesis than the current outputs show

### Semantic Sections

- `COMPOSITION_QUALITY = SUFFICIENT_V1`
- `MODEL_UTILIZATION = LOW`

Reason:

- composition is deterministic, bounded, multi-membership aware, and transport-preserved
- prompt builders do not explain or explicitly use the section structure

### Relationship Highlights

- `RELATIONSHIP_HIGHLIGHTS_PRIMARY_BOTTLENECK = NO`

Reason:

- fresh benchmark still carries `relationshipHighlights = 0`
- post-0103 investigation already explained zero eligibility as a dataset characteristic, not a broken path
- current shallow outputs are observed even where relationships are not the decisive missing ingredient

## 21. ADR-064 Sequence Decision

Decision:

`KEEP_ADR_064_SEQUENCE_PAUSED`

Reason:

- current bottleneck is not the deterministic composition architecture itself
- semantic sections already reach the model and survive transport
- adding more deterministic structure before fixing prompt utilization risks increasing payload without proportional product improvement

## 22. Marginal Value Of Paused ADR-064 Slices

### Timeline Highlights

- Classification: `MEDIUM`

Reason:

- would elevate chronology currently spread across `HISTORY` and repository evidence
- could help decision reconstruction and project-evolution synthesis
- but still risks being underused while prompt utilization remains weak

### Grounding Support

- Classification: `MEDIUM`

Reason:

- would add missing decision audibility information that is genuinely absent today
- but decision quality is already limited earlier by prompt synthesis underuse
- not the first bottleneck for the current benchmark

### Objective-Specific Deterministic Emphasis

- Classification: `HIGH`

Reason:

- aligns directly with ADR-064 and the measured mismatch between broad input and weak intent-specific synthesis
- but should follow or be co-designed with prompt utilization rules, not implemented blindly as more payload structure

## 23. Next Engineering Recommendation

Primary recommendation:

`A — Prompt Architecture / Prompt Utilization Design`

Proposed next step label:

`Intent-Aware Structured Context Utilization`

`DESIGN_REQUIRED = YES`

Reason:

- the measured dominant limitation is generation-side underutilization of already-available structured context
- this should be designed before resuming more ADR-064 composition slices

## 24. Open Questions

- How should Semantic Sections be explained to the model without overstating their authority?
- Which utilization instructions should be shared across intents?
- Which instructions must remain intent-specific?
- How should prompts ask for cross-section synthesis without encouraging hallucinated causal claims?
- Should prompts explicitly distinguish canonical content from semantic memberships?
- Should section priorities be objective-dependent?
- How should history, validated knowledge, and repository changes be connected for decision reconstruction?
- How should future grounding requirements interact with stronger synthesis prompts?
- How should before/after quality be measured after prompt redesign?

## 25. Explicit Non-Actions

This investigation does not recommend, implement, or authorize:

- prompt edits
- Python edits
- Java production changes
- frontend changes
- schema changes
- semantic-section redesign
- selection redesign
- RAG escalation
- architecture-overview redesign
- Story materialization
- ADR creation

## 26. Conclusion

### Fact

Story 0105 corrected the major result-projection loss boundary. The current canonical result is now sufficient to evaluate generated Analysis quality.

### Measurement

Post-0105 benchmark runs show rich selected knowledge reaching Python intact, including Semantic Sections.

### Observation

`describe-project-v1` and `analyze-engineering-decision-v1` still produce shallow or generic synthesis relative to input richness. `architecture-overview-v1` correctly returns zero under its current delta contract.

### Inference

The next real bottleneck is not transport, not result projection, and not primarily deterministic composition. It is the weak utilization of structured context by the current prompt architecture.

### Recommendation

The next engineering step should be a narrowly scoped design investigation for intent-aware structured-context utilization, not another composition slice and not a retrieval/RAG expansion.

Terminal state:

`POST_0105_ANALYSIS_SYNTHESIS_INVESTIGATION_READY_FOR_HUMAN_REVIEW`
