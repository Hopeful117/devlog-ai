# Post-Story-0104 Investigation — Analysis Quality, Payload Cost, and MCP Context Exposure

**Date:** 2026-08-30
**Status:** INVESTIGATION_COMPLETE
**Scope:** READ-ONLY — no code changes, no commits, no design modifications

---

## Executive Summary

Story 0104 implemented deterministic semantic sections for Analysis context composition. This investigation answers three questions:

1. **Did Semantic Sections materially improve human-facing Analysis quality?**
   - describe-project-v1: 7 proposals (up from 6). Quality partially grounded but summaries remain short.
   - architecture-overview-v1: 0 proposals (UNCHANGED from Story 0103). No improvement.
   - analyze-engineering-decision-v1: 4 proposals (UNCHANGED from Story 0103). Titles only, no summaries.
   - **Conclusion: PARTIALLY_EFFECTIVE** — describe-project improved slightly; architecture-overview and engineering-decisions unchanged.

2. **Why did the real prompt payload grow by ~44–58% instead of ~11–12%?**
   - Two independent causes: (a) semanticSections overhead (~24-27% of total payload), and (b) canonical content growth from different selected knowledge (+7.5-15.3%).
   - The +7.5-12% estimate was for semanticSections only, measured against a different baseline.
   - **Conclusion: EXPLAINED** — semanticSections contribute 23,912-26,429 bytes; canonical content grew independently due to different analysis runs.

3. **Does the MCP path interact with Story 0104 changes?**
   - MCP uses a completely separate data path (ProjectContextProvider → RepositoryContextAdapter → EngineeringContextContractMapper).
   - MCP does NOT use SelectedKnowledge, SemanticSectionComposer, or PromptProjection.
   - **Conclusion: NO_INTERACTION** — MCP is completely isolated from Story 0104.

---

## Part I — Story 0104 Human Analysis Quality

### describe-project-v1

**Proposal count:** 7 (up from 6 in Story 0103)

| # | Title | Confidence | Evidence Preview | Summary Length |
|---|---|---|---|---|
| 1 | Project Documentation Structure | 0.90 | Fact#bdf9dd06 | 177 chars |
| 2 | Automated and Integration Testing Present in Project | 0.90 | Fact#6e703e51 | 164 chars |
| 3 | Use of Architecture Decision Records (ADR) for Documentation | 0.95 | Fact#9795dbbd | 193 chars |
| 4 | Project Containerization with Docker and Docker Compose | 0.95 | Fact#01f0c58f, Fact#2a098ac8, Fact#bba5db0c, Observation#a226b596 | 160 chars |
| 5 | Multi-module Build System Using Maven | 0.95 | Fact#093c7d20, Fact#21bec8e2, Fact#87bb70f7 | 167 chars |
| 6 | Spring Boot REST API Application | 0.95 | Fact#1a49ce7b | 161 chars |
| 7 | Overview of the 'devlog-ai' Project | 0.95 | (none) | 224 chars |

**Assessment:**
- Proposals are **partially grounded** — evidencePreview references specific fact/observation IDs
- Summaries are **short** (160-224 chars) — factual statements, not deep analysis
- Proposal 7 has **no evidence** — generic project overview
- No deltaType or insightType fields populated in response
- SupportingFactIds and SupportingObservationIds arrays are **empty** despite evidencePreview showing references

**Rubric-relevant observations:**
- 0 = absent/unusable: NOT applicable — proposals exist
- 1 = generic fact enumeration: MOSTLY — proposals enumerate facts without synthesis
- 2 = partially grounded: SOME proposals (4,5 have multi-fact grounding)
- 3 = coherent purpose + architecture + current state + evolution: NOT achieved

### architecture-overview-v1

**Proposal count:** 0 (UNCHANGED from Story 0103)

**Assessment:**
- Zero proposals produced — same as Story 0103
- This is **expected behavior** per ADR-064: "Architecture review zero-proposal behavior unchanged (out of scope)"
- The architecture-overview intent produces insights, not proposals (2 insights were produced but not exposed in the result)
- **No improvement from Story 0104**

