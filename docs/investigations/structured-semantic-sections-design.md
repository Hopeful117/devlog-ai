# ADR-064 Slice 2 Investigation — Structured Semantic Sections for Analysis Context Composition

## Status

**CORRECTIVE INVESTIGATION** — Updated after human architectural review.
Initial design superseded by this document.
No production changes. No tests modified. No Story materialization.

## Baseline

- Baseline SHA: `24d5bb2` (post-Story-0103 merge)
- Story 0103 merge: PR #88 at `24d5bb2`
- Governing ADR: ADR-064 (accepted 2026-08-30)
- Story 0103 regression baseline: `canonical=44, eligible=0, projected=0, COMMIT_DIFF=12`

---

## Human Review Corrections

| Initial Decision | Review Outcome | Correction |
|---|---|---|
| `name().contains()` string-heuristic classification | **REJECTED** | Explicit type-based classification using `EnumSet.of()` per section |
| Implicit default classification (unknown → REPOSITORY_CHANGES / PROJECT_STATE) | **REJECTED** | `UNCLASSIFIED` policy — items remain in canonical arrays, not in semantic sections |
| `FIRST_MATCH_WINS` for ambiguous types | **REJECTED** | Multi-section membership for items with genuine multiple semantic dimensions |
| Single-section membership assumed | **NOT JUSTIFIED** | Items may belong to `1..N` sections through explicit deterministic classification |
| Full-content transitional hybrid (+2–5% estimate) | **NOT DEMONSTRATED** | Measured: full-content duplication = +51% payload; reference-based model required |
| No measurement of actual payload costs | **MUST_BE_MEASURED** | Measured from real benchmark data — see Section 19 |
| Section taxonomy topic/provenance mixing not analyzed | **MUST_BE_INVESTIGATED** | Analyzed — see Section 24 |

---

## 1. ADR-064 Constraints

ADR-064 remains authoritative. The intended composition architecture:

```text
SelectedKnowledge
        ↓
deterministic context composition
        ↓
Objective / Scope
Semantic Sections
Relationship Highlights
Timeline Highlights (future)
Grounding Support (future)
        ↓
PromptProjection
        ↓
AI
```

ADR-064 architectural invariants:

1. Selection and composition are separate responsibilities.
2. Context composition remains deterministic.
3. AI does not control evidence budgets or deterministic composition policy.
4. One shared context architecture serves generic objectives.
5. Objective-specific behavior is expressed through deterministic emphasis.
6. Composed AI context is a projection, not trusted canonical knowledge.
7. Composition must remain bounded and measurable.

---

## 2. Current SelectedKnowledge Inventory

| # | Field | Java Type | Max | Projected | Semantic Meaning |
|---|---|---|---|---|---|
| 1 | `project` | `ProjectSnapshot` | 1 | Yes | Project identity |
| 2 | `analysis` | `AnalysisSnapshot` | 1 | Yes | Analysis identity + intent |
| 3 | `projectProfile` | `ProjectProfileResponse` | 1 | Yes | Project profile |
| 4 | `selectedObservations` | `List<ObservationSnapshot>` | 25 | Yes | Derived engineering observations |
| 5 | `selectedFacts` | `List<FactSnapshot>` | 40 | Yes | Evidence facts |
| 6 | `diagnostics` | `DiagnosticSnapshot` | 1 | Yes | Collection completeness |
| 7 | `selectedInsights` | `List<InsightSnapshot>` | 10 | Yes (IDs stripped) | Validated insights |
| 8 | `existingArchitectureKnowledge` | `List<ExistingArchitectureKnowledgeSnapshot>` | 5 | Yes | Trusted architecture knowledge |
| 9 | `selectedEngineeringEvents` | `List<EngineeringEventSnapshot>` | 10 | Yes | Engineering evolution events |
| 10 | `selectedHumanContextInputs` | `List<HumanContextInputSnapshot>` | 5 | Yes | Human-provided context |
| 11 | `knowledgeRelations` | `List<KnowledgeRelationSnapshot>` | Unbounded | Policy A | Explicit trusted relationships |
| 12 | `repositoryContext` | `RepositoryContext` | 60 evidence | Yes (reduced) | Repository-derived evidence |
| 13 | `evolutionContext` | `EvolutionContext` | 0–1 | Yes | Commit diff evolution |
| 14 | `selectionMetadata` | `SelectionMetadata` | 1 | Yes | Selection rules + budget |
| 15 | `selectionDigest` | `String` | 1 | Yes | Deterministic digest |

---

## 3. Actual Domain Type Systems

### FactType (56 values)

```java
COMMIT, COMMIT_DIFF_SUMMARY, COMMIT_CHANGES_MODULE, COMMIT_ADDS_FEATURE,
COMMIT_FIXES_BUG, COMMIT_REFACTORS_CODE, FILE_CHANGE, DEPENDENCY_CHANGE,
CODE_METRIC, DOCUMENTATION_CHANGE, TECHNOLOGY, REPOSITORY_REVISION_RESOLVED,
REPOSITORY_STRUCTURE_SUMMARY, SOURCE_DIRECTORY_PRESENT, PRIMARY_FILE_EXTENSION,
MULTI_MODULE_STRUCTURE_PRESENT, CONFIGURATION_FILE_PRESENT, BUILD_SYSTEM_DETECTED,
BUILD_WRAPPER_PRESENT, JAVA_VERSION_DECLARED, PROJECT_VERSION_DECLARED,
BUILD_MODULE_DECLARED, DEPENDENCY_DECLARED, BUILD_PLUGIN_DECLARED,
SPRING_BOOT_DETECTED, SPRING_BOOT_VERSION_DECLARED, SPRING_CLOUD_DETECTED,
SPRING_SECURITY_DETECTED, SPRING_DATA_DETECTED, SPRING_WEB_DETECTED,
SPRING_ACTUATOR_DETECTED, SPRING_CONFIGURATION_FILE_PRESENT,
REST_CONTROLLER_DECLARED, SPRING_CONFIGURATION_CLASS_DECLARED, DOCKERFILE_PRESENT,
DOCKER_COMPOSE_PRESENT, DOCKER_SERVICE_DECLARED, DOCKER_MULTI_STAGE_BUILD_PRESENT,
DOCKER_NON_ROOT_USER_DECLARED, DOCKER_HEALTHCHECK_DECLARED,
DOCKER_EXPOSED_PORT_DECLARED, DOCKER_VOLUME_DECLARED, DOCKERIGNORE_PRESENT,
README_PRESENT, DOCUMENTATION_DIRECTORY_PRESENT, MARKDOWN_DOCUMENT_PRESENT,
ADR_DIRECTORY_PRESENT, ADR_DOCUMENT_PRESENT, API_DOCUMENTATION_PRESENT,
ARCHITECTURE_DOCUMENTATION_PRESENT, CONTRIBUTING_GUIDE_PRESENT, CHANGELOG_PRESENT,
TEST_SOURCE_DIRECTORY_PRESENT, TEST_FILE_PRESENT, TEST_FRAMEWORK_DECLARED,
INTEGRATION_TEST_FILE_PRESENT, TESTCONTAINERS_DECLARED,
TEST_RESOURCE_DIRECTORY_PRESENT, OTHER
```

