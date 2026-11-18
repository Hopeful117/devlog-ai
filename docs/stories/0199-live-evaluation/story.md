# Story 0199 — Live Evaluation: Extending Story 0177 with Real Captures

## Status

**DESIGN APPROVED** — Requires Story 0196 (semantic collector) before
implementation. Implementation authorization required.

## Problem

Story 0177's evaluation confirmed semantic retrieval improves recall on
fixture candidates, but the fixtures were manually crafted and not drawn
from the live repository. The production impact — whether the vocabulary gap
is real, how much noise increases, and whether latency stays acceptable —
has not been measured on actual `RepositoryContextEngine` outputs.

## Goal

Measure the production impact of hybrid retrieval on real candidate sets:

1. Capture live `RepositoryContextEngine` outputs for representative
   retrieval scenarios.
2. Run the Story 0177 evaluation harness against real captures.
3. Produce a comparison report: LEXICAL_CONTROL vs. HYBRID.
4. Provide a quantified go/no-go signal for enabling hybrid retrieval by
   default.

## Governing Artifacts

- ADR-066 — Replayable AI Intent Evaluation Harness
- ADR-080 — Hybrid Retrieval Architecture
- Story 0177 — Evaluate Hybrid Retrieval for Story Context (prerequisite)
- Story 0196 — Semantic Retrieval Collector (prerequisite)

## Architectural Constraints

1. Live capture is opt-in (`devlog.retrieval.capture.enabled=false` by
   default) and must not alter production behavior.
2. Captures must not contain credentials, auth tokens, full prompts, or
   raw provider payloads.
3. Captures are evaluation artifacts, not production traces; they follow
   the ADR-066 evaluation-only storage contract.
4. No production selection policy, grounding rule, or provider policy is
   changed by this story.

## In Scope

**Capture tooling (Java Core):**
- `RetrievalCaptureService`: when enabled, records a bounded capture after
  `RepositoryContextEngine.build()` completes.
- Captured fields: scenario identifier, project slug, revision, question,
  candidate references (with family/trust_tier/status/provenance), selected
  references, rejection reasons, context/projection digests, candidate count,
  selected count, estimated tokens, truncated flag, timing.
- Storage: JSON files under `evaluations/captures/live/` (not in DB,
  not in production traces).

**Evaluation harness extension:**
- Extend `evaluations/hybrid_retrieval.py` to load live captures
  (alongside the existing fixture corpus).
- Run LEXICAL_CONTROL and HYBRID_EXPERIMENT conditions on live captures.
- Extend metrics: recall, noise, context tokens, semantic-only recall
  (evidence found by semantic but not lexical), lexical-only recall
  (evidence found by lexical but not semantic), latency delta.

**Evaluation scenarios (3 minimum):**
1. Query whose vocabulary differs from the best-matching ADR (e.g., query:
   "decoupling strategy" → target: ADR about "dependency inversion").
2. Query that should retrieve an Engineering Story but currently returns 0
   Story candidates.
3. Query that should retrieve a validated Insight that is currently starved
   (scored ≤1/18 in baseline).

**Evaluation report:**
- Tabular results per condition and per scenario.
- Recommendation: `ENABLE_HYBRID`, `LEXICAL_SUFFICIENT`, or
  `COMPOSITION_BOTTLENECK` (not a retrieval problem).

**Tests:**
- Capture tooling: disabled flag → no capture file written, engine behavior
  unchanged.
- Harness: live capture loads and replays deterministically.

## Acceptance Criteria

- [ ] At least 3 live capture scenarios collected from a development
  instance with the semantic collector enabled.
- [ ] Evaluation harness runs on live captures without error.
- [ ] Evaluation report shows measurable recall improvement on at least 2
  of 3 scenarios without noise increase > 15%.
- [ ] No timeout regression in live captures.
- [ ] Capture tooling disabled by default: zero effect on existing behavior.
- [ ] No existing tests broken.

## Success Signal

The story is accepted when the evaluation report confirms (or refutes) the
production value of hybrid retrieval with real data. Either outcome is a
valid acceptance — the goal is measurement, not a predetermined result.
