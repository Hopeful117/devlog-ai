# Investigation Report: Connect Analysis Results to Trusted Engineering Artifacts (Story 101)

## 1. Current Proposal Domain Model

### ValidatableProposal Entity (`ValidatableProposal.java`)

**Fields & Identifiers:**
- `id` (UUID, PK) — unique proposal identifier
- `project` (Project, FK, not null) — owning project
- `analysis` (Analysis, FK, not null) — source analysis
- `aiTask` (AiTask, FK, nullable) — originating AI task
- `sourceIndex` (Integer, updatable=false) — source index for multi-source analyses
- `type` (ProposalType) — INSIGHT, ENGINEERING_DECISION, ENGINEERING_EVENT, CHALLENGE, DOCUMENTATION
- `status` (ProposalStatus) — PROPOSED, ACCEPTED, REJECTED
- `payload` (Map<String, Object>, JSONB) — structured proposal content
- `confidence` (BigDecimal) — AI confidence score
- `supportingFactIds` (List<UUID>) — deterministic fact references
- `supportingObservationIds` (List<UUID>) — deterministic observation references
- `evidenceReferences` (List<String>) — evidence references
- `createdAt` (Instant, created, immutable)
- `decidedAt` (Instant, nullable) — when proposal was decided

**Proposal Status Lifecycle:** PROPOSED → (ACCEPTED | REJECTED)

**Proposal Types:** INSIGHT, ENGINEERING_DECISION, ENGINEERING_EVENT, CHALLENGE, DOCUMENTATION

**Relationships:**
- `project` (ManyToOne, required) — owning project
- `analysis` (ManyToOne, required) — source analysis
- `aiTask` (ManyToOne, optional) — originating AI task
- **Reverse relationships (on promoted entities):**
  - `Insight.proposal` (OneToOne, required, unique)
  - `Decision.proposal` (OneToOne, optional, unique)
  - `EngineeringEvent.proposal` (OneToOne, required, unique)

**Proposal does NOT store reference to promoted domain object.**
**Reverse references exist on promoted entities (Insight, Decision, EngineeringEvent).**

---

## 2. Acceptance and Promotion Flow

### Entry Point: `ValidationController` → `ValidationServiceImpl.validate()`

```java
@PostMapping("/proposal/{proposalId}")
public ValidationResponse validate(@PathVariable UUID proposalId, @Valid @RequestBody CreateValidationRequest request)
```

### Execution Path (`ValidationServiceImpl.validate()`):

1. **Load proposal** — `proposalRepository.findByIdForValidation(proposalId)`
2. **Validate state** — must be `PROPOSED`, not already validated
3. **Create Validation entity** — `ValidationMapper.toEntity(request)`
4. **Duplicate guard check** — `trustedKnowledgeDuplicateGuard.assertCanAccept(proposal)` for ACCEPTED
5. **Set proposal status** — `proposal.setStatus(ACCEPTED|REJECTED)` + `decidedAt = now()`
6. **Persist proposal** — `proposalRepository.save(proposal)` (status becomes ACCEPTED)
8. **Promotion (only if ACCEPTED)** — `promotionService.promote(proposal, savedValidation, insightSeverity)`

### Promotion Flow (`ProposalPromotionService.promote()`):

```java
switch (proposal.getType()) {
    case INSIGHT -> insights.promote(proposal, validation, severity);
    case ENGINEERING_EVENT -> promoteEvent(proposal, validation, severity);
    case ENGINEERING_DECISION -> promoteDecision(proposal, validation);
}
```

**Type-specific Promotion:**

| Proposal Type | Promoted Entity | Key Fields Set |
|---------------|-----------------|----------------|
| INSIGHT | `Insight` | `.proposal(proposal)`, `.validation(validation)`, `.type()`, `.severity()`, `.title()`, `.content()`, `.rationale()`, `.confidence()`, `.evidenceReferences()`, `.sourceType()` |
| ENGINEERING_DECISION | `Decision` | `.proposal(proposal)`, `.title()`, `.context()`, `.choice()`, `.rationale()`, `.consequences()` |
| ENGINEERING_EVENT | `EngineeringEvent` | `.proposal(proposal)`, `.validation(validation)`, `.source()`, `.category()`, `.title()`, `.summary()`, `.significance()`, `.baseCommit()`, `.targetCommit()`, `.occurredAt()` |

**Key Points:**
- Proposal status becomes ACCEPTED at line 68 (`proposal.setStatus(ACCEPTED)`) in `ValidationServiceImpl`
- Promotion happens **after** validation is persisted (line 82: `promotionService.promote()`)
- Promotion is **within the same transaction** (`@Transactional` on `validate()`)
- Acceptance and promotion are **atomic** (same transaction)
- Promotion creates the trusted domain object and **sets `proposal` field** on it

