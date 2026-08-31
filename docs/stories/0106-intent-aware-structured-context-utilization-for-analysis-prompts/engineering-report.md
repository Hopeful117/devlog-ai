# Story 0106 — Engineering Report

## Status

**STORY_0106_FINALIZATION_READY_FOR_HUMAN_COMMIT_REVIEW**

## Architecture

Story 0106 remains a bounded prompt-utilization story.

```text
SelectedKnowledge / PromptProjection / PromptRequest transport
        ↓ unchanged
InsightPromptBuilder / EngineeringDecisionPromptBuilder
        ↓
shared structured-context utilization contract
        +
intent-specific synthesis guidance
        ↓
existing structured output contracts
```

## Production Changes

- `ai-engine/app/prompts/structured_context.py`
  added one minimal shared prompt-level instruction module for structured-context semantics
- `ai-engine/app/prompts/insight.py`
  added the shared contract and bounded intent-specific guidance for `describe-project-v1` and `architecture-overview-v1`; preserved grounding and architecture delta rules
- `ai-engine/app/prompts/decision.py`
  added the shared contract and bounded intent-specific guidance for project-specific decision reconstruction

## Boundary Verification

Verified unchanged:

- Java selection and budgets
- Semantic Section composition
- transport schema
- output schemas
- decision grounding contract
- persistence
- result projection
- frontend
- database
- provider API integration path

## RED / GREEN Evidence

### RED

- Command: `pytest tests/test_prompt_builder.py tests/test_decision_generation_service.py`
- Result: `6 failed, 20 passed`
- Missing behavior demonstrated by RED:
  - no Semantic Section index semantics
  - no no-double-counting rule
  - no project-evidence-over-generic-assumption rule
  - no describe-project synthesis guidance
  - no architecture section-emphasis guidance
  - no engineering-decision reconstruction guidance

### GREEN

- Command: `pytest tests/test_prompt_builder.py tests/test_decision_generation_service.py`
- Result: `26 passed`

## Full AI-Engine Verification

- Command: `pytest`
- Result: `89 passed`

## Prompt Size Measurement

Measurement method:

- UTF-8 byte length of rendered `system_message + user_message + expected_output_schema` using the same builder family as the runtime prompt path
- BEFORE measured against the authoritative post-0105 benchmark AI-task snapshots
- AFTER measured against the first post-change benchmark run for each intent

Results:

- `describe-project-v1`: `121004 -> 122717` bytes (`+1713`, `+1.42%`)
- `architecture-overview-v1`: `117619 -> 119288` bytes (`+1669`, `+1.42%`)
- `analyze-engineering-decision-v1`: `111661 -> 113322` bytes (`+1661`, `+1.49%`)

## Product Benchmark

Provider/model remained:

- `openai`
- `gpt-4.1-mini`

### describe-project-v1

- Run 1 analysis: `b7b9ad68-7d40-4624-9750-a574df3ad7d6`
- Repeat analysis: `6c5bb229-390b-4ba7-b634-954894c788f2`
- Both runs completed with `7` INSIGHT proposals
- Output remains mostly project-characteristic enumeration despite clearer grounding language

### architecture-overview-v1

- Analysis: `ddb31f60-cc38-47e8-a930-7cd686b54b8a`
- Completed with `0` proposals
- No false-positive architecture delta observed

### analyze-engineering-decision-v1

- Run 1 analysis: `ba91569f-4a82-435c-9ed8-8034c101230f`
- Repeat analysis: `0b16b32d-204c-40ad-98c6-55796a8d8176`
- Run 1 produced `1` ADR-focused decision, materially more specific and conservative than baseline
- Repeat produced `4` generic technology decisions similar to baseline behavior

## Interpretation

Story 0106 successfully implemented the approved prompt architecture with a low prompt-size increase and no upstream/downstream drift.

The benchmark evidence shows:

- structural prompt-utilization rules are now present
- architecture zero-delta safety is preserved
- decision generation can improve materially under the new prompt
- but the primary product-quality target is not yet demonstrated consistently across repeated decision runs

Human review is required to decide whether this implementation is sufficient or needs correction.

## Corrective Evolution

