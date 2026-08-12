# Story 0042 — Fix Analysis Context Grounding Closure — Code Review

## Status

Reviewed

## Review Scope

Review of the source-context grounding closure bugfix:

* `AnalysisContextServiceImpl`
* backend regression tests for source-context closure and fact-budget pressure
* verification results from targeted tests and full backend quality gate

## Findings

No blocking findings.

### 1. The fix is applied at the correct layer ✅

The remaining failure after Story 0041 came from the source `AnalysisContext`,
not from the selected-knowledge layer.

The implementation corrects `AnalysisContextServiceImpl` directly instead of:

* weakening validators;
* rewriting observation support metadata;
* or undoing Story 0041.

That is the right boundary.

### 2. Truthfulness is preserved under budget pressure ✅

The implementation keeps a hard fact budget and reduces retained observations
deterministically when their required support closure would overflow it.

That is preferable to:

* trimming `supportingFactIds`;
* exceeding the budget;
* or emitting a contradictory base context.

### 3. Layering with Story 0041 remains coherent ✅

Story 0041 still owns selected-snapshot closure.

Story 0042 now owns source-context closure.

That layered split is clear and avoids duplicated policy drift between
`AnalysisContext` construction and `SelectedKnowledge` construction.

### 4. Regression coverage matches the real bug shape ✅

The new tests cover:

* retention of required support facts that are absent from the initial ranked
  fact page;
* deterministic observation reduction when required support exceeds the fact
  budget;
* preservation of the closure invariant for all visible
  `supportingFactIds`.

This gives good confidence that the live failure shape is addressed.

### 5. Live retest still depends on the running local process ⚠️

Repository-level validation is green, but the previously failing refresh path
still depends on the local runtime being rebuilt/restarted before a live
verification can confirm the production symptom is gone.

This is not a blocker for code review because the repository change itself is
correct and well covered, but it remains the last practical verification step.

## Gate Results

* `./mvnw -Dtest=AnalysisContextServiceTest test`: **PASS**
* backend `./mvnw verify`: **PASS**
* JaCoCo coverage checks: **PASS**
* `git diff --check`: **PASS**

## Conclusion

Approve.

The Story fixes the remaining grounding inconsistency at the correct source
layer, preserves strict validation, and adds the right deterministic bounded
behavior for budget pressure.
