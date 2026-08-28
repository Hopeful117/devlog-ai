# Investigation: Current Analysis Result Representation

## Executive Summary

The current system lacks a single canonical human-facing Analysis result projection. Results are fragmented across multiple domain objects, APIs, and Angular surfaces. The human must reconstruct the result by navigating between:

- **Analysis** (execution metadata, status)
- **AiTask** (prompt/execution metadata, selected knowledge snapshot)
- **ValidatableProposal** (AI-generated proposals awaiting review)
- **Insight** (validated proposals promoted to trusted knowledge)
- **GeneratedDeliverable** (human-authored documents from insights)
- **Diagnostics/SelectedEvidence** (evidence provenance, collection details)
- **Project Profile** (deterministic summary)

No single API or UI surface presents a coherent "Analysis Result" that a human can consume without understanding internal orchestration concepts.

---

## 1. Domain Object Analysis

### 1.1 Analysis (`analysis` table)
**Purpose**: Orchestration aggregate; tracks execution lifecycle
**Key Fields**:
- `id`, `project_id`, `selected_source_id`, `selected_source_snapshot`
- `type` (ARCHITECTURE_REVIEW, PROJECT_EVOLUTION, TECHNICAL_DEBT, SECURITY_REVIEW, DOCUMENTATION_REVIEW)
- `intent_id`, `intent_version`
- `user_guidance`
- `status` (PENDING, IN_PROGRESS, COMPLETED, FAILED)
- `target_revision`
- `started_at`, `completed_at`
- `understanding_execution_key`, `evolution_execution_key` (deduplication)

**What it lacks**: No direct reference to results (proposals, insights, deliverables). Only execution metadata.

### 1.2 AiTask (`ai_tasks` table)
**Purpose**: Single AI Engine execution record
**Key Fields**:
- `analysis_id` (FK), `correlation_id` (unique)
- `task_type` (DECISION_PROPOSAL_GENERATION, EVENT_PROPOSAL_GENERATION, INSIGHT_GENERATION, DOCUMENTATION_GENERATION, CHALLENGE_PROPOSAL_GENERATION)
- `intent_snapshot`, `user_guidance_snapshot` (immutable at creation)
- `prompt_request_id`, `prompt_version`, `provider`, `model_identifier`
- `prompt_content_digest`, `context_digest`
- `selected_knowledge_snapshot` (the evidence sent to AI)
- `selection_version`, `selection_digest`
- `context_snapshot` (RepositoryContext sent to AI)
- `status` (CREATED, SUBMITTED, PROCESSING, COMPLETED, FAILED)
- `external_job_id`, `attempt_count`, `failure_code`, `failure_message`

**What it lacks**: Does not contain the AI response/proposals. Only input context and execution metadata.

### 1.3 ValidatableProposal (`validatable_proposals` table)
**Purpose**: AI-generated proposal awaiting human validation (ADR-006)
**Key Fields**:
- `project_id`, `analysis_id`, `ai_task_id` (FK)
- `source_index` (which source in multi-source analysis)
- `type` (INSIGHT, ENGINEERING_DECISION, ENGINEERING_EVENT, CHALLENGE, DOCUMENTATION)
- `status` (PROPOSED, ACCEPTED, REJECTED)
- `payload` (JSON - the proposal content)
- `confidence` (BigDecimal)
- `supporting_fact_ids`, `supporting_observation_ids`, `evidence_references`
- `created_at`, `decided_at`

**Relationship**: One AiTask → many ValidatableProposals (one per proposal in AI response)

### 1.4 Insight (`insights` table)
**Purpose**: Validated proposal promoted to trusted knowledge (ADR-006)
**Key Fields**:
- `project_id`, `analysis_id` (FK)
- `proposal_id` (OneToOne, unique, FK to ValidatableProposal)
- `validation_id` (OneToOne, unique, FK to Validation)
- `type` (InsightType enum), `severity` (InsightSeverity), `status` (ACTIVE, SUPERSEDED, ARCHIVED)
- `title`, `content`, `rationale`, `confidence`
- `evidence_references`, `source_type`
- `created_at`, `updated_at`

**Relationship**: ValidatableProposal (ACCEPTED) → Insight (1:1 via proposal_id)

