# Post-Story-0104 Investigation — Structured Context to Useful Analysis Output

**Date:** 2026-08-30
**Status:** INVESTIGATION_COMPLETE
**Scope:** READ-ONLY — no code changes, no commits, no design modifications

---

## Executive Summary

Story 0104 successfully implemented deterministic Semantic Sections that propagate to the AI Engine. However, human product review found that richer context still produces shallow, incomplete, or poorly exposed Analysis results.

This investigation traces the complete transformation from SelectedKnowledge to human-facing result to identify the actual bottleneck.

**Key finding:** The problem is NOT a single bottleneck. It is a **cascade of independent gaps** at different pipeline boundaries, each specific to each intent. The most impactful gap is that the **Result Projection** only exposes `title` and `summary` from proposals, hiding `rationale`, `insightType`, `context`, `choice`, `consequences`, and grounding information that exists in the persisted payload.

**Architecture-overview anomaly explained:** The model correctly returned 0 proposals because the prompt instructs it to compare against 5 existing architecture knowledge items and return empty when nothing new is learned. This is **correct behavior**, not a defect.

---

## 1. Current Product Benchmark

| Intent | Score | Proposals | Key Issue |
|---|---|---|---|
| describe-project-v1 | 2/3 | 7 | Partially grounded, factual enumeration, no synthesis |
| architecture-overview-v1 | 0/3 | 0 | Correct behavior — model returns empty when nothing new |
| analyze-engineering-decision-v1 | 1/3 | 4 | Empty summaries, empty grounding, titles only |

---

## 2. End-to-End Analysis Pipeline

```
SelectedKnowledge (Java)
    ↓
PromptProjection (Java) — adds semanticSections
    ↓
Java serialization → JSON
    ↓
AI task request → Python ai-engine
    ↓
Prompt construction (system + user message)
    ↓
OpenAI responses.parse() with Pydantic schema
    ↓
Parsed Pydantic model → AiProposalResult
    ↓
Java callback → AiTaskResultServiceImpl
    ↓
ValidatableProposal (persisted as JSONB)
    ↓
AnalysisResultQueryServiceImpl → ProposalSummary
    ↓
Human-facing result (GET /api/v1/analyses/{id}/result)
```

---

## 3. describe-project-v1 Trace

### Input Quality
The prompt payload contained:
- 40 selected facts (build system, Docker, ADRs, tests, documentation, etc.)
- 1 observation
- 10 validated insights
- 3 human context inputs
- 60 repository evidence items
- 7 semantic sections (189 items)
- Project profile, analysis metadata

**Input quality: SUFFICIENT** — enough material for a rich project description.

### Prompt Utilization
The prompt says: "Describe the project using only objectively supported characteristics."

The prompt does NOT:
- Explain what semantic sections mean
- Instruct cross-section synthesis
- Ask for architecture explanation
- Ask for evolution/history description
- Ask for decision context
- Distinguish observations from conclusions

**Prompt gap: GENERIC_INSTRUCTIONS** — the prompt is a single generic instruction, not intent-specific synthesis guidance.

### Raw AI Output
The model produced 7 proposals with:
- `title`: Populated (descriptive)
- `summary`: Populated (160-224 chars, factual)
- `rationale`: **NOT in result** (stored in payload but not surfaced)
- `insightType`: **NOT in result** (stored in payload but not surfaced)
- `evidencePreview`: Truncated to "Fact#XXXXXXXX" labels
- `supportingFactIds`: **NOT in result** (stored but not surfaced)
- `supportingObservationIds`: **NOT in result** (stored but not surfaced)

### Result Projection
`ProposalSummary` extracts only: `title`, `summary`, `evidencePreview`, `confidence`, `type`, `status`.

**Fields stored but NOT surfaced:** `rationale`, `insightType`, `deltaType`, `supportingFactIds`, `supportingObservationIds`, `evidenceReferences`.

### First Material Loss Boundary
**RESULT_PROJECTION_GAP** — the result projection only extracts `title` and `summary`, hiding `rationale` and grounding information that exists in the persisted payload.

---

## 4. architecture-overview-v1 Trace (Highest Priority)

### Input Quality
The prompt payload contained:
- 40 selected facts
- 4 observations
- 10 validated insights
- 5 existing architecture knowledge items (TECHNOLOGY and ARCHITECTURAL types)
- 60 repository evidence items
- 7 semantic sections (202 items)

