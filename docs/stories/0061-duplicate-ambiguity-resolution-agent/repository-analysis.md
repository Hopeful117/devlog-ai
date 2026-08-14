# Story 0061 — Duplicate Ambiguity Resolution Agent — Repository Analysis

## Existing Codebase Patterns

### Duplicate Detection Infrastructure

The codebase already has a robust duplicate detection system:

1. **`TrustedKnowledgeDuplicateAuditService`** — deterministic audit engine
   - Groups insights by exact fingerprint (type, sourceType, title, content, rationale)
   - BFS-based topic clustering for semantic duplicates
   - Richness scoring based on sourceType, rationale, confidence, evidence references, content length
   - Classification: `EXACT_DUPLICATE`, `LIKELY_SEMANTIC_DUPLICATE`, `LIKELY_RICHER_SUCCESSOR`, `REVIEW_REQUIRED`

2. **`MaintenanceEvaluationServiceImpl`** — orchestrates maintenance findings
   - Calls `duplicateAuditService.audit(projectId)` to get clusters
   - Maps clusters to `MaintenanceFinding` entities via `duplicateDebtRequest()`
   - Skips equivalent active findings to avoid duplicates

3. **`MaintenanceFinding`** — persisted finding entities
   - `TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE` — medium severity, human review required
   - `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW` — medium severity, human review required
   - `TRUSTED_KNOWLEDGE_EXACT_DUPLICATE` — high severity, human review required

### Assessment Infrastructure (Story 0060)

The assessment model is already in place:

1. **`MaintenanceAssessment`** entity — confidence, classification, rationale, recommended action
2. **`MaintenanceAssessmentService`** — persistence layer for assessments
3. **Enums**: `MaintenanceAssessmentConfidenceLevel`, `MaintenanceAssessmentSemanticClassification`, `MaintenanceAssessmentRecommendedAction`

### AI Infrastructure

The codebase uses an external AI Engine microservice:

1. **`AIEngineClient`** — interface for submitting prompts to AI Engine
2. **`RestAIEngineClient`** — REST implementation using Spring RestClient
3. **Callback flow** — AI Engine calls back with results asynchronously
4. **`AiTaskType`** — enum for task types (currently: DECISION_PROPOSAL_GENERATION, EVENT_PROPOSAL_GENERATION, INSIGHT_GENERATION, DOCUMENTATION_GENERATION, CHALLENGE_PROPOSAL_GENERATION)

## Design Decisions

### Rule-Based vs AI-Based for Story 0061

**Decision**: Implement a rule-based agent for Story 0061, with AI integration deferred to a later story.

**Rationale**:
- The deterministic layer already provides rich cluster metadata (category, rationale, member details)
- A rule-based approach can handle the majority of cases with high confidence
- AI integration requires significant infrastructure work (new AiTaskType, prompt templates, callback handling)
- The story scope is bounded to the semantic-overlap problem domain
- The agent can be enhanced with AI in a follow-up story

### Agent Architecture

**Decision**: Create a `DuplicateAmbiguityResolutionAgent` component that evaluates cluster metadata.

**Rationale**:
- Follows the bounded agent pattern from ADR-054
- Separates evaluation logic from persistence (MaintenanceAssessmentService)
- Can be called synchronously after finding creation
- Easy to test in isolation

### Integration Point

**Decision**: Integrate agent into `MaintenanceEvaluationServiceImpl.evaluate()` method.

**Rationale**:
- Agent is called after each duplicate finding is created
- Agent has access to the cluster data from the audit
- Assessment creation is wrapped in try-catch to prevent agent failures from blocking evaluation

## Key File Paths

| Component | Path |
|-----------|------|
| DuplicateAmbiguityResolutionAgent | `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/agent/DuplicateAmbiguityResolutionAgent.java` |
| MaintenanceEvaluationServiceImpl | `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceEvaluationServiceImpl.java` |
| MaintenanceAssessmentService | `backend/src/main/java/com/hopeful117/devlogai/contextmaintenance/service/MaintenanceAssessmentService.java` |
| TrustedKnowledgeDuplicateAuditService | `backend/src/main/java/com/hopeful117/devlogai/insight/service/TrustedKnowledgeDuplicateAuditService.java` |
| InsightDuplicateClusterResponse | `backend/src/main/java/com/hopeful117/devlogai/insight/dto/response/InsightDuplicateClusterResponse.java` |
