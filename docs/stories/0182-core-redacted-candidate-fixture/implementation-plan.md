# Implementation Plan - Story 0182

## Objective

Add a Core-authorized, bounded, redacted candidate fixture to the existing
Story Context evaluation path, then use it to replay the current and two
instrumented selection alternatives offline. No production selection or budget
behavior will change.

## Approved Basis

- Story 0182 Repository Analysis was explicitly approved by the human.
- Story 0181 provides the existing bounded telemetry and capture correlation
  conventions.
- The current `RepositoryContext.SelectionDecision` already contains the
  authoritative reference, selected state, rejection reason, relevance score,
  and estimated tokens.
- Core `RepositoryEvidence` remains the source for category, provenance path,
  revision, and authorized trust/grounding state.

## Proposed Human Decisions

1. **Evaluation-only fixture.** Add the fixture to the existing Core-owned
   evaluation snapshot/ledger path, not to the supported REST/MCP product
   response.
2. **Bounded complete set.** Use a maximum of `4096` candidate rows, matching
   the offline comparator bound. If the authorized candidate set exceeds the
   bound, mark the capture non-evaluable rather than truncating it.
3. **Path policy.** Include only normalized candidate paths within the explicit
   requested-file scope. Candidates outside that scope carry no path.
4. **Replay scalar.** Expose the Core relevance score used by the selector as
   the deterministic replay score; do not expose explanations or content.
5. **Fail closed.** Reject unsupported versions, missing correlation digests,
   malformed rows, duplicate references, oversized fields, or digest mismatch.

## Planned Changes

### 1. Define the Core fixture contract

Add an immutable Core-owned fixture model adjacent to
`ContextLossLedger`, containing:

- fixture version and maximum row bound;
- task/revision/context/projection correlation fields;
- canonical candidate rows with reference, kind, optional requested-scope
  path, relevance score, trust, grounded, selected, projected, and bounded
  decision reason;
- canonical digest over deterministic row ordering and values.

The model must validate row count, field lengths, score/count ranges, allowed
trust values, canonical paths, duplicate references, and digest format.

### 2. Construct the fixture in Core

Extend `ContextLossLedger.capture(...)` or an immediate Core-owned collaborator
to derive rows from the same `RepositoryContext` and authorized context used by
the existing ledger:

- candidate references and kinds from Core repository evidence/decisions;
- relevance score and decision reason from `SelectionDecision`;
- trust from `canonical.trustByReference()`;
- grounding from Core authorized revision-aware references;
- projected state from the existing projection references;
- path only when it matches a normalized requested-file scope.

Do not change `BudgetedDiverseEvidenceSelector`, ranking, category ceilings,
item/token budgets, enrichment, projection, provider, or retry behavior.

### 3. Preserve capture and digest integrity

- Include the fixture in the existing ledger identity/digest.
- Correlate the fixture with task identity, target revision, context digest, and
  projection digest.
- Keep the existing 256-entry diagnostic ledger unchanged.
- Ensure all-or-nothing candidate coverage: oversized candidate sets become
  non-evaluable and are never silently truncated.

### 4. Extend Python extraction and validation

Update `story_context_budget_live.py` to transport only the allowlisted fixture
fields and `story_context_runtime_report.py` to validate:

- fixture version and row bound;
- correlation fields and digest;
- canonical row ordering and duplicate references;
- path, trust, score, status, and reason bounds;
- complete candidate coverage and explicit overflow classification.

Python must not construct missing candidate rows or infer trust/grounding.

### 5. Use the existing offline comparator

Update `story_context_policy_comparison.py` to consume the validated fixture
envelope and preserve the existing alternatives:

- `current`;
- `balanced`;
- `unrestricted`.

Each policy result must report candidate/rejected/selected distributions,
requested-file survival, selected trust, grounded selected count, and selected
count. The comparator must remain deterministic and must not call Core.

### 6. Add deterministic tests

Backend tests:

- fixture construction from candidate decisions;
- requested-scope path redaction;
- trust and revision-aware grounding;
- complete-set and duplicate-reference validation;
- bounds, digest correlation, and malformed fixture rejection;
- unchanged selector behavior.

Python tests:

- valid fixture extraction and digest validation;
- missing/oversized/malformed fixture non-evaluability;
- path and trust redaction;
- alternative policy trade-off output;
- requested-file, category, trust, and grounding comparisons.

Tests must not require provider credentials or a live provider.

### 7. Run live validation and produce the evaluation report

After implementation and container synchronization:

1. Run focused Core and Python tests.
2. Run the targeted B6000 smoke with an explicitly requested file.
3. Verify fixture completeness, correlation, and digest validation.
4. Run the four-scenario B6000 campaign only after the targeted smoke passes.
5. Run offline `current`/`balanced`/`unrestricted` comparison against the
   Core-generated fixture.
6. Produce a Story-local evaluation report that separates measurements from
   policy recommendations.
7. Make no production-policy recommendation if fixture or requested-file
   measurements are invalid.

## Files Expected To Change

- `backend/src/main/java/com/hopeful117/devlogai/storycontextanalysis/usecase/ContextLossLedger.java`
  or a directly adjacent Core-owned fixture model.
- Relevant backend Story Context and repository-context tests.
- `ai-engine/evaluations/story_context_budget_live.py`.
- `ai-engine/evaluations/story_context_runtime_report.py`.
- `ai-engine/evaluations/story_context_policy_comparison.py`.
- Evaluation tests and Story-local evaluation artifacts.

No selector, ranking, enrichment, projection, provider, retry, retrieval, or
context-budget policy implementation file is planned to change.

## Validation Plan

1. Run focused fixture/model tests.
2. Run evaluator and comparator tests.
3. Run backend compilation and relevant Story Context tests.
4. Verify redaction, bounds, duplicate rejection, and digest correlation.
5. Rebuild Docker images and verify service health.
6. Run targeted live smoke.
7. Run the bounded four-scenario campaign.
8. Run offline policy comparison and inspect trade-offs.
9. Run `git diff --check` and inspect the complete diff.

## Failure Handling

- Missing or incomplete Core fixture stops policy comparison.
- Oversized candidate sets are non-evaluable, never truncated silently.
- Paths outside the approved requested scope are omitted, not inferred.
- Missing trust or grounding remains unknown and cannot be promoted by Python.
- Provider timeout remains separate from fixture/readiness failure.
- Any need to expose the fixture through a supported public API stops the Story
  and requires separate architectural authorization.

## Documentation Reconciliation

Story-local evaluation and decision reports are expected. General REST, MCP,
operator, and deployment documentation should not change because the fixture is
evaluation-only.

## Human Approval Gate

Implementation must not begin until the human explicitly approves this current
Implementation Plan. Approval of Story 0182 and its Repository Analysis does
not authorize implementation until this gate is satisfied.
