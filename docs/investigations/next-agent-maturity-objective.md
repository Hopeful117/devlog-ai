# Investigation: Next Agent Maturity Objective

Investigation-only artifact. No production code, test, benchmark harness, ADR, or Engineering Story
was created or modified.

## Status

**INVESTIGATION_COMPLETE — AWAITING_HUMAN_REVIEW**

## Scope

This investigation determines the next highest-value engineering objective for DevLog AI after
Story 0114, grounded in repository evidence, accepted architecture, and the product milestone:

> DevLog becomes useful, trustworthy, and agent-like enough that giving it a physical presence
> through the future Agent Presence Device V0 is justified by real utility rather than by a
> hardware demonstration.

## Executive Conclusion

**DevLog's primary maturity gap is that the Story Context Analysis agent receives no validated
knowledge.** The knowledge pipeline is architecturally complete — Facts, Observations, Insights,
Decisions, and Engineering Events are all persisted as trusted or deterministic knowledge — but
the `AnalyzeStoryContextUseCase.buildSelectedKnowledge()` method hardcodes `selectedFacts`,
`selectedObservations`, and `selectedInsights` as empty lists. The AI agent therefore analyzes
each story with repository evidence only, disconnected from the accumulated project memory that
is DevLog's core value proposition.

This gap directly causes the product quality gate failures observed in Stories 0108/0109, where
the AI cannot synthesize relationships that were never deterministically collected or provided.
It is the single most impactful prerequisite for meaningful proactive communication: without
validated knowledge informing analysis, there is nothing important enough to communicate.

---

## 1. CURRENT_AGENT_MATURITY

DevLog has achieved substantial functional completeness across its knowledge pipeline:

- **Repository sync**: Automated Git → DB ingestion with scheduled detection, crash recovery,
  and freshness tracking (Stories 0021, 0085, 0091-0093).
- **Deterministic extraction**: ~60+ Fact types, Observation derivation, Repository Context
  Engine with multi-layer evidence collection (Stories 0004-0006, 0012-0016, 0081, 0097).
- **Knowledge selection**: Intent-aware ranking, budget constraints, grounding closure
  enforcement, semantic section composition (ADR-033, `KnowledgeSelectionServiceImpl`).
- **AI task lifecycle**: Asynchronous create → submit → callback → persist with idempotent
  handling (ADR-017, ADR-019, ADR-020).
- **Proposal governance**: PROPOSED → ACCEPTED/REJECTED with human validation gate
  (ADR-006).
- **Knowledge promotion**: Atomic promotion to immutable Insight, Decision, EngineeringEvent
  with similarity assessment (ADR-048, ADR-049).
- **Story Context Analysis agent**: Structured output with grounding contract, epistemic
  classification, relationship semantics, and deterministic freshness snapshot (ADR-067,
  Story 0114).
- **MCP surface**: 3 tools, 9 resources, discovery endpoint (Stories 0087-0090, 0113).
- **Evaluation harness**: Replayable AI Intent testing (ADR-066, Story 0110).

### What the agent does well

- Strong grounding enforcement: every finding must reference valid evidence from the
  EngineeringContext.
- Clear epistemic classification: FACTUAL_EXTRACTION, AI_INTERPRETATION, RECOMMENDATION
  prevents the LLM from presenting inference as fact.
- Relationship semantics: EXPLICIT, TEMPORAL_PROXIMITY, POSSIBLE_RELEVANCE,
  INFERRED_HYPOTHESIS provide principled vocabulary for expressing evidence relationships.
- Deterministic provenance: contextDigest, promptVersion, provider, model are recorded.
- Freshness awareness: Story 0114 ensures the agent's output carries a deterministic
  freshness snapshot.

### What the agent cannot do

1. **Cannot access validated knowledge** — Facts, Observations, Insights are hardcoded as
   empty lists (AnalyzeStoryContextUseCase.java:123-126).
2. **Cannot iterate or refine** — Single prompt-generation round trip with one corrective
   retry; no follow-up questions or deeper analysis.
