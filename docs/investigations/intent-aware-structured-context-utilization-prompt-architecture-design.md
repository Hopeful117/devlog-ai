# Intent-Aware Structured Context Utilization — Prompt Architecture Design

## 1. Status

- Status: `DESIGN_INVESTIGATION_COMPLETE`
- Scope: `DESIGN_ONLY`
- Date: `2026-08-31`

## 2. Scope

This document designs how DevLog should instruct the AI to interpret and synthesize already-available structured engineering context according to Analysis intent.

Explicit non-scope:

- no implementation
- no prompt edits in this task
- no schema changes
- no Java or Angular changes
- no Story materialization
- no ADR creation

## 3. Baseline

- Baseline branch: `main`
- Baseline SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Story 0104 merged: `YES`
- Story 0105 merged: `YES`
- Post-0105 prompt-utilization investigation present: `YES`

Repository state at design start:

- untracked `data/`
- untracked `docs/investigations/post-0104-structured-context-to-analysis-output-investigation.md`
- untracked `docs/investigations/post-0105-prompt-utilization-and-analysis-synthesis.md`

## 4. Governing Context

Read and preserved:

- `docs/decisions/ADR-064.md`
- Story 0103 engineering report
- post-0103 zero-relationship investigation
- Story 0104 engineering report
- post-0104 investigations
- Story 0105 engineering report
- post-0105 prompt-utilization investigation

Key invariants retained:

- Java organizes known engineering context deterministically
- AI interprets and synthesizes supplied context
- Semantic Sections are reference-based semantic perspectives over canonical content
- multi-membership is allowed and does not create extra evidence
- zero-delta architecture behavior remains valid
- prompt-injection boundary remains mandatory

## 5. Established Diagnosis

Measured facts carried forward:

- selected context is rich enough for current V1 evaluation
- Semantic Sections survive Java -> Python transport
- current prompts mostly serialize `selectedKnowledge` as JSON
- Semantic Sections are not explicitly explained to the model
- cross-section synthesis is not explicitly requested
- Story 0105 made result presentation sufficient for evaluating generated semantics
- primary gap: `PROMPT_UTILIZATION_GAP`
- secondary gap: `PROMPT_SYNTHESIS_GAP`

## 6. Current Prompt Architecture

### Fact

Current Python prompt path:

```text
InsightPromptBuilder
  -> shared for describe-project / architecture-overview / generate-readme

EngineeringDecisionPromptBuilder
  -> dedicated for analyze-engineering-decision
```

Both builders currently:

- place authoritative instructions outside untrusted context
- serialize full `selectedKnowledge` JSON inside `BEGIN UNTRUSTED SELECTED KNOWLEDGE`
- append output contract and grounding constraints
- provide only limited interpretation instructions

### Design constraint

The next change should improve interpretation of existing structure, not duplicate canonical content in new textual form.

## 7. Semantic Sections Model-Facing Semantics

### Proposed contract

The model should be told that Semantic Sections are semantic perspectives over the same selected project context.

For each section:

| Section | Represents | Authority | Model Should Use It For | Model Must Not Infer |
|---|---|---|---|---|
| `PROJECT_STATE` | current project identity, profile, stable project-level characteristics | deterministic project context | establish what the project currently is | motivation or historical causality from current state alone |
| `ARCHITECTURE` | architecture-relevant facts, observations, validated architecture knowledge, architecture-tagged repository evidence | deterministic evidence + trusted existing knowledge where supplied | identify structure, boundaries, architectural characteristics, and architecture-relevant deltas | architecture quality judgment unless supported; rediscovery of trusted knowledge as new |
| `DECISIONS` | explicit decision signals, ADR-related evidence, decision-tagged repository evidence | deterministic evidence | identify whether a concrete engineering choice likely exists | that any technology presence automatically proves a deliberate decision |
| `VALIDATED_KNOWLEDGE` | existing trusted insights and validated events | strongest existing project knowledge in prompt context | use as established context, compare new evidence against it, avoid reproposing it as new | that trusted knowledge alone proves a new delta or new decision |
| `HISTORY` | chronological/evolution context, commits, prior analyses, evolution snapshots | deterministic chronology | reconstruct how the project evolved when evidence supports it | causality from sequence alone |
| `REPOSITORY_CHANGES` | repository-derived change evidence and provenance-oriented repository context | deterministic repository evidence | identify what changed and what implementation activity is visible | developer intent or business rationale from change existence alone |
| `HUMAN_CONTEXT` | authorized human-provided goals, constraints, assumptions, known gaps, domain context | trusted human project input, but not repository fact | incorporate stated goals/constraints into interpretation; identify tension with repository evidence | that human statements rewrite repository facts |

