# Story 0104 — Structured Semantic Sections for Analysis Context Composition

## Status

**IMPLEMENTATION_READY_FOR_HUMAN_REVIEW**

## Baseline

- Baseline SHA: `24d5bb2`
- Governing ADR: ADR-064 (accepted 2026-08-30)
- Governing Design: `docs/investigations/structured-semantic-sections-design.md` (corrective)
- Story 0103 regression baseline: `canonical=44, eligible=0, projected=0, COMMIT_DIFF=12`

## Objective

Improve Analysis context composition by deterministically organizing already-selected engineering knowledge into bounded semantic sections, without changing evidence selection, retrieval, canonical knowledge, or trusted knowledge.

Current problem:

```text
SelectedKnowledge
        ↓
PromptProjection
        ↓
large flat collections
        ↓
AI must reconstruct semantic organization itself
```

Target:

```text
SelectedKnowledge
        ↓
deterministic semantic composition
        ↓
canonical projected content
+
lightweight semantic index
        ↓
AI receives explicit engineering structure
```

Core invariant:

```text
ONE CONTENT REPRESENTATION
N SEMANTIC MEMBERSHIPS
```

Semantic composition organizes knowledge. It does NOT select additional knowledge.

## Authoritative Boundary

```text
SelectedKnowledge
        ↓
SemanticSectionComposer (new)
        ↓
List<PromptSemanticSection> (reference-based)
        ↓
SelectedKnowledgePromptProjectionService (modified)
        ↓
PromptProjection.semanticSections
        ↓
existing automatic shared serialization
        ↓
AI-facing selectedKnowledge
```

```text
SELECTION != COMPOSITION
```

`SemanticSectionComposer` consumes existing `SelectedKnowledge`. It must NOT:

- query repositories;
- retrieve additional evidence;
- change evidence budgets;
- expand selection;
- modify evidence ranking;
- modify category floors/ceilings;
- make selection relationship-aware;
- invoke AI for classification.

## Semantic Vocabulary (V1)

```text
PROJECT_STATE      — Current project identity, profile, and status
ARCHITECTURE       — Architectural facts, observations, and trusted architecture knowledge
DECISIONS          — Decision-relevant evidence (ADRs, decision types)
VALIDATED_KNOWLEDGE — Validated insights and engineering events
HISTORY            — Engineering evolution, commits, chronological evidence
REPOSITORY_CHANGES — Repository-derived evidence organized by category
HUMAN_CONTEXT      — Human-provided context with preserved authority
```

## Classification Policy

```text
CLASSIFICATION_POLICY = Explicit EnumSet.of() per section per type
NEW_TYPE_POLICY = New enum value → UNCLASSIFIED until explicit EnumSet update
UNCLASSIFIED_POLICY = Item stays in canonical array; absent from semantic sections; not lost
```

No string heuristics. No `name().contains()`. No regex. No AI classification.

## Multi-Section Membership

```text
SINGLE_SECTION_MEMBERSHIP = NO
MULTI_SECTION_MEMBERSHIP = YES — determined by explicit type metadata
FIRST_MATCH_WINS = REJECTED
```

Items may belong to `1..N` sections when current domain metadata explicitly justifies them. Content is never duplicated. Only references appear in multiple sections.

## Representation Model

```text
RECOMMENDED_REPRESENTATION = Model C — Lightweight References
itemType  — "FACT", "OBSERVATION", "INSIGHT", etc.
itemId    — UUID as string, or reference string
label     — short semantic label (type name or title)
```

Full-content duplication is rejected at +51% payload increase. Lightweight references add +7.5% base overhead, ~+11–12% with actual multi-membership.

## Identity Policy

All entity types have stable IDs for reference-based sections.

Required change: `InsightSnapshot` IDs must be preserved in PromptProjection (currently stripped). Minimal change to `projectInsight()`.

## Human Context Policy

```text
HUMAN_CONTEXT membership is MANDATORY for all HumanContextInputSnapshot items.
Additional memberships are ALLOWED based on ProjectHumanContextInputType.
```

## Validated Knowledge Policy

```text
VALIDATED_KNOWLEDGE membership is MANDATORY for all InsightSnapshot and EngineeringEventSnapshot items.
Additional memberships are ALLOWED based on InsightType.
```

## Deterministic Ordering

### Section ordering (fixed)

```text
PROJECT_STATE → ARCHITECTURE → DECISIONS → VALIDATED_KNOWLEDGE → HISTORY → REPOSITORY_CHANGES → HUMAN_CONTEXT
```

### Item ordering within sections

Deterministic, using existing timestamps and IDs. Same SelectedKnowledge input must produce byte/logically stable output.

## Empty Sections

```text
empty semantic section → omitted
```

## Relationship Highlights Regression

Story 0103 behavior must remain unchanged:

```text
canonical = 44
eligible = 0
projected = 0
COMMIT_DIFF = 12
```

Relationship Highlights remain a separate top-level composition concern.

## Scope

### IN SCOPE

- `SemanticSectionComposer.java` (create)
- `SemanticSection.java` (create — records + enum + classification maps)
- `SelectedKnowledgePromptProjectionService.java` (modify — add `semanticSections`, call composer, preserve insight IDs)
- `SemanticSectionComposerTest.java` (create)
- `SelectedKnowledgePromptProjectionServiceTest.java` (modify — add section propagation tests)

### EXPLICIT NON-SCOPE