3. **Cannot compare against prior analyses** — Each analysis is independent; no diffing
   against previous StoryContextAnalysis for the same story.
4. **Cannot assess significance or importance** — No scoring model for what matters;
   InsightSeverity is human-supplied text only.
5. **Cannot trigger re-analysis automatically** — Analyses are only triggered by explicit
   human action.
6. **Cannot communicate proactively** — No event architecture, no notifications, no
   webhooks, no SSE/WebSocket.
7. **Cannot reason across stories** — Single-story isolation; no cross-story dependency
   mapping.
8. **Cannot produce risk assessment** — No structured output for risk evaluation despite
   having evidence that could support it.

---

## 2. PRIMARY_MATURITY_GAP

**The knowledge pipeline is architecturally complete but functionally disconnected from the
Story Context Analysis agent.**

### Evidence

The critical code in `AnalyzeStoryContextUseCase.buildSelectedKnowledge()` (lines 119-140):

```java
Map<String, Object> result = new LinkedHashMap<>();
result.put("project", context.project());
result.put("analysis", Map.of());
result.put("projectProfile", context.project());
result.put("selectedFacts", List.of());        // ← EMPTY
result.put("selectedObservations", List.of());  // ← EMPTY
result.put("diagnostics", List.of());
result.put("selectedInsights", List.of());      // ← EMPTY
result.put("selectionMetadata", List.of());
result.put("selectionDigest", context.metadata().contextDigest());
result.put("repositoryContext", Map.of("evidence", context.evidence()));
```

The Python prompt builder (`story_context_analysis.py:77-78`) explicitly expects these fields:

```python
required_sections = [
    "project", "analysis", "projectProfile", "selectedFacts",
    "selectedObservations", "diagnostics", "selectedInsights",
    ...
]
```

And other intent prompt builders (insight.py:340-341) actively use them for grounding:

```python
facts = selected_knowledge.get("selectedFacts", [])
observations = selected_knowledge.get("selectedObservations", [])
```

The `KnowledgeSelectionServiceImpl` already implements intent-aware ranking, budget
constraints, and grounding closure. The infrastructure to select and provide validated
knowledge exists. It is simply not wired into the Story Context Analysis use case.

### Consequence

The agent produces analysis that is:
- **Disjoint from project memory** — Cannot reference that "this decision was made in Story
  0025" or "this pattern was established in Insight X."
- **Unable to synthesize relationships** — The product quality gate failures in Stories
  0108/0109 stem partly from the AI's inability to connect dots that exist in validated
  knowledge but were never provided.
- **Lower quality than architecturally possible** — The sophisticated knowledge selection
  engine runs but its output is discarded for this intent.

---

## 3. CANDIDATES_EVALUATED

### Candidate 1: Wire validated knowledge into Story Context Analysis

**Why it exists**: The knowledge pipeline is complete but disconnected from the agent.

**Repository evidence**: AnalyzeStoryContextUseCase.java:123-126 hardcodes empty lists;
KnowledgeSelectionServiceImpl provides the selection infrastructure; Stories 0108/0109 show
product quality gate failures that correlate with missing knowledge context.

**What current limitation it fixes**: The agent cannot reference validated Facts, Observations,
or Insights in its analysis. It cannot demonstrate the value of accumulated project memory.

**How it improves DevLog Agent maturity**: Directly addresses the core value proposition —
DevLog as "shared/project memory." Without this, the agent is a generic code analyzer, not
a knowledge-informed engineering assistant.

**Whether it is prerequisite for proactive communication**: YES. Proactive communication
requires something important to communicate. Analysis informed by validated knowledge is
more likely to contain important findings than analysis with repository evidence only.

**Estimated architectural impact**: Low-to-medium. The selection infrastructure exists; the
change is primarily wiring `KnowledgeSelectionService` output into the use case and
ensuring the prompt builder handles non-empty knowledge sections.