### Trusted Artifact Types Currently Promotable

| Proposal Type | Promoted Entity | Repository | API Endpoint | Frontend Route |
|---------------|-----------------|------------|--------------|----------------|
| INSIGHT | `Insight` | `InsightRepository` | `GET /api/v1/insights/{id}` | `/insights/{id}` |
| ENGINEERING_DECISION | `Decision` | `DecisionRepository` | `GET /api/v1/decisions/{id}` | *(no detail page)* |
| ENGINEERING_EVENT | `EngineeringEvent` | `EngineeringEventRepository` | `GET /api/v1/engineering-events/{id}` | `/engineering-events/{id}` |

---

## 3. Trusted Artifact Types Currently Promotable

### 1. Insight (`Insight.java`)
- **Repository**: `InsightRepository`
- **API**: `GET /api/v1/insights/{id}`, `GET /api/v1/insights/analysis/{analysisId}`
- **Frontend**: `/insights/{id}` (InsightDetailPage), `/insights` (InsightsPage)
- **Provenance**: `proposal` (OneToOne), `validation` (OneToOne), `analysis`, `project`
- **Human-navigable**: Yes (`/insights/{id}`)

### 2. Decision (`Decision.java`)
- **Repository**: `DecisionRepository`
- **API**: `GET /api/v1/decisions/{id}` (no dedicated controller visible)
- **Frontend**: No dedicated detail page (referenced in proposal detail page)
- **Provenance**: `proposal` (OneToOne, unique), `project`
- **Human-navigable**: No dedicated page

### 3. EngineeringEvent (`EngineeringEvent.java`)
- **Repository**: `EngineeringEventRepository`
- **API**: `GET /api/v1/engineering-events/{id}`
- **Frontend**: `/engineering-events/{id}` (EngineeringEventDetailPage)
- **Provenance**: `proposal` (OneToOne), `validation`, `analysis`, `project`, `source`
- **Human-navigable**: Yes (`/engineering-events/{id}`)

### 4. GeneratedDeliverable
- **Repository**: `GeneratedDeliverableRepository`
- **API**: `POST /api/v1/deliverables`, `GET /api/v1/deliverables/{id}`
- **Frontend**: `/deliverables/{id}` (DeliverableDetailPage)
- **Provenance**: `analysis` (optional), `project`, `sourceInsights` (many-to-many Insight)
- **Human-navigable**: Yes (`/deliverables/{id}`)

---

## 4. Existing Provenance Relationships

### Explicit Proposal → Trusted Object Links

| Trusted Entity | Proposal Reference | Type | Persisted? |
|----------------|-------------------|------|------------|
| `Insight.proposal` | `@OneToOne` | `@JoinColumn(name="proposal_id", unique=true)` | ✅ |
| `Decision.proposal` | `@OneToOne` | `@JoinColumn(name="proposal_id", unique=true)` | ✅ |
| `EngineeringEvent.proposal` | `@OneToOne` | `@JoinColumn(name="proposal_id", unique=true)` | ✅ |

### Reverse Navigation (Query-Time)

| Query | Method |
|-------|--------|
| Insight by proposalId | `insightRepository.findByProposalId(proposalId)` |
| Decision by proposalId | `decisionRepository.findByProposalId(proposalId)` |
| EngineeringEvent by proposalId | `engineeringEventRepository.findByProposalIdIn(List<UUID>)` |
| Insight by proposalId (list) | `insightRepository.findByProposalIdIn(List<UUID>)` |

### Provenance in API Responses

| API Response | Includes `proposalId`? |
|--------------|------------------------|
| `InsightResponse` | ✅ `proposalId` |
| `EngineeringEventResponse` | ✅ `proposalId` |
| `DecisionResponse` | ❌ **Missing!** |
| `ValidatableProposalResponse` | ❌ (has `id` as `proposalId`) |
| `AnalysisResultResponse.ProposalSummary` | ✅ `proposalId` |
| `ProposalReviewItem` | ✅ `id` (proposal id) |

### Existing Provenance Search (Lineage Service)

`KnowledgeLifecycleDiagnosticService.diagnose(UUID proposalId)`:
- Resolves promoted artifacts by proposal type
- `INSIGHT` → `insightRepository.findByProposalIdIn()`
- `ENGINEERING_DECISION` → `decisionRepository.findByProposalId()`
- `ENGINEERING_EVENT` → `engineeringEventRepository.findByProposalIdIn()`

