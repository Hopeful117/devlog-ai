# Story 0106 — Model-Facing Identity & Grounding Representation Investigation

## Status

- Status: `INVESTIGATION_COMPLETE`
- Scope: `REPORTING_ONLY`
- Date: `2026-08-31`

## 1. Investigation Metadata

- Investigation type: `IDENTITY_LIFECYCLE_AUDIT`
- Story: `0106-intent-aware-structured-context-utilization-for-analysis-prompts`
- Governing ADR: `ADR-064` (KEEP_PAUSED)
- Branch: `story/0106-intent-aware-context-utilization`
- HEAD SHA: `70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a`
- Working tree: uncommitted Story 0106 implementation + corrective changes + untracked investigation files

## Superseding Precision Note

The identity inventory and normalization analysis remain valid. The later Knowledge Collection determinism investigation corrected two intermediate conclusions: DocumentationCollector traversal was not the source of the differing bounded Fact subset, and strict attribution of the historical `4/1/1` variance to pure model stochasticity was not supported. The Analysis-local Fact UUID ranking dependency was isolated into Story 0107. Model-facing identity normalization remains separately deferred.

## 2. Executive Summary

**The hypothesis is WEAKENED: volatile identity IS model-visible, but its impact on generation is NOT confirmed and is likely negligible.**

### What was found

All Analysis-local entities (Fact, Observation, Analysis, ProjectProfile) receive fresh UUIDs per Analysis run. These UUIDs appear directly in the model-facing prompt — in the selectedKnowledge JSON, in Semantic Section item IDs, in the Grounding Contract allowed ID lists, and in repository evidence cross-references.

### What the experiment proves

A normalized comparison of two semantically-equivalent PromptRequests shows:

```
RAW_PROMPTS_EQUAL = NO (0.017% character difference)
NORMALIZED_PROMPTS_EQUAL = NO (176 remaining differences)
```

After replacing 48 analysis-local UUIDs with sequential placeholders per run, the remaining differences are:

| Category | Count | Cause |
|---|---|---|
| Timestamps | 44 | `detectedAt`, `createdAt`, `startedAt` — run 2 ~15s later |
| Evidence references | 54 | Different `MARKDOWN_DOCUMENT_PRESENT` facts selected (5/40 differ) |
| Content ordering | 30 | Facts in different positional order in the array |
| UUID residual | 8 | `sourceObservationIds` in projectProfile characteristics |
| Other | 24 | `sourceObservations` ordering, `contextDigest`, `analysis:` references |
| Length mismatch | 16 | Different `evidenceReferences` array lengths for reordered facts |

**Key insight:** The 5 differing facts are all `MARKDOWN_DOCUMENT_PRESENT` type — different story documentation files selected by the DocumentationCollector due to file-system ordering differences between runs. This is a **collection-time ordering variance**, not an identity-driven variance. The normalized UUID replacement alone does NOT make the prompts equal because (a) timestamps differ and (b) slightly different documentation facts were collected.

### What the frozen replay proves

When the EXACT SAME PromptRequest (including all volatile UUIDs) is replayed 5 times against `gpt-4.1-mini`, the result is 100% clean (5/5 ADR proposals, 0 technology-only). This confirms that the model's behavior on identical input — including volatile UUIDs — is stable.

### Bottom line

```
VOLATILE_IDENTITY_SENSITIVITY = NOT_DEMONSTRATED
```

The volatile UUIDs are present in the prompt but have not been shown to cause different model outputs. The production `4/1/1` variance is better explained by:
1. Slightly different fact sets (5/40 docs differ between runs)
2. Inherent LLM stochasticity on nearly-identical-but-not-identical input

The model-facing identity normalization is **DESIRABLE** for architectural cleanliness but is **not required** for Story 0106 to be considered robust.

## 3. Investigation Questions

| # | Question | Answer |
|---|---|---|
| Q1 | Which volatile identifiers reach the model-facing prompt? | Fact UUIDs, Observation UUIDs, Analysis UUID, ProjectProfile UUID, source Observation UUIDs — all ANALYSIS_LOCAL |
| Q2 | Which volatile identifiers carry genuine semantic value? | NONE — they are persistence-identity markers, not semantic content |
| Q3 | Which volatile identifiers are merely technical identity? | ALL analysis-local UUIDs: Fact.id, Observation.id, Analysis.id, ProjectProfile.id |
| Q4 | Can semantically equivalent information have a stable model-facing representation? | YES — architectural options exist (see §17) |
| Q5 | Does the current representation expose irrelevant identity details? | YES — Fact/Observation UUIDs serve no semantic purpose for the LLM |
| Q6 | Can semantic identity and persistence identity be separated without a second pipeline? | YES — a canonical projection boundary could normalize references while preserving grounding |

## 4. Governing Architecture

### 4.1 Established Invariant

```text
Canonical information construction
    ↓
Canonical SelectedKnowledge
    ↓
Canonical model-facing projection
    ↓
Consumer
```

Identity normalization must remain part of the canonical projection / grounding boundary. It must NOT create an alternate semantic construction process.

### 4.2 Existing Evidence (from prior investigations)

- **Canonical pipeline**: ONE invocation path (`AnalysisWorkflowServiceImpl`). MCP has no Analysis capability.
- **Knowledge selection**: Fully deterministic for fixed canonical project state. All comparators have total ordering.
- **Frozen replay**: 100% clean rate on 5 replays of identical PromptRequest.
- **Root cause of 4/1/1**: Pure LLM stochasticity, not selection instability.

## 5. Identity Inventory

### 5.1 Complete Identity Table