### analyze-engineering-decision-v1

**Proposal count:** 4 (UNCHANGED from Story 0103)

| # | Title | Confidence | Summary | Evidence |
|---|---|---|---|---|
| 1 | Retain Project Containerization Using Docker and Docker Compose | 1.0 | (empty) | (empty) |
| 2 | Continue Using Spring Boot to Expose REST API Controllers | 1.0 | (empty) | (empty) |
| 3 | Preserve Multi-Module Build Architecture Using Maven | 1.0 | (empty) | (empty) |
| 4 | Maintain Use of Architecture Decision Records (ADRs) for Architectural Decision Documentation | 1.0 | (empty) | (empty) |

**Assessment:**
- All 4 proposals have **empty summaries** and **empty evidence arrays**
- Titles are reasonable but provide no context, trade-offs, or consequences
- Confidence is 1.0 for all — no nuance
- **No improvement from Story 0104**

### Architecture Proposal Change Investigation

**Historical Story 0103:** architecture-overview-v1 produced 0 proposals.
**Story 0104:** architecture-overview-v1 produced 0 proposals.

The earlier benchmark report claiming "4 proposals" was **incorrect** — it was a misreading of the result structure. The actual result shows `total: 0, items: []`.

**Classification: UNCHANGED** — no causal relationship to investigate.

---

## Part II — Story 0104 Payload Cost

### Why did payload grow by ~44–58%?

The payload increase has **two independent components**:

#### Component 1: SemanticSections overhead (Story 0104)

| Intent | SemanticSections bytes | % of total payload |
|---|---|---|
| describe-project-v1 | 24,583 | 24.9% |
| architecture-overview-v1 | 26,429 | 26.9% |
| analyze-engineering-decision-v1 | 23,912 | 25.2% |

#### Component 2: Canonical content growth (different selected knowledge)

| Intent | Historical baseline | Current (no semanticSections) | Delta |
|---|---|---|---|
| describe-project-v1 | 68,040 | 74,063 | +6,023 (+8.9%) |
| architecture-overview-v1 | 62,136 | 71,630 | +9,494 (+15.3%) |
| analyze-engineering-decision-v1 | 66,077 | 71,052 | +4,975 (+7.5%) |

**Explanation:** The historical baselines are from Story 0103, which ran against a different knowledge graph state. The Story 0104 benchmark runs against the current knowledge graph (more facts, observations, insights available). This canonical content growth is **NOT caused by Story 0104** — it's a dataset/environment difference.

#### Total delta decomposition

| Intent | Canonical delta | SemanticSections | Total delta | % increase |
|---|---|---|---|---|
| describe-project-v1 | +6,023 | +24,583 | +30,628 | +45.0% |
| architecture-overview-v1 | +9,494 | +26,429 | +35,945 | +57.8% |
| analyze-engineering-decision-v1 | +4,975 | +23,912 | +28,909 | +43.8% |

### Semantic Reference Cost Breakdown

| Metric | describe-project | architecture-overview | engineering-decisions |
|---|---|---|---|
| Total references | 189 | 202 | 186 |
| Unique items | 117 | 125 | 117 |
| Average reference size | 127.3 bytes | 128.2 bytes | 125.7 bytes |
| Single-membership items | 45 (38%) | 48 (38%) | 48 (41%) |
| Multi-membership items | 72 (61%) | 77 (62%) | 69 (59%) |
| Multi-membership overhead | 14,015 bytes | 14,547 bytes | 12,600 bytes |

**Average reference cost:** ~127 bytes per reference (includes JSON structural overhead: `{` `}` `"` `,` keys).

**Multi-membership byte cost:** 12,600-14,547 bytes — the cost of items appearing in 2 sections.

### Section Size Breakdown (describe-project-v1)