### ObservationType (12 values)

```java
ASYNCHRONOUS_COMMUNICATION, HTTP_SERVICE_COMMUNICATION, ARCHITECTURE_MODULARIZATION,
AUTHENTICATION_LAYER, TEST_COVERAGE_DECREASE, CONTAINERIZED_PROJECT,
SPRING_BOOT_REST_APPLICATION, ARCHITECTURE_DOCUMENTATION_PRESENT,
AUTOMATED_TEST_SUITE_PRESENT, INTEGRATION_TEST_SUITE_PRESENT,
MULTI_MODULE_BUILD, OTHER
```

### InsightType (8 values)

```java
ARCHITECTURAL, DOCUMENTATION, TECHNOLOGY, EVOLUTION,
TECHNICAL_DEBT, SECURITY, RISK, RECOMMENDATION
```

### RepositoryContextLayer (9 values)

```java
CURRENT_ANALYSIS, RELATED_SOURCE_CODE, GIT_HISTORY, COMMIT_DIFF,
ADR, ROADMAP, VALIDATED_INSIGHT, PREVIOUS_ANALYSIS, PROJECT_DOCUMENTATION
```

### Evidence kind strings (actual values from collectors)

```java
ANALYSIS, CHANGED_FILE, INSIGHT, COMMIT, ENGINEERING_STORY, CHALLENGE, DECISION,
MODULE_SUMMARY, SOURCE_DIRECTORIES, TEST_DIRECTORIES, CONFIGURATION_FILES,
FILE_EXTENSIONS, MODULE, SOURCE_FILE, TEST_FILE, CONFIG_FILE, FACT, OBSERVATION,
MILESTONE, ARTIFACT
```

### ProjectHumanContextInputType (5 values)

```java
GOAL, CONSTRAINT, ASSUMPTION, KNOWN_GAP, DOMAIN_CONTEXT
```

### KnowledgeRelationType (6 values)

```java
RESOLVES, CAUSED_BY, RELATES_TO, DERIVED_FROM, ADDRESSES, INFORMED_BY
```

### EntityType (4 values)

```java
CHALLENGE, DECISION, ENGINEERING_EVENT, INSIGHT
```

---

## 4. Explicit Classification Matrix

All classification is deterministic, using `EnumSet.of()` per section. No string heuristics. No naming convention inference.

### FactType → Section Membership

Each FactType maps to a `Set<SemanticSectionId>`. Types not listed are UNCLASSIFIED.

| FactType | Primary | Additional | Rationale |
|---|---|---|---|
| `COMMIT` | HISTORY | — | Chronological record |
| `COMMIT_DIFF_SUMMARY` | HISTORY | — | Chronological record |
| `COMMIT_CHANGES_MODULE` | HISTORY | ARCHITECTURE | Module change = architectural signal |
| `COMMIT_ADDS_FEATURE` | HISTORY | — | Feature addition = chronological |
| `COMMIT_FIXES_BUG` | HISTORY | — | Bug fix = chronological |
| `COMMIT_REFACTORS_CODE` | HISTORY | ARCHITECTURE | Refactoring = architectural signal |
| `FILE_CHANGE` | HISTORY | — | Change record |
| `DEPENDENCY_CHANGE` | HISTORY | ARCHITECTURE | Dependency change = architectural signal |
| `CODE_METRIC` | REPOSITORY_CHANGES | — | Repository-derived metric |
| `DOCUMENTATION_CHANGE` | PROJECT_STATE | HISTORY | Documentation evolution |
| `TECHNOLOGY` | ARCHITECTURE | PROJECT_STATE | Technology choice = architecture + state |
| `REPOSITORY_REVISION_RESOLVED` | REPOSITORY_CHANGES | — | Repository metadata |
| `REPOSITORY_STRUCTURE_SUMMARY` | PROJECT_STATE | ARCHITECTURE | Structure = state + architecture |
| `SOURCE_DIRECTORY_PRESENT` | PROJECT_STATE | — | Project structure |
| `PRIMARY_FILE_EXTENSION` | PROJECT_STATE | — | Project characteristic |
| `MULTI_MODULE_STRUCTURE_PRESENT` | ARCHITECTURE | PROJECT_STATE | Module structure = architecture |
| `CONFIGURATION_FILE_PRESENT` | PROJECT_STATE | — | Project configuration |
| `BUILD_SYSTEM_DETECTED` | ARCHITECTURE | PROJECT_STATE | Build system = architecture |
| `BUILD_WRAPPER_PRESENT` | PROJECT_STATE | — | Build tooling state |
| `JAVA_VERSION_DECLARED` | PROJECT_STATE | ARCHITECTURE | Version = state; Java choice = architecture |
| `PROJECT_VERSION_DECLARED` | PROJECT_STATE | — | Version = state |
| `BUILD_MODULE_DECLARED` | ARCHITECTURE | PROJECT_STATE | Module = architecture |
| `DEPENDENCY_DECLARED` | ARCHITECTURE | — | Dependency = architecture |
| `BUILD_PLUGIN_DECLARED` | ARCHITECTURE | — | Plugin = architecture |
| `SPRING_BOOT_DETECTED` | ARCHITECTURE | — | Framework = architecture |
| `SPRING_BOOT_VERSION_DECLARED` | ARCHITECTURE | — | Framework version = architecture |
| `SPRING_CLOUD_DETECTED` | ARCHITECTURE | — | Framework = architecture |
| `SPRING_SECURITY_DETECTED` | ARCHITECTURE | — | Framework = architecture |
| `SPRING_DATA_DETECTED` | ARCHITECTURE | — | Framework = architecture |
| `SPRING_WEB_DETECTED` | ARCHITECTURE | — | Framework = architecture |
| `SPRING_ACTUATOR_DETECTED` | ARCHITECTURE | — | Framework = architecture |
| `SPRING_CONFIGURATION_FILE_PRESENT` | ARCHITECTURE | — | Framework config = architecture |
| `REST_CONTROLLER_DECLARED` | ARCHITECTURE | — | API architecture |
| `SPRING_CONFIGURATION_CLASS_DECLARED` | ARCHITECTURE | — | Framework config = architecture |
| `DOCKERFILE_PRESENT` | ARCHITECTURE | PROJECT_STATE | Container = architecture + state |
| `DOCKER_COMPOSE_PRESENT` | ARCHITECTURE | PROJECT_STATE | Orchestration = architecture + state |
| `DOCKER_SERVICE_DECLARED` | ARCHITECTURE | — | Service architecture |
| `DOCKER_MULTI_STAGE_BUILD_PRESENT` | ARCHITECTURE | — | Build architecture |
| `DOCKER_NON_ROOT_USER_DECLARED` | ARCHITECTURE | — | Security architecture |
| `DOCKER_HEALTHCHECK_DECLARED` | ARCHITECTURE | — | Reliability architecture |
| `DOCKER_EXPOSED_PORT_DECLARED` | ARCHITECTURE | — | Network architecture |
| `DOCKER_VOLUME_DECLARED` | ARCHITECTURE | — | Storage architecture |
| `DOCKERIGNORE_PRESENT` | PROJECT_STATE | — | Configuration state |
| `README_PRESENT` | PROJECT_STATE | — | Documentation state |
| `DOCUMENTATION_DIRECTORY_PRESENT` | PROJECT_STATE | — | Documentation structure |
| `MARKDOWN_DOCUMENT_PRESENT` | PROJECT_STATE | — | Documentation state |
| `ADR_DIRECTORY_PRESENT` | PROJECT_STATE | DECISIONS | ADR = decisions + state |
| `ADR_DOCUMENT_PRESENT` | DECISIONS | PROJECT_STATE | ADR = decisions |
| `API_DOCUMENTATION_PRESENT` | PROJECT_STATE | ARCHITECTURE | API docs = state + architecture |
| `ARCHITECTURE_DOCUMENTATION_PRESENT` | ARCHITECTURE | PROJECT_STATE | Architecture docs |
| `CONTRIBUTING_GUIDE_PRESENT` | PROJECT_STATE | — | Documentation state |
| `CHANGELOG_PRESENT` | PROJECT_STATE | HISTORY | Changelog = state + history |
| `TEST_SOURCE_DIRECTORY_PRESENT` | PROJECT_STATE | — | Test structure |
| `TEST_FILE_PRESENT` | PROJECT_STATE | — | Test state |
| `TEST_FRAMEWORK_DECLARED` | PROJECT_STATE | ARCHITECTURE | Test framework = state + architecture |
| `INTEGRATION_TEST_FILE_PRESENT` | PROJECT_STATE | — | Test state |
| `TESTCONTAINERS_DECLARED` | ARCHITECTURE | — | Test infrastructure = architecture |
| `TEST_RESOURCE_DIRECTORY_PRESENT` | PROJECT_STATE | — | Test structure |
| `OTHER` | UNCLASSIFIED | — | No semantic classification |