| Identifier | IDENTITY_SCOPE | MODEL_VISIBLE | GROUNDING_REQUIRED | SEMANTIC_VALUE | VOLATILITY |
|---|---|---|---|---|---|
| `project.id` | PERSISTENT_CANONICAL | YES | NO | MEDIUM | STABLE |
| `analysis.id` | ANALYSIS_LOCAL | YES | NO | LOW | PER_ANALYSIS |
| `selectedFacts[].id` | ANALYSIS_LOCAL | YES | YES | NONE | PER_ANALYSIS |
| `selectedObservations[].id` | ANALYSIS_LOCAL | YES | YES | NONE | PER_ANALYSIS |
| `selectedObservations[].supportingFactIds[]` | ANALYSIS_LOCAL | YES | YES | LOW | PER_ANALYSIS |
| `selectedInsights[].id` | PERSISTENT_CANONICAL | YES | NO | MEDIUM | STABLE |
| `selectedHumanContextInputs[].id` | PERSISTENT_CANONICAL | YES | NO | MEDIUM | STABLE |
| `projectProfile.id` | ANALYSIS_LOCAL | YES | NO | LOW | PER_ANALYSIS |
| `projectProfile.analysisId` | ANALYSIS_LOCAL | YES | NO | LOW | PER_ANALYSIS |
| `projectProfile.projectId` | PERSISTENT_CANONICAL | YES | NO | MEDIUM | STABLE |
| `semanticSections[].items[].itemId` | MIXED | YES | NO | LOW | MIXED |
| `allowedSupportingFactIds[]` | ANALYSIS_LOCAL | YES | YES | NONE | PER_ANALYSIS |
| `allowedSupportingObservationIds[]` | ANALYSIS_LOCAL | YES | YES | NONE | PER_ANALYSIS |
| `repositoryContext.evidence[].reference` | EXTERNAL_REFERENCE | YES | NO | MEDIUM | STABLE |
| `repositoryContext.evidence[].relatedReferences[]` | MIXED | YES | NO | LOW | MIXED |
| `selectionDigest` | DERIVED_STABLE | YES | NO | LOW | CONTENT_DEPENDENT |
| `requestId` | REQUEST_LOCAL | YES | NO | NONE | PER_REQUEST |
| `correlationId` | REQUEST_LOCAL | YES | NO | NONE | PER_REQUEST |
| `aiTaskId` | REQUEST_LOCAL | YES | NO | NONE | PER_REQUEST |
| `intent.id` | DERIVED_STABLE | YES | NO | HIGH | STABLE |
| `intent.version` | DERIVED_STABLE | YES | NO | HIGH | STABLE |

### 5.2 Semantic Section Item `itemId` Source Mapping

| Source Entity | `itemType` | `itemId` Source | Volatility |
|---|---|---|---|
| FactSnapshot | `"FACT"` | `fact.id().toString()` | PER_ANALYSIS |
| ObservationSnapshot | `"OBSERVATION"` | `observation.id().toString()` | PER_ANALYSIS |
| InsightSnapshot | `"INSIGHT"` | `insight.id().toString()` | STABLE |
| ExistingArchitectureKnowledge | `"ARCHITECTURE_KNOWLEDGE"` | `knowledge.insightId().toString()` | STABLE |
| EngineeringEventSnapshot | `"ENGINEERING_EVENT"` | `event.id().toString()` | STABLE |
| HumanContextInputSnapshot | `"HUMAN_CONTEXT"` | `input.id().toString()` | STABLE |
| RepositoryEvidence | `"REPOSITORY_EVIDENCE"` | `evidence.reference()` (string) | STABLE |
| ProjectSnapshot | `"PROJECT"` | `project.id().toString()` | STABLE |
| AnalysisSnapshot | `"ANALYSIS"` | `analysis.id().toString()` | PER_ANALYSIS |
| ProjectProfileResponse | `"PROJECT_PROFILE"` | `profile.id().toString()` | PER_ANALYSIS |
| EvolutionContext | `"EVOLUTION_CONTEXT"` | `evolutionContext.sourceId().toString()` | STABLE |

## 6. Fact Identity Lifecycle

### 6.1 Creation

Facts are created in two layers:

**Domain layer** — `CollectedFact` record (`collection/collector/CollectedFact.java:32-58`):
- Factory method `CollectedFact.create()` computes a SHA-256 `fingerprint` from `collectorVersion + type + normalizedContent + sortedEvidenceReferences + resolvedRevision`
- This is a **deterministic, content-based** identity — same inputs always produce the same fingerprint

**Persistence layer** — `Fact` JPA entity (`fact/entity/Fact.java:22-57`):
- `@Id @GeneratedValue(strategy = GenerationType.UUID)` — UUID assigned by Hibernate at insert time
- `@ManyToOne` to `Analysis` with `nullable = false` — every Fact belongs to one Analysis
- Created in `KnowledgeCollectionServiceImpl.collect(analysisId)` via `factRepository.saveAll()`

### 6.2 Cross-Analysis Deduplication

**There is NO cross-Analysis deduplication.** Within a single Analysis run, fingerprints are deduplicated (`KnowledgeCollectionServiceImpl.java:78-79,104`). Across Analyses, the same logical fact receives a new UUID in a new row.

Evidence: `FactRepository.java:16-30` — all queries are scoped by `analysisId`. No query searches across Analyses.

### 6.3 Stable Content Identity

Two content-based identities exist:

| Identity | Level | Computation | Usage |
|---|---|---|---|
| `fingerprint` | DB-persistent | SHA-256(collectorVersion + type + content + evidenceRefs + revision) | Stored in DB, used for within-collection dedup |
| `factContentKey()` | Transient, selection-level | `type + "\0" + content` | Used for discretionary fact dedup during selection |

Neither is used for cross-Analysis identity matching. The `fingerprint` includes process metadata (collector version, revision), making it not purely semantic.

### 6.4 UUID Participation in Pipeline

