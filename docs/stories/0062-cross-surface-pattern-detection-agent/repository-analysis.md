# Story 0062 — Cross-Surface Pattern Detection Agent — Repository Analysis

## Existing Codebase Patterns

### Context Surfaces

The codebase defines three context surfaces via `MaintenanceContextSurface`:

1. **`PROJECT_UNDERSTANDING`** — Staleness of trusted knowledge / repository understanding
   - Findings: `STALE_PROJECT_UNDERSTANDING`, `TRUSTED_KNOWLEDGE_EXACT_DUPLICATE`, `TRUSTED_KNOWLEDGE_SEMANTIC_DUPLICATE`, `TRUSTED_KNOWLEDGE_OVERLAP_REVIEW`

2. **`PROJECT_PROJECTION`** — Gaps in freshness projections
   - Findings: `MISSING_PROJECTION_REFRESH`

3. **`INTERNAL_HUMAN_CONTEXT`** — Staleness of human-authored context inputs
   - Findings: `STALE_HUMAN_CONTEXT_INPUT`

### Finding Lifecycle

- All findings are created in `MaintenanceEvaluationServiceImpl.evaluate()`
- Findings have status: `OPEN`, `ACKNOWLEDGED`, `RESOLVED`, `DISMISSED`
- Auto-resolution only applies to staleness findings (not duplicate debt)

### Assessment Model (Story 0060)

- `MaintenanceAssessmentSemanticClassification` already includes `CORRELATED_STALENESS` and `ISOLATED_SIGNAL`
- These values are defined but not yet produced by any agent

### Existing Agent Pattern (Story 0061)

- `DuplicateAmbiguityResolutionAgent` provides the pattern:
  - `@Component` class in the `agent` package
  - Single `evaluate()` method returning `Optional<AgentAssessmentResult>`
  - Integration in `MaintenanceEvaluationServiceImpl.evaluate()`
  - Low-confidence suppression

## Design Decisions

### Rule-Based vs AI-Based

**Decision**: Implement a rule-based agent for Story 0062.

**Rationale**:
- Cross-surface correlation is deterministic when based on existing findings
- The agent evaluates finding metadata (surface, issueType, status)
- AI integration can be added later for more nuanced pattern detection

### Pattern Detection Strategy

**Decision**: Detect two types of patterns:
1. **Correlated Staleness**: Multiple stale signals across surfaces
2. **Correlated Duplicate Debt**: Multiple duplicate findings

**Rationale**:
- These patterns align with ADR-054's identified reasoning domains
- They are the most impactful cross-surface patterns
- The deterministic layer already produces the findings needed

### Integration Point

**Decision**: Integrate agent at the end of `MaintenanceEvaluationServiceImpl.evaluate()`.

**Rationale**:
- Agent needs all findings to detect cross-surface patterns
- Runs after all deterministic findings are created
- Assessment is linked to the first contributing finding

## Key File Paths

| Component | Path |
|-----------|------|
| CrossSurfacePatternDetectionAgent | `backend/src/main/java/.../agent/CrossSurfacePatternDetectionAgent.java` |
| MaintenanceEvaluationServiceImpl | `backend/src/main/java/.../service/MaintenanceEvaluationServiceImpl.java` |
| MaintenanceContextSurface | `backend/src/main/java/.../entity/MaintenanceContextSurface.java` |
| MaintenanceFinding | `backend/src/main/java/.../entity/MaintenanceFinding.java` |
