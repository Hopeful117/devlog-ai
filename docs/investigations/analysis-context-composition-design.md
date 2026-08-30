# Analysis Context Composition Design Investigation

## Status

Investigation and architecture design only. No production changes.

## Baseline

- Baseline SHA: `a4aa44d`
- Governing merged change: Story 0098 resolved the `CATEGORY_SELECTION` bottleneck
- Accepted next primary bottleneck: `CONTEXT_COMPOSITION`

## Objective

Define how rich selected engineering evidence should be transformed into an effective
AI-facing Analysis context before generation.

## Current End-to-End Pipeline

```text
candidate evidence
-> deterministic selection
-> selected evidence / SelectedKnowledge
-> prompt projection
-> AI task request
-> prompt builder
-> provider payload
-> structured AI response
-> proposal persistence
-> canonical result projection
```

### 1. Persisted Analysis State -> `AnalysisContext`

- Owner: `AnalysisContextServiceImpl`
- Output: `AnalysisContext`
- Contains:
  - project, analysis, projectProfile
  - facts, observations
  - related analyses, related decisions, architecture artifacts
  - milestones, validated proposals, human context inputs
  - knowledge relations, engineering stories, validated engineering events
  - optional evolution context
- Notes:
  - facts and observations are bounded and closure-trimmed deterministically
  - this is the richest internal context object on the backend side

### 2. `AnalysisContext` -> Repository Evidence Candidates

- Owner: `RepositoryContextEngine.retrieveCandidates`
- Output: `List<RepositoryEvidence>`
- Evidence fields include:
  - `layer`, `kind`, `reference`, `summary`, `occurredAt`
  - `score`, `relatedReferences`, `provenance`, `extractionMetadata`
  - `rankingReasons`, `content`, `symbols`

### 3. Candidates -> Ranked/Selected `RepositoryContext`

- Owners:
  - ranking: `DeterministicEvidenceRanker`
  - selection: `BudgetedDiverseEvidenceSelector`
  - enrichment: file/symbol enrichers
- Output: `RepositoryContext`
- Story 0098 impact:
  - `COMMIT_DIFF` is now capped at `12 / 60 = 20%`

### 4. `AnalysisContext` + `RepositoryContext` -> `SelectedKnowledge`

- Owner: `KnowledgeSelectionServiceImpl`
- Output: `SelectedKnowledge`
- Contains selected slices:
  - selected facts, observations, insights
  - existing architecture knowledge
  - engineering events, human context inputs
  - repository context
  - evolution context
  - selection metadata and digest
- Important loss already visible here:
  - many rich `AnalysisContext` structures are no longer first-class after selection,
    especially relationship-carrying sets such as `knowledgeRelations`,
    `validatedProposals`, `relatedDecisions`, `engineeringStories`, `openChallenges`

### 5. `SelectedKnowledge` -> AI-Facing Projection

- Owner: `SelectedKnowledgePromptProjectionService`
- Output: `Map<String,Object>`
- Keeps:
  - project, analysis, projectProfile
  - selected facts, observations, insights
  - existing architecture knowledge
  - engineering events, human context inputs
  - reduced repository context
  - evolution context
  - selection metadata and digest
- Drops or weakens:
  - selected insight ids and analysis linkage
  - repository context diagnostics, selection decisions, budget accounting
  - repository evidence score, provenance, extraction metadata, ranking reasons

### 6. Projection -> `AiTask` / `PromptRequest`

- Owners: `AnalysisWorkflowServiceImpl`, `AiTaskServiceImpl`
- Persisted:
  - full `AnalysisContext` in `contextSnapshot`
  - projected selected knowledge in `selectedKnowledgeSnapshot`
- AI engine receives:
  - projected selected knowledge, not full `AnalysisContext`

### 7. Prompt Builder

- Owners:
  - `InsightPromptBuilder`
  - `EngineeringDecisionPromptBuilder`
  - `EngineeringEventPromptBuilder`
- Current representation:
  - a large JSON block embedded in prompt text:
    - `BEGIN UNTRUSTED SELECTED KNOWLEDGE ... END`
  - plus a grounding contract JSON block
  - plus optional user guidance and output schema blocks

### 8. Provider Payload

- Owner: `OpenAiLlmProvider`
- Representation:
  - system message + user message text
  - structured output model via provider API

### 9. Structured AI Response -> Proposal Persistence

- Insight/event flows preserve grounding arrays
- Engineering decision flow currently hardcodes:
  - `supportingFactIds=[]`
  - `supportingObservationIds=[]`
  - `evidenceReferences=[]`

### 10. Persistence -> Canonical `/result`

- Owner: `AnalysisResultQueryServiceImpl`
- Canonical result further flattens selected evidence to generic preview items
- Repository evidence preview is limited to 5 items

## Information-Loss Matrix