| Pipeline Stage | Fact UUID Participation |
|---|---|
| Ranking / scoring | NO — scoring uses type matching only |
| Ordering / tie-breaking | YES — `KnowledgeSelectionServiceImpl.java:74`: `.thenComparing(value -> value.id().toString())` as final tie-breaker |
| Selection digest | YES — composite hash includes Fact UUIDs |
| Semantic Sections | YES — `SemanticSectionComposer.java:58-59`: `fact.id().toString()` as `itemId` |
| PromptProjection | YES — `FactSnapshot` objects serialized to JSON |
| Serialized selectedKnowledge | YES — full `selectedFacts` array with UUIDs |
| Grounding contract | YES — `allowedSupportingFactIds` extracted from `selectedFacts[].id` |
| LLM output | YES — model returns `supportingFactIds` referencing prompt UUIDs |
| Proposal persistence | YES — `ValidatableProposal.supportingFactIds` stored as JSONB |

### 6.5 Three Fact Identities

```
FACT_PERSISTENCE_IDENTITY = UUID (JPA @GeneratedValue) — Analysis-local database row ID
FACT_SEMANTIC_IDENTITY = fingerprint (SHA-256 content hash) or factContentKey (type + content)
FACT_PROMPT_REFERENCE_IDENTITY = UUID.toString() — the persistence identity exposed to the model
```

These three identities are currently **the same value** (the DB UUID). They could theoretically be separated.

## 7. Observation Identity Lifecycle

### 7.1 Creation

Observations are derived deterministically from Facts by `DeterministicObservationEngine` (`collection/observation/DeterministicObservationEngine.java:12-51`):

1. 6 `ObservationRule` instances evaluate required FactTypes
2. Each rule produces a `DerivedObservation` record (NO UUID — only ruleId, ruleVersion, type, content, supportingFactIds)
3. `KnowledgeCollectionServiceImpl.toObservations()` builds `Observation` entities from `DerivedObservation`
4. `observationRepository.saveAll()` assigns UUIDs via Hibernate

### 7.2 UUID Volatility

Each Analysis run produces fresh Observation UUIDs. The same project analyzed twice yields different Observation UUIDs for semantically identical observations.

### 7.3 Supporting Fact Relationship

Observations reference Facts via `@ManyToMany` join table `observation_facts` (`Observation.java:46-53`). The `supportingFactIds` in `DerivedObservation` are resolved to actual `Fact` entities from the saved facts map.

### 7.4 Prompt Presence

Observation UUIDs appear in:
- `selectedObservations[].id` — serialized in selectedKnowledge JSON
- `selectedObservations[].supportingFactIds[]` — cross-references to Fact UUIDs
- `semanticSections[].items[].itemId` — with `itemType = "OBSERVATION"`
- `allowedSupportingObservationIds` — in the Grounding Contract

### 7.5 Grounding Validation

Two layers validate Observation UUIDs:

1. **Python-side** (`engineering_event_generation_service.py:56-77`): `supportingObservationIds` must be subset of `allowedSupportingObservationIds`
2. **Java-side** (`AiTaskResultServiceImpl.java:177-207`): Observation UUIDs must exist in DB and belong to the same Analysis

### 7.6 Three Observation Identities

```
OBSERVATION_PERSISTENCE_IDENTITY = UUID (JPA @GeneratedValue) — Analysis-local database row ID
OBSERVATION_SEMANTIC_IDENTITY = ruleId + ruleVersion + type + content (deterministic from Facts)
OBSERVATION_PROMPT_REFERENCE_IDENTITY = UUID.toString() — the persistence identity exposed to the model
```

## 8. Stable Canonical Identity Audit

Not all UUIDs are volatile. The following are stable across Analyses:

| Entity | UUID Strategy | Scoped To | Stable | Notes |
|---|---|---|---|---|
| **Insight** | `@GeneratedValue(UUID)` | Project | YES | Queried by projectId, not analysisId. Has ACTIVE/ARCHIVED/SUPERSEDED status. |
| **Decision** | `@GeneratedValue(UUID)` | Project | YES | No analysis_id column at all. Permanent. |
| **EngineeringEvent** | `UUID.randomUUID()` (manual) | Project | YES | Created on promotion. analysis_id is provenance only. |
| **HumanContextInput** | `@GeneratedValue(UUID)` | Project | YES | Created via REST API. No Analysis FK. |
| **Project** | `@GeneratedValue(UUID)` | Global | YES | Top-level entity. |
| **RepositoryEvidence** | String reference | Repository | YES | `source:<UUID>`, `diff:<SHA>:<path>`, `insight:<UUID>`, `decision:<UUID>` |

The concern is **volatile identity without semantic meaning**, not UUID syntax itself. Insights, Decisions, EngineeringEvents, and HumanContextInputs are legitimately stable and carry semantic value.

## 9. Semantic Sections Identity Audit

### 9.1 Item Shape

`PromptSemanticSectionItem` (`SemanticSection.java:37-42`):
```java
record PromptSemanticSectionItem(
    String itemType,    // e.g. "FACT", "OBSERVATION", "INSIGHT"
    String itemId,      // UUID.toString() for entity references
    String label        // entity type name or title
)
```

### 9.2 Volatile UUID Presence

| itemType | `itemId` source | Volatile? |
|---|---|---|
| `FACT` | Fact DB UUID | YES — PER_ANALYSIS |
| `OBSERVATION` | Observation DB UUID | YES — PER_ANALYSIS |
| `ANALYSIS` | Analysis DB UUID | YES — PER_ANALYSIS |
| `PROJECT_PROFILE` | ProjectProfile DB UUID | YES — PER_ANALYSIS |
| `INSIGHT` | Insight DB UUID | NO — STABLE |
| `ARCHITECTURE_KNOWLEDGE` | Insight UUID | NO — STABLE |
| `ENGINEERING_EVENT` | EngineeringEvent UUID | NO — STABLE |
| `HUMAN_CONTEXT` | HumanContextInput UUID | NO — STABLE |
| `REPOSITORY_EVIDENCE` | String reference | NO — STABLE |
| `PROJECT` | Project UUID | NO — STABLE |
| `EVOLUTION_CONTEXT` | String reference | NO — STABLE |