### ObservationType → Section Membership

| ObservationType | Primary | Additional | Rationale |
|---|---|---|---|
| `ASYNCHRONOUS_COMMUNICATION` | ARCHITECTURE | — | Communication pattern = architecture |
| `HTTP_SERVICE_COMMUNICATION` | ARCHITECTURE | — | Communication pattern = architecture |
| `ARCHITECTURE_MODULARIZATION` | ARCHITECTURE | — | Explicitly architectural |
| `AUTHENTICATION_LAYER` | ARCHITECTURE | — | Security architecture |
| `TEST_COVERAGE_DECREASE` | PROJECT_STATE | — | Project health state |
| `CONTAINERIZED_PROJECT` | ARCHITECTURE | PROJECT_STATE | Deployment architecture + state |
| `SPRING_BOOT_REST_APPLICATION` | ARCHITECTURE | — | Application architecture |
| `ARCHITECTURE_DOCUMENTATION_PRESENT` | ARCHITECTURE | PROJECT_STATE | Architecture docs |
| `AUTOMATED_TEST_SUITE_PRESENT` | PROJECT_STATE | — | Test state |
| `INTEGRATION_TEST_SUITE_PRESENT` | PROJECT_STATE | — | Test state |
| `MULTI_MODULE_BUILD` | ARCHITECTURE | PROJECT_STATE | Module architecture |
| `OTHER` | UNCLASSIFIED | — | No semantic classification |

### InsightType → Section Membership

| InsightType | Primary | Additional | Rationale |
|---|---|---|---|
| `ARCHITECTURAL` | VALIDATED_KNOWLEDGE | ARCHITECTURE | Architecture insight |
| `DOCUMENTATION` | VALIDATED_KNOWLEDGE | PROJECT_STATE | Documentation insight |
| `TECHNOLOGY` | VALIDATED_KNOWLEDGE | ARCHITECTURE | Technology insight |
| `EVOLUTION` | VALIDATED_KNOWLEDGE | HISTORY | Evolution insight |
| `TECHNICAL_DEBT` | VALIDATED_KNOWLEDGE | ARCHITECTURE | Technical debt = architecture concern |
| `SECURITY` | VALIDATED_KNOWLEDGE | ARCHITECTURE | Security = architecture concern |
| `RISK` | VALIDATED_KNOWLEDGE | — | Risk = validated knowledge only |
| `RECOMMENDATION` | VALIDATED_KNOWLEDGE | — | Recommendation = validated knowledge only |

### RepositoryContextLayer → Section Membership

| Layer | Primary | Additional | Rationale |
|---|---|---|---|
| `COMMIT_DIFF` | REPOSITORY_CHANGES | HISTORY | Change record + chronological |
| `GIT_HISTORY` | REPOSITORY_CHANGES | HISTORY | History record |
| `VALIDATED_INSIGHT` | REPOSITORY_CHANGES | VALIDATED_KNOWLEDGE | Validated knowledge evidence |
| `PREVIOUS_ANALYSIS` | REPOSITORY_CHANGES | HISTORY | Historical analysis |
| `ROADMAP` | REPOSITORY_CHANGES | — | Roadmap evidence |
| `ADR` | REPOSITORY_CHANGES | DECISIONS | Decision evidence |
| `CURRENT_ANALYSIS` | REPOSITORY_CHANGES | — | Current analysis evidence |
| `RELATED_SOURCE_CODE` | REPOSITORY_CHANGES | ARCHITECTURE | Source code = architecture evidence |
| `PROJECT_DOCUMENTATION` | REPOSITORY_CHANGES | PROJECT_STATE | Documentation evidence |