| Information | At Selection | After Projection | In Full AnalysisContext | In Final AI Payload | Operationally Emphasized |
|---|---|---|---|---|---|
| Evidence identity | Yes | Yes | Partial | Yes | Weak |
| Category/type | Yes | Yes | Yes | Yes | Moderate |
| Content | Partial | Partial | Yes | Partial | Weak |
| Summary | Yes | Yes | N/A | Yes | Moderate |
| Timestamp | Yes | Yes | Yes | Yes | Weak |
| Chronology | Implicit | Implicit | Implicit | Technically present | No |
| Relevance/ranking | Yes | No | No | No | No |
| Provenance | Yes | Mostly lost | N/A | Mostly lost | No |
| `supportingFactIds` | Yes | Yes | Yes | Yes | Moderate |
| `relatedReferences` | Yes | Yes | N/A | Yes | Weak |
| `knowledgeRelations` | In `AnalysisContext` | No | Yes | No | No |
| Source relationships | Partial | Partial | Partial | Partial | Weak |
| ADR relationships | Partial | Partial | Partial | Partial | Weak |
| Roadmap relationships | Partial | Partial | Partial | Partial | Weak |
| Historical relationships | Partial | Partial | Partial | Partial | Weak |
| Validated-knowledge relationships | Partial | Partial | Partial | Partial | Weak |
| Human context | Yes | Yes | Yes | Yes | Weak |

Key distinction:

- Many fields remain technically present.
- They are not effectively usable by the model because they are embedded in one large,
  weakly structured JSON payload with little semantic hierarchy.

## Existing Relationship Model

### Explicit relationships already available

- Observation -> supporting Facts
- Insight -> project / analysis / proposal / validation
- EngineeringEvent -> project / analysis / proposal / validation / source
- Decision -> project / proposal
- `KnowledgeRelation` graph:
  - entity types: `CHALLENGE`, `DECISION`, `ENGINEERING_EVENT`, `INSIGHT`
  - relation types: `RESOLVES`, `CAUSED_BY`, `RELATES_TO`, `DERIVED_FROM`, `ADDRESSES`, `INFORMED_BY`
- Existing architecture knowledge keeps `insightId`, `proposalId`, and `evidenceReferences`

### Implicit relationships already available

- `RepositoryEvidence.reference`
- `RepositoryEvidence.relatedReferences`
- commit parent references
- evolution scope base/target commit semantics

### Relationship loss today

- `knowledgeRelations` do not survive into AI-facing selected knowledge
- many top-level relationship carriers cease to exist as first-class projected context
- repository provenance and extraction metadata are removed before AI generation

## Existing Temporal Model

Available temporal signals today:

- fact `detectedAt`
- observation `createdAt`
- analysis `startedAt`, `completedAt`, `createdAt`
- insight/decision/challenge/context-input timestamps
- engineering event `occurredAt`
- commit and diff timestamps
- evolution scope commit ordering

What exists:

- reverse-chronological repository queries
- commit-parent history semantics
- deterministic recency scoring

What is missing operationally:

- explicit, bounded, model-usable chronology projection for generic Analysis

## Deterministic vs AI Responsibility

Preferred boundary:

```text
Java:
organize known engineering context

AI:
interpret and synthesize that context
```

Java should deterministically provide:

- objective
- scope
- category boundaries
- chronology cues
- explicit trusted relationships already known
- provenance
- salience

AI should infer:

- higher-level meaning
- synthesis
- proposal framing

Java must not invent unsupported semantic relationships.

## Current Flat JSON Problem

The current issue is not “JSON” itself.

The issue is that selected knowledge is flattened into one large prompt block with:

- weak grouping
- weak hierarchy
- weak relationship projection
- chronology only as metadata
- limited salience cues
- too much competition for model attention

## Exact Composition Failures

- `GIT_HISTORY`, `PREVIOUS_ANALYSIS`, `ROADMAP`, and `HUMAN_CONTEXT` are often selected but ignored
- `knowledgeRelations` exist upstream but disappear before AI generation
- architecture review can receive rich evidence and still produce zero proposals
- engineering decision analysis receives evidence identities but persists empty grounding
- canonical result preview hides some internal diversity, but this is secondary

## Grounding Failure Trace

Trace:

```text
selected evidence identity
-> projected selected knowledge
-> decision prompt grounding allow-list
-> decision output contract without grounding fields
-> decision generation service writes empty arrays
-> backend persists empty grounding arrays
```

Root cause:

- combination of contract design and service mapping
- not a retrieval failure
- not a selection failure

## Architecture Review Zero-Proposal Trace

Trace:

```text
selected architecture evidence
-> existingArchitectureKnowledge added to prompt
-> prompt reframes task as delta detection
-> zero proposals explicitly allowed
-> empty output accepted as success
```

Root cause:

- current architecture-review objective behaves like “meaningful new architecture delta only”
- not like “helpful architecture synthesis from rich context”

## Candidate Designs

### Candidate A — Structured Sections