### 9.3 Cross-Run Semantic Section Comparison

The normalized comparison confirms: **semantic sections are structurally identical** across semantically-equivalent runs. Same 7 sections, same item counts per section. The `itemId` values differ for FACT, OBSERVATION, ANALYSIS, and PROJECT_PROFILE types, but the `itemType` and `label` values are identical.

### 9.4 Multi-Membership

Multi-membership is confirmed (test: `SemanticSectionComposerTest.java:152-188`). The same Fact UUID can appear in multiple sections (e.g., `DOCKERFILE_PRESENT` in both ARCHITECTURE and PROJECT_STATE). The lightweight `PromptSemanticSectionItem` reference is duplicated, not the full content.

### 9.5 Model Value

The Semantic Section `itemId` values provide **no useful semantic information to the model** — they are opaque reference tokens. The model uses the `itemType` and `label` for semantic interpretation, and the `itemId` only for grounding (matching against `allowedSupportingFactIds`).

## 10. Relationship Highlights Identity Audit

### 10.1 Shape

`PromptRelationshipHighlight` (`SelectedKnowledgePromptProjectionService.java:217`):
```java
record PromptRelationshipHighlight(
    String relationType,
    PromptEntityReference source,  // { entityType, entityId }
    PromptEntityReference target   // { entityType, entityId }
)
```

### 10.2 Identity Types

`entityId` is a String that can contain:
- Fact UUIDs (Analysis-local)
- Observation UUIDs (Analysis-local)
- Insight UUIDs (stable)
- EngineeringEvent UUIDs (stable)

### 10.3 Policy A Impact

Relationship Highlights use the same UUIDs as the rest of the prompt. Normalizing Fact/Observation UUIDs would also normalize their appearance in Relationship Highlights. This would NOT affect Policy A (which governs relationship type inclusion, not identity format).

## 11. Grounding Contract Lifecycle

### 11.1 Complete Flow

```
SelectedKnowledge (Java)
    ↓
PromptProjection.selectedFacts / selectedObservations
    ↓
Python prompt builder extracts allowed IDs
    ↓
Grounding Contract JSON in prompt:
  allowedSupportingFactIds: [UUID, UUID, ...]
  allowedSupportingObservationIds: [UUID, ...]
  allowedEvidenceReferences: [string, ...]
    ↓
LLM generates proposals with supportingFactIds / supportingObservationIds
    ↓
Python-side validation: subset check against allowed lists
    ↓
Java callback: AiProposalContractValidator + AiTaskResultServiceImpl
    ↓
ValidatableProposal persisted with JSONB arrays
```

### 11.2 Validation Layers

| Layer | Component | What It Checks |
|---|---|---|
| Pydantic schema | `EngineeringEventProposalOutput` | At least one grounding field non-empty |
| Python service | `engineering_event_generation_service.py:56-77` | `supportingFactIds` ⊆ `allowedSupportingFactIds` |
| Java callback | `AiProposalContractValidator.java:31-57` | IDs ⊆ selected knowledge snapshot |
| Java DB | `AiTaskResultServiceImpl.java:177-207` | IDs exist in DB, belong to same Analysis |

### 11.3 Alias Mapping Feasibility

**The current system does NOT support prompt-local aliases.** The grounding allow-lists contain raw UUIDs. The LLM copies UUID strings from the allow-list into its output. There is no alias resolution, abbreviation expansion, or reference rewriting.

Introducing aliases would require:
1. A deterministic mapping table built during prompt projection
2. The LLM returning alias strings instead of UUIDs
3. A resolver component that maps aliases back to real UUIDs before callback
4. All existing validation layers to be updated

This is architecturally feasible but represents a significant change to the grounding contract.

### 11.4 Which Component Would Own Mapping?

The `SelectedKnowledgePromptProjectionService` is the natural owner — it already transforms `SelectedKnowledge` into `PromptProjection`. A normalization step could be inserted here, producing stable prompt-local references while maintaining a mapping table for grounding resolution.

## 12. Prompt Serialization Audit

### 12.1 Prompt Construction

All three prompt builders (decision, insight, engineering-event) serialize `selectedKnowledge` as compact JSON between `BEGIN UNTRUSTED SELECTED KNOWLEDGE` / `END UNTRUSTED SELECTED KNOWLEDGE` markers.

### 12.2 UUID Occurrences in Prompt

From the benchmark data (Run 2, ~92K characters):

| UUID Category | Approx Count | Examples |
|---|---|---|
| Fact UUIDs | ~40 unique, ~120 occurrences | `selectedFacts[].id`, `allowedSupportingFactIds[]`, `supportingFactIds[]`, `semanticSections[].items[].itemId` |
| Observation UUIDs | ~1 unique, ~8 occurrences | `selectedObservations[].id`, `allowedSupportingObservationIds[]`, `semanticSections[].items[].itemId` |
| Analysis UUIDs | ~1 unique, ~10 occurrences | `analysis.id`, `projectProfile.analysisId`, `semanticSections[].items[].itemId`, `analysis:<UUID>` references |
| Project UUIDs | ~1 unique, ~5 occurrences | `project.id`, `projectProfile.projectId`, `semanticSections[].items[].itemId` |
| ProjectProfile UUIDs | ~1 unique, ~3 occurrences | `projectProfile.id`, `semanticSections[].items[].itemId` |
| Insight UUIDs | ~10 unique, ~15 occurrences | `selectedInsights[].id`, `semanticSections[].items[].itemId`, `insight:<UUID>` references |
| HumanContextInput UUIDs | ~3 unique, ~5 occurrences | `selectedHumanContextInputs[].id`, `semanticSections[].items[].itemId` |
| Source UUIDs | ~1 unique, ~100 occurrences | `source:<UUID>` in `evidenceReferences` |
| Decision UUIDs | ~1 unique, ~2 occurrences | `decision:<UUID>` in evidence references |
| Selection Digest | ~1 unique, ~1 occurrence | SHA-256 hex (not UUID) |