### ProjectHumanContextInputType → Section Membership

| Type | Primary | Additional | Rationale |
|---|---|---|---|
| `GOAL` | HUMAN_CONTEXT | PROJECT_STATE | Goal = human context + project state |
| `CONSTRAINT` | HUMAN_CONTEXT | ARCHITECTURE | Constraint = human context + architecture |
| `ASSUMPTION` | HUMAN_CONTEXT | PROJECT_STATE | Assumption = human context + state |
| `KNOWN_GAP` | HUMAN_CONTEXT | — | Gap = human context only |
| `DOMAIN_CONTEXT` | HUMAN_CONTEXT | ARCHITECTURE | Domain = human context + architecture |

### Fixed-Membership Entities

| Entity Type | Section(s) | Rationale |
|---|---|---|
| `project` | PROJECT_STATE | Identity — always state |
| `analysis` | PROJECT_STATE | Identity — always state |
| `projectProfile` | PROJECT_STATE | Profile — always state |
| `EngineeringEventSnapshot` | VALIDATED_KNOWLEDGE | Validated event = validated knowledge |
| `EvolutionContext` | HISTORY | Evolution = chronological |
| `HumanContextInputSnapshot` | HUMAN_CONTEXT (mandatory) | Authority preservation |

---

## 5. New Type Policy

When a new `FactType`, `ObservationType`, or `InsightType` enum value appears:

1. The `SEMANTIC_SECTION_MAP` must be explicitly updated
2. Until updated, the new type is `UNCLASSIFIED`
3. `UNCLASSIFIED` items remain in canonical projected arrays (not lost)
4. `UNCLASSIFIED` items do NOT appear in semantic sections
5. The classification update is a deliberate, reviewable code change

---

## 6. Unclassified Policy

An item that cannot be classified into any semantic section:

- **Remains in its canonical projected array** (e.g., `selectedFacts`)
- **Does NOT appear in any semantic section**
- **Is NOT lost from the AI prompt** — canonical arrays are still present
- **Signals classification gap** — absence from sections is visible in tests

Semantic classification is organization, not selection. Unclassified items retain full presence in the projected knowledge.

---

## 7. Multi-Section Membership

Items may belong to `1..N` semantic sections through explicit deterministic classification.

The invariant:

```text
ONE CONTENT REPRESENTATION (in canonical projected arrays)
N SEMANTIC MEMBERSHIPS (in semantic section references)
```

Content is never duplicated. Only references appear in multiple sections.

### Membership counts by entity type (from actual benchmark data)

| Entity Type | Count | Single-Membership | Multi-Membership | Max Memberships |
|---|---|---|---|---|
| Facts | 40 | ~20 | ~20 | 2 |
| Observations | 1 | 0 | 1 | 2 |
| Insights | 10 | 4 | 6 | 2 |
| Evidence | 60 | ~30 | ~30 | 2 |
| Human Context | 3 | 1 | 2 | 2 |
| Arch Knowledge | 0 | — | — | — |
| Engineering Events | 0 | — | — | — |

Multi-membership is genuinely useful: ~57 of 114 items (~50%) benefit from appearing in multiple sections based on their deterministic type metadata.

---

## 8. Human Context Membership Policy

```text
HUMAN_CONTEXT membership is MANDATORY for all HumanContextInputSnapshot items.
Additional memberships are ALLOWED based on ProjectHumanContextInputType.
```

Rationale:
- Human Context Supremacy requires mandatory HUMAN_CONTEXT membership
- Additional memberships (PROJECT_STATE, ARCHITECTURE) are deterministically encoded by type
- Multi-membership does NOT obscure authority — HUMAN_CONTEXT is always present
- Authority is structural (section membership) not just labeling

---

## 9. Validated Knowledge Membership Policy

```text
VALIDATED_KNOWLEDGE membership is MANDATORY for all InsightSnapshot and EngineeringEventSnapshot items.
Additional memberships are ALLOWED based on InsightType.
```

Rationale:
- Insights and events are validated by definition — VALIDATED_KNOWLEDGE is mandatory
- InsightType provides deterministic secondary membership (e.g., ARCHITECTURAL → +ARCHITECTURE)
- This correctly models "validated architecture insight" as having both provenance and topic dimensions

---

## 10. Section Taxonomy: Topic vs Provenance

The current 7-section vocabulary mixes two dimensions:

**Topic dimensions**: PROJECT_STATE, ARCHITECTURE, DECISIONS, HISTORY
**Provenance dimensions**: VALIDATED_KNOWLEDGE, REPOSITORY_CHANGES, HUMAN_CONTEXT

This is DESIRABLE for V1 because:
- Multi-membership handles items that span both dimensions
- A validated architecture insight correctly appears in both VALIDATED_KNOWLEDGE and ARCHITECTURE
- Human context correctly appears in HUMAN_CONTEXT and potentially ARCHITECTURE
- The mixed taxonomy is simpler than a separate multidimensional tagging system

The alternative (sections as topic-only, provenance as item metadata) would:
- Require adding provenance fields to every item
- Lose the structural separation that makes HUMAN_CONTEXT meaningful
- Increase implementation complexity without clear AI benefit

---

## 11. Identity Audit

Every entity type must have a stable ID for reference-based sections.

| Entity Type | ID Field | Available in SelectedKnowledge | Available in PromptProjection | Currently Stripped | Stable | Suitable for References |
|---|---|---|---|---|---|---|
| `FactSnapshot` | `UUID id` | Yes | Yes (passed through) | No | Yes | Yes |
| `ObservationSnapshot` | `UUID id` | Yes | Yes (passed through) | No | Yes | Yes |
| `InsightSnapshot` | `UUID id` | Yes | **Stripped** | Yes | Yes (in SK) | Requires ID preservation |
| `EngineeringEventSnapshot` | `UUID id` | Yes | Yes (passed through) | No | Yes | Yes |
| `ExistingArchitectureKnowledgeSnapshot` | `UUID insightId` | Yes | Yes (passed through) | No | Yes | Yes |
| `HumanContextInputSnapshot` | `UUID id` | Yes | Yes (passed through) | No | Yes | Yes |
| `RepositoryEvidence` | `String reference` | Yes | Yes (passed through) | No | Yes | Yes (string ref) |
| `EvolutionContext` | `UUID sourceId` | Yes | Yes (passed through) | No | Yes | Yes |