**Risk of premature complexity**: Low. The selection engine is already implemented and
tested. The prompt builder already handles these fields. The main risk is ensuring the
Story Context Analysis intent's knowledge budget is appropriate.

### Candidate 2: Automated stale knowledge detection and re-analysis trigger

**Why it exists**: Knowledge degrades silently; the system detects staleness but does not
act on it.

**Repository evidence**: ADR-062 (sync lifecycle), MaintenanceFinding system, Story 0114
(freshness snapshot), ScheduledRepositoryChangeDetector (polls but does not trigger
re-analysis).

**What current limitation it fixes**: Analyses become stale without notification or automatic
refresh. The MaintenanceFinding system detects staleness but is pull-based.

**How it improves DevLog Agent maturity**: Ensures knowledge stays current, which is
prerequisite for trustworthy analysis.

**Whether it is prerequisite for proactive communication**: Partially. Stale knowledge
produces stale analysis, but the immediate blocker is that the analysis doesn't receive
validated knowledge at all.

**Estimated architectural impact**: Medium. Requires event-driven triggers or scheduled
re-analysis orchestration.

**Risk of premature complexity**: Medium. Introduces automated workflow orchestration,
which is a new architectural pattern for the system.

### Candidate 3: Analysis significance / priority model

**Why it exists**: DevLog cannot distinguish important findings from background noise.

**Repository evidence**: InsightSeverity is human-supplied text; no scoring system exists
across knowledge types; the `significance` field on EngineeringEvent is free-text only.

**What current limitation it fixes**: Without significance modeling, proactive communication
would produce noise. The system cannot determine what is "event worthy of human attention."

**How it improves DevLog Agent maturity**: Enables prioritization of what to communicate
and what to surface on a presence device.

**Whether it is prerequisite for proactive communication**: YES. This is the direct enabler
for meaningful proactive communication. However, it requires the analysis to be good first
(Candidate 1).

**Estimated architectural impact**: Medium. Requires a scoring model that spans knowledge
types and analysis outputs.

**Risk of premature complexity**: Medium. Significance is inherently subjective and may
require calibration.

### Candidate 4: Agent iterative refinement capability

**Why it exists**: Single-shot analysis cannot handle complex discussions or follow-up
questions.

**Repository evidence**: The current flow is fire-and-forget; no dialogue mechanism exists;
ADR-067 explicitly defers "persistent conversational memory."

**What current limitation it fixes**: Enables richer interaction for Discuss/Plan
preparation.

**How it improves DevLog Agent maturity**: Nice-to-have for depth but not blocking the
core value proposition.

**Whether it is prerequisite for proactive communication**: No.

**Estimated architectural impact**: High. Requires conversation state management, multi-turn
prompt construction, and session lifecycle.

**Risk of premature complexity**: High. This is a significant new capability that should
come after the core analysis quality is proven.

### Candidate 5: Event-driven architecture / proactive workflow

**Why it exists**: The system is entirely pull-based; no automated workflows exist.

**Repository evidence**: No ApplicationEvent subclasses, no outbox pattern, no message
broker (ADR-010 rejected Kafka/RabbitMQ for V1); the only automated pipeline is
repository sync.

**What current limitation it fixes**: Enables automated re-analysis, cross-system
notifications, and proactive behavior.

**How it improves DevLog Agent maturity**: Enables the system to act without human
initiation.

**Whether it is prerequisite for proactive communication**: YES, but can be deferred.
Manual workflows are sufficient for V1; automated workflows are a scaling concern.

**Estimated architectural impact**: High. Introduces a new communication pattern.

**Risk of premature complexity**: High. Event-driven architecture is a fundamental
architectural shift.

---

## 4. RANKING_RATIONALE

| Rank | Candidate | Impact | Risk | Dependency |
|------|-----------|--------|------|------------|
| 1 | Wire validated knowledge into SCA | Highest | Low | None |
| 2 | Significance / priority model | High | Medium | Requires #1 |
| 3 | Automated re-analysis trigger | Medium | Medium | Independent |
| 4 | Event-driven architecture | High | High | Independent |
| 5 | Agent iterative refinement | Medium | High | Requires #1 |