**Input quality: SUFFICIENT** — enough material for architecture analysis.

### Prompt Utilization
The prompt says: "Describe demonstrable architectural characteristics without quality judgements."

**Critical instruction:** "If nothing materially new is learned, return an empty proposals array."

The prompt also provides 5 existing architecture knowledge items and instructs: "Compare the new evidence against it. Return only meaningful architecture deltas."

### Raw AI Output
The model compared the selected knowledge against the 5 existing architecture knowledge items:
- "Automated and Integration Testing Present" (TECHNOLOGY)
- "Use of Architecture Decision Records (ADR) for Documentation" (TECHNOLOGY)
- "Project Containerization with Docker and Docker Compose" (ARCHITECTURAL)
- "Spring Boot REST API Application" (ARCHITECTURAL)
- "Multi-module Build System Using Maven" (ARCHITECTURAL)

The model decided: **nothing materially new** compared to existing knowledge → returned 0 proposals.

### Why This Is Correct Behavior
The prompt explicitly says: "If nothing materially new is learned, return an empty proposals array."

The model followed this instruction correctly. The existing architecture knowledge already covers the key architectural characteristics. The selected knowledge (facts, observations, insights) does not contain information that would constitute a **meaningful delta** beyond what's already trusted.

**This is NOT a defect.** It is the intended behavior of the architecture-overview intent when the project's architecture is already well-documented.

### Why the Earlier Investigation Claimed "2 Insights"
The earlier investigation incorrectly reported "2 insights reportedly produced." The actual result shows:
- `proposals: {total: 0, items: []}`
- `insights: {total: 0, items: []}`
- `deliverables: {total: 0, items: []}`

The "2 insights" claim was a misreading of the data.

### First Material Loss Boundary
**NOT_APPLICABLE** — there is no loss boundary because the model correctly returned 0 proposals. The issue is that the architecture-overview intent's delta-against-existing-knowledge design means it will produce 0 proposals for any project whose architecture is already documented.

---

## 5. analyze-engineering-decision-v1 Trace

### Input Quality
The prompt payload contained:
- 40 selected facts
- 1 observation
- 10 validated insights
- 60 repository evidence items
- 7 semantic sections (186 items)

**Input quality: SUFFICIENT** — enough material for engineering decision analysis.

### Prompt Utilization
The prompt says: "Identify only engineering decisions that are supported by the supplied evidence. Distinguish context from choice and rationale."

### Raw AI Output
The model produced 4 proposals with:
- `title`: Populated (descriptive)
- `context`: **NOT in schema** (schema has `extra="forbid"`)
- `choice`: **NOT in schema** (schema has `extra="forbid"`)
- `rationale`: **NOT in schema** (schema has `extra="forbid"`)
- `consequences`: **NOT in schema** (schema has `extra="forbid"`)

Wait — let me re-check. The schema DOES have these fields:

```python
class EngineeringDecisionProposalOutput(DecisionOutputModel):
    title: str
    context: str
    choice: str
    rationale: str
    consequences: str | None
```

But the result shows `summary: ""` and `evidencePreview: []`. The `context`, `choice`, `rationale`, `consequences` fields are NOT in the result projection.

### Where the Information Disappears

1. **AI Output Contract:** The decision schema requires `title`, `context`, `choice`, `rationale`, `consequences` (optional). The model should produce these.

2. **Python → Java Mapping:** The `_payload()` method in `decision_generation_service.py` maps:
   ```python
   payload = {
       "title": proposal.title,
       "context": proposal.context,
       "choice": proposal.choice,
       "rationale": proposal.rationale,
   }
   if proposal.consequences is not None:
       payload["consequences"] = proposal.consequences
   ```
   All fields are preserved in the payload.

3. **Java Persistence:** The entire `payload` Map is stored as JSONB on `ValidatableProposal`. Nothing is dropped.

4. **Result Projection:** `ProposalSummary` extracts only `title` and `summary` from the payload. For ENGINEERING_DECISION, the payload has `context`, `choice`, `rationale`, `consequences` — but `summary` is NOT one of these fields. So `summary` is empty.

**The critical issue:** The result projection calls `extractSummary(proposal.getPayload())` which looks for `payload["summary"]`. But the ENGINEERING_DECISION payload doesn't have a `summary` field — it has `context`, `choice`, `rationale`, `consequences`. So `summary` is always empty.

