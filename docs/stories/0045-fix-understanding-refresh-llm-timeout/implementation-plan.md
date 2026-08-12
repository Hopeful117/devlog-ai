# Story 0045 — Fix Understanding Refresh LLM Timeout — Implementation Plan

## Status

Planned

## Planning Goal

Apply the smallest deterministic fix that makes understanding refreshes usable
again after the grounding repairs, without masking prompt bloat behind a
timeout-only configuration increase.

## Key Decision

Treat the timeout primarily as an **AI-facing payload compaction problem**,
not as a transport problem.

Do **not** start with:

* a blind increase of `LLM_TIMEOUT_SECONDS`,
* a provider swap,
* or a broad redesign of repository-context ranking.

The current evidence shows that the refresh path reaches the AI Engine
successfully, then times out at the provider budget while sending an oversized
prompt dominated by `repositoryContext`.

## Why This Approach

The Repository Analysis established four important facts:

1. Core → AI Engine submission is fast and healthy.
2. The provider-side timeout aligns almost exactly with the configured
   `30s` LLM budget.
3. The persisted `SelectedKnowledge` snapshot is roughly `202k` characters.
4. The dominant contributor is `repositoryContext` at roughly `178k`
   characters, including internal ranking and accounting metadata.

That means the highest-value fix is to reduce the AI-facing shape of
`repositoryContext` before prompt construction, while keeping the grounding
contract strict.

## In-Scope Implementation Steps

### Step 1 — Introduce a compact AI-facing repository-context projection

Primary targets:

* `backend/src/main/java/com/hopeful117/devlogai/repositorycontext/RepositoryContext.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`

Planned change:

* define a compact repository-context payload used specifically for LLM-facing
  selected knowledge;
* keep the evidence actually useful to the model:
  * evidence `reference`
  * short `summary`
  * compact content/symbol payloads when present
  * any grounding-relevant fields
* remove or aggressively trim internal metadata not needed by the model:
  * score breakdown internals
  * `selectionDecisions`
  * rich ranking explanations
  * repository-context diagnostics/accounting details
  * verbose extraction/provenance fields unless strictly required

Design preference:

* preserve the existing internal repository-context model for deterministic
  selection and diagnostics;
* introduce a dedicated AI-facing projection rather than weakening the engine’s
  internal structure.

### Step 2 — Keep prompt construction and grounding semantics unchanged

Primary targets:

* `ai-engine/app/prompts/insight.py`
* `ai-engine/app/services/insight_generation_service.py`

Planned change:

* continue building prompts from `SelectedKnowledge`;
* avoid changing the grounding contract semantics;
* ensure the compacted projection still supports:
  * exact `evidenceReferences`
  * strict `supportingFactIds`
  * strict `supportingObservationIds`

The fix should shrink the payload while preserving the model contract, not
soften validation.

### Step 3 — Add observability for prompt-size regressions

Primary targets:

* AI Engine prompt execution logging
* relevant backend or AI Engine tests

Planned change:

* record or expose enough prompt-size information to make future payload
  explosions visible;
* prefer lightweight, deterministic observability such as:
  * user-message character count
  * serialized selected-knowledge size
  * compact repository-context evidence count

This should help prevent future regressions of the same class.

### Step 4 — Reevaluate timeout budget only after compaction

Possible targets:

* `.env.example`
* `docker-compose.yml`
* `ai-engine/app/core/config.py`

Planned rule:

* only adjust `LLM_TIMEOUT_SECONDS` if the compacted prompt still remains too
  close to the current `30s` boundary in the validated local scenario;
* any increase must be documented as a secondary operational tuning change, not
  the primary fix.

## Explicit Out-Of-Scope Choices

This Story will **not**:

* redesign the repository-context ranking engine
* remove repository-context evidence entirely
* weaken the grounding validator
* replace the OpenAI provider
* create provider-live flaky tests

Those would expand the Story far beyond the current failure.

## Files Likely To Change

Expected:

* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/SelectedKnowledge.java`
* `backend/src/main/java/com/hopeful117/devlogai/knowledge/selection/KnowledgeSelectionServiceImpl.java`
* one or more repository-context projection or mapping classes
* `ai-engine/app/prompts/insight.py`
* AI Engine and backend regression tests related to prompt size / timeout path

Possible:

* `docker-compose.yml`
* `.env.example`
* `ai-engine/app/core/config.py`

only if a small timeout adjustment is still justified after payload compaction.

## Validation Plan

At minimum:

* targeted backend regression tests for the compact projection
* targeted AI Engine tests for prompt construction or processing with the
  compacted payload
* `./mvnw verify`
* relevant `pytest` tests in `ai-engine`
* `git diff --check`

Operational validation:

* rerun the local understanding refresh scenario that currently produces
  `LLM_PROVIDER_ERROR`
* verify that the refresh no longer fails for timeout in that scenario

## Risks

### Risk 1 — Over-trimming useful evidence

Mitigation:

* trim internal metadata first, not primary evidence summaries/references;
* keep grounding references exact and test-covered.

### Risk 2 — Fixing the symptom but not the growth path

Mitigation:

* add prompt-size observability so future expansions are visible.

### Risk 3 — Hiding inefficiency behind a timeout increase

Mitigation:

* treat timeout tuning as optional and secondary;
* require compaction first.

## Planned Outcome

After this Story:

* understanding refresh should stop failing due to the current provider timeout
  in the validated local scenario;
* the LLM-facing selected-knowledge payload should be materially smaller;
* repository-context internals should remain available for deterministic
  selection and diagnostics, while the prompt receives only a compact
  projection;
* future prompt-size regressions should be easier to notice before they make
  refresh unusable again.