## 8. Canonical Content vs Membership Semantics

### Decision

The model should explicitly be told:

```text
Canonical selected content contains the actual engineering information.
Semantic Sections are indexes/perspectives over that content.
Section membership helps locate relevance; it does not create new evidence.
```

### Rationale

- enough explanation to prevent misuse
- not a full implementation manual
- directly addresses the measured utilization gap

## 9. Evidence Semantics

### Decision

Use a multidimensional evidence authority model rather than a single absolute ranking.

Proposed semantics:

- canonical facts and repository evidence: strongest for observed project reality
- validated knowledge: strongest for already-trusted project interpretation
- observations: deterministic higher-level observations grounded in facts
- human context: strongest for goals, constraints, assumptions, and explicitly human project statements
- AI inference: always weakest and must remain framed as inference/proposal
- generic model knowledge: background only, never a substitute for project evidence

### Conflict rule

When evidence types point in different directions:

- do not silently collapse the tension
- prefer repository evidence for repository reality
- prefer human context for explicitly human goals/constraints
- prefer validated knowledge as existing trusted interpretation unless current evidence materially supports enrichment or tension
- reduce confidence and avoid unsupported resolution when the conflict cannot be resolved safely

## 10. Shared Utilization Contract

### Proposed shared contract

All generic Analysis prompts should share a concise structured-context utilization layer outside the untrusted context block.

Minimal effective shared rules:

1. Use only the supplied context.
2. Treat Semantic Sections as semantic indexes over canonical selected content.
3. Do not double-count an item that appears in multiple sections.
4. Use section membership to find relevant evidence perspectives, not to force one output per section.
5. Prefer project-specific evidence over generic framework assumptions.
6. Distinguish observed facts, trusted existing knowledge, human context, and new inference.
7. Do not infer causality or developer intent unless supported by the supplied evidence.
8. When evidence is insufficient or conflicting, reduce confidence or return zero proposals rather than inventing certainty.
9. Do not restate every input item; emit only useful higher-level conclusions supported by the context.
10. Ground synthesized claims with the evidence that materially supports them, not every inspected item.

### Rejected shared rules

- explicit hidden reasoning procedures
- one output per section behavior
- section-by-section summarization instructions
- verbose explanation of internal Java architecture

## 11. Intent-Specific Synthesis Requirements

### Decision

Prompt behavior should be split into:

- `SHARED CONTEXT UTILIZATION`
- `INTENT-SPECIFIC SYNTHESIS STRATEGY`

Shared behavior should define evidence semantics and hallucination safeguards.

Intent-specific behavior should define:

- objective-specific emphasis
- what counts as a useful conclusion
- when zero output is correct
- what type of synthesis is desired

## 12. describe-project Design

### Desired cognitive task

Produce a coherent understanding of the project as it currently exists, supported by multiple relevant evidence perspectives when available.

### Proposed synthesis strategy

The prompt should instruct the model to:

1. identify the most project-defining characteristics, not every detectable characteristic
2. combine `PROJECT_STATE`, `ARCHITECTURE`, and `VALIDATED_KNOWLEDGE` first
3. use `HISTORY`, `REPOSITORY_CHANGES`, `DECISIONS`, and `HUMAN_CONTEXT` only when they materially clarify the project understanding
4. distinguish stable current state from evolution/history
5. emit only insights that improve a human engineer's understanding of the project

### Intended emphasis

- primary: `PROJECT_STATE`, `ARCHITECTURE`, `VALIDATED_KNOWLEDGE`
- supporting: `HISTORY`, `REPOSITORY_CHANGES`, `HUMAN_CONTEXT`, `DECISIONS`

### Safety

- do not produce one proposal per section
- do not use generic framework knowledge to explain project purpose
- do not convert historical changes into unsupported motivations

