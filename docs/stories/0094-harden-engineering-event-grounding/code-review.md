# Code Review — Story 0094

## Scope Reviewed

`ai-engine/app/prompts/engineering_event.py`,
`ai-engine/app/services/engineering_event_generation_service.py`,
`ai-engine/tests/test_engineering_event_grounding.py` (new).

## Grounding Safety

- **Exact allow-list semantics**: allow-lists are rendered as labeled line
  lists derived from the same `_grounding()` source used by the validator —
  prompt and validation can never diverge on what is allowed.
- **Unrelated UUIDs cannot be cited**: prose explicitly disqualifies
  project/source/analysis/event identifiers and commit hashes appearing
  elsewhere in SelectedKnowledge; validator enforces subset regardless of what
  the model emits.
- **Validator remains strict**: no relaxation anywhere; schema
  `require_grounding` (≥1 grounding element) untouched; empty-list nuance is
  *described* to the model, not weakened in code.

## Retry

- Offending IDs included: service raises `…allowed list: [sorted offenders]`;
  retry prompt embeds that text verbatim.
- Allow-list restated: retry retains the full original user message (which
  contains the ALLOWED_* blocks) plus an explicit pointer to them.
- Bounded: exactly one corrective attempt; persistent invalidity terminates
  with `INVALID_LLM_OUTPUT` (test proves exactly 2 provider calls, no loop).
- Copy-exact instruction repeated verbatim in the retry block.

## Error Semantics

- Output-invalid ≠ provider failure: second-attempt except split mirrors the
  proven insight-service pattern.
- First-attempt provider failure → `LLM_PROVIDER_ERROR` without retry
  (unchanged); second-attempt provider failure → `LLM_PROVIDER_ERROR`
  (previously misclassified as INVALID_LLM_OUTPUT — fixed and tested both ways).
- Final failure mapping truthful: terminal INVALID messages contain the actual
  offending values; provider messages carry exception text only.

## Prompt version

Template id intentionally unchanged (`analyze-engineering-event-prompt-v1`) —
backend-owned identity asserted by builder; reproducibility via persisted
`prompt_content_digest`, matching the insight-prompt hardening precedent.
Documented in implementation-plan §Versioning; no misleading silent v1 drift.

## Scope verification

- MCP tools/resources: untouched ✓
- Backend Java/DB/schema: zero diffs ✓ (`git diff 491d0cf -- backend/ db` empty)
- Synchronization/freshness: untouched ✓
- Documentation generation: untouched ✓
- RepositoryContextAdapter / KnowledgeSelectionService / collectors /
  BudgetedDiverseEvidenceSelector / EngineeringContextContractMapper:
  untouched ✓
- insight.py: untouched ✓ (duplication of ~8 lines of contract prose accepted
  over premature abstraction)

## Test quality

- Semantic-fragment assertions (not brittle full-string snapshots) for prompt
  rules.
- Instance-scoped fake providers — a draft class-level monkey-patch leak was
  caught by the full suite run and fixed before final green; final suite has
  zero cross-test state.

## Reproducibility

Prompt content digest recorded per task; deterministic tests pin rule
fragments; runtime evidence archived in implementation-report.

## Verdict

**APPROVED_FOR_COMMIT_APPROVAL** — scope clean, invariants hold, tests prove
the mission's required matrix including honest error classification.