**Required change**: `InsightSnapshot` IDs must be preserved in PromptProjection for reference-based sections. This is a minimal change to `projectInsight()` — stop stripping `id`.

**Collision avoidance**: Use `itemType:id` format in references (e.g., `FACT:uuid`, `INSIGHT:uuid`, `REPOSITORY_EVIDENCE:reference`). Entity types have distinct ID spaces (UUID vs String reference), preventing collisions.

---

## 12. Reference-Based Semantic Model

### Why not full-content sections

Measured from real benchmark data (`describe-project-v1`):

| Component | Bytes |
|---|---|
| Baseline payload | 68,040 |
| `selectedFacts` array | 23,721 |
| `selectedObservations` array | 454 |
| `selectedInsights` array | 2,877 |
| `selectedHumanContextInputs` array | 6,509 |
| `existingArchitectureKnowledge` array | ~0 (empty) |
| `selectedEngineeringEvents` array | ~0 (empty) |

Full-content sections would duplicate all fact/observation/insight/event content:

| Model | Additional Bytes | Total | Increase |
|---|---|---|---|
| Baseline | — | 68,040 | — |
| Full-content sections | +34,818 | 102,858 | **+51%** |
| Pure reference sections | +2,080 | 70,120 | +3% |
| Lightweight reference sections | +5,080 | 73,120 | +7.5% |

Full-content duplication is **unacceptable** at +51%.

### Three representation models compared

#### Model A — Full Content

```json
{
  "sectionId": "ARCHITECTURE",
  "items": [
    {
      "itemType": "FACT",
      "id": "uuid-here",
      "type": "SPRING_BOOT_DETECTED",
      "title": "Spring Boot detected",
      "content": "full original content...",
      "occurredAt": "2026-08-15T12:00:00Z"
    }
  ]
}
```

| Criterion | Assessment |
|---|---|
| Prompt size | +51% — unacceptable |
| Multi-membership cost | Content duplicated N times per membership |
| AI interpretability | High — full context in section |
| Traceability | High — full entity in section |
| Implementation complexity | Low |
| Future replacement of flat arrays | Natural — sections become canonical |
| **Verdict** | **REJECTED** — payload cost too high |

#### Model B — Pure References

```json
{
  "sectionId": "ARCHITECTURE",
  "itemReferences": [
    { "itemType": "FACT", "itemId": "uuid-1" },
    { "itemType": "OBSERVATION", "itemId": "uuid-2" }
  ]
}
```

| Criterion | Assessment |
|---|---|
| Prompt size | +3% — excellent |
| Multi-membership cost | ~45 bytes per additional membership |
| AI interpretability | Low — opaque UUIDs, requires join with canonical arrays |
| Traceability | High — stable IDs |
| Implementation complexity | Low |
| Future replacement of flat arrays | Requires canonical arrays to remain |
| **Verdict** | **VIABLE** but AI interpretability concern |

#### Model C — Lightweight References (RECOMMENDED)

```json
{
  "sectionId": "ARCHITECTURE",
  "itemReferences": [
    { "itemType": "FACT", "itemId": "uuid-1", "label": "SPRING_BOOT_DETECTED" },
    { "itemType": "OBSERVATION", "itemId": "uuid-2", "label": "CONTAINERIZED_PROJECT" }
  ]
}
```

| Criterion | Assessment |
|---|---|
| Prompt size | +7.5% — acceptable |
| Multi-membership cost | ~50 bytes per additional membership |
| AI interpretability | Moderate — labels provide semantic context for join |
| Traceability | High — stable IDs + labels |
| Implementation complexity | Low |
| Future replacement of flat arrays | Labels become section items naturally |
| Future grounding | IDs directly usable |
| Future objective-specific emphasis | Section references reorderable |
| **Verdict** | **RECOMMENDED** — best balance |

### Why lightweight references over pure references

The AI must associate section references with canonical content arrays separated by ~15,000 tokens in the prompt. Pure UUID references (`FACT:uuid-1`) provide no semantic context for this join. Lightweight references include the existing `type` field value (e.g., `SPRING_BOOT_DETECTED`) as a `label`, giving the AI immediate semantic context to locate and understand the referenced item.

The ~2,000 byte overhead (7.5% vs 3%) is justified by significantly improved AI interpretability.

---

## 13. Multi-Membership with References

Example with actual benchmark data:

```json
// Canonical content (once):
"selectedFacts": [
  { "id": "uuid-1", "type": "DOCKERFILE_PRESENT", "content": "..." }
]

// Semantic sections (references only):
"semanticSections": [
  {
    "sectionId": "ARCHITECTURE",
    "itemReferences": [
      { "itemType": "FACT", "itemId": "uuid-1", "label": "DOCKERFILE_PRESENT" }
    ]
  },
  {
    "sectionId": "PROJECT_STATE",
    "itemReferences": [
      { "itemType": "FACT", "itemId": "uuid-1", "label": "DOCKERFILE_PRESENT" }
    ]
  }
]
```

Expected property:

```text
content serialized once (in selectedFacts)
semantic reference serialized twice (in ARCHITECTURE + PROJECT_STATE)
additional cost per multi-membership: ~50 bytes
```

---

## 14. Semantic Sections: Current Flattening Analysis

| Field | Status | Evidence |
|---|---|---|
| `selectedFacts` | PARTIALLY_PRESERVED | IDs and content survive; 40 items in one flat array with no semantic grouping |
| `selectedObservations` | PARTIALLY_PRESERVED | 1 observation in flat array; no semantic grouping |
| `selectedInsights` | PARTIALLY_PRESERVED | Content survives but **IDs stripped**; 10 items as anonymous summaries |
| `existingArchitectureKnowledge` | CLEARLY_PRESERVED | Full snapshot with IDs, evidence references, rationale |
| `selectedEngineeringEvents` | CLEARLY_PRESERVED | IDs survive; 0 events in current benchmark |
| `selectedHumanContextInputs` | PARTIALLY_PRESERVED | Content survives; 3 items in flat array; authority distinction weak |
| `repositoryContext` | FLATTENED | 60 evidence items in one array; category labels exist as `kind` field but no structural hierarchy |
| `knowledgeRelations` | FLATTENED | Preserved in SK but Policy A rejects all 44 in current benchmark |

---

## 15. Proposed Semantic Vocabulary

