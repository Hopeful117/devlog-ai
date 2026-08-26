# Implementation Plan — Story 0094

## Summary

Repair Engineering Event grounding reliability inside the AI Engine only:
explicit copy-exact contract with separated allow-lists in the prompt,
offender-naming validation, diagnostic corrective retry, and honest
provider-vs-invalid error classification. Backend/DB untouched; validator
strictness unchanged.

## Production Changes

### File: `ai-engine/app/prompts/engineering_event.py`

1. `build()` — replace the bare GROUNDING CONTRACT JSON dump with a section
   that embeds the three allow-lists under explicit headers plus the copy-exact
   prose (adapted from insight.py semantics):
   - copy values exactly from the corresponding allowed list;
   - never derive/shorten/extend/construct IDs;
   - use `[]` for supportingFactIds only when observations or evidence
     references ground the proposal (schema requires ≥1 grounding element);
   - IDs appearing elsewhere in SelectedKnowledge (project/source/analysis/
     event identifiers, commit hashes) are NOT citable unless present in the
     allow-lists.
2. `corrective_retry(original, error)` — append a structured CORRECTIVE RETRY
   block: the error text (now offender-naming), an explicit restatement that
   the ALLOWED_* lists inside the original message are the only valid sources,
   and the copy-exact instruction.
3. Template identifier stays `analyze-engineering-event-prompt-v1` (backend-
   owned intent identity); content digest changes naturally.

### File: `ai-engine/app/services/engineering_event_generation_service.py`

1. `_generate` — replace first-violation aborts with per-field offender-naming
   errors (`<field> contains references absent from the allowed list: [ids]`)
   while keeping duplicate-proposal detection unchanged.
2. Retry exception handling — split:
   - `(ValidationError, EngineeringEventOutputError, ValueError)` →
     `INVALID_LLM_OUTPUT` (terminal after bounded single retry);
   - bare `Exception` → `LLM_PROVIDER_ERROR` (honest provider classification),
     mirroring `insight_generation_service.process`.

## Versioning Decision

Template id unchanged (see repository-analysis §6): backend-owned identity +
content_digest reproducibility, matching the insight prompt's established
in-place hardening precedent. No silent misleading version: persisted
prompt_content_digest changes and is recorded on every task.

## Test Changes

### File: `ai-engine/tests/test_engineering_event.py` (extend)

Prompt-contract tests (semantic fragments, not full snapshots):

1. user message contains `ALLOWED_SUPPORTING_FACT_IDS`, `ALLOWED_SUPPORTING_
   OBSERVATION_IDS`, `ALLOWED_EVIDENCE_REFERENCES`;
2. user message states copy-exact rule and "IDs that appear elsewhere … are
   not valid";
3. empty-list nuance stated (observations/evidence grounding requirement);
4. corrective retry prompt contains offending ID, both allowed IDs, and the
   copy-exact instruction.

Validation matrix via service `_generate` (allowed = {A,B}):

| model output | expectation |
|---|---|
| [A] | accepted |
| [A, B] | accepted |
| [random uuid] | rejected, message lists the random uuid |
| [sourceId-style uuid not in facts] | rejected |
| [] + observations grounding | accepted (schema nuance) |
| [] everywhere | rejected by schema (`requires grounding`) |

Service-level behavior tests:

5. invalid first output → retry request contains diagnostics; second output
   valid → callback COMPLETED with proposals;
6. invalid twice → FAILED `INVALID_LLM_OUTPUT` with offending IDs in message,
   no third attempt;
7. provider timeout/connection error on attempt 1 → FAILED
   `LLM_PROVIDER_ERROR` without retry;
8. provider failure on attempt 2 → FAILED `LLM_PROVIDER_ERROR`
   (NOT INVALID_LLM_OUTPUT).

Existing tests must remain green (category acceptance, ungrounded rejection).

## Behavior Change

Before: opaque single-shot "Unknown supportingFactIds"; provider failures on
retry misreported as invalid output; ~50% chronic failure.

After: model receives an unambiguous citation contract; failures carry
offending values; terminal codes are truthful; validator strictness unchanged.

## Rollback/Safety

Two-file AI Engine change; revert restores prior behavior. No schema/API/
contract changes; backend ignores unknown fields it already validates.

## Non-Goals

Backend Java/DB · MCP · ranking/budget · RepositoryContextAdapter · freshness/
sync · deliverables · Cockpit UI · insight.py refactor · provider choice.
