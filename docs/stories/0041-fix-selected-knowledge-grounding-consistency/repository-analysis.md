# Story 0041 — Fix Selected Knowledge Grounding Consistency — Repository Analysis

## Purpose

Understand why the project-understanding / insight-generation refresh path can
fail with:

```text
INVALID_LLM_OUTPUT: supportingFactIds contains references absent from AnalysisContext
```

and determine the minimum deterministic fix that restores grounding consistency
without weakening the AI output validator.

## Story Understanding

This is not primarily a prompt-quality issue and not a reason to relax the AI
contract.

The error occurs after the model returns a proposal and the AI Engine validates
that proposal against the `SelectedKnowledge` snapshot it received.

The core question is:

* why can the model legitimately see a fact identifier in selected knowledge,
  then be rejected when it returns that identifier in `supportingFactIds`?

The answer is in the shape of `SelectedKnowledge`, not in the idea of strict
grounding itself.

## Relevant Components

### `KnowledgeSelectionServiceImpl`

File:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`

Current behavior:

* selects observations and facts independently:
  - `selectedObservations` comes from ranked observations with an observation
    budget;
  - `selectedFacts` comes from ranked facts with a fact budget;
* does not enforce closure between:
  - the selected observations;
  - the facts referenced by those observations’ `supportingFactIds`.

This is the most important bug source.

### `AnalysisContext.ObservationSnapshot`

File:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContext.java`

Current behavior:

* every observation snapshot carries:
  - `id`
  - `type`
  - `content`
  - `ruleId`
  - `ruleVersion`
  - `supportingFactIds`

That means selected observations expose fact identifiers directly to the AI
layer.

### `AnalysisContextServiceImpl`

File:

* `backend/src/main/java/com/hopeful117/devlogai/analysis/context/AnalysisContextServiceImpl.java`

Current behavior:

* builds `ObservationSnapshot.supportingFactIds` from the real persisted
  observation-to-fact relation;
* sorts those UUIDs deterministically;
* does not itself create the inconsistency.

Interpretation:

* the base `AnalysisContext` is internally coherent;
* inconsistency is introduced later, during knowledge selection.

### `InsightPromptBuilder`

File:

* `ai-engine/app/prompts/insight.py`

Current behavior:

* sends the complete `selectedKnowledge` JSON to the model;
* exposes exact allowed grounding values through:
  - `allowedSupportingFactIds`
  - `allowedSupportingObservationIds`
  - `allowedEvidenceReferences`
* builds `allowedSupportingFactIds` from `selectedFacts` only;
* still includes selected observations in the untrusted selected-knowledge body.

Important consequence:

* if a selected observation contains a fact UUID that is not present in
  `selectedFacts`, the model can still see that UUID in the observation payload
  even though the grounding contract forbids it.

### `InsightGenerationService`

File:

* `ai-engine/app/services/insight_generation_service.py`

Current behavior:

* validates model output by requiring:
  - `supportingFactIds ⊆ selectedFacts.id`
  - `supportingObservationIds ⊆ selectedObservations.id`
  - `evidenceReferences ⊆ allowed evidence references`
* raises:

```text
supportingFactIds contains references absent from AnalysisContext
```

when the proposal returns a fact UUID not present in `selectedFacts`.

The validator is behaving correctly.

### `AiProposalContractValidator`

File:

* `backend/src/main/java/com/hopeful117/devlogai/ai/engine/service/AiProposalContractValidator.java`

Current behavior:

* mirrors the same strict subset rule against the persisted selected-knowledge
  snapshot.

Interpretation:

* both Core and AI Engine already agree on the correct contract;
* the bug is upstream from validation.

## Root Cause

The selected-knowledge snapshot can become internally inconsistent.

Specifically:

1. `AnalysisContextServiceImpl` builds coherent observations with real
   `supportingFactIds`.
2. `KnowledgeSelectionServiceImpl` selects:
   * observations by one ranking/budget path;
   * facts by another ranking/budget path.
3. A selected observation may therefore retain `supportingFactIds` pointing to
   facts that were not retained in `selectedFacts`.
4. `InsightPromptBuilder` still exposes the selected observation payload to the
   model.
5. The model can copy one of those visible fact UUIDs into
   `supportingFactIds`.
6. `InsightGenerationService` rejects the output because that UUID is absent
   from the authorized `selectedFacts` set.

So the error is deterministic:

