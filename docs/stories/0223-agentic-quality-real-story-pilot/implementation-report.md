# Implementation Report - Story 0223

## Repository State

- Branch: `story/0210-agent-response-acceptability-contract`
- HEAD: `525c9701`
- Worktree: modified before this implementation; unrelated changes were preserved.

## Governance

- Story: `0223 - Agentic Quality Evaluation and Real Engineering Story Pilot`
- Governing constraints: ADR-006, ADR-063, ADR-066, ADR-067, ADR-079
- Approved artifacts: `story.md`, `repository-analysis.md`, `implementation-plan.md`
- DevLog preflight: attempted with `devlog_devlog_story_agent`; resumed with the
  same idempotency key and returned `401 Unauthorized`. Repository evidence was
  used instead; no DevLog context was treated as authoritative.

## Files Changed

- `ai-engine/evaluations/comparative_pilot_v1/protocol.py`: provider-free
  Story 0223 protocol contracts, identity validation, isolation, observations,
  completeness, pairing, authorization metadata, and neutral reports.
- `ai-engine/evaluations/comparative_pilot_v1/proof_gate.py`: fail-closed
  proof-of-result gate for epic closure; it requires human-approved thresholds,
  complete paired execution, reviewed oracles, comparative quality metrics, and
  human-utility evidence.
- `ai-engine/evaluations/comparative_pilot_v1/pilot-manifest.json`: frozen Q1-Q8
  population, three treatments, three repetitions, accepted Q1 oracle status,
  approved proof thresholds, and human-scoring protocol.
- `ai-engine/evaluations/comparative_pilot_v1/*-policy.json`: isolated treatment
  policies for DevLog context, direct investigation, and Core-authorized Story
  Context investigation.
- `ai-engine/tests/test_comparative_pilot_v1.py`: deterministic protocol,
  leakage, digest, secret, completeness, pairing, and provisional-report tests.
- Story 0223 documentation: statuses and implementation report.

## Implementation Summary

The provider-free Story 0223 protocol is implemented as an isolated package and
does not modify Story 0133 primitives. It validates the 72-cell assignment
matrix, treatment-specific inputs, oracle exclusion, immutable hash-bound
  observation metadata, secret rejection, three-treatment pairing, execution
  completeness, and fail-closed proof reporting.

No provider client, repository access, network call, prompt change, retrieval
change, budget change, production service change, commit, or push was performed.

## Architectural Decisions Preserved

- Story 0133 remains unchanged and retains its original two-treatment semantics.
- Core-authorized context and Story investigation remain distinct from direct
  repository investigation.
- Python remains limited to evaluation contracts and defensive validation.
- Technical failures and missing assignments remain outside semantic quality
  denominators.
- Human-reviewed oracles are required before official quality conclusions.
- Live provider validation remains a follow-up and is not implicitly authorized
  by campaign or oracle approval.

## Tests Executed

```bash
uv run pytest tests/test_comparative_pilot_v1.py -q
uv run pytest tests/test_comparative_baseline.py tests/test_comparative_collection_runtime.py tests/test_comparative_live_adapters.py tests/test_story_context_abstention_confidence.py -q
python3 -m compileall -q evaluations/comparative_pilot_v1 tests/test_comparative_pilot_v1.py
git diff --check -- ai-engine/evaluations/comparative_pilot_v1 ai-engine/tests/test_comparative_pilot_v1.py docs/stories/0223-agentic-quality-real-story-pilot
```

## Test Results

- Story 0223 focused tests: **13 passed**.
- Existing comparative and Story 0222 tests: **87 passed**.
- Full AI Engine suite: **passed**, with 2 skipped tests.
- Backend suite: **1,879 passed**, 0 failures, 0 errors.
- MCP Server suite: **125 passed**, 0 failures, 0 errors.
- Core/Python Q1 integration: **2 passed**.
- Python compilation: **passed**.
- Diff whitespace check: **passed**.

## Known Limitations

- Q1-Q8 oracle acceptance is recorded in the oracle proposal document; inferred
  claims remain explicitly qualified and are not promoted to deterministic
  facts.
- The proof gate currently returns `NOT_PROVEN` until approved thresholds and
  campaign evidence are supplied; approved thresholds are recorded in the pilot
  manifest and the gate does not fabricate human-utility evidence.
- No live technical validation or 72-assignment provider campaign was run.
- The live authorization contract is offline-validatable only; it does not
  execute providers.
- The first combined Maven run exceeded its command timeout during MCP tests;
  the backend and MCP modules were subsequently validated independently.

## Blockers / Repository Inconsistencies

- DevLog context service returned `401 Unauthorized` during preflight.
- Human acceptance of Story 0223 and separate live-provider authorization remain
  required. The campaign plan and scoring protocol are approved, but execution
  remains gated by technical live validation and authorization.

## Human Decisions Recorded

- Q1 oracle: accepted, including claims, scoring weights, format-neutral rules,
  and suitability for the asymmetric comparison.
- Q2-Q8 oracles: accepted as written, retaining all inferred-claim labels,
  expected uncertainty, and forbidden-claim boundaries.
- Campaign: 8 questions x 3 treatments x 3 repetitions (72 assignments).
- Human scoring: 2 blinded raters with adjudication of disagreements.
- Human utility: separate utility session for both target treatments.
- Live provider validation: follow-up for Story acceptance, not implicitly
  authorized by this decision.
- Live execution decision: prepare the bounded authorization only; provider
  calls remain explicitly unauthorized.
- Proof thresholds: execution completeness 1.00, human utility 0.75,
  non-inferiority margin 0.05, advantage delta 0.10 on
  `groundedCorrectnessRate`.

## Commit / Push

- Commit created: **NO**
- Push performed: **NO**

## Readiness

READY_FOR_HUMAN_ACCEPTANCE