| Section | Items | Section bytes | Items bytes | Wrapper bytes |
|---|---|---|---|---|
| PROJECT_STATE | 31 | 3,347 | 3,273 | 63 |
| ARCHITECTURE | 16 | 1,735 | 1,663 | 61 |
| DECISIONS | 2 | 289 | 223 | 55 |
| VALIDATED_KNOWLEDGE | 22 | 2,470 | 2,384 | 75 |
| HISTORY | 55 | 7,711 | 7,649 | 51 |
| REPOSITORY_CHANGES | 60 | 8,649 | 8,565 | 73 |
| HUMAN_CONTEXT | 3 | 368 | 294 | 63 |

### Accidental Duplication Check

**CLEAN** — all items have exactly 3 keys: `itemType`, `itemId`, `label`. No full objects, no nested data, no duplicate canonical content.

---

## Part III — Semantic Membership Distribution

### PROJECT_STATE Breadth

**Total items:** 31
**Multi-membership rate:** 58% (18 of 31 items also appear in another section)

| itemType | Count | Also in |
|---|---|---|
| FACT | 19 | ARCHITECTURE (9), DECISIONS (1) |
| INSIGHT | 5 | VALIDATED_KNOWLEDGE (5) |
| HUMAN_CONTEXT | 3 | HUMAN_CONTEXT (3) |
| ANALYSIS | 1 | (only PROJECT_STATE) |
| OBSERVATION | 1 | ARCHITECTURE (1) |
| PROJECT | 1 | (only PROJECT_STATE) |
| PROJECT_PROFILE | 1 | (only PROJECT_STATE) |

**Classification:** `PROJECT_STATE_MAPPING = BROAD_BUT_DESIGN_CONSISTENT`

PROJECT_STATE has become a broad umbrella section — 58% of its items also belong to another primary section. This is **consistent with the approved design**: PROJECT_STATE captures "everything relevant to current project identity/state" and multi-membership handles cross-dimension items.

### REPOSITORY_CHANGES Breadth

**Total items:** 60
**All items have itemType=REPOSITORY_EVIDENCE:** YES

| Cross-section | Count |
|---|---|
| Also in HISTORY | 35 |
| Also in VALIDATED_KNOWLEDGE | 12 |
| Also in DECISIONS | 1 |
| Only in REPOSITORY_CHANGES | 0 |

**All 60 items also belong to at least one other section.** REPOSITORY_CHANGES is a pure provenance section — every repository evidence item has a topic-based primary section (HISTORY, VALIDATED_KNOWLEDGE, or DECISIONS).

**Classification:** `REPOSITORY_CHANGES_MAPPING = CONSISTENT` — the approved mixed provenance + topic taxonomy is working as designed.

---

## Part IV — MCP Architecture

### MCP Implementation Summary

The repository contains a **complete, production-grade MCP server**:

- **Module:** `mcp-server/` (Spring Boot 4.1.0, Spring AI MCP 2.0.0)
- **Transport:** STDIO synchronous (no SSE, no HTTP)
- **Tools:** 3 (`get_engineering_context`, `search_project_history`, `echo_message`)
- **Resources:** 9 (including 1 template)
- **Prompts:** 1 (`explain_code`)
- **Shared contracts:** `devlog-contracts/` module (16 DTOs)
- **Tests:** 16 test files including STDIO protocol hygiene test

### MCP Context Flow

```
MCP Client (IDE agent)
    ↓ STDIO (JSON-RPC)
MCP Server (mcp-server module)
    ↓ HTTP (RestClient)
Backend EngineeringContextController
    ↓
EngineeringContextFacadeImpl
    ├── ProjectContextProvider.build(projectId)
    │       → ProjectContextSnapshot
    ├── RepositoryContextAdapter.buildRepositoryContext(projectId, intent, projectContext)
    │       → RepositoryContext
    └── ProjectFreshnessService.summary(projectId)
            → ProjectFreshnessSummary
    ↓
EngineeringContextContractMapper.toContract(...)
    ↓
EngineeringContext (MCP contract DTO)
    ↓
JSON serialization → STDIO → MCP Client
```