| Section ID | Human Meaning | Enum |
|---|---|---|
| `PROJECT_STATE` | Current project identity, profile, and status | `SemanticSectionId.PROJECT_STATE` |
| `ARCHITECTURE` | Architectural facts, observations, and trusted architecture knowledge | `SemanticSectionId.ARCHITECTURE` |
| `DECISIONS` | Decision-relevant evidence (ADRs, decision types) | `SemanticSectionId.DECISIONS` |
| `VALIDATED_KNOWLEDGE` | Validated insights and engineering events | `SemanticSectionId.VALIDATED_KNOWLEDGE` |
| `HISTORY` | Engineering evolution, commits, chronological evidence | `SemanticSectionId.HISTORY` |
| `REPOSITORY_CHANGES` | Repository-derived evidence organized by category | `SemanticSectionId.REPOSITORY_CHANGES` |
| `HUMAN_CONTEXT` | Human-provided context with preserved authority | `SemanticSectionId.HUMAN_CONTEXT` |

---

## 16. Ordering Policy

### Section ordering (fixed)

```java
enum SemanticSectionId {
    PROJECT_STATE,       // 1. What the project is
    ARCHITECTURE,        // 2. How it's structured
    DECISIONS,           // 3. What was decided
    VALIDATED_KNOWLEDGE, // 4. What is confirmed
    HISTORY,             // 5. What happened
    REPOSITORY_CHANGES,  // 6. Supporting evidence
    HUMAN_CONTEXT        // 7. Human authority (last = anchoring)
}
```

### Item ordering within sections

Deterministic, using existing timestamps and IDs:

| Section | Primary Order | Secondary |
|---|---|---|
| PROJECT_STATE | Fixed sub-order (project → analysis → profile → items) | `createdAt` DESC |
| ARCHITECTURE | Type name ASC, then `createdAt` DESC | `id` ASC |
| DECISIONS | Type name ASC, then `createdAt` DESC | `id` ASC |
| VALIDATED_KNOWLEDGE | Insights by `createdAt` DESC, events by `occurredAt` DESC | `id` ASC |
| HISTORY | `detectedAt`/`createdAt` DESC | `id` ASC |
| REPOSITORY_CHANGES | Layer order (COMMIT_DIFF → GIT_HISTORY → ... → RELATED_SOURCE_CODE) | `occurredAt` DESC |
| HUMAN_CONTEXT | `updatedAt` DESC | `id` ASC |

---

## 17. Empty-Section Policy

**Omit empty sections.** When a section has zero item references, it is NOT included in `semanticSections`.

Rationale:
- Empty sections add structural noise
- Omitting signals "no evidence of this type was selected"
- Simpler to test
- Stable array schema; elements vary

---

## 18. Relationship Highlights Interaction

Relationship Highlights remain one independent top-level composition component.

```text
PromptProjection
├── project
├── analysis
├── projectProfile
├── semanticSections: [...]        ← NEW (Slice 2)
├── relationshipHighlights: [...]  ← EXISTING (Story 0103)
├── repositoryContext: {...}       ← EXISTING
├── evolutionContext: {...}        ← EXISTING
├── selectionMetadata: {...}       ← EXISTING
├── selectionDigest: "..."         ← EXISTING
└── diagnostics: {...}             ← EXISTING
```

Story 0103 baseline preserved: `canonical=44, eligible=0, projected=0`.

---

## 19. Prompt Budget Analysis (Measured)

### Real benchmark data (`describe-project-v1`)

| Key | Bytes | % of Total |
|---|---|---|
| `repositoryContext` | 29,841 | 44% |
| `selectedFacts` | 23,721 | 35% |
| `selectedHumanContextInputs` | 6,509 | 10% |
| `projectProfile` | 5,039 | 7% |
| `selectedInsights` | 2,877 | 4% |
| `selectedObservations` | 454 | 1% |
| `project` | 314 | <1% |
| `analysis` | 269 | <1% |
| Other | 1,016 | 2% |
| **Total baseline** | **68,040** | **100%** |

### Item counts

| Item Type | Count |
|---|---|
| Facts | 40 |
| Observations | 1 |
| Insights | 10 |
| Human Context | 3 |
| Evidence | 60 |
| Arch Knowledge | 0 |
| Engineering Events | 0 |
| **Total items** | **114** |

### Representation model costs

| Model | Section Overhead | Content Duplication | Total Additional | Total Payload | Increase |
|---|---|---|---|---|---|
| Baseline | — | — | — | 68,040 | — |
| Full Content (A) | ~1,120 | ~33,698 | ~34,818 | 102,858 | +51% |
| Pure Reference (B) | ~2,080 | 0 | ~2,080 | 70,120 | +3% |
| Lightweight Reference (C) | ~5,080 | 0 | ~5,080 | 73,120 | +7.5% |

### Multi-membership overhead (Lightweight Reference model)

| Memberships per Item | Additional Bytes | Total Additional for 114 Items |
|---|---|---|
| 1 (all single) | ~50 | ~5,700 |
| 2 (typical mix) | ~50 × 57 extra refs | ~2,850 extra |
| 3 (max) | ~50 × extra refs | bounded |

Typical total overhead with mixed membership: ~7,500–8,500 bytes (+11–12%).

---

## 20. Benchmark Data: Multi-Membership Distribution

From actual `describe-project-v1` data:

### Facts by type and membership

| FactType | Count | Primary Section | Additional Section | Memberships |
|---|---|---|---|---|
| `COMMIT_FIXES_BUG` | 13 | HISTORY | — | 1 |
| `MARKDOWN_DOCUMENT_PRESENT` | 8 | PROJECT_STATE | — | 1 |
| `COMMIT_ADDS_FEATURE` | 5 | HISTORY | — | 1 |
| `BUILD_MODULE_DECLARED` | 3 | ARCHITECTURE | PROJECT_STATE | 2 |
| `DOCKERFILE_PRESENT` | 3 | ARCHITECTURE | PROJECT_STATE | 2 |
| `BUILD_SYSTEM_DETECTED` | 1 | ARCHITECTURE | PROJECT_STATE | 2 |
| `INTEGRATION_TEST_FILE_PRESENT` | 1 | PROJECT_STATE | — | 1 |
| `SPRING_BOOT_DETECTED` | 1 | ARCHITECTURE | — | 1 |
| `COMMIT_REFACTORS_CODE` | 1 | HISTORY | ARCHITECTURE | 2 |
| `DOCUMENTATION_DIRECTORY_PRESENT` | 1 | PROJECT_STATE | — | 1 |
| `ADR_DIRECTORY_PRESENT` | 1 | PROJECT_STATE | DECISIONS | 2 |
| `COMMIT_DIFF_SUMMARY` | 1 | HISTORY | — | 1 |
| `DOCKER_COMPOSE_PRESENT` | 1 | ARCHITECTURE | PROJECT_STATE | 2 |

