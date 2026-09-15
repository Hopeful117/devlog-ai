# Story 0129 - Repository Analysis

## Investigation Baseline

| Field | Value |
|---|---|
| Branch | `main` |
| HEAD | `d05598202413e81f64bc70a5fed171f603e535ef` |
| ORIGIN_MAIN | `d05598202413e81f64bc70a5fed171f603e535ef` |
| Worktree | Modified generated `devlog-contracts/target/*`; untracked `docs/discoveries/`; untracked Story 0127 document |
| Story 0128 merged | YES, merge commit `d055982` |
| ADR-068 | Accepted and present in the merged baseline |
| Production/test changes in this investigation | NONE |

The unrelated generated and untracked worktree changes were preserved. The
repository, rather than historical reports, is the source of truth.

## Post-0128 Architecture

The implemented `architecture-overview-v3` flow is:

```text
SelectedKnowledge
    -> AiReferenceRegistryFactory / AiReferenceRegistry
    -> AiReferenceMappingSnapshot on AiTask
    -> toTypedArchitectureOverviewMap()
    -> PromptRequest typed context + groundingCandidates
    -> Python TypedInsightGenerationOutput
    -> AiTaskResultRequest typed references
    -> findByCorrelationIdForUpdate()
    -> AiReferenceResolver from the originating snapshot
    -> resolved domain identities
    -> existing ValidatableProposal(PROPOSED)
```

| Invariant | Current evidence |
|---|---|
| `JAVA_CORE_AUTHORITY` | Registry allocation, snapshot persistence, resolver and Java callback validation |
| `PYTHON_DEFENSIVE_VALIDATION` | Pydantic typed models and candidate-subset checks in `InsightGenerationService` |
| `PROVIDER_OPAQUE_REFERENCES` | `ProviderAiReference` and Python `ProviderAiReference` expose only `type/ref/scope` |
| `EXECUTION_SCOPED_RESOLUTION` | `AiTaskResultServiceImpl` reconstructs `AiReferenceResolver` from the task snapshot |
| `GROUNDING_CAPABILITY_VALIDATION` | Resolver checks `SUPPORTING_FACT`, `SUPPORTING_OBSERVATION` and `EVIDENCE_REFERENCE` |
| `PROPOSAL_LIFECYCLE` | Resolved output is converted into the existing proposal persistence path |
| `LEGACY_COMPATIBILITY` | v1/v2 use raw contracts; typed fields are rejected outside v3; null snapshots are not reconstructed |

## Legacy AI-Facing Path Inventory

| Flow | Intent/version | Provider sees raw identity? | Provider returns identity? | Grounding authority? | Typed snapshot available? | Complexity | Risk |
|---|---|---:|---:|---|---:|---|---|
| Generic project description | `describe-project-v1` / `INSIGHT_GENERATION` | Yes, Facts, Observations and evidence strings | Yes, raw `supportingFactIds`, `supportingObservationIds`, `evidenceReferences` | Java validator and selected snapshot | Yes for task preparation, not consumed for resolution | Medium | Medium |
| Architecture overview legacy | `architecture-overview-v1` / `INSIGHT_GENERATION` | Yes | Yes, raw grounding fields | Java validator and selected snapshot | Yes for preparation, legacy callback | Low | Medium, compatibility constrained |
| Architecture overview synthesis legacy | `architecture-overview-v2` / `INSIGHT_GENERATION` | Yes | Yes, raw proposal and `groundingReferences` | Java validator and synthesis rules | Yes for preparation, legacy callback | Medium | Medium/high |
| Architecture overview typed | `architecture-overview-v3` / `INSIGHT_GENERATION` | No for citable v3 projection | Yes, typed refs | Java task snapshot resolver | Yes and consumed | Medium | **High until corrective fix** |
| Engineering event | `analyze-engineering-event-v1` / `EVENT_PROPOSAL_GENERATION` | Yes | Yes, raw Fact/Observation UUIDs and evidence strings | Python allow-list plus Java proposal validator | Snapshot may be created but is not consumed | Medium | Medium/high |
| Engineering decision | `analyze-engineering-decision-v1` / `DECISION_PROPOSAL_GENERATION` | Context contains raw IDs/strings | Callback carries empty raw grounding lists | Java payload validator; output is effectively ungrounded | Snapshot may be created but is not consumed | Medium/high | High, but requires a decision-specific contract |
| Story Context Analysis | `engineering-story-context-analysis-v1` / `STORY_CONTEXT_ANALYSIS` | Yes, raw evidence references and context IDs | Yes, raw evidence strings in `EvidenceRef` | Java-authored `allowedEvidenceReferences`, Java callback validation | Snapshot is persisted but not consumed | High | High |
| Deliverable generation | Direct deliverable API, no Core intent task | No relevant Core identity contract | No proposal grounding callback | Deliverable endpoint/service | No applicable task snapshot | Low | Low for ADR-068 |