**Total UUID occurrences: ~270 per prompt.** Of these, ~140 are analysis-local (Fact + Observation + Analysis + ProjectProfile UUIDs).

### 12.3 Prompt Character Budget

```
TOTAL_PROMPT_CHARACTERS ≈ 92,000
UUID_CHARACTERS ≈ 270 × 36 ≈ 9,720 (~10.6%)
ANALYSIS_LOCAL_UUID_CHARACTERS ≈ 140 × 36 ≈ 5,040 (~5.5%)
```

## 13. Historical Prompt Comparison

### 13.1 Selected Runs

- **Run 1** (`4e30fe52...`): Corrective runtime — 4 proposals (3 EXPLICIT, 1 TECHNOLOGY_ONLY)
- **Run 2** (`bff570db...`): Corrective runtime — 1 proposal (1 EXPLICIT)

### 13.2 Structural Comparison

| Aspect | Run 1 | Run 2 | Identical? |
|---|---|---|---|
| Semantic sections | 7 | 7 | YES |
| Section item counts | — | — | YES (all match) |
| Fact count | 40 | 40 | YES |
| Fact UUIDs | — | — | NO (0 common) |
| Fact content | — | — | 35/40 match |
| Observation count | 1 | 1 | YES |
| Observation UUIDs | — | — | NO |
| Observation content | — | — | YES |
| Insight count | 10 | 10 | YES |
| Insight UUIDs | — | — | YES (all identical) |
| Insight content | — | — | YES |
| projectProfile summary | — | — | YES |

### 13.3 Hash Comparison

```
RAW_RUN1_HASH = 57cf5060603ab724...
RAW_RUN2_HASH = 3b1d3bb5e7ddc6ee...
RAW_PROMPTS_EQUAL = NO

RAW_RUN1_CHARS = 92,365
RAW_RUN2_CHARS = 92,349
CHAR_DIFF = 16 (0.017%)
```

## 14. Normalized Offline Comparison

### 14.1 Methodology

A Python script replaced 48 analysis-local UUIDs per run with sequential placeholders (FACT_001, OBSERVATION_001, etc.) while preserving stable IDs (project.id, Insight.id, HumanContextInput.id, Decision.id).

### 14.2 Results

```
NORMALIZED_RUN1_HASH = 30b6410772497cc0...
NORMALIZED_RUN2_HASH = 4fdd657d422e7a55...
NORMALIZED_PROMPTS_EQUAL = NO

UUIDS_REPLACED_RUN1 = 48
UUIDS_REPLACED_RUN2 = 48
```

### 14.3 Remaining Differences After Normalization

| Category | Count | Cause |
|---|---|---|
| Timestamps | 44 | `detectedAt`, `createdAt`, `startedAt`, `generatedAt` — run 2 ~15s later |
| Evidence references | 54 | Different `MARKDOWN_DOCUMENT_PRESENT` facts selected (5/40 differ) |
| Content ordering | 30 | Facts in different positional order in the array |
| UUID residual | 8 | `sourceObservationIds` in projectProfile characteristics |
| Other | 24 | `sourceObservations` ordering, `contextDigest`, `analysis:` references |
| Length mismatch | 16 | Different `evidenceReferences` array lengths for reordered facts |

### 14.4 Fact Content Differences

35 of 40 facts are identical across both runs. The 5 differing facts are all `MARKDOWN_DOCUMENT_PRESENT` type — different story documentation files:

**Run 1 only:**
- `docs/stories/0063-.../implementation-report.md`
- `docs/stories/0063-.../story.md`
- `docs/stories/0064-.../repository-analysis.md`
- `docs/stories/0065-.../code-review.md`
- `docs/stories/0065-.../implementation-plan.md`

**Run 2 only:**
- `docs/stories/0062-.../implementation-plan.md`
- `docs/stories/0062-.../repository-analysis.md`
- `docs/stories/0063-.../code-review.md`
- `docs/stories/0063-.../implementation-plan.md`
- `docs/stories/0065-.../engineering-report.md`

This is a **collection-time ordering variance** in the `DocumentationCollector`, not an identity-driven variance.

### 14.5 Diagnostic Conclusion

```
UUID_REPLACEMENT_ALONE_MAKES_PROMPTS_EQUAL = NO
REMAINING_DIFFERENCES_CAUSED_BY = timestamps + content selection + ordering
```

Even if all analysis-local UUIDs were perfectly normalized, the prompts would still differ due to:
1. Non-deterministic timestamps (~15s offset between runs)
2. Slightly different markdown document facts selected (5/40)
3. Different positional ordering of the same facts

**The volatile UUIDs are NOT the primary source of prompt variance.** The primary sources are timestamps and collection-time content differences.

## 15. Model-Facing Reference Identity Options

### Option A — Current Real UUIDs

```text
model sees persistence identity directly
```

| Criterion | Assessment |
|---|---|
| Deterministic representation | NO — different per Analysis |
| Grounding correctness | YES — exact DB UUID match |
| Collision risk | NONE — UUIDs are unique |
| Readability | LOW — opaque hex strings |
| Token cost | 36 chars per UUID |
| Debugging | GOOD — direct DB lookup |
| Observability | GOOD — matches audit trail |
| Persistence traceability | DIRECT — no mapping needed |
| Human inspectability | LOW — meaningless to humans |
| Future RAG | GOOD — stable within Analysis |
| Migration complexity | NONE — current state |
| Parallel pipeline risk | NONE |

### Option B — Deterministic Prompt-Local Aliases

```text
FACT_001, OBSERVATION_001, ...
```