## 13. architecture-overview Design

### Desired cognitive task

Compare current architecture-relevant evidence against existing trusted architecture knowledge and emit only meaningful deltas.

### Proposed synthesis strategy

The prompt should instruct the model to:

1. use `VALIDATED_KNOWLEDGE` and `existingArchitectureKnowledge` as the comparison baseline
2. use `ARCHITECTURE` as the primary evidence surface
3. use `HISTORY` and `REPOSITORY_CHANGES` only to support whether a current architecture characteristic is meaningfully new or enriching
4. avoid rediscovering already-trusted architecture as `NEW`
5. return `[]` when evidence does not support a meaningful delta

### Intended emphasis

- primary: `ARCHITECTURE`, `VALIDATED_KNOWLEDGE`
- supporting: `HISTORY`, `REPOSITORY_CHANGES`, `PROJECT_STATE`

### Safety

- preserve delta-only semantics
- do not force output to prove the prompt changed

## 14. engineering-decision Design

### Desired cognitive task

Reconstruct concrete project-specific engineering choices only when the supplied evidence supports the existence of a real decision.

### Proposed synthesis strategy

The prompt should instruct the model to:

1. look first for explicit or strongly convergent evidence of a project-specific choice
2. combine `DECISIONS`, `HISTORY`, `REPOSITORY_CHANGES`, and `VALIDATED_KNOWLEDGE`
3. use `ARCHITECTURE`, `PROJECT_STATE`, and `HUMAN_CONTEXT` as supporting context
4. distinguish the observed choice from inferred rationale
5. emit zero proposals when evidence shows technology presence but not a concrete decision worth reconstructing

### Intended emphasis

- primary: `DECISIONS`, `HISTORY`, `REPOSITORY_CHANGES`, `VALIDATED_KNOWLEDGE`
- supporting: `ARCHITECTURE`, `HUMAN_CONTEXT`, `PROJECT_STATE`

### Safety

- technology usage alone is not enough
- generic framework benefits are not project evidence
- rationale may be inferred only when supported by project context and should stay conservative

## 15. Decision Reconstruction Safety

### Decision

The prompt should explicitly distinguish:

- observed choice
- known rationale
- inferred rationale

### Output-contract assessment

Current decision schema can represent:

- `context`
- `choice`
- `rationale`
- `consequences`

But it cannot explicitly tag `rationale` as inferred vs known.

Therefore:

- `ENGINEERING_DECISION_OUTPUT_CONTRACT_LIMITATION = PARTIAL`

This is not a blocker for a prompt-utilization Story, but it is a real safety limitation. The prompt should compensate by instructing conservative rationale phrasing rather than pretending rationale is historical fact.

### Proposed threshold

Emit a decision only when the supplied evidence supports a concrete engineering choice. If rationale is only partially supported, keep it conservative and evidence-tied rather than generic or motivationally certain.

## 16. Prompt-Injection / Security Boundary

### Decision

All structured-context interpretation instructions must remain outside the untrusted selected-knowledge block.

Preferred placement semantics:

- authoritative context-utilization rules: system prompt or authoritative user-prefix outside untrusted data
- `selectedKnowledge` JSON: unchanged inside the existing `BEGIN/END UNTRUSTED SELECTED KNOWLEDGE` delimiters

### Rationale

- preserves the current injection boundary
- prevents repository-controlled content from masquerading as instructions

## 17. Prompt-Size Considerations

### Measurement baseline

Existing selectedKnowledge payloads are already large; prior investigations measured roughly `95KB-99KB` total prompt payload for the canonical benchmark.

### Design rule

Add concise instruction text only. Do not duplicate canonical content in another textual section.

### Estimated impact

- shared utilization contract: about `0.7-1.2KB`
- intent-specific synthesis text: about `0.3-0.8KB` per intent
- expected total delta: `LOW`, materially smaller than the existing selectedKnowledge body

### Acceptance requirement

Future implementation should record before/after prompt size and justify the delta.

## 18. Output-Contract Compatibility

### describe-project-v1

- existing output contract sufficient: `YES`

Reason:

- current insight schema already supports grounded higher-level descriptive synthesis

### architecture-overview-v1

- existing output contract sufficient: `YES`