### First Material Loss Boundary
**RESULT_PROJECTION_GAP** — the result projection assumes all proposals have a `summary` field, but ENGINEERING_DECISION proposals have `context`, `choice`, `rationale`, `consequences` instead. The result projection doesn't know how to extract decision-specific fields.

---

## 6. Grounding Representation Audit

### Current State

**For INSIGHT proposals:**
- `supportingFactIds`: Stored on `ValidatableProposal`, NOT surfaced in `ProposalSummary`
- `supportingObservationIds`: Stored on `ValidatableProposal`, NOT surfaced in `ProposalSummary`
- `evidenceReferences`: Stored on `ValidatableProposal`, NOT surfaced in `ProposalSummary`
- `evidencePreview`: Truncated to 3 fact labels + 2 observation labels (string format)

**For ENGINEERING_DECISION proposals:**
- `supportingFactIds`: Always empty (hardcoded in Python)
- `supportingObservationIds`: Always empty (hardcoded in Python)
- `evidenceReferences`: Always empty (hardcoded in Python)
- `evidencePreview`: Always empty

**For ENGINEERING_EVENT proposals:**
- `supportingFactIds`: Populated (validated against allowed set)
- `supportingObservationIds`: Populated (validated against allowed set)
- `evidenceReferences`: Populated (validated against allowed set)
- `evidencePreview`: Truncated to 3+2 labels

### Grounding Model Classification
**MULTIPLE_INCOMPATIBLE_REPRESENTATIONS:**
1. INSIGHT: Has grounding fields in schema, stored, but not surfaced in result
2. ENGINEERING_DECISION: No grounding fields in schema (by design per ADR-064 "grounding defect")
3. ENGINEERING_EVENT: Has grounding fields, stored, truncated in result

---

## 7. Proposal vs Insight Semantics

### Current Semantic Distinction

| Concept | What It Is | When Created | When Promoted |
|---|---|---|---|
| AI-generated proposal | Unvalidated suggestion from AI | During analysis execution | Never (stays PROPOSED until human validates) |
| ValidatableProposal | Persisted proposal entity | After AI callback | When human accepts |
| Insight | Trusted knowledge entity | When human validates a proposal | Promotion creates Insight |
| AnalysisResult primary item | Human-facing display | During result projection | Shows PROPOSED proposals + ACTIVE insights |

### Architecture-Overview Semantic Mismatch
The architecture-overview intent produces INSIGHT proposals (type=INSIGHT). These are meant to become trusted architecture knowledge when validated.

But the prompt's delta-against-existing-knowledge design means:
- If nothing new → 0 proposals → nothing to validate → no new insights
- This is correct behavior — you shouldn't create new insights when nothing new is learned

**The issue is not semantic — it's that the architecture-overview intent is designed to produce deltas, not comprehensive descriptions.**

---

## 8. Intent Contract Audit

| Intent | Expected Output | Actual Output | Mismatch |
|---|---|---|---|
| describe-project | INSIGHT proposals with insightType, title, summary, rationale, grounding | 7 proposals with title + summary only (rationale/grounding not surfaced) | RESULT_PROJECTION_GAP |
| architecture-overview | INSIGHT proposals (deltas against existing knowledge) | 0 proposals (correct per prompt design) | NO_MISMATCH — correct behavior |
| analyze-engineering-decision | ENGINEERING_DECISION proposals with title, context, choice, rationale, consequences | 4 proposals with title only (context/choice/rationale/consequences not surfaced) | RESULT_PROJECTION_GAP |

### Key Finding
The intent contracts are well-defined. The mismatch is NOT in the contracts themselves but in:
1. **Result projection** — doesn't expose all stored fields
2. **Decision grounding** — intentionally excluded per ADR-064 (acknowledged defect)

---

## 9. Prompt Architecture

### Shared Behavior
All intents share:
- Same system message template (role definition, priority rules, grounding instructions)
- Same grounding contract construction (allowed IDs)
- Same corrective retry mechanism
- Same structured output via OpenAI responses.parse()

### Intent-Specific Behavior
| Intent | Specific Instructions |
|---|---|
| describe-project | "Describe the project using only objectively supported characteristics" |
| architecture-overview | "Describe demonstrable architectural characteristics without quality judgements" + existing knowledge comparison + delta logic |
| analyze-engineering-decision | "Identify only engineering decisions supported by evidence. Distinguish context from choice and rationale." |

