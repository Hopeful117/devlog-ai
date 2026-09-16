# Story 0132 - V2 Implementation Report

## Status

`V2_IMPLEMENTED - DETERMINISTICALLY_VERIFIED - LIVE_NOT_EXECUTED`

## Contract

V2 adds `CausalQuestion`, `CausalAssessment`, `EvidenceAssertion` and typed
`EvidenceLocator` contracts while retaining the legacy `CausalClaim` shape for
historical payloads. New causal-required tasks persist a Core-owned `causalQuestion`
and `causalContractVersion=V2` in the grounding contract.

The V2 path requires exactly one `causalAssessment`, rejects legacy causal claims,
checks exact question identity, resolves assertions through the existing
`AiReference` mapping and binds authoritative content and a SHA-256 digest in
Core. Roles remain model interpretation metadata and do not establish causality.

## Locator Boundary

Implemented locator kinds:

- `LINE_RANGE`: one-based inclusive line range in complete selected text.
- `SECTION`: exact Markdown heading and its bounded section.

Resolution is snapshot-only. It does not call collectors, retrieval, workspace
HEAD, RAG or a second registry. Unsupported, unavailable, truncated or
revision-mismatched evidence fails closed. Generated excerpts are compared with
resolved content; Core replaces model-supplied content and digest values.

## Tests

```text
AI Engine full suite: 233 passed
Backend full suite: 1335 tests, 0 failures
V2 resolver tests: 4 passed
Focused Story0132 Java tests: 25 passed
git diff --check: PASS
```

The tests cover fixed-question validation, bounded locators, line and section
resolution, reference authorization, revision binding, digest binding and
fabricated excerpt rejection. The backend callback validates V2 cardinality and
relationship identity before persistence.

## Frozen History

`evaluation/ground-truth-green-2026-09-15.json` was not overwritten. Its SHA-256
remains `011aed5f5c75264f2fea7272c745d8bc9fff45ee8c58b5c74ce8494190b345e1`.

The prior RED/live artifact was not overwritten. Its SHA-256 remains
`bf148c3ed4b5e697478d7dc3325dd7c39a0e8b3ec604fec85e4c1545145f4128`.

## Live Evaluation

`LIVE_EVALUATION=NOT_EXECUTED`. The repository contains a deterministic capture
scorer, but no Story0132 live runner that performs the frozen three-repetition
model calls. Existing first-GREEN artifacts contain the legacy `causalClaims`
contract and cannot be relabeled as V2 output. No V2 result artifact was
fabricated and no model/provider/benchmark was changed.

Consequently, V2 live metrics, evidence-removal live behavior and assertion
traceability over live cases remain `NOT_EXECUTED`. The implementation is not
reported as Story0132 product success.

## Boundaries

- Model: unchanged.
- Retrieval and Ground Truth: unchanged.
- RAG: not added.
- ADR-006: preserved; assessments remain untrusted AI analysis.
- Human Utility: `NOT_YET_ESTABLISHED`.
- Story0133: not created.
- Commit/push/merge: not performed.
