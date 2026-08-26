# Engineering Report — Story 0094

## Branch

`feature/story-0094-harden-engineering-event-grounding` — base `main @ 491d0cf`
(Story 0093 + Human-vs-Agent investigation available). Left unmerged for human
review/PR.

## Story

0094 — Harden Engineering Event AI Grounding Contract and Failure Diagnostics.
Artifacts: `docs/stories/0094-harden-engineering-event-grounding/`.

## DevLog MCP (self-use)

- `get_engineering_context`: freshness CURRENT (`491d0cf` across all three
  checkpoints) — trustworthy; evidence git-only, nothing on grounding → not
  useful for this defect.
- `search_project_history`: "analysis context grounding closure" surfaced the
  Story 0042 prior-art commits — genuinely useful. Prompt-vocabulary queries
  returned 0.

## Before

`analyze-engineering-event-prompt-v1` embedded a UUID-dense SelectedKnowledge
blob; its GROUNDING CONTRACT was an unlabeled JSON dump with none of the
copy-exact discipline insight.py carries. Validation raised opaque
"Unknown supportingFactIds"; corrective retry repeated that string verbatim;
second-attempt provider failures were collapsed into INVALID_LLM_OUTPUT.
Chronic ~50% failure (historical 4F/4C; pre-fix live runs: 2 FAILED / 3).

## Root Cause

Model cannot distinguish citable IDs from the many other UUIDs in context;
when it guessed, feedback named no offenders, so the bounded retry reproduced
the same mistake; error taxonomy then hid provider failures inside grounding
statistics.

## Prompt Change

GROUNDING CONTRACT now renders three labeled allow-list blocks plus explicit
rules: copy exactly / never derive / per-field empty arrays allowed while every
proposal keeps ≥1 grounding element / non-allow-listed identifiers elsewhere in
context are invalid citations.

## Corrective Retry

Before: `+ "\n\nCORRECTIVE RETRY\n" + str(error)` ("Unknown supportingFactIds").
After: rejection reason with sorted offending values + restatement that ONLY
the ALLOWED_* lists are valid sources + unrelated-ID prohibition + empty-
proposals permission. Validator now names offenders per field
(`_require_subset`), including evidence references.

## Error Mapping

| Situation | Before | After |
|---|---|---|
| Attempt 2 schema/grounding violation | INVALID_LLM_OUTPUT | INVALID_LLM_OUTPUT (with offender diagnostics) |
| Attempt 2 timeout/network/provider error | INVALID_LLM_OUTPUT ✗ | **LLM_PROVIDER_ERROR** ✓ |
| Attempt 1 provider failure | LLM_PROVIDER_ERROR | LLM_PROVIDER_ERROR (unchanged) |

## Prompt Version Decision

Identifier unchanged (backend-owned intent identity; digest carries
reproducibility) — matches the insight-prompt in-place hardening precedent;
changing it would have required backend IntentCatalog changes outside the
approved NONE-backend boundary.

## Tests

New `tests/test_engineering_event_grounding.py`: 12 tests (prompt contract ×3,
validation matrix ×6 incl. empty-list domain rule, retry diagnostics,
persistent-invalid terminal state, provider-failure classification first and
second attempt). Full ai-engine suite: **83 passed**. Backend untouched — no
backend run claimed for this Story.

## Runtime

Post-deploy (`docker compose up -d --build ai-engine`, healthy): **5 live
Analyze Evolution runs → 4 COMPLETED, 1 FAILED** (diagnostic INVALID_LLM_OUTPUT
naming offending file-path evidence). Pre-fix same-day baseline: 3 runs →
1 COMPLETED, 2 opaque FAILURES. Small samples; no statistical claim — wording
deliberate per mission §28.

## Grounding Proof

Task `3db2b48e…` COMPLETED proposalCount=1: cited fact
`a090fe00-68f4-47e8-9fd4-007f96c49c93` ∈ persisted allowed selectedFacts;
observation/evidence citations accepted by untouched strict validators. No
unknown IDs in any completed proposal.

## Non-regression

Insight generation suite green (untouched code); all pre-existing engineering
event tests green; zero backend/schema/MCP diffs verified via git.

## Remaining Limitations

- The model can still ground proposals on *irrelevant-but-allowed* facts or
  emit insufficient-evidence empty results — quality-of-selection, out of scope.
- One live failure mode observed post-fix: model cites changed-file paths as
  evidenceReferences when they were not allow-listed; now fails loudly with
  named values instead of silently corrupting knowledge.
- Provider-timeout retry behavior inside `MockLlmProvider`/OpenAI transport is
  upstream of this Story and unchanged.

## DevLog MCP Evaluation

Freshness trustworthy (CURRENT); history search useful once (Story 0042 prior
art); context tool not useful for prompt-level defects (expected given known
projection gaps — separate investigation tracks those).

## Suggested Next Story (not created)

MCP trusted-knowledge projection / context capability gap (HIGH finding #2 of
the investigation: adapter-empty facts + budget starvation), only after this
Story merges cleanly.