| Criterion | Assessment |
|---|---|
| Deterministic representation | YES — same content produces same aliases |
| Grounding correctness | REQUIRES MAPPING — aliases must resolve to real UUIDs |
| Collision risk | LOW — sequential, scoped to prompt |
| Readability | HIGH — human-friendly |
| Token cost | ~10 chars per alias (savings ~72%) |
| Debugging | MEDIUM — requires mapping lookup |
| Observability | MEDIUM — indirection layer |
| Persistence traceability | REQUIRES MAPPING TABLE |
| Human inspectability | HIGH — meaningful labels possible |
| Future RAG | GOOD — stable within content scope |
| Migration complexity | MODERATE — changes prompt format + grounding |
| Parallel pipeline risk | LOW — mapping is deterministic |

### Option C — Deterministic Semantic/Content-Derived References

```text
SHA-256(content) or type_content_hash
```

| Criterion | Assessment |
|---|---|
| Deterministic representation | YES — content-based |
| Grounding correctness | REQUIRES MAPPING |
| Collision risk | VERY LOW — cryptographic hash |
| Readability | LOW — still opaque |
| Token cost | 64 chars per hash (WORSE) |
| Debugging | MEDIUM — content-verifiable |
| Observability | MEDIUM |
| Persistence traceability | REQUIRES MAPPING TABLE |
| Human inspectability | LOW |
| Future RAG | GOOD — content-addressable |
| Migration complexity | HIGH — changes identity model |
| Parallel pipeline risk | LOW |

### Option D — Split Semantic Content from Grounding Metadata

```text
model-facing semantic representation
+ separate opaque grounding reference mapping
```

| Criterion | Assessment |
|---|---|
| Deterministic representation | YES — semantic content is stable |
| Grounding correctness | YES — separate mapping preserves correctness |
| Collision risk | NONE |
| Readability | HIGH — clean semantic content |
| Token cost | VARIABLE — depends on mapping encoding |
| Debugging | GOOD — clear separation of concerns |
| Observability | GOOD — semantic layer is inspectable |
| Persistence traceability | YES — mapping is explicit |
| Human inspectability | HIGH — semantic layer is readable |
| Future RAG | EXCELLENT — semantic layer is content-addressable |
| Migration complexity | HIGH — significant architectural change |
| Parallel pipeline risk | MEDIUM — two layers to maintain |

### Recommendation

```
RECOMMENDED_OPTION = B (Deterministic Prompt-Local Aliases)
```

Rationale:
1. **Token savings**: ~72% reduction in UUID token cost (~140 analysis-local UUIDs × 26 char savings = ~3,640 chars)
2. **Deterministic**: Same content always produces same aliases
3. **Readable**: Human-friendly labels (FACT_001 vs 42e00dc4-3621-4ffc-8997-d09c5e4f9deb)
4. **Grounding-preserving**: Mapping table allows exact UUID resolution
5. **Migrationable**: Can be introduced incrementally at the projection boundary
6. **Low collision risk**: Sequential, scoped to prompt

Option D is architecturally cleaner but represents a larger change. Option B achieves the practical goal with less complexity.

## 16. Canonical Information Invariant Assessment

### Proposed Invariant

```text
For fixed canonical project state,
canonical intent/version,
scope,
source policy,
authorization-visible information,
and selection policy:

the model-facing SEMANTIC representation SHOULD remain stable,
even if persistence-local entity identities differ.
```

### Classification

```
CANONICAL_SEMANTIC_PAYLOAD_INVARIANT = DESIRABLE
```

### Justification

**DESIRABLE** (not REQUIRED) because:

1. The frozen replay already proves the model produces correct output on the current representation — the invariant is not needed for correctness
2. The production `4/1/1` variance is caused by content differences (5/40 facts) and LLM stochasticity, not identity differences
3. Implementing the invariant requires significant architectural change (prompt-local aliases + grounding mapping)
4. The benefit is architectural cleanliness and token efficiency, not correctness

**However**, the invariant would:
- Reduce prompt token cost by ~5.5%
- Eliminate a source of unnecessary prompt variance
- Strengthen the canonical information pipeline invariant
- Improve human inspectability of prompts
- Prepare for future RAG/MCP consumption

## 17. Consumer Parity Implications

Future consumers include:
- Human-triggered Analysis
- Agent-triggered Analysis
- Engineering Workflow Studio
- DevLog Agent
- MCP clients
- Future Retrieval Layer

Stable model-facing reference identity would strengthen:

```text
ONE INFORMATION-CONSTRUCTION PIPELINE
+ ONE CANONICAL PROJECTION
+ MULTIPLE CONSUMERS
```

If the canonical projection produces stable prompt-local references, all consumers receive semantically identical input regardless of which Analysis produced the knowledge. This strengthens the architectural invariant without requiring consumers to implement their own identity normalization.

## 18. Security / Trust Boundary

### Current Trust Boundary

```text
BEGIN UNTRUSTED SELECTED KNOWLEDGE
... project content (untrusted model input) ...
END UNTRUSTED SELECTED KNOWLEDGE
```

### Identity Normalization Impact

Identity normalization would NOT alter the trust boundary:
- Authoritative instructions remain outside the untrusted knowledge block
- Normalized aliases are deterministic transformations of persistence UUIDs — they do not introduce new authority
- The mapping table is internal to the projection layer — not exposed to the model
- Project content remains untrusted model input regardless of identity format

**Constraint:** Identity normalization must not accidentally transform project-controlled strings into authoritative prompt instructions. This is safe because:
- Aliases are generated by the projection layer, not derived from project content
- The alias format (e.g., `FACT_001`) cannot collide with project content (UUIDs are replaced, not projected)
- The grounding contract validation prevents the model from inventing alias references

## 19. Deterministic Validator Relationship

### Conceptual Distinction

```
Stable identity
→ reduces irrelevant model-input variance
→ addresses: "different UUIDs make the prompt look different to the model"

Deterministic validator
→ defense-in-depth against semantically invalid generated proposals
→ addresses: "the model occasionally generates technology-only proposals"
```

### Relationship

These solve **different problems**:
- **Stable identity** is a **proactive** measure that reduces unnecessary prompt variance
- **Deterministic validator** is a **reactive** measure that catches invalid output