Display-only identities are not equivalent to grounding authority. The highest
priority remaining provider-returned grounding paths are Story Context Analysis,
Engineering Events, generic Insight intents and legacy architecture versions.

## Story Context Analysis Assessment

The path is:

```text
AnalyzeStoryContextUseCase.execute()
    -> EngineeringContext + SelectedKnowledge
    -> raw selectedKnowledgeSnapshot + groundingContract
    -> AiTask STORY_CONTEXT_ANALYSIS
    -> StoryContextAnalysisPromptBuilder v1
    -> StoryContextAnalysisResult with EvidenceRef.reference strings
    -> callback
    -> AnalyzeStoryContextUseCase.handleCallback()
    -> StoryContextAnalysis persistence
```

Story Context already persists an `AiReferenceMappingSnapshot` when its task is
created, but its provider input and callback still use
`allowedEvidenceReferences` and raw evidence strings. It is a serious next
migration candidate because it is a distinct non-proposal result path and would
exercise typed repository evidence without changing proposal promotion.

It is not selected for Story 0129 because the existing v3 path has a confirmed
authorization defect. Expanding the migration before closing that defect would
spread an insufficiently proven foundation.

## Confirmed Correctness Defect (Class A)

An `ENRICHES` v3 proposal may target an arbitrary project-scoped Insight rather
than an Insight present in `existingArchitectureKnowledge`.

Evidence:

- Python `_validate_typed_output()` checks only that `targetInsightRef` is an
  `INSIGHT` with `PROJECT` scope.
- Java `resolveTypedProposals()` resolves that reference and writes
  `targetInsightId` without checking architecture membership.
- Java `AiProposalContractValidator.validateArchitectureDelta()` returns early
  for typed v3 before the existing architecture-target allow-list check.

This violates the architecture-delta meaning retained from v2 and the required
Core rule that a mapped identity belongs to the authorized task/context. It is
not a future enhancement; it can authorize enrichment of the wrong trusted
architecture record after human review.

## Additional Findings

### Class B - required hardening before broader migration

- Typed semantic-section items, facts, architecture knowledge and relationship
  endpoints silently lose their identity when registry lookup fails.
- Repository relationship endpoints can fail to map when endpoint canonical
  identity differs from the registered evidence identity.

These are fail-closed projection concerns and should be covered by the
corrective Story where the affected citable projection is in scope.

### Class D - useful but not a blocker

- The callback envelope remains a shared backward-compatible DTO rather than a
  version-specific Java DTO.
- The Python callback envelope is permissive about unknown envelope fields.
- Diagnostics are server-side and do not expose the internal mapping snapshot.

### Class E - explicitly deferred

- Retrieval, embeddings, vector search, ContextPack and RAG orchestration.
- DevLog Agent runtime or tool-use infrastructure.

## Candidate Ranking

| Candidate | Product value | Architecture value | Reuse of 0128 | Isolation | Risk | Scope | Recommendation |
|---|---|---|---|---|---|---|---|
| Harden v3 target authorization and fail-closed projection | HIGH | VERY_HIGH | VERY_HIGH | VERY_HIGH | LOW/MEDIUM | NARROW | **Now: corrective Story 0129** |
| Typed Story Context Analysis | HIGH | VERY_HIGH | HIGH | HIGH | MEDIUM | MEDIUM/HIGH | Next after correction |
| Typed Engineering Event grounding | MEDIUM/HIGH | HIGH | HIGH | HIGH | MEDIUM/HIGH | MEDIUM | Later |
| Typed Decision grounding | MEDIUM | HIGH | MEDIUM | MEDIUM | HIGH | HIGH | Later, after decision contract clarification |
| Global typed-reference migration | Unclear | LOW incremental value | LOW | LOW | VERY_HIGH | Unbounded | Reject |

## Classification And Decision

| Finding | Classification |
|---|---|
| v3 architecture enrichment target bypasses architecture membership | A - correctness defect |
| Silent loss of unresolved typed citable identities | B - generic infrastructure gap before expansion |
| Typed Story Context Analysis | C - candidate migration |
| Shared callback DTO strictness | D - useful improvement, not prerequisite |
| Retrieval/RAG concerns | E - future concern |

```text
NEXT = CORRECTIVE_STORY
TOP_CANDIDATE = Harden v3 typed-reference integrity
SECOND_CANDIDATE = Typed Story Context Analysis
```

ADR-068 is sufficient for the corrective work. No new ADR is required and no
new versioning mechanism is justified. Story 0129 must not implement Story
Context Analysis or a global migration.