```text
AnalysisContext
├── objective
├── scope
├── projectState
├── architecture
├── decisions
├── history
├── roadmap
├── validatedKnowledge
├── repositoryChanges
└── grounding
```

Pros:

- incremental
- easy to test
- better hierarchy than current flat payload

Cons:

- relationships remain mostly implicit
- chronology remains weaker than desired

### Candidate B — Evidence + Relationship Projection

```text
AnalysisContext
├── objective
├── evidence[]
├── relationships[]
├── timeline[]
└── grounding
```

Pros:

- strongest raw information preservation
- strongest explicit relationship and temporal potential

Cons:

- more complex
- greater contract disruption
- weaker direct model usability unless additionally curated

### Candidate C — Hybrid Structured Sections + Relationship/Timeline Highlights

```text
AnalysisContext
├── objective
├── sections
│   ├── projectState
│   ├── architecture
│   ├── decisions
│   ├── history
│   ├── roadmap
│   ├── validatedKnowledge
│   └── repositoryChanges
├── relationshipHighlights[]
├── timelineHighlights[]
└── grounding
```

Pros:

- preserves hierarchy
- preserves important explicit relationships
- preserves bounded chronology
- strongest model usability / preservation balance
- supports objective-specific emphasis without separate pipelines

Cons:

- requires design work on highlight policies

## Comparison

| Criterion | A | B | C |
|---|---|---|---|
| Information preservation | Medium | High | High |
| Deterministic ownership | High | High | High |
| Model usability | Medium-High | Medium | High |
| Grounding support | Medium | High | High |
| Temporal understanding | Medium | High | High |
| Relationship preservation | Low-Medium | High | High |
| Complexity | Low | High | Medium |
| Testability | High | Medium | High |
| Backward compatibility | High | Medium-Low | Medium-High |
| Future compatibility | Medium | High | High |

## Recommended Architecture

Recommended: **Candidate C — Hybrid Structured Sections + Relationship/Timeline Highlights**

Reason:

- best fit for current observed failure mode
- improves model usability without requiring graph-style consumption
- keeps deterministic ownership in Java
- preserves chronology and explicit relationships in bounded form
- supports objective-specific emphasis on top of one shared model

## Objective-Specific Strategy

Use one shared underlying context model plus deterministic emphasis per objective.

- Understand this project:
  - emphasize project purpose, current modules, validated knowledge, recent evolution
- Review the architecture:
  - emphasize architecture, ADR links, related source evidence, trusted architecture knowledge, timeline highlights
- Analyze engineering decisions:
  - emphasize decision context, ADRs, history, roadmap links, explicit grounding support
- Prepare README information:
  - emphasize project state, usage/install signals, validated knowledge

## Affected Contracts

Likely affected:

- selected-knowledge prompt projection shape
- backend -> AI engine `PromptRequest.selectedKnowledge`
- AI-engine prompt builders
- decision output/grounding contract if grounding is fixed
- selected-evidence historical projection readers if backward compatibility is needed

Likely unaffected:

- persistence schema itself, if JSONB snapshots remain versioned and backward-compatible

## Persistence Impact

- No persistence migration appears necessary
- Existing JSONB snapshot fields can carry new projection versions
- Backward-compatible projection readers may still be required

## ADR Recommendation

`NEW_ADR_REQUIRED`

Justification:

- the deterministic-to-AI context boundary is an architectural concern
- the change affects multi-service contracts and semantics
- existing ADRs govern evidence selection and lifecycle, but not this next-stage context architecture in enough detail

## Proposed Incremental Implementation Sequence

### Slice 1

Preserve explicit relationship carriers in projected selected knowledge.

### Slice 2

Introduce structured semantic sections.

### Slice 3

Add bounded timeline highlights.

### Slice 4

Add objective-specific deterministic emphasis.

### Slice 5

Fix engineering-decision grounding contract and mapping.

### Slice 6

Re-evaluate architecture-review delta-only behavior against the new composed context.

## Measurement Strategy

Re-run the same three objectives:

- Understand this project
- Review the architecture
- Analyze engineering decisions

Deterministic measures:

- relationship survival from selection to AI payload
- presence of timeline highlights
- grounding population for decisions
- presence of objective-relevant sections
- use of non-COMMIT_DIFF categories in proposals

Qualitative measures:

- project understanding depth
- architecture synthesis quality
- historical reasoning quality
- causal chain quality
- decision grounding quality

Proposal count alone is not sufficient.

## Risks

- over-structuring may increase prompt size
- too many projected relationships may add noise
- timeline projection may privilege recency incorrectly if not bounded well
- decision grounding changes may require broader contract updates

## Unresolved Questions

- Should architecture review remain delta-only?
- How much repository provenance should be restored into the AI-facing payload?
- Should prior insights remain thin summaries or keep stronger identity/rationale?
- Should relationship highlights be top-level only or also embedded in sections?