### Analysis Context Flow (for comparison)

```
AnalysisController.create(request)
    ↓
AnalysisServiceImpl.create(intent)
    ↓
KnowledgeSelectionService.select(projectId, analysis)
    ↓
SelectedKnowledgePromptProjectionService.project(selectedKnowledge)
    ├── SemanticSectionComposer.compose(selectedKnowledge)  ← Story 0104
    │       → List<PromptSemanticSection>
    ├── projectInsight(insight)  ← Story 0104: preserves insight ID
    │       → PromptInsightSnapshot
    └── buildRelationshipHighlights(selectedKnowledge)
            → List<PromptRelationshipHighlight>
    ↓
PromptProjection (with semanticSections field)
    ↓
ObjectMapper → JSON → AI Engine
```

---

## Part V — MCP Context Exposure

### MCP Tool: get_engineering_context

**Returns:** `EngineeringContext` with:
- Project identity (name, slug, description)
- Intent (free-text string)
- Repository evidence (60 items with full content, symbols, resource URIs)
- Metadata (candidateCount, selectedCount, truncated, usedTokens, contextDigest, warnings, freshness)

**Does NOT return:**
- selectedFacts, selectedObservations, selectedInsights
- projectProfile, humanContextInputs, engineeringStories
- knowledgeRelations, validatedProposals, architectureArtifacts
- semanticSections, relationshipHighlights

### MCP Resources

| Resource | URI | Exposes |
|---|---|---|
| Projects | `devlog://projects` | Project list |
| Project Context | `devlog://projects/{slug}/context` | Identity + notes |
| Decision | `devlog://projects/{slug}/decisions/{id}` | Single decision |
| Insight | `devlog://projects/{slug}/insights/{id}` | Single active insight |
| Story | `devlog://projects/{slug}/stories/{id}` | Single story |
| Engineering Event | `devlog://projects/{slug}/engineering-events/{id}` | Single event |
| Commit Context | `devlog://projects/{slug}/commits/{sha}` | Commit context |
| Freshness | `devlog://projects/{slug}/freshness` | Freshness status |
| Server Info | `devlog://server/info` | Server identity |

### Semantic Sections MCP Visibility

**Classification: NO_SEPARATE_PATH**

Story 0104 semanticSections are NOT visible to MCP consumers. The MCP path uses a completely separate data flow (ProjectContextProvider → RepositoryContextAdapter → EngineeringContextContractMapper) that does not touch SelectedKnowledge, SemanticSectionComposer, or PromptProjection.

This is **correct behavior** — MCP and Analysis have different responsibilities:
- MCP: provide raw engineering evidence for interactive investigation
- Analysis: provide structured, section-organized knowledge for AI generation

---

## Part VI — Human Context / Supervision Audit

### MCP Evidence Categories

| Category | MCP Exposure | Analysis Exposure |
|---|---|---|
| Project | FULL (via project context resource) | FULL (project snapshot) |
| Facts | ABSENT (not in engineering context) | FULL (selectedFacts) |
| Observations | ABSENT (not in engineering context) | FULL (selectedObservations) |
| Insights | PARTIAL (single insight via resource, not bulk) | FULL (selectedInsights) |
| Engineering Events | PARTIAL (single event via resource, not bulk) | FULL (selectedEngineeringEvents) |
| Human Context | ABSENT (not in engineering context) | FULL (selectedHumanContextInputs) |
| Repository Evidence | FULL (60 items with full content) | FULL (repositoryContext.evidence) |
| Evolution/History | PARTIAL (via commit context resource) | FULL (evolutionContext) |
| Relationships | ABSENT (not exposed) | PARTIAL (relationshipHighlights, 0 eligible) |
| Project Profile | ABSENT (not in engineering context) | FULL (projectProfile) |
| Architecture Artifacts | ABSENT (not exposed) | FULL (architectureArtifacts) |
| Validated Proposals | ABSENT (not exposed) | FULL (validatedProposals) |
| Semantic Sections | ABSENT (not exposed) | FULL (semanticSections) |