### 1.5 GeneratedDeliverable (`generated_deliverables` table)
**Purpose**: Human-authored documents from insights (post-validation)
**Key Fields**:
- `project_id`, `analysis_id` (optional FK)
- `type` (DeliverableType), `audience`, `style`, `language`, `additional_guidance`
- `title`, `content` (TEXT)
- `prompt_version`, `prompt_digest`, `provider`, `model_identifier`
- `generated_at`
- `source_insights` (ManyToMany with Insight)

### 1.6 Validation (`validations` table)
**Purpose**: Records human decision on a proposal
**Key Fields** (from code review):
- `proposal_id` (FK), `decision` (ACCEPTED/REJECTED), `validated_by`, `comment`, `validated_at`

---

## 2. Current API Contracts

### 2.1 Analysis APIs (`AnalysisController`)
| Endpoint | Returns | Purpose |
|---|---|---|
| `GET /api/v1/analyses/{id}` | `AnalysisResponse` | Basic analysis metadata |
| `GET /api/v1/analyses/{id}/diagnostics` | `AnalysisDiagnosticsResponse` | Execution diagnostics (counts, pipeline, aiTask, profile) |
| `GET /api/v1/analyses/{id}/selected-evidence` | `AiTaskSelectedEvidenceResponse` | Selected knowledge snapshot with categories |
| `GET /api/v1/analyses/{id}/warnings` | `CollectionWarningResponse[]` | Collection warnings |
| `GET /api/v1/analyses/{id}/context` | `Map<String, Object>` | Raw analysis context |
| `GET /api/v1/analyses/{id}/profile` | `ProjectProfile` | Deterministic profile |

**Gap**: No endpoint returns proposals, insights, or deliverables for an analysis.

### 2.2 Proposal APIs
| Endpoint | Returns |
|---|---|
| `GET /api/v1/analyses/{id}/proposal-review` | `ProposalReviewResponse` (paginated proposals with evidence) |
| `POST /api/v1/proposals/{id}/accept` | Accepts proposal → promotes to Insight/Event/Decision |
| `POST /api/v1/proposals/{id}/reject` | Rejects proposal |

### 2.3 Insight APIs
| Endpoint | Returns |
|---|---|
| `GET /api/v1/insights/analysis/{analysisId}` | Insights for analysis |
| `GET /api/v1/insights/{id}` | Single insight |

### 2.4 Deliverable APIs
| Endpoint | Returns |
|---|---|
| `POST /api/v1/deliverables` | Generate deliverable from insights |
| `GET /api/v1/deliverables/{id}` | Deliverable details |

---

## 3. Current Angular Result Surfaces

### 3.1 Analysis Detail Page (`analysis-detail-page`)
**Tabs/Sections**:
1. **Analysis Metadata** (type, status, intent, timestamps)
2. **Diagnostics** (collection counts, pipeline, AI task summary, profile)
3. **AI Execution** (AiTask metadata, prompt digests, correlation ID, attempts)
4. **Selected Evidence** (`AiTaskSelectedEvidenceSection` - categorized evidence)
5. **Project Profile** (deterministic summary)
6. **Warnings** (collection warnings)
7. **Analysis Context** (raw JSON)

### 3.2 Insights Section (`analysis-insights-section`)
**Two lists**:
1. **Insight Proposals** (from `InsightProposalService.getProposalsByAnalysis`) - shows count by status, links to proposal review
2. **Validated Insights** (from `InsightService.getInsightsByAnalysis`) - links to insight detail + deliverable panel

### 3.3 Proposal Review Page (`proposal-review-page`)
**Sequential carousel** for human validation:
- One proposal at a time with evidence (facts/observations)
- Accept/Reject decision with severity (for INSIGHT), comment, reviewer identity
- Session persistence (local reviewer UUID)

### 3.4 Deliverable Panel (`analysis-deliverable-panel`)
**Form** to generate deliverables from validated insights (audience, style, language, guidance)

---

## 4. Key Fragmentation Problems

| Problem | Impact |
|---|---|
| **No canonical "Analysis Result" object** | Human must mentally merge Analysis + AiTask + Proposals + Insights + Deliverables |
| **Internal concepts leak to UI** | AiTask, correlationId, prompt digests, selection digest, selection version exposed in primary detail page |
| **Result split across 7+ API calls** | Analysis + Diagnostics + SelectedEvidence + Proposals + Insights + Profile + Deliverables |
| **Proposal review is separate page** | Not integrated into Analysis result view |
| **Deliverables are afterthought** | Only accessible from Insights section, not from Analysis result |
| **Evidence provenance buried** | `AiTaskSelectedEvidenceResponse` is 500+ lines of nested DTOs, shown as raw JSON in Context tab |
| **Execution metadata mixed with results** | Diagnostics shows both collection metrics AND aiTask summary in same response |