**Ranking criteria applied:**
- Impact on agent usefulness (highest weight)
- Impact on trustworthiness
- Dependency for later capabilities
- Evidence strength (code-level proof vs. hypothetical)
- Complexity and reversibility
- Architectural risk
- Ability to validate experimentally

**Why #1 ranks above #2**: Candidate 1 addresses the most fundamental gap — the agent
cannot use the knowledge that DevLog has painstakingly accumulated. Candidate 2 (significance)
requires the analysis to be good first; there is no point scoring the importance of analysis
that doesn't leverage validated knowledge.

**Why #1 ranks above #3**: Candidate 3 (automated re-analysis) ensures knowledge stays
current, but the immediate problem is that current knowledge isn't provided to the agent
at all. Fresh knowledge that isn't used is no better than stale knowledge that isn't used.

**Why #1 ranks above #4**: Candidate 4 (event-driven architecture) is a foundational
shift, but the current synchronous architecture is sufficient for V1. The knowledge wiring
can be done within the existing architecture.

**Why #1 ranks above #5**: Candidate 5 (iterative refinement) is a significant new
capability that should come after the core analysis quality is proven.

---

## 5. RECOMMENDED_NEXT_OBJECTIVE

```
NEXT_DEVLOG_OBJECTIVE =
    Wire validated knowledge into Story Context Analysis agent

PRIMARY_GAP =
    AnimateStoryContextUseCase.buildSelectedKnowledge() hardcodes
    selectedFacts, selectedObservations, selectedInsights as empty lists.
    The agent analyzes stories without any validated project knowledge.

WHY_NOW =
    This is the most fundamental gap between DevLog's architectural
    capability and its actual agent behavior. The knowledge selection
    engine exists, the prompt builder expects these fields, and the
    product quality gate failures (Stories 0108/0109) correlate with
    missing knowledge context. Fixing this demonstrates the core
    value proposition of DevLog as shared/project memory.

SUCCESS_CRITERIA =
    1. StoryContextAnalysis agent receives non-empty selectedFacts,
       selectedObservations, and selectedInsights when available.
    2. Agent findings reference validated knowledge items by ID/title.
    3. Agent output classification includes FACTUAL_EXTRACTION entries
       that reference validated knowledge, not just repository evidence.
    4. ADR-066 evaluation scenario for story-context-analysis passes
       with the knowledge-enriched context.
    5. Product quality gate (STRONG or ACCEPTABLE) is achievable for
       story-context-analysis with knowledge-enriched context.

UNLOCKS =
    - Meaningful analysis that demonstrates accumulated project memory
    - Significance scoring over knowledge-informed analysis
    - Proactive communication with important findings
    - Future Agent Presence Device with useful content

DOES_NOT_YET_REQUIRE =
    - Event-driven architecture
    - Vector retrieval / RAG
    - Iterative agent refinement
    - Real-time communication
    - Hardware integration
```

---

## 6. PRESENCE_DEVICE_RELEVANCE

The causal chain from this objective to the Agent Presence Device:

```
Current state:
  Agent produces analysis → analysis lacks validated knowledge →
  analysis quality is limited → nothing important to communicate →
  presence device would display noise

After this objective:
  Agent produces analysis → analysis informed by validated knowledge →
  analysis quality improves → important findings emerge →
  presence device can display genuinely useful information
```

The presence device needs a concise, important message to display. That message must come
from analysis that is good enough to contain important findings. Analysis informed by
validated knowledge is more likely to contain important findings than analysis with
repository evidence only. Therefore, wiring validated knowledge is a prerequisite for
meaningful physical presence.

This does NOT mean the presence device should be implemented next. It means the content
that would eventually appear on the device must be high-quality first.

---

## 7. RAG_READINESS

```
RAG_READINESS = NOT_READY
```

**Reasoning:**