### Semantic Sections in Prompt
Semantic sections are embedded as opaque JSON in the `selectedKnowledge` blob. The prompt does NOT:
- Explain what sections mean
- Instruct cross-section synthesis
- Reference sections by name
- Use sections for reasoning

**The model sees sections as just another JSON key, not as a reasoning structure.**

---

## 10. Semantic Sections Utilization

| Intent | Sections Present | Prompt Explains Meaning | Prompt Instructs Synthesis | Output Demonstrates Cross-Section Reasoning |
|---|---|---|---|---|
| describe-project | YES | NO | NO | NO — output is factual enumeration from individual facts |
| architecture-overview | YES | NO | NO | N/A — 0 proposals |
| engineering-decision | YES | NO | NO | NO — output is generic titles |

**Semantic sections are present but not utilized by the model for reasoning.** The model treats them as opaque data, not as a reasoning structure.

---

## 11. Output Contract Constraints

| Field | Insight Schema | Decision Schema | Event Schema |
|---|---|---|---|
| title | REQUIRED (1-255) | REQUIRED (1-255) | REQUIRED (1-255) |
| summary | REQUIRED (1-5000) | NOT_PRESENT | REQUIRED (1-5000) |
| rationale | REQUIRED (1-5000) | REQUIRED (1-5000) | NOT_PRESENT |
| context | NOT_PRESENT | REQUIRED (1-5000) | NOT_PRESENT |
| choice | NOT_PRESENT | REQUIRED (1-5000) | NOT_PRESENT |
| consequences | NOT_PRESENT | OPTIONAL (1-5000) | NOT_PRESENT |
| significance | NOT_PRESENT | NOT_PRESENT | REQUIRED (1-5000) |
| insightType | REQUIRED (enum) | NOT_PRESENT | NOT_PRESENT |
| confidence | REQUIRED (0-1) | NOT_PRESENT | REQUIRED (0-1) |
| grounding | REQUIRED (3 fields) | NOT_PRESENT | REQUIRED (3 fields) |

**Schema capability:** All required fields for deeper analysis are SUPPORTED in the schemas. The issue is not schema limitation — it's that:
1. Decision schema intentionally excludes grounding (ADR-064 defect)
2. Result projection doesn't expose all stored fields

---

## 12. Raw Output → Human Result Information Loss

### describe-project-v1

| Information | Prompt | Raw AI | Persisted | Result | Lost At |
|---|---|---|---|---|---|
| Title | YES | YES | YES | YES | — |
| Summary | YES | YES | YES | YES | — |
| Rationale | YES | YES | YES (JSONB) | NO | Result Projection |
| InsightType | YES | YES | YES (JSONB) | NO | Result Projection |
| DeltaType | YES | YES | YES (JSONB) | NO | Result Projection |
| SupportingFactIds | YES | YES | YES | NO (truncated) | Result Projection |
| SupportingObsIds | YES | YES | YES | NO (truncated) | Result Projection |
| EvidenceReferences | YES | YES | YES | NO (truncated) | Result Projection |

### architecture-overview-v1

| Information | Prompt | Raw AI | Persisted | Result | Lost At |
|---|---|---|---|---|---|
| Proposals | YES | 0 | 0 | 0 | — (correct) |

### analyze-engineering-decision-v1

| Information | Prompt | Raw AI | Persisted | Result | Lost At |
|---|---|---|---|---|---|
| Title | YES | YES | YES | YES | — |
| Context | YES | NOT_OBSERVABLE | YES (JSONB) | NO | Result Projection |
| Choice | YES | NOT_OBSERVABLE | YES (JSONB) | NO | Result Projection |
| Rationale | YES | NOT_OBSERVABLE | YES (JSONB) | NO | Result Projection |
| Consequences | YES | NOT_OBSERVABLE | YES (JSONB) | NO | Result Projection |
| Summary | N/A | N/A | N/A | "" | Schema mismatch |
| Grounding | N/A | N/A | N/A | [] | Schema excludes |

---

## 13. First Material Loss Boundaries

### describe-project-v1
**FIRST_MATERIAL_LOSS_BOUNDARY = RESULT_PROJECTION**
The result projection only extracts `title` and `summary`, hiding `rationale`, `insightType`, and grounding information.

### architecture-overview-v1
**FIRST_MATERIAL_LOSS_BOUNDARY = NOT_APPLICABLE**
The model correctly returned 0 proposals per prompt design. No loss boundary exists.