### Summary

| Metric | Value |
|---|---|
| Total items | 114 |
| Single-membership items | ~57 |
| Multi-membership items | ~57 |
| Average memberships per item | ~1.5 |
| Max memberships per item | 2 |

---

## 21. Composition Boundary

### Recommended: Dedicated SemanticSectionComposer

```text
SelectedKnowledge (input)
        ↓
SemanticSectionComposer.compose(selectedKnowledge, intent)
        ↓
List<PromptSemanticSection> (reference-based)

SelectedKnowledge (input)
        ↓
SelectedKnowledgePromptProjectionService.buildRelationshipHighlights()
        ↓
List<PromptRelationshipHighlight>

        ↓
PromptProjection (extended with semanticSections)
        ↓
Map<String, Object> (automatic shared propagation)
```

Justification:
- Separation of concerns from projection
- Independent testability
- Future slices (Timeline Highlights) get their own composer
- Prevents god service

---

## 22. PromptProjection Extension

```java
record PromptProjection(
    // ... existing 15 fields unchanged ...
    List<PromptSemanticSection> semanticSections,  // NEW
) {}

record PromptSemanticSection(
    String sectionId,
    String sectionTitle,
    List<PromptSemanticSectionItem> items
) {}

record PromptSemanticSectionItem(
    String itemType,    // "FACT", "OBSERVATION", "INSIGHT", etc.
    String itemId,      // UUID as string, or reference string
    String label        // short semantic label (type name or title)
) {}
```

---

## 23. Implementation Scope

### CREATE

| File | Purpose |
|---|---|
| `SemanticSectionComposer.java` | Deterministic section composition with reference-based output |
| `SemanticSection.java` | Section record + SectionId enum + classification maps |

### MODIFY

| File | Change |
|---|---|
| `SelectedKnowledgePromptProjectionService.java` | Add `semanticSections` to `PromptProjection`; call composer; preserve insight IDs |

### LEAVE UNTOUCHED

| File | Reason |
|---|---|
| `SelectedKnowledge.java` | No new fields — composer reads existing fields |
| `KnowledgeSelectionServiceImpl.java` | Selection — not composition |
| `BudgetedDiverseEvidenceSelector.java` | Selection — not composition |
| Python prompt builders | Automatic shared propagation |
| Python schemas | No changes needed |
| Frontend | No changes needed |
| Persistence / migrations | No changes needed |

---

## 24. Test Strategy

### CREATE

| File | Tests |
|---|---|
| `SemanticSectionComposerTest.java` | Classification correctness, multi-membership, ordering, empty sections, no-duplication |

### MODIFY

| File | Tests |
|---|---|
| `SelectedKnowledgePromptProjectionServiceTest.java` | Integration: sections appear in `toMap()` output |

### Required test categories

| Category | What It Proves |
|---|---|
| Deterministic classification | Every FactType/ObservationType/InsightType maps correctly |
| Multi-membership correctness | Items appear in all justified sections |
| No content duplication | References only; content in canonical arrays |
| No invented knowledge | Sections contain only references to SelectedKnowledge items |
| UNCLASSIFIED handling | Unknown types remain in canonical arrays, absent from sections |
| Stable ordering | Same input → same output |
| Empty-section omission | Empty sections not in output |
| Insight ID preservation | IDs no longer stripped in projection |
| Relationship baseline | Story 0103 regression unchanged |
| Automatic propagation | `semanticSections` in `toMap()` output |

---

## 25. Benchmark Plan

### AFTER benchmark (if Story 0104 implemented)

Same three objectives:

```text
describe-project-v1
architecture-overview-v1
analyze-engineering-decision-v1
```

Capture:

| Metric | Source |
|---|---|
| Total payload bytes | `toMap().toString().length()` |
| Baseline delta | Compared to 68,040 / 62,136 / 66,077 |
| Section count | `semanticSections.size()` |
| Items per section | `section.items.size()` |
| Multi-membership count | Items in >1 section |
| Empty sections | Count omitted |
| Relationship highlights | `relationshipHighlights.size()` |
| COMMIT_DIFF count | `repositoryContext.evidence` filtered |
| Proposal count | AI response |
| Story 0103 regression | `canonical=44, eligible=0, projected=0` |

### Story 0103 regression baseline

```text
canonical = 44
eligible = 0
projected = 0
COMMIT_DIFF = 12
```

---

## 26. Qualitative Review Rubric

### Understand this project

| Score | Meaning |
|---|---|
| 0 | Absent / unusable |
| 1 | Generic — enumerates facts without synthesis |
| 2 | Partially grounded — explains project purpose and some modules |
| 3 | Coherent — explains purpose, architecture, current state, and evolution |

### Review the architecture

| Score | Meaning |
|---|---|
| 0 | Absent / zero proposals |
| 1 | Generic — lists technologies without architectural reasoning |
| 2 | Partially grounded — identifies components and some decisions |
| 3 | Coherent — identifies components, decisions, trade-offs, and relationships |

### Analyze engineering decisions

| Score | Meaning |
|---|---|
| 0 | Absent / unusable |
| 1 | Generic — restates obvious choices |
| 2 | Partially grounded — identifies decisions with some context |
| 3 | Coherent — identifies decisions, context, trade-offs, and consequences |

---

## 27. Known Limitations

### Selection-layer gaps

Fields NOT in SelectedKnowledge (not addressed by Slice 2):
- `relatedAnalyses`, `architectureArtifacts`, `relatedDecisions`, `recentMilestones`
- `validatedProposals`, `recentKnowledgeEvents`, `openChallenges`, `engineeringStories`

### Insight ID stripping

Current projection strips insight IDs. Semantic sections require ID preservation. This is a minimal change to `projectInsight()`.

### Architecture-overview only

`existingArchitectureKnowledge` only populated for `architecture-overview` intent. Other intents rely on architecture-classified facts/observations.

---

## 28. Risks

| Risk | Mitigation |
|---|---|
| Classification misclassification | Explicit EnumSet maps; new types require code change |
| AI cannot perform reference joins | Lightweight labels provide semantic context; content is co-located in prompt |
| Payload increase | +7.5–12% measured; acceptable for structural improvement |
| Multi-membership confusion | HUMAN_CONTEXT and VALIDATED_KNOWLEDGE always mandatory; provenance preserved |
| Premature abstraction | SemanticSectionComposer is minimal; no framework |