### Human Context Supremacy

**Within the same authorization scope, every canonical engineering-evidence category available to an AI agent must be retrievable/inspectable by the supervising HUMAN.**

MCP currently exposes:
- Repository evidence: FULL (via get_engineering_context tool)
- Individual artifacts: PARTIAL (via resources — decision, insight, story, event, commit)
- Bulk knowledge (facts, observations, insights): ABSENT

The HUMAN can inspect individual artifacts via MCP resources but cannot bulk-inspect the knowledge selection that feeds the Analysis pipeline. This is a **pre-existing gap** not caused by Story 0104.

---

## Part VII — Identity / Relationship / History Audit

### MCP Identity Preservation

| Entity | MCP Identity | Stable? |
|---|---|---|
| Decision | `devlog://projects/{slug}/decisions/{id}` (UUID) | YES |
| Insight | `devlog://projects/{slug}/insights/{id}` (UUID) | YES |
| Story | `devlog://projects/{slug}/stories/{id}` (UUID) | YES |
| Engineering Event | `devlog://projects/{slug}/engineering-events/{id}` (UUID) | YES |
| Commit | `devlog://projects/{slug}/commits/{sha}` (SHA) | YES |
| Repository Evidence | `resource` field in engineering context | PARTIAL (only DECISION, INSIGHT, ENGINEERING_STORY, ENGINEERING_EVENT, COMMIT have URIs) |

**Story 0104 insight ID preservation:** The `projectInsight()` change in SelectedKnowledgePromptProjectionService now preserves `InsightSnapshot.id`. This affects the Analysis path only — MCP already exposes insight IDs via the insight resource.

### MCP Relationship Visibility

MCP does NOT expose knowledge relations. The Analysis path has `relationshipHighlights` (currently 0 due to Policy-A eligibility). Neither path exposes the full knowledge relation graph to external consumers.

### MCP History Visibility

**Classification: PARTIAL**

MCP exposes:
- Commit context via `devlog://projects/{slug}/commits/{sha}` (deterministic, classified diff)
- Search via `search_project_history` tool (keyword search over commit messages and paths)
- Freshness status via `devlog://projects/{slug}/freshness`

MCP does NOT expose:
- Full commit list (only individual commit context)
- Evolution context (previous analyses, temporal knowledge)
- Historical facts or observations
- ADR history or roadmap history

---

## Part VIII — Story 0104 MCP Regression Risk

**Classification: MCP_REGRESSION_RISK = NONE**

Evidence:
1. MCP does NOT use `SelectedKnowledgePromptProjectionService` (modified by Story 0104)
2. MCP does NOT use `SemanticSectionComposer` (created by Story 0104)
3. MCP does NOT use `PromptProjection` (modified by Story 0104)
4. MCP uses `EngineeringContextContractMapper` (completely separate)
5. No shared DTOs were modified by Story 0104
6. The only shared data models (ProjectContextSnapshot, RepositoryContext) are read-only in both paths
7. The insight ID preservation change affects PromptProjection only — MCP already exposes insight IDs via resources

---

## Part IX — Product Gaps

### Gap 1: Bulk Knowledge Exposure via MCP

**Classification: MCP_EXPOSURE_GAP**

- **Current behavior:** MCP exposes individual artifacts (decisions, insights, stories, events) via resources, but not bulk knowledge (facts, observations, insights in aggregate)
- **Expected property:** HUMAN should be able to inspect the knowledge selection that feeds Analysis
- **Evidence:** get_engineering_context returns 60 repository evidence items but no facts, observations, or insights
- **Impact:** HUMAN cannot verify what knowledge the AI engine receives without using the backend API directly
- **Story 0104 caused:** NO — pre-existing gap

### Gap 2: Semantic Sections Not Exposed to MCP