```text
selection changes
retrieval changes
evidence budget changes
category floor/ceiling changes
relationship-aware selection
Relationship Policy A changes
Timeline Highlights
Grounding Support implementation
engineering-decision grounding fix
architecture-review proposal-generation fix
objective-specific emphasis
RAG
vector search
embeddings
graph database
knowledge graph
new persistence
database migrations
Python prompt redesign
frontend changes
agent behavior
AI semantic classification
new ontology/tagging framework
```

## Acceptance Criteria

### AC-1 — Dedicated composition

Given SelectedKnowledge,
when semantic context composition occurs,
then SemanticSectionComposer produces deterministic semantic sections without performing retrieval or selection.

### AC-2 — Explicit classification

Semantic membership uses explicit domain type mappings.

No string-heuristic or naming-convention inference is permitted.

### AC-3 — Multi-membership

An item with multiple explicitly justified semantic meanings appears by reference in every applicable section.

Its canonical content remains serialized once.

### AC-4 — No full-content duplication

Semantic sections contain lightweight references, not copies of canonical item content.

### AC-5 — UNCLASSIFIED

An unclassified item remains available in canonical projected knowledge but does not receive an invented semantic membership.

### AC-6 — Human Context authority

Every selected human-context item retains mandatory HUMAN_CONTEXT membership.

### AC-7 — Validated Knowledge

Every applicable validated insight retains VALIDATED_KNOWLEDGE membership, with deterministic secondary membership when justified.

### AC-8 — Identity

Every semantic reference is resolvable through a stable canonical identity/reference.

Insight IDs are preserved in PromptProjection as required.

### AC-9 — Determinism

Identical SelectedKnowledge produces identical semantic section membership and ordering.

### AC-10 — Empty sections

Sections with no references are omitted.

### AC-11 — Selection isolation

Story 0104 does not modify evidence selection, evidence budgets, category caps, retrieval, or SelectedKnowledge population.

### AC-12 — Relationship regression

Story 0103 Relationship Highlights behavior remains unchanged.

### AC-13 — Shared prompt propagation

`semanticSections` reaches the existing selected-knowledge JSON supplied to the AI through the established shared propagation path without Python-specific duplication.

### AC-14 — Payload bound

Runtime benchmark must measure actual payload delta.

Investigation estimates:

```text
+7.5% lightweight-reference base overhead
~+11–12% with actual multi-membership
```

The implementation benchmark must report the actual result.

### AC-15 — No invented knowledge

Every semantic reference corresponds to knowledge already present in SelectedKnowledge.

## Test Intent

Tests must prove behavior for at least:

- explicit FactType classification (all 56 types mapped)
- explicit ObservationType classification (all 12 types mapped)
- explicit InsightType classification (all 8 types mapped)
- RepositoryContextLayer classification (all 9 layers mapped)
- Human Context classification (all 5 types mapped)
- single membership
- multi-membership
- UNCLASSIFIED behavior
- mandatory HUMAN_CONTEXT membership
- mandatory VALIDATED_KNOWLEDGE membership
- no full-content duplication
- stable identity/reference creation
- Insight ID preservation
- deterministic section ordering
- deterministic item/reference ordering
- empty-section omission
- same item referenced by multiple sections without content duplication
- no selection expansion
- Relationship Highlights regression
- `semanticSections` presence in PromptProjection / `toMap()`
- existing selection/category-cap regressions

## Benchmark Requirements

AFTER benchmark on same three generic intents:

```text
describe-project-v1
architecture-overview-v1
analyze-engineering-decision-v1
```

Capture for each:

```text
selected evidence total
COMMIT_DIFF count/share
semantic section count
reference count per section
unique referenced items
total semantic memberships
multi-membership count
zero-membership / UNCLASSIFIED count
relationship highlight count
payload bytes
payload delta from baseline
proposal count
```

Human qualitative review using existing 0–3 rubric for:

```text
Understand this project
Review the architecture
Analyze engineering decisions
```

## Historical Benchmark References

```text
Story 0103:
describe-project-v1:           payload ≈ 68,040 bytes
architecture-overview-v1:      payload ≈ 62,136 bytes
analyze-engineering-decision-v1: payload ≈ 66,077 bytes
Relationship baseline: canonical=44, eligible=0, projected=0
Story 0098: COMMIT_DIFF=12 / 20%
```

## Governing Decisions

- ADR-064 (accepted 2026-08-30) — Hybrid Analysis Context Composition Architecture
- `docs/investigations/structured-semantic-sections-design.md` (corrective) — approved design

## Architecture Invariants

```text
Java owns deterministic engineering/context composition.
AI interprets composed context.
AI does not decide evidence selection.
AI does not decide semantic membership.
SelectedKnowledge remains the bounded selected input.
Semantic Sections are a projection/composition structure.
Semantic Sections are not trusted canonical knowledge.
Human Context remains identifiable.
Relationship Highlights remain independent.
Timeline Highlights remain future work.
Grounding remains separate.
No semantic composition decision may mutate trusted knowledge.
```

## Implementation Forecast

### CREATE

```text
SemanticSectionComposer.java
SemanticSection.java (records + enum + classification maps)
```

### MODIFY

```text
SelectedKnowledgePromptProjectionService.java
```

### Tests

```text
SemanticSectionComposerTest.java
SelectedKnowledgePromptProjectionServiceTest.java
```

## Story Lifecycle Status

- Story definition/design review: **READY_FOR_HUMAN_REVIEW**
- Implementation authorization: **NOT_AUTHORIZED**
- Branch creation: **NOT_AUTHORIZED**

`STORY_0104_READY_FOR_HUMAN_REVIEW`