1. The primary bottleneck is not retrieval/relevance — it is that the agent doesn't receive
   validated knowledge at all. RAG solves "finding relevant information from a large corpus";
   the current problem is "providing any validated knowledge to the agent."

2. ADR-015 explicitly defers semantic retrieval until concrete need is demonstrated. The
   concrete need has not yet been demonstrated because the basic wiring is incomplete.

3. ADR-063 explicitly rejects RAG infrastructure before retrieval semantics stabilize.
   Retrieval semantics cannot stabilize until the basic knowledge pipeline is functional.

4. ADR-067 Non-Goals include "RAG, vector database, embeddings." This remains appropriate
   until the deterministic knowledge pipeline is proven.

**When RAG becomes relevant:** After the validated knowledge wiring is complete and the
agent demonstrates that knowledge-informed analysis is meaningfully better than evidence-only
analysis. At that point, if the agent consistently cannot find relevant knowledge from the
existing selection engine, RAG may be warranted.

---

## 8. ADR_REQUIRED

**NO.**

This objective does not introduce a new architectural decision. The knowledge selection
infrastructure already exists (ADR-033). The Story Context Analysis agent architecture
is already governed by ADR-067. The change is wiring existing capabilities into an existing
use case, not introducing a new boundary or contract.

If the investigation reveals that the knowledge budget for Story Context Analysis requires
new architectural constraints (e.g., a different budget than other intents, or special
selection rules), a focused ADR may be warranted. But that determination should be made
during implementation investigation, not now.

---

## 9. UNRESOLVED_QUESTIONS

1. **Knowledge budget for Story Context Analysis**: What budget constraints should apply?
   The existing `KnowledgeSelectionServiceImpl` uses intent-aware budgets. The Story
   Context Analysis intent may need different budgets than architecture-overview or
   engineering-event intents.

2. **Which knowledge types are most valuable**: Should the agent receive all validated
   knowledge types (Facts, Observations, Insights, Decisions, Engineering Events) or
   a subset? The prompt builder expects all of them, but the agent may not need all
   of them for every story.

3. **Cross-story context**: Should the agent receive knowledge from other stories?
   The current architecture scopes analysis to a single story. Cross-story knowledge
   could improve analysis but introduces scope complexity.

4. **Grounding contract update**: The current grounding contract extracts allowed
   references from repository evidence only. With validated knowledge, the grounding
   contract should also include references to validated knowledge items.

5. **Evaluation scenario update**: The ADR-066 evaluation scenario for
   story-context-analysis should be updated to include validated knowledge in the
   fixture. This validates that the knowledge-enriched prompt produces better output.

6. **Historical analysis compatibility**: Should existing analyses (with empty knowledge)
   be backfilled? The Story 0114 precedent suggests no — historical analyses keep their
   original context. This should be confirmed.

---

## 10. INVESTIGATION_METHOD

This investigation was conducted by:

1. **Story and roadmap analysis**: Read all 113+ story directories, the roadmap, and
   README to reconstruct the project trajectory.
2. **ADR analysis**: Read 48 accepted ADRs and 11 proposed ADRs to understand
   architectural constraints and open decisions.
3. **Code analysis**: Read the end-to-end Story Context Analysis flow from trigger
   through persistence, including the knowledge selection engine, prompt builders,
   and AI generation service.
4. **Event architecture analysis**: Searched for ApplicationEvent, @EventListener,
   @Scheduled, message broker, outbox, and notification patterns.
5. **Knowledge system analysis**: Read the Fact, Observation, Insight, Decision,
   EngineeringEvent, and KnowledgeRelation entities and their lifecycle.
6. **Communication readiness analysis**: Assessed significance modeling, proactive
   behavior, notification mechanisms, and real-time capabilities.

All findings are grounded in repository evidence. No assumptions were made about
capabilities not present in the codebase.

---

*Generated from repository evidence on the `investigation/agent-maturity-next-objective` branch.*
*If repository reality contradicts this investigation, repository reality wins.*