---

## 5. Current AnalysisResult Projection (Story 100)

### Proposal Summary in `AnalysisResultResponse`

```java
ProposalSummary {
    UUID id;                    // proposal.id
    ProposalType type;          // proposal.type
    ProposalStatus status;      // proposal.status
    Double confidence;          // proposal.confidence
    String title;               // extracted from payload
    String summary;             // extracted from payload
    List<String> evidencePreview; // Fact#xxx, Observation#xxx
    UUID proposalId;            // proposal.id  ← KEY FIELD
}
```

**Current Exposure:**
- ✅ Proposal ID exposed as `proposalId`
- ✅ Status filtering (PROPOSED + ACCEPTED only)
- ✅ Type, confidence, title, summary
- ✅ Evidence preview (top 3 facts, 2 observations)
- ✅ Next action: `REVIEW_PROPOSALS` with link to `/analyses/{id}/proposal-review`

**Missing from Proposal Summary:**
- ❌ No `promotedArtifactId` / `promotedArtifactType` / `promotedType`
- ❌ No direct link to trusted artifact
- ❌ ACCEPTED proposals don't show resulting artifact ID

### Angular `AnalysisResultPage` Proposal Display

Current `analysis-result-page.html` shows proposals as cards with:
- Type badge, status badge
- Title, summary
- Evidence preview (Fact#xxx)
- **Action button**: `[routerLink]="['/proposals', proposal.proposalId]"` → `/proposals/{id}` (ProposalDetailPage)

**No direct navigation to promoted trusted artifact.**

---

## 5. Existing Backend Endpoints & Frontend Routes

### Backend Endpoints

| Endpoint | Purpose |
|----------|---------|
| `GET /api/v1/analyses/{id}/result` | Canonical analysis result (Story 100) |
| `GET /api/v1/analyses/{id}/proposal-review` | Paginated proposal review |
| `GET /api/v1/proposals/{id}` | Proposal detail |
| `POST /api/v1/proposals/{id}/accept` | Accept proposal |
| `POST /api/v1/proposals/{id}/reject` | Reject proposal |
| `GET /api/v1/insights/{id}` | Insight detail |
| `GET /api/v1/insights/analysis/{analysisId}` | Insights by analysis |
| `GET /api/v1/engineering-events/{id}` | Engineering event detail |
| `GET /api/v1/deliverables/{id}` | Deliverable detail |

### Frontend Routes

| Route | Component | Purpose |
|-------|-----------|---------|
| `/analyses/{id}/result` | `AnalysisResultPage` | Canonical result (Story 100) |
| `/analyses/{id}/diagnostics` | `AnalysisDiagnosticsPage` | Expert diagnostics |
| `/analyses/{id}/proposal-review` | `ProposalReviewPage` | Sequential proposal review |
| `/proposals/{id}` | `ProposalDetailPage` | Single proposal review |
| `/insights/{id}` | `InsightDetailPage` | Validated insight detail |
| `/engineering-events/{id}` | `EngineeringEventDetailPage` | Engineering event detail |
| `/deliverables/{id}` | `DeliverableDetailPage` | Generated deliverable |

---

## 6. Provenance Gap Assessment

### Current State

| Relationship | Exists? | Mechanism |
|--------------|---------|-----------|
| Analysis → Proposals | ✅ | `proposalRepository.findByAnalysisId()` |
| Proposal → Promoted Artifact | ❌ | **Not stored on proposal** |
| Promoted Artifact → Proposal | ✅ | `proposal` OneToOne on Insight/Decision/Event |
| Proposal → Decision/Insight/Event ID | ❌ | Not stored on proposal |
| Reverse query (proposal → artifact) | ❌ | Requires separate queries |

### The Gap

**No durable `promotedArtifactId` / `promotedArtifactType` on `ValidatableProposal`.**

Current workarounds:
1. **Lineage service** (`KnowledgeLifecycleDiagnosticService`) reconstructs via reverse queries
2. **Frontend** (`ProposalDetailPage`) shows linked Insight if `proposal.status === 'ACCEPTED'` by querying `InsightService.getInsightsByAnalysis()` and matching `insight.proposalId === proposal.id`

### What's Missing for Story 101

To enable `AnalysisResultPage` → direct link to promoted artifact:
1. Need `promotedArtifactId` + `promotedArtifactType` on proposal (or query-time resolution)
2. Frontend needs to render "View Insight" / "View Decision" / "View Event" link for ACCEPTED proposals
3. Must not break existing ADR-006 guarantees (atomic promotion, immutable proposals)

---

## 6. Story 101 Design Options Assessment

### Option A: Existing Provenance is Sufficient (Query-Time Only)

**Assessment:** ❌ **Insufficient**

Current `AnalysisResultQueryService` composes proposals and insights separately. While it *could* join them at query time by:
```java
// Pseudo-code
insights.stream().filter(i -> proposals.anyMatch(p -> p.getId().equals(i.getProposalId())))
```

**Problems:**
- Insight is queried by `analysisId`, not `proposalId` (requires full scan or new query)
- `InsightRepository` has `findByProposalIdIn()` but not `findByProposalId()`
- Decision & EngineeringEvent have no `findByProposalId()` — only `findByProposalIdIn(List)`
- Would require new repository methods + DTO joins

### Option B: Reconstruct Indirectly (Query-Time Join)

**Assessment:** ⚠️ **Possible but Fragile**

Could extend `AnalysisResultQueryService` to:
1. Fetch all ACCEPTED proposals for analysis
2. For each proposal, query `InsightRepository.findByProposalId()` / `DecisionRepository` / `EngineeringEventRepository`
3. Join results into `ProposalSummary`

**Issues:**
- Requires adding `findByProposalId()` to `DecisionRepository` and `EngineeringEventRepository`
- N+1 query risk (mitigable with batch queries)
- Still no durable `promotedArtifactId` on proposal for direct navigation

### Option C: Durable Provenance (Minimal Domain Change) — **RECOMMENDED**

**Add durable `promotedArtifactId` + `promotedArtifactType` to `ValidatableProposal`:**

```java
@Entity
public class ValidatableProposal {
    // ... existing fields ...

    @Column(name = "promoted_artifact_id")
    private UUID promotedArtifactId;

    @Enumerated(EnumType.STRING)
    @Column(name = "promoted_artifact_type", length = 50)
    private PromotedArtifactType promotedArtifactType;
}

public enum PromotedArtifactType {
    INSIGHT,
    ENGINEERING_DECISION,
    ENGINEERING_EVENT
}
```

**Set at promotion time (in `ProposalPromotionService`):**

```java
public void promote(ValidatableProposal proposal, Validation validation, InsightSeverity severity) {
    switch (proposal.getType()) {
        case INSIGHT -> {
            PromotionResult result = insights.promote(proposal, validation, severity);
            proposal.setPromotedArtifactId(result.getInsight().getId());
            proposal.setPromotedArtifactType(PromotedArtifactType.INSIGHT);
        }
        case ENGINEERING_EVENT -> {
            EngineeringEvent event = promoteEvent(...);
            proposal.setPromotedArtifactId(event.getId());
            proposal.setPromotedArtifactType(PromotedArtifactType.ENGINEERING_EVENT);
        }
        case ENGINEERING_DECISION -> {
            Decision decision = promoteDecision(...);
            proposal.setPromotedArtifactId(decision.getId());
            proposal.setPromotedArtifactType(PromotedArtifactType.ENGINEERING_DECISION);
        }
    }
}
```

**Benefits:**
- Durable, queryable link from proposal → promoted artifact
- Single query in `AnalysisResultQueryService`: `proposal.getPromotedArtifactId()`
- No new ADR required (adds columns to existing table)
- Preserves ADR-006: proposal remains immutable after ACCEPTED, promotion still atomic
- Angular can directly construct navigation: `/insights/{promotedArtifactId}`, `/decisions/{id}`, `/engineering-events/{id}`

---

## 7. Recommended Minimal Architecture for Story 101

### Backend Changes

1. **Domain Model** (`ValidatableProposal.java`):
   - Add `promotedArtifactId` (UUID, nullable)
   - Add `promotedArtifactType` (enum, nullable)
   - Add `@Column` annotations

2. **Promotion Service** (`ProposalPromotionService.java`):
   - Set `promotedArtifactId` and `promotedArtifactType` on accepted proposal
   - Return promoted artifact ID from `InsightPromotionService.promote()`

3. **Repository** (`ValidatableProposalRepository.java`):
   - No changes needed (existing queries work)

4. **DTO** (`ValidatableProposalResponse.java`):
   - Add `promotedArtifactId` and `promotedArtifactType` fields

5. **Query Service** (`AnalysisResultQueryServiceImpl.java`):
   - Include `promotedArtifactId` + `promotedArtifactType` in `ProposalSummary`

6. **Controller** (`ValidatableProposalController.java`):
   - No changes (existing DTO includes new fields)

### Frontend Changes

1. **Model** (`analysis.models.ts`):
   - Add `promotedArtifactId` and `promotedArtifactType` to `ProposalSummary`
   - Add `PromotedArtifactType` enum

2. **Analysis Result Page** (`analysis-result-page.html`):
   - For ACCEPTED proposals, show "View Insight" / "View Decision" / "View Event" link
   - Route construction: `/insights/{id}`, `/decisions/{id}`, `/engineering-events/{id}`

2. **Analysis Result Page** (`analysis-result-page.ts`):
   - Helper for route construction

---

## 8. New ADR Required?

**NO.** This change:
- Adds columns to existing `validatable_proposals` table (no new table)
- Does not change AI behavior, proposal generation, or ADR-006 guarantees
- Promotion remains atomic within the same transaction
- AI still cannot create trusted knowledge directly
- Proposal remains immutable after ACCEPTED

---

## 9. Candidate Acceptance Criteria for Story 101

1. **AC1**: `GET /api/v1/analyses/{id}/result` returns `promotedArtifactId` and `promotedArtifactType` for each ACCEPTED proposal in `proposals.items`
2. **AC2**: Angular `/analyses/{id}/result` displays "View Insight" / "View Decision" / "View Event" link for ACCEPTED proposals with valid `promotedArtifactId`
3. **AC3**: Clicking "View Insight" navigates to `/insights/{promotedArtifactId}` (existing route)
4. **AC4**: "View Decision" navigates to `/decisions/{promotedArtifactId}` (new route needed) or integrates into existing page
5. **AC5**: "View Engineering Event" navigates to `/engineering-events/{promotedArtifactId}` (existing route)
5. **AC6**: Existing proposals with ACCEPTED status get `promotedArtifactId` backfilled via migration
6. **AC7**: All existing tests pass; new tests cover projection and navigation

---

## 10. Risks / Technical Debt Discovered

| Risk | Severity | Mitigation |
|------|----------|------------|
| `DecisionResponse` missing `proposalId` | Medium | Add `proposalId` to `DecisionResponse` for consistency |
| `Decision` no detail page | Medium | Create `/decisions/{id}` route or embed in proposal detail |
| Migration for existing ACCEPTED proposals | Medium | One-time migration script to populate `promotedArtifactId` |
| `EngineeringEvent` promotion sets `proposal` but no `decidedAt` sync | Low | Verify `proposal.setDecidedAt()` is called before promotion |
| `CHALLENGE` and `DOCUMENTATION` types have no promotion handler | Low | Document as intentionally unsupported; throw clear error |

---

## 11. Files Likely Affected by Story 101

### Backend
| File | Change Type |
|------|-------------|
| `ValidatableProposal.java` | Add fields + enum |
| `ProposalPromotionService.java` | Set promoted artifact on promotion |
| `InsightPromotionService.java` | Return `PromotionResult` with saved Insight |
| `ValidatableProposalResponse.java` | Add fields to DTO |
| `AnalysisResultQueryServiceImpl.java` | Include promoted artifact in projection |
| `ValidatableProposalRepository.java` | No change |
| `DecisionRepository.java` | Add `findByProposalId(UUID)` |
| `EngineeringEventRepository.java` | Add `findByProposalId(UUID)` |
| `ValidatableProposalController.java` | No change (DTO auto-serialized) |

### Frontend
| File | Change Type |
|------|-------------|
| `analysis.models.ts` | Add fields to `ProposalSummary`, add `PromotedArtifactType` enum |
| `analysis-result-page.html` | Add "View X" links for ACCEPTED proposals |
| `analysis-result-page.ts` | Helper for route construction |
| `analysis-result-page.spec.ts` | Test new links |
| `insights-page.ts` / `insights-page.html` | Add `/decisions/{id}` route if needed |
| `app.routes.ts` | Add `/decisions/:id` route if Decision detail page added |

### Database
- Migration: `ALTER TABLE validatable_proposals ADD COLUMN promoted_artifact_id UUID, ADD COLUMN promoted_artifact_type VARCHAR(50)`
- Migration: Backfill existing ACCEPTED proposals via `KnowledgeLifecycleDiagnosticService.resolvePromoted()`

---

## 12. Decision

### `READY_TO_DESIGN_STORY_101`

**Rationale:**
- Architecture is well-understood
- Minimal domain change (2 columns + enum) establishes durable provenance
- All promoted artifact types (Insight, Decision, EngineeringEvent) already have `proposal` reverse reference
- No ADR required; no changes to AI, proposal generation, or ADR-006
- Clear path to query-time projection and Angular navigation
- Migration path for existing data is straightforward via existing lineage service

**Next Step:** Human approval to proceed with Story 101 design document.