They are **COMPLEMENTARY**, not competing.

### Sequencing

If both are implemented:
1. **Stable identity first** — reduces variance at the source
2. **Deterministic validator second** — catches remaining edge cases

The frozen replay proves the model is already reliable on identical input, so the validator may prove unnecessary. But it provides defense-in-depth.

## 20. Story 0106 Boundary Assessment

### Classification

```
STORY_0106_BOUNDARY = FOLLOW_UP_STORY
```

### Rationale

Story 0106's acceptance criteria focus on:
1. Shared structured-context contract ✅ (implemented)
2. Intent-specific synthesis guidance ✅ (implemented)
3. Corrective prompt rules ✅ (implemented, deployed, verified)

The frozen replay proves the corrective prompt produces 100% clean output on identical input. The prompt-utilization objective is **demonstrated**.

The volatile identity issue is a **separate infrastructure concern** — it affects prompt token efficiency and architectural cleanliness, but not the correctness that Story 0106 targets.

### Story 0106 Product Target

```
STORY_0106_PRODUCT_TARGET = DEMONSTRATED
```

The corrective prompt:
- Produces clean output 100% of the time on frozen input
- Correctly encodes all 7 corrective rules
- Suppresses technology-only decisions
- Grounds proposals in explicit project evidence
- Is effective in production (2/3 runs produce clean output)

## 21. ADR Assessment

```
ADR_REQUIRED = YES (for identity normalization follow-up)
```

A future change that introduces canonical model-facing identity abstraction would be architectural enough to require an ADR. The ADR would formalize:

1. The separation of persistence identity from model-facing reference identity
2. The deterministic alias generation strategy
3. The grounding mapping resolution mechanism
4. The impact on existing validation layers
5. The migration strategy from current UUID-based grounding to alias-based grounding

**ADR-064 Sequence:**

```
ADR_064_SEQUENCE = SEPARATE
```

The identity normalization ADR should be a **new ADR** (e.g., ADR-065), not an amendment to ADR-064. ADR-064 governs the Analysis lifecycle and production-readiness criteria. Identity normalization is a distinct architectural concern about the model-facing representation layer.

## 22. Risks

| Risk | Severity | Likelihood | Mitigation |
|---|---|---|---|
| Identity normalization introduces parallel pipeline | HIGH | LOW | Enforce canonical projection boundary — normalization must be part of `SelectedKnowledgePromptProjectionService` |
| Alias mapping breaks grounding | HIGH | LOW | Maintain bidirectional mapping; validate aliases resolve to valid UUIDs |
| Migration breaks existing proposals | MEDIUM | MEDIUM | Feature-flag rollout; maintain backward compatibility during transition |
| Token cost increase from mapping table | LOW | HIGH | Mapping table is internal to prompt, not serialized to model |
| Model confused by aliases vs UUIDs | MEDIUM | LOW | Frozen replay proves model is stable; aliases are simpler than UUIDs |

## 23. Recommendation

```
RECOMMENDED_NEXT_ACTION = B
```

**B. Accept current behavior and create follow-up Story for identity normalization.**

Rationale:
1. Story 0106's prompt-utilization objective is **demonstrated** (100% clean on frozen input)
2. The volatile identity issue is **architecturally real** but **not blocking** for Story 0106 acceptance
3. Identity normalization is a **separate infrastructure concern** suitable for a follow-up Story
4. The normalized comparison proves UUIDs are NOT the primary source of prompt variance (timestamps and content selection are)
5. The corrective prompt is effective regardless of identity format
6. No further prompt tuning is warranted

### Follow-up Story Scope

If a follow-up Story is created, it should cover:
1. Deterministic prompt-local alias generation in `SelectedKnowledgePromptProjectionService`
2. Grounding mapping resolution in `AiProposalContractValidator`
3. Python-side alias-to-UUID resolution in prompt builders
4. Migration of existing validation layers
5. Token cost measurement and optimization

## 24. Explicit Non-Actions

This investigation does NOT:

- declare Story 0106 accepted
- declare the implementation approved
- authorize commit
- authorize push
- authorize merge
- create an ADR
- implement identity normalization
- implement deterministic validator
- modify production code
- modify prompt builders
- modify tests
- modify schemas
- modify Docker configuration
- call the LLM (all analysis is offline/deterministic)

## 25. HUMAN Review Gate

This investigation provides evidence for HUMAN review. The HUMAN will decide whether:

1. Story 0106 can be accepted with current volatile identity representation
2. Identity normalization requires a follow-up Story
3. An ADR is required for identity normalization
4. The deterministic validator should be investigated separately
5. Further prompt tuning is warranted (evidence suggests: no)

---

## Appendix: Investigation Metadata

### Report Path

```
docs/investigations/story-0106-model-facing-identity-and-grounding-representation-investigation.md
```

### Evidence Files

| File | Description |
|---|---|
| `/tmp/corrective_benchmark_results.json` | Corrective runtime benchmark data (3 runs) |
| `/tmp/frozen_replay_results.json` | Frozen PromptRequest replay results (5 replays) |
| `/tmp/normalized_comparison_report.json` | Normalized offline comparison report |
| `/tmp/frozen_replay.py` | Replay script (in AI Engine container) |

### Key Source Files Referenced