* the model can be grounded on data that the validator later disallows.

This is a selected-snapshot consistency bug.

## Why this matches prior grounding history

This pattern is consistent with a previous grounding lesson:

* on 2026-08-09, a similar issue was identified where strict grounding was
  correct, but the selected knowledge available to the AI could not satisfy the
  validator because the allowed fact set was incomplete. Source:
  `memory/2026-08-09.md#L110-L118`

The earlier lesson was:

* do not weaken the grounding contract;
* fix the deterministic knowledge supplied to the AI.

The current bug follows the same principle.

## Reproduction Logic

The bug does not require random LLM behavior.

Minimal failing shape:

* selected observation:
  - `id = O1`
  - `supportingFactIds = [F1]`
* selected facts:
  - does **not** contain `F1`

If the model returns:

* `supportingObservationIds = [O1]`
* `supportingFactIds = [F1]`

then `InsightGenerationService._require_subset(...)` fails exactly as observed.

## Candidate Fixes

### Option A — Trim observation `supportingFactIds` to selected facts

Approach:

* keep the current fact budget unchanged;
* rewrite each selected observation so its `supportingFactIds` only references
  facts still present in `selectedFacts`.

Pros:

* small implementation footprint;
* preserves current fact count budget.

Cons:

* mutates observation semantics after selection;
* an observation can become less trustworthy or look partially detached from
  its real deterministic support;
* the model may lose meaningful grounding continuity.

Verdict:

* acceptable as fallback, but not preferred.

### Option B — Enforce selected-fact closure for selected observations

Approach:

* once observations are selected, compute the transitive set of supporting fact
  UUIDs they reference;
* ensure every referenced fact is present in `selectedFacts`;
* then fill remaining fact budget with ranked discretionary facts.

Pros:

* preserves the truthfulness of selected observations;
* keeps the snapshot internally coherent;
* respects the existing strict validator;
* matches the principle that deterministic context should satisfy the contract
  before it reaches the AI.

Cons:

* may slightly reduce room for purely discretionary facts when many selected
  observations require support facts.

Verdict:

* recommended.

### Option C — Relax the validator to accept fact IDs seen only inside observations

Approach:

* accept any fact UUID that appears anywhere in selected observations.

Pros:

* very small code change.

Cons:

* weakens the explicit grounding contract;
* makes `selectedFacts` less meaningful as the authoritative fact allow-list;
* increases drift between prompt contract and validation semantics.

Verdict:

* reject.

## Recommended Fix Direction

Implement Option B:

* `selectedFacts` should become the closure-preserving authoritative fact set
  for the selected observations;
* any selected observation must be fully supported by visible selected facts;
* the remaining fact budget can still be used for additional ranked facts when
  capacity remains.

This keeps:

* prompt contract unchanged;
* Python validator unchanged;
* Java proposal validator unchanged;
* strict grounding guarantees intact.

## Expected Affected Files

Primary:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceTest.java`
* `backend/src/test/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceAdditionalTest.java`
* `ai-engine/tests/test_insight_generation_service.py`

Potentially relevant:

* `ai-engine/app/services/insight_generation_service.py` only if a more
  explicit regression fixture is useful, not because the validator appears
  wrong

Not expected to change:

* `AnalysisContextServiceImpl`
* `AiProposalContractValidator`
* `InsightPromptBuilder`

unless implementation reveals a second inconsistency.

## Risks

### 1. Fact budget semantics change subtly

If support-fact closure is added naïvely, total selected-fact count may exceed
  the configured budget or may reorder facts nondeterministically.

Mitigation:

* preserve `maximumFacts`;
* include required support facts first with stable ordering;
* fill remaining slots deterministically.

### 2. Silent ranking regression

If closure logic is added after ranking without explicit tests, important ranked
facts may disappear unexpectedly.

Mitigation:

* add tests that prove:
  - dangling references are impossible;
  - selection remains deterministic;
  - budget remains bounded.

### 3. Fixing only the AI-engine symptom

If the Python validator is loosened instead of the snapshot fixed, the same
class of inconsistency will remain in stored selected knowledge.

Mitigation:

* keep validators strict;
* fix the selected snapshot upstream.

## Gate Recommendation

Approve Repository Analysis.

The root cause is concrete, deterministic, and narrowly scoped. The most
appropriate fix is to restore internal grounding consistency in
`SelectedKnowledge` rather than weakening validation.