Reason:

- current insight schema already supports `NEW` vs `ENRICHES` delta behavior and empty result semantics

### analyze-engineering-decision-v1

- existing output contract sufficient: `YES_WITH_LIMITATION`

Reason:

- current schema supports better decision specificity than today
- but it cannot explicitly distinguish inferred rationale from known rationale

## 19. Design Options

### Option A — Shared utilization contract only

Description:

- add one shared structured-context interpretation contract
- keep intent-specific prompt strategy largely unchanged

Assessment:

- product fit: `MEDIUM`
- architectural fit: `MEDIUM`
- implementation complexity: `LOW`
- prompt-size impact: `LOW`
- testability: `MEDIUM`
- hallucination risk: `MEDIUM`
- expected benchmark impact: `MEDIUM`
- extensibility: `MEDIUM`

Risk:

- may improve evidence handling but not sufficiently improve intent-specific synthesis

### Option B — Intent-specific instructions only

Description:

- each prompt builder explains context usage independently

Assessment:

- product fit: `MEDIUM`
- architectural fit: `LOW_TO_MEDIUM`
- implementation complexity: `LOW_TO_MEDIUM`
- prompt-size impact: `MEDIUM`
- testability: `MEDIUM`
- hallucination risk: `MEDIUM_TO_HIGH`
- expected benchmark impact: `MEDIUM_TO_HIGH`
- extensibility: `LOW`

Risk:

- duplication and semantic drift across intents

### Option C — Hybrid shared contract + intent-specific synthesis

Description:

- shared contract defines evidence semantics, multi-membership rule, no-double-counting rule, and conservative synthesis rules
- each intent adds bounded objective-specific synthesis strategy and section emphasis

Assessment:

- product fit: `HIGH`
- architectural fit: `HIGH`
- implementation complexity: `MEDIUM`
- prompt-size impact: `LOW_TO_MEDIUM`
- testability: `HIGH`
- hallucination risk: `LOW_TO_MEDIUM`
- expected benchmark impact: `HIGH`
- extensibility: `HIGH`

Risk:

- requires disciplined boundaries so the shared layer stays concise

### Option D — No prompt change; resume deterministic ADR-064 composition

Description:

- keep current prompts and add more deterministic composition slices first

Assessment:

- product fit: `LOW`
- architectural fit: `MEDIUM`
- implementation complexity: `MEDIUM`
- prompt-size impact: `MEDIUM_TO_HIGH`
- testability: `MEDIUM`
- hallucination risk: `MEDIUM`
- expected benchmark impact: `LOW`
- extensibility: `MEDIUM`

Risk:

- adds more structure to a prompt path that already underuses current structure

## 20. Selected Design

### Decision

`OPTION C — Hybrid shared contract + intent-specific synthesis`

### Rationale

- best matches the measured split between a general utilization failure and intent-specific synthesis failure
- preserves one shared context architecture, consistent with ADR-064
- avoids repeating section semantics independently in every prompt
- allows `describe-project`, `architecture-overview`, and `analyze-engineering-decision` to prioritize different semantic perspectives without changing deterministic composition

## 21. Benchmark Design

Use the same canonical benchmark project and intents:

- `describe-project-v1`
- `architecture-overview-v1`
- `analyze-engineering-decision-v1`

Measure separately:

- selected context counts
- prompt size
- proposal count
- project-specific specificity
- cross-evidence synthesis
- reliance on validated knowledge
- historical reconstruction quality
- unsupported causal claims
- repetition / section checklist behavior
- grounding validity for insight outputs
- architecture false-positive deltas

### Recommended methodology

- fixed provider/model configuration for before/after comparison
- one canonical run per intent as the minimum gate
- one additional repeat run per changed intent if cost allows
- manual qualitative scoring with the existing 0-3 rubric
- treat improvement as meaningful only when it changes the qualitative category, not just wording

### Variance rule

An improvement should be considered causally meaningful when it consistently shifts the score or clearly reduces the targeted failure mode, for example:

- `describe-project`: `2/3` with less enumeration and better cross-evidence synthesis
- `engineering-decision`: `1/3 -> 2/3` with more project-specific and historically grounded decisions

## 22. Success Criteria

### describe-project