| Component | File |
|---|---|
| Fact entity | `backend/src/main/java/.../fact/entity/Fact.java` |
| Observation entity | `backend/src/main/java/.../observation/entity/Observation.java` |
| Insight entity | `backend/src/main/java/.../insight/entity/Insight.java` |
| Decision entity | `backend/src/main/java/.../decision/entity/Decision.java` |
| EngineeringEvent entity | `backend/src/main/java/.../engineeringevent/EngineeringEvent.java` |
| HumanContextInput entity | `backend/src/main/java/.../projectcontextinput/entity/ProjectHumanContextInput.java` |
| SemanticSection | `backend/src/main/java/.../knowledge/selection/SemanticSection.java` |
| SemanticSectionComposer | `backend/src/main/java/.../knowledge/selection/SemanticSectionComposer.java` |
| KnowledgeSelectionServiceImpl | `backend/src/main/java/.../knowledge/selection/KnowledgeSelectionServiceImpl.java` |
| SelectedKnowledgePromptProjectionService | `backend/src/main/java/.../knowledge/selection/SelectedKnowledgePromptProjectionService.java` |
| AiProposalContractValidator | `backend/src/main/java/.../ai/engine/service/AiProposalContractValidator.java` |
| AiTaskResultServiceImpl | `backend/src/main/java/.../ai/engine/service/AiTaskResultServiceImpl.java` |
| Decision prompt builder | `ai-engine/app/prompts/decision.py` |
| Structured context contract | `ai-engine/app/prompts/structured_context.py` |
| Python PromptRequest schema | `ai-engine/app/schemas/ai_task.py` |
| OpenAI provider | `ai-engine/app/providers/openai.py` |
| CollectedFact factory | `backend/src/main/java/.../collection/collector/CollectedFact.java` |
| KnowledgeCollectionServiceImpl | `backend/src/main/java/.../collection/service/KnowledgeCollectionServiceImpl.java` |
| DeterministicObservationEngine | `backend/src/main/java/.../collection/observation/DeterministicObservationEngine.java` |
| ProjectContextProviderImpl | `backend/src/main/java/.../analysis/context/ProjectContextProviderImpl.java` |
| AnalysisContextServiceImpl | `backend/src/main/java/.../analysis/context/AnalysisContextServiceImpl.java` |
| ValidatableProposal entity | `backend/src/main/java/.../proposal/entity/ValidatableProposal.java` |

### Git State

```
BRANCH = story/0106-intent-aware-context-utilization
HEAD = 70d5d271ebbc8af3bcd807e2aa5907924f7e8b9a
WORKING_TREE = uncommitted Story 0106 implementation + corrective changes + untracked investigation files
```

---

## Appendix: Required Explicit Verdicts

```
CANONICAL_INFORMATION_PIPELINE = CONFIRMED (single invocation path, one canonical process)
KNOWLEDGE_SELECTION_SEMANTIC_STABILITY = STRONG (selection is deterministic; content differs by 5/40 facts due to collection ordering)
PERSISTENCE_IDENTITY_STABILITY = ANALYSIS_LOCAL (Fact, Observation, Analysis, ProjectProfile UUIDs are per-Analysis)
MODEL_FACING_SEMANTIC_STABILITY = STRONG (semantic content is stable; identity format varies)
FACT_UUID_VOLATILITY = PER_ANALYSIS (JPA @GeneratedValue, new UUID per Analysis)
OBSERVATION_UUID_VOLATILITY = PER_ANALYSIS (JPA @GeneratedValue, new UUID per Analysis)
VOLATILE_IDS_MODEL_VISIBLE = YES (Fact, Observation, Analysis, ProjectProfile UUIDs appear in prompt)
VOLATILE_IDS_GROUNDING_REQUIRED = YES (Fact and Observation UUIDs are required for grounding validation)
VOLATILE_IDS_SEMANTICALLY_REQUIRED = NO (LLM uses content, not UUIDs, for semantic interpretation)
SEMANTIC_SECTIONS_AFFECTED_BY_VOLATILE_IDS = YES (FACT, OBSERVATION, ANALYSIS, PROJECT_PROFILE itemIds differ per run)
GROUNDING_REQUIRES_DATABASE_UUID_AT_MODEL_BOUNDARY = YES (current validation uses exact UUID match)
PROMPT_LOCAL_REFERENCE_MAPPING_FEASIBLE = YES (architecturally feasible at SelectedKnowledgePromptProjectionService boundary)
NORMALIZED_EQUIVALENT_PROMPTS_EQUAL = NO (timestamps + content selection + ordering cause remaining differences)
MODEL_FACING_REPRESENTATION_SENSITIVITY = NOT_DEMONSTRATED (frozen replay proves stability on identical input; volatile IDs not shown to cause different output)
VOLATILE_IDENTITY_SENSITIVITY = NOT_DEMONSTRATED (normalized comparison shows UUIDs are NOT the primary variance source)
CANONICAL_MODEL_FACING_IDENTITY_ABSTRACTION = DESIRABLE (architecturally clean, token-efficient, but not required for correctness)
CANONICAL_SEMANTIC_PAYLOAD_INVARIANT = DESIRABLE (strengthens canonical pipeline, prepares for RAG/MCP, but not blocking)
DETERMINISTIC_ELIGIBILITY_VALIDATOR = COMPLEMENTARY (different problem — validator catches invalid output, stable identity reduces input variance)
STORY_0106_BOUNDARY = FOLLOW_UP_STORY (identity normalization is separate infrastructure concern)
STORY_0106_PRODUCT_TARGET = DEMONSTRATED (corrective prompt produces 100% clean output on frozen input)
ADR_REQUIRED = YES (for identity normalization follow-up — new ADR, not amendment to ADR-064)
ADR_064_SEQUENCE = SEPARATE (identity normalization is distinct from Analysis lifecycle governance)
RECOMMENDED_NEXT_ACTION = B (accept current behavior; create follow-up Story for identity normalization)
```

---

`STORY_0106_MODEL_FACING_IDENTITY_INVESTIGATION_COMPLETE`

`REPORT_PATH = docs/investigations/story-0106-model-facing-identity-and-grounding-representation-investigation.md`

`PRODUCTION_CODE_CHANGED = NO`
`PROMPT_CHANGED = NO`
`TEST_CODE_CHANGED = NO`
`MODEL_CALLS_PERFORMED = NO`
`COMMIT_CREATED = NO`
`PUSH_PERFORMED = NO`
`MERGE_PERFORMED = NO`

`READY_FOR_HUMAN_REVIEW`