**Classification: MCP_EXPOSURE_GAP**

- **Current behavior:** MCP does not expose semanticSections
- **Expected property:** Whether MCP SHOULD expose semantic sections is a design question, not a gap
- **Evidence:** MCP path is completely separate from Analysis path
- **Impact:** MCP consumers cannot see how knowledge is organized into sections
- **Story 0104 caused:** NO — MCP was separate before Story 0104

### Gap 3: Relationship Graph Not Exposed

**Classification: RELATIONSHIP_ACCESS_GAP**

- **Current behavior:** Neither MCP nor Analysis exposes the full knowledge relation graph
- **Expected property:** HUMAN should be able to inspect knowledge relationships
- **Evidence:** 44 canonical relations exist (all INSIGHT→INSIGHT RESOLVES links), none exposed via MCP
- **Impact:** HUMAN cannot trace knowledge provenance through relationships
- **Story 0104 caused:** NO — pre-existing gap

### Gap 4: Engineering Decision Proposals Have Empty Summaries

**Classification: OBSERVABILITY_GAP**

- **Current behavior:** analyze-engineering-decision-v1 produces 4 proposals with empty summaries and empty evidence arrays
- **Expected property:** Proposals should include context, trade-offs, and consequences
- **Evidence:** All 4 proposals have `summary: ""` and `evidencePreview: []`
- **Impact:** HUMAN cannot evaluate proposal quality without reading raw AI output
- **Story 0104 caused:** NO — same behavior as Story 0103

---

## Part X — Recommendations

### Recommendation 1: Investigate canonical content growth separately

The +7.5-15.3% canonical content growth is independent of Story 0104. A separate investigation should determine whether this is due to:
- Knowledge graph growth (more facts/observations/insights available)
- Different selection behavior (different items selected)
- Different analysis configuration

### Recommendation 2: Consider exposing semantic sections via MCP (future design question)

Whether MCP should expose semantic sections is a product design decision. The current separation (MCP = raw evidence, Analysis = structured sections) is architecturally valid. If MCP consumers need section-organized knowledge, a dedicated MCP resource could be added without changing the existing architecture.

### Recommendation 3: Address engineering decision proposal quality (separate from Story 0104)

The empty summaries in engineering decision proposals are a pre-existing issue. The prompt template or output contract may need adjustment to produce more substantive proposals.

---

## Appendix A — Raw Benchmark Data

### describe-project-v1

- **Analysis ID:** a29221f4-e931-4083-9230-adbebb039c30
- **Task ID:** a746d6ce-e261-4726-a7ef-95aca6c5fa39
- **Status:** COMPLETED
- **Payload bytes:** 98,668
- **SemanticSections bytes:** 24,583 (24.9%)
- **Sections:** 7 (all populated)
- **Total items:** 189
- **Unique items:** 117
- **Multi-membership:** 72 (61%)
- **Proposals:** 7

### architecture-overview-v1

- **Analysis ID:** 0a98f847-540a-42c7-b053-7ed5f93d4165
- **Task ID:** 8531b9a0-0603-4df4-bfb3-f2609e11eeed
- **Status:** COMPLETED
- **Payload bytes:** 98,081
- **SemanticSections bytes:** 26,429 (26.9%)
- **Sections:** 7 (all populated)
- **Total items:** 202
- **Unique items:** 125
- **Multi-membership:** 77 (62%)
- **Proposals:** 0

### analyze-engineering-decision-v1

- **Analysis ID:** 491e9ea0-63d0-42c0-a348-1b40a1aa5dbc
- **Task ID:** f80f0ef3-47f3-46ef-8608-ae7140b72ba6
- **Status:** COMPLETED
- **Payload bytes:** 94,986
- **SemanticSections bytes:** 23,912 (25.2%)
- **Sections:** 7 (all populated)
- **Total items:** 186
- **Unique items:** 117
- **Multi-membership:** 69 (59%)
- **Proposals:** 4 (all with empty summaries)