The first implementation did not pass immediately. Engineering-decision benchmarking exposed an under-specified emission threshold, and HUMAN review required correction. The corrective prompt then added the approved positive gate, negative gate, evidence-independence, convergence, rationale, selectivity, and zero-output semantics. The rebuilt running AI Engine was verified to contain those rules.

The subsequent corrective runtime benchmark produced `4/1/1`. This intermediate result demonstrated material improvement but did not establish strict consistency. Later exact-input replay froze one complete corrective PromptRequest and produced five clean ADR-only results:

```text
TOTAL_REPLAYS = 5
CLEAN_REPLAYS = 5
TECHNOLOGY_ONLY_FAILURES = 0
GENERIC_RATIONALE_FAILURES = 0
UNSUPPORTED_CAUSALITY_FAILURES = 0
```

The corrective prompt demonstrated stable, valid engineering-decision generation across five replays of the same exact PromptRequest. This is frozen-input evidence, not proof that the model is globally deterministic.

## Historical Causality Precision

The historical runtime variance cannot be attributed solely to prompt behavior or pure model stochasticity. A separate upstream comparator used Analysis-local Fact UUIDs as a semantic ranking tie-breaker. That defect was isolated and implemented independently as Story 0107.

```text
Story 0106 → interpretation of selected structured context by prompts
Story 0107 → persistence-identity-independent upstream Fact ranking
```

Story 0107 is outside this diff and does not invalidate Story 0106's frozen-input prompt evidence.

## Runtime Evidence Classification

- Corrective prompt present in running local/containerized AI Engine: `CONFIRMED_BY_RUNTIME`
- Corrective `4/1/1` benchmark: `CONFIRMED_BY_RUNTIME`
- Five clean exact-input replays: `CONFIRMED_BY_FROZEN_REPLAY`
- Shared contract, intent strategies, and seven decision semantics: `CONFIRMED_BY_CODE` and `CONFIRMED_BY_TEST`
- Sole cause of historical variance: `UNKNOWN`
- Global model determinism: `NOT_DEMONSTRATED`

## Acceptance Criteria Audit

- `AC-01 = PASS` — shared contract exists in `structured_context.py`, is consumed by both builders, and is covered by focused prompt tests.
- `AC-02 = PASS` — describe-project, architecture-overview, and engineering-decision intent strategies are present and covered by focused tests.
- `AC-03 = PASS` — the seven approved decision semantics are present across `SYSTEM_MESSAGE`, `_decision_strategy()`, and the shared contract; corrective tests and running prompt inspection confirm them.
- `AC-04 = PASS` — architecture trusted-knowledge and empty-delta behavior remain implemented and tested.
- `AC-05 = PASS` — selected knowledge and User Guidance remain within explicit untrusted delimiters; authoritative instructions remain outside.
- `AC-06 = PASS` — diff audit shows no Java, schema, persistence, REST, MCP, provider, model, temperature, or seed changes.
- `AC-07 = PASS` — focused/full tests pass, running prompt verification exists, and the frozen replay is `5/5` clean with appropriately bounded claims.
- `AC-08 = PASS` — no Story 0107 code is present; deferred items and paused ADR-064 status are preserved.

## Final Product Assessment

```text
STORY_0106_IMPLEMENTATION_CORRECTNESS = DEMONSTRATED
STORY_0106_PROMPT_UTILIZATION_OBJECTIVE = DEMONSTRATED
ENTIRE_ANALYSIS_PRODUCT_QUALITY = NOT_CLAIMED
```

Independent upstream/downstream product-quality topics remain outside this Story.

## Final Quality Gates

- Focused AI Engine: `pytest tests/test_prompt_builder.py tests/test_decision_generation_service.py` → `34 passed`
- Full AI Engine: `pytest` → `97 passed`
- Backend impact gate: `mvn test --no-transfer-progress` → `1,049 passed`, `BUILD SUCCESS`
- Python lint/type gates: not run because Ruff, mypy, and Black are not configured in `ai-engine/pyproject.toml` or repository scripts
- Story 0106 requires no backend production change; the backend gate verifies branch isolation without Story 0107

## Deferred Work

- deterministic cross-Analysis Fact ranking: Story 0107, separate
- documentation overflow policy: deferred
- model-facing identity normalization: deferred
- deterministic proposal eligibility validator: deferred
- ADR-064 next composition sequence: `KEEP_PAUSED`
