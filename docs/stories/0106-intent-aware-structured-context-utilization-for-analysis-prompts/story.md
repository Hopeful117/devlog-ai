# Story 0106 — Intent-Aware Structured Context Utilization For Analysis Prompts

## Status

**STORY_0106_FINALIZATION_READY_FOR_HUMAN_COMMIT_REVIEW**

## Baseline

- Baseline SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Baseline branch: `main`
- Implementation branch: `story/0106-intent-aware-context-utilization`
- Governing ADR: `docs/decisions/ADR-064.md`
- Governing investigation: `docs/investigations/post-0105-prompt-utilization-and-analysis-synthesis.md`
- Governing design: `docs/investigations/intent-aware-structured-context-utilization-prompt-architecture-design.md`
- Prior merged story: Story 0105

## Objective

Improve how existing structured engineering context is interpreted and synthesized by the AI across the canonical Analysis intents, using one concise shared structured-context utilization contract plus bounded intent-specific synthesis guidance.

## Implemented Boundary

```text
SelectedKnowledge / PromptProjection / PromptRequest transport
        ↓ unchanged
Python prompt builders
        ↓
shared structured-context utilization contract
        +
intent-specific synthesis guidance
        ↓
existing output contracts
```

## Preserved Diagnosis

- `CONTEXT_QUALITY = SUFFICIENT_V1`
- `TRANSPORT = PRESERVED`
- `RESULT_PROJECTION = SUFFICIENT_FOR_CURRENT_ANALYSIS_EVALUATION`
- `SELECTION_PRIMARY_BOTTLENECK = NO`
- `SEMANTIC_SECTION_COMPOSITION = SUFFICIENT_V1`
- `SEMANTIC_SECTION_MODEL_UTILIZATION = LOW`
- `PRIMARY_GAP = PROMPT_UTILIZATION_GAP`
- `SECONDARY_GAP = PROMPT_SYNTHESIS_GAP`
- `TERTIARY_GAP = ENGINEERING_DECISION_GROUNDING_CONTRACT_GAP`
- `ADR_064_SEQUENCE = KEEP_PAUSED`

## Shared Contract Implemented

Implemented prompt-level semantics:

- project-specific conclusions must be grounded in supplied project context
- generic model knowledge must not be used as evidence about this project
- canonical selected content contains the actual engineering information
- Semantic Sections are semantic indexes / perspectives over canonical selected content
- section membership helps locate relevant evidence and does not create new evidence
- multi-membership must not be double-counted
- sections are not output categories and must not force one proposal per section
- prompts distinguish observable project reality, trusted validated knowledge, human context, and new AI inference
- prompts prohibit unsupported causality, historical motivation, and developer-intent claims
- prompts require conservative behavior when evidence is weak or conflicting
- prompts discourage restating every input item

## Intent-Specific Guidance Implemented

### describe-project-v1

- primary perspectives: `PROJECT_STATE`, `ARCHITECTURE`, `VALIDATED_KNOWLEDGE`
- supporting perspectives: `HISTORY`, `REPOSITORY_CHANGES`, `HUMAN_CONTEXT`, `DECISIONS`
- guidance now requests project-defining synthesis, distinction between current state and historical evolution, and less category-style enumeration

### architecture-overview-v1

- primary perspectives: `ARCHITECTURE`, `VALIDATED_KNOWLEDGE`
- supporting perspectives: `HISTORY`, `REPOSITORY_CHANGES`, `PROJECT_STATE`
- delta-only semantics preserved, including legitimate empty output

### analyze-engineering-decision-v1

- primary perspectives: `DECISIONS`, `HISTORY`, `REPOSITORY_CHANGES`, `VALIDATED_KNOWLEDGE`
- supporting perspectives: `ARCHITECTURE`, `HUMAN_CONTEXT`, `PROJECT_STATE`
- guidance now requests explicit or strongly convergent project-specific decision evidence, conservative rationale phrasing, and zero output when evidence supports only technology usage

## Scope Confirmed

### Production

- `ai-engine/app/prompts/insight.py`
- `ai-engine/app/prompts/decision.py`
- `ai-engine/app/prompts/structured_context.py`

### Tests

- `ai-engine/tests/test_prompt_builder.py`
- `ai-engine/tests/test_decision_generation_service.py`

### Explicit Non-Scope Preserved

- no Java changes
- no output schema changes
- no decision grounding changes
- no frontend changes
- no database changes
- no ADR changes
- no selection/composition/transport changes

## Verification Summary

### First Implementation

- RED targeted prompt tests captured: `6` failures
- GREEN targeted prompt tests: `26` passed
- Full AI-engine suite: `89` passed
- Prompt-size delta measured on canonical benchmark payloads:
  - `describe-project-v1`: `121004 -> 122717` bytes (`+1713`, `+1.42%`)
  - `architecture-overview-v1`: `117619 -> 119288` bytes (`+1669`, `+1.42%`)
  - `analyze-engineering-decision-v1`: `111661 -> 113322` bytes (`+1661`, `+1.49%`)
- Canonical AFTER benchmark executed for:
  - `describe-project-v1`
  - `architecture-overview-v1`
  - `analyze-engineering-decision-v1`
- Repeat runs executed for:
  - `describe-project-v1`
  - `analyze-engineering-decision-v1`