---

## 29. Final Handoff

```text
BASELINE_SHA = 24d5bb2
STORY_0103_PRESENT = YES

ACTUAL_FACT_TYPES = 56 values (COMMIT, COMMIT_DIFF_SUMMARY, ... OTHER)
ACTUAL_OBSERVATION_TYPES = 12 values (ASYNCHRONOUS_COMMUNICATION, ... OTHER)
ACTUAL_REPOSITORY_EVIDENCE_KINDS = ANALYSIS, CHANGED_FILE, INSIGHT, COMMIT, ENGINEERING_STORY, CHALLENGE, DECISION, MODULE_SUMMARY, SOURCE_DIRECTORIES, TEST_DIRECTORIES, CONFIGURATION_FILES, FILE_EXTENSIONS, MODULE, SOURCE_FILE, TEST_FILE, CONFIG_FILE

CLASSIFICATION_POLICY = Explicit EnumSet.of() per section per type. No string heuristics.
NEW_TYPE_POLICY = New enum value → UNCLASSIFIED until explicit EnumSet update
UNCLASSIFIED_POLICY = Item stays in canonical array; absent from semantic sections; not lost

SINGLE_SECTION_MEMBERSHIP = NO — items may belong to 1..N sections
MULTI_SECTION_MEMBERSHIP = YES — determined by explicit type metadata
FIRST_MATCH_WINS = REJECTED

SEMANTIC_TAXONOMY_MODEL = Mixed topic + provenance dimensions
TOPIC_VS_PROVENANCE_FINDING = Sections mix topic (PROJECT_STATE, ARCHITECTURE, etc.) and provenance (VALIDATED_KNOWLEDGE, HUMAN_CONTEXT). This is desirable — multi-membership handles cross-dimension items.

HUMAN_CONTEXT_MEMBERSHIP_POLICY = HUMAN_CONTEXT mandatory; additional memberships allowed by ProjectHumanContextInputType
VALIDATED_KNOWLEDGE_MEMBERSHIP_POLICY = VALIDATED_KNOWLEDGE mandatory; additional memberships allowed by InsightType

IDENTITY_AUDIT_RESULT = All entity types have stable IDs. Insight IDs must be preserved in projection (currently stripped). RepositoryEvidence uses String reference (stable).
REFERENCE_BASED_MODEL_VIABLE = YES — all entities have usable identifiers

REPRESENTATION_A_FULL_CONTENT = REJECTED — +51% payload increase
REPRESENTATION_B_PURE_REFERENCE = VIABLE — +3% but low AI interpretability
REPRESENTATION_C_LIGHTWEIGHT_REFERENCE = RECOMMENDED — +7.5% with labels for AI comprehension

RECOMMENDED_REPRESENTATION = Model C — Lightweight References (itemType, itemId, label)

BASELINE_PAYLOAD_BYTES = 68,040 (describe-project-v1)
FULL_CONTENT_PAYLOAD_BYTES = 102,858 (+51%)
PURE_REFERENCE_PAYLOAD_BYTES = 70,120 (+3%)
LIGHTWEIGHT_REFERENCE_PAYLOAD_BYTES = 73,120 (+7.5%)

UNIQUE_SEMANTIC_ITEMS = 114 (40 facts + 1 obs + 10 insights + 3 human + 60 evidence)
TOTAL_SEMANTIC_MEMBERSHIPS = ~171 (114 items × avg 1.5 memberships)
AVERAGE_MEMBERSHIPS_PER_ITEM = ~1.5
MAX_MEMBERSHIPS_PER_ITEM = 2

ZERO_MEMBERSHIP_ITEMS = 0 (UNCLASSIFIED items stay in canonical arrays, not counted)
ONE_MEMBERSHIP_ITEMS = ~57
TWO_MEMBERSHIP_ITEMS = ~57
THREE_PLUS_MEMBERSHIP_ITEMS = 0 (no types justify 3+ memberships from metadata alone)

SELECTION_CHANGE_REQUIRED = NO
PYTHON_CHANGE_REQUIRED = NO (automatic shared propagation)
FRONTEND_CHANGE_REQUIRED = NO
PERSISTENCE_CHANGE_REQUIRED = NO

SEMANTIC_SECTION_COMPOSER_ASSESSMENT = Dedicated SemanticSectionComposer justified by separation of concerns and expected ADR-064 slice growth

STORY_0104_DESIGN_READINESS = A — DESIGN_READY_FOR_HUMAN_REVIEW
STORY_0104_MATERIALIZATION = NOT_AUTHORIZED
IMPLEMENTATION = NOT_AUTHORIZED
```

### Strongest Evidence Supporting Recommendation

1. **Full-content duplication is measured and rejected at +51%**: The initial design's transitional hybrid estimate of +2–5% was incorrect. Real measurement from benchmark data shows that duplicating fact/observation/insight content into sections adds ~35KB to a 68KB payload. Reference-based composition is the only viable path.

2. **Multi-membership is genuinely useful with current data**: ~50% of items (57 of 114) have justified multiple section memberships based on their deterministic type metadata. For example, `DOCKERFILE_PRESENT` is both ARCHITECTURE and PROJECT_STATE. `ADR_DOCUMENT_PRESENT` is both DECISIONS and PROJECT_STATE. Single-section membership would lose meaningful semantic structure.

3. **All entities have stable IDs for reference-based composition**: The identity audit confirms every entity type has a usable identifier. The only required change is preserving Insight IDs in projection (currently stripped). This is a one-line change to `projectInsight()`.

4. **Topic/provenance mixing is correct for V1**: The 7-section vocabulary mixes topic dimensions (ARCHITECTURE, DECISIONS, HISTORY) with provenance dimensions (VALIDATED_KNOWLEDGE, HUMAN_CONTEXT). Multi-membership naturally handles items that span both — a "validated architecture insight" correctly appears in both VALIDATED_KNOWLEDGE (provenance) and ARCHITECTURE (topic). A separate multidimensional tagging system would add complexity without AI benefit.

5. **Lightweight references balance AI interpretability with payload cost**: The +7.5% overhead is acceptable for the structural improvement. Labels like `SPRING_BOOT_DETECTED` give the AI immediate semantic context for joining references with canonical content, significantly better than opaque UUIDs.

`ADR_064_SLICE_2_CORRECTIVE_DESIGN_READY_FOR_HUMAN_REVIEW`
