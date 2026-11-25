# Story 0245 - Citable Reference Aliases and Finalize on Refusal

## Status

**IMPLEMENTED - HUMAN ACCEPTANCE PENDING.**

Requested by the human on 2026-11-25 ("Aliases + finalize") after the
[Q1 Technical Gate 3 report](../../evaluation/devlog-ai-q1-gate-3-report.md).

## Epic

[Fiabiliser les reponses DevLog pour les agents](../../epics/agent-response-reliability/epic.md)

## Problem

Gate 3 failed on model compliance, which Core correctly rejected:

- **A.** A citable reference is about 110 characters (source UUID plus a full
  commit hash). The model cited ADR-006 with one mistyped hash character, and
  grounding validation rejected the whole answer (`GROUNDING_UNKNOWN_REFERENCE`).
- **C.** After a first search returned 10 in-scope items, the model's second
  action targeted a path outside the authorized scope. Core refused it
  (`PATH_OUT_OF_SCOPE`). Only budget refusals led to FINAL (Story 0244), so
  the task failed.

## Design

- **Aliases.**
  - The `CITABLE REFERENCES` block lists each reference as
    `R<n> = <exact reference>`. The numbering follows Core's order in the
    typed grounding allow-list and depends only on the grounding contract.
  - The requested-file scope mapping shows aliases.
  - The reference copy rule asks for the alias, or the exact reference copied
    character for character.
- **Resolution (AI Engine, before any validation).**
  `app/prompts/reference_aliases.resolve_reference_aliases` walks the provider
  result and replaces every `reference` field holding a known alias with its
  exact reference. It runs on the one-shot path and on the investigation FINAL
  path.
  - This is a deterministic table lookup, not a fuzzy match. An unknown alias
    is left unchanged and rejected by grounding validation. An exact reference
    is left unchanged.
  - Core receives and validates exact references only. Java validation is
    unchanged, so grounding is not weakened.
- **Retry.** The unknown-reference correction also quotes rejected aliases.
- **Finalize on any refusal.** When Core refuses an action (`REJECTED`) or
  cannot serve it (`UNAVAILABLE`) after earlier actions returned evidence, the
  loop asks for FINAL over the already authorized evidence. Without gathered
  evidence, a refusal stays terminal. Core's refusal is never overridden. The
  forced-FINAL prompt line now says "budget exhausted or Core refused an
  action".
- No budget, retrieval, content-policy or Core change.

## Acceptance Criteria

1. Aliases follow Core's order; a known alias resolves to its exact reference;
   unknown aliases and exact references are untouched.
2. The prompt lists aliases with their exact references and maps requested
   files to aliases.
3. A non-budget refusal after gathered evidence leads to FINAL; without
   evidence it stays terminal.
4. Live: pinned A and C requests complete without a reference-copy failure.

## Implementation Report

### Files Changed
- `ai-engine/app/prompts/reference_aliases.py` (new): alias table and resolver.
  `citable_references` moved here from the prompt module, which re-exports it.
- `ai-engine/app/prompts/story_context_analysis.py`: alias block, alias
  mapping, copy rule, alias-aware retry rule, forced-FINAL wording.
- `ai-engine/app/services/story_context_analysis_generation_service.py`:
  aliases are resolved before one-shot and FINAL validation; FINAL runs after
  any refusal once evidence has been gathered.
- `ai-engine/evaluations/scenarios/engineering-story-context-analysis-v1/scenario.json`:
  `promptContentDigest` updated to the new production prompt digest
  (`d7829070...`), as for #245 and Story 0243. `replay.json` is unchanged.
- Tests:
  - `tests/test_reference_aliases.py` (new, 3);
  - `tests/test_story_context_analysis_wire_contract.py`: +1 (finalize after
    `PATH_OUT_OF_SCOPE`);
  - `tests/test_story_context_analysis_prompt.py`: +1 (unknown alias quoted);
    4 assertions updated to the alias rendering.

### Validation
- AI Engine: 960 passed, 2 skipped.
- Live, pinned at `0673d1d3`, AI Engine rebuilt, same question, scope and
  budget as gate 3 (direct MCP calls, not a full authorized gate):
  - **A** (`9da8e73a`): COMPLETED on the first attempt, with no grounding
    retry. Honest abstention (`NOT_ESTABLISHED`, LOW), 21 references,
    4 architecture findings. In gate 3 the same condition failed on a mistyped
    reference.
  - **C** (`301eb1de`): COMPLETED. Two searches (8 + 7 items, 15/15), then a
    FINAL on the first attempt. MEDIUM confidence, 54 references, findings
    across architecture (3), impacted components (4), constraints (2),
    evidence (3) and decisions (2). Usage: 112 s of 300, 5.1k of 24k output
    tokens.
  - No alias remains in either persisted result, so Core received exact
    references only. The raw provider output is not stored, so whether the
    model cited aliases or full references is not directly observable. No
    copy failure occurred.
  - Finalize on a non-budget refusal was not exercised live: no action was
    refused in these runs. It is covered by tests.
  - The harness MCP call again reported `POLL_TIMEOUT` for C, which outlived
    the tool's wait. The task completed afterwards. Gate 2/3 showed that the
    harness re-reads the terminal status (A in gate 3: `resolvedAfter=POLL_TIMEOUT`).