### analyze-engineering-decision-v1
**FIRST_MATERIAL_LOSS_BOUNDARY = RESULT_PROJECTION**
The result projection assumes `summary` exists, but ENGINEERING_DECISION has `context`, `choice`, `rationale`, `consequences` instead. Additionally, the decision schema intentionally excludes grounding.

---

## 14. Bottleneck Classification

### Gap 1: RESULT_PROJECTION_GAP
- **Evidence:** ProposalSummary extracts only `title` and `summary` from payload. Rationale, insightType, context, choice, consequences, grounding are stored but not surfaced.
- **Affected intents:** describe-project, analyze-engineering-decision
- **Severity:** HIGH — prevents HUMAN from evaluating proposal quality
- **First material loss boundary:** Result Projection
- **Story 0104 caused:** NO — pre-existing
- **Blocks deeper Analysis:** YES — HUMAN cannot see the depth that exists in the data

### Gap 2: PROMPT_UTILIZATION_GAP
- **Evidence:** Prompt does not explain semantic section meaning or instruct cross-section synthesis. Model treats sections as opaque JSON.
- **Affected intents:** all
- **Severity:** MEDIUM — model may produce richer output with better instructions
- **First material loss boundary:** Prompt Construction
- **Story 0104 caused:** NO — semantic sections were always opaque
- **Blocks deeper Analysis:** PARTIALLY — model may not synthesize across evidence categories

### Gap 3: AI_OUTPUT_CONTRACT_GAP (Decision Grounding)
- **Evidence:** Engineering Decision schema has `extra="forbid"` and excludes grounding fields. Python service hardcodes `confidence=1.0` and empty grounding arrays.
- **Affected intents:** analyze-engineering-decision
- **Severity:** MEDIUM — acknowledged ADR-064 defect
- **First material loss boundary:** AI Output Contract
- **Story 0104 caused:** NO — pre-existing ADR-064 defect
- **Blocks deeper Analysis:** PARTIALLY — decisions lack evidence traceability

### Gap 4: INTENT_CONTRACT_GAP (Architecture Delta Design)
- **Evidence:** Architecture-overview prompt instructs delta-against-existing-knowledge. With 5 existing items, model returns 0 proposals for well-documented projects.
- **Affected intents:** architecture-overview
- **Severity:** LOW — correct behavior per design, but may need rethinking for comprehensive descriptions
- **First material loss boundary:** Intent Design
- **Story 0104 caused:** NO — pre-existing design
- **Blocks deeper Analysis:** YES for comprehensive architecture descriptions, NO for delta tracking

---

## 15. ADR-064 Sequence Recommendation

**Classification: PAUSE_ADR_064_SEQUENCE**

Reasoning:
1. ADR-064's next planned slices (Timeline Highlights, Grounding Support) address context composition
2. The investigation found that the primary bottleneck is NOT context composition — it's result projection and prompt utilization
3. Adding Timeline Highlights won't help if the model doesn't use semantic sections for synthesis
4. Adding Grounding Support won't help if grounding information is stored but not surfaced
5. The more impactful next steps are:
   - Fix result projection to expose all stored proposal fields
   - Improve prompt instructions to leverage semantic sections
   - Address the architecture-overview intent design (delta vs comprehensive)

---

## 16. Recommended Next Design Step

The investigation reveals that the next bottleneck is **NOT** in context composition (ADR-064 domain) but in:

1. **Result Projection** — expose `rationale`, `insightType`, `context`, `choice`, `consequences`, and grounding in the human-facing result
2. **Prompt Architecture** — instruct the model to use semantic sections for cross-section synthesis
3. **Intent Design** — reconsider architecture-overview's delta-only approach for comprehensive descriptions

These are separate concerns from ADR-064's composition architecture.

---

## Appendix A — Key Code References

| File | Role |
|---|---|
| `ai-engine/app/prompts/insight.py` | Insight prompt construction, grounding contract |
| `ai-engine/app/prompts/decision.py` | Decision prompt construction |
| `ai-engine/app/schemas/insight.py` | Insight output DTO |
| `ai-engine/app/schemas/decision.py` | Decision output DTO (no grounding) |
| `ai-engine/app/services/decision_generation_service.py` | Decision mapping (hardcoded confidence=1.0) |
| `backend/.../AnalysisResultQueryServiceImpl.java` | Result projection (title+summary only) |
| `backend/.../AiTaskResultServiceImpl.java` | AI response persistence |
| `backend/.../SelectedKnowledgePromptProjectionService.java` | Semantic sections composition |
