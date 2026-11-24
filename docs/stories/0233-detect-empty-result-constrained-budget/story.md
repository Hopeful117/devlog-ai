# Story 0233 — Détecter et signaler le résultat vide après budget excessivement contraint

## Status

DESIGN_APPROVED

## Context

During the qualitative evaluation of the epic "Fiabiliser les réponses DevLog pour les agents"
(2026-11-24), Config B (`devlog_story_agent` with story 0228, 2-package file scope) returned
`status: COMPLETED` with an entirely empty result: `architectureFindings: []`,
`decisionFindings: []`, `objectiveUnderstanding.summary: ""`.

The budget dropped to 25 selected candidates (from ~60 without file scope). Neither Python
validation (`_validate_output`) nor Java validation (`StoryContextResultValidator`) detected
the empty result — it passed all schema checks and was promoted to `COMPLETED`.

A consumer receiving `COMPLETED` with no content has no way to distinguish a genuinely empty
context from a system failure. This is a violation of the acceptability contract established
in Story 0210.

**Finding reference**: Epic evaluation finding F2 — "Résultat vide silencieux (défaut d'acceptabilité)"

## Governing ADRs

- ADR-006 — AI output = `ValidatableProposal`; trust and acceptability rules
- Story 0210 — Story Context Analysis acceptability contract

## Design

### Python (primary guard) — `_validate_output()`

In `ai-engine/app/services/story_context_analysis_generation_service.py`, add an early guard
in `_validate_output()` before the per-finding loops:

```python
all_findings = (
    output.architecture_findings
    + output.decision_findings
    + output.evidence_findings
    + output.historical_context
    + output.constraint_findings
    + output.impacted_component_findings
)
if not all_findings and not output.objective_understanding.summary.strip():
    raise StoryContextAnalysisOutputValidationError(
        "Result is fully empty: no findings and no objective understanding summary. "
        "The context budget was too constrained to produce a substantive response."
    )
```

`StoryContextAnalysisOutputValidationError` with `retryable=True` triggers one corrective retry.
If the retry also returns empty, the second `except` block sends `FAILED` with
`STRUCTURAL_CONTRACT_ERROR`.

### Python (secondary) — `_validate_requested_file_grounding()`

If `file_grounded_refs` is empty (no candidates mapped to the requested files), add an early
return before the per-finding loop to avoid a spurious `GROUNDING_REQUESTED_FILE_SCOPE` failure:

```python
if not file_grounded_refs:
    return  # No refs mapped to requested files — grounding check is vacuously satisfied
```

### Java (safety net) — `StoryContextResultValidator`

In `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/StoryContextResultValidator.java`,
add a secondary guard:

```java
boolean hasFindings = !result.architectureFindings().isEmpty()
    || !result.decisionFindings().isEmpty()
    || !result.evidenceFindings().isEmpty()
    || !result.historicalContext().isEmpty()
    || !result.constraintFindings().isEmpty()
    || !result.impactedComponentFindings().isEmpty();
boolean hasObjective = result.objectiveUnderstanding() != null
    && !result.objectiveUnderstanding().summary().isBlank();
if (!hasFindings && !hasObjective) {
    throw new IllegalStateException("Analysis result is fully empty");
}
```

## Acceptance Criteria

1. `devlog_story_agent` with a file scope that yields < 30 selected candidates and an empty
   AI result returns `FAILED` or `ABSTAINED`, never `COMPLETED` with empty content.
2. The `missingInformation` or `failureCode` field is populated with a human-readable message.
3. A result with at least one non-empty finding or a non-blank `summary` is unaffected.
4. `StoryContextResultValidatorTest` covers the empty-result rejection path.
5. `test_story_context_analysis_generation_service.py` covers the Python guard.