### Corrective Implementation

- 8 new corrective RED tests added
- GREEN targeted prompt tests: `34` passed
- Full AI-engine suite: `97` passed
- Corrective prompt size: `116093` bytes (`+2771`, `+2.45%`)
- AI Engine rebuilt and recreated
- Corrective prompt verified in running container (all 7 rules confirmed)
- 3 fresh corrective runtime benchmarks executed

### Corrective Runtime Results

- Pre-corrective baseline: 76.7% technology-only emission rate
- Corrective runtime: 25% technology-only emission rate
- Runs 2 and 3: correctly suppressed technology-presence decisions (ADR only)
- Run 1: still emitted technology-presence decisions; the historical cause cannot be attributed solely to model behavior
- Strict success rule: NOT_DEMONSTRATED (Run 1 regression)

### Final Validation

- Corrective prompt verified in the running local/containerized AI Engine
- Exact corrective PromptRequest replayed five times
- Frozen replay result: `5/5` clean ADR-only results
- Technology-only failures: `0`
- Generic-rationale failures: `0`
- Unsupported-causality failures: `0`
- This demonstrates stable, valid engineering-decision generation across five replays of the same exact PromptRequest; it does not prove global model determinism
- A separate Analysis-local Fact UUID ranking dependency was identified upstream and isolated into Story 0107

## Benchmark Outcome

### First Implementation

- `describe-project-v1`: still largely enumerative; no clear qualitative category shift beyond stronger explicit grounding language
- `architecture-overview-v1`: zero-delta behavior preserved
- `analyze-engineering-decision-v1`: first run improved sharply to a single ADR-backed decision, but repeat run regressed to four generic technology decisions; quality movement is therefore not yet consistent across runs

### Corrective Runtime (After AI-Engine Rebuild)

- Pre-corrective baseline: 4/5/4 proposals (76.7% technology-only)
- Corrective runtime: 4/1/1 proposals (25% technology-only)
- Runs 2 and 3: only ADR proposal emitted (corrective effective)
- Run 1: still emitted technology-presence decisions (corrective not effective in this run)
- This intermediate `4/1/1` result required further investigation and must not be treated as the final Story verdict

### Final Scope Conclusion

- `STORY_0106_PROMPT_UTILIZATION_TARGET = DEMONSTRATED`
- `STORY_0106_PRODUCT_TARGET_FOR_SCOPE = DEMONSTRATED`
- `ENTIRE_ANALYSIS_PRODUCT_QUALITY = SUBJECT_TO_INDEPENDENT_UPSTREAM_AND_DOWNSTREAM_IMPROVEMENTS`
- Historical runtime variance cannot be attributed solely to prompt behavior or pure model stochasticity
- Story 0107 independently addresses deterministic cross-Analysis Fact ranking; no Story 0107 production or test code is included here

## Acceptance Criteria

### AC-01 — Shared structured-context contract

One shared contract explains canonical selected content, Semantic Sections, evidence authority, no-double-counting behavior, conservative causality, and project-specific grounding.

### AC-02 — Intent-specific synthesis

`describe-project-v1`, `architecture-overview-v1`, and `analyze-engineering-decision-v1` receive bounded intent-specific synthesis guidance without introducing another prompt framework.

### AC-03 — Corrective decision eligibility

Engineering-decision prompts preserve the seven approved semantics: technology presence and implementation state are insufficient; positive emission requires explicit or strongly convergent project evidence; convergence requires independent signals; generic model knowledge is not project rationale; fewer well-supported decisions are preferred; unsupported project causality is prohibited.

### AC-04 — Architecture delta safety

`architecture-overview-v1` preserves trusted-knowledge comparison, delta-only behavior, and legitimate empty output.

### AC-05 — Security boundary

Repository-derived selected knowledge and User Guidance remain delimited untrusted data and cannot become authoritative instructions.

### AC-06 — Contract and architecture stability

Output schemas, grounding contracts, Java DTOs, persistence, REST, MCP, provider/model configuration, and canonical Analysis invocation remain unchanged.

### AC-07 — Verification and product evidence

Focused and full AI Engine tests pass; the running corrective prompt is verified; five exact-input replays produce valid clean results without overclaiming global determinism or strict historical causality.

### AC-08 — Scope isolation

Story 0106 remains prompt-utilization work. Deterministic Fact ranking is isolated to Story 0107, model-facing identity normalization and deterministic proposal validation are deferred, and ADR-064 remains paused.

## Lifecycle State

- Story materialization: completed
- Human design approval: completed
- First implementation: completed
- First verification: completed
- First HUMAN implementation review: CHANGES_REQUIRED
- Variance investigation: APPROVED
- Corrective implementation: completed
- Corrective verification: completed (34/34 targeted, 97/97 full)
- Stale runtime benchmark: INVALID_FOR_CORRECTIVE_VALIDATION
- AI Engine rebuild: completed
- Corrective runtime validation: completed
- HUMAN implementation approval for intended scope: completed
- Final HUMAN pre-commit review: pending
- Commit: not authorized
- Push: not authorized
- Merge: human-only

Terminal state:

`STORY_0106_FINALIZATION_READY_FOR_HUMAN_COMMIT_REVIEW`
