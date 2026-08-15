# Code Review — Story 0076

## Verdict

The implementation is clean and respects the architectural separation between deterministic duplicate prevention and semantic similarity awareness. The `InsightPromotionService.promote()` contract is changed from `void` to `PromotionResult`, which is a minimal, non-breaking API change when viewed from the caller side (the `ValidationService` and `ValidationController` are updated to handle the new return type). The semantic similarity computation uses the existing `InsightSimilarityService` without reinterpreting its thresholds as block/warn/allow — the similarity score is returned as factual metadata, and the blocking/decision logic remains the responsibility of the caller (or is intentionally absent, per the revised principle). All changes are backward-compatible at the test level (754/754 tests pass).

## Review Findings

### Functional and Architectural Review

#### Promotion Result Contract (correct)

Changing `promote()` from `void` to `PromotionResult(promotedInsight, similarityAssessment)` is the minimal change that exposes the similarity assessment without forcing a blocking decision in the service layer. The `PromotionResult` is a value object (`@Value` Lombok) with two fields: `promotedInsight` and `similarityAssessment`. The `SimilarityAssessment` is likewise a value object with `hasClosestMatch`, `closestInsightId`, `closestInsightTitle`, and `similarityScore`. No `DUPLICATE` label is attached — the assessment is purely factual. This design respects the revised principle that "semantic similarity alone MUST NOT throw `ConflictException`".

#### Semantic Similarity Integration (correct)

The `assessSemanticSimilarity()` method in `InsightPromotionService` uses the existing `InsightSimilarityService.computeSimilarity()` with the project-scoped, status-aware comparison. The method is correct in that it:
* Limits comparison to ACTIVE insights for the same project
* Finds the best matching insight and score
* Returns `hasClosestMatch=false` and `similarityScore=0.0` when no existing insights are found
* The score is in [0.0, 1.0] range, computed via TF-IDF cosine similarity

The method is **not** correct in the sense of making a blocking decision — it merely exposes metadata. The caller ( `ValidationService.validate()` ) must decide what to do with the score. This is the right separation of concerns.

#### Non-insight Proposal Handling (correct)

When `proposal.getType() != ProposalType.INSIGHT`, the service returns a `PromotionResult` with `null` promotedInsight and a default `SimilarityAssessment`. This is a clean edge-case handling that doesn't throw exceptions or create findings for non-insight types.

#### Enrichment Relation Preservation (correct)

The `createEnrichmentRelationIfNeeded()` method is preserved unchanged. The `deltaType=ENRICHES` flow continues to work as before, creating `KnowledgeRelation(DERIVED_FROM)` relations. The similarity assessment is computed after the insight is saved and the enrichment relation is created, which is the correct ordering.

### Test Review

* `InsightPromotionServiceTest`: 5 new/updated tests verify the new promotion result contract, similarity assessment for various scenarios (high similarity, no existing insights, incomplete payload, severity validation, enrichment relation), and all pass
* `TrustedKnowledgeDuplicateGuardTest`: 0 affected — the exact duplicate guard is unchanged and still blocks at validation time
* `MaintenanceEvaluationServiceTest`: 0 affected — the finding-creation logic is unchanged
* **754 backend tests pass**, 0 failures, 0 errors — the full test suite validates that the new promotion result contract doesn't break any existing behavior

### Data and Compatibility Review

No database migrations required. No schema changes. The `PromotionResult` and `SimilarityAssessment` are new classes in the insight service layer, not API changes to existing endpoints. The `ValidationService` and `ValidationController` were updated to handle the new `PromotionResult` return type, but the public API contracts (JSON payloads) are either unchanged or extended with optional similarity fields. No frontend changes are required.

### Residual Risks

* **Low** — The `InsightPromotionService.assessSemanticSimilarity()` method loads ALL ACTIVE insights for the project and compares the candidate against each one. For projects with thousands of insights, this could be slow. The `maxCandidates` property (default: 50) from the original implementation plan is not yet in the code, but the performance is acceptable for the stated constraint of typical project sizes.
* **Low** — The similarity score is exposed in the promotion result but has no inherent interpretation (is 0.85 a "block" or a "warn"?). This is intentional — the interpretation is delegated to the caller. However, this means downstream consumers need to know the thresholds to make decisions.

### Repository Hygiene

* No secrets or credentials in any diff
* No hardcoded paths or environment-specific values
* All new code follows existing patterns (Lombok `@Value`, Spring `@Service`, Mockito `@InjectMocks`)
* Test coverage is maintained at the high level established by the existing suite (754/754)

## Verdict

Accept. The story implements the pre-promotion similarity awareness as non-blocking metadata, which is the correct architectural choice given the existing deterministic duplicate prevention by `TrustedKnowledgeDuplicateGuard`. The code is clean, the tests pass, and the separation of concerns between "exact duplicate blocking" and "semantic similarity awareness" is well-defined.