- factual correctness preserved
- fewer section-like enumerations
- better project-defining synthesis across relevant evidence perspectives
- history and validated knowledge used when materially relevant
- no unsupported causal claims
- no proposal-count inflation solely to appear richer

### engineering-decision

- decisions are concrete and project-specific
- technology presence alone is not treated as a decision
- context and rationale are more evidence-tied
- generic framework commentary decreases
- unsupported motivational claims do not increase
- zero proposals remains valid when evidence is weak

### architecture-overview

- zero-delta semantics preserved
- no reproposal of existing trusted architecture as new
- if a delta exists, it is specific and evidence-supported

## 23. Regression Criteria

Preserve:

- Story 0098 `COMMIT_DIFF <= 20%`
- Story 0103 relationship behavior
- Story 0104 Semantic Section composition
- Story 0105 result projection
- trusted-artifact behavior
- proposal lifecycle
- human validation boundary
- prompt-injection boundary

Negative regression metrics:

- unsupported causal claims
- generic framework assumptions presented as project facts
- evidence double-counting
- proposal count inflation
- repeated section summaries
- prompt-size growth without benchmark benefit
- grounding degradation for insight outputs
- architecture false-positive deltas

## 24. Risks

- shared contract could grow into a verbose policy block that reduces signal
- intent-specific emphasis could accidentally bias the model into section checklist behavior
- decision prompts may still be limited by the inability to label inferred rationale explicitly
- benchmark variance may hide small improvements; success must focus on category shifts, not prose preference

## 25. Expected Future Change Surface

Likely future touched areas:

- Python prompt builders
- possibly one shared prompt helper or shared instruction constant
- Python prompt tests
- benchmark/investigation documentation

Expected untouched areas:

- Java selection
- `SemanticSectionComposer`
- `SelectedKnowledgePromptProjectionService`
- transport schemas
- Java persistence
- Angular
- database
- output contracts, unless a separate future Story addresses decision rationale safety or decision grounding

## 26. ADR Assessment

### Decision

`ADR_REQUIRED = NO`

### Rationale

- this design refines prompt behavior within the already accepted ADR-064 architecture
- it does not create a new durable system boundary
- it does not alter deterministic composition ownership, persistence, or transport architecture

## 27. Story 0106 Recommendation

### Decision

`STORY_0106_JUSTIFIED = YES`

Recommended title:

`Intent-Aware Structured Context Utilization For Analysis Prompts`

Objective:

- improve how existing structured context is interpreted and synthesized by the AI across the canonical Analysis intents

Scope boundary:

- add shared structured-context utilization instructions
- add bounded intent-specific synthesis guidance for `describe-project-v1`, `architecture-overview-v1`, and `analyze-engineering-decision-v1`
- preserve existing output contracts, deterministic composition, selection, and result projection

Benchmark obligation:

- rerun the canonical three-intent benchmark with before/after scoring, prompt-size comparison, regression checks, and causal assessment

## 28. Open Questions

- should the shared contract live in the system message or in an authoritative user-prefix shared by builders?
- how concise can the shared rules be while still preventing multi-membership misuse?
- should `describe-project` explicitly mention “project-defining characteristics” or use a different phrase?
- is conservative rationale wording enough for decisions, or is a later output-contract change eventually necessary?
- should future repeated-run methodology be one extra run for all intents or only for changed intents with non-empty outputs?

## 29. Explicit Non-Actions

This design does not implement or authorize:

- prompt edits
- Python changes
- Java changes
- Angular changes
- schema changes
- deterministic objective emphasis changes in Java
- ADR-064 timeline work
- ADR-064 grounding-support work
- Story creation
- ADR creation

## 30. Conclusion

The measured post-0105 bottleneck is not missing context but weak use of existing context. The best next step is a bounded hybrid prompt-architecture evolution: one concise shared structured-context utilization contract plus intent-specific synthesis strategy. This preserves ADR-064, avoids reopening deterministic composition, and creates a causally measurable path to improve `describe-project` and `analyze-engineering-decision` while preserving correct zero-delta `architecture-overview` behavior.

Terminal state:

`INTENT_AWARE_CONTEXT_UTILIZATION_DESIGN_READY_FOR_HUMAN_REVIEW`