---

## 5. ADR Compliance Check

| ADR | Requirement | Current State |
|---|---|---|
| ADR-006 | Proposals untrusted until individual human validation | ✅ Enforced via ProposalReviewService |
| ADR-017 | Analysis and AiTask separate; snapshots immutable | ✅ Analysis ↔ AiTask 1:N, snapshots immutable |
| ADR-020 | Provider callback and proposal persistence | ✅ AiEngineClient → AiTask → ValidatableProposal |
| ADR-063 | Context retrieval/composition ownership | ✅ Single bounded envelope (60 items) |
| Human Context Supremacy | Evidence inspectability | ✅ SelectedEvidence exposes all categories |
| Trusted knowledge promotion | Validated proposals → Insights/Events/Decisions | ✅ ProposalPromotionService |

---

## 6. Data Flow Summary

```
Analysis (PENDING)
  → AnalysisWorkflowService.start()
      → KnowledgeCollectionService.collect() → Facts/Observations
      → DeterministicAnalysisService.analyze()
      → ProjectProfileService.build()
      → AnalysisContextService.build() → RepositoryContext
      → AiTaskService.create() → AiTask (CREATED)
      → KnowledgeSelectionService.select() → SelectedKnowledge
      → AiTaskService.attachSelectedKnowledge()
      → AIEngineClient.submit() → externalJobId
      → AiTaskService.submit() → AiTask (SUBMITTED)
      → [async] AI Engine processes → callback
      → AiProposalContractValidator.validate() → ValidatableProposal[] persisted
      → AiTask (COMPLETED)
      → Analysis (COMPLETED)
```

**Result artifacts created**:
- `ValidatableProposal[]` (one per AI proposal)
- `AiTask` with `selected_knowledge_snapshot` and `context_snapshot`
- `ProjectProfile` (deterministic)
- `AnalysisDiagnostics` (collection metrics)

**Human validation flow**:
```
ValidatableProposal (PROPOSED)
  → Human reviews via ProposalReviewPage
  → POST /accept or /reject
  → Validation persisted
  → If ACCEPTED: ProposalPromotionService.promote()
      → INSIGHT → Insight + Validation
      → ENGINEERING_EVENT → EngineeringEvent + Validation
      → ENGINEERING_DECISION → Decision + Validation
```

**Deliverable generation** (post-validation, human-initiated):
```
Insights → GenerateDeliverableRequest (audience, style, language, guidance)
  → AI Engine → GeneratedDeliverable (content, title, metadata)
```

---

## 7. What the Human Currently Sees vs. What They Need

| Current (Fragmented) | Needed (Canonical) |
|---|---|
| Analysis metadata (type, status, intent) | **Analysis Result Header**: objective, scope, status, completion time |
| Diagnostics (counts, pipeline, collectors) | **Execution Summary**: success/failure, duration, sources analyzed |
| AI Task metadata (provider, model, digests) | **AI Execution**: hidden by default; available in diagnostics |
| Selected Evidence (8 categories, 500+ line DTO) | **Supporting Evidence**: curated, human-readable, provenance-aware |
| Proposals (separate page, paginated) | **Proposals**: inline in result, grouped by type, with validation status |
| Insights (separate list) | **Validated Insights**: inline, with severity, rationale, evidence |
| Deliverables (separate panel, post-validation) | **Deliverables**: inline, linked from insights, generation status |
| Project Profile (deterministic summary) | **Project Summary**: integrated into result header |

---

## 8. Conclusion

The canonical human-facing Analysis result does not exist as a single domain concept or API projection. It is an emergent composition of:
1. **Analysis** (execution record)
2. **ValidatableProposal[]** (AI output awaiting validation)
3. **Insight[]** (validated trusted knowledge)
4. **GeneratedDeliverable[]** (human-authored outputs)
5. **Supporting Evidence** (facts, observations, repository evidence, prior insights)
6. **Execution Diagnostics** (collection, selection, AI execution metadata)

**Recommendation**: Create a new read-model/projection `AnalysisResult` that composes these fragments into a coherent human-facing product surface, with a dedicated API endpoint and Angular component. The internal domain objects (Analysis, AiTask, ValidatableProposal, Insight, GeneratedDeliverable) remain unchanged; only the projection layer is added.