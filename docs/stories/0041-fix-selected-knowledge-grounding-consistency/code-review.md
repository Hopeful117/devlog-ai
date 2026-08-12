# Story 0041 — Fix Selected Knowledge Grounding Consistency — Code Review

## Status

Reviewed

## Review Scope

Review of the selected-knowledge grounding consistency bugfix:

* `KnowledgeSelectionServiceImpl`
* backend regression tests for closure and budget pressure
* AI-engine regression test for copied `supportingFactIds`
* verification results from targeted tests and full backend quality gate

## Findings

No blocking findings.

### 1. The fix is applied at the correct boundary ✅

The root cause was upstream from validation:

* selected observations exposed fact IDs;
* selected facts were budgeted independently;
* validators later enforced the stricter subset rule.

The implementation fixes the selected snapshot itself rather than weakening the
contract in Python or Java.

That is the right architectural choice.

### 2. The bounded-selection tradeoff is coherent ✅

Under fact-budget pressure, the implementation now drops lower-priority
observations until their required supporting facts fit inside the fact budget.

That is preferable to:

* trimming `supportingFactIds`;
* exceeding the fact budget;
* or emitting a self-contradictory selected snapshot.

The tradeoff is explicit and deterministic.

### 3. Required facts are preserved without breaking discretionary deduplication ✅

The service now keeps all required support facts by identity while still
deduplicating discretionary facts by `type + content`.

This is an important nuance:

* required support facts are part of observation truthfulness;
* discretionary facts remain budget-optimized.

That split matches the Story intent well.

### 4. Regression coverage matches the bug shape ✅

The new tests cover:

* a low-ranked required support fact that must still be retained;
* budget pressure that forces observation reduction;
* AI-engine acceptance when a visible `supportingFactId` is also present in
  `selectedFacts`;
* continued failure for truly out-of-context fact IDs through the existing
  negative path.

That gives good confidence the bug is fixed without masking the validator.

### 5. Residual assumption remains on base AnalysisContext coherence ⚠️

The implementation filters required fact IDs against the fact IDs available in
the ranked fact pool.

This is acceptable because Repository Analysis showed the inconsistency is
introduced in selection, not in `AnalysisContextServiceImpl`.

Still, if a future bug made the base `AnalysisContext` itself inconsistent,
this Story would not surface a dedicated failure earlier than selection-time
behavior.

This is not a blocker for Story 0041, but it remains an invariant worth
protecting in the analysis-context layer.

## Gate Results

* `./mvnw -Dtest=KnowledgeSelectionServiceTest,KnowledgeSelectionServiceAdditionalTest test`: **PASS**
* `./.venv/bin/python -m pytest tests/test_insight_generation_service.py -q`: **PASS**
* backend `./mvnw verify`: **PASS**
* JaCoCo coverage checks: **PASS**
* `git diff --check`: **PASS**

## Conclusion

Approve.

The Story fixes the deterministic contract mismatch at the right layer,
preserves strict grounding validation, and adds the right regression coverage
for both the backend selector and the AI-engine consumer path